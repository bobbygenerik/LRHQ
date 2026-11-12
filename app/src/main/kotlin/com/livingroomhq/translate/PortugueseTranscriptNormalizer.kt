package com.livingroomhq.translate

/**
 * Normalizes European Portuguese ASR output prior to machine translation.
 * Addresses common phonetic confusions and question structures from Whisper on pt-PT TV speech.
 */
internal object PortugueseTranscriptNormalizer {
    private val tagQuestionRegex = Regex("""(?i)\b([a-zà-ÿ0-9\s]+?)\s+(certo|não é|pois não|pois é)\s*\??$""")
    private val verbalConnectivePorque = Regex("""(?i)\b(estou|estava|estavam|estávamos|estamos)\s+a\s+([a-zà-ÿ]+)\s+por\s+que\b""")
    private val connectivePorque = Regex("""(?i)\bpor\s+que\s+(precisava|queria|gostaria|posso|podia|preciso|havia|houve|foi|tinham|tinha)\b""")
    private val creditCardRegex = Regex("""(?i)\b(catão\s+creia|cartão\s+creta|cartão\s+de\s+crédit)\b""")
    private val directDebitRegex = Regex("""(?i)\bdébitos\s+direitos\b""")
    private val danceStepsRegex = Regex("""(?i)\bpaz\s+de\s+dança\b""")
    private val hotterConversationsRegex = Regex("""(?i)\bpaz\s+quentes\b""")
    private val bragancaRegex = Regex("""(?i)\bBrancança\b""")

    fun normalize(text: String): String {
        if (text.isBlank()) return text
        var result = text
        result = bragancaRegex.replace(result, "Bragança")
        result = creditCardRegex.replace(result, "cartão de crédito")
        result = directDebitRegex.replace(result, "débitos diretos")
        result = danceStepsRegex.replace(result, "passos de dança")
        result = hotterConversationsRegex.replace(result, "mais quentes")
        result = verbalConnectivePorque.replace(result, "$1 a $2 porque")
        result = connectivePorque.replace(result, "porque $1")
        result = tagQuestionRegex.replace(result, "$1, $2?")
        return result
    }
}
