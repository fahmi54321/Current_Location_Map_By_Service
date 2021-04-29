package com.android.ridemapservice.Callback

import com.android.ridemapservice.Model.DriverGeoModel


interface FirebaseDriverInfoListener {
    //todo 70 load driver
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}