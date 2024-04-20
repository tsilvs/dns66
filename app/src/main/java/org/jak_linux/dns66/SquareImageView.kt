package org.jak_linux.dns66

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * Workaround for Android 5.0 and 5.1 not correctly scaling vector drawables.
 * <p>
 * Android 5.0 and 5.1 only correctly scale vector drawables for scale type fitXY. We want to
 * keep the aspect ratio of our images however. This works around this by using fitXY and in
 * onMeasure() restricting width and height to the minimum of both, creating a square image.
 * <p>
 * To use this view, you have to make sure it is centered horizontally in its layout.
 */
class SquareImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    init {
        setScale()
    }

    private fun setScale() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            scaleType = ScaleType.FIT_XY
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (Build.VERSION.SDK_INT < 23) {
            val width = measuredWidth
            val height = measuredHeight
            val result = min(width.toDouble(), height.toDouble()).toInt()
            setMeasuredDimension(result, result)
        }
    }
}
