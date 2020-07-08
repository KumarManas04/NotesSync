package com.infinitysolutions.notessync.Util

import android.content.ContentValues.TAG
import android.util.Log
import com.infinitysolutions.notessync.Fragments.SettingsFragment

class ColorsUtil {
    private var colorType = SettingsFragment.COLOR_TYPE_STRING

    private val colorsListDark = arrayOf(
        "#2196f3",// 0
        "#00BCD4",// 1
        "#009688",// 2
        "#3DDC84",// 3
        "#9C27B0",// 4
        "#AD1457",// 5
        "#E91E63",// 6
        "#F44336",// 7
        "#FF5722",//8
        "#795548",//9
        "#607D8B",//10
        "#263238" //11
    )

    private val colorsListLight = arrayOf(
        "#96ADC8",// 0
        "#8B9BDC",// 1
        "#D7708F",// 2
        "#D792B7",// 3
        "#F8A098",// 4
        "#F8D9AD",// 5
        "#FFE090",// 6
        "#76D7A4",// 7
        "#41A287",//8
        "#A0796C",//9
        "#3F3F3F",//10
        "#181818" //11
    )

    fun getColorType(): String {
        return colorType
    }

    private fun getColorType(type: String): Array<String> {
        Log.d(TAG, "getColorType: $type")
        return if (colorType === "dark") {
            colorsListDark
        } else {
            colorsListLight
        }
    }

    fun getColor(position: Int?): String {

        val colors = getColorType(colorType)

        if (position == null || position > getSize())

            return colors[0]

        return colors[position]
    }

    fun getSize(): Int {
        val colorsList = getColorType(colorType)
        return colorsList.size
    }
}