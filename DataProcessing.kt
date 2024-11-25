package com.example.myapplication

import kotlin.math.PI
import kotlin.math.exp

object DataProcessing {
    fun zeroPhaseFilter(data: List<Double>, scanRate: Int): List<Double> {
        val cutoffFrequency = when {
            scanRate in 5..10 -> 1.0
            scanRate in 11..100 -> 3.0
            scanRate >= 101 -> 10.0
            else -> 1.0 // 기본값
        }
        val filteredData = mutableListOf<Double>()

        for (i in data.indices) {
            val weight = exp(-2 * PI * cutoffFrequency * i)
            val smoothedValue = data[i] * weight
            filteredData.add(smoothedValue)
        }

        return filteredData
    }
}
