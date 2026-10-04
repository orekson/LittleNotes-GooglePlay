package tw.local.memonote.data

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Decrypted attachment bytes stay in process memory while an encrypted note is open. */
object VaultMedia {
    private val images = ConcurrentHashMap<String, ByteArray>()

    fun put(session: String, bytes: ByteArray): String {
        require(session.matches(Regex("[a-f0-9-]{36}")))
        val ref = "vault:" + session + "/" + UUID.randomUUID()
        images[ref] = bytes
        return ref
    }

    fun get(ref: String): ByteArray? = images[ref]

    fun clear(session: String) {
        val prefix = "vault:" + session + "/"
        images.keys.filter { it.startsWith(prefix) }.forEach { ref ->
            images.remove(ref)?.fill(0)
        }
    }
}
