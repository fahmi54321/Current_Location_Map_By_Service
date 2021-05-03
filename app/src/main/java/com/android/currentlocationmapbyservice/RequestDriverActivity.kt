package com.android.currentlocationmapbyservice

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.currentlocationmapbyservice.EventBus.SelectPlaceEvent
import com.android.currentlocationmapbyservice.Remote.IGoogleAPI
import com.android.currentlocationmapbyservice.Remote.RetrofitClient

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.ui.IconGenerator
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.lang.Exception

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment:SupportMapFragment
    companion object {
        val MY_PERMISSIONS_REQUEST_LOCATION = 99
    }

    //todo 3 estimate_routes
    private var selectPlaceEvent:SelectPlaceEvent?=null
    //routes
    private val compositeDisposable = CompositeDisposable()
    private var iGoogleApi: IGoogleAPI? = null
    private var blackPolyLine: Polyline? = null
    private var greyPolyLine: Polyline? = null
    private var polyLineOptions: PolylineOptions? = null
    private var blackPolyLineOptions: PolylineOptions? = null
    private var polyLineList: ArrayList<LatLng?>? = null
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null
    override fun onStart() {
        if (!EventBus.getDefault().isRegistered(this)){
            EventBus.getDefault().register(this)
        }
        super.onStart()
    }
    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(SelectPlaceEvent::class.java)){
            EventBus.getDefault().removeStickyEvent(SelectPlaceEvent::class.java)
        }
        EventBus.getDefault().unregister(this)
        super.onStop()
    }
    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    fun onSelectePlaceEvent(event:SelectPlaceEvent){
        selectPlaceEvent = event
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_driver)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //todo 3 estimate_routes
        init()

    }

    private fun init() {
        iGoogleApi = RetrofitClient.intance?.create(IGoogleAPI::class.java)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        //todo 4 estimate_routes
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
        mMap.isMyLocationEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true
        mMap.setOnMyLocationClickListener {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectPlaceEvent?.origin, 18f))
            true
        }

        drawPath(selectPlaceEvent)

        val locationButton = (mapFragment.requireView()
            .findViewById<View>("1".toInt()).parent as View)
            .findViewById<View>("2".toInt())
        val params = locationButton.layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        params.bottomMargin = 250
        mMap.uiSettings.isZoomControlsEnabled = true
        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this,
                    R.raw.uber_maps_style
                )
            )
            if (!success) {
                Snackbar.make(
                    mapFragment.requireView(),
                    "Load map style failed",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Snackbar.make(mapFragment.requireView(), e.message.toString(), Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun drawPath(selectedPlaceEvent: SelectPlaceEvent?) {
        //Request Api
        compositeDisposable.add(
            iGoogleApi?.getDirections(
                "driving",
                "less_driving",
                selectedPlaceEvent?.originString,
                selectedPlaceEvent?.destinationString,
                getString(R.string.google_maps_key)
            )
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
                            polyLineList = Common.decodePoly(polyLine)
                        }

                        polyLineOptions = PolylineOptions()
                        polyLineOptions?.color(Color.GRAY)
                        polyLineOptions?.width(12f)
                        polyLineOptions?.startCap(SquareCap())
                        polyLineOptions?.jointType(JointType.ROUND)
                        polyLineOptions?.addAll(polyLineList)
                        greyPolyLine = mMap.addPolyline(polyLineOptions)

                        blackPolyLineOptions = PolylineOptions()
                        blackPolyLineOptions?.color(Color.BLACK)
                        blackPolyLineOptions?.width(5f)
                        blackPolyLineOptions?.startCap(SquareCap())
                        blackPolyLineOptions?.jointType(JointType.ROUND)
                        blackPolyLineOptions?.addAll(polyLineList)
                        blackPolyLine = mMap.addPolyline(blackPolyLineOptions)

                        //Animator
                        val valueAnimator = ValueAnimator.ofInt(0, 100)
                        valueAnimator.duration = 1100
                        valueAnimator.repeatCount = ValueAnimator.INFINITE
                        valueAnimator.interpolator = LinearInterpolator()
                        valueAnimator.addUpdateListener {
                            val points = greyPolyLine?.points
                            val percentValue = it.animatedValue.toString().toInt()
                            val size = points?.size
                            val newPoints = (size?.times((percentValue / 100.0f)))?.toInt()
                            val p = points?.subList(0, newPoints ?: 0)
                            blackPolyLine?.points = (p)
                        }
                        valueAnimator.start()

                        val latLngBound = LatLngBounds.Builder().include(selectedPlaceEvent?.origin)
                            .include(selectedPlaceEvent?.destination)
                            .build()
                        //Add car icon for origin
                        val objects = jsonArray.getJSONObject(0)
                        val legs = objects.getJSONArray("legs")
                        val legsObject = legs.getJSONObject(0)
                        val time = legsObject.getJSONObject("duration")
                        val duration = time.getString("text")
                        val start_address = legsObject.getString("start_address")
                        val end_address = legsObject.getString("end_address")

                        addOriginMarker(duration, start_address)
                        addDestinationMarker(end_address)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound, 160))
                        mMap.moveCamera(
                            CameraUpdateFactory.zoomTo(
                                mMap.cameraPosition?.zoom?.minus(
                                    1
                                ) ?: 0f
                            )
                        )


                    } catch (e: Exception) {
                        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                    }
                }, {

                })
        )
    }
    private fun addDestinationMarker(endAddress: String) {
        val view = layoutInflater.inflate(R.layout.destination_info_windows, null)
        val txt_destination = view.findViewById<View>(R.id.txt_destination) as TextView
        txt_destination.text = Common.formatAddress(endAddress)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        destinationMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectPlaceEvent?.destination!!)
        )
    }
    private fun addOriginMarker(duration: String, startAddress: String) {
        val view = layoutInflater.inflate(R.layout.origin_info_windows, null)
        val txt_time = view.findViewById<View>(R.id.txt_time) as TextView
        val txt_origin = view.findViewById<View>(R.id.txt_origin) as TextView

        txt_time.text = Common.formatDuration(duration)
        txt_origin.text = Common.formatAddress(startAddress)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        originMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectPlaceEvent?.origin!!)
        )
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
                requestPermissions(permList, MapsActivity.MY_PERMISSIONS_REQUEST_LOCATION)
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
                        RequestDriverActivity.MY_PERMISSIONS_REQUEST_LOCATION
                    )
                }
                .create()
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                RequestDriverActivity.MY_PERMISSIONS_REQUEST_LOCATION
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