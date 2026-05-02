package com.example.tcgscanner

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val borderPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#88000000")
    }

    private val rect = RectF()

    private var zoomFactor = 1f
    private var offsetXFactor = 0f
    private var offsetYFactor = 0f
    private var sizeFactor = 1f

    fun setZoomFactor(zoom: Float) {
        zoomFactor = zoom
        invalidate()
    }

    fun setOffsets(x: Float, y: Float) {
        offsetXFactor = x
        offsetYFactor = y
        invalidate()
    }

    fun setSizeFactor(size: Float) {
        sizeFactor = size
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val widthFactor = when (zoomFactor) {
            1f -> 0.35f
            2f -> 0.40f
            2.5f -> 0.45f
            3f -> 0.50f
            else -> 0.35f
        }

        val heightFactor = when (zoomFactor) {
            1f -> 0.08f
            2f -> 0.10f
            2.5f -> 0.11f
            3f -> 0.12f
            else -> 0.08f
        }

        val rectWidth = w * widthFactor * sizeFactor
        val rectHeight = h * heightFactor * sizeFactor

        val baseLeft = w * 0.55f
        val baseTop = h * 0.55f

        val zoomOffset = when (zoomFactor) {
            1f -> 0f
            2f -> h * 0.12f
            2.5f -> h * 0.18f
            3f -> h * 0.25f
            else -> (zoomFactor - 1f) * h * 0.15f
        }

        val left = baseLeft + (offsetXFactor * w)
        val top = baseTop + zoomOffset + (offsetYFactor * h)
        val right = left + rectWidth
        val bottom = top + rectHeight

        rect.set(left, top, right, bottom)

        canvas.drawRect(0f, 0f, w, rect.top, backgroundPaint)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, backgroundPaint)
        canvas.drawRect(rect.right, rect.top, w, rect.bottom, backgroundPaint)
        canvas.drawRect(0f, rect.bottom, w, h, backgroundPaint)

        canvas.drawRect(rect, borderPaint)
    }

    fun getScanRect(): RectF {
        return rect
    }
}