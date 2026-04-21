package com.gifboard

import android.util.Base64

/**
 * Builds Google consent cookies that bypass the cookie consent banner
 * while rejecting all non-essential tracking.
 *
 * Google uses two cookies to track consent state:
 *
 * 1. **CONSENT** — Marks the consent flow as "completed" so the banner is
 *    suppressed. "YES" means "acknowledged," not "accepted."
 *
 * 2. **SOCS** (State Of Consent Settings) — A base64-encoded protobuf
 *    containing the actual privacy preferences (reject all, locale, timestamp).
 */
object GoogleConsentCookies {

    /**
     * Builds the CONSENT cookie value with today's date and a random ID.
     * Format: `CONSENT=YES+cb.<YYYYMMDD>-17-p0.en+FX+<random 3-digit number>`
     */
    fun buildConsentCookie(): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val today = sdf.format(java.util.Date())
        val randomId = (100..999).random()
        return "CONSENT=YES+cb.$today-17-p0.en+FX+$randomId"
    }

    /**
     * Builds the SOCS cookie for "Reject All" (English locale).
     * The value is a base64-encoded protobuf with: version=1,
     * choice=reject-all, locale="en", acknowledged=true,
     * and a timestamp of the current time.
     */
    fun buildSocsCookie(): String {
        val consentFields = byteArrayOf(
            0x08, 0x01,                         // version: 1
            0x12, 0x0E,                         // consent_info (14 bytes):
              0x12, 0x06,                       //   choice (6 bytes):
                0x0A, 0x02, 0x08, 0x03,         //     type: 3 (reject)
                0x10, 0x02,                     //     action: 2 (reject all)
              0x1A, 0x02, 0x65, 0x6E,           //   locale: "en"
            0x20, 0x01                          // acknowledged: true
        )

        // Wrap current Unix time as: field 3 { field 1: <varint> }
        val tsVarint = encodeProtobufVarint(System.currentTimeMillis() / 1000)
        val tsInner = byteArrayOf(0x08) + tsVarint
        val tsField = byteArrayOf(0x1A, tsInner.size.toByte()) + tsInner

        val value = Base64.encodeToString(
            consentFields + tsField,
            Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "SOCS=$value"
    }

    /** Encodes a Long as a protobuf base-128 varint. */
    private fun encodeProtobufVarint(value: Long): ByteArray {
        val bytes = mutableListOf<Byte>()
        var v = value
        while (v > 0x7F) {
            bytes.add((v.toInt() and 0x7F or 0x80).toByte())
            v = v ushr 7
        }
        bytes.add(v.toByte())
        return bytes.toByteArray()
    }
}
