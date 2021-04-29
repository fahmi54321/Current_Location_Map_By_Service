package com.android.ridemapservice.Model

import com.firebase.geofire.GeoLocation

//todo 71 load driver
class DriverGeoModel {
    var key:String?=null
    var geoLocation:GeoLocation?=null
    var driverInfoModel:DriverInfoModel?=null
    var isDecline:Boolean = false

    //todo 73
    constructor(key:String?,geoLocation: GeoLocation?){
        this.key = key
        this.geoLocation = geoLocation
    }
}
