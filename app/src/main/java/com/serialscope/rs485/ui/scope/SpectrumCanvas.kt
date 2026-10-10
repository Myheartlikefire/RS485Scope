package com.serialscope.rs485.ui.scope

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.serialscope.rs485.ui.theme.ScopeColors

/**
 * Magnitude spectrum of the currently selected channel, useful to verify that a
 * notch really removed a tone. Bins come in as dB values from [com.serialscope.rs485.dsp.Fft].
 */
@Composable
fun SpectrumCanvas(
    tick: State<Long>,
    bins: FloatArray,
    binCount: Int,
    sampleRate: Double,
    traceColor: Color,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            color = android.graphics.Color.argb(180, 143, 166, 186)
        }
    }

    Canvas(modifier) {
        tick.value

        val plotLeft = 8.dp.toPx()
        val plotTop = 6.dp.toPx()
        val plotRight = size.width - 8.dp.toPx()
        val plotBottom = size.height - 6.dp.toPx()
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

        drawRect(color = ScopeColors.Background)

        val dbTop = 0f
        val dbBottom = -120f
        val nyquist = (sampleRate / 2.0).coerceAtLeast(1.0)

        for (k in 0..8) {
            val x = plotLeft + plotWidth * k / 8f
            drawLine(
                color = if (k % 4 == 0) ScopeColors.GridStrong else ScopeColors.Grid,
                start = Offset(x, plotTop),
                end = Offset(x, plotBottom),
                strokeWidth = 1f
            )
        }
        for (k in 0..4) {
            val y = plotTop + plotHeight * k / 4f
            drawLine(
                color = if (k == 0 || k == 4) ScopeColors.GridStrong else ScopeColors.Grid,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1f
            )
        }

        if (binCount > 1) {
            val path = Path()
            var started = false
            for (k in 0 until binCount) {
                val db = bins[k].coerceIn(dbBottom, dbTop)
                val x = plotLeft + plotWidth * k / (binCount - 1).toFloat()
                val y = plotTop + (dbTop - db) / (dbTop - dbBottom) * plotHeight
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(path = path, color = traceColor, style = Stroke(width = 1.6f))
        }

        val native = drawContext.canvas.nativeCanvas
        native.drawText("0 dB", plotLeft + 6f, plotTop + 26f, labelPaint)
        native.drawText("-120 dB", plotLeft + 6f, plotBottom - 6f, labelPaint)
        native.drawText("DC", plotLeft + 6f, plotBottom + 0f, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        val nyquistLabel = if (nyquist >= 1000) "%.1f kHz".format(nyquist / 1000.0) else "%.0f Hz".format(nyquist)
        native.drawText(nyquistLabel, plotRight - 4f, plotBottom - 6f, labelPaint)
        labelPaint.textAlign = Paint.Align.LEFT

        drawRect(
            color = ScopeColors.Outline,
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotWidth, plotHeight),
            style = Stroke(width = 1f)
        )
    }
}