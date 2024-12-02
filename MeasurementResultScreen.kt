package com.example.myapplication

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.io.File
import com.google.gson.Gson
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.util.Log
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementResultScreen(context: Context, navController: NavController) {
    var fileList by remember { mutableStateOf(listOf<File>()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var graphData by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var isFiltered by remember { mutableStateOf(false) }

    // 그래프 축 설정
    var xMin by remember { mutableStateOf<Double?>(null) }
    var xMax by remember { mutableStateOf<Double?>(null) }
    var yMin by remember { mutableStateOf<Double?>(null) }
    var yMax by remember { mutableStateOf<Double?>(null) }
    var xStep by remember { mutableStateOf(1.0) }
    var yStep by remember { mutableStateOf(1.0) }

    LaunchedEffect(Unit) {
        fileList = context.filesDir.listFiles { file -> file.name.endsWith(".json") }?.toList() ?: emptyList()
    }

    LaunchedEffect(selectedFile) {
        selectedFile?.let { file ->
            loadFileData(file, context, isFiltered) { data ->
                graphData = data

                // 초기 축 설정
                if (data.isNotEmpty()) {
                    xMin = data.minOfOrNull { it.first }
                    xMax = data.maxOfOrNull { it.first }
                    yMin = data.minOfOrNull { it.second }
                    yMax = data.maxOfOrNull { it.second }
                    xStep = ((xMax ?: 1.0) - (xMin ?: 0.0)) / 10 // 기본 10등분
                    yStep = ((yMax ?: 1.0) - (yMin ?: 0.0)) / 10
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Measurement Results") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            if (selectedFile == null) {
                LazyColumn {
                    items(fileList) { file ->
                        Text(
                            text = file.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFile = file
                                }
                                .padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                Text("Selected File: ${selectedFile?.name ?: "None"}", style = MaterialTheme.typography.bodyMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (isFiltered) "Filtered Data" else "Raw Data")
                    Switch(checked = isFiltered, onCheckedChange = {
                        isFiltered = it
                        selectedFile?.let { file ->
                            loadFileData(file, context, isFiltered) { data ->
                                graphData = data
                            }
                        }
                    })
                }

                if (graphData.isNotEmpty()) {
                    PlotData(
                        data = graphData,
                        type = if (isFiltered) "Filtered" else "Raw",
                        label = if (isFiltered) "Filtered Data" else "Raw Data",
                        xMin = xMin,
                        xMax = xMax,
                        yMin = yMin,
                        yMax = yMax,
                        xStep = xStep,
                        yStep = yStep
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 축 설정 입력 필드
                    Column {
                        Text("Graph Settings", style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = xMin?.toString() ?: "",
                                onValueChange = { xMin = it.toDoubleOrNull() },
                                label = { Text("X Min") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = xMax?.toString() ?: "",
                                onValueChange = { xMax = it.toDoubleOrNull() },
                                label = { Text("X Max") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = xStep.toString(),
                                onValueChange = { xStep = it.toDoubleOrNull() ?: 1.0 },
                                label = { Text("X Step") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = yMin?.toString() ?: "",
                                onValueChange = { yMin = it.toDoubleOrNull() },
                                label = { Text("Y Min") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = yMax?.toString() ?: "",
                                onValueChange = { yMax = it.toDoubleOrNull() },
                                label = { Text("Y Max") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = yStep.toString(),
                                onValueChange = { yStep = it.toDoubleOrNull() ?: 1.0 },
                                label = { Text("Y Step") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Text("No data to display")
                }
            }
        }
    }
}

private fun loadFileData(
    file: File,
    context: Context,
    isFiltered: Boolean,
    onDataLoaded: (List<Pair<Double, Double>>) -> Unit
) {
    try {
        val jsonData = file.readText()
        val dataList = Gson().fromJson<List<Map<String, Any>>>(jsonData, object : TypeToken<List<Map<String, Any>>>() {}.type)

        val graphData = dataList.mapNotNull {
            val x = it["pwmCal"] as? Double ?: it["time"] as? Double
            val y = if (isFiltered) it["valCalFiltered"] as? Double else it["valCalRaw"] as? Double
            if (x != null && y != null) x to y else null
        }

        onDataLoaded(graphData)
    } catch (e: Exception) {
        Log.e("MeasurementResultScreen", "Error loading data: ${e.message}")
        onDataLoaded(emptyList())
    }
}
