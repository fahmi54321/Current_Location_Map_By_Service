package com.android.ridemapservice

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import java.io.IOException
import java.util.*


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

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

        //todo 14
        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")


        //todo fix permission first launch
        permissionLocation()

        //todo 4 current location
        buildLocationRequest()
        buildLocationCallback()

        //todo 7
        updateLocation()
    }

    private fun permissionLocation() {
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
        }else{
            checkLocationPermission()
        }
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

                    //todo 15
                    val geoCoder = Geocoder(this@MapsActivity, Locale.getDefault())
                    val addressList: List<Address>?
                    try {
                        addressList = geoCoder.getFromLocation(
                                locationResults?.lastLocation?.latitude?:0.0,
                                locationResults?.lastLocation?.longitude?:0.0,
                                1
                        )
                        val cityName = addressList[0].locality
                        driverLocationRef =
                                FirebaseDatabase.getInstance()
                                        .getReference(Common.DRIVER_LOCATION_REFERENCE)
                                        .child(cityName)
                        currentUserRef = driverLocationRef.child(
                                FirebaseAuth.getInstance().currentUser!!.uid
                        )
                        geoFire = GeoFire(driverLocationRef)

                        //update location
                        geoFire.setLocation(
                                FirebaseAuth.getInstance().currentUser!!.uid,
                                GeoLocation(
                                        locationResults?.lastLocation?.latitude?:0.0,
                                        locationResults?.lastLocation?.longitude?:0.0
                                )
                        ) { key: String, error: DatabaseError? ->
                            if (error != null) {
                                Snackbar.make(
                                        mapFragment.requireView(),
                                        error.message,
                                        Snackbar.LENGTH_LONG
                                ).show()
                            } else {
                                Snackbar.make(
                                        mapFragment.requireView(),
                                        "You are online",
                                        Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        }

                        registerOnlineSystem()

                        //modifikasi rule
//                    "MotorLocations" : {
//                            "$uid" : {
//                            ".read" : "auth !=null",
//                            ".write" : "$auth != null"
//                        }
//                    },

                    } catch (e: IOException) {
                        Snackbar.make(mapFragment.requireView(), e.message?:"", Snackbar.LENGTH_SHORT).show()
                    }

                    //setting ulang rules realtime database di firebase (testing aplikasi)
                    // we will let info can be read by anyone (with authenticated) but only write by theirself
                    /*{
                    "rules": {
                    "MotorInfo" : {
                            "$uid" : {
                                ".read" : "auth !=null",
                                ".write" : "$uid == auth.uid"
                            }
                        }
                    },
                    "MotorLocations" : {
                            "$uid" : {
                            ".read" : "auth !=null",
                            ".write" : "$uid == auth.uid"
                        }
                    }
                }*/

                }
            }
        }
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
                    params.bottomMargin = 50

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
                    TODO("Not yet implemented")
                }

            })
            .check()


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


}