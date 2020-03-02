package com.infinitysolutions.notessync.Util

class ColorsUtil {
    private val colorsList = arrayOf(
        "#2196f3",// 0
        "#AD1457",// 1
        "#FF5722",// 2
        "#9C27B0",// 3
        "#607D8B",// 4
        "#009688",// 5
        "#E91E63",// 6
        "#795548",// 7
        "#263238" //8
    )

    fun getColor(position: Int?): String {
        if (position == null || position > 8)
            return colorsList[0]
        return colorsList[position]
    }

    fun getSize(): Int{
        return colorsList.size
    }
}