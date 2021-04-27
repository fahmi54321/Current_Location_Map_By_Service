package com.android.currentlocationmapbyservice

import android.content.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.widget.Toast
import com.android.currentlocationmapbyservice.Model.MapLocation
import com.android.currentlocationmapbyservice.Service.MyMapService
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_service_map.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class MainActivityServiceMap : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

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

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onBackgroundLocationRetrive(event: MapLocation) {

        if (event.location != null) {
            Toast.makeText(this, Common.getLocationText(event.location), Toast.LENGTH_SHORT).show()
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_map)

        Dexter.withActivity(this)
                .withPermissions(Arrays.asList(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        android.Manifest.permission.FOREGROUND_SERVICE))
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
                        request_update_location.setOnClickListener {
                            mService?.requestLocationUpdates()
                        }
                        remove_update_location.setOnClickListener {
                            mService?.removeLocationUpdates()
                        }

                        setButtonState(Common.requestingLocationUpdates(this@MainActivityServiceMap))
                        bindService(Intent(
                                this@MainActivityServiceMap,
                                MyMapService::class.java),
                                mServiceConnection,
                                Context.BIND_AUTO_CREATE)
                    }

                    override fun onPermissionRationaleShouldBeShown(p0: MutableList<PermissionRequest>?, p1: PermissionToken?) {
                        TODO("Not yet implemented")
                    }

                })
                .check()

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
}