package com.livingroomhq.translate

/** Preserve explicitly introduced names rather than translating them as common nouns. */
internal object TranslationNames {
    private val introducedName = Regex(
        """\b(?i:se chama|chama-se|chamado|chamada|nome é|se llama|llamado|llamada|s'appelle|appelé|appelée)\s+["“'«]?([A-ZÀ-Þ][\p{L}\p{N}'-]*(?:\s+[A-ZÀ-Þ][\p{L}\p{N}'-]*){0,3})""",
    )

    data class Protected(val text: String, val names: Map<String, String>) {
        fun restore(translated: String): String? {
            var result = translated
            for ((marker, name) in names) {
                if (!result.contains(marker, ignoreCase = true)) return null
                result = result.replace(marker, name, ignoreCase = true)
            }
            return result
        }
    }

    fun protect(text: String): Protected {
        val names = linkedMapOf<String, String>()
        val output = introducedName.replace(text) { match ->
            val group = match.groups[1]!!
            var marker = "QZX${names.size}QZX"
            while (text.contains(marker, ignoreCase = true)) marker += "Z"
            names[marker] = group.value
            match.value.replaceRange(group.range.first - match.range.first, group.range.last - match.range.first + 1, marker)
        }
        return Protected(output, names)
    }
}
