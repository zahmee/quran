package com.mushaf.reader.data.backup

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Turns the name a reader picks into the sign-in identity Firebase understands.
 *
 * Firebase's password sign-in is keyed by e-mail address, and this app asks for none. So the name
 * is folded to a canonical form and hashed into the local part of an address on a reserved domain
 * that can never receive mail. Hashing also keeps Arabic names inside the ASCII an address allows.
 *
 * The folding is frozen: an account is found again only when today's typing folds to the key it
 * was created under. Never change [canonical] for existing users; add a new scheme beside it.
 */
object CloudUsername {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 24
    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 128

    /** RFC 2606 reserves example.com: no one can register it, and it accepts no mail. */
    private const val DOMAIN = "natlu.example.com"
    private const val SEPARATORS = "_.-"

    /** What is wrong with [name], in Arabic for the form, or null when it can be used. */
    fun problemWith(name: String): String? {
        val trimmed = name.trim()
        val length = trimmed.codePointCount(0, trimmed.length)
        return when {
            length < MIN_LENGTH -> "اكتب اسماً من ${arabicDigits(MIN_LENGTH)} أحرف على الأقل."
            length > MAX_LENGTH -> "الاسم أطول من ${arabicDigits(MAX_LENGTH)} حرفاً."
            trimmed.any { it.isWhitespace() } -> "لا يقبل الاسم المسافات؛ استعمل _ أو - بدلاً منها."
            !trimmed.all { it.isLetterOrDigit() || it in SEPARATORS || isMark(it) } ->
                "يقبل الاسم الحروف والأرقام و _ . - فقط."
            trimmed.none { it.isLetter() } -> "يجب أن يحتوي الاسم على حرف واحد على الأقل."
            else -> null
        }
    }

    fun problemWithPassword(password: String): String? = when {
        password.length < MIN_PASSWORD -> "كلمة المرور قصيرة؛ اكتب ${arabicDigits(MIN_PASSWORD)} أحرف على الأقل."
        password.length > MAX_PASSWORD -> "كلمة المرور أطول من الحد المسموح."
        else -> null
    }

    /**
     * The form two spellings of the same name share: "أحمد" and "احمد", "Ahmed" and "ahmed",
     * "١٢٣" and "123", with or without harakat or tatweel.
     */
    fun canonical(name: String): String {
        val folded = StringBuilder()
        for (char in Normalizer.normalize(name.trim(), Normalizer.Form.NFKC)) {
            when (char) {
                in '٠'..'٩' -> folded.append('0' + (char - '٠'))
                in '۰'..'۹' -> folded.append('0' + (char - '۰'))
                'أ', 'إ', 'آ', 'ٱ' -> folded.append('ا')
                'ى', 'ئ' -> folded.append('ي')
                'ؤ' -> folded.append('و')
                'ة' -> folded.append('ه')
                'ـ' -> Unit
                else -> if (!isMark(char)) folded.append(char)
            }
        }
        return folded.toString().lowercase(Locale.ROOT)
    }

    fun email(name: String): String = sha256Hex(canonical(name)).take(40) + "@" + DOMAIN

    private fun arabicDigits(value: Int): String =
        value.toString().map { '\u0660' + (it - '0') }.joinToString("")

    private fun isMark(char: Char): Boolean =
        Character.getType(char) == Character.NON_SPACING_MARK.toInt()

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
