"""Replay production audio slices through the same whisper.cpp JNI decoder settings."""
import argparse
import base64
import csv
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--decoder", required=True, type=Path)
parser.add_argument("--model", required=True, type=Path)
parser.add_argument("--manifest", required=True, type=Path)
parser.add_argument("--output", required=True, type=Path)
parser.add_argument("--max-tokens", type=int, default=128)
parser.add_argument("--transcribe", action="store_true", help="Recognize source Portuguese rather than direct English")
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
results = []
with (args.output / "decoder.log").open("w") as errors, (args.output / "raw.tsv").open("w") as raw:
    process = subprocess.Popen(
        [str(args.decoder), str(args.model), str(args.max_tokens)],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=errors, text=True,
    )
    try:
        for clip, filename, start, end, utterance_end in csv.reader(args.manifest.open(), delimiter="\t"):
            if any(character.isspace() for character in filename):
                raise ValueError("Decoder input path must not contain whitespace")
            sample_count = Path(filename).stat().st_size // 4
            process.stdin.write(f"{filename} 0 {sample_count} {0 if args.transcribe else 1}\n")
            process.stdin.flush()
            line = process.stdout.readline()
            if not line:
                raise RuntimeError("Decoder stopped; inspect decoder.log")
            result = json.loads(line)
            if result["rc"]:
                raise RuntimeError(f"Whisper inference failed: {result['rc']}")
            result.update(clip=clip, startSample=int(start), endSample=int(end), utteranceEnd=utterance_end == "true")
            results.append(result)
            (args.output / "decoded.json").write_text(json.dumps(results, ensure_ascii=False, indent=2))
            encoded = base64.b64encode(result["text"].encode()).decode()
            # Apply the same no-speech rejection before the production cleanup helper.
            if result["p"] >= .55:
                encoded = ""
            raw.write(f"{clip}\t{utterance_end}\t{encoded}\n")
            raw.flush()
            print(clip, int(start) / 16_000, result["text"].strip(), flush=True)
    finally:
        process.stdin.close()
        if process.poll() is None:
            process.wait(timeout=30)
