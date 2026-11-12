package com.example.jeia.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.jeia.JeiaApplication
import com.example.jeia.MainActivity
import com.example.jeia.R
import com.example.jeia.domain.AckStatus
import com.example.jeia.domain.AlertAck
import com.example.jeia.domain.AlertType
import com.example.jeia.domain.RingConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service d'arrière-plan (Foreground Service) garantissant le maintien de la connexion BLE
 * et l'écoute des alertes même lorsque l'écran est verrouillé ou que l'application est en arrière-plan.
 */
class JeiaRingService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private lateinit var notificationManager: NotificationManager

  override fun onCreate() {
    super.onCreate()
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    createNotificationChannels()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
      }
      else -> {
        startForeground(NOTIFICATION_ID, buildServiceNotification(getString(R.string.ring_state_disconnected)))
        observeRingEvents()
      }
    }
    return START_STICKY
  }

  private fun observeRingEvents() {
    val app = application as? JeiaApplication ?: return
    val container = app.container

    // Observe l'état de connexion pour mettre à jour la notification permanente
    serviceScope.launch {
      container.ringRepository.connectionState.collect { state ->
        val text = when (state) {
          RingConnectionState.DISCONNECTED -> getString(R.string.ring_state_disconnected)
          RingConnectionState.SCANNING -> getString(R.string.ring_state_scanning)
          RingConnectionState.CONNECTED -> getString(R.string.ring_state_connected)
        }
        notificationManager.notify(NOTIFICATION_ID, buildServiceNotification(text))
      }
    }

    // Écoute les alertes en tâche de fond
    serviceScope.launch {
      container.ringRepository.alertEvents.collect { event ->
        if (event.type == AlertType.ALERT) {
          showEmergencyNotification(event.counter)
          // Déclenche la localisation GPS et l'envoi d'urgence
          serviceScope.launch {
            val location = container.locationProvider.getCurrentLocation()
            val contacts = container.emergencyContactsRepository.contacts.value
            container.alertDispatcher.dispatchAlert(event.counter, location, contacts)
            // Renvoie l'ACK pour que l'ESP32 confirme par 2 clignotements
            container.ringRepository.sendAck(AlertAck(event.counter, AckStatus.SENT))
          }
        }
      }
    }
  }

  private fun buildServiceNotification(statusText: String): Notification {
    val openAppIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(getString(R.string.service_notification_text, statusText))
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun showEmergencyNotification(counter: Int) {
    val openAlertIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_TRIGGER_ALERT, counter)
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      counter,
      openAlertIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
      .setContentTitle("ALERTE JEIA EN COURS")
      .setContentText("Alerte SOS #$counter déclenchée depuis la bague !")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentIntent(pendingIntent)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setAutoCancel(true)
      .build()

    notificationManager.notify(ALERT_NOTIFICATION_ID_OFFSET + counter, notification)
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val serviceChannel = NotificationChannel(
        CHANNEL_SERVICE_ID,
        "Surveillance Bague JEIA",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Maintient la liaison Bluetooth avec la bague"
      }

      val alertChannel = NotificationChannel(
        CHANNEL_ALERT_ID,
        "Alertes d'Urgence JEIA",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Notifications d'urgence et SOS"
        enableVibration(true)
      }

      notificationManager.createNotificationChannel(serviceChannel)
      notificationManager.createNotificationChannel(alertChannel)
    }
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val CHANNEL_SERVICE_ID = "jeia_ble_service_channel"
    const val CHANNEL_ALERT_ID = "jeia_alert_emergency_channel"
    const val NOTIFICATION_ID = 1001
    const val ALERT_NOTIFICATION_ID_OFFSET = 2000

    const val ACTION_START = "com.example.jeia.action.START_SERVICE"
    const val ACTION_STOP = "com.example.jeia.action.STOP_SERVICE"
    const val EXTRA_TRIGGER_ALERT = "extra_trigger_alert"

    fun start(context: Context) {
      val intent = Intent(context, JeiaRingService::class.java).apply {
        action = ACTION_START
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, JeiaRingService::class.java).apply {
        action = ACTION_STOP
      }
      context.startService(intent)
    }
  }
}
