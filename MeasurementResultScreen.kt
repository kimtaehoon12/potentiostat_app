package com.example.myapplication

import java.io.IOException
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object BluetoothCommunication {

    fun sendData(socket: BluetoothSocket, data: String) {
        try {
            socket.outputStream.write(data.toByteArray())
        } catch (e: IOException) {
            Log.e("BluetoothCommunication", "Error writing data: ${e.message}")
        }
    }

    fun receiveData(socket: BluetoothSocket, onDataReceived: (String) -> Unit) {
        val queue = LinkedBlockingQueue<String>() // 무한 큐
        val tempBuffer = ByteArray(2048)
        var leftover = ""

        val receiveThread = Thread {
            try {
                while (socket.isConnected) {
                    val bytesRead = socket.inputStream.read(tempBuffer)
                    if (bytesRead > 0) {
                        val receivedData = leftover + String(tempBuffer, 0, bytesRead)
                        val lines = receivedData.split("\n")
                        leftover = if (receivedData.endsWith("\n")) "" else lines.last()

                        // 큐에 데이터 추가
                        synchronized(queue) {
                            lines.dropLast(1).forEach { queue.offer(it.trim()) }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("BluetoothCommunication", "Error reading data: ${e.message}")
            }
        }

        val processThread = Thread {
            try {
                while (true) {
                    val data = quaeue.poll(50, TimeUnit.MILLISECONDS) // 대기 시간을 줄임
                    data?.let { onDataReceived(it) }
                }
            } catch (e: Exception) {
                Log.e("BluetoothCommunication", "Error processing data: ${e.message}")
            }
        }

        receiveThread.start()
        processThread.start()
    }




}
