package com.infinitysolutions.notessync.util

import android.util.Log
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.*

class GoogleDriveHelper(driveService: Drive) {
    private var googleDriveService: Drive = driveService
    private val TAG = "DriveHelperClass"
    lateinit var appFolderId: String
    lateinit var fileSystemId: String
    lateinit var imageFileSystemId: String

    fun getFileContent(fileId: String?): String? {
        if (fileId == null || fileId == "-1")
            return null
        val outputStream = ByteArrayOutputStream()
        googleDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        return outputStream.toString()
    }

    fun getFileContentStream(fileId: String?, path: String){
        if (fileId == null || fileId == "-1")
            return
        val tempFile = java.io.File(path, "temp.txt")
        if(tempFile.exists())
            tempFile.delete()
        val fos = FileOutputStream(tempFile)
        googleDriveService.files().get(fileId).executeMediaAndDownloadTo(fos)
        fos.flush()
        fos.close()
    }

    fun updateFile(fileId: String, fileContent: String): String? {
        val mediaStream = InputStreamContent("text/plain", fileContent.byteInputStream())
        val contentFile = File()
        val file = googleDriveService.files().update(fileId, contentFile, mediaStream)
            .execute()

        return file.id
    }

    fun updateFileStream(fileId: String, path: String){
        val localFile = java.io.File(path)
        val fileContent = FileContent("text/plain", localFile)
        val contentFile = File()
        googleDriveService.files().update(fileId, contentFile, fileContent)
            .execute()
    }

    fun searchFile(fileName: String, fileMimeType: String): String? {
        var fileId: String? = null
        val result = googleDriveService.files().list().apply {
            q = "name='$fileName' and mimeType='$fileMimeType'"
            spaces = "drive"
            fields = "files(id)"
        }.execute()
        if (result.files.size > 0) {
            fileId = result.files[0].id
        } else {
            Log.d(TAG, "File not found")
        }
        return fileId
    }

    fun createFile(parentFolderId: String?, fileName: String, fileMimeType: String, content: String?): String {
        val fileMetadata = File()
        fileMetadata.name = fileName
        fileMetadata.mimeType = fileMimeType
        if (parentFolderId != null)
            fileMetadata.parents = Collections.singletonList(parentFolderId)

        val file = if (content != null) {
            val contentStream = ByteArrayContent.fromString("text/plain", content)
            googleDriveService.files().create(fileMetadata, contentStream)
                .setFields("id")
                .execute()
        } else {
            googleDriveService.files().create(fileMetadata)
                .setFields("id")
                .execute()
        }

        return file.id
    }

    fun createFileFromStream(parentFolderId: String?, fileName: String, fileMimeType: String, path: String): String {
        val fileMetadata = File()
        fileMetadata.name = fileName
        fileMetadata.mimeType = fileMimeType
        if (parentFolderId != null)
            fileMetadata.parents = Collections.singletonList(parentFolderId)

        val file = java.io.File(path)
        val fileContent = FileContent("text/plain", file)
        val cloudFile = googleDriveService.files().create(fileMetadata, fileContent)
            .setFields("id")
            .execute()

        return cloudFile.id
    }

    fun deleteFile(fileId: String?) {
        if (fileId != null)
            googleDriveService.files().delete(fileId).execute()
    }
}