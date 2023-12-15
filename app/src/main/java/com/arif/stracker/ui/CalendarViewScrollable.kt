package com.arif.stracker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.CalendarView

class CalendarViewScrollable(context: Context, attrs: AttributeSet?) : CalendarView(context, attrs) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            parent.requestDisallowInterceptTouchEvent(true)
        }
        return false
    }
}