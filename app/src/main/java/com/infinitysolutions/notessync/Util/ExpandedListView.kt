package com.infinitysolutions.notessync.Util

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.ListView


class ExpandedListView(context: Context, attrs: AttributeSet) : ListView(context, attrs) {

    private var params: android.view.ViewGroup.LayoutParams? = null
    private var oldCount = 0

    override fun onDraw(canvas: Canvas) {
        if (count != oldCount) {
            oldCount = count
            params = layoutParams
            params!!.height = count * if (oldCount > 0) getChildAt(0).height else 0
            layoutParams = params
        }

        super.onDraw(canvas)
    }

}