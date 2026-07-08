package com.travelassistant.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelassistant.app.data.model.Candle
import com.travelassistant.app.ui.theme.Divider
import com.travelassistant.app.ui.theme.Down
import com.travelassistant.app.ui.theme.TextMuted
import com.travelassistant.app.ui.theme.Up
import kotlin.math.max

enum class ChartMode { CANDLES, LINE }

/**
 * Price chart for future departure dates: candlesticks or a filled line, with a price
 * grid (right axis) and departure-date labels (bottom axis).
 */
@Composable
fun PriceChart(
    candles: List<Candle>,
    mode: ChartMode,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = TextMuted, fontSize = 10.sp)
    Canvas(modifier = modifier) {
        if (candles.size < 2) return@Canvas
        val axisWidth = 64f
        val bottomAxis = if (xLabels.isEmpty()) 0f else 30f
        val plotWidth = size.width - axisWidth
        val plotHeight = size.height - bottomAxis
        var minP = candles.minOf { it.low }
        var maxP = candles.maxOf { it.high }
        val pad = (maxP - minP).takeIf { it > 0 }?.times(0.08) ?: 1.0
        minP -= pad
        maxP += pad
        val range = (maxP - minP).takeIf { it > 0 } ?: 1.0

        fun yFor(price: Double): Float =
            (plotHeight - ((price - minP) / range * plotHeight)).toFloat()

        // ---- Grid + right-side price labels ----
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = plotHeight / gridLines * i
            drawLine(
                color = Divider.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(plotWidth, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
            )
            val price = maxP - (range / gridLines * i)
            val layout = measurer.measure("%,.0f".format(price), labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = plotWidth + 8f,
                    y = (y - layout.size.height / 2f).coerceIn(0f, plotHeight - layout.size.height),
                ),
            )
        }

        when (mode) {
            ChartMode.LINE -> drawLineChart(candles, plotWidth, plotHeight, ::yFor)
            ChartMode.CANDLES -> drawCandles(candles, plotWidth, ::yFor)
        }

        // ---- Bottom departure-date labels (a sparse subset to avoid crowding) ----
        if (xLabels.isNotEmpty()) {
            val slots = 4
            for (s in 0..slots) {
                val idx = (xLabels.size - 1) * s / slots
                val label = xLabels.getOrNull(idx) ?: continue
                val layout = measurer.measure(label, labelStyle)
                val cx = plotWidth * s / slots
                val x = when (s) {
                    0 -> 0f
                    slots -> (plotWidth - layout.size.width).coerceAtLeast(0f)
                    else -> cx - layout.size.width / 2f
                }
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x, plotHeight + 8f),
                )
            }
        }
    }
}

private fun DrawScope.drawCandles(
    candles: List<Candle>,
    plotWidth: Float,
    yFor: (Double) -> Float,
) {
    val slot = plotWidth / candles.size
    val bodyWidth = max(2f, slot * 0.6f)
    candles.forEachIndexed { i, c ->
        val cx = slot * i + slot / 2f
        val color = if (c.isBullish) Up else Down
        drawLine(
            color = color,
            start = Offset(cx, yFor(c.high)),
            end = Offset(cx, yFor(c.low)),
            strokeWidth = max(1f, bodyWidth * 0.14f),
        )
        val top = yFor(max(c.open, c.close))
        val bottom = yFor(minOf(c.open, c.close))
        val height = max(1.5f, bottom - top)
        drawRect(
            color = color,
            topLeft = Offset(cx - bodyWidth / 2f, top),
            size = Size(bodyWidth, height),
            style = Fill,
        )
    }
}

private fun DrawScope.drawLineChart(
    candles: List<Candle>,
    plotWidth: Float,
    plotHeight: Float,
    yFor: (Double) -> Float,
) {
    val stepX = plotWidth / (candles.size - 1)
    val up = candles.last().close >= candles.first().close
    val color = if (up) Up else Down

    fun x(i: Int) = stepX * i
    val line = Path().apply {
        moveTo(0f, yFor(candles.first().close))
        for (i in 1 until candles.size) lineTo(x(i), yFor(candles[i].close))
    }
    val fill = Path().apply {
        addPath(line)
        lineTo(plotWidth, plotHeight)
        lineTo(0f, plotHeight)
        close()
    }
    drawPath(
        path = fill,
        brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)),
        style = Fill,
    )
    drawPath(path = line, color = color, style = Stroke(width = 2.5.dp.toPx()))
    val lastX = x(candles.size - 1)
    val lastY = yFor(candles.last().close)
    drawCircle(color = color.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = Offset(lastX, lastY))
    drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
}
