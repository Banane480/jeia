package com.example.jeia.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.jeia.domain.AlertAck
import com.example.jeia.domain.AlertEvent
import com.example.jeia.domain.RingConnectionState
import com.example.jeia.domain.RingRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Implémentation réelle de [RingRepository] communiquant via Bluetooth Low Energy (BLE)
 * avec le firmware ESP32 (ou DA14531) selon le contrat [JeiaBleContract].
 */
class BleRingRepository(
  private val context: Context,
) : RingRepository {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val bluetoothManager: BluetoothManager? =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

  private val _connectionState = MutableStateFlow(RingConnectionState.DISCONNECTED)
  override val connectionState: StateFlow<RingConnectionState> = _connectionState.asStateFlow()

  private val _alertEvents = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 16)
  override val alertEvents: Flow<AlertEvent> = _alertEvents

  private var activeGatt: BluetoothGatt? = null
  private var eventCharacteristic: BluetoothGattCharacteristic? = null
  private var ackCharacteristic: BluetoothGattCharacteristic? = null
  private var isScanning = false
  private var autoReconnectEnabled = false

  private val gattCallback = object : BluetoothGattCallback() {
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
      Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
      if (newState == BluetoothProfile.STATE_CONNECTED) {
        Log.i(TAG, "Connecté au périphérique BLE, découverte des services...")
        gatt.discoverServices()
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        Log.w(TAG, "Périphérique BLE déconnecté")
        cleanupGatt()
        _connectionState.value = RingConnectionState.DISCONNECTED
        if (autoReconnectEnabled) {
          scope.launch { connect() }
        }
      }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
      if (status != BluetoothGatt.GATT_SUCCESS) {
        Log.e(TAG, "Échec de la découverte des services GATT: $status")
        return
      }

      val service = gatt.getService(JeiaBleContract.SERVICE_UUID)
      if (service == null) {
        Log.e(TAG, "Service JEIA ${JeiaBleContract.SERVICE_UUID} introuvable sur l'appareil.")
        return
      }

      eventCharacteristic = service.getCharacteristic(JeiaBleContract.ALERT_EVENT_UUID)
      ackCharacteristic = service.getCharacteristic(JeiaBleContract.ALERT_ACK_UUID)

      val eventChar = eventCharacteristic
      if (eventChar != null) {
        enableNotifications(gatt, eventChar)
      } else {
        Log.e(TAG, "Caractéristique Alert Event introuvable !")
      }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
      if (descriptor.uuid == CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
        Log.i(TAG, "Abonnement aux notifications actif. Bague JEIA prête !")
        _connectionState.value = RingConnectionState.CONNECTED
      }
    }

    // Android 13+ (API 33+)
    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray,
    ) {
      handleNotificationData(characteristic.uuid, value)
    }

    // Android < 13
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
    ) {
      @Suppress("DEPRECATION")
      val value = characteristic.value ?: return
      handleNotificationData(characteristic.uuid, value)
    }

    override fun onCharacteristicWrite(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      status: Int,
    ) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        Log.i(TAG, "ACK écrit avec succès vers la bague.")
      } else {
        Log.e(TAG, "Échec de l'écriture de l'ACK: $status")
      }
    }
  }

  private val scanCallback = object : ScanCallback() {
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      val device = result.device
      val deviceName = result.scanRecord?.deviceName ?: device.name ?: ""
      Log.i(TAG, "Appareil BLE trouvé: $deviceName [${device.address}]")

      stopScan()
      connectToDevice(device)
    }

    override fun onScanFailed(errorCode: Int) {
      Log.e(TAG, "Échec du scan BLE: $errorCode")
      isScanning = false
      _connectionState.value = RingConnectionState.DISCONNECTED
    }
  }

  private fun handleNotificationData(characteristicUuid: UUID, data: ByteArray) {
    if (characteristicUuid == JeiaBleContract.ALERT_EVENT_UUID) {
      val event = JeiaBleContract.decodeAlertEvent(data)
      if (event != null) {
        Log.i(TAG, "Événement bague reçu : type=${event.type}, counter=${event.counter}")
        scope.launch {
          _alertEvents.emit(event)
        }
      } else {
        Log.w(TAG, "Données d'alerte corrompues : ${data.joinToString { "%02x".format(it) }}")
      }
    }
  }

  @SuppressLint("MissingPermission")
  override suspend fun connect() {
    if (!hasRequiredPermissions()) {
      Log.w(TAG, "Permissions Bluetooth non accordées.")
      return
    }

    val adapter = bluetoothAdapter
    if (adapter == null || !adapter.isEnabled) {
      Log.w(TAG, "Bluetooth désactivé ou indisponible.")
      return
    }

    autoReconnectEnabled = true
    _connectionState.value = RingConnectionState.SCANNING

    val scanner = adapter.bluetoothLeScanner
    if (scanner == null) {
      Log.e(TAG, "BluetoothLeScanner indisponible.")
      _connectionState.value = RingConnectionState.DISCONNECTED
      return
    }

    val filters = listOf(
      ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(JeiaBleContract.SERVICE_UUID))
        .build(),
      ScanFilter.Builder()
        .setDeviceName(JeiaBleContract.DEVICE_NAME)
        .build(),
    )

    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()

    isScanning = true
    Log.i(TAG, "Lancement du scan BLE pour la bague JEIA...")
    scanner.startScan(filters, settings, scanCallback)
  }

  @SuppressLint("MissingPermission")
  private fun stopScan() {
    if (isScanning) {
      try {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
      } catch (e: Exception) {
        Log.w(TAG, "Erreur lors de l'arrêt du scan", e)
      }
      isScanning = false
    }
  }

  @SuppressLint("MissingPermission")
  private fun connectToDevice(device: BluetoothDevice) {
    Log.i(TAG, "Connexion GATT à ${device.address}...")
    activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    } else {
      device.connectGatt(context, false, gattCallback)
    }
  }

  @SuppressLint("MissingPermission")
  private fun enableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
    gatt.setCharacteristicNotification(char, true)
    val descriptor = char.getDescriptor(CCCD_UUID)
    if (descriptor != null) {
      val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(descriptor, value)
      } else {
        @Suppress("DEPRECATION")
        descriptor.value = value
        @Suppress("DEPRECATION")
        gatt.writeDescriptor(descriptor)
      }
    } else {
      Log.w(TAG, "Descripteur CCCD introuvable sur la caractéristique d'événement.")
      _connectionState.value = RingConnectionState.CONNECTED
    }
  }

  @SuppressLint("MissingPermission")
  override suspend fun sendAck(ack: AlertAck) {
    val gatt = activeGatt ?: return
    val char = ackCharacteristic ?: return

    val bytes = JeiaBleContract.encodeAlertAck(ack)
    Log.i(TAG, "Envoi de l'ACK #${ack.counter} (${ack.status}) vers la bague...")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    } else {
      @Suppress("DEPRECATION")
      char.value = bytes
      @Suppress("DEPRECATION")
      gatt.writeCharacteristic(char)
    }
  }

  @SuppressLint("MissingPermission")
  override suspend fun disconnect() {
    autoReconnectEnabled = false
    stopScan()
    cleanupGatt()
    _connectionState.value = RingConnectionState.DISCONNECTED
  }

  @SuppressLint("MissingPermission")
  private fun cleanupGatt() {
    try {
      activeGatt?.disconnect()
      activeGatt?.close()
    } catch (_: Exception) {}
    activeGatt = null
    eventCharacteristic = null
    ackCharacteristic = null
  }

  private fun hasRequiredPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val scan = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.BLUETOOTH_SCAN,
      ) == PackageManager.PERMISSION_GRANTED
      val connect = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.BLUETOOTH_CONNECT,
      ) == PackageManager.PERMISSION_GRANTED
      scan && connect
    } else {
      true
    }
  }

  companion object {
    private const val TAG = "BleRingRepository"
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
  }
}
