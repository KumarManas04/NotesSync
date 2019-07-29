package com.infinitysolutions.notessync.Util

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


class AES256Helper{
    private lateinit var key: SecretKey

    fun generateKey(passwordString: String, saltString: String) {
        val password = passwordString.toCharArray()
        val salt = saltString.toByteArray()
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val keySpec = PBEKeySpec(password, salt, 1000, 256)
        key =  secretKeyFactory.generateSecret(keySpec)
    }

    fun encrypt(plainTextMessage: String): String {
        val plainText = plainTextMessage.toByteArray()
        val IV = ByteArray(12)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(IV)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key.encoded, "AES")
        val gcmParameterSpec = GCMParameterSpec(128, IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec)
        val cipherText = cipher.doFinal(plainText)

        val byteBuffer = ByteBuffer.allocate(4 + IV.size + cipherText.size)
        byteBuffer.putInt(IV.size)
        byteBuffer.put(IV)
        byteBuffer.put(cipherText)
        val cipherOutput = byteBuffer.array()

        return Base64.encodeToString(cipherOutput, Base64.DEFAULT)
    }

    fun decrypt(cipherMessageEncoded: String): String {
        val cipherMessage = Base64.decode(cipherMessageEncoded, Base64.DEFAULT)
        //Extracting extra details from cipher message
        val byteBuffer = ByteBuffer.wrap(cipherMessage)
        val ivLength = byteBuffer.int
        if (ivLength < 12 || ivLength >= 16) { // check input parameter
            throw IllegalArgumentException("invalid IV length")
        }
        val IV = ByteArray(ivLength)
        byteBuffer.get(IV)
        val cipherText = ByteArray(byteBuffer.remaining())
        byteBuffer.get(cipherText)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key.encoded, "AES")
        val gcmParameterSpec = GCMParameterSpec(128, IV)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec)
        val decryptedText = cipher.doFinal(cipherText)
        return String(decryptedText)
    }
}