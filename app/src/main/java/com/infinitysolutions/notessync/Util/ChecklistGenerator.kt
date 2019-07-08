package com.infinitysolutions.notessync.Util

import com.infinitysolutions.notessync.Model.ChecklistItem

class ChecklistGenerator {
    companion object {

        fun generateList(content: String?): List<ChecklistItem> {
            val resultList: MutableList<ChecklistItem> = ArrayList()
            if (content != null) {
                val simpleList = content.split("[")
                var itemParts: Array<String>
                for (i in 0 until simpleList.size) {
                    itemParts = simpleList[i].split("]", limit = 2).toTypedArray()
                    if (itemParts.isNotEmpty()) {
                        if (itemParts.size == 1){
                            if (itemParts[0].contains("x"))
                                resultList.add(ChecklistItem("", true))
                        }else {
                            if (itemParts[0].contains("x"))
                                resultList.add(ChecklistItem(itemParts[1], true))
                            else
                                resultList.add(ChecklistItem(itemParts[1], false))
                        }
                    }
                }
            }
            return resultList
        }
    }
}