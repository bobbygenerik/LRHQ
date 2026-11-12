# Caption accuracy replay

This tooling tests meaning on reference audio using the actual Kotlin segmenter/cleanup, the native decoder parameters, and the actual Android translator. It does not assert that all translations are correct or simulate the Shield's real-time queue timing.

The 2026-10-07 evidence and qualitative assessments are in `local/caption-accuracy/2026-10-07/REPORT.md`. Google FLEURS dev references are aligned by shared ID across `pt_br` and `en_us`; source: https://huggingface.co/datasets/google/fleurs (CC BY 4.0). The small development sample was used to adjust pipeline parameters, without training or fine-tuning the recognition model; do not report a general accuracy score from it.

## Segment and clean with production Kotlin

Convert 16 kHz mono recordings to raw little-endian float32 first:

```sh
ffmpeg -i sample.wav -ar 16000 -ac 1 -f f32le audio/sample.f32
./gradlew -I tools/caption_accuracy/replay.init.gradle :app:captionAccuracyReplay \
  -PcaptionAudioDir=/absolute/audio -PcaptionEvidenceDir=/absolute/current-windows \
  -PcaptionReplayMode=current
```

`baseline` selects the original .6-second minimum, 1.8-second maximum, .25-second pause, .4-second overlap and .4 relative silence ratio. `current` uses the current production defaults and 1.1-second minimum native input padding. Recordings get one second of trailing silence for boundary detection. `windows.tsv` contains clip ID, file, unpadded source start/end sample and utterance-end status; emitted `.f32` contains the actual model input. Do not compare offsets using the padded file length.

The `commit` mode consumes `<clip> TAB <utteranceEnd> TAB <base64 transcript>` and invokes production hypothesis commitment, repetition cleanup and noise rejection. `clean` skips hypothesis commitment, matching English MT cleanup:

```sh
./gradlew -I tools/caption_accuracy/replay.init.gradle :app:captionAccuracyReplay \
  -PcaptionAudioDir=commit -PcaptionEvidenceDir=/absolute/native-output/raw.tsv \
  -PcaptionReplayMode=/absolute/committed.tsv
```

The output contains clip and base64 caption; empty captions must remain represented when aligning native audio-window results.

## Native recognition

Build whisper.cpp v1.7.5 with `GGML_NATIVE=OFF` and `GGML_CUDA=OFF`; use the version fetched by the app under `app/.cxx/.../_deps/whisper_cpp-src`, or the upstream v1.7.5 release. Compile `decoder.cpp` with its `include` and `ggml/include`, linking libwhisper/libggml/libggml-base/libggml-cpu and pthread from that build. This runner uses two host CPU threads and the same greedy parameters as JNI. CPU output and speed can differ from device acceleration.

Models are from https://huggingface.co/ggerganov/whisper.cpp:

- `ggml-base-q5_1.bin`, SHA-256 `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`
- `ggml-small-q5_1.bin`, SHA-256 `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`

```sh
python3 tools/caption_accuracy/run_decoder.py --decoder /absolute/decoder \
  --model /absolute/ggml-small-q5_1.bin --manifest /absolute/current-windows/windows.tsv \
  --output /absolute/native-output --transcribe
```

For the original baseline, omit `--transcribe`, use base, baseline windows, and `--max-tokens 64`. Current default is 128. The runner rejects no-speech probability >= .55 before Kotlin commitment. It saves per-window decoded JSON, raw base64 TSV, and the native diagnostic log. Input paths must have no whitespace.

## Actual Android MT

A standalone QA app source snapshot is saved alongside the local evidence under `mlkit-qa`. It has a separate application ID, uses ML Kit 17.0.3 and copies the production backend/interface/name-protection files. Refresh those copies before using it to test newer changes. Build its Release and ReleaseAndroidTest APKs using the repository wrapper with `-p /absolute/mlkit-qa` and Android SDK environment. Its signing config is only for that separate QA app.

On a local API 34 emulator, install both QA APKs with `adb -s emulator-5554 install -r`, write an array of objects with a `portuguese` field to `/sdcard/Android/data/com.livingroomhq.captionqa/files/input.json`, and run:

```sh
adb -s emulator-5554 shell am instrument -w \
  com.livingroomhq.captionqa.test/com.livingroomhq.captionqa.AccuracyInstrumentation
```

Pull the `results.json` reported by instrumentation. The first run needs the PT/EN pack downloaded. A `RESULT_OK` proves execution succeeded, not semantic correctness. Compare each output against the audible speech and human reference; preserve errors in the report.

## Broadcast evidence on Shield

The normal build does not record. For a bounded diagnostic check:

```sh
./gradlew :app:testReleaseUnitTest :app:assembleRelease -PcaptionDiagnostics=true
```

The flag enables local `CaptionDiagnosticRecorder` (60 seconds, 16 kHz mono float32 WAV), with session/audio-range/transcript/translation/display events in JSONL. No upload occurs. Files are in `/sdcard/Android/data/com.livingroomhq/files/caption_accuracy/latest.{wav,jsonl}`. Each caption session/channel/language change overwrites the previous evidence. Stopping preserves it. Ignore captions whose `audioComplete` is false at the cap.

Compare the APK signer to the currently installed app before `adb install -r`. Never uninstall or clear app data. Coordinate the playback interruption with the viewer. Reopen the channel, enable captions, and select PT after restart. Measure actual Shield recognition time, missing windows, audible meaning, and readable caption timing. The local replay does not replace this check.


The current full-context small model collects at most 3.5 seconds, and rejects audio queued for more than 0.5 seconds or results older than four seconds after the window end. These limits drop work when recognition cannot keep up. The local report also includes pt-PT MINDS-14 checks, captured RTP1 evidence, quiet native Shield timings, and the rejected context/weight-format experiments. They do not certify European Portuguese TV accuracy.


`PipelineInstrumentation` in the saved QA source replays `broadcast.f32` through the copied production pipeline at real-time speed on the Shield without playing audio or waking a screen. Register that class as the QA `testInstrumentationRunner`, refresh the matching native libraries from the current APK, install the separate QA APKs with `-r`, and run its instrumentation component. It writes `pipeline-results.json` and bounded diagnostic evidence. Its original build configuration currently selects this runner; select `AccuracyInstrumentation` or `TimingInstrumentation` and rebuild the test APK for their respective checks. Native Java/C signatures must match; never combine new Kotlin bridge code with old libraries.
