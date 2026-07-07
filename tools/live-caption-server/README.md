# LRHQ live caption server

Prototype LAN caption worker for IPTV playback. The Android TV app sends the IPTV stream URL here; this process opens the same stream with `ffmpeg`, captures mono 16 kHz audio chunks, runs ASR locally, and serves rolling cues back to the app.

## Setup

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install faster-whisper
```

Install `ffmpeg` if it is not already available.

## Run

```bash
python server.py --host 0.0.0.0 --port 8765 --model small --task translate
```

Then set this in Android `local.properties` before building/installing the app:

```properties
caption.serverUrl=http://YOUR_MACHINE_LAN_IP:8765
```

Use `--task transcribe` for same-language captions. Use `--task translate` to ask Whisper to translate speech to English.
