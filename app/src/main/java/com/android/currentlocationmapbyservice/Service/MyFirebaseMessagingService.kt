package com.android.currentlocationmapbyservice.Service

import android.content.Intent
import android.util.Log
import com.android.currentlocationmapbyservice.Common
import com.android.currentlocationmapbyservice.MapsActivity
import com.android.currentlocationmapbyservice.Utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService:FirebaseMessagingService() {

    //todo 2 notifications (next splashscreen)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (FirebaseAuth.getInstance().currentUser != null) {
            UserUtils.updateToken(this, token)
        }
    }

    //todo 4 notifications (next manifest)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.e("message", "received")
        val data = remoteMessage.data
        val intent = Intent(this, MapsActivity::class.java)
        if (data != null) {
            Common.showNotification(
                    this, Random.nextInt(),
                    data[Common.NOTIF_TITLE],
                    data[Common.NOTIF_BODY],
                    intent
            )
        }
    }


}