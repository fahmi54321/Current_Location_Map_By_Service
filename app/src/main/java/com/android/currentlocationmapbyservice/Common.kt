package com.android.currentlocationmapbyservice

import android.content.Context
import android.location.Location
import android.preference.PreferenceManager
import com.android.currentlocationmapbyservice.Model.DriverInfoModel
import java.text.DateFormat
import java.util.*

object Common {
    fun getLocationText(mLocation: Location?): String {
        return if (mLocation == null) {
            "Uknown Location"
        } else {
            "" + mLocation.latitude + "/" + mLocation.longitude
        }

    }

    fun getLocationTitle(context: Context): String {

        return String.format("Location Updated : ${DateFormat.getDateInstance().format(Date())}")

    }

    fun setRequestingLocationUpdates(context: Context, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_REQUEST_LOCATION_UPDATE, value)
                .apply()
    }

    fun requestingLocationUpdates(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_REQUEST_LOCATION_UPDATE, false)
    }

    val KEY_REQUEST_LOCATION_UPDATE = "requesting_location_update"
    var currentUser: DriverInfoModel? = null
    val DRIVER_INFO_REFERENCE: String = "DriverInfo"
    val DRIVER_LOCATION_REFERENCE: String = "DriverLocations"
}