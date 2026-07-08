package com.travelassistant.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import com.travelassistant.app.ui.theme.Down
import com.travelassistant.app.ui.theme.Up

/** Compact trend line for list rows, colored by overall direction. */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    up: Color = Up,
    down: Color = Down,
) {
    Canvas(modifier = modifier) {
        drawSparkline(values, up, down)
    }
}

private fun DrawScope.drawSparkline(values: List<Double>, up: Color, down: Color) {
    if (values.size < 2) return
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0
    val w = size.width
    val h = size.height
    val stepX = w / (values.size - 1)
    val color = if (values.last() >= values.first()) up else down

    fun pointAt(i: Int): Offset {
        val x = i * stepX
        val norm = (values[i] - min) / range
        val y = h - (norm * h).toFloat()
        return Offset(x, y)
    }

    val line = Path().apply {
        moveTo(0f, pointAt(0).y)
        for (i in 1 until values.size) {
            val p = pointAt(i)
            lineTo(p.x, p.y)
        }
    }
    val fill = Path().apply {
        addPath(line)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        path = fill,
        brush = Brush.verticalGradient(
            listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)),
        ),
        style = Fill,
    )
    drawPath(
        path = line,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
    )
}
