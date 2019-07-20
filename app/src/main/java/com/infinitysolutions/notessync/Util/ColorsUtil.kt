package com.infinitysolutions.notessync.Util

class ColorsUtil {
    private val colorsList = arrayOf(
        "#3d81f4",// 0
        "#940044",// 1
        "#ff5b3a",// 2
        "#ac00ae",// 3
        "#5e7c8a",// 4
        "#009d88",// 5
        "#ff0071",// 6
        "#7b5448" // 7
    )

    fun getColor(position: Int?): String {
        if (position == null || position > 7)
            return colorsList[0]
        return colorsList[position]
    }

    fun getSize(): Int{
        return colorsList.size
    }
}