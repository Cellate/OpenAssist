package com.openassist.app

object Translit {
    private val devanagariToLatin = mapOf(
        "अ" to "a", "आ" to "aa", "इ" to "i", "ई" to "ee", "उ" to "u", "ऊ" to "oo",
        "ऋ" to "ri", "ए" to "e", "ऐ" to "ai", "ओ" to "o", "औ" to "au",
        "क" to "k", "ख" to "kh", "ग" to "g", "घ" to "gh", "ङ" to "ng",
        "च" to "ch", "छ" to "chh", "ज" to "j", "झ" to "jh", "ञ" to "ny",
        "ट" to "t", "ठ" to "th", "ड" to "d", "ढ" to "dh", "ण" to "n",
        "त" to "t", "थ" to "th", "द" to "d", "ध" to "dh", "न" to "n",
        "प" to "p", "फ" to "f", "ब" to "b", "भ" to "bh", "म" to "m",
        "य" to "y", "र" to "r", "ल" to "l", "व" to "v", "श" to "sh",
        "ष" to "sh", "स" to "s", "ह" to "h",
        "ा" to "aa", "ि" to "i", "ी" to "ee", "ु" to "u", "ू" to "oo",
        "ृ" to "ri", "े" to "e", "ै" to "ai", "ो" to "o", "ौ" to "au",
        "ं" to "n", "ः" to "h", "्" to ""
    )

    fun isAlreadyLatin(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return true
        val latinCount = letters.count { it in 'a'..'z' || it in 'A'..'Z' }
        return latinCount.toFloat() / letters.length > 0.6f
    }

    fun toHinglish(hindi: String): String {
        if (isAlreadyLatin(hindi)) return hindi
        val sb = StringBuilder()
        var i = 0
        while (i < hindi.length) {
            val ch = hindi[i].toString()
            sb.append(devanagariToLatin[ch] ?: ch)
            i++
        }
        return sb.toString()
    }
}
