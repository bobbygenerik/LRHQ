package com.livingroomhq.translate

import android.content.Context
import android.util.Log
import com.livingroomhq.player.pcm.CaptionAudioPolicy
import com.livingroomhq.player.pcm.PcmCaptureHub
import com.livingroomhq.translate.whisper.WhisperSpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Live captions:
 * Media3 tee PCM → bounded queue → 16 kHz mono → Whisper → ML Kit → English UI.
 *
 * PCM consumer never blocks on Whisper. Live energy clears captions when dialog
 * stops; late ASR/MT results from before silence are discarded (timing, not hallucination).
 */
class LiveTranslatePipeline(
    context: Context,
    private val recognizer: SpeechRecognizerBackend = WhisperSpeechRecognizer(context),
    private val translator: TranslationBackend = MlKitTranslationBackend(),
) {
    private val _englishCaption = MutableStateFlow<String?>(null)
    val englishCaption: StateFlow<String?> = _englishCaption.asStateFlow()

    private val _sourceCaption = MutableStateFlow<String?>(null)
    val sourceCaption: StateFlow<String?> = _sourceCaption.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    @Volatile var showSourceDebug: Boolean = false

    private val sessionGen = AtomicLong(0)
    /** 0 = live speech recently; else wall-ms when live energy went silent. */
    private val liveSilentSinceMs = AtomicLong(0)

    @Volatile private var sourceLanguage: String = "es"
    @Volatile private var channelId: String = ""

    private var pipelineScope: CoroutineScope? = null
    private var loopJob: Job? = null
    private var statusJob: Job? = null
    private var inferJob: Job? = null

    private var lastSourceText: String = ""
    private var lastTranslatedSource: String = ""
    private var lastCaptionAtMs: Long = 0L
    private var loggedInputFormat: String? = null

    fun isRunning(): Boolean = loopJob?.isActive == true

    fun currentSessionId(): Long = sessionGen.get()

    fun start(parentScope: CoroutineScope) {
        if (loopJob?.isActive == true) return
        clearCaptions()
        CaptionAudioPolicy.setForcePcmDecode(true)
        val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
        pipelineScope = scope
        bumpSession("start")
        statusJob =
            scope.launch {
                translator.preparingStatus.collect { prep ->
                    if (prep != null) _status.value = prep
                }
            }
        loopJob =
            scope.launch(Dispatchers.Default) {
                try {
                    _status.value = "Loading Whisper…"
                    recognizer.ensureReady()
                    if (!isActive) return@launch
                    _status.value = "Loading translator (${sourceLanguage.uppercase()}→EN)…"
                    runCatching { translator.ensureReady(sourceLanguage) }
                        .onFailure { t ->
                            Log.e(TAG, "translator init failed session=${sessionGen.get()}", t)
                            _status.value = "Translation model failed — change language to retry"
                            return@launch
                        }
                    PcmCaptureHub.startCapture()
                    logInputFormatOnce()
                    _status.value = "Live ${sourceLanguage.uppercase()}→EN"
                    Log.i(
                        TAG,
                        "pipeline live session=${sessionGen.get()} lang=$sourceLanguage channel=$channelId " +
                            "windowSec=${WINDOW_SAMPLES.toDouble() / PcmAudioNormalizer.TARGET_RATE} " +
                            "hopSec=${HOP_SAMPLES.toDouble() / PcmAudioNormalizer.TARGET_RATE}",
                    )
                    runLoop(scope)
                } catch (t: Throwable) {
                    Log.e(TAG, "pipeline failed", t)
                    _status.value = t.message ?: "Caption pipeline failed"
                } finally {
                    inferJob?.cancel()
                    inferJob = null
                    PcmCaptureHub.stopCapture()
                }
            }
    }

    fun stop() {
        bumpSession("stop")
        inferJob?.cancel()
        inferJob = null
        loopJob?.cancel()
        loopJob = null
        statusJob?.cancel()
        statusJob = null
        PcmCaptureHub.stopCapture()
        CaptionAudioPolicy.setForcePcmDecode(false)
        clearCaptions()
        _status.value = null
        loggedInputFormat = null
        liveSilentSinceMs.set(0)
        val scope = pipelineScope
        pipelineScope = null
        scope?.launch(Dispatchers.IO) {
            runCatching { recognizer.release() }
            runCatching { translator.release() }
            scope.cancel()
        } ?: run {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { recognizer.release() }
                runCatching { translator.release() }
            }
        }
    }

    fun onChannelChanged(newChannelId: String) {
        channelId = newChannelId
        if (!isRunning()) return
        val sid = bumpSession("channel:$newChannelId")
        resetStreamingState()
        clearCaptions()
        PcmCaptureHub.flushQueue()
        Log.i(TAG, "channelChanged session=$sid channel=$newChannelId")
    }

    fun setSourceLanguage(code: String) {
        val next = code.lowercase().take(2)
        if (next !in SUPPORTED_LANGS) return
        val changed = next != sourceLanguage
        sourceLanguage = next
        if (!isRunning()) return
        if (!changed) return
        val sid = bumpSession("lang:$next")
        resetStreamingState()
        clearCaptions()
        PcmCaptureHub.flushQueue()
        val scope = pipelineScope ?: return
        scope.launch(Dispatchers.IO) {
            _status.value = "Switching to ${next.uppercase()}→EN…"
            runCatching { translator.ensureReady(next) }
                .onFailure { t ->
                    Log.e(TAG, "lang switch failed session=$sid lang=$next", t)
                    if (stillCurrent(sid)) _status.value = "Translation model failed"
                    return@launch
                }
            if (stillCurrent(sid)) {
                _status.value = "Live ${next.uppercase()}→EN"
                Log.i(TAG, "langReady session=$sid lang=$next")
            }
        }
    }

    fun clearCaptions() {
        _englishCaption.value = null
        _sourceCaption.value = null
        lastCaptionAtMs = 0L
    }

    private fun resetStreamingState() {
        lastSourceText = ""
        lastTranslatedSource = ""
        liveSilentSinceMs.set(0)
        inferJob?.cancel()
        inferJob = null
    }

    private fun bumpSession(reason: String): Long {
        val sid = sessionGen.incrementAndGet()
        Log.i(TAG, "session=$sid reason=$reason lang=$sourceLanguage channel=$channelId")
        return sid
    }

    private fun stillCurrent(sid: Long): Boolean = sessionGen.get() == sid

    private fun logInputFormatOnce() {
        val fmt = PcmCaptureHub.currentFormat()
        if (fmt.sampleRateHz <= 0) return
        val key = "${fmt.sampleRateHz}/${fmt.channelCount}/${fmt.encoding}"
        if (key == loggedInputFormat) return
        loggedInputFormat = key
        Log.i(
            TAG,
            "inputPcm sampleRate=${fmt.sampleRateHz} channels=${fmt.channelCount} encoding=${fmt.encoding}",
        )
    }

    private fun updateLiveSilence(buffer: ArrayList<Float>, sid: Long) {
        if (buffer.isEmpty()) return
        val liveRms = rmsTail(buffer, LIVE_VAD_SAMPLES)
        val now = System.currentTimeMillis()
        // Clear when energy falls toward floor — not the higher "start Whisper" bar.
        if (liveRms < LIVE_SILENCE_RMS_THRESHOLD) {
            val started = liveSilentSinceMs.get()
            if (started == 0L) {
                liveSilentSinceMs.set(now)
            } else if (now - started >= LIVE_SILENCE_CLEAR_MS) {
                if (_englishCaption.value != null || _sourceCaption.value != null) {
                    Log.i(
                        TAG,
                        "liveSilenceClear session=$sid silentForMs=${now - started} rms=${"%.5f".format(liveRms)}",
                    )
                    clearCaptions()
                    lastSourceText = ""
                    lastTranslatedSource = ""
                }
            }
        } else {
            liveSilentSinceMs.set(0)
        }
    }

    private suspend fun runLoop(scope: CoroutineScope) {
        val buffer = ArrayList<Float>(WINDOW_SAMPLES + HOP_SAMPLES)
        var droppedSamples = 0L
        var sid = sessionGen.get()
        var lastStatusHeartbeat = 0L

        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val currentSid = sessionGen.get()
            if (currentSid != sid) {
                Log.i(TAG, "sessionRoll old=$sid new=$currentSid — flush local ASR buffer")
                sid = currentSid
                buffer.clear()
                resetStreamingState()
                continue
            }

            maybeHideStaleCaption()

            val chunk = PcmCaptureHub.pollChunk()
            if (chunk == null) {
                delay(8)
                // Even with no new chunks, expire captions if we already marked silence.
                updateLiveSilence(buffer, sid)
                continue
            }
            logInputFormatOnce()
            val fmt = PcmCaptureHub.currentFormat()
            PcmAudioNormalizer.appendNormalized(chunk, fmt, buffer)

            val maxBuffered = WINDOW_SAMPLES + HOP_SAMPLES
            if (buffer.size > maxBuffered) {
                val drop = buffer.size - WINDOW_SAMPLES
                repeat(drop) { buffer.removeAt(0) }
                droppedSamples += drop
                Log.w(
                    TAG,
                    "backlogTrim session=$sid dropSamples=$drop " +
                        "droppedTotal=$droppedSamples hubDropped=${PcmCaptureHub.snapshot().droppedBuffers}",
                )
            }

            updateLiveSilence(buffer, sid)

            // One Whisper at a time — keep polling live audio while it runs.
            if (inferJob?.isActive == true) {
                if (buffer.size > WINDOW_SAMPLES) {
                    val drop = buffer.size - WINDOW_SAMPLES
                    repeat(drop) { buffer.removeAt(0) }
                }
                continue
            }

            if (buffer.size < WINDOW_SAMPLES) continue

            val slice = FloatArray(WINDOW_SAMPLES)
            for (i in 0 until WINDOW_SAMPLES) slice[i] = buffer[i]
            val windowEndWallMs = System.currentTimeMillis()
            val remove = minOf(HOP_SAMPLES, buffer.size)
            repeat(remove) { buffer.removeAt(0) }

            val energy = rms(slice)
            if (energy < SPEECH_RMS_THRESHOLD) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "vadSkipSpeech session=$sid rms=${"%.5f".format(energy)}")
                }
                continue
            }

            val captureSid = sid
            val lang = sourceLanguage
            val audioSec = WINDOW_SAMPLES.toDouble() / PcmAudioNormalizer.TARGET_RATE

            inferJob =
                scope.launch(Dispatchers.Default) {
                    val tAsr0 = System.nanoTime()
                    val rawSource =
                        runCatching { recognizer.transcribe(slice, lang) }
                            .getOrElse { t ->
                                Log.e(TAG, "whisper failed session=$captureSid", t)
                                ""
                            }
                    val asrMs = (System.nanoTime() - tAsr0) / 1_000_000.0
                    if (!stillCurrent(captureSid)) {
                        Log.i(TAG, "dropStaleAsr session=$captureSid current=${sessionGen.get()}")
                        return@launch
                    }

                    val ageMs = System.currentTimeMillis() - windowEndWallMs
                    val rtf = asrMs / (audioSec * 1000.0)
                    val snap = PcmCaptureHub.snapshot()
                    Log.i(
                        TAG,
                        "whisperMs=${"%.0f".format(asrMs)} audioSec=${"%.2f".format(audioSec)} " +
                            "rtf=${"%.2f".format(rtf)} resultAgeMs=$ageMs queueDepth=${snap.queueDepth} " +
                            "hubDropped=${snap.droppedBuffers} session=$captureSid lang=$lang",
                    )

                    val silentSince = liveSilentSinceMs.get()
                    if (shouldDropForLiveSilence(windowEndWallMs, silentSince)) {
                        Log.i(
                            TAG,
                            "dropAfterSilence session=$captureSid windowEnd=$windowEndWallMs " +
                                "silentSince=$silentSince ageMs=$ageMs " +
                                "silentForMs=${System.currentTimeMillis() - silentSince}",
                        )
                        return@launch
                    }

                    val (deduped, dedupeNote) = dedupeOverlapDetailed(lastSourceText, rawSource)
                    val source = collapseRepetitions(deduped)
                    Log.i(
                        TAG,
                        "dedupe session=$captureSid note=$dedupeNote rawChars=${rawSource.length} " +
                            "outChars=${source.length} collapsed=${source != deduped}",
                    )
                    if (source.isBlank() || isNoiseOrEmpty(source) || isWeakSource(source)) {
                        if (source.isNotBlank()) {
                            Log.i(
                                TAG,
                                "skipWeak session=$captureSid text=\"${source.take(48)}\" " +
                                    "noise=${isNoiseOrEmpty(source)} weak=${isWeakSource(source)}",
                            )
                        }
                        return@launch
                    }

                    // Re-check silence after dedupe — only if pause is long (not slow-speech gaps).
                    if (shouldDropForLiveSilence(windowEndWallMs, liveSilentSinceMs.get())) {
                        Log.i(TAG, "dropAfterSilencePreMt session=$captureSid")
                        return@launch
                    }

                    lastSourceText = source
                    _sourceCaption.value = source

                    val normalized = normalizeForMt(source)
                    if (normalized == lastTranslatedSource) {
                        Log.i(TAG, "skipDupMt session=$captureSid")
                        return@launch
                    }

                    val tMt0 = System.nanoTime()
                    val english =
                        runCatching { translator.translate(source, lang) }
                            .getOrElse { t ->
                                Log.e(TAG, "translate failed session=$captureSid", t)
                                ""
                            }
                    val mtMs = (System.nanoTime() - tMt0) / 1_000_000.0
                    val totalAgeMs = System.currentTimeMillis() - windowEndWallMs
                    Log.i(
                        TAG,
                        "translateMs=${"%.0f".format(mtMs)} totalAgeMs=$totalAgeMs session=$captureSid " +
                            "lang=$lang src=\"${source.take(80)}\"",
                    )
                    if (!stillCurrent(captureSid)) return@launch

                    if (shouldDropForLiveSilence(windowEndWallMs, liveSilentSinceMs.get())) {
                        Log.i(
                            TAG,
                            "dropAfterSilencePostMt session=$captureSid ageMs=$totalAgeMs",
                        )
                        return@launch
                    }

                    if (english.isNotBlank() && !isNoiseOrEmpty(english)) {
                        val cleanEn = collapseRepetitions(english)
                        lastTranslatedSource = normalized
                        publishCaption(source, cleanEn, captureSid)
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastStatusHeartbeat > 5_000L) {
                        lastStatusHeartbeat = now
                        if (translator.preparingStatus.value == null) {
                            _status.value = "Live ${sourceLanguage.uppercase()}→EN"
                        }
                    }
                }
        }
    }

    private fun publishCaption(source: String, english: String?, sid: Long) {
        if (!stillCurrent(sid) || english.isNullOrBlank()) return
        val display = if (showSourceDebug) "$english\n[$source]" else english
        if (display == _englishCaption.value) {
            lastCaptionAtMs = System.currentTimeMillis()
            return
        }
        _englishCaption.value = display
        lastCaptionAtMs = System.currentTimeMillis()
    }

    private fun maybeHideStaleCaption() {
        val last = lastCaptionAtMs
        if (last == 0L) return
        if (System.currentTimeMillis() - last < STALE_CAPTION_MS) return
        if (_englishCaption.value != null || _sourceCaption.value != null) {
            Log.i(TAG, "staleHide session=${sessionGen.get()}")
            clearCaptions()
        }
    }

    companion object {
        private const val TAG = "LiveTranslate"
        val SUPPORTED_LANGS = setOf("es", "pt", "fr")

        /** ~2 s Whisper window — shorter = less Shield lag. */
        const val WINDOW_SAMPLES = PcmAudioNormalizer.TARGET_RATE * 2
        /** Hop 1 s → 1 s overlap. */
        const val HOP_SAMPLES = PcmAudioNormalizer.TARGET_RATE * 1
        /** Must clear this bar to spend Whisper cycles. */
        const val SPEECH_RMS_THRESHOLD = 0.014f
        /** Below this → live silence clock for clear / late-drop. */
        const val LIVE_SILENCE_RMS_THRESHOLD = 0.010f
        /** @deprecated alias. */
        const val VAD_RMS_THRESHOLD = SPEECH_RMS_THRESHOLD
        /** ~250 ms of newest audio for live silence tracking. */
        const val LIVE_VAD_SAMPLES = PcmAudioNormalizer.TARGET_RATE / 4
        /**
         * Clear on-screen caption only after a real pause — slow narrators
         * leave gaps between sentences; 700ms was wiping everything.
         */
        const val LIVE_SILENCE_CLEAR_MS = 2_200L
        /**
         * Drop a late ASR result only if live has been quiet this long.
         * Shorter dips = breathing room while Whisper (~6s) finishes.
         */
        const val DROP_AFTER_SILENCE_HOLD_MS = 2_500L
        /**
         * Keep last caption up through a full Whisper cycle (~7s on Shield).
         * Live silence clear still wipes when dialog really stops.
         */
        const val STALE_CAPTION_MS = 10_000L

        /**
         * True when live energy has been below the silence floor long enough
         * that a delayed caption would look like a ghost line.
         */
        fun shouldDropForLiveSilence(windowEndWallMs: Long, silentSinceMs: Long): Boolean {
            if (silentSinceMs <= 0L) return false
            if (silentSinceMs < windowEndWallMs) return false
            val silentFor = System.currentTimeMillis() - silentSinceMs
            return silentFor >= DROP_AFTER_SILENCE_HOLD_MS
        }

        fun rms(samples: FloatArray): Float {
            if (samples.isEmpty()) return 0f
            var sum = 0.0
            for (s in samples) sum += (s * s).toDouble()
            return sqrt(sum / samples.size).toFloat()
        }

        fun rmsTail(buffer: List<Float>, maxSamples: Int): Float {
            if (buffer.isEmpty()) return 0f
            val n = minOf(maxSamples, buffer.size)
            val start = buffer.size - n
            var sum = 0.0
            for (i in start until buffer.size) {
                val s = buffer[i]
                sum += (s * s).toDouble()
            }
            return sqrt(sum / n).toFloat()
        }

        fun normalizeForMt(text: String): String =
            text.trim().lowercase().replace(Regex("\\s+"), " ")

        /**
         * Drop only clear ASR junk before MT. Do not apply to English captions —
         * short translations like "You can stay." are valid.
         */
        fun isWeakSource(text: String): Boolean {
            val t = text.trim()
            if (t.isEmpty()) return true
            val letters = t.count { it.isLetter() }
            // Allow short talk ("é isso.") — block single crumbs ("a").
            if (letters < 5) return true
            val words = t.split(Regex("\\s+")).filter { w -> w.any { it.isLetter() } }
            if (words.isEmpty()) return true
            val stripped =
                normalizeForMt(t)
                    .replace("...", "")
                    .replace("…", "")
                    .replace(Regex("""[^a-zà-ÿ0-9\s]"""), "")
                    .trim()
            if (stripped.length < 4) return true
            return false
        }

        /** @deprecated name — use [isWeakSource]. */
        fun isWeakTranscript(text: String): Boolean = isWeakSource(text)
        /**
         * Whisper-tiny often loops a short phrase ("Ça va ! - Ça va ! - …").
         * Collapse before MT so English captions don't spam the same line.
         */
        fun collapseRepetitions(text: String): String {
            var t = text.trim()
            if (t.isEmpty()) return t

            // "X - X - X" / em-dash separators
            val dashParts =
                t.split(Regex("""\s*[-–—]\s*"""))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            if (dashParts.size >= 2) {
                val norms = dashParts.map { normalizeForMt(it.trimEnd('!', '?', '.', ',', ';', ':')) }
                if (norms.all { it == norms.first() && it.isNotEmpty() }) {
                    return dashParts.first().trim()
                }
                val collapsed = ArrayList<String>()
                for (p in dashParts) {
                    if (collapsed.isEmpty() ||
                        normalizeForMt(collapsed.last()) != normalizeForMt(p)
                    ) {
                        collapsed.add(p)
                    }
                }
                t = collapsed.joinToString(" — ")
            }

            // Consecutive duplicate words: "oui oui oui" → "oui"
            val words = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isNotEmpty()) {
                val out = ArrayList<String>()
                for (w in words) {
                    if (out.isEmpty() || !out.last().equals(w, ignoreCase = true)) out.add(w)
                }
                t = out.joinToString(" ")
            }

            // Whole-string n-gram loop: (phrase)×N
            val tokens = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
            for (n in 1..minOf(8, tokens.size / 2)) {
                if (tokens.size < n * 2) continue
                val pattern = tokens.take(n)
                var i = 0
                var reps = 0
                while (i + n <= tokens.size) {
                    val slice = tokens.subList(i, i + n)
                    val same =
                        slice.indices.all { idx ->
                            slice[idx].equals(pattern[idx], ignoreCase = true)
                        }
                    if (!same) break
                    reps++
                    i += n
                }
                if (reps >= 2 && i == tokens.size) {
                    t = pattern.joinToString(" ")
                    break
                }
            }

            // Stutter letters: "est-t-t-t" → "est"
            t = t.replace(Regex("""([A-Za-zÀ-ÿ])(?:-\1){2,}"""), "$1")

            return t.trim()
        }

        fun isNoiseOrEmpty(text: String): Boolean {
            val t = text.trim()
            if (t.length <= 1) return true
            val lower = normalizeForMt(t)
                .replace(Regex("""^\[+|\]+$"""), "")
                .replace(Regex("""^\(+|\)+$"""), "")
                .trim()
            if (lower in SILENCE_TOKENS) return true
            if (MUSIC_ONLY.matches(lower)) return true
            return false
        }

        private val MUSIC_ONLY =
            Regex("""^(♪+|music|música|musica|musique)(\s+playing)?$""", RegexOption.IGNORE_CASE)

        private val SILENCE_TOKENS =
            setOf(
                ".",
                "...",
                "…",
                "thank you",
                "thanks for watching",
                "subtitles by",
                "music",
                "música",
                "musica",
                "musique",
                "[music]",
                "(music)",
                "(música)",
                "[música]",
                "♪",
                "♪♪",
                "applause",
                "aplausos",
                "applaudissements",
            )

        fun dedupeOverlap(previous: String, next: String): String =
            dedupeOverlapDetailed(previous, next).first

        fun dedupeOverlapDetailed(previous: String, next: String): Pair<String, String> {
            val a = previous.trim()
            val b = next.trim()
            if (b.isEmpty()) return "" to "empty"
            if (a.isEmpty()) return b to "first"
            if (b.equals(a, ignoreCase = true)) return "" to "exactDup"
            if (b.startsWith(a)) {
                return b.removePrefix(a).trim() to "prefixStrip"
            }
            val aTokens = a.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val bTokens = b.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val max = minOf(aTokens.size, bTokens.size, 12)
            var overlap = 0
            for (n in max downTo 1) {
                val left = aTokens.takeLast(n).map { it.lowercase() }
                val right = bTokens.take(n).map { it.lowercase() }
                if (left == right) {
                    overlap = n
                    break
                }
            }
            return if (overlap > 0) {
                bTokens.drop(overlap).joinToString(" ") to "tokenOverlap=$overlap"
            } else {
                b to "noOverlap"
            }
        }
    }
}
