package com.android.currentlocationmapbyservice

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
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
import com.android.currentlocationmapbyservice.Model.DriverGeoModel
import com.android.currentlocationmapbyservice.Remote.IGoogleAPI
import com.android.currentlocationmapbyservice.Remote.RetrofitClient
import com.android.currentlocationmapbyservice.Utils.UserUtils

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
import kotlinx.android.synthetic.main.activity_request_driver.*
import kotlinx.android.synthetic.main.layout_confirm_pickup.*
import kotlinx.android.synthetic.main.layout_confirm_uber.*
import kotlinx.android.synthetic.main.layout_finding_your_driver.*
import kotlinx.android.synthetic.main.origin_info_windows.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.lang.Exception

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txt_origin:TextView

    //Spinning animation
    private var animator: ValueAnimator? = null
    private val DESIRED_NUM_OF_SPINS = 5
    private val DESIRED_SECONDS_PER_ONE_FULL_360_SPIN = 40

    //effect
    private var lastUserCircle: Circle? = null
    val duration = 1000
    private var lastPulseAnimator: ValueAnimator? = null

    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    companion object {
        val MY_PERMISSIONS_REQUEST_LOCATION = 99
    }

    //todo 3 estimate_routes
    private var selectPlaceEvent: SelectPlaceEvent? = null

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
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
        super.onStart()
    }

    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(SelectPlaceEvent::class.java)) {
            EventBus.getDefault().removeStickyEvent(SelectPlaceEvent::class.java)
        }
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onSelectePlaceEvent(event: SelectPlaceEvent) {
        selectPlaceEvent = event
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_driver)

        //todo 3 estimate_routes
        init()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    private fun init() {
        iGoogleApi = RetrofitClient.intance?.create(IGoogleAPI::class.java)

//      todo 2 confirm_uber
        btn_confirm_uber.setOnClickListener {
            confirm_pickup_layout.visibility = View.VISIBLE
            confirm_uber_layout.visibility = View.GONE
            setDataPickup()
        }

//      todo 2 confirm_pickup_spot
        btn_confirm_uber_pickup.setOnClickListener {
            if (mMap == null) {
                return@setOnClickListener
            }
            if (selectPlaceEvent == null) {
                return@setOnClickListener
            }

            //clear map
            mMap.clear()

            //Tilt
            val cameraPos = CameraPosition.Builder().target(selectPlaceEvent?.origin)
                .tilt(45f)
                .zoom(16f)
                .build()
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

            //start animation
            addMarkerWithPulseAnimation()
        }
    }

    //      todo 3 confirm_pickup_spot
    private fun addMarkerWithPulseAnimation() {
        confirm_pickup_layout.visibility = View.GONE
        fill_maps.visibility = View.VISIBLE
        finding_you_ride_layout.visibility = View.VISIBLE
        originMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.defaultMarker())
                .position(selectPlaceEvent?.origin!!)
        )

        addPulsatingEffect(selectPlaceEvent?.origin)
    }

    //      todo 4 confirm_pickup_spot
    private fun addPulsatingEffect(origin: LatLng?) {
        if (lastPulseAnimator != null) {
            lastPulseAnimator?.cancel()
        }
        if (lastUserCircle != null) {
            lastUserCircle?.center = origin
        }

        lastPulseAnimator =
            Common.valueAnimate(duration, object : ValueAnimator.AnimatorUpdateListener {
                override fun onAnimationUpdate(p0: ValueAnimator?) {
                    if (lastUserCircle != null) {
                        lastUserCircle?.radius = p0?.animatedValue.toString().toDouble()
                    } else {
                        lastUserCircle = mMap.addCircle(
                            CircleOptions()
                                .center(origin)
                                .radius(p0?.animatedValue.toString().toDouble())
                                .strokeColor(Color.WHITE)
                                .fillColor(
                                    ContextCompat.getColor(
                                        this@RequestDriverActivity,
                                        R.color.teal_700
                                    )
                                )
                        )
                    }
                }

            })

        //Start rotating camera
        startMapCameraSpinningAnimation(mMap.cameraPosition.target)
    }

    //      todo 5 confirm_pickup_spot
    private fun startMapCameraSpinningAnimation(target: LatLng?) {
        if (animator != null) animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, (DESIRED_NUM_OF_SPINS.times(360).toFloat()))
        animator?.duration =
            (DESIRED_NUM_OF_SPINS.times(DESIRED_SECONDS_PER_ONE_FULL_360_SPIN).times(1000).toLong())
        animator?.interpolator = LinearInterpolator()
        animator?.startDelay = (100)
        animator?.addUpdateListener {
            val newBearingValue = it.animatedValue as Float
            mMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(target)
                        .zoom(16f)
                        .tilt(45f)
                        .bearing(newBearingValue)
                        .build()
                )
            )
        }
        animator?.start()

//        todo 8 Find Nearby Driver
        findNearbyDriver(target)
    }

    fun findNearbyDriver(target: LatLng?) {
        if (Common.driverFound.size > 0) {
            var main = 0f
            var foundDrivers = Common.driverFound[Common.driverFound.keys.iterator().next()]
            var currentRiderLocation = Location("")
            currentRiderLocation.latitude = target?.latitude ?: 0.0
            currentRiderLocation.longitude = target?.longitude ?: 0.0

            for (key in Common.driverFound.keys) {
                var driverLocation = Location("")
                driverLocation.latitude = Common.driverFound[key]?.geoLocation?.latitude ?: 0.0
                driverLocation.longitude = Common.driverFound[key]?.geoLocation?.longitude ?: 0.0

                if (main == 0f) {
                    main = driverLocation.distanceTo(currentRiderLocation)
                    foundDrivers = Common.driverFound[key]
                } else if (driverLocation.distanceTo(currentRiderLocation) < main) {
                    main = driverLocation.distanceTo(currentRiderLocation)
                    foundDrivers = Common.driverFound[key]
                }
            }
            Snackbar.make(
                main_layout, StringBuilder("Found Driver: ")
                    .append(foundDrivers?.driverInfoModel?.phoneNumber), Snackbar.LENGTH_LONG
            ).show()

            //todo 1 notif request driver
            UserUtils.sendRequestToDriver(this,main_layout,foundDrivers,target)

        } else {
            Snackbar.make(main_layout, getString(R.string.driver_not_found), Snackbar.LENGTH_LONG)
                .show()
        }
    }

    override fun onDestroy() {
        if (animator != null) {
            animator?.end()
        }
        super.onDestroy()
    }

    private fun setDataPickup() {
        txt_address_pickup.text = if (txt_origin != null) txt_origin.text else "None"
        mMap.clear()
        addPickupMarker()
    }

    private fun addPickupMarker() {
        val view = layoutInflater.inflate(R.layout.pickup_info_windows, null)
        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        originMarker = mMap.addMarker(
            MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectPlaceEvent?.origin!!)
        )
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        drawPath(selectPlaceEvent)

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
        txt_origin = view.findViewById<View>(R.id.txt_origin) as TextView

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