#!/usr/bin/env python3
import argparse
import json
import queue
import shutil
import signal
import subprocess
import threading
import time
import uuid
from collections import deque
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Dict, List, Optional
from urllib.parse import parse_qs, unquote, urlparse


SAMPLE_RATE = 16_000
BYTES_PER_SAMPLE = 2

# Chunks queued between the ffmpeg reader and the transcriber. When the
# transcriber falls behind real time the oldest chunk is dropped so captions
# stay near-live instead of accumulating latency.
CHUNK_QUEUE_DEPTH = 3

# Sessions the client stopped polling (crashed app, dropped LAN link, or a
# client that failed to send DELETE) are reaped so ffmpeg + whisper don't
# transcribe a live stream forever.
IDLE_SESSION_TTL_SECONDS = 90.0
REAPER_INTERVAL_SECONDS = 15.0


@dataclass
class Cue:
    seq: int
    start_ms: int
    end_ms: int
    text: str


@dataclass
class CaptionSession:
    id: str
    stream_url: str
    channel_name: str
    target_language: str
    status: str = "starting"
    created_at: float = field(default_factory=time.time)
    last_polled: float = field(default_factory=time.time)
    next_seq: int = 1
    cues: List[Cue] = field(default_factory=list)
    error: Optional[str] = None
    stop_event: threading.Event = field(default_factory=threading.Event)
    process: Optional[subprocess.Popen] = None
    thread: Optional[threading.Thread] = None
    lock: threading.Lock = field(default_factory=threading.Lock)

    def add_cue(self, start_ms: int, end_ms: int, text: str) -> None:
        clean = " ".join(text.split())
        if not clean:
            return
        with self.lock:
            self.cues.append(Cue(self.next_seq, start_ms, end_ms, clean))
            self.next_seq += 1
            self.cues = self.cues[-80:]

    def snapshot(self, since_seq: int) -> dict:
        self.last_polled = time.time()
        now_ms = int((time.time() - self.created_at) * 1000)
        with self.lock:
            cues = [cue for cue in self.cues if cue.seq > since_seq]
            recent = [cue for cue in self.cues if cue.end_ms >= now_ms - 15_000]
            active = recent[-1].text if recent else None
            return {
                "sessionId": self.id,
                "status": self.error or self.status,
                "activeText": active,
                "cues": [
                    {
                        "seq": cue.seq,
                        "startMs": cue.start_ms,
                        "endMs": cue.end_ms,
                        "text": cue.text,
                    }
                    for cue in cues
                ],
            }

    def stop(self) -> None:
        self.stop_event.set()
        process = self.process
        if process and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    pass


class TooManySessions(Exception):
    pass


class CaptionServer:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.sessions: Dict[str, CaptionSession] = {}
        self.lock = threading.Lock()
        self.model = None
        self.model_error: Optional[str] = None
        self.model_lock = threading.Lock()
        self.np = None
        self.reaper = threading.Thread(
            target=self.reap_idle_sessions, name="session-reaper", daemon=True
        )
        self.reaper.start()

    def load_model(self):
        # Locked: two first sessions racing here would otherwise load two copies.
        with self.model_lock:
            if self.model or self.model_error:
                return self.model
            try:
                import numpy

                from faster_whisper import WhisperModel

                self.np = numpy
                self.model = WhisperModel(
                    self.args.model,
                    device=self.args.device,
                    compute_type=self.args.compute_type,
                )
                return self.model
            except Exception as exc:
                self.model_error = str(exc)
                return None

    def create_session(self, payload: dict) -> CaptionSession:
        stream_url = payload.get("streamUrl", "").strip()
        if not stream_url:
            raise ValueError("streamUrl is required")
        session = CaptionSession(
            id=str(uuid.uuid4()),
            stream_url=stream_url,
            channel_name=payload.get("channelName", "Live TV"),
            target_language=payload.get("targetLanguage", "en"),
        )
        session.thread = threading.Thread(
            target=self.run_session,
            args=(session,),
            name=f"caption-{session.id[:8]}",
            daemon=True,
        )
        with self.lock:
            if len(self.sessions) >= self.args.max_sessions:
                raise TooManySessions(
                    f"session limit reached ({self.args.max_sessions})"
                )
            self.sessions[session.id] = session
        session.thread.start()
        return session

    def get_session(self, session_id: str) -> Optional[CaptionSession]:
        with self.lock:
            return self.sessions.get(session_id)

    def delete_session(self, session_id: str) -> bool:
        with self.lock:
            session = self.sessions.pop(session_id, None)
        if not session:
            return False
        session.stop()
        return True

    def reap_idle_sessions(self) -> None:
        while True:
            time.sleep(REAPER_INTERVAL_SECONDS)
            now = time.time()
            with self.lock:
                stale = [
                    session
                    for session in self.sessions.values()
                    if now - session.last_polled > IDLE_SESSION_TTL_SECONDS
                ]
                for session in stale:
                    self.sessions.pop(session.id, None)
            for session in stale:
                session.stop()
                if not self.args.quiet:
                    print(f"reaped idle session {session.id[:8]} ({session.channel_name})")

    def run_session(self, session: CaptionSession) -> None:
        if shutil.which("ffmpeg") is None:
            session.error = "ffmpeg not found"
            return
        model = self.load_model()
        if model is None:
            session.error = self.model_error or "faster-whisper is not available"
            return

        chunk_bytes = self.args.chunk_seconds * SAMPLE_RATE * BYTES_PER_SAMPLE
        command = [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "warning",
            "-i",
            session.stream_url,
            "-vn",
            "-ac",
            "1",
            "-ar",
            str(SAMPLE_RATE),
            "-f",
            "s16le",
            "pipe:1",
        ]
        started_at = time.time()
        stderr_tail: deque = deque(maxlen=5)
        chunks: "queue.Queue" = queue.Queue(maxsize=CHUNK_QUEUE_DEPTH)
        try:
            session.process = subprocess.Popen(
                command, stdout=subprocess.PIPE, stderr=subprocess.PIPE
            )
            # stderr must be drained or ffmpeg blocks once the pipe fills.
            threading.Thread(
                target=self.drain_stderr,
                args=(session.process, stderr_tail),
                name=f"stderr-{session.id[:8]}",
                daemon=True,
            ).start()
            reader = threading.Thread(
                target=self.read_chunks,
                args=(session, chunk_bytes, chunks),
                name=f"reader-{session.id[:8]}",
                daemon=True,
            )
            session.status = "listening"
            reader.start()

            while not session.stop_event.is_set():
                try:
                    item = chunks.get(timeout=0.5)
                except queue.Empty:
                    if reader.is_alive():
                        continue
                    break
                if item is None:
                    break
                start_ms, end_ms, audio = item
                session.status = "transcribing"
                text = self.transcribe_chunk(model, audio)
                session.add_cue(
                    start_ms,
                    max(end_ms, int((time.time() - started_at) * 1000)),
                    text,
                )
                session.status = "running"

            if session.process.poll() not in (None, 0) and not session.error:
                detail = "; ".join(stderr_tail)
                session.error = f"ffmpeg exited ({session.process.returncode})" + (
                    f": {detail}" if detail else ""
                )
        except Exception as exc:
            session.error = str(exc)
        finally:
            session.stop()

    def read_chunks(
        self, session: CaptionSession, chunk_bytes: int, chunks: "queue.Queue"
    ) -> None:
        """Reads PCM off ffmpeg concurrently with transcription so a slow
        transcribe pass doesn't backpressure ffmpeg and drift off live."""
        bytes_read = 0
        stdout = session.process.stdout
        try:
            while not session.stop_event.is_set():
                audio = stdout.read(chunk_bytes)
                if not audio:
                    break
                start_ms = int(bytes_read * 1000 / (SAMPLE_RATE * BYTES_PER_SAMPLE))
                bytes_read += len(audio)
                end_ms = int(bytes_read * 1000 / (SAMPLE_RATE * BYTES_PER_SAMPLE))
                self.offer_chunk(chunks, (start_ms, end_ms, audio))
        finally:
            self.offer_chunk(chunks, None)

    @staticmethod
    def offer_chunk(chunks: "queue.Queue", item) -> None:
        while True:
            try:
                chunks.put_nowait(item)
                return
            except queue.Full:
                try:
                    chunks.get_nowait()
                except queue.Empty:
                    pass

    @staticmethod
    def drain_stderr(process: subprocess.Popen, tail: deque) -> None:
        stream = process.stderr
        if stream is None:
            return
        for raw in iter(stream.readline, b""):
            line = raw.decode("utf-8", errors="replace").strip()
            if line:
                tail.append(line)

    def transcribe_chunk(self, model, audio: bytes) -> str:
        # Feed PCM straight to faster-whisper; the temp-WAV round trip costs
        # a file write + reparse every chunk for nothing.
        samples = (
            self.np.frombuffer(audio, dtype=self.np.int16).astype(self.np.float32)
            / 32768.0
        )
        segments, _ = model.transcribe(
            samples,
            task=self.args.task,
            beam_size=self.args.beam_size,
            vad_filter=True,
        )
        return " ".join(segment.text.strip() for segment in segments).strip()


def build_handler(server_state: CaptionServer):
    class Handler(BaseHTTPRequestHandler):
        def authorized(self) -> bool:
            token = server_state.args.token
            if not token:
                return True
            header = self.headers.get("Authorization", "")
            return header == f"Bearer {token}"

        def require_auth(self) -> bool:
            if self.authorized():
                return True
            self.send_json({"error": "unauthorized"}, 401)
            return False

        def do_POST(self):
            if not self.require_auth():
                return
            if self.path != "/sessions":
                self.send_json({"error": "not found"}, 404)
                return
            try:
                payload = self.read_json()
                session = server_state.create_session(payload)
                self.send_json(
                    {
                        "sessionId": session.id,
                        "status": session.status,
                    },
                    201,
                )
            except TooManySessions as exc:
                self.send_json({"error": str(exc)}, 429)
            except ValueError as exc:
                self.send_json({"error": str(exc)}, 400)

        def do_GET(self):
            parsed = urlparse(self.path)
            if parsed.path == "/health":
                self.send_json({"ok": True})
                return
            if not self.require_auth():
                return
            parts = parsed.path.strip("/").split("/")
            if len(parts) != 3 or parts[0] != "sessions" or parts[2] != "cues":
                self.send_json({"error": "not found"}, 404)
                return
            session = server_state.get_session(unquote(parts[1]))
            if session is None:
                self.send_json({"error": "unknown session"}, 404)
                return
            query = parse_qs(parsed.query)
            try:
                since_seq = int(query.get("sinceSeq", ["0"])[0])
            except ValueError:
                self.send_json({"error": "sinceSeq must be an integer"}, 400)
                return
            self.send_json(session.snapshot(since_seq))

        def do_DELETE(self):
            if not self.require_auth():
                return
            parts = self.path.strip("/").split("/")
            if len(parts) != 2 or parts[0] != "sessions":
                self.send_json({"error": "not found"}, 404)
                return
            deleted = server_state.delete_session(unquote(parts[1]))
            self.send_json({"deleted": deleted}, 200 if deleted else 404)

        def read_json(self) -> dict:
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                raise ValueError("invalid Content-Length")
            data = self.rfile.read(length).decode("utf-8", errors="replace")
            try:
                return json.loads(data or "{}")
            except json.JSONDecodeError as exc:
                raise ValueError(f"invalid JSON body: {exc}")

        def send_json(self, payload: dict, status: int = 200) -> None:
            body = json.dumps(payload).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt: str, *args) -> None:
            if not server_state.args.quiet:
                super().log_message(fmt, *args)

    return Handler


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="LRHQ live IPTV caption server")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--model", default="small")
    parser.add_argument("--device", default="auto")
    parser.add_argument("--compute-type", default="auto")
    parser.add_argument("--task", choices=["transcribe", "translate"], default="translate")
    parser.add_argument("--chunk-seconds", type=int, default=6)
    parser.add_argument("--beam-size", type=int, default=3)
    parser.add_argument(
        "--max-sessions",
        type=int,
        default=4,
        help="Concurrent transcription sessions before new ones get 429",
    )
    parser.add_argument(
        "--token",
        default="",
        help="Shared secret; when set, requests must send 'Authorization: Bearer <token>' "
        "(configure the same token in LRHQ Settings). /health stays open.",
    )
    parser.add_argument("--quiet", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    state = CaptionServer(args)
    httpd = ThreadingHTTPServer((args.host, args.port), build_handler(state))

    def shutdown(_signum, _frame):
        for session in list(state.sessions.values()):
            session.stop()
        httpd.shutdown()

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)
    print(f"LRHQ caption server listening on http://{args.host}:{args.port}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
