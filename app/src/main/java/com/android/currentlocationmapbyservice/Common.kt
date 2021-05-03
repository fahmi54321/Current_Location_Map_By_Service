package com.android.currentlocationmapbyservice

import android.animation.ValueAnimator
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
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.android.currentlocationmapbyservice.Model.AnimationModel
import com.android.currentlocationmapbyservice.Model.DriverGeoModel
import com.android.currentlocationmapbyservice.Model.DriverInfoModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object Common {
    val PICKUP_LOCATION: String = "PickupLocation"
    val REQUEST_DRIVER_TITLE: String ="RequestDriver"
    val driversSubscribe: MutableMap<String,AnimationModel> = HashMap<String,AnimationModel>()
    val driverFound: MutableMap<String,DriverGeoModel> = HashMap<String,DriverGeoModel>() //todo 1 Find Nearby Driver(next MapsActivity)
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

    //todo 1 animation car (next packet remote, next maps activity)
    //GET BEARING
    fun getBearing(begin: LatLng, end: LatLng): Float {
        //You can copy this function by link at description
        val lat = Math.abs(begin.latitude - end.latitude)
        val lng = Math.abs(begin.longitude - end.longitude)
        if (begin.latitude < end.latitude && begin.longitude < end.longitude)
            return Math.toDegrees(Math.atan(lng / lat)).toFloat()
        else if (begin.latitude >= end.latitude && begin.longitude < end.longitude)
            return (90 - Math.toDegrees(Math.atan(lng / lat)) + 90).toFloat()
        else if (begin.latitude >= end.latitude && begin.longitude >= end.longitude)
            return (Math.toDegrees(Math.atan(lng / lat)) + 180).toFloat()
        else if (begin.latitude < end.latitude && begin.longitude >= end.longitude)
            return (90 - Math.toDegrees(Math.atan(lng / lat)) + 270).toFloat()
        return (-1).toFloat()
    }

    fun setWelcomeMessage(txtWelcome: TextView) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour>=1 && hour<=12){
            txtWelcome.setText(java.lang.StringBuilder("Good Morning"))
        }else if(hour>12 && hour<=17){
            txtWelcome.setText(java.lang.StringBuilder("Good Afternoon"))
        }else{
            txtWelcome.setText(java.lang.StringBuilder("Good Evening"))
        }
    }

    //DECODE POLY
    fun decodePoly(encoded: String): ArrayList<LatLng?> {
        val poly = ArrayList<LatLng?>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val p = LatLng(lat.toDouble() / 1E5,
                lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    fun formatDuration(duration: String): CharSequence? {
        if (duration.contains("mins")){
            return duration.substring(0,duration.length-1)
        }else{
            return duration
        }
    }

    fun formatAddress(startAddress: String): CharSequence? {
        val firstIndextComma = startAddress.indexOf(",")
        return startAddress.substring(0,firstIndextComma)
    }

    fun valueAnimate(duration: Int, listener: ValueAnimator.AnimatorUpdateListener?): ValueAnimator {
        val va = ValueAnimator.ofFloat(0f,100f)
        va.duration = duration.toLong()
        va.addUpdateListener (listener)
        va.repeatCount = ValueAnimator.INFINITE
        va.repeatMode = ValueAnimator.RESTART
        va.start()

        return va
    }
}