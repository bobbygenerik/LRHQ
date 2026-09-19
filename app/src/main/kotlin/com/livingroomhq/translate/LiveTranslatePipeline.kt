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
 * PCM consumer never blocks on Whisper. While ASR runs, audio keeps accumulating
 * (up to a catch-up window) and is processed FIFO when free — not thrown away at 3 s.
 * Live energy clears captions when dialog stops; late ASR/MT results from before
 * silence are discarded (timing, not hallucination).
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
                    // Start PCM while translator packs load so first window is warm.
                    PcmCaptureHub.startCapture()
                    logInputFormatOnce()
                    _status.value = "Loading translator (${sourceLanguage.uppercase()}→EN)…"
                    runCatching { translator.ensureReady(sourceLanguage) }
                        .onFailure { t ->
                            Log.e(TAG, "translator init failed session=${sessionGen.get()}", t)
                            _status.value = "Translation model failed — change language to retry"
                            return@launch
                        }
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
        var lastCatchUpLogMs = 0L

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

            // Drain hub in bursts so ExoPlayer chunks don't pile up / get dropped.
            val fmt = PcmCaptureHub.currentFormat()
            var drained = 0
            while (drained < MAX_DRAIN_CHUNKS_PER_TICK) {
                val chunk = PcmCaptureHub.pollChunk() ?: break
                if (drained == 0) logInputFormatOnce()
                PcmAudioNormalizer.appendNormalized(chunk, fmt, buffer)
                drained++
            }
            if (drained == 0) {
                delay(8)
                updateLiveSilence(buffer, sid)
                // Still try to start Whisper on whatever is already buffered.
            }

            if (buffer.size > MAX_ACCUMULATION_SAMPLES) {
                val drop = buffer.size - MAX_ACCUMULATION_SAMPLES
                dropFront(buffer, drop)
                droppedSamples += drop
                Log.w(
                    TAG,
                    "backlogTrim session=$sid dropSamples=$drop dropSec=${"%.2f".format(drop.toDouble() / PcmAudioNormalizer.TARGET_RATE)} " +
                        "droppedTotal=$droppedSamples hubDropped=${PcmCaptureHub.snapshot().droppedBuffers}",
                )
            }

            updateLiveSilence(buffer, sid)

            val bufferSec = buffer.size.toDouble() / PcmAudioNormalizer.TARGET_RATE
            val catchingUp = bufferSec >= CATCHUP_BUFFER_SEC
            val nowWall = System.currentTimeMillis()
            if (catchingUp && nowWall - lastCatchUpLogMs > 2_000L) {
                lastCatchUpLogMs = nowWall
                val snap = PcmCaptureHub.snapshot()
                Log.i(
                    TAG,
                    "catchUp session=$sid bufferSec=${"%.2f".format(bufferSec)} " +
                        "hubDepth=${snap.queueDepth} hubDropped=${snap.droppedBuffers} " +
                        "inferBusy=${inferJob?.isActive == true}",
                )
            }

            // One Whisper at a time — keep draining live audio into buffer while it runs.
            if (inferJob?.isActive == true) {
                continue
            }

            if (buffer.size < WINDOW_MIN_SAMPLES) continue

            // Live-edge: caption "now", not FIFO through a deep backlog (felt like 10s blank).
            val keepAfter = WINDOW_MAX_SAMPLES + OVERLAP_SAMPLES
            if (buffer.size > keepAfter + LIVE_EDGE_PAD_SAMPLES) {
                val drop = buffer.size - keepAfter
                dropFront(buffer, drop)
                droppedSamples += drop
                Log.i(
                    TAG,
                    "liveEdgeSnap session=$sid dropSec=${"%.2f".format(drop.toDouble() / PcmAudioNormalizer.TARGET_RATE)} " +
                        "keepSec=${"%.2f".format(keepAfter.toDouble() / PcmAudioNormalizer.TARGET_RATE)}",
                )
            }

            val sliceSize = minOf(buffer.size, WINDOW_MAX_SAMPLES)
            val sliceStart = buffer.size - sliceSize
            val slice = FloatArray(sliceSize)
            for (i in 0 until sliceSize) slice[i] = buffer[sliceStart + i]
            val windowEndWallMs = System.currentTimeMillis()

            // After taking newest window, keep only overlap tail for continuity.
            val overlap = OVERLAP_SAMPLES
            val remove = maxOf(1, buffer.size - overlap)
            dropFront(buffer, remove)

            val energy = rms(slice)
            val peakFrameRms = maxFrameRms(slice, LIVE_VAD_SAMPLES)
            if (energy < SPEECH_RMS_THRESHOLD && peakFrameRms < SPEECH_PEAK_RMS_THRESHOLD) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "vadSkipSpeech session=$sid rms=${"%.5f".format(energy)} peak=${"%.5f".format(peakFrameRms)}")
                }
                continue
            }

            val captureSid = sid
            val lang = sourceLanguage
            val audioSec = sliceSize.toDouble() / PcmAudioNormalizer.TARGET_RATE

            inferJob =
                scope.launch(Dispatchers.Default) {
                    val tAsr0 = System.nanoTime()
                    val asr =
                        runCatching { recognizer.transcribe(slice, lang) }
                            .getOrElse { t ->
                                Log.e(TAG, "whisper failed session=$captureSid", t)
                                AsrResult("")
                            }
                    val rawSource = asr.text
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
                            "hubDropped=${snap.droppedBuffers} noSpeech=${"%.2f".format(asr.maxNoSpeechProb)} " +
                            "session=$captureSid lang=$lang",
                    )

                    if (asr.maxNoSpeechProb >= NO_SPEECH_PROB_REJECT) {
                        Log.i(
                            TAG,
                            "skipNoSpeech session=$captureSid p=${"%.2f".format(asr.maxNoSpeechProb)} " +
                                "text=\"${rawSource.take(48)}\"",
                        )
                        return@launch
                    }

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
                    if (isHallucinatedLoop(rawSource, source)) {
                        Log.i(
                            TAG,
                            "skipHallucLoop session=$captureSid rawChars=${rawSource.length} outChars=${source.length}",
                        )
                        return@launch
                    }
                    if (source.isBlank() || isNoiseOrEmpty(source) || isWeakSource(source) || isSfxLabel(source)) {
                        if (source.isNotBlank()) {
                            Log.i(
                                TAG,
                                "skipWeak session=$captureSid text=\"${source.take(48)}\" " +
                                    "noise=${isNoiseOrEmpty(source)} weak=${isWeakSource(source)} " +
                                    "sfx=${isSfxLabel(source)}",
                            )
                        }
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

                    val cleanEn = if (english.isNotBlank() && !isNoiseOrEmpty(english)) {
                        collapseRepetitions(english)
                    } else {
                        ""
                    }

                    if (cleanEn.isNotBlank()) {
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

        /** ~2 s min audio window. */
        const val WINDOW_MIN_SAMPLES = PcmAudioNormalizer.TARGET_RATE * 2
        /** Dynamic max window ~2 s for fast Shield turnaround. */
        const val WINDOW_MAX_SAMPLES = PcmAudioNormalizer.TARGET_RATE * 2
        /**
         * Hold up to ~6 s while Whisper runs (Vulkan ~0.5 s → small growth).
         * Live-edge snap keeps captions on "now", not a deep FIFO.
         */
        const val MAX_ACCUMULATION_SAMPLES = PcmAudioNormalizer.TARGET_RATE * 6
        /** Extra samples beyond window+overlap before liveEdgeSnap. */
        const val LIVE_EDGE_PAD_SAMPLES = PcmAudioNormalizer.TARGET_RATE / 2
        /** Log catch-up depth (should stay low with live-edge). */
        const val CATCHUP_BUFFER_SEC = 4.0
        /** Overlap window ~0.5 s. */
        const val OVERLAP_SAMPLES = PcmAudioNormalizer.TARGET_RATE / 2
        /** Poll this many hub chunks per loop tick before Whisper work. */
        const val MAX_DRAIN_CHUNKS_PER_TICK = 48
        /** Drop ASR when whisper says segment is mostly non-speech. */
        const val NO_SPEECH_PROB_REJECT = 0.55f

        /** Deprecated window constant alias. */
        const val WINDOW_SAMPLES = WINDOW_MIN_SAMPLES
        /** Hop 1 s → 1 s overlap. */
        const val HOP_SAMPLES = PcmAudioNormalizer.TARGET_RATE * 1
        /** Must clear this bar to spend Whisper cycles. */
        const val SPEECH_RMS_THRESHOLD = 0.008f
        /** Peak 250ms frame energy threshold. */
        const val SPEECH_PEAK_RMS_THRESHOLD = 0.012f
        /** Below this → live silence clock for clear / late-drop. */
        const val LIVE_SILENCE_RMS_THRESHOLD = 0.005f
        /** @deprecated alias. */
        const val VAD_RMS_THRESHOLD = SPEECH_RMS_THRESHOLD
        /** ~250 ms of newest audio for live silence tracking. */
        const val LIVE_VAD_SAMPLES = PcmAudioNormalizer.TARGET_RATE / 4
        /** Clear on-screen caption after real pause. */
        const val LIVE_SILENCE_CLEAR_MS = 2_200L
        /** Drop a late ASR result only if live has been quiet this long. */
        const val DROP_AFTER_SILENCE_HOLD_MS = 10_000L
        /** Keep last caption up for 4.5s max so lines do not linger. */
        const val STALE_CAPTION_MS = 4_500L

        /** O(n) once — never [repeat] [removeAt](0) on large PCM buffers. */
        fun dropFront(buffer: ArrayList<Float>, n: Int) {
            if (n <= 0) return
            if (n >= buffer.size) {
                buffer.clear()
                return
            }
            buffer.subList(0, n).clear()
        }

        /** Tiny looped Whisper garbage: huge raw → tiny collapsed. */
        fun isHallucinatedLoop(raw: String, collapsed: String): Boolean {
            if (raw.length < 80) return false
            if (collapsed.length >= 24) return false
            return collapsed.length * 8 <= raw.length
        }

        /** Bracket/paren SFX labels Whisper invents: (risos), [campainha], ♪… */
        fun isSfxLabel(text: String): Boolean {
            val t = text.trim()
            if (t.isEmpty()) return true
            if (BRACKET_ONLY.matches(t)) return true
            val lower = normalizeForMt(t)
            if (SFX_TOKENS.any { lower == it || lower.contains(it) }) return true
            return false
        }

        private val BRACKET_ONLY =
            Regex("""^[\[(【（].+[\]】）)]$""")

        private val SFX_TOKENS =
            setOf(
                "risos",
                "risas",
                "rire",
                "rires",
                "laughter",
                "laughing",
                "campainha",
                "timbre",
                "doorbell",
                "aplausos",
                "applaudissements",
                "applause",
                "inaudible",
                "inaudível",
                "music playing",
            )

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

        fun maxFrameRms(samples: FloatArray, frameSize: Int): Float {
            if (samples.isEmpty()) return 0f
            var maxRms = 0f
            var i = 0
            while (i < samples.size) {
                val end = minOf(i + frameSize, samples.size)
                val len = end - i
                var sum = 0.0
                for (j in i until end) {
                    val s = samples[j]
                    sum += (s * s).toDouble()
                }
                val r = sqrt(sum / len).toFloat()
                if (r > maxRms) maxRms = r
                i += frameSize
            }
            return maxRms
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
            val words = t.split(Regex("\\s+")).filter { w -> w.any { it.isLetter() } }
            if (words.isEmpty()) return true
            val letterCount = t.count { it.isLetter() }
            // Crumbs like "Não," — need substance. Allow short punchy "Sim!" / "Não?"
            val punchy = t.endsWith("!") || t.endsWith("?")
            if (letterCount < 3) return true
            if (!punchy && letterCount < 4) return true
            if (words.size == 1 && !punchy && letterCount < 6) return true
            val stripped =
                normalizeForMt(t)
                    .replace("...", "")
                    .replace("…", "")
                    .replace(Regex("""[^a-zà-ÿ0-9\s]"""), "")
                    .trim()
            if (stripped.isEmpty()) return true
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
            var tokens = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isNotEmpty()) {
                val out = ArrayList<String>()
                for (w in tokens) {
                    if (out.isEmpty() || !out.last().equals(w, ignoreCase = true)) out.add(w)
                }
                tokens = out
                t = tokens.joinToString(" ")
            }

            // N-gram phrase loop collapse (lengths 1..8) anywhere in sequence
            if (tokens.size >= 2) {
                var changed = true
                var passCount = 0
                while (changed && passCount < 4) {
                    changed = false
                    passCount++
                    val maxN = minOf(8, tokens.size / 2)
                    for (n in 1..maxN) {
                        var i = 0
                        val newTokens = ArrayList<String>()
                        while (i < tokens.size) {
                            if (i + 2 * n <= tokens.size) {
                                val p1 = tokens.subList(i, i + n)
                                val p2 = tokens.subList(i + n, i + 2 * n)
                                val p1Norm = p1.map { normalizeForMt(it.trimEnd('!', '?', '.', ',', ';', ':')) }
                                val p2Norm = p2.map { normalizeForMt(it.trimEnd('!', '?', '.', ',', ';', ':')) }
                                if (p1Norm == p2Norm) {
                                    newTokens.addAll(p1)
                                    i += 2 * n
                                    while (i + n <= tokens.size) {
                                        val pNext = tokens.subList(i, i + n).map { normalizeForMt(it.trimEnd('!', '?', '.', ',', ';', ':')) }
                                        if (pNext == p1Norm) i += n else break
                                    }
                                    changed = true
                                    continue
                                }
                            }
                            newTokens.add(tokens[i])
                            i++
                        }
                        if (changed) {
                            tokens = newTokens
                            t = tokens.joinToString(" ")
                            break
                        }
                    }
                }
            }

            // Stutter letters: "est-t-t-t" → "est"
            t = t.replace(Regex("""([A-Za-zÀ-ÿ])(?:-\1){2,}"""), "$1")

            return t.trim()
        }

        fun isNoiseOrEmpty(text: String): Boolean {
            val t = text.trim()
            if (t.length <= 1) return true
            if (isSfxLabel(t)) return true
            val lower = normalizeForMt(t)
                .replace(Regex("""^\[+|\]+$"""), "")
                .replace(Regex("""^\(+|\)+$"""), "")
                .trim()
            if (lower in SILENCE_TOKENS) return true
            if (SILENCE_PATTERNS.any { lower.contains(it) }) return true
            if (MUSIC_ONLY.matches(lower)) return true
            return false
        }

        private val MUSIC_ONLY =
            Regex("""^(♪+|music|música|musica|musique)(\s+playing)?$""", RegexOption.IGNORE_CASE)

        private val SILENCE_PATTERNS =
            listOf(
                "amara.org",
                "opensubtitles",
                "sous-titres réalisés par",
                "subtítulos por amara",
                "legendas por amara",
                "subtitles by",
                "translated by",
                "transcription by",
            )

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
