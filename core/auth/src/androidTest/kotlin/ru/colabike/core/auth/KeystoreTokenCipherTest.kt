package ru.colabike.core.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The real Android Keystore: runs on the emulator job of CI, not in JVM unit tests. */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {
    private val cipher = KeystoreTokenCipher(alias = "colabike.test.v1")
    private val file =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
            "test.bin",
        )

    @After
    fun cleanUp() {
        cipher.reset()
        file.delete()
    }

    @Test
    fun encryptsWithAFreshIvAndReadsBack() {
        val first = cipher.encrypt("cola_rt_secret".encodeToByteArray())
        val second = cipher.encrypt("cola_rt_secret".encodeToByteArray())

        assertThat(first).isNotEqualTo(second)
        assertThat(String(first, Charsets.ISO_8859_1)).doesNotContain("cola_rt_secret")
        assertThat(cipher.decrypt(first).decodeToString()).isEqualTo("cola_rt_secret")
    }

    @Test
    fun aDeletedKeyMeansNoSession() {
        val store = EncryptedFileStore(file, cipher)
        store.write("cola_rt_secret")
        cipher.reset()

        assertThat(store.read()).isNull()
        assertThat(file.exists()).isFalse()
    }
}
