package com.android.currentlocationmapbyservice.Callback

import com.android.currentlocationmapbyservice.Model.DriverGeoModel

interface FirebaseDriverInfoListener {
    //todo 70 load driver
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}