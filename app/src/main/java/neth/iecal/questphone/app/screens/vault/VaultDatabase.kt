package neth.iecal.questphone.app.screens.vault

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

// ── Vault item categories ─────────────────────────────────────────────────────

enum class VaultCategory(val emoji: String, val label: String) {
    PASSWORD("🔑", "Password"),
    NOTE("📝", "Secret Note"),
    CARD("💳", "Card / Account"),
    ID("🪪", "ID / Document"),
    KEY("🗝️", "Key / Code"),
    OTHER("📦", "Other")
}

@Serializable
data class VaultItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",       // stored ENCRYPTED in SharedPreferences
    val category: String = VaultCategory.NOTE.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val categoryEnum: VaultCategory
        get() = try { VaultCategory.valueOf(category) } catch (_: Exception) { VaultCategory.OTHER }
}

// ── AES-GCM crypto (PIN-derived key, PBKDF2) ─────────────────────────────────

object VaultCrypto {
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun encrypt(plaintext: String, pin: String, salt: ByteArray): String {
        val key = deriveKey(pin, salt)
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(ciphertext: String, pin: String, salt: ByteArray): String? = try {
        val raw = Base64.decode(ciphertext, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, IV_BYTES)
        val data = raw.copyOfRange(IV_BYTES, raw.size)
        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    } catch (_: Exception) { null }

    fun hashPin(pin: String, salt: ByteArray): String {
        return Base64.encodeToString(deriveKey(pin, salt), Base64.NO_WRAP)
    }

    fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun deriveKey(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

// ── Vault Storage ─────────────────────────────────────────────────────────────

object VaultDatabase {
    private const val PREFS = "data_vault"
    private const val KEY_ITEMS = "items_json"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_DATA_SALT = "data_salt"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun isPinSet(context: Context): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) != null

    /** Returns true on success */
    fun setPin(context: Context, pin: String): Boolean {
        if (pin.length < 4) return false
        val pinSalt = VaultCrypto.generateSalt()
        val dataSalt = VaultCrypto.generateSalt()
        val pinHash = VaultCrypto.hashPin(pin, pinSalt)
        prefs(context).edit()
            .putString(KEY_PIN_HASH, pinHash)
            .putString(KEY_PIN_SALT, Base64.encodeToString(pinSalt, Base64.NO_WRAP))
            .putString(KEY_DATA_SALT, Base64.encodeToString(dataSalt, Base64.NO_WRAP))
            .apply()
        return true
    }

    /** Returns true if PIN is correct */
    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val storedHash = p.getString(KEY_PIN_HASH, null) ?: return false
        val saltB64 = p.getString(KEY_PIN_SALT, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        return VaultCrypto.hashPin(pin, salt) == storedHash
    }

    /** Load and decrypt all items. Returns null if PIN is wrong or vault is empty */
    fun loadItems(context: Context, pin: String): List<VaultItem>? {
        if (!verifyPin(context, pin)) return null
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        val dataSalt = getDataSalt(context) ?: return emptyList()
        return try {
            val decrypted = VaultCrypto.decrypt(raw, pin, dataSalt) ?: return null
            json.decodeFromString(decrypted)
        } catch (_: Exception) { null }
    }

    /** Encrypt and save all items */
    fun saveItems(context: Context, pin: String, items: List<VaultItem>): Boolean {
        val dataSalt = getDataSalt(context) ?: return false
        return try {
            val plaintext = json.encodeToString(items)
            val encrypted = VaultCrypto.encrypt(plaintext, pin, dataSalt)
            prefs(context).edit().putString(KEY_ITEMS, encrypted).apply()
            true
        } catch (_: Exception) { false }
    }

    fun upsertItem(context: Context, pin: String, item: VaultItem): Boolean {
        val items = loadItems(context, pin)?.toMutableList() ?: return false
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx >= 0) items[idx] = item.copy(updatedAt = System.currentTimeMillis())
        else items.add(0, item)
        return saveItems(context, pin, items)
    }

    fun deleteItem(context: Context, pin: String, id: String): Boolean {
        val items = loadItems(context, pin) ?: return false
        return saveItems(context, pin, items.filter { it.id != id })
    }

    /** Wipe everything — nuclear option */
    fun wipeVault(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun getDataSalt(context: Context): ByteArray? {
        val b64 = prefs(context).getString(KEY_DATA_SALT, null) ?: return null
        return Base64.decode(b64, Base64.NO_WRAP)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
