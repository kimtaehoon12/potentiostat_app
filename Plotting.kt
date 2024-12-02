package com.example.myapplication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

@Composable
fun PlotData(
    data: List<Pair<Double, Double>>,
    label: String,
    type: String,
    xMin: Double? = null,
    xMax: Double? = null,
    yMin: Double? = null,
    yMax: Double? = null,
    xStep: Double = 1.0,
    yStep: Double = 0.2
) {
    // 데이터의 X축과 Y축 범위 설정
    val actualXMin = xMin ?: data.minOfOrNull { it.first } ?: 0.0
    val actualXMax = xMax ?: data.maxOfOrNull { it.first } ?: 1.0
    val actualYMin = yMin ?: data.minOfOrNull { it.second } ?: 0.0
    val actualYMax = yMax ?: data.maxOfOrNull { it.second } ?: 1.0
    val actualXStep = xStep.takeIf { it > 0 } ?: ((actualXMax - actualXMin) / 10)
    val actualYStep = yStep.takeIf { it > 0 } ?: ((actualYMax - actualYMin) / 10)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        val width = size.width
        val height = size.height

        val padding = 50f
        val contentWidth = width - 2 * padding
        val contentHeight = height - 2 * padding

        val xScale = contentWidth / (actualXMax - actualXMin).toFloat()
        val yScale = contentHeight / (actualYMax - actualYMin).toFloat()

        // 축 그리기
        drawLine(
            color = Color.Black,
            start = Offset(padding, height - padding),
            end = Offset(width - padding, height - padding),
            strokeWidth = 2f
        ) // X축
        drawLine(
            color = Color.Black,
            start = Offset(padding, padding),
            end = Offset(padding, height - padding),
            strokeWidth = 2f
        ) // Y축

        // X축 라벨
        var xValue = actualXMin
        while (xValue <= actualXMax) {
            val xOffset = padding + (xValue - actualXMin).toFloat() * xScale
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.2f", xValue),
                xOffset,
                height - padding / 2,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 24f
                }
            )
            xValue += actualXStep
        }

        // Y축 라벨
        var yValue = actualYMin
        while (yValue <= actualYMax) {
            val yOffset = height - padding - (yValue - actualYMin).toFloat() * yScale
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.2f", yValue),
                padding / 2,
                yOffset,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 24f
                }
            )
            yValue += actualYStep
        }

        // 그래프 데이터 그리기
        val path = Path().apply {
            data.forEachIndexed { index, (xValue, yValue) ->
                val x = padding + (xValue - actualXMin).toFloat() * xScale
                val y = height - padding - (yValue - actualYMin).toFloat() * yScale
                if (index == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x, y)
                }
            }
        }

        drawPath(
            path = path,
            color = if (type == "Filtered") Color.Green else Color.Blue,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )
    }
}


