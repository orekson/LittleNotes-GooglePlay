package tw.local.memonote.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned, chunk-authenticated password encryption. Every chunk has a fresh
 * AES-GCM nonce and binds its sequence number, length, and file header as AAD.
 * The authenticated zero-length terminal chunk detects truncated files.
 */
object PasswordCrypto {
    const val BACKUP_MAGIC = "LNBKUP01"
    const val NOTE_MAGIC = "LNNOTE01"
    private const val VERSION = 1
    private const val CHUNK_SIZE = 64 * 1024
    private const val TAG_SIZE = 16
    private const val ITERATIONS = 250_000
    private const val MAX_PLAINTEXT = 512L * 1024 * 1024
    private const val HEADER_SIZE = 8 + 1 + 16 + 8

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.isNotEmpty())
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2withHmacSHA256").generateSecret(spec).encoded
            return try { SecretKeySpec(raw, "AES") } finally { raw.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, nonce: ByteArray, counter: Int, header: ByteArray, size: Int): Cipher {
        val iv = ByteBuffer.allocate(12).put(nonce).putInt(counter).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(128, iv))
            updateAAD(header)
            updateAAD(ByteBuffer.allocate(8).putInt(counter).putInt(size).array())
        }
    }

    fun encrypting(output: OutputStream, password: CharArray, magic: String): OutputStream {
        require(magic.toByteArray(Charsets.US_ASCII).size == 8)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(magic.toByteArray(Charsets.US_ASCII)).put(VERSION.toByte()).put(salt).put(nonce).array()
        val secret = key(password, salt)
        output.write(header)
        return object : OutputStream() {
            private val data = DataOutputStream(output)
            private val buffer = ByteArray(CHUNK_SIZE)
            private var used = 0
            private var counter = 0
            private var closed = false

            private fun block(length: Int) {
                check(counter >= 0 && !closed)
                data.writeInt(length)
                data.write(cipher(Cipher.ENCRYPT_MODE, secret, nonce, counter, header, length)
                    .doFinal(buffer, 0, length))
                counter++
                used = 0
            }

            override fun write(value: Int) {
                check(!closed)
                buffer[used++] = value.toByte()
                if (used == CHUNK_SIZE) block(used)
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
                check(!closed)
                var source = offset
                val end = offset + length
                while (source < end) {
                    val amount = minOf(end - source, CHUNK_SIZE - used)
                    bytes.copyInto(buffer, used, source, source + amount)
                    used += amount
                    source += amount
                    if (used == CHUNK_SIZE) block(used)
                }
            }

            override fun flush() {
                check(!closed)
                if (used > 0) block(used)
                data.flush()
            }

            override fun close() {
                if (closed) return
                try {
                    if (used > 0) block(used)
                    block(0)
                    data.flush()
                } finally {
                    closed = true
                    buffer.fill(0)
                    data.close()
                }
            }
        }
    }

    fun decrypting(input: InputStream, password: CharArray, magic: String): InputStream {
        require(magic.toByteArray(Charsets.US_ASCII).size == 8)
        val data = DataInputStream(input)
        val header = ByteArray(HEADER_SIZE)
        data.readFully(header)
        val expected = magic.toByteArray(Charsets.US_ASCII)
        if (!header.copyOfRange(0, 8).contentEquals(expected) || header[8].toInt() != VERSION) {
            throw IOException("不是支援的檔案格式")
        }
        val salt = header.copyOfRange(9, 25)
        val nonce = header.copyOfRange(25, 33)
        val secret = key(password, salt)
        return object : InputStream() {
            private var plain = ByteArray(0)
            private var position = 0
            private var counter = 0
            private var total = 0L
            private var finished = false

            private fun fill(): Boolean {
                if (finished) return false
                val size = try { data.readInt() } catch (e: java.io.EOFException) {
                    throw IOException("加密檔案不完整", e)
                }
                if (size !in 0..CHUNK_SIZE || counter < 0 || total + size > MAX_PLAINTEXT) {
                    throw IOException("加密檔案大小或格式不正確")
                }
                val encrypted = ByteArray(size + TAG_SIZE)
                data.readFully(encrypted)
                val clear = try {
                    cipher(Cipher.DECRYPT_MODE, secret, nonce, counter, header, size).doFinal(encrypted)
                } catch (e: AEADBadTagException) {
                    throw IOException("密碼錯誤或檔案損壞", e)
                }
                counter++
                if (size == 0) {
                    if (data.read() != -1) throw IOException("加密檔案尾端有多餘資料")
                    finished = true
                    plain = ByteArray(0)
                    position = 0
                    return false
                }
                total += size
                plain = clear
                position = 0
                return true
            }

            override fun read(): Int {
                if (position >= plain.size && !fill()) return -1
                return plain[position++].toInt() and 0xff
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
                if (length == 0) return 0
                if (position >= plain.size && !fill()) return -1
                val count = minOf(length, plain.size - position)
                plain.copyInto(bytes, offset, position, position + count)
                position += count
                return count
            }

            override fun close() {
                plain.fill(0)
                data.close()
            }
        }
    }

    fun encryptBytes(clear: ByteArray, password: CharArray, magic: String): ByteArray {
        val output = ByteArrayOutputStream()
        encrypting(output, password, magic).use { it.write(clear) }
        return output.toByteArray()
    }

    fun decryptBytes(encrypted: ByteArray, password: CharArray, magic: String): ByteArray =
        decrypting(ByteArrayInputStream(encrypted), password, magic).use { it.readBytes() }
}
