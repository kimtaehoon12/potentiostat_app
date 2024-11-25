package com.example.myapplication

import android.util.Log
import java.io.IOException
import android.bluetooth.BluetoothSocket

object BluetoothCommunication {
    fun sendData(socket: BluetoothSocket, data: String) {
        try {
            socket.outputStream.write(data.toByteArray())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun receiveData(socket: BluetoothSocket, onDataReceived: (String) -> Unit) {
        Thread {
            try {
                val buffer = ByteArray(1024)
                while (socket.isConnected) { // 소켓이 연결된 경우에만 읽기
                    val bytes = socket.inputStream.read(buffer)
                    val receivedData = String(buffer, 0, bytes)
                    onDataReceived(receivedData)
                    if (receivedData.trim() == "done") break // 수신 완료 시 종료
                }
            } catch (e: IOException) {
                Log.e("BluetoothCommunication", "Error reading data: ${e.message}")
            }
        }.start()
    }
}
