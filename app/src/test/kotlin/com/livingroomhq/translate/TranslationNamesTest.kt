package com.livingroomhq.translate

import org.junit.Assert.*
import org.junit.Test

class TranslationNamesTest {
    @Test fun introducedBrandsKeepTheirSpokenNameRatherThanBecomingCommonNouns() {
        val protected = TranslationNames.protect("A outra cerveja se chama Manta.")
        assertEquals("A outra cerveja se chama QZX0QZX.", protected.text)
        assertEquals("The other beer is called Manta.", protected.restore("The other beer is called QZX0QZX."))
    }

    @Test fun ordinarySpeechAndLowercaseCommonNounsAreStillTranslated() {
        for (text in listOf("Feliz Natal.", "Eu uso uma manta.", "Ela é chamada professora.", "Bom dia, Maria.")) {
            assertEquals(text, TranslationNames.protect(text).text)
        }
    }

    @Test fun multipleNamesAccentsAndAllSupportedIntroducersRoundTrip() {
        for (text in listOf("Se chama São Paulo.", "Se llama María José.", "Il s'appelle Jean-Pierre.")) {
            val protected = TranslationNames.protect(text)
            assertTrue(protected.names.isNotEmpty())
            assertEquals(text, protected.restore(protected.text))
        }
        val protected = TranslationNames.protect("Se chama Manta. Se chama Maria.")
        assertEquals(2, protected.names.size)
        assertNull(protected.restore("The marker was lost."))
    }
}
