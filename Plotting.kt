package com.example.myapplication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

@Composable
fun PlotData(data: List<Pair<Double, Double>>, label: String, type: String) {
    // 데이터의 X축과 Y축 범위 계산
    val maxX = data.maxOfOrNull { it.first } ?: 1.0
    val maxY = data.maxOfOrNull { it.second } ?: 1.0
    val minX = data.minOfOrNull { it.first } ?: 0.0
    val minY = data.minOfOrNull { it.second } ?: 0.0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp) // 그래프 높이
    ) {
        val width = size.width
        val height = size.height

        // 스케일링 계산
        val xScale = width / (maxX - minX).toFloat()
        val yScale = height / (maxY - minY).toFloat()

        // 선 그래프 생성
        val path = Path().apply {
            data.forEachIndexed { index, (xValue, yValue) ->
                val x = ((xValue - minX) * xScale).toFloat()
                val y = (height - ((yValue - minY) * yScale)).toFloat() // Y축 뒤집기
                if (index == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x, y)
                }
            }
        }

        // 그래프 선 그리기
        drawPath(
            path = path,
            color = Color.Blue,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )

        // 데이터 포인트 그리기
        data.forEach { (xValue, yValue) ->
            val x = ((xValue - minX) * xScale).toFloat()
            val y = (height - ((yValue - minY) * yScale)).toFloat()
            drawCircle(
                color = Color.Red,
                radius = 4f,
                center = Offset(x, y)
            )
        }

        // 축 그리기
        drawLine(
            color = Color.Black,
            start = Offset(0f, height),
            end = Offset(width, height),
            strokeWidth = 2f
        ) // X축
        drawLine(
            color = Color.Black,
            start = Offset(0f, 0f),
            end = Offset(0f, height),
            strokeWidth = 2f
        ) // Y축
    }
}