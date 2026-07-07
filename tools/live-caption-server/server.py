#!/usr/bin/env python3
import argparse
import json
import shutil
import signal
import subprocess
import tempfile
import threading
import time
import uuid
import wave
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Dict, List, Optional
from urllib.parse import parse_qs, unquote, urlparse


SAMPLE_RATE = 16_000
BYTES_PER_SAMPLE = 2


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
        if self.process and self.process.poll() is None:
            self.process.terminate()


class CaptionServer:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.sessions: Dict[str, CaptionSession] = {}
        self.lock = threading.Lock()
        self.model = None
        self.model_error: Optional[str] = None

    def load_model(self):
        if self.model or self.model_error:
            return self.model
        try:
            from faster_whisper import WhisperModel

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
        try:
            session.process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            session.status = "listening"
            chunk_index = 0
            while not session.stop_event.is_set():
                if session.process.stdout is None:
                    break
                audio = session.process.stdout.read(chunk_bytes)
                if not audio:
                    break
                start_ms = int(chunk_index * self.args.chunk_seconds * 1000)
                end_ms = start_ms + int(len(audio) / (SAMPLE_RATE * BYTES_PER_SAMPLE) * 1000)
                chunk_index += 1
                session.status = "transcribing"
                text = self.transcribe_chunk(model, audio)
                session.add_cue(start_ms, max(end_ms, int((time.time() - started_at) * 1000)), text)
                session.status = "running"
        except Exception as exc:
            session.error = str(exc)
        finally:
            session.stop()

    def transcribe_chunk(self, model, audio: bytes) -> str:
        with tempfile.NamedTemporaryFile(suffix=".wav") as wav_file:
            with wave.open(wav_file.name, "wb") as wav:
                wav.setnchannels(1)
                wav.setsampwidth(BYTES_PER_SAMPLE)
                wav.setframerate(SAMPLE_RATE)
                wav.writeframes(audio)
            segments, _ = model.transcribe(
                wav_file.name,
                task=self.args.task,
                beam_size=self.args.beam_size,
                vad_filter=True,
            )
            return " ".join(segment.text.strip() for segment in segments).strip()


def build_handler(server_state: CaptionServer):
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
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
            except ValueError as exc:
                self.send_json({"error": str(exc)}, 400)

        def do_GET(self):
            parsed = urlparse(self.path)
            if parsed.path == "/health":
                self.send_json({"ok": True})
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
            since_seq = int(query.get("sinceSeq", ["0"])[0])
            self.send_json(session.snapshot(since_seq))

        def do_DELETE(self):
            parts = self.path.strip("/").split("/")
            if len(parts) != 2 or parts[0] != "sessions":
                self.send_json({"error": "not found"}, 404)
                return
            deleted = server_state.delete_session(unquote(parts[1]))
            self.send_json({"deleted": deleted}, 200 if deleted else 404)

        def read_json(self) -> dict:
            length = int(self.headers.get("Content-Length", "0"))
            data = self.rfile.read(length).decode("utf-8")
            return json.loads(data or "{}")

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
