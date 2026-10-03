package ru.colabike.core.auth

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import org.junit.Test

class EncryptedFileStoreTest {
    private class FakeCipher : TokenCipher {
        var resets = 0
        var broken = false

        override fun encrypt(plain: ByteArray) =
            byteArrayOf(1) + plain.map { (it.toInt() xor 0x5a).toByte() }

        override fun decrypt(sealed: ByteArray): ByteArray {
            if (broken) throw GeneralSecurityException("key invalidated")
            return sealed.drop(1).map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        }

        override fun reset() {
            resets++
        }
    }

    private val dir: File = Files.createTempDirectory("store").toFile()
    private val cipher = FakeCipher()
    private val store = EncryptedFileStore(File(dir, "session/refresh.bin"), cipher)

    @Test
    fun `writes only ciphertext and reads it back`() {
        store.write("cola_rt_secret")
        assertThat(File(dir, "session/refresh.bin").readText()).doesNotContain("cola_rt_secret")
        assertThat(store.read()).isEqualTo("cola_rt_secret")
        store.clear()
        assertThat(store.read()).isNull()
    }

    @Test
    fun `a key that stopped working means no session, and the key is replaced`() {
        store.write("cola_rt_secret")
        cipher.broken = true
        assertThat(store.read()).isNull()
        assertThat(File(dir, "session/refresh.bin").exists()).isFalse()
        assertThat(cipher.resets).isEqualTo(1)
    }
}
