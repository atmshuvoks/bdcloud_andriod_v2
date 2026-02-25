package org.bdcloud.clash.crypto

import android.util.Base64
import org.bdcloud.clash.api.*
import com.squareup.moshi.Types
import org.bdcloud.clash.api.ApiClient
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Port of the Rust derive_key + decrypt_fragment logic.
 *
 * Server pipeline:
 *   1. watermark(proxies, userId)
 *   2. fragment(watermarked) → frag1, frag2, frag3
 *   3. encrypt(each fragment, sessionKey) → { iv, data, tag }
 *
 * Client decryption:
 *   1. GET /proxies/session-key → { sessionId, hint, keyData, expiresIn }
 *   2. deriveKey(keyInfo) → 32-byte AES key
 *   3. For each fragment: decryptFragment(fragment, key) → JSON array
 *   4. Reassemble: merge frag1 + frag2 + frag3 by _id
 */
object ProxyCrypto {

    private const val GCM_TAG_LENGTH = 128 // bits

    /**
     * Derive the 32-byte AES key from the session key info.
     *
     * Algorithm (from server's xorMask + Rust derive_key):
     *   mask = SHA-256("mask:" + sessionId)
     *   maskedKey = hex_decode(keyData)
     *   key[i] = maskedKey[i] XOR mask[i % 32]
     */
    fun deriveKey(keyInfo: SessionKeyInfo): ByteArray {
        val mask = sha256("mask:${keyInfo.sessionId}".toByteArray(Charsets.UTF_8))
        val maskedKey = hexDecode(keyInfo.keyData)

        require(maskedKey.isNotEmpty()) { "Empty key data" }

        val key = ByteArray(maskedKey.size)
        for (i in maskedKey.indices) {
            key[i] = (maskedKey[i].toInt() xor mask[i % mask.size].toInt()).toByte()
        }

        require(key.size == 32) { "Derived key length is invalid: ${key.size}" }
        return key
    }

    /**
     * Decrypt a single AES-256-GCM encrypted fragment.
     *
     * Fragment fields (all hex-encoded):
     *   iv:   12 bytes (nonce)
     *   data: ciphertext
     *   tag:  16 bytes (auth tag)
     *
     * In Java's GCM, the auth tag must be appended to the ciphertext before decryption.
     */
    fun decryptFragmentBytes(fragment: EncryptedFragment, key: ByteArray): ByteArray {
        val iv = hexDecode(fragment.iv)
        val ciphertext = hexDecode(fragment.data)
        val tag = hexDecode(fragment.tag)

        require(iv.size == 12) { "Invalid IV length: ${iv.size}" }

        // GCM expects ciphertext + tag concatenated
        val combined = ciphertext + tag

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(combined)
    }

    /**
     * Decrypt a fragment and parse the JSON result into a list of items.
     */
    inline fun <reified T> decryptFragment(fragment: EncryptedFragment, key: ByteArray): List<T> {
        val plaintext = decryptFragmentBytes(fragment, key)
        val json = String(plaintext, Charsets.UTF_8)
        val type = Types.newParameterizedType(List::class.java, T::class.java)
        val adapter = ApiClient.moshi.adapter<List<T>>(type)
        return adapter.fromJson(json) ?: emptyList()
    }

    /**
     * Full decryption pipeline:
     *   1. Derive AES key from session key info
     *   2. Decrypt all 3 fragments
     *   3. Reassemble into DecryptedProxy list
     */
    fun decryptProxies(payload: EncryptedPayload, keyInfo: SessionKeyInfo): List<DecryptedProxy> {
        require(payload.fragments.size == 3) {
            "Expected 3 fragments, got ${payload.fragments.size}"
        }

        val key = deriveKey(keyInfo)

        // Decrypt each fragment
        val frag1Items: List<Frag1Item> = decryptFragment(payload.fragments[0], key)
        val frag2Items: List<Frag2Item> = decryptFragment(payload.fragments[1], key)
        val frag3Items: List<Frag3Item> = decryptFragment(payload.fragments[2], key)

        // Build lookup maps by _id
        val frag2Map = frag2Items.associateBy { it.id }
        val frag3Map = frag3Items.associateBy { it.id }

        // Reassemble
        return frag1Items.mapNotNull { f1 ->
            val f2 = frag2Map[f1.id] ?: return@mapNotNull null
            val f3 = frag3Map[f1.id] ?: return@mapNotNull null

            DecryptedProxy(
                id = f1.id,
                name = f1.n ?: "Proxy ${f1.id}",
                server = f1.s ?: "",
                port = f2.p ?: 0,
                username = decodeOptionalBase64(f2.u),
                password = decodeOptionalBase64(f3.k),
                location = f1.l ?: "Unknown"
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────

    private fun sha256(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    private fun hexDecode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex string length: ${hex.length}" }
        return ByteArray(hex.length / 2) { i ->
            val h = hex.substring(i * 2, i * 2 + 2)
            h.toInt(16).toByte()
        }
    }

    private fun decodeOptionalBase64(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return try {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            value // fallback: return as-is
        }
    }
}
