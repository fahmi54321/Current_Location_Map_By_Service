package com.android.currentlocationmapbyservice.Service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.currentlocationmapbyservice.Common
import com.android.currentlocationmapbyservice.Model.MapLocation
import com.android.currentlocationmapbyservice.R
import com.google.android.gms.location.*
import org.greenrobot.eventbus.EventBus
import java.security.Security

class MyMapService : Service() {

    companion object {
        private val CHANNEL_ID = "channel_01"
        private val PACKAGE_NAME = "com.android.currentlocationmapbyservice.Service"
        private val EXTRA_STARTED_FROM_NOTIFICATION = "$PACKAGE_NAME.started_from_notification"
        private val UPDATE_INTERVAL_IN_MIL: Long = 10000
        private val FASTED_UPDATE_INTERVAL_IN_MIL: Long = UPDATE_INTERVAL_IN_MIL / 2
        private val NOTIFICATION_ID = 1234
    }

    private val mBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        internal val service: MyMapService
            get() = this@MyMapService
    }

    private var onChangingConfiguration = false
    private var mNotificationManager:NotificationManager?=null
    private var locationRequest : LocationRequest?=null
    private var fusedLocationProviderClient:FusedLocationProviderClient?=null
    private var locationCallback:LocationCallback?=null
    private var mServiceHandler:Handler?=null
    private var mLocation:Location?=null
    private val notification:Notification
    get() {
        val intent = Intent(this,MyMapService::class.java)
        val text = Common.getLocationText(mLocation)
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION,true)
        val servicePendingIntent = PendingIntent.getService(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT)
        val activityPendingIntent = PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this)
            .addAction(R.drawable.ic_baseline_launch_24,"Launch",activityPendingIntent)
            .addAction(R.drawable.ic_baseline_cancel_24,"Cancel",servicePendingIntent)
            .setContentText(text)
            .setContentTitle(Common.getLocationTitle(this))
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker(text)
            .setSound(null)
            .setWhen(System.currentTimeMillis())
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            builder.setChannelId(CHANNEL_ID)
        }
        return builder.build()
    }

    override fun onCreate() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback(){
            override fun onLocationResult(p0: LocationResult?) {
                super.onLocationResult(p0)
                onNewLocation(p0?.lastLocation)
            }
        }

        createLocationRequest()
        getLastLocation()
        val handlerThread = HandlerThread("Test")
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)
        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val name = packageName
            val mChannel = NotificationChannel(CHANNEL_ID,name,NotificationManager.IMPORTANCE_DEFAULT)
            mChannel.setSound(null,null)
            mChannel.enableVibration(false)
            mNotificationManager?.createNotificationChannel(mChannel)
        }
     }

    private fun getLastLocation() {
        try {
            fusedLocationProviderClient?.lastLocation
                ?.addOnCompleteListener{
                    if (it.isSuccessful && it.result !=null){
                        mLocation = it.result
                    }else{
                        Log.e("Test","failed to get location")
                    }
                }
        }catch (ex:SecurityException){
            Log.e("Test",""+ex.message)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedFromNotifications = intent!!.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,false)
        if (startedFromNotifications){
            removeLocationUpdates()
            stopSelf()
        }
        return Service.START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onChangingConfiguration = true
    }

    fun removeLocationUpdates() {
        try {
            fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
            Common.setRequestingLocationUpdates(this,false)
            stopSelf()
        }catch (ex:SecurityException){
            Common.setRequestingLocationUpdates(this,true)
            Log.e("Test","Lost location permission.$ex")
        }
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest?.interval = UPDATE_INTERVAL_IN_MIL
        locationRequest?.fastestInterval = UPDATE_INTERVAL_IN_MIL
        locationRequest?.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    private fun onNewLocation(lastLocation: Location?) {
        mLocation = lastLocation
        EventBus.getDefault().postSticky(MapLocation(mLocation!!))
        if (serviceIsRunningInForeground(this)){
            Log.e("map back","lat : ${mLocation?.latitude} long : ${mLocation?.longitude}")
            mNotificationManager?.notify(NOTIFICATION_ID,notification)
        }
    }

    private fun serviceIsRunningInForeground(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)){
            if (service.foreground){
                return true
            }
        }
        return false
    }

    override fun onBind(p0: Intent?): IBinder? {
        stopForeground(true)
        onChangingConfiguration = false
        return mBinder
    }

    override fun onRebind(intent: Intent?) {
        stopForeground(true)
        onChangingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (!onChangingConfiguration && Common.requestingLocationUpdates(this)){
            startForeground(NOTIFICATION_ID,notification)
        }
        return true
    }

    override fun onDestroy() {
        mServiceHandler?.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    fun requestLocationUpdates(){
        Common.setRequestingLocationUpdates(this,true)
        startService(Intent(applicationContext,MyMapService::class.java))
        try {
            fusedLocationProviderClient?.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.myLooper())
        }catch (ex:SecurityException){
            Common.setRequestingLocationUpdates(this,false)
            Log.e("Test","Lost Location Permission. $ex")
        }
    }
}
