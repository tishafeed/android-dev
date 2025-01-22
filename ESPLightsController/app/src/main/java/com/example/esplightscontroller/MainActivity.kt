package com.example.esplightscontroller

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothSocketException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.esplightscontroller.ui.theme.ESPLightsControllerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.OutputStream
import java.util.UUID

const val REQUEST_ENABLE_BT = 10001

class MainActivity : ComponentActivity() {
    private var isConnected = false
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var takePermissionLauncher: ActivityResultLauncher<String>
    private lateinit var bluetoothSwitchLauncher: ActivityResultLauncher<Intent>
    private lateinit var bluetoothSocket: BluetoothSocket
    private lateinit var connectedDevice: BluetoothDevice
    private lateinit var outputStream: OutputStream
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        takePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            if (it)
            {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothSwitchLauncher.launch(intent)
            } else
            {
                Toast.makeText(applicationContext,"Bluetooth Permission is not Granted", Toast.LENGTH_LONG).show()
            }
        }
        bluetoothSwitchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(applicationContext, "Bluetooth ON", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Bluetooth OFF", Toast.LENGTH_SHORT).show()
            }
        }
        enableEdgeToEdge()
        setContent {
            ESPLightsControllerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { MainTopBar() }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        BodyElement()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainTopBar() {
        var mainTopBarContext = LocalContext.current
        var enabledOrDisabledText by remember { mutableStateOf(if (!bluetoothAdapter.isEnabled) "Disabled" else "Enabled")}
        TopAppBar(
            title = {
                Text(
                    text = "MyESPLights",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            actions = {
                Row(
                    modifier = Modifier.padding(horizontal = 25.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                )
                {
                    Button(onClick = {
                        if (bluetoothAdapter.isEnabled && !isConnected) { // ask permission and connect
                            if (ActivityCompat.checkSelfPermission(
                                    applicationContext,
                                    android.Manifest.permission.BLUETOOTH_CONNECT)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                // connect to paired "ESP..." device
                                CoroutineScope(Dispatchers.IO).launch {
                                    val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
                                    val item = pairedDevices.find {
                                        it.name == "ESP32-Lights-Controller"
                                    }
                                    if (item != null) {
                                        connectedDevice = item
                                        bluetoothSocket = connectedDevice.createRfcommSocketToServiceRecord(
                                            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                                        try {
                                            bluetoothAdapter.cancelDiscovery()
                                            bluetoothSocket.connect()
                                            enabledOrDisabledText = "Connected"
                                            isConnected = true
                                            outputStream = bluetoothSocket.outputStream
                                        } catch (e: Exception) {
                                            Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(applicationContext, "Not Paired, Not Connected", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else if (!bluetoothAdapter.isEnabled){
                            // if disabled turn on bluetooth after permission
                            takePermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                            enabledOrDisabledText = "Enabled"
                        } else if (bluetoothAdapter.isEnabled && isConnected) {
                            CoroutineScope(Dispatchers.IO).launch {
                                bluetoothSocket.close()
                                isConnected = false
                                enabledOrDisabledText = "Enabled"
                            }
                        }
                    }
                    ){
                        Text(enabledOrDisabledText)
                    }
                } }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    fun BodyElement() {
        var red by remember { mutableFloatStateOf(0f) }
        var green by remember { mutableFloatStateOf(0f) }
        var blue by remember { mutableFloatStateOf(0f) }
        var selectedMode by remember { mutableStateOf("Static") }
        var speed by remember { mutableFloatStateOf(0.5f) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // RGB Sliders (only enabled if static mode is selected)
            if (selectedMode == "Static") {
                Text(text = "Adjust Colors", style = MaterialTheme.typography.labelMedium)
                ColorSlider("Red", Color.Red, red) { value ->
                    red = value
                    if (isConnected) {
                        val command = "S $red $green $blue\n".toByteArray()
                        runBlocking {
                            CoroutineScope(Dispatchers.IO).launch {
                                outputStream.write(command)
                                yield()
                            }
                        }
                    }
                }
                ColorSlider("Green", Color.Green, green) { value ->
                    green = value
                    if (isConnected) {
                        val command = "S $red $green $blue\n".toByteArray()
                        runBlocking(Dispatchers.IO) {
                            CoroutineScope(Dispatchers.IO).launch {
                                outputStream.write(command)
                                yield()
                            }
                        }
                    }
                }
                ColorSlider("Blue", Color.Blue, blue) { value ->
                    blue = value
                    if (isConnected) {
                        val command = "S $red $green $blue\n".toByteArray()
                        runBlocking(Dispatchers.IO) {
                            CoroutineScope(Dispatchers.IO).launch {
                                outputStream.write(command)
                                yield()
                            }
                        }
                    }
                }
            }
            // Mode Selection
            Text(text = "Modes", style = MaterialTheme.typography.labelMedium)
            ModeSelection(selectedMode) { mode ->
                selectedMode = mode
                if (isConnected) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val m = selectedMode.first()
                        if (m == 'S') {
                            val command = "S $red $green $blue\n".toByteArray()
                            runBlocking(Dispatchers.IO) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    outputStream.write(command)
                                    yield()
                                }
                            }
                        } else {
                            val command = "$m $speed\n".toByteArray()
                            runBlocking(Dispatchers.IO) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    outputStream.write(command)
                                    yield()
                                }
                            }
                        }
                    }
                }
            }

            // Speed Control (only enabled if not static mode)
            if (selectedMode != "Static") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Speed")
                    Slider(
                        steps = 50,
                        value = speed,
                        onValueChange = {
                            speed = it
                            if (isConnected) {
                                val m = selectedMode.first()
                                val command = "$m $speed\n".toByteArray()
                                runBlocking(Dispatchers.IO) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        outputStream.write(command)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1f)
                    )
                }
            }
        }
    }

    @Composable
    fun ColorSlider(label: String, color: Color, value: Float, onValueChange: (Float) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = color,
                modifier = Modifier.width(50.dp)
            )
            Slider(
                steps = 0,
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    fun ModeSelection(selectedMode: String, onModeSelected: (String) -> Unit) {
        val modes = listOf("Static", "Blinking", "Fading", "Moving") // dynamic list of program modes
        Column {
            modes.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModeSelected(mode) }
                        .padding(8.dp)
                ) {
                    RadioButton(
                        selected = (mode == selectedMode),
                        onClick = {
                            onModeSelected(mode)
                        }
                    )
                    Text(text = mode, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}


