package com.infinitysolutions.notessync.Util

class ChecklistGenerator {
    companion object {

        fun generateList(content: String?): String? {
            var resultList = ""
            if (content != null) {
                resultList = content.replace("[ ]", "□")
                resultList = resultList.replace("[x]", "✓")
            }
            return resultList
        }
    }
}