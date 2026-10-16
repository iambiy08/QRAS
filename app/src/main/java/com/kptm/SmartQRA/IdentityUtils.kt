package com.kptm.SmartQRA

/**
 * Shared helpers so CreateEvent (writing invitedEmails) and ReportsActivity
 * (reading invitedEmails) normalise identifiers in exactly the same way.
 */
object IdentityUtils {

    // zero-width chars, BOM and non-breaking space (common in Excel / Google Sheets CSV exports)
    private val INVISIBLE = Regex("[\\u200B-\\u200D\\uFEFF\\u00A0]")
    private val EMAIL = Regex("[a-z0-9._%+\\-]+@[a-z0-9\\-]+(?:\\.[a-z0-9\\-]+)*\\.[a-z]{2,}")
    private val STUDENT_ID = Regex("(?:usr|adm)-[a-z0-9_\\-]+")

    /** trim + strip invisible chars + strip wrapping quotes + lowercase */
    fun clean(raw: String): String =
        raw.replace(INVISIBLE, " ")
            .trim()
            .trim('"', '\'', '`')
            .trim()
            .lowercase()

    /**
     * Returns a valid identifier (email or usr-/adm- ID) found in [raw], or null.
     * Handles values like "biytest4@gmail.com", Biytest <biytest4@gmail.com>, or a BOM-prefixed USR-35681.
     * Plain names and header words ("name", "email", "userid") return null.
     */
    fun extractIdentifier(raw: String): String? {
        val c = clean(raw)
        if (c.isEmpty()) return null
        EMAIL.find(c)?.let { return it.value }
        if (STUDENT_ID.matches(c)) return c
        return null
    }

    /** Splits a CSV/TXT line and returns only the valid identifiers on it. */
    fun extractIdentifiers(line: String): List<String> =
        line.split(',', ';', '\t').mapNotNull { extractIdentifier(it) }

    /**
     * Normalises any token (uid, userId, email, or a stored invited value) for comparison.
     * Falls back to clean() so Firebase UIDs are still lowercased consistently.
     */
    fun normalize(raw: String?): String {
        if (raw == null) return ""
        return extractIdentifier(raw) ?: clean(raw)
    }
}