package ru.colabike.core.auth

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * One secret kept between launches: the refresh token, or the PKCE verifier of a sign-in in the
 * browser. The access token is never stored: it stays in memory and dies with the process.
 */
interface SecretStore {
    fun read(): String?

    fun write(token: String)

    fun clear()
}

/** Turns secrets into bytes only this app on this device can read back. */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws when the data or the key is no longer valid. */
    fun decrypt(sealed: ByteArray): ByteArray

    /** Forgets the key, e.g. after it stopped decrypting. */
    fun reset()
}

/**
 * A secret encrypted with [cipher] in one file. The file belongs in `noBackupFilesDir`, and the
 * backup rules exclude it as well: a copy on another device is useless anyway, the Keystore key
 * does not leave this one.
 */
class EncryptedFileStore(private val file: File, private val cipher: TokenCipher) : SecretStore {
    @Synchronized
    override fun read(): String? {
        if (!file.exists()) return null
        return try {
            cipher.decrypt(file.readBytes()).decodeToString()
        } catch (e: IOException) {
            null
        } catch (e: GeneralSecurityException) {
            forget()
        } catch (e: ProviderException) {
            forget()
        } catch (e: IllegalArgumentException) {
            forget()
        } catch (e: IllegalStateException) {
            forget()
        }
    }

    @Synchronized
    override fun write(token: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(cipher.encrypt(token.encodeToByteArray()))
        Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    @Synchronized
    override fun clear() {
        file.delete()
    }

    // A key invalidated by the system or a damaged file: the session is lost, start over.
    private fun forget(): String? {
        clear()
        cipher.reset()
        return null
    }
}
