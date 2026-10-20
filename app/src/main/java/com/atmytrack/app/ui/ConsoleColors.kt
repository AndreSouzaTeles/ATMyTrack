package com.atmytrack.app.ui

import androidx.compose.ui.graphics.Color
import java.text.Normalizer
import java.util.Locale

internal val LockRed = Color(0xFFE05B60)
internal val Accent = Color(0xFF89ACF2)
internal val Mint = Color(0xFF88B9A0)
internal val Amber = Color(0xFFD0AD74)
internal val Coral = Color(0xFFDC938A)
internal val Violet = Color(0xFFB29CDA)
internal val Cyan = Color(0xFF81BDC8)

private fun normalized(name: String) = Normalizer.normalize(name, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

// Visual hints from file names only; never identifies or processes the audio.
internal fun channelColor(name: String): Color {
    val n = normalized(name)
    return when {
        n == "master" -> Accent
        listOf("bateria", "drum", "kick", "snare", "cymbal", "tom ").any(n::contains) -> Coral
        listOf("percuss", "perc", "shaker", "tambour").any(n::contains) -> Amber
        listOf("baixo", "bass").any(n::contains) -> Mint
        listOf("guit", "violao", "acoustic").any(n::contains) -> Color(0xFF83B4DB)
        listOf("piano", "tecla", "keys", "keyboard", "organ", "synth").any(n::contains) -> Violet
        listOf("voz", "vocal", "choir", "backing", "coro").any(n::contains) -> Cyan
        listOf("click", "guia", "guide", "metro").any(n::contains) -> Amber
        n.contains("pad") -> Violet
        else -> listOf(Cyan, Violet, Amber, Mint, Coral, Accent)[Math.floorMod(n.hashCode(), 6)]
    }
}

internal fun toolColor(label: String): Color = when(label) {
    "TOM" -> Accent
    "VELOCIDADE", "DCA" -> Amber
    "PAD", "BUS", "CROSSMUSIC" -> Violet
    "LOOP", "BOTH ALL" -> Mint
    "METRÔNOMO", "CLICK INTERNO" -> Coral
    "ROUTING", "STEREO SPLIT", "ATUALIZAR SAÍDA" -> Cyan
    else -> Accent
}
