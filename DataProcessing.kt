package com.example.myapplication

object DataProcessing {

    // SciPy로 계산한 Butterworth 필터 계수
    private val filterCoefficients = mapOf(
        1 to FilterCoefficients(
            b = listOf(1.53324552e-09, 6.13298208e-09, 9.19947312e-09, 6.13298208e-09, 1.53324552e-09),
            a = listOf(1.0, -3.9671626, 5.90202586, -3.90255878, 0.96769554)
        ),
        3 to FilterCoefficients(
            b = listOf(1.20231162e-07, 4.80924648e-07, 7.21386972e-07, 4.80924648e-07, 1.20231162e-07),
            a = listOf(1.0, -3.90149030, 5.70929400, -3.71397992, 0.90617815)
        ),
        10 to FilterCoefficients(
            b = listOf(1.32937289e-05, 5.31749156e-05, 7.97623734e-05, 5.31749156e-05, 1.32937289e-05),
            a = listOf(1.0, -3.67172909, 5.06799839, -3.11596693, 0.71991033)
        )
    )

    // Zero-Phase Butterworth 필터
    fun zeroPhaseFilter(data: List<Double>, scanRate: Int, padlen: Int = 400): List<Double> {
        if (data.isEmpty()) return data

        // 컷오프 주파수 선택
        val cutoffFrequency = when (scanRate) {
            in 5..10 -> 1
            in 11..100 -> 3
            in 101..Int.MAX_VALUE -> 10
            else -> 1 // 기본값
        }

        val coefficients = filterCoefficients[cutoffFrequency]
            ?: throw IllegalArgumentException("No coefficients available for cutoff frequency: $cutoffFrequency")

        // 데이터 패딩
        val paddedData = applyPadding(data, padlen)

        // 순방향 필터링
        val forwardFiltered = applyButterworthFilter(paddedData, coefficients)

        // 역방향 필터링을 위한 데이터 준비
        val reversedData = forwardFiltered.reversed()

        // **역방향 필터링을 위한 패딩 적용**
        val reversedPaddedData = applyPadding(reversedData, padlen)

        // 역방향 필터링
        val backwardFiltered = applyButterworthFilter(reversedPaddedData, coefficients)

        // 패딩 제거 및 역순 복원
        val filteredData = removePadding(backwardFiltered, data.size, padlen * 2).reversed()

        return filteredData
    }

    private fun applyPadding(data: List<Double>, padlen: Int): List<Double> {
        val startPadding = data.subList(1, padlen + 1).reversed().map { 2 * data.first() - it }
        val endPadding = data.takeLast(padlen + 1).dropLast(1).reversed().map { 2 * data.last() - it }
        return startPadding + data + endPadding
    }

    private fun removePadding(filteredData: List<Double>, originalSize: Int, padlen: Int): List<Double> {
        val start = padlen
        val end = filteredData.size - padlen
        return filteredData.subList(start, end)
    }

    private fun applyButterworthFilter(
        data: List<Double>,
        coefficients: FilterCoefficients
    ): List<Double> {
        val b = coefficients.b
        val a = coefficients.a
        val output = MutableList(data.size) { 0.0 }

        for (i in data.indices) {
            val x = (b.indices).sumOf { j ->
                if (i - j >= 0) data[i - j] * b[j] else 0.0
            }
            val y = (1 until a.size).sumOf { j ->
                if (i - j >= 0) output[i - j] * a[j] else 0.0
            }
            output[i] = x - y
        }

        return output
    }
}

// 데이터 클래스
data class FilterCoefficients(val b: List<Double>, val a: List<Double>)
