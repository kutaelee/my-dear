package app.mydear.android.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.mydear.android.memory.LocalMemoryRetriever
import app.mydear.android.memory.PersonalMemory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedMemoryStore(context: Context) {
    private val directory = context.filesDir.resolve("private-memory").canonicalFile
    private val file get() = directory.resolve("memories.v1.aesgcm")
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }

    @Synchronized fun load(): List<PersonalMemory> = runCatching {
        if (!file.isFile) return emptyList()
        val bytes = file.readBytes()
        require(bytes.size in (IV_BYTES + 1)..MAX_ENCRYPTED_BYTES)
        val buffer = ByteBuffer.wrap(bytes)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        json.decodeFromString<List<StoredMemory>>(cipher.doFinal(ciphertext).decodeToString())
            .takeLast(MAX_MEMORIES)
            .map { PersonalMemory(it.id.take(80), it.text.take(MAX_MEMORY_CHARS), it.createdAtEpochMs) }
    }.getOrDefault(emptyList())

    @Synchronized fun remember(fact: String): PersonalMemory {
        val normalized = fact.replace(Regex("\\s+"), " ").trim().take(MAX_MEMORY_CHARS)
        require(normalized.length >= 2)
        val current = load().filterNot { it.text.equals(normalized, ignoreCase = true) }.toMutableList()
        val memory = PersonalMemory(UUID.randomUUID().toString(), normalized, System.currentTimeMillis())
        current += memory
        save(current.takeLast(MAX_MEMORIES))
        return memory
    }

    @Synchronized fun forgetMatching(query: String): List<PersonalMemory> {
        val current = load()
        val removed = LocalMemoryRetriever.bestMatches(current, query, limit = 1)
        if (removed.isNotEmpty()) save(current.filterNot { candidate -> removed.any { it.id == candidate.id } })
        return removed
    }

    @Synchronized fun clear() = save(emptyList())

    @Synchronized private fun save(memories: List<PersonalMemory>) {
        check(directory.exists() || directory.mkdirs()) { "기억 저장 폴더를 만들 수 없어요" }
        val records = memories.takeLast(MAX_MEMORIES).map { StoredMemory(it.id.take(80), it.text.take(MAX_MEMORY_CHARS), it.createdAtEpochMs) }
        val plaintext = json.encodeToString(records).encodeToByteArray()
        require(plaintext.size <= MAX_PLAINTEXT_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plaintext)
        val output = ByteBuffer.allocate(cipher.iv.size + encrypted.size).put(cipher.iv).put(encrypted).array()
        val pending = directory.resolve(".${file.name}.new")
        pending.outputStream().use { stream ->
            stream.write(output)
            stream.fd.sync()
        }
        Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    @Serializable private data class StoredMemory(val id: String, val text: String, val createdAtEpochMs: Long)

    companion object {
        private const val KEY_ALIAS = "my_dear_personal_memory_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val MAX_MEMORIES = 50
        private const val MAX_MEMORY_CHARS = 500
        private const val MAX_PLAINTEXT_BYTES = 128 * 1024
        private const val MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + 64
    }
}
