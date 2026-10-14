package com.geotag.app.util

/**
 * Plus Code (Open Location Code) — kode alamat akurat ala Google Maps,
 * contoh: "6P4JC28P+M9" untuk (-7.583308, 112.035881).
 * Encode/decode mengikuti standar Open Location Code (bagian pasangan/10 karakter).
 */
object PlusCode {
    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private const val SEPARATOR = '+'
    private const val SEPARATOR_POSITION = 8
    private val PAIR_RESOLUTIONS = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)

    /** Koordinat -> Plus Code lengkap 10 karakter. */
    fun encode(latitude: Double, longitude: Double): String {
        var lat = latitude.coerceIn(-90.0, 90.0)
        var lng = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        if (lat == 90.0) lat = 89.9999999
        lat += 90.0
        lng += 180.0
        val code = StringBuilder()
        for (i in PAIR_RESOLUTIONS.indices) {
            if (code.length >= 10) break
            val latDigit = (lat / PAIR_RESOLUTIONS[i]).toInt()
            val lngDigit = (lng / PAIR_RESOLUTIONS[i]).toInt()
            lat -= latDigit * PAIR_RESOLUTIONS[i]
            lng -= lngDigit * PAIR_RESOLUTIONS[i]
            code.append(ALPHABET[latDigit])
            code.append(ALPHABET[lngDigit])
            if (code.length == SEPARATOR_POSITION) code.append(SEPARATOR)
        }
        return code.toString()
    }

    /** Plus Code lengkap -> titik tengah sel (lat, lng). Null bila tidak valid. */
    fun decode(code: String): Pair<Double, Double>? {
        val clean = normalize(code) ?: return null
        val sepIndex = clean.indexOf(SEPARATOR)
        if (sepIndex == -1) return null
        val all = (clean.substring(0, sepIndex) + clean.substring(sepIndex + 1))
            .take(10) // buang karakter grid (presisi ekstra) bila ada
        if (all.isEmpty() || all.length % 2 != 0) return null
        if (all.any { it !in ALPHABET }) return null
        var lat = 0.0
        var lng = 0.0
        var resolution = 20.0
        for (i in all.indices step 2) {
            val pairIndex = i / 2
            if (pairIndex >= PAIR_RESOLUTIONS.size) return null
            resolution = PAIR_RESOLUTIONS[pairIndex]
            lat += ALPHABET.indexOf(all[i]) * resolution
            lng += ALPHABET.indexOf(all[i + 1]) * resolution
        }
        lat += resolution / 2
        lng += resolution / 2
        return Pair(lat - 90.0, lng - 180.0)
    }

    /** True bila kode lengkap (8 karakter sebelum '+'). */
    fun isFullCode(code: String): Boolean {
        val clean = normalize(code) ?: return false
        return clean.indexOf(SEPARATOR) == SEPARATOR_POSITION
    }

    /**
     * Pulihkan Plus Code pendek (misal "C28P+M9C" dari Google Maps)
     * memakai lokasi referensi terdekat (lokasi device / titik peta).
     */
    fun recoverNearest(shortCode: String, refLat: Double, refLng: Double): String? {
        val clean = normalize(shortCode) ?: return null
        val sepIndex = clean.indexOf(SEPARATOR)
        if (sepIndex == -1 || sepIndex >= SEPARATOR_POSITION) return null
        val paddingLength = SEPARATOR_POSITION - sepIndex
        if (paddingLength % 2 != 0) return null
        val refCode = encode(refLat, refLng)
        val padded = refCode.substring(0, paddingLength) + clean
        val decoded = decode(padded) ?: return null
        // Sesuaikan prefix bila titik hasil decode terlalu jauh dari referensi
        val paddingResolution = PAIR_RESOLUTIONS[paddingLength / 2 - 1]
        var (dLat, dLng) = decoded
        if (dLat - refLat > paddingResolution / 2) dLat -= paddingResolution
        if (refLat - dLat > paddingResolution / 2) dLat += paddingResolution
        if (dLng - refLng > paddingResolution / 2) dLng -= paddingResolution
        if (refLng - dLng > paddingResolution / 2) dLng += paddingResolution
        return encode(dLat, dLng).substring(0, paddingLength) + clean
    }

    /** Decode kode apa pun: lengkap langsung, pendek dipulihkan pakai referensi. */
    fun decodeAny(code: String, refLat: Double?, refLng: Double?): Pair<Double, Double>? {
        val clean = normalize(code) ?: return null
        if (isFullCode(clean)) return decode(clean)
        if (refLat != null && refLng != null) {
            val full = recoverNearest(clean, refLat, refLng) ?: return null
            return decode(full)
        }
        return null
    }

    private fun normalize(code: String): String? {
        // Ambil token pertama (buang nama lokalitas seperti "Mabung, ..." bila ada)
        val token = code.trim().uppercase().split(" ", ",").firstOrNull().orEmpty()
        if (token.isEmpty()) return null
        return token
    }
}
