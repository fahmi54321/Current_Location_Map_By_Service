package com.android.currentlocationmapbyservice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.currentlocationmapbyservice.Callback.FirebaseDriverInfoListener
import com.android.currentlocationmapbyservice.Callback.FirebaseFailedListener
import com.android.currentlocationmapbyservice.Model.DriverGeoModel
import com.android.currentlocationmapbyservice.Model.DriverInfoModel
import com.android.currentlocationmapbyservice.Model.GeoQueryModel
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, FirebaseDriverInfoListener {

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
    private lateinit var geoFire: GeoFire
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
        geoFire.removeLocation(FirebaseAuth.getInstance().currentUser?.uid)
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

    }

    private fun init() {

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
                    cityName = addressList[0].locality

                    //query
                    val driver_location_ref = FirebaseDatabase.getInstance()
                        .getReference((Common.DRIVER_LOCATION_REFERENCE))
                        .child(cityName)
                    val gf = GeoFire(driver_location_ref)
                    val geoQuery = gf.queryAtLocation(GeoLocation(location.latitude,location.longitude),distance)
                    geoQuery.removeAllListeners()
                    geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener{
                        override fun onKeyEntered(key: String?, location: GeoLocation?) {
                            Common.driverFound.add(DriverGeoModel(key,location))
                        }

                        override fun onKeyExited(key: String?) {
                        }

                        override fun onKeyMoved(key: String?, location: GeoLocation?) {
                        }

                        override fun onGeoQueryReady() {
                            if (distance<=LIMIT_RANGE){
                                distance++
                                loadAvailableDrivers()
                            }else{
                                distance = 0.0
                                addDriverMarker()
                            }
                        }

                        override fun onGeoQueryError(error: DatabaseError?) {
                            Snackbar.make(mapFragment.requireView(),error?.message?:"",Snackbar.LENGTH_LONG).show()
                        }

                    })

                    driver_location_ref.addChildEventListener(object : ChildEventListener{
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
                            Snackbar.make(mapFragment.requireView(),error.message,Snackbar.LENGTH_LONG).show()
                        }

                    })
                }catch (e:IOException){
                    Snackbar.make(mapFragment.requireView(),e.message?:"",Snackbar.LENGTH_LONG).show()
                }
            }
    }

    //todo 5 load all driver
    private fun addDriverMarker() {

        if (Common.driverFound.size > 0) {
            Observable.fromIterable(Common.driverFound)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ driverGeoModel: DriverGeoModel? ->
                    findDriverByKey(driverGeoModel)
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

                                driverLocations.removeEventListener(this)
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


}