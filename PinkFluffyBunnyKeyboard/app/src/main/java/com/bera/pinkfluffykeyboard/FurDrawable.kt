package com.bera.pinkfluffykeyboard

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * Draws a ready-made plush PNG as a key (with soft shadow),
 * or, with cover = true, as a rounded keyboard background.
 */
class FurDrawable(
    private val src: Bitmap,
    private val radius: Float = 0f,
    private val cover: Boolean = false
) : Drawable() {

    private var body: Bitmap? = null
    private var shadow: Bitmap? = null
    private val off = IntArray(2)
    private var ins = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(bounds: Rect) {
        body = null
        shadow = null
    }

    private fun build(w: Int, h: Int) {
        if (cover) {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val clip = Path()
            clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
            c.clipPath(clip)
            val s = max(w.toFloat() / src.width, h.toFloat() / src.height)
            val m = Matrix()
            m.postScale(s, s)
            m.postTranslate((w - src.width * s) / 2f, (h - src.height * s) / 2f)
            c.drawBitmap(src, m, paint)
            body = out
            shadow = null
            ins = 0f
            return
        }
        ins = minOf(w, h) * 0.04f
        val bw = (w - ins * 2f).toInt().coerceAtLeast(1)
        val bh = (h - ins * 3f).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, bw, bh, true)
        val bp = Paint()
        bp.maskFilter = BlurMaskFilter(ins * 1.2f + 1f, BlurMaskFilter.Blur.NORMAL)
        shadow = scaled.extractAlpha(bp, off)
        body = scaled
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        if (body == null) build(b.width(), b.height())
        val bd = body ?: return
        val sh = shadow
        if (sh != null) {
            shadowPaint.color = Color.argb(70, 110, 40, 70)
            canvas.drawBitmap(sh, b.left + ins + off[0], b.top + ins + off[1] + ins * 0.8f, shadowPaint)
        }
        canvas.drawBitmap(bd, b.left + ins, b.top + ins, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
