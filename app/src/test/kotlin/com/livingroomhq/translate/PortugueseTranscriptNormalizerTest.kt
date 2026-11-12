package com.livingroomhq.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class PortugueseTranscriptNormalizerTest {
    @Test
    fun testTagQuestionPunctuation() {
        assertEquals("Ele vai pagar isto, certo?", PortugueseTranscriptNormalizer.normalize("Ele vai pagar isto certo?"))
        assertEquals("Ele vai pagar isto, certo?", PortugueseTranscriptNormalizer.normalize("Ele vai pagar isto certo"))
        assertEquals("Ele vai pagar isto, certo?", PortugueseTranscriptNormalizer.normalize("Ele vai pagar isto, certo?"))
        assertEquals("Estás bem, não é?", PortugueseTranscriptNormalizer.normalize("Estás bem não é?"))
    }

    @Test
    fun testPorqueConnective() {
        assertEquals(
            "Bom dia, estou a ligar porque precisava de ver.",
            PortugueseTranscriptNormalizer.normalize("Bom dia, estou a ligar por que precisava de ver.")
        )
    }

    @Test
    fun testCreditCardAndDirectDebit() {
        assertEquals(
            "O meu cartão de crédito não funciona.",
            PortugueseTranscriptNormalizer.normalize("O meu cartão creta não funciona.")
        )
        assertEquals(
            "O meu cartão de crédito não funciona.",
            PortugueseTranscriptNormalizer.normalize("O meu catão creia não funciona.")
        )
        assertEquals(
            "O meu cartão de crédito não funciona.",
            PortugueseTranscriptNormalizer.normalize("O meu cartão de crédit não funciona.")
        )
        assertEquals(
            "aceder aos débitos diretos da minha conta",
            PortugueseTranscriptNormalizer.normalize("aceder aos débitos direitos da minha conta")
        )
    }

    @Test
    fun testPhoneticDanceStepsAndPlaceNames() {
        assertEquals(
            "passos de dança só para adultos",
            PortugueseTranscriptNormalizer.normalize("paz de dança só para adultos")
        )
        assertEquals(
            "conversas mais quentes",
            PortugueseTranscriptNormalizer.normalize("conversas paz quentes")
        )
        assertEquals(
            "Miranda do Corvo, Bragança, Oliveira",
            PortugueseTranscriptNormalizer.normalize("Miranda do Corvo, Brancança, Oliveira")
        )
    }
}
