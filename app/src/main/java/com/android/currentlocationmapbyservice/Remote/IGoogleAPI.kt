package com.android.currentlocationmapbyservice.Remote

import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

//todo 86 (next object RetrofitClient)
interface IGoogleAPI {

    @GET("maps/api/directions/json")
    fun getDirections(
        @Query("mode") mode:String?,
        @Query("transit_routing_preference") transit_routing:String?,
        @Query("origin") from:String?,
        @Query("destination") to:String?,
        @Query("key") key:String?
    ):Observable<String>?
}