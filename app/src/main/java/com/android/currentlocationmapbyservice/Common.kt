package com.android.currentlocationmapbyservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.currentlocationmapbyservice.Model.DriverGeoModel
import com.android.currentlocationmapbyservice.Model.DriverInfoModel
import com.google.android.gms.maps.model.Marker

object Common {
    val driverFound: MutableSet<DriverGeoModel> = HashSet<DriverGeoModel>()
    val TOKEN_REFERENCE: String = "Token"
    var currentUser: DriverInfoModel?=null
    val DRIVER_INFO_REFERENCE: String = "DriverInfo"
    val DRIVER_LOCATION_REFERENCE: String = "DriverLocations"
    val NOTIF_BODY: String = "body"
    val NOTIF_TITLE: String= "title"
    val marketList: MutableMap<String, Marker> = HashMap()

    fun buildName(firstName: String?, lastName: String?): String? {
        return java.lang.StringBuilder(firstName).append(" ").append(lastName).toString()
    }

    fun showNotification(
            context: Context,
            id: Int,
            title: String?,
            body: String?,
            intent: Intent?
    ) {
        var pendingIntent: PendingIntent? = null
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (intent != null) {
            pendingIntent = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_ONE_SHOT)
            val NOTIFICATION_CHANNEL_ID = "uber"
            val notoficationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notoficationChannel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Uber Saya",
                        NotificationManager.IMPORTANCE_HIGH
                )
                notoficationChannel.description = "description"
                notoficationChannel.enableLights(true)
                notoficationChannel.lightColor = Color.RED
                notoficationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                notoficationChannel.enableVibration(true)
                notoficationManager.createNotificationChannel(notoficationChannel)
            }

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .setSmallIcon(R.drawable.ic_baseline_directions_car_24)
                    .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_baseline_directions_car_24))

            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
                val notafication = builder.build()
                notoficationManager.notify(id, notafication)
            }
        }else{
            Log.e("intent","ada")
        }
    }
}