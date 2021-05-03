package com.android.currentlocationmapbyservice.Utils

import android.content.Context
import android.widget.RelativeLayout
import android.widget.Toast
import com.android.currentlocationmapbyservice.Common
import com.android.currentlocationmapbyservice.EventBus.SelectPlaceEvent
import com.android.currentlocationmapbyservice.Model.DriverGeoModel
import com.android.currentlocationmapbyservice.Model.FCMSendData
import com.android.currentlocationmapbyservice.Model.TokenModel
import com.android.currentlocationmapbyservice.R
import com.android.currentlocationmapbyservice.Remote.IFCMService
import com.android.currentlocationmapbyservice.Remote.RetrofitFCMClient
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.lang.StringBuilder

object UserUtils {
    fun updateToken(context: Context, token: String) {
        val tokenModel = TokenModel()
        tokenModel.token = token

        FirebaseDatabase.getInstance().getReference(Common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser?.uid?:"")
            .setValue(tokenModel)
            .addOnFailureListener { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
    }

    fun sendRequestToDriver(context: Context, mainLayout: RelativeLayout?, foundDriver: DriverGeoModel?, target: LatLng?) {
        val compositeDisposable = CompositeDisposable()
        val ifcmService = RetrofitFCMClient.intance?.create(IFCMService::class.java)
        //get token
        FirebaseDatabase.getInstance()
                .getReference(Common.TOKEN_REFERENCE)
                .child(foundDriver?.key ?: "")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {

                            val tokenModel = snapshot.getValue(TokenModel::class.java)
                            val notificationData: MutableMap<String, String> = HashMap()
                            notificationData.put(Common.NOTIF_TITLE, Common.REQUEST_DRIVER_TITLE)
                            notificationData.put(Common.NOTIF_BODY, "This Message represent for request motor action")
                            notificationData.put(Common.PICKUP_LOCATION, StringBuilder()
                                    .append(target?.latitude)
                                    .append(",")
                                    .append(target?.longitude)
                                    .toString())

                            val fcmSendData = FCMSendData(tokenModel?.token ?: "", notificationData)
                            ifcmService?.sendNotification(fcmSendData)
                                    ?.subscribeOn(Schedulers.newThread())
                                    ?.observeOn(AndroidSchedulers.mainThread())
                                    ?.subscribe({

                                        if (it?.success == 0) {
                                            compositeDisposable.clear()
                                            mainLayout?.let { it1 -> Snackbar.make(it1, context.getString(R.string.send_request_driver_failed), Snackbar.LENGTH_LONG).show() }
                                        }

                                    }, {
                                        compositeDisposable.clear()
                                        mainLayout?.let { it1 -> Snackbar.make(it1, it.message.toString(), Snackbar.LENGTH_LONG).show() }
                                    })?.let { compositeDisposable.add(it) }

                        } else {
                            mainLayout?.let { Snackbar.make(it, context.getString(R.string.token_not_found), Snackbar.LENGTH_LONG).show() }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        mainLayout?.let { Snackbar.make(it, error.message, Snackbar.LENGTH_LONG).show() }
                    }

                })
    }

}