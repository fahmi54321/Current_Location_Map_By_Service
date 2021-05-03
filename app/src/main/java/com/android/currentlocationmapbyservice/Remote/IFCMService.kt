package com.android.currentlocationmapbyservice.Remote

import com.android.currentlocationmapbyservice.Model.FCMResponse
import com.android.currentlocationmapbyservice.Model.FCMSendData
import io.reactivex.Observable
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// todo 123 send request to driver(next UserUtils)
interface IFCMService {

    @Headers(
            "Content-Type:application/json",
            "Authorization:key=AAAADF7lJHg:APA91bHTzdH2q0c2S3csdtwBwZJVmwuxHkQ7L2ySCAn-JFmkW1aDlU8t34ez0-SL_bcDNSAG-5UcigfnPcBYqY7cC0N7WyYzen7qIJqXnVZwXzg72sbioxr2nAhlTQRbpy79ZRCJOkp1"
    )

    @POST("fcm/send")
    fun sendNotification(@Body body: FCMSendData?): Observable<FCMResponse?>?

}