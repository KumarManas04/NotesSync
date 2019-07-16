package com.infinitysolutions.notessync.Util

import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.SearchMode
import com.dropbox.core.v2.files.WriteMode
import java.io.ByteArrayOutputStream

class DropboxHelper(client: DbxClientV2) {
    private val TAG = "DropboxHelperClass"
    private val dropboxClient: DbxClientV2 = client

    fun getFileContent(fileName: String?): String? {
        return if (fileName != null) {
            val outputStream = ByteArrayOutputStream()
            dropboxClient.files()?.download("/$fileName")?.download(outputStream)
            outputStream.toString()
        }else{
            null
        }
    }

    fun writeFile(fileName: String?, fileContent: String?) {
        if (fileName != null && fileContent != null) {
            val inputStream = fileContent.byteInputStream()
            dropboxClient.files().uploadBuilder("/$fileName").withMode(WriteMode.OVERWRITE).uploadAndFinish(inputStream)
        }
    }

    fun checkIfFileExists(fileName: String?): Boolean {
        var result = false
        if (fileName != null) {
            val sResult = dropboxClient.files().searchBuilder("", fileName)?.withMode(SearchMode.FILENAME)?.start()
            result = !sResult.toString().contains("\"matches\":[]")
        }
        return result
    }

    fun deleteFile(fileName: String?){
        if (fileName != null)
            dropboxClient.files().deleteV2("/$fileName")
    }
}