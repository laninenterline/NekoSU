package com.nekosu.domain.text

fun interface TextTransliterator {
    fun transliterate(value: String): String
}
