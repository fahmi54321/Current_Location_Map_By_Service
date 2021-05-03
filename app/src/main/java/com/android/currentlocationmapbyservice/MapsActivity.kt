package com.android.currentlocationmapbyservice

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.android.currentlocationmapbyservice.Callback.FirebaseDriverInfoListener
import com.android.currentlocationmapbyservice.Callback.FirebaseFailedListener
import com.android.currentlocationmapbyservice.EventBus.SelectPlaceEvent
import com.android.currentlocationmapbyservice.Model.AnimationModel
import com.android.currentlocationmapbyservice.Model.DriverGeoModel
import com.android.currentlocationmapbyservice.Model.DriverInfoModel
import com.android.currentlocationmapbyservice.Model.GeoQueryModel
import com.android.currentlocationmapbyservice.Remote.IGoogleAPI
import com.android.currentlocationmapbyservice.Remote.RetrofitClient
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, FirebaseDriverInfoListener {


    //todo 2 Find Nearby Driver(perbaiki Common.driversFound)

//    todo 2 interated google places
    private lateinit var slidingUpPanelLayout:SlidingUpPanelLayout
    private lateinit var txt_welcome:TextView
    private var autocompleteSupportFragment:AutocompleteSupportFragment?=null

    //todo 4 animation car
    val compositeDisposable = CompositeDisposable()
    private var iGoogleAPI: IGoogleAPI? = null

    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()
    }

    private lateinit var mMap: GoogleMap

    //todo 1 load all driver
    var distance = 1.0
    var LIMIT_RANGE = 10.0
    var previousLocation: Location? = null
    var currentLocation: Location? = null
    var fistTime = true
    //listener
    lateinit var iFirebaseDriverInfoListener: FirebaseDriverInfoListener
    lateinit var iFirebaseFailedListener: FirebaseFailedListener
    var cityName = ""

    //todo 1 current location
    private lateinit var mapFragment: SupportMapFragment

    //    todo 2 current location
    private var locationRequest: LocationRequest? = null
    private var locationCallback: LocationCallback? = null
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    companion object {
        val MY_PERMISSIONS_REQUEST_LOCATION = 99
    }

    //todo 10
    private lateinit var onlineRef:DatabaseReference
    private var currentUserRef:DatabaseReference?=null
    private lateinit var driverLocationRef: DatabaseReference
    private var geoFire: GeoFire?=null
    private var onlineValueEventListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists() && currentUserRef != null) {
                currentUserRef?.onDisconnect()?.removeValue()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
        }

    }

    //todo 5
    override fun onDestroy() {

        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)

        //todo 11
        geoFire?.removeLocation(FirebaseAuth.getInstance().currentUser?.uid)
        onlineRef.removeEventListener(onlineValueEventListener)

        super.onDestroy()
    }

    //todo 12
    override fun onResume() {
        super.onResume()
        registerOnlineSystem()
    }

    //todo 13
    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


        //todo 3 current location
        init()

        //todo 2 interated google places
        initViews()

    }

    //todo 3 interated google places
    private fun initViews() {
        slidingUpPanelLayout = findViewById(R.id.activity_main) as SlidingUpPanelLayout
        txt_welcome = findViewById(R.id.txt_welcome) as TextView
        Common.setWelcomeMessage(txt_welcome)
    }

    private fun init() {
        //todo 4 interated google places
        Places.initialize(this,getString(R.string.google_maps_key))
        autocompleteSupportFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteSupportFragment?.setPlaceFields(Arrays.asList(
            Place.Field.ID,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.NAME
        ))

        autocompleteSupportFragment?.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                //todo 2 estimate_routes(next RequestDriverActivity)
                if (ContextCompat.checkSelfPermission(
                                this@MapsActivity,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                ) {
                    //Location Permission already granted
                    Toast.makeText(this@MapsActivity, "Permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    //Request Location Permission
                    checkLocationPermission()
                }
                fusedLocationProviderClient?.lastLocation?.addOnSuccessListener {
                    val origin = LatLng(it.latitude,it.longitude)
                    val destination = LatLng(place.latLng?.latitude?:0.0,place.latLng?.longitude?:0.0)

                    startActivity(Intent(this@MapsActivity,RequestDriverActivity::class.java))
                    EventBus.getDefault().postSticky(SelectPlaceEvent(origin,destination))
                }
            }

            override fun onError(p0: Status) {
                Toast.makeText(this@MapsActivity, ""+p0.statusMessage, Toast.LENGTH_SHORT).show()
            }

        })

        //todo 5 animation car
        iGoogleAPI = RetrofitClient.intance?.create(IGoogleAPI::class.java)

        //todo 9 load all driver
        iFirebaseDriverInfoListener = this

        //todo 14
        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")


        //todo 4 current location
        buildLocationRequest()
        buildLocationCallback()

        //todo 7
        updateLocation()
    }

    //todo 8
    private fun updateLocation() {
        if (fusedLocationProviderClient==null){
            fusedLocationProviderClient =LocationServices.getFusedLocationProviderClient(this)
            if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
            ) {
                //Location Permission already granted
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                //Request Location Permission
                checkLocationPermission()
            }
            fusedLocationProviderClient?.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.myLooper()
            )

            //todo 3 load all driver
            loadAvailableDrivers()
        }
    }

    private fun buildLocationCallback() {
        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResults: LocationResult?) {
                    super.onLocationResult(locationResults)

                    val newPos = LatLng(
                        locationResults?.lastLocation?.latitude ?: 0.0,
                        locationResults?.lastLocation?.longitude ?: 0.0
                    )
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                    //todo 2 load all driver
                    if (fistTime) {
                        previousLocation = locationResults?.lastLocation
                        currentLocation = locationResults?.lastLocation

                        setRestrictPlacesInCountry(locationResults?.lastLocation) //todo 4 interated google places

                        fistTime = false
                    } else {
                        previousLocation = currentLocation
                        currentLocation = locationResults?.lastLocation
                    }


                    if (previousLocation!!.distanceTo(currentLocation)/1000 <= LIMIT_RANGE) {
                        loadAvailableDrivers()
                    }

                }
            }
        }
    }

    private fun setRestrictPlacesInCountry(location: Location?) {

        try {
            val geoCoder = Geocoder(this, Locale.getDefault())
            var addressList =
                geoCoder.getFromLocation(location?.latitude ?: 0.0, location?.longitude
                    ?: 0.0, 1)
            if (addressList.size > 0) {
                autocompleteSupportFragment?.setCountry(addressList[0].countryCode)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    //todo 4 load all driver
    private fun loadAvailableDrivers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {

            } else {
                //Request Location Permission
                checkLocationPermission()
            }
        }
        fusedLocationProviderClient?.lastLocation
            ?.addOnFailureListener {
                Snackbar.make(mapFragment.requireView(), it.message ?: "", Snackbar.LENGTH_SHORT).show()
            }
            ?.addOnSuccessListener { location ->

                val geoCoder = Geocoder(this,Locale.getDefault())
                var addressList : List<Address>
                try {
                    addressList = geoCoder.getFromLocation(location.latitude,location.longitude,1)

                    //todo 5 interated google places
                    if (addressList.size>0){
                        cityName = addressList[0].locality
                    }

                    //query
                    if (!TextUtils.isEmpty(cityName)) {
                        val driver_location_ref = FirebaseDatabase.getInstance()
                            .getReference((Common.DRIVER_LOCATION_REFERENCE))
                            .child(cityName)
                        val gf = GeoFire(driver_location_ref)
                        val geoQuery = gf.queryAtLocation(
                            GeoLocation(location.latitude, location.longitude),
                            distance
                        )
                        geoQuery.removeAllListeners()
                        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                            override fun onKeyEntered(key: String?, location: GeoLocation?) {
//                                Common.driverFound.add(DriverGeoModel(key, location))
                                //todo 3 Find Nearby Driver
                                if (!Common.driverFound.containsKey(key)){
                                    Common.driverFound[key?:""] = DriverGeoModel(key,location)
                                }
                            }

                            override fun onKeyExited(key: String?) {
                            }

                            override fun onKeyMoved(key: String?, location: GeoLocation?) {
                            }

                            override fun onGeoQueryReady() {
                                if (distance <= LIMIT_RANGE) {
                                    distance++
                                    loadAvailableDrivers()
                                } else {
                                    distance = 0.0
                                    addDriverMarker()
                                }
                            }

                            override fun onGeoQueryError(error: DatabaseError?) {
                                Snackbar.make(
                                    mapFragment.requireView(),
                                    error?.message ?: "",
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }

                        })
                        driver_location_ref.addChildEventListener(object : ChildEventListener {
                            override fun onChildAdded(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {
                                val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                                val geoLocation = GeoLocation(
                                    geoQueryModel?.l?.get(0) ?: 0.0,
                                    geoQueryModel?.l?.get(1) ?: 0.0
                                ) // L = Letter 'L' lower case
                                val driverGeoModel = DriverGeoModel(snapshot.key, geoLocation)
                                val newDriverLocation = Location("")
                                newDriverLocation.latitude = geoLocation.latitude
                                newDriverLocation.longitude = geoLocation.longitude
                                val newDistance =
                                    location.distanceTo(newDriverLocation) / 1000 // in km
                                if (newDistance <= LIMIT_RANGE) {
                                    findDriverByKey(driverGeoModel)
                                }
                            }

                            override fun onChildChanged(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {
                            }

                            override fun onChildRemoved(snapshot: DataSnapshot) {
                            }

                            override fun onChildMoved(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Snackbar.make(
                                    mapFragment.requireView(),
                                    error.message,
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }

                        })
                    }else{
                        Toast.makeText(this, getString(R.string.kota_tidak_ditemukan), Toast.LENGTH_SHORT).show()
                    }
                }catch (e:IOException){
                    Snackbar.make(mapFragment.requireView(),e.message?:"",Snackbar.LENGTH_LONG).show()
                }
            }
    }

    //todo 5 load all driver
    private fun addDriverMarker() {

        if (Common.driverFound.size > 0) {

            //todo 4 Find Nearby Driver
            Observable.fromIterable(Common.driverFound.keys)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ key: String? -> //todo 5 Find Nearby Driver
                    findDriverByKey(Common.driverFound[key]) //todo 6 Find Nearby Driver
                }, {
                    Snackbar.make(mapFragment.requireView(), it.message ?: "", Snackbar.LENGTH_SHORT).show()
                })
        } else {
            Snackbar.make(mapFragment.requireView(), "Driver not found", Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    //todo 6 load all driver
    private fun findDriverByKey(it: DriverGeoModel?) {
        FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_INFO_REFERENCE)
            .child(it?.key ?: "")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()) {
                        it?.driverInfoModel = (snapshot.getValue(DriverInfoModel::class.java))
                        Common.driverFound[it?.key]?.driverInfoModel = (snapshot.getValue(DriverInfoModel::class.java)) //todo 7 Find Nearby Driver (next RequestDriverActivity)
                        iFirebaseDriverInfoListener.onDriverInfoLoadSuccess(it)
                    } else {
                        iFirebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_founds) + it?.key)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(error.message)
                }

            })
    }

    private fun buildLocationRequest() {
        if (locationRequest == null) {
            locationRequest = LocationRequest()
            locationRequest?.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            locationRequest?.setFastestInterval(15000) // 15 detik
            locationRequest?.interval = 10000 // 10 detik
            locationRequest?.setSmallestDisplacement(50f)// 50m
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        //todo 6
        Dexter.withContext(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    Toast.makeText(
                        this@MapsActivity,
                        "Permission" + p0?.permissionName,
                        Toast.LENGTH_SHORT
                    ).show()

                    if (ActivityCompat.checkSelfPermission(
                            this@MapsActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            this@MapsActivity,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        //Location Permission already granted
                        Toast.makeText(this@MapsActivity, "Permission granted", Toast.LENGTH_SHORT)
                            .show()
                        return
                    } else {
                        //Request Location Permission
                        checkLocationPermission()
                    }
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationClickListener {
                        fusedLocationProviderClient?.lastLocation
                            ?.addOnFailureListener {
                                Toast.makeText(this@MapsActivity, it.message, Toast.LENGTH_SHORT)
                                    .show()
                            }
                            ?.addOnSuccessListener {
                                val userLatLng = LatLng(it.latitude, it.longitude)
                                mMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        userLatLng, 18f
                                    )
                                )
                            }
                        true
                    }

                    val locationButton = (mapFragment.requireView()
                        .findViewById<View>("1".toInt())
                        .parent as View).findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250 //todo 8 load all driver

                    //todo 9
                    buildLocationRequest()
                    buildLocationCallback()
                    updateLocation()

                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(this@MapsActivity, p0?.permissionName, Toast.LENGTH_SHORT).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {
                }

            })
            .check()

        //todo 7 load all driver
        //emable zoom
        mMap.uiSettings.isZoomControlsEnabled = true


    }

    private fun checkLocationPermission() {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            if (!checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                !checkSinglePermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                val permList = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                requestPermissions(permList, MY_PERMISSIONS_REQUEST_LOCATION)
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                checkSinglePermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                checkSinglePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) return
            val permList = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            requestPermissions(permList, MY_PERMISSIONS_REQUEST_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (checkSinglePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) return
            AlertDialog.Builder(this)
                .setTitle("Location Permission Needed")
                .setMessage("This app needs the Location permission, please accept to use location functionality")
                .setPositiveButton(
                    "OK"
                ) { _, _ ->
                    //Prompt the user once explanation has been shown
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        MapsActivity.MY_PERMISSIONS_REQUEST_LOCATION
                    )
                }
                .create()
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MapsActivity.MY_PERMISSIONS_REQUEST_LOCATION
            )
        }
    }


    private fun Context.checkSinglePermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    //todo 10 load all driver (finish)
    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        if (!Common.marketList.containsKey(driverGeoModel?.key)) {
            Common.marketList.put(
                driverGeoModel?.key ?: "",
                mMap.addMarker(
                    MarkerOptions()
                        .position(
                            LatLng(
                                driverGeoModel?.geoLocation?.latitude ?: 0.0,
                                driverGeoModel?.geoLocation?.longitude ?: 0.0
                            )
                        )
                        .flat(true)
                        .title(
                            Common.buildName(
                                driverGeoModel?.driverInfoModel?.firstName,
                                driverGeoModel?.driverInfoModel?.lastName
                            )
                        )
                        .snippet(driverGeoModel?.driverInfoModel?.phoneNumber)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.carr))
                )
            )

            if (!TextUtils.isEmpty(cityName)) {
                val driverLocations = FirebaseDatabase.getInstance()
                    .getReference(Common.DRIVER_LOCATION_REFERENCE)
                    .child(cityName)
                    .child(driverGeoModel?.key ?: "")
                driverLocations.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.hasChildren()) {
                            if (Common.marketList.get(driverGeoModel?.key) != null) {
                                val marker = Common.marketList.get(driverGeoModel?.key)
                                marker?.remove()
                                Common.marketList.remove(driverGeoModel?.key)
                                Common.driversSubscribe.remove(driverGeoModel?.key) //todo 2 animation car
                                driverLocations.removeEventListener(this)
                            }
                        }else{
                            //todo 3 animation car
                            if (Common.marketList.get(driverGeoModel?.key) != null) {
                                val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                                val animationModel = geoQueryModel?.let { AnimationModel(false, it) }
                                if (Common.driversSubscribe.get(driverGeoModel?.key) != null) {
                                    val marker = Common.marketList.get(driverGeoModel?.key)
                                    val oldPosition = Common.driversSubscribe.get(driverGeoModel?.key)

                                    val from = StringBuilder()
                                        .append(oldPosition?.geoQueryModel?.l?.get(0))
                                        .append(",")
                                        .append(oldPosition?.geoQueryModel?.l?.get(1))
                                        .toString()

                                    val to = StringBuilder()
                                        .append(animationModel?.geoQueryModel?.l?.get(0))
                                        .append(",")
                                        .append(animationModel?.geoQueryModel?.l?.get(1))
                                        .toString()

                                    moveMarkerAnimation(
                                        driverGeoModel?.key ?: "",
                                        animationModel,
                                        marker,
                                        from,
                                        to
                                    )
                                } else {
                                    animationModel?.let {
                                        Common.driversSubscribe.put(
                                            driverGeoModel?.key ?: "",
                                            it
                                        )
                                    }  // first location init
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
                    }

                })
            }
        }
    }

    private fun moveMarkerAnimation(
        key: String,
        newData: AnimationModel?,
        marker: Marker?,
        from: String,
        to: String
    ) {
        if (!newData?.isRun!!) {
            //Request Api
                compositeDisposable.add(iGoogleAPI?.getDirections("driving",
                "less_driving",
                from, to,
                getString(R.string.google_maps_key))
                    !!.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        Log.d("API_RETURN", it)
                        try {
                            val jsonObject = JSONObject(it)
                            val jsonArray = jsonObject.getJSONArray("routes")
                            for (i in 0 until jsonArray.length()) {
                                val route = jsonArray.getJSONObject(i)
                                val poly = route.getJSONObject("overview_polyline")
                                val polyLine = poly.getString("points")
                                newData.polylineList = Common.decodePoly(polyLine)
                            }

                            //Moving
                            newData.index = -1
                            newData.next = 1

                            val runnable = object : Runnable {
                                override fun run() {
                                    if (newData.polylineList != null && newData.polylineList!!.size > 1) {
                                        if (newData.index < newData.polylineList!!.size- 2) {
                                            newData.index++
                                            newData.next = newData.index + 1
                                            newData.start = newData.polylineList!![newData.index]!!
                                            newData.end = newData.polylineList!![newData.next]!!
                                        }
                                        val valueAnimator = ValueAnimator.ofInt(0, 1)
                                        valueAnimator.duration = 3000
                                        valueAnimator.interpolator = LinearInterpolator()
                                        valueAnimator.addUpdateListener {
                                            newData.v = it.animatedFraction
                                            newData.lat = newData.v * newData.end!!.latitude + (1 - newData.v) * newData.start!!.latitude
                                            newData.lng = newData.v * newData.end!!.longitude + (1 - newData.v) * newData.start!!.longitude
                                            val newPos = LatLng(newData.lat, newData.lng)
                                            marker!!.position = newPos
                                            marker!!.setAnchor(0.5f, 0.5f)
                                            marker!!.rotation = Common.getBearing(newData.start!!, newPos)
                                        }
                                        valueAnimator.start()
                                        if (newData.index < newData.polylineList!!.size - 2) {
                                            newData.handler!!.postDelayed(this, 1500)
                                        } else if (newData.index < newData.polylineList!!.size-1) {
                                            newData.isRun = false
                                            Common.driversSubscribe.put(key, newData)
                                        }
                                    }
                                }

                            }
                            newData.handler!!.postDelayed(runnable, 1500)

                        } catch (e: Exception) {
                            Snackbar.make(mapFragment.requireView(), e.message
                                ?: "", Snackbar.LENGTH_LONG).show()
                        }
                    },{
                        Snackbar.make(mapFragment.requireView(), it.message
                            ?: "", Snackbar.LENGTH_LONG).show()
                    }))
        }
    }


}