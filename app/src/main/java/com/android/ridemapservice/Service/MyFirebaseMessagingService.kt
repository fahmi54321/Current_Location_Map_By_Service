package com.android.ridemapservice.Service

import android.content.Intent
import android.util.Log
import com.android.ridemapservice.MainActivityServiceMap
import com.android.ridemapservice.Utils.Common
import com.android.ridemapservice.Utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random


class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        if (FirebaseAuth.getInstance().currentUser != null) {
            UserUtils.updateToken(this, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.e("message", "received")
        val data = remoteMessage.data
        val intent = Intent(this, MainActivityServiceMap::class.java)
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