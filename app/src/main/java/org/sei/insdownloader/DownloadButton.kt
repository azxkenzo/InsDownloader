package org.sei.insdownloader

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat

class DownloadButton : View {

    private val _min = 0
    private var _max = 0
    private var _progress = 0
    private var _visualProgress = 0f
    private val uiThreadId = Thread.currentThread().id

    private val _progressDrawable by lazy {
        ResourcesCompat.getDrawable(
            context.resources,
            R.drawable.progress,
            null
        )
    }

    private val _text = "DOWNLOAD"

    private val textPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ResourcesCompat.getColor(context.resources, R.color.black, null)
        textSize = context.resources.getDimensionPixelOffset(R.dimen.buttonTextSize).toFloat()
        letterSpacing = 0.05f
    } }

    private val textWidth by lazy {
        val widths = FloatArray(_text.length)
        textPaint.getTextWidths(_text, widths)
        var width = 0f
        for (i in widths) width += i
        width
    }

    private val textHeight by lazy {
        textPaint.fontMetrics.let {
            it.top / -2 - it.bottom / 2
        }
    }

    private val PROGRESS_ANIM_DURATION = 350L
    private val PROGRESS_ANIM_INTERPOLATER = DecelerateInterpolator()

    constructor(ctx: Context) : this(ctx, null)

    constructor(ctx: Context, attrs: AttributeSet?) : this(ctx, attrs, 0)

    constructor(ctx: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        ctx,
        attrs,
        defStyleAttr
    ) {
        initProgressDrawable()
    }

    private fun initProgressDrawable() {
        _progressDrawable?.callback = this
        doRefreshProgress(0, false)
    }

    fun setMax(max: Int) {
        if (max <= _min) return
        _max = max
        _visualProgress = 1f
        _progress = 0
        refreshProgress(0)
    }

    @Synchronized
    fun setProgress(p: Int, animate: Boolean = true) {
        val progress = constrain(p, _max)
        if (_progress == progress) return
        _progress = progress
        refreshProgress(_progress, animate)
    }

    private fun constrain(p: Int, max: Int): Int {
        return when {
            p <= _min -> _min
            p >= max -> max
            else -> p
        }
    }

    @Synchronized
    private fun refreshProgress(p: Int, animate: Boolean = true) {
        if (uiThreadId == Thread.currentThread().id) {
            doRefreshProgress(p, animate)
        } else {

        }
    }

    @Synchronized
    private fun doRefreshProgress(p: Int, animate: Boolean = true) {
        val range = _max - _min
        val scale = if (range > 0) (p - _min) / range.toFloat() else 0f
        if (animate) {
            ObjectAnimator.ofFloat(this, "visualProgress", _visualProgress, scale).apply {
                duration = PROGRESS_ANIM_DURATION
                interpolator = PROGRESS_ANIM_INTERPOLATER
                setAutoCancel(true)
                start()
            }
        } else {
            setVisualProgress(scale)
        }
    }

    fun setVisualProgress(p: Float) {
        _visualProgress = p
        (_progressDrawable as? LayerDrawable)?.findDrawableByLayerId(R.id.progress)?.level =
            if (_max == 0) 10000 else (p * 10000).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var w = 100
        var h = 100
        w = resolveSize(w, widthMeasureSpec)
        h = resolveSize(h, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        _progressDrawable?.setBounds(0, 0, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        _progressDrawable?.draw(canvas)
        canvas.drawText(_text, (canvas.width - textWidth) / 2, canvas.height / 2 + textHeight, textPaint)
    }

    override fun invalidateDrawable(drawable: Drawable) {
        super.invalidateDrawable(drawable)
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return who == _progressDrawable || super.verifyDrawable(who)
    }

}