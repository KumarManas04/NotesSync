package com.infinitysolutions.notessync.util

class ChecklistConverter {
    companion object {

        fun convertList(content: String?): String {
            var resultList = ""
            if (content != null) {
                resultList = content.replace("[ ]", "□")
                resultList = resultList.replace("[x]", "✓")
            }
            return resultList
        }
    }
}