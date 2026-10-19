package com.atmytrack.app

import com.atmytrack.app.ui.channelColor
import org.junit.Assert.*
import org.junit.Test

class ConsoleColorsTest {
    @Test fun instrumentsAcceptPortugueseEnglishAndAccents() {
        for ((a,b) in listOf("BATERIA 01" to "Drums", "Baixo" to "Bass DI", "Violão" to "Acoustic guitar", "Teclado" to "Keys", "Voz" to "Lead vocals", "Percussão" to "Percussion")) {
            assertEquals("$a / $b", channelColor(a), channelColor(b))
        }
        assertEquals(6, listOf("Bateria", "Baixo", "Guitarra", "Piano", "Voz", "Percussão").map(::channelColor).toSet().size)
    }
    @Test fun unnamedInstrumentColorIsIndependentOfOrderAndLocale() {
        val names=listOf("Track 1", "ÁUDIO I", "Track 2", "Aux")
        val before=names.associateWith(::channelColor)
        assertEquals(before, names.reversed().associateWith(::channelColor))
        assertEquals(channelColor("ÁUDIO I"), channelColor("audio i"))
    }
}
