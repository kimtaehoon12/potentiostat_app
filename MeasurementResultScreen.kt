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

    LaunchedEffect(Unit) {
        // 저장된 파일 리스트 불러오기
        fileList = context.filesDir.listFiles { file -> file.name.endsWith(".json") }?.toList() ?: emptyList()
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
                // 파일 리스트 표시
                LazyColumn {
                    items(fileList) { file ->
                        Text(
                            text = file.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFile = file
                                    loadFileData(file, context, isFiltered) { data ->
                                        graphData = data
                                    }
                                }
                                .padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // 선택된 파일의 플롯 표시
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Selected File: ${selectedFile?.name ?: "None"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { selectedFile = null }) {
                        Text("Back to File List")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                if (graphData.isNotEmpty()) {
                    PlotData(
                        data = graphData,
                        type = if (isFiltered) "Filtered" else "Raw",
                        label = if (isFiltered) "Filtered Data" else "Raw Data" // label 추가
                    )
                } else {
                    Text("No data to display", style = MaterialTheme.typography.bodyMedium)
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
