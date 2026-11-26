package me.rerere.rikkahub.ui.floating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.petterp.floatingx.core.FxControl
import kotlin.math.roundToInt

private const val TAG = "FloatingCounterOverlay"

/**
 * Non-touchable visual window for the child-task number. The live bubble window is deliberately
 * narrow so its transparent padding cannot swallow taps. Offsets outside that window are drawn
 * here instead of enlarging the bubble's input region.
 */
internal class FloatingCounterOverlay(
    private val context: Context,
    private val onActiveChanged: (Boolean) -> Unit,
) {
    private data class Model(
        val count: Int? = null,
        val scale: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val mirror: Boolean = false,
        val fontPath: String = "",
    )

    private val view = CounterOverlayView(context)
    private val window = CounterWindow(context, view)
    private var model = Model()
    private var host: View? = null
    private var hostTree: ViewTreeObserver? = null
    private var preDraw: ViewTreeObserver.OnPreDrawListener? = null
    private var hostVisible = false
    private var yielded = false
    private var active = false
    private var lastLocationX = Int.MIN_VALUE
    private var lastLocationY = Int.MIN_VALUE
    private var lastWidth = -1
    private var lastHeight = -1

    /** Bind after FloatingX has created the live bubble view; safe to call repeatedly. */
    fun bind(control: FxControl) {
        val content = control.contentView ?: return
        if (host === content) return
        unbindHost()
        host = content
        view.setCounter(model.count, model.fontPath)
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                refreshFromHost()
                return true
            }
        }
        preDraw = listener
        hostTree = content.viewTreeObserver
        hostTree?.addOnPreDrawListener(listener)
        refreshFromHost(force = true)
    }

    fun setHostVisible(visible: Boolean) {
        if (hostVisible == visible) return
        hostVisible = visible
        sync()
    }

    fun setYielded(yielding: Boolean) {
        if (yielded == yielding) return
        yielded = yielding
        sync()
    }

    fun setMirror(mirror: Boolean) {
        if (model.mirror == mirror) return
        model = model.copy(mirror = mirror)
        sync()
    }

    fun update(count: Int?, scale: Float, offsetX: Float, offsetY: Float, fontPath: String) {
        val next = model.copy(
            count = count,
            scale = FloatingPanelSizing.clampBubbleScale(scale),
            offsetX = FloatingCounterGeometry.clampOffset(offsetX),
            offsetY = FloatingCounterGeometry.clampOffset(offsetY),
            fontPath = fontPath,
        )
        if (next != model) {
            model = next
            view.setCounter(next.count, next.fontPath)
            sync()
        }
    }

    /** Remove the companion window but keep the host/view so a later bind can resume it. */
    fun close() {
        unbindHost()
        hide()
    }

    private fun refreshFromHost(force: Boolean = false) {
        val content = host ?: return
        val location = content.screenLocation() ?: return
        val changed = force || location[0] != lastLocationX || location[1] != lastLocationY ||
            content.width != lastWidth || content.height != lastHeight
        if (!changed) return
        lastLocationX = location[0]
        lastLocationY = location[1]
        lastWidth = content.width
        lastHeight = content.height
        sync()
    }

    private fun sync() {
        val m = model
        val content = host
        val location = content?.screenLocation()
        val count = m.count
        if (!hostVisible || yielded || content == null || location == null || count == null || m.scale <= 0f) {
            hide()
            return
        }
        val needsDetached = FloatingCounterGeometry.needsDetachedWindow(
            horizontal = m.offsetX,
            vertical = m.offsetY,
            mirror = m.mirror,
            bubbleSize = FloatingCounterGeometry.BUBBLE_SIZE_DP,
            visualScale = m.scale,
        )
        if (!needsDetached) {
            hide()
            return
        }

        val density = context.resources.displayMetrics.density
        val leading = if (m.mirror) {
            FloatingCounterGeometry.HOST_TRAILING_PADDING_DP
        } else {
            FloatingCounterGeometry.HOST_LEADING_PADDING_DP
        }
        val ballCenterX = location[0] + (leading + FloatingCounterGeometry.BUBBLE_SIZE_DP / 2f) * density
        val ballCenterY = location[1] +
            (FloatingCounterGeometry.HOST_VERTICAL_PADDING_DP + FloatingCounterGeometry.BUBBLE_SIZE_DP / 2f) * density
        val displacement = FloatingCounterGeometry.displacement(
            horizontal = m.offsetX,
            vertical = m.offsetY,
            mirror = m.mirror,
            bubbleSize = FloatingCounterGeometry.BUBBLE_SIZE_DP,
        )
        val centerX = ballCenterX + displacement.x * m.scale * density
        val centerY = ballCenterY + displacement.y * m.scale * density
        val width = (FloatingCounterGeometry.BUBBLE_SIZE_DP / 2f * m.scale * density)
            .roundToInt().coerceAtLeast(1)
        val height = (FloatingCounterGeometry.BUBBLE_SIZE_DP / 4f * m.scale * density)
            .roundToInt().coerceAtLeast(1)

        if (!window.place(
                x = (centerX - width / 2f).roundToInt(),
                y = (centerY - height / 2f).roundToInt(),
                width = width,
                height = height,
            ) || !window.show()
        ) {
            setActive(false)
            return
        }
        setActive(true)
    }

    private fun hide() {
        window.hide()
        setActive(false)
    }

    private fun setActive(activeNow: Boolean) {
        if (active == activeNow) return
        active = activeNow
        onActiveChanged(activeNow)
    }

    private fun unbindHost() {
        val listener = preDraw
        val tree = hostTree
        if (listener != null && tree != null && tree.isAlive) {
            tree.removeOnPreDrawListener(listener)
        }
        preDraw = null
        hostTree = null
        host = null
        lastLocationX = Int.MIN_VALUE
        lastLocationY = Int.MIN_VALUE
        lastWidth = -1
        lastHeight = -1
    }

    private fun View.screenLocation(): IntArray? {
        if (!isAttachedToWindow || width <= 0 || height <= 0) return null
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location
    }
}

private class CounterOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val bounds = Rect()
    private var count = 0
    private var fontPath = ""
    private var typeface: Typeface = Typeface.SERIF

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setCounter(count: Int?, path: String) {
        val safeCount = (count ?: 0).coerceAtLeast(0)
        if (safeCount == this.count && path == fontPath) return
        this.count = safeCount
        if (path != fontPath) {
            fontPath = path
            typeface = loadFloatingCounterTypeface(context, path)
        }
        contentDescription = safeCount.toString()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawFloatingCounter(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            count = count,
            typeface = typeface,
            paint = paint,
            bounds = bounds,
        )
    }
}

private class CounterWindow(
    private val context: Context,
    private val view: CounterOverlayView,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // On Android 12+, NOT_TOUCHABLE alone does not permit taps through an opaque overlay.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val input = context.getSystemService(android.hardware.input.InputManager::class.java)
            alpha = ((input?.maximumObscuringOpacityForTouch ?: 0.8f) - 0.01f).coerceIn(0f, 1f)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    private var attached = false

    fun show(): Boolean {
        if (attached) return true
        if (!Settings.canDrawOverlays(context)) return false
        return try {
            windowManager.addView(view, params)
            attached = true
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "counter overlay could not be attached", e)
            false
        }
    }

    fun place(x: Int, y: Int, width: Int, height: Int): Boolean {
        if (params.x == x && params.y == y && params.width == width && params.height == height) {
            return true
        }
        params.x = x
        params.y = y
        params.width = width
        params.height = height
        if (!attached) return true // show() will attach at the already configured bounds.
        return try {
            windowManager.updateViewLayout(view, params)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "counter overlay could not be moved", e)
            hide()
            false
        }
    }

    fun hide() {
        if (!attached) return
        attached = false
        try {
            windowManager.removeViewImmediate(view)
        } catch (e: RuntimeException) {
            Log.w(TAG, "counter overlay could not be detached", e)
        }
    }
}

internal fun loadFloatingCounterTypeface(context: Context, fontPath: String): Typeface {
    if (fontPath.isBlank()) return Typeface.SERIF
    return runCatching {
        val root = context.filesDir.canonicalFile
        val font = java.io.File(root, fontPath).canonicalFile
        if (font.path.startsWith(root.path + java.io.File.separator) && font.isFile) {
            Typeface.createFromFile(font)
        } else {
            Typeface.SERIF
        }
    }.getOrDefault(Typeface.SERIF)
}

internal fun drawFloatingCounter(
    canvas: Canvas,
    width: Float,
    height: Float,
    count: Int,
    typeface: Typeface,
    paint: Paint,
    bounds: Rect,
) {
    if (width <= 0f || height <= 0f) return
    val label = count.coerceAtLeast(0).toString()
    paint.typeface = typeface
    if (!label.all { paint.hasGlyph(it.toString()) }) paint.typeface = Typeface.SERIF
    paint.textSize = height * 2f
    paint.getTextBounds(label, 0, label.length, bounds)
    if (bounds.width() <= 0 || bounds.height() <= 0) return
    val stroke = height * 0.055f
    val fit = minOf(
        (width - 2f * stroke) / bounds.width(),
        (height - 2f * stroke) / bounds.height(),
    ).coerceAtLeast(0f)
    paint.textSize *= fit
    paint.getTextBounds(label, 0, label.length, bounds)
    val x = width / 2f - (bounds.left + bounds.right) / 2f
    val baseline = height / 2f - (bounds.top + bounds.bottom) / 2f
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = stroke
    paint.strokeJoin = Paint.Join.ROUND
    paint.color = android.graphics.Color.argb(150, 24, 22, 25)
    canvas.drawText(label, x, baseline, paint)
    paint.style = Paint.Style.FILL
    paint.color = FloatingCounterVisuals.colorArgb(count)
    canvas.drawText(label, x, baseline, paint)
}
