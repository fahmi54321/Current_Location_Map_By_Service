package com.android.ridemapservice

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.ridemapservice.Model.MapLocation
import com.android.ridemapservice.Service.MyMapService
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_service_map.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.IOException
import java.util.*

class MainActivityServiceMap : AppCompatActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    //todo 1 current location
    private lateinit var mapFragment: SupportMapFragment

    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    private var mService: MyMapService? = null
    private var mBound = false
    private var mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as MyMapService.LocalBinder
            mService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            mService = null
            mBound = false
        }

    }
    private lateinit var onlineRef: DatabaseReference
    private var currentUserRef: DatabaseReference?=null
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

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onBackgroundLocationRetrive(event: MapLocation) {

        if (event.location != null) {
//            Toast.makeText(this, Common.getLocationText(event.location), Toast.LENGTH_SHORT).show()
            Log.e("map back", "lat : ${event.location.latitude} long : ${event.location.longitude}")
            val newPos = LatLng(
                event.location.latitude,
                event.location.longitude
            )
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))
            val geoCoder = Geocoder(this, Locale.getDefault())
            val addressList: List<Address>?
            try {
                addressList = geoCoder.getFromLocation(
                    event.location.latitude,
                    event.location.longitude,
                    1
                )
                val cityName = addressList[0].locality
                driverLocationRef =
                    FirebaseDatabase.getInstance()
                        .getReference(Common.RIDER_LOCATION_REFERENCE)
                        .child(cityName)
                currentUserRef = driverLocationRef.child(
                    FirebaseAuth.getInstance().currentUser!!.uid
                )
                geoFire = GeoFire(driverLocationRef)

                //update location
                geoFire.setLocation(
                    FirebaseAuth.getInstance().currentUser!!.uid,
                    GeoLocation(
                        event.location.latitude,
                        event.location.longitude
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
        }

    }

    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_map)

        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //todo 3 current location
        init()

    }

    private fun init() {
        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        EventBus.getDefault().unregister(this)

        super.onStop()
    }

    override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {
        if (p1.equals(Common.KEY_REQUEST_LOCATION_UPDATE)) {
            setButtonState(p0!!.getBoolean(Common.KEY_REQUEST_LOCATION_UPDATE, false))
        }
    }

    private fun setButtonState(boolean: Boolean) {
        if (boolean) {
            remove_update_location.isEnabled = true
            request_update_location.isEnabled = false
        } else {
            remove_update_location.isEnabled = false
            request_update_location.isEnabled = true
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap!!

        Dexter.withActivity(this)
            .withPermissions(
                Arrays.asList(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    android.Manifest.permission.FOREGROUND_SERVICE
                )
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
                    request_update_location.setOnClickListener {
                        mService?.requestLocationUpdates()

                        if (ActivityCompat.checkSelfPermission(
                                this@MainActivityServiceMap,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                this@MainActivityServiceMap,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            //Location Permission already granted
                            Toast.makeText(
                                this@MainActivityServiceMap,
                                "Permission granted",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                            return@setOnClickListener
                        } else {
                            //Request Location Permission
                            checkLocationPermission()
                        }
                        mMap.isMyLocationEnabled = true
                        mMap.uiSettings.isMyLocationButtonEnabled = true
                        mMap.setOnMyLocationClickListener {
                            fusedLocationProviderClient?.lastLocation
                                ?.addOnFailureListener {
                                    Toast.makeText(
                                        this@MainActivityServiceMap,
                                        it.message,
                                        Toast.LENGTH_SHORT
                                    ).show()
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

                    }
                    remove_update_location.setOnClickListener {
                        mService?.removeLocationUpdates()
                    }

                    setButtonState(Common.requestingLocationUpdates(this@MainActivityServiceMap))
                    bindService(
                        Intent(
                            this@MainActivityServiceMap,
                            MyMapService::class.java
                        ),
                        mServiceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: MutableList<PermissionRequest>?,
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
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
                requestPermissions(permList, MapsActivity.MY_PERMISSIONS_REQUEST_LOCATION)
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                checkSinglePermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                checkSinglePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                checkSinglePermission(Manifest.permission.FOREGROUND_SERVICE)
            ) return
            val permList = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE
            )
            requestPermissions(permList, MapsActivity.MY_PERMISSIONS_REQUEST_LOCATION)
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