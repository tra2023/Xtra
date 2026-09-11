package com.github.andreyasadchy.xtra.repository

fun interface ImageFileDeleter {
    fun delete(path: String)
}
