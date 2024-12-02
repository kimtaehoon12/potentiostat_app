package com.example.myapplication
import com.example.myapplication.MeasurementResultScreen
import com.example.myapplication.BluetoothCommunication
import com.example.myapplication.PlotData
import com.example.myapplication.DataProcessing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothSocket
import android.os.IBinder
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import java.util.UUID
import java.io.IOException
import java.io.File
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay


object SharedMeasurementState {
    var isMeasuring by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var allPermissionsGranted = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 모든 권한이 승인되었는지 확인
        allPermissionsGranted.value = permissions.all { it.value }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 권한 요청
        checkAndRequestPermissions()

        setContent {
            if (allPermissionsGranted.value) {
                AppNavigator()
            } else {
                PermissionRequestScreen {
                    checkAndRequestPermissions()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            allPermissionsGranted.value = true
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}

@Composable
fun PermissionRequestScreen(onPermissionGranted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bluetooth permissions are required to use this app.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onPermissionGranted() }) {
            Text("Grant Permissions")
        }
    }
}

// Foreground Service for Measurement
class BluetoothForegroundService : Service() {
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "measurement_channel"
        const val NOTIFICATION_ID = 1

        var bluetoothSocket: BluetoothSocket? = null
            private set
        var isConnected: Boolean = false
            private set

        fun updateConnectionState(socket: BluetoothSocket?) {
            bluetoothSocket = socket
            isConnected = socket != null && socket.isConnected
        }

        val dataQueue: MutableList<String> = mutableListOf()
    }

    private val rawData = mutableListOf<Pair<Double, Double>>()
    private val filteredData = mutableListOf<Pair<Double, Double>>()

    private var duringMeasurement = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val deviceAddress = intent.getStringExtra("device_address")
                val measurementType = intent.getStringExtra("measurement_type")
                if (deviceAddress != null && measurementType != null) {
                    val notification = createNotification("Preparing Measurement...")
                    startForeground(NOTIFICATION_ID, notification)
                    startMeasurement(deviceAddress, measurementType)
                } else {
                    Log.e("BluetoothService", "Invalid intent data")
                }
            }
            ACTION_STOP -> {
                stopMeasurement()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startMeasurement(deviceAddress: String, measurementType: String) {
        if (duringMeasurement) {
            Log.w("BluetoothService", "Measurement already in progress.")
            return
        }
        duringMeasurement = true

        synchronized(dataQueue) {
            dataQueue.clear() // 기존 데이터 초기화
        }

        try {
            if (bluetoothSocket?.isConnected == true) {
                Log.d("BluetoothService", "Using existing Bluetooth connection")
                executeMeasurement(measurementType)
            } else {
                Log.d("BluetoothService", "Reconnecting Bluetooth socket")
                val newSocket = reconnectSocket(deviceAddress)
                if (newSocket != null) {
                    executeMeasurement(measurementType)
                } else {
                    Log.e("BluetoothService", "Failed to reconnect socket.")
                    duringMeasurement = false
                }
            }
        } catch (e: Exception) {
            Log.e("BluetoothService", "Error during startMeasurement: ${e.message}")
            duringMeasurement = false
        }
    }


    @SuppressLint("MissingPermission")
    private fun executeMeasurement(measurementType: String) {
        val sharedPreferences = getSharedPreferences("MeasurementSettings", Context.MODE_PRIVATE)
        try {
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                Log.d("BluetoothService", "Socket disconnected, attempting reconnect.")
                val deviceAddress = bluetoothSocket?.remoteDevice?.address
                if (deviceAddress != null) {
                    bluetoothSocket = reconnectSocket(deviceAddress)
                }
            }

            bluetoothSocket?.let { socket ->
                val command = when (measurementType) {
                    "CV" -> createCVCommand()
                    "CA" -> createCACommand()
                    else -> throw IllegalArgumentException("Invalid measurement type")
                }

                BluetoothCommunication.sendData(socket, command)
                BluetoothCommunication.receiveData(socket) { receivedData ->
                    if (receivedData.trim() == "done") {
                        SharedMeasurementState.isMeasuring = false
                        val scanRate = if (measurementType == "CA") 5 else {
                            sharedPreferences.getString("scanRate", "50")?.toInt() ?: 50
                        }
                        processAndSaveData(measurementType, scanRate)
                        Log.d("BluetoothService", "Measurement complete.")
                    } else {
                        synchronized(dataQueue) {
                            dataQueue.add(receivedData.trim())
                        }
                        processIncomingData(receivedData)
                    }
                }

            } ?: throw IOException("Socket is null after reconnect attempt.")
        } catch (e: Exception) {
            Log.e("BluetoothService", "Error during executeMeasurement: ${e.message}")
        } finally {
            duringMeasurement = false
        }
    }
    private fun createCVCommand(): String {
        val sharedPreferences = getSharedPreferences("MeasurementSettings", Context.MODE_PRIVATE)
        val minVoltage = sharedPreferences.getString("cvMinVoltage", "-0.5") ?: "-0.5"
        val maxVoltage = sharedPreferences.getString("cvMaxVoltage", "0.5") ?: "0.5"
        val scanRate = sharedPreferences.getString("scanRate", "50") ?: "50"
        val cycle = sharedPreferences.getString("cvCycle", "5") ?: "5"
        return "CV,$minVoltage,$maxVoltage,$scanRate,$cycle\n"
    }

    private fun createCACommand(): String {
        val sharedPreferences = getSharedPreferences("MeasurementSettings", Context.MODE_PRIVATE)
        val caVoltage = sharedPreferences.getString("caVoltage", "0.0") ?: "0.0"
        val caTimeValue = sharedPreferences.getString("caTimeValue", "10") ?: "10"
        val caTimeUnit = sharedPreferences.getString("caTimeUnit", "sec") ?: "sec"
        return "CA,$caVoltage,$caTimeValue,$caTimeUnit\n"
    }


    @SuppressLint("MissingPermission")
    private fun reconnectSocket(deviceAddress: String): BluetoothSocket? {
        return try {
            // 이전 소켓 닫기
            bluetoothSocket?.close()
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            val newSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            newSocket.connect()
            BluetoothForegroundService.updateConnectionState(newSocket)
            newSocket
        } catch (e: IOException) {
            Log.e("BluetoothService", "Failed to reconnect socket: ${e.message}")
            null
        }
    }




    private fun processIncomingData(data: String) {
        val values = data.split(",")
        if (values.size == 2) {
            val x = values[0].toDoubleOrNull()
            val y = values[1].toDoubleOrNull()
            if (x != null && y != null) {
                synchronized(rawData) {
                    rawData.add(x to y)
                }
            }
        }
    }

    private fun processAndSaveData(measurementType: String, scanRate: Int) {
        val filteredValues = rawData.map { it.second }.let {
            DataProcessing.zeroPhaseFilter(it, scanRate)
        }

        filteredData.clear()
        rawData.forEachIndexed { index, pair ->
            if (index < filteredValues.size) {
                filteredData.add(pair.first to filteredValues[index])
            }
        }

        saveCombinedDataToFile(measurementType)
    }

    private fun saveCombinedDataToFile(measurementType: String) {
        val fileIndex = getNextFileIndex(measurementType)
        val fileName = generateFileName(measurementType, fileIndex)

        val combinedData = rawData.mapIndexed { index, pair ->
            val rawValue = pair.second
            val filteredValue = if (index < filteredData.size) filteredData[index].second else null

            when (measurementType) {
                "CV" -> mapOf("pwmCal" to pair.first, "valCalRaw" to rawValue, "valCalFiltered" to filteredValue)
                "CA" -> mapOf("time" to pair.first, "valCalRaw" to rawValue, "valCalFiltered" to filteredValue)
                else -> null
            }
        }.filterNotNull()

        val file = File(applicationContext.filesDir, fileName)
        file.writeText(Gson().toJson(combinedData))
    }

    private fun generateFileName(measurementType: String, fileIndex: Int): String {
        val dateFormat = SimpleDateFormat("yy_MM_dd", Locale.getDefault())
        val date = dateFormat.format(Date())
        return "${measurementType}_${date}_$fileIndex.json"
    }

    private fun getNextFileIndex(measurementType: String): Int {
        val files = applicationContext.filesDir.listFiles { file ->
            file.name.startsWith(measurementType) && file.name.endsWith(".json")
        } ?: emptyArray()
        return files.size + 1
    }

    private fun stopMeasurement() {
        if (!duringMeasurement) return
        duringMeasurement = false

        try {
            // 블루투스 소켓 닫기
            bluetoothSocket?.let { socket ->
                if (socket.isConnected) {
                    socket.close()
                }
            }
            BluetoothForegroundService.updateConnectionState(null) // 연결 상태 초기화
        } catch (e: IOException) {
            Log.e("BluetoothService", "Error while closing socket: ${e.message}")
        } finally {
            try {
                // Foreground Notification 중지
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.e("BluetoothService", "Error stopping foreground: ${e.message}")
            }
            stopSelf() // 서비스 자체를 종료
        }
    }


    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bluetooth Measurement Service")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Measurement Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for Measurement Foreground Service"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}


// Start Measurement Service
fun startMeasurementService(context: Context, measurementType: String, deviceAddress: String) {
    val intent = Intent(context, BluetoothForegroundService::class.java).apply {
        action = BluetoothForegroundService.ACTION_START
        putExtra("measurement_type", measurementType)
        putExtra("device_address", deviceAddress)
    }
    ContextCompat.startForegroundService(context, intent)
}

// Stop Measurement Service
fun stopMeasurementService(context: Context) {
    val intent = Intent(context, BluetoothForegroundService::class.java).apply {
        action = BluetoothForegroundService.ACTION_STOP
    }
    context.stopService(intent)
}

@Composable
fun HomeScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { navController.navigate("bluetooth") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connection")
        }

        Button(
            onClick = { navController.navigate("measurement") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Measurement")
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(navController: NavController) {
    val context = LocalContext.current
    var connectionStatus by remember {
        mutableStateOf(
            if (BluetoothForegroundService.isConnected)
                "Connected to: ${BluetoothForegroundService.bluetoothSocket?.remoteDevice?.name ?: "Unknown"}"
            else
                "No device connected!"
        )
    }

    val receivedDataList = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        while (true) {
            synchronized(BluetoothForegroundService.dataQueue) {
                if (BluetoothForegroundService.dataQueue.isNotEmpty()) {
                    val data = BluetoothForegroundService.dataQueue.removeAt(0) // 첫 번째 항목 제거
                    receivedDataList.add(data)
                }
            }
            delay(10) // 폴링 주기를 10ms로 설정 (필요에 따라 조정 가능)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Measurement") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = connectionStatus,
                color = if (BluetoothForegroundService.isConnected) Color.Green else Color.Red,
                modifier = Modifier.padding(8.dp)
            )

            Button(
                onClick = {
                    if (!SharedMeasurementState.isMeasuring && BluetoothForegroundService.isConnected) {
                        connectionStatus = "Starting CV Measurement..."
                        startMeasurementService(
                            context,
                            "CV",
                            BluetoothForegroundService.bluetoothSocket?.remoteDevice?.address ?: "Unknown"
                        )
                        SharedMeasurementState.isMeasuring = true
                    } else {
                        connectionStatus = "No device connected!"
                    }
                },
                enabled = !SharedMeasurementState.isMeasuring && BluetoothForegroundService.isConnected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (SharedMeasurementState.isMeasuring) "Measuring..." else "CV Measure")
            }

            Button(
                onClick = {
                    if (!SharedMeasurementState.isMeasuring && BluetoothForegroundService.isConnected) {
                        connectionStatus = "Starting CA Measurement..."
                        startMeasurementService(
                            context,
                            "CA",
                            BluetoothForegroundService.bluetoothSocket?.remoteDevice?.address ?: "Unknown"
                        )
                        SharedMeasurementState.isMeasuring = true
                    } else {
                        connectionStatus = "No device connected!"
                    }
                },
                enabled = !SharedMeasurementState.isMeasuring && BluetoothForegroundService.isConnected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (SharedMeasurementState.isMeasuring) "Measuring..." else "CA Measure")
            }

            Button(
                onClick = {
                    connectionStatus = "Stopping Measurement..."
                    stopMeasurementService(context)
                    SharedMeasurementState.isMeasuring = false
                },
                enabled = SharedMeasurementState.isMeasuring,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Measurement")
            }

            Button(
                onClick = { navController.navigate("measurement_setting") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Measurement Setting")
            }

            Button(
                onClick = { navController.navigate("measurement_result") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Measurement Result")
            }


            Text("Received Data", style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                modifier = Modifier.fillMaxHeight(0.5f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(receivedDataList) { data ->
                    Text(text = data)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementSettingScreen(navController: NavController) {
    val context = LocalContext.current

    // SharedPreferences 객체 생성
    val sharedPreferences = context.getSharedPreferences("MeasurementSettings", Context.MODE_PRIVATE)

    // CV settings state
    var cvMinVoltage by remember { mutableStateOf(sharedPreferences.getString("cvMinVoltage", "-0.5") ?: "-0.5") }
    var cvMaxVoltage by remember { mutableStateOf(sharedPreferences.getString("cvMaxVoltage", "0.5") ?: "0.5") }
    var scanRate by remember { mutableStateOf(sharedPreferences.getString("scanRate", "50") ?: "50") }
    var cvCycle by remember { mutableStateOf(sharedPreferences.getString("cvCycle", "1") ?: "1") } // Cycle 추가

    // CA settings state
    var caVoltage by remember { mutableStateOf(sharedPreferences.getString("caVoltage", "0.0") ?: "0.0") }
    var caTimeValue by remember { mutableStateOf(sharedPreferences.getString("caTimeValue", "10") ?: "10") }
    var caTimeUnit by remember { mutableStateOf(sharedPreferences.getString("caTimeUnit", "sec") ?: "sec") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Measurement Setting") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // CV Setting Section
            Text("CV Setting", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = cvMinVoltage,
                onValueChange = { cvMinVoltage = it },
                label = { Text("Min Voltage (V)") },
                placeholder = { Text("e.g., -0.5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cvMaxVoltage,
                onValueChange = { cvMaxVoltage = it },
                label = { Text("Max Voltage (V)") },
                placeholder = { Text("e.g., 0.5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = scanRate,
                onValueChange = { scanRate = it },
                label = { Text("Scan Rate (mV/s)") },
                placeholder = { Text("e.g., 50") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cvCycle,
                onValueChange = { cvCycle = it },
                label = { Text("Cycle Count") }, // Cycle 설정 추가
                placeholder = { Text("e.g., 1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // CA Setting Section
            Text("CA Setting", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = caVoltage,
                onValueChange = { caVoltage = it },
                label = { Text("CA Voltage (V)") },
                placeholder = { Text("e.g., 0.0") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = caTimeValue,
                    onValueChange = { caTimeValue = it },
                    label = { Text("Time Value") },
                    placeholder = { Text("e.g., 10") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                var expanded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = caTimeUnit)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                caTimeUnit = "sec"
                                expanded = false
                            },
                            text = { Text("sec") }
                        )
                        DropdownMenuItem(
                            onClick = {
                                caTimeUnit = "min"
                                expanded = false
                            },
                            text = { Text("min") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = {
                    try {
                        // 값 검증
                        val minVoltage = cvMinVoltage.toFloat()
                        val maxVoltage = cvMaxVoltage.toFloat()
                        val rate = scanRate.toInt()
                        val cycle = cvCycle.toInt() // Cycle 검증
                        val timeValue = caTimeValue.toInt()

                        if (minVoltage < -1.0 || maxVoltage > 1.0 || minVoltage >= maxVoltage) {
                            Toast.makeText(context, "Voltage range must be within ±1V.", Toast.LENGTH_SHORT).show()
                        } else if (rate < 5 || rate > 200) {
                            Toast.makeText(context, "Scan rate must be between 5 and 200 mV/s.", Toast.LENGTH_SHORT).show()
                        } else if (cycle < 1 || cycle > 100) {
                            Toast.makeText(context, "Cycle must be between 1 and 100.", Toast.LENGTH_SHORT).show()
                        } else if (timeValue < 1 || timeValue > 60) {
                            Toast.makeText(context, "Time value must be between 1 and 60.", Toast.LENGTH_SHORT).show()
                        } else {
                            // Save settings to SharedPreferences
                            with(sharedPreferences.edit()) {
                                putString("cvMinVoltage", cvMinVoltage)
                                putString("cvMaxVoltage", cvMaxVoltage)
                                putString("scanRate", scanRate)
                                putString("cvCycle", cvCycle) // Cycle 저장
                                putString("caVoltage", caVoltage)
                                putString("caTimeValue", caTimeValue)
                                putString("caTimeUnit", caTimeUnit)
                                apply()
                            }
                            Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, "Invalid input values. Please check your inputs.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }
        }
    }
}

@ExperimentalMaterial3Api
@Composable
fun AppNavigator() {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("bluetooth") { BluetoothDiscoveryScreen(navController) }
        composable("measurement") { MeasurementScreen(navController) }
        composable("measurement_setting") { MeasurementSettingScreen(navController) }
        composable("measurement_result") {
            MeasurementResultScreen(context = context, navController = navController)
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDiscoveryScreen(navController: NavController) {
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    var devices by remember { mutableStateOf<Set<BluetoothDevice>>(emptySet()) }
    var isDiscovering by remember { mutableStateOf(false) }
    var connectionStatus by remember {
        mutableStateOf(
            if (BluetoothForegroundService.isConnected) {
                "Connected Device: ${BluetoothForegroundService.bluetoothSocket?.remoteDevice?.name ?: "Unknown"}"
            } else {
                "No device connected"
            }
        )
    }
    val context = LocalContext.current

    // Bluetooth discovery receiver 설정
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            devices = devices + it
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isDiscovering = false
                    }
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, intentFilter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Connection") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 현재 연결 상태 표시
            Text(
                text = connectionStatus,
                color = when {
                    connectionStatus.startsWith("Connected") -> Color.Green
                    connectionStatus.startsWith("Connecting") -> Color.Blue
                    else -> Color.Red
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // 장치 검색 버튼
            Button(
                onClick = {
                    devices = emptySet()
                    isDiscovering = true
                    connectionStatus = "No device connected" // 상태 초기화

                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        bluetoothAdapter?.startDiscovery()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (isDiscovering) {
                                bluetoothAdapter?.cancelDiscovery()
                                isDiscovering = false
                            }
                        }, 12000)
                    } else {
                        Toast.makeText(context, "Permission denied for Bluetooth scan", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isDiscovering,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isDiscovering) "Searching..." else "Start Discovery")
            }

            // 검색된 장치 목록 표시
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices.toList()) { device ->
                    val deviceName = device.name ?: "Unknown Device"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 장치 연결 시작
                                connectionStatus = "Connecting to $deviceName..."
                                connectToDevice(
                                    context = context,
                                    deviceAddress = device.address
                                ) { status ->
                                    connectionStatus = when {
                                        status.startsWith("Connected") -> {
                                            BluetoothForegroundService.updateConnectionState(BluetoothForegroundService.bluetoothSocket)
                                            "Connected Device: $deviceName"
                                        }
                                        status.startsWith("Failed") -> {
                                            "Failed to connect to $deviceName"
                                        }
                                        else -> status
                                    }
                                }
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Device Name: $deviceName", fontSize = 16.sp)
                            Text(
                                text = "Address: ${device.address}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

// 블루투스 장치와 연결 함수
fun connectToDevice(
    context: Context,
    deviceAddress: String,
    onConnectionStateChange: (String) -> Unit
) {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    val device = bluetoothAdapter.getRemoteDevice(deviceAddress)

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        onConnectionStateChange("Permission Denied: BLUETOOTH_CONNECT is required.")
        return
    }

    Thread {
        try {
            onConnectionStateChange("Fetching UUIDs...")
            device.fetchUuidsWithSdp()
            val uuid = device.uuids?.firstOrNull()?.uuid ?: UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            onConnectionStateChange("UUID found: $uuid")

            val socket = device.createRfcommSocketToServiceRecord(uuid)
            onConnectionStateChange("Connecting to ${device.name}...")

            socket.connect()
            onConnectionStateChange("Connected to ${device.name}")

            // 소켓을 닫지 않고 서비스에 전달하거나 유지
            BluetoothForegroundService.updateConnectionState(socket)

        } catch (e: IOException) {
            onConnectionStateChange("Failed to connect: ${e.message}")
        } catch (e: SecurityException) {
            onConnectionStateChange("Permission error: ${e.message}")
        }
    }.start()
}
