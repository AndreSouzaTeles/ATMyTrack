package com.atmytrack.app

import java.io.File

/** Retires downloaded AI data while retaining audio already referenced by saved projects. */
object RetiredStorageCleanup {
    fun clean(filesDir:File, retained:Set<String>) {
        val appRoot=filesDir.canonicalFile
        for(name in listOf("models","separation")) {
            val root=File(appRoot,name)
            if(!root.exists() || root.canonicalFile.parentFile!=appRoot || root.canonicalFile!=root.absoluteFile)continue
            root.walkTopDown().onEnter { it.canonicalFile==it.absoluteFile }.toList().asReversed().forEach { entry ->
                val resolved=entry.canonicalFile
                if(resolved==entry.absoluteFile && resolved.toPath().startsWith(root.canonicalFile.toPath())) {
                    val relative=entry.relativeTo(appRoot).invariantSeparatorsPath
                    if(relative !in retained)entry.delete() // Nonempty folders containing retained audio remain.
                }
            }
        }
    }
}
