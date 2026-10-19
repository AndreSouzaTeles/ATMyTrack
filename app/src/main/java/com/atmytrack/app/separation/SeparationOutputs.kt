package com.atmytrack.app.separation

import java.io.File

object SeparationOutputs {
    // Ordering is part of the pinned ONNX model contract, not inferred from file names.
    val current=listOf("Bateria","Baixo","Outros","Voz","Guitarra","Piano")
    fun names(dir:File):List<String> {
        val names=if(File(dir,"complete").readLines().getOrNull(1)?.startsWith("htdemucs-6s")==true)current else listOf("Voz","Instrumental")
        require(names.all { File(dir,"$it.wav").isFile }) { "Resultado incompleto." }
        return names
    }
}
