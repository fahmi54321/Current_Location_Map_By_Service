package com.android.ridemapservice.Utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.text.TextUtils
import java.io.IOException
import java.lang.StringBuilder
import java.util.*


//todo 1 fix city name empty(next home fragment)
object LocationUtils {

    fun getAddressFromLocation(context: Context?,location: Location):String{
        val result = StringBuilder()
        val geoCoder = Geocoder(context, Locale.getDefault())
        val addressList:List<Address>?

        return try {
            addressList = geoCoder.getFromLocation(location.latitude,location.longitude,1)
            if (addressList != null && addressList.size>0){
                if (addressList[0].locality!=null && !TextUtils.isEmpty(addressList[0].locality)){

                    //if address have city field
                    result.append(addressList[0].locality)
                }else if (addressList[0].subAdminArea!=null && !TextUtils.isEmpty(addressList[0].subAdminArea)){
                    //if do not have city field, we looking for subadmin are
                    result.append(addressList[0].subAdminArea)
                }else if(addressList[0].adminArea!=null && !TextUtils.isEmpty(addressList[0].adminArea)){
                    //if do not have subadmin, we looking for admin are
                    result.append(addressList[0].adminArea)
                }else{
                    //if do not have admin, we looking for country
                    result.append(addressList[0].countryName)
                }

                //final result , apply country code
                result.append(addressList[0].countryCode)
            }

            result.toString()
        }catch (e:IOException){
            result.toString()
        }
    }
}