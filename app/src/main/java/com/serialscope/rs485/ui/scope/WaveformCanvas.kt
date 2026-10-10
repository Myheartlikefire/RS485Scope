package com.serialscope.rs485.ui.scope

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.serialscope.rs485.core.SignalStats
import com.serialscope.rs485.ui.theme.ScopeColors
import kotlin.math.max
import kotlin.math.min

/** Smooths the auto scale range so the trace does not jump between frames. */
private class AutoRange {
    var min = -1f
    var max = 1f
    private var ready = false

    fun update(targetMin: Float, targetMax: Float) {
        if (!ready) {
            min = targetMin
            max = targetMax
            ready = true
            return
        }
        min += (targetMin - min) * 0.25f
        max += (targetMax - max) * 0.25f
    }
}

/**
 * Oscilloscope style plot.
 *
 * When the visible window holds more samples than the plot has pixel columns the
 * trace is drawn as a per column min/max envelope, which keeps narrow spikes
 * visible instead of aliasing them away.
 */
@Composable
fun WaveformCanvas(
    tick: State<Long>,
    traces: Array<FloatArray>,
    lengths: IntArray,
    rawTraces: Array<FloatArray>,
    rawLengths: IntArray,
    colors: IntArray,
    visible: BooleanArray,
    stats: Array<SignalStats>,
    autoScale: Boolean,
    manualMin: Float,
    manualMax: Float,
    showGrid: Boolean,
    modifier: Modifier = Modifier
) {
    val auto = remember { AutoRange() }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
            color = android.graphics.Color.argb(190, 143, 166, 186)
        }
    }
    val traceLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
            isFakeBoldText = true
        }
    }

    Canvas(modifier) {
        tick.value // draw phase read: invalidates the draw each UI frame

        val padLeft = 8.dp.toPx()
        val padRight = 8.dp.toPx()
        val padTop = 8.dp.toPx()
        val padBottom = 8.dp.toPx()
        val plotLeft = padLeft
        val plotTop = padTop
        val plotRight = size.width - padRight
        val plotBottom = size.height - padBottom
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

        drawRect(color = ScopeColors.Background)

        var lo: Float
        var hi: Float
        if (autoScale) {
            var mn = Float.MAX_VALUE
            var mx = -Float.MAX_VALUE
            for (i in traces.indices) {
                if (!visible[i] || lengths[i] < 2) continue
                mn = min(mn, stats[i].min)
                mx = max(mx, stats[i].max)
            }
            if (mn > mx) {
                mn = -1f
                mx = 1f
            }
            var span = mx - mn
            if (span <= 0f) span = max(1f, kotlin.math.abs(mx))
            val padding = span * 0.08f
            auto.update(mn - padding, mx + padding)
            lo = auto.min
            hi = auto.max
        } else {
            lo = manualMin
            hi = manualMax
        }
        var span = hi - lo
        if (span <= 1e-9f) {
            lo -= 0.5f
            hi += 0.5f
            span = 1f
        }

        fun yOf(v: Float): Float =
            plotBottom - (v - lo) / span * plotHeight

        if (showGrid) {
            val divisionsX = 10
            val divisionsY = 8
            val gridStroke = 1f
            for (k in 0..divisionsX) {
                val x = plotLeft + plotWidth * k / divisionsX
                val strong = k == 0 || k == divisionsX || k == divisionsX / 2
                drawLine(
                    color = if (strong) ScopeColors.GridStrong else ScopeColors.Grid,
                    start = Offset(x, plotTop),
                    end = Offset(x, plotBottom),
                    strokeWidth = gridStroke
                )
            }
            for (k in 0..divisionsY) {
                val y = plotTop + plotHeight * k / divisionsY
                val strong = k == 0 || k == divisionsY || k == divisionsY / 2
                drawLine(
                    color = if (strong) ScopeColors.GridStrong else ScopeColors.Grid,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = gridStroke
                )
            }
        }

        // Y axis scale readout
        val native = drawContext.canvas.nativeCanvas
        labelPaint.textAlign = Paint.Align.LEFT
        val yTop = hi
        val yMid = (hi + lo) / 2f
        val yBottom = lo
        native.drawText(formatAxis(yTop), plotLeft + 6f, plotTop + 30f, labelPaint)
        native.drawText(formatAxis(yMid), plotLeft + 6f, plotTop + plotHeight / 2f - 6f, labelPaint)
        native.drawText(
            formatAxis(yBottom),
            plotLeft + 6f,
            plotBottom - 8f,
            labelPaint
        )

        val strokeWidth = 1.6.dp.toPx()

        // optional raw overlay behind the filtered trace
        for (i in traces.indices) {
            if (!visible[i] || rawLengths[i] < 2) continue
            drawTrace(
                data = rawTraces[i],
                count = rawLengths[i],
                color = Color(colors[i]).copy(alpha = 0.32f),
                strokeWidth = 1f,
                plotLeft = plotLeft,
                plotTop = plotTop,
                plotWidth = plotWidth,
                plotHeight = plotHeight,
                yOf = ::yOf
            )
        }

        for (i in traces.indices) {
            if (!visible[i] || lengths[i] < 2) continue
            val color = Color(colors[i])
            drawTrace(
                data = traces[i],
                count = lengths[i],
                color = color,
                strokeWidth = strokeWidth,
                plotLeft = plotLeft,
                plotTop = plotTop,
                plotWidth = plotWidth,
                plotHeight = plotHeight,
                yOf = ::yOf
            )
            // channel tag next to the newest sample
            val last = traces[i][lengths[i] - 1]
            if (!last.isNaN()) {
                traceLabelPaint.color = colors[i]
                native.drawText(
                    "CH${i + 1}",
                    plotRight - 62f,
                    yOf(last).coerceIn(plotTop + 26f, plotBottom - 6f),
                    traceLabelPaint
                )
            }
        }

        drawRect(
            color = ScopeColors.Outline,
            topLeft = Offset(plotLeft, plotTop),
            size = androidx.compose.ui.geometry.Size(plotWidth, plotHeight),
            style = Stroke(width = 1f)
        )
    }
}

private fun formatAxis(value: Float): String {
    val a = kotlin.math.abs(value)
    return when {
        a == 0f -> "0"
        a >= 1000f -> "%.2g".format(value)
        a >= 1f -> "%.3g".format(value)
        a >= 0.001f -> "%.3g".format(value)
        else -> "%.1e".format(value)
    }
}

private fun DrawScope.drawTrace(
    data: FloatArray,
    count: Int,
    color: Color,
    strokeWidth: Float,
    plotLeft: Float,
    plotTop: Float,
    plotWidth: Float,
    plotHeight: Float,
    yOf: (Float) -> Float
) {
    val path = Path()
    val columns = plotWidth.toInt().coerceAtLeast(1)
    var lastValid = 0f
    var hasValid = false

    fun valueAt(index: Int): Float {
        val v = data[index]
        return if (v.isNaN() || v.isInfinite()) {
            if (hasValid) lastValid else 0f
        } else {
            lastValid = v
            hasValid = true
            v
        }
    }

    if (count <= columns) {
        for (k in 0 until count) {
            val x = plotLeft + plotWidth * k / (count - 1).coerceAtLeast(1)
            val y = yOf(valueAt(k)).coerceIn(plotTop, plotTop + plotHeight)
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    } else {
        var started = false
        for (c in 0 until columns) {
            val start = (c.toLong() * count / columns).toInt()
            val end = ((c + 1L) * count / columns).toInt().coerceAtMost(count)
            if (end <= start) continue
            var mn = Float.MAX_VALUE
            var mx = -Float.MAX_VALUE
            for (k in start until end) {
                val v = valueAt(k)
                if (v < mn) mn = v
                if (v > mx) mx = v
            }
            val x = plotLeft + c.toFloat()
            val yHigh = yOf(mx).coerceIn(plotTop, plotTop + plotHeight)
            val yLow = yOf(mn).coerceIn(plotTop, plotTop + plotHeight)
            if (!started) {
                path.moveTo(x, yHigh)
                started = true
            }
            path.lineTo(x, yHigh)
            path.lineTo(x, yLow)
        }
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}