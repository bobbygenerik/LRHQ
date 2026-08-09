package com.livingroomhq.translate

/**
 * Translation stack decision (2026-08-08).
 *
 * ## Requested: opus-mt-ROMANCE-en
 * Helsinki-NLP `opus-mt-ROMANCE-en` **does** include Spanish, Portuguese, and French
 * among Romance sources → English. That part checks out.
 *
 * ## Blocker for Bergamot/Marian-on-Android
 * 1. HF `opus-mt-ROMANCE-en` is Transformers/Marian (PyTorch/NPZ) — **not** Bergamot
 *    `model.*.intgemm.alphas.bin` student format.
 * 2. Firefox/Bergamot production models are **one-to-one** (esen, pten, fren). There is
 *    **no** combined Romance→en Bergamot artifact.
 * 3. `mozilla/bergamot-translator` has Python/WASM bindings only — **no official Android
 *    JNI**. NDK + Marian + RUY is multi-day and fragile for this tree.
 *
 * ## Shipped runtime (kept)
 * [MlKitTranslationBackend]: Google ML Kit on-device Translate for es/pt/fr → en.
 * Language packs download once, then offline. Behind [TranslationBackend] so a future
 * Bergamot NDK build can replace it without changing the Media3/Whisper pipeline.
 * Decision 2026-08-08: finish live captions on ML Kit first; do not swap to Bergamot unless
 * ML Kit is a hard blocker on Shield.
 *
 * ## Firefox models (for future Bergamot)
 * Hashes of decompressed Release `base-memory` packs from Mozilla GCS (2026-08-08):
 * - es: model.esen.intgemm.alphas.bin sha256=4aed7734…b033 (~30.1 MB)
 * - pt: model.pten.intgemm.alphas.bin sha256=7b854f1e…9157 (~30.1 MB)
 * - fr: model.fren.intgemm.alphas.bin sha256=15f997bc…2b90 (~30.1 MB)
 * Plus matching vocab.*.spm and lex.50.50.*.s2t.bin (see research notes).
 */
object TranslationStackNotes
