package app.mydear.android.storage

import android.content.Context
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.Provenance
import app.mydear.android.domain.Role
import app.mydear.android.domain.SearchSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.net.URI
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class EncryptedChatStore(context: Context) {
    private val directory = context.filesDir.resolve("private-chat").canonicalFile
    private val file get() = directory.resolve("history.v1.aesgcm")
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }

    fun load(): List<ChatMessage> = runCatching {
        if (!file.isFile) return emptyList()
        val bytes = file.readBytes()
        require(bytes.size in (IV_BYTES + 1)..MAX_ENCRYPTED_BYTES)
        val buffer = ByteBuffer.wrap(bytes)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        val records = json.decodeFromString<List<StoredMessage>>(cipher.doFinal(ciphertext).decodeToString())
        records.takeLast(MAX_MESSAGES).map { record ->
            val provenance = record.web?.toProvenance() ?: Provenance.Local
            ChatMessage(
                record.id.take(80),
                if (record.role == "user") Role.User else Role.Assistant,
                record.text.take(MAX_MESSAGE_CHARS),
                provenance,
            )
        }
    }.getOrDefault(emptyList())

    fun save(messages: List<ChatMessage>) {
        check(directory.exists() || directory.mkdirs()) { "대화 저장 폴더를 만들 수 없어요" }
        val records = messages.takeLast(MAX_MESSAGES).map { message ->
            val provenance = message.provenance as? Provenance.Web
            val web = provenance?.sources?.firstOrNull()
                ?.takeIf { it.url.startsWith("https://") }
                ?.let { source ->
                    StoredWeb(
                        searchedAtEpochMs = provenance.searchedAtEpochMs,
                        title = source.title.take(MAX_SOURCE_TITLE_CHARS),
                        url = source.url.take(MAX_SOURCE_URL_CHARS),
                        updatedAt = source.updatedAt?.take(MAX_SOURCE_DATE_CHARS),
                    )
                }
            StoredMessage(
                message.id.take(80),
                if (message.role == Role.User) "user" else "assistant",
                message.text.take(MAX_MESSAGE_CHARS),
                web,
            )
        }
        val plaintext = json.encodeToString(records).encodeToByteArray()
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "대화 저장 용량을 초과했어요" }
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

    private fun StoredWeb.toProvenance(): Provenance {
        val parsed = runCatching { URI(url) }.getOrNull()
        val host = parsed?.host?.takeIf { parsed.scheme == "https" && it.isNotBlank() } ?: return Provenance.Local
        return Provenance.Web(
            searchedAtEpochMs.coerceAtLeast(0),
            listOf(SearchSource(title.take(MAX_SOURCE_TITLE_CHARS), host, url.take(MAX_SOURCE_URL_CHARS), updatedAt?.take(MAX_SOURCE_DATE_CHARS))),
        )
    }

    @Serializable private data class StoredMessage(
        val id: String,
        val role: String,
        val text: String,
        val web: StoredWeb? = null,
    )

    @Serializable private data class StoredWeb(
        val searchedAtEpochMs: Long,
        val title: String,
        val url: String,
        val updatedAt: String? = null,
    )

    companion object {
        private const val KEY_ALIAS = "my_dear_chat_history_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val MAX_MESSAGES = 200
        private const val MAX_MESSAGE_CHARS = 12_000
        private const val MAX_SOURCE_TITLE_CHARS = 200
        private const val MAX_SOURCE_URL_CHARS = 2_048
        private const val MAX_SOURCE_DATE_CHARS = 80
        private const val MAX_PLAINTEXT_BYTES = 2 * 1024 * 1024
        private const val MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + 64
    }
}
