package com.infinitysolutions.notessync.Util

import android.util.Base64
import android.util.Base64InputStream
import android.util.Base64OutputStream
import android.util.Log
import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
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

    fun encryptStream(fis: FileInputStream, tempFilePath: String){
        val IV = ByteArray(12)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(IV)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key.encoded, "AES")
        val gcmParameterSpec = GCMParameterSpec(128, IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec)

        val tempFile = File(tempFilePath)
        if(tempFile.exists())
            tempFile.delete()
        val byteBuffer = ByteBuffer.allocate(12)
        byteBuffer.put(IV)
        val header = byteBuffer.array()

        val fos = FileOutputStream(tempFile)
        val base64stream = Base64OutputStream(fos, Base64.DEFAULT)
        base64stream.write(header)
        val cipherStream = CipherOutputStream(base64stream, cipher)

        val buffer = ByteArray(4096)
        var bytesRead = fis.read(buffer)
        while(bytesRead != -1){
            cipherStream.write(buffer, 0, bytesRead)
            bytesRead = fis.read(buffer)
        }
        fis.close()

        cipherStream.flush()
        cipherStream.close()
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

    fun decryptStream(inputStream: InputStream, filePath: String): String{
        val base64stream = Base64InputStream(inputStream, Base64.DEFAULT)
        val IV = ByteArray(12)
        base64stream.read(IV)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key.encoded, "AES")
        val gcmParameterSpec = GCMParameterSpec(128, IV)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec)

        val time = Calendar.getInstance().timeInMillis
        val imageFile = File(filePath, "$time.png")
        val fos = FileOutputStream(imageFile)
        val cipherStream = CipherOutputStream(fos, cipher)

        val buffer = ByteArray(4096)
        var bytesRead = base64stream.read(buffer)
        while(bytesRead != -1){
            cipherStream.write(buffer, 0, bytesRead)
            bytesRead = base64stream.read(buffer)
        }
        base64stream.close()
        cipherStream.flush()
        cipherStream.close()
        return imageFile.absolutePath
    }
}