package com.example.jeia.data

import android.annotation.SuppressLint
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
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RingState(
  val status: String = "Bague non connectée",
  val busy: Boolean = false,
  val alert: String = "Aucune alerte reçue",
)

internal fun alertCounter(value: ByteArray): Int? {
  if (value.size != 3 || value[0] != 1.toByte()) return null
  return (value[1].toInt() and 0xff) or ((value[2].toInt() and 0xff) shl 8)
}

internal fun acknowledgement(counter: Int): ByteArray =
  byteArrayOf(counter.toByte(), (counter shr 8).toByte(), 0)

@SuppressLint("MissingPermission")
class RingConnection(private val context: Context) {
  private val serviceUuid = UUID.fromString("4a450001-7c1d-4a8e-9b3f-2f6d8c1a0b11")
  private val eventUuid = UUID.fromString("4a450002-7c1d-4a8e-9b3f-2f6d8c1a0b11")
  private val ackUuid = UUID.fromString("4a450003-7c1d-4a8e-9b3f-2f6d8c1a0b11")
  private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
  private val handler = Handler(Looper.getMainLooper())
  private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
  private val mutableState = MutableStateFlow(RingState())
  val state = mutableState.asStateFlow()
  private var gatt: BluetoothGatt? = null
  private var scanning = false
  private var lastCounter: Int? = null
  private var writingAck = false

  private val timeout = Runnable { fail("Bague introuvable ou connexion expirée. Réessaie.") }

  private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      handler.post {
        if (!scanning) return@post
        stopScan()
        mutableState.update { it.copy(status = "Connexion en cours…") }
        try {
          gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
          if (gatt == null) fail("Connexion impossible. Réessaie.")
        } catch (_: SecurityException) {
          fail("Autorisation Bluetooth nécessaire.")
        }
      }
    }

    override fun onScanFailed(errorCode: Int) {
      handler.post { if (scanning) fail("Recherche Bluetooth impossible. Réessaie.") }
    }
  }

  private val gattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
      handler.post {
        if (connection !== gatt) return@post
        try {
          if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
            fail("Bague déconnectée. Réessaie.")
          } else if (newState == BluetoothProfile.STATE_CONNECTED && !connection.discoverServices()) {
            fail("Recherche du service Bluetooth impossible.")
          }
        } catch (_: SecurityException) {
          fail("Autorisation Bluetooth nécessaire.")
        }
      }
    }

    override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
      handler.post {
        if (connection !== gatt) return@post
        try {
          val service = connection.getService(serviceUuid)
          val event = service?.getCharacteristic(eventUuid)
          val descriptor = event?.getDescriptor(cccdUuid)
          if (status != BluetoothGatt.GATT_SUCCESS || event == null || descriptor == null ||
            service.getCharacteristic(ackUuid) == null ||
            !connection.setCharacteristicNotification(event, true)
          ) {
            fail("Service d’alerte indisponible sur la bague.")
            return@post
          }
          @Suppress("DEPRECATION")
          descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
          @Suppress("DEPRECATION")
          if (!connection.writeDescriptor(descriptor)) fail("Activation des alertes impossible.")
        } catch (_: SecurityException) {
          fail("Autorisation Bluetooth nécessaire.")
        }
      }
    }

    override fun onDescriptorWrite(connection: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
      handler.post {
        if (connection !== gatt || descriptor.uuid != cccdUuid) return@post
        if (status != BluetoothGatt.GATT_SUCCESS) {
          fail("Activation des alertes impossible.")
        } else {
          handler.removeCallbacks(timeout)
          mutableState.update { it.copy(status = "Bague connectée", busy = true) }
        }
      }
    }

    @Deprecated("Used by Android 12 and earlier")
    override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
      @Suppress("DEPRECATION")
      val value = characteristic.value?.copyOf() ?: return
      receive(connection, characteristic.uuid, value)
    }

    override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
      receive(connection, characteristic.uuid, value.copyOf())
    }

    override fun onCharacteristicWrite(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
      handler.post {
        if (connection !== gatt || characteristic.uuid != ackUuid) return@post
        writingAck = false
        if (status != BluetoothGatt.GATT_SUCCESS) {
          mutableState.update { it.copy(status = "Alerte reçue, confirmation Bluetooth échouée") }
        } else {
          mutableState.update { it.copy(status = "Bague connectée") }
        }
      }
    }
  }

  fun connect() {
    if (mutableState.value.busy) return
    try {
      if (adapter?.isEnabled != true) {
        mutableState.update { it.copy(status = "Active le Bluetooth du téléphone.") }
        return
      }
      val scanner = adapter.bluetoothLeScanner
      if (scanner == null) {
        mutableState.update { it.copy(status = "Bluetooth LE indisponible sur ce téléphone.") }
        return
      }
      lastCounter = null
      scanning = true
      mutableState.update { it.copy(status = "Recherche de la bague…", busy = true) }
      val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()
      val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
      scanner.startScan(listOf(filter), settings, scanCallback)
      handler.postDelayed(timeout, 20_000)
    } catch (_: SecurityException) {
      fail("Autorisation Bluetooth nécessaire.")
    }
  }

  fun close() {
    handler.removeCallbacks(timeout)
    stopScan()
    try {
      gatt?.close()
    } catch (_: SecurityException) {
      // Android can revoke permissions while the connection is active.
    }
    gatt = null
    writingAck = false
  }

  fun dismissAlert() {
    mutableState.update { it.copy(alert = "Aucune alerte reçue") }
  }

  fun permissionDenied() {
    mutableState.update { it.copy(status = "Autorisation Bluetooth nécessaire pour connecter la bague.") }
  }

  private fun stopScan() {
    if (!scanning) return
    scanning = false
    try {
      adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    } catch (_: SecurityException) {
      // Android can revoke permissions while scanning.
    }
  }

  private fun fail(message: String) {
    close()
    mutableState.update { it.copy(status = message, busy = false) }
  }

  private fun receive(connection: BluetoothGatt, uuid: UUID, value: ByteArray) {
    handler.post {
      if (connection !== gatt || uuid != eventUuid) return@post
      val counter = alertCounter(value) ?: return@post
      if (counter != lastCounter) {
        lastCounter = counter
        mutableState.update { it.copy(alert = "Alerte n°$counter reçue depuis la bague !") }
      }
      if (writingAck) return@post
      val ack = connection.getService(serviceUuid)?.getCharacteristic(ackUuid) ?: return@post
      try {
        @Suppress("DEPRECATION")
        ack.value = acknowledgement(counter)
        ack.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        @Suppress("DEPRECATION")
        val started = connection.writeCharacteristic(ack)
        writingAck = started
        if (!started) mutableState.update { it.copy(status = "Alerte reçue, confirmation Bluetooth échouée") }
      } catch (_: SecurityException) {
        fail("Autorisation Bluetooth nécessaire.")
      }
    }
  }
}
