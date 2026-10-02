package io.github.androidmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.androidmonitor.data.HISTORY_SIZE
import io.github.androidmonitor.ui.util.niceCeil

@Immutable
data class ChartSeries(
    val values: List<Float>,
    val color: Color,
    val fill: Boolean = true,
    val dashed: Boolean = false,
)

/**
 * A Task Manager style graph: newest sample on the right, a light grid, a filled line,
 * and a frame in the metric's color. [maxValue] null means auto-scale.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    maxValue: Float? = 100f,
    frameColor: Color = series.firstOrNull()?.color ?: MaterialTheme.colorScheme.outline,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    showGrid: Boolean = true,
    capacity: Int = HISTORY_SIZE,
) {
    Canvas(modifier.clipToBounds()) {
        val width = size.width
        val height = size.height
        val top = maxValue ?: niceCeil(series.maxOfOrNull { s -> s.values.maxOrNull() ?: 0f } ?: 0f)

        if (showGrid) {
            for (i in 1 until 10) {
                val x = width * i / 10
                drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
            }
            for (i in 1 until 5) {
                val y = height * i / 5
                drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
            }
        }

        val stepX = width / (capacity - 1)
        for (s in series) {
            val values = s.values.takeLast(capacity)
            if (values.size < 2) continue
            val offset = capacity - values.size
            val line = Path()
            values.forEachIndexed { i, value ->
                val x = (offset + i) * stepX
                val y = height - (value / top).coerceIn(0f, 1f) * height
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            if (s.fill) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(width, height)
                    lineTo(offset * stepX, height)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(listOf(s.color.copy(alpha = 0.32f), s.color.copy(alpha = 0.04f))),
                )
            }
            drawPath(
                line,
                s.color,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (s.dashed) {
                        PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    } else {
                        null
                    },
                ),
            )
        }

        drawRect(frameColor, style = Stroke(width = 1.dp.toPx()))
    }
}
