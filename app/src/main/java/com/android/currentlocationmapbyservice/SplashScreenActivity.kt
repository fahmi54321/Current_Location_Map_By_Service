package com.android.currentlocationmapbyservice

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import com.android.currentlocationmapbyservice.Model.DriverInfoModel
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {

    //todo 2 register
    private lateinit var database: FirebaseDatabase
    private lateinit var driverInfoRef: DatabaseReference

    //todo 4 login
    companion object {
        private val LOGIN_REQUEST_CODE = 7171
    }

    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener

    //todo 5 login
    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    //todo 6 login
    private fun delaySplashScreen() {
        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe({
                firebaseAuth.addAuthStateListener(listener)
            })
    }

    //todo 7 login
    override fun onStop() {
        if (firebaseAuth != null && listener != null) {
            firebaseAuth.removeAuthStateListener(listener)
        }

        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        //todo 8 login
        init()

    }

    //todo 9 login
    private fun init() {

        //todo 3 register
        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Common.DRIVER_INFO_REFERENCE)

        providers = Arrays.asList(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )
        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener {
            val user = it.currentUser
            if (user != null) {
//                Toast.makeText(this, user.phoneNumber, Toast.LENGTH_SHORT).show()
                //todo 4 register
                checkUserFromFirebase()
            } else {
                showLoginLayout()
            }
        }
    }

    //todo 5 register
    private fun checkUserFromFirebase() {
        driverInfoRef.child(FirebaseAuth.getInstance().currentUser?.uid ?: "")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val model = snapshot.getValue(DriverInfoModel::class.java)
                        gotoMapsActivity(model)
                    } else {
                        showRegisterLayout()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_SHORT)
                        .show()
                }

            })
    }

    //todo 6 register
    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.DialogTheme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)
        var firstName = itemView.findViewById<View>(R.id.edt_first_name) as TextInputEditText
        var lastName = itemView.findViewById<View>(R.id.edt_last_name) as TextInputEditText
        var phoneNumber = itemView.findViewById<View>(R.id.edit_phone_number) as TextInputEditText
        var btnContinue = itemView.findViewById<View>(R.id.btn_continue) as Button
        var progressBar = itemView.findViewById<View>(R.id.progress_bar) as ProgressBar

        //set data
        if (FirebaseAuth.getInstance().currentUser?.phoneNumber != null && !TextUtils.isDigitsOnly(
                FirebaseAuth.getInstance().currentUser?.phoneNumber
            )
        ) {
            phoneNumber.setText(FirebaseAuth.getInstance().currentUser?.phoneNumber)
        }

        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        //event
        btnContinue.setOnClickListener {
            if (TextUtils.isDigitsOnly(firstName.text.toString())) {
                Toast.makeText(this, "Please Enter First Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(lastName.text.toString())) {
                Toast.makeText(this, "Please Enter Last Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(phoneNumber.text.toString())) {
                Toast.makeText(this, "Please Enter Phone Number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }else{

                var model = DriverInfoModel()
                model.firstName = firstName.text.toString()
                model.lastName = lastName.text.toString()
                model.phoneNumber = phoneNumber.text.toString()
                model.rating = 0.0

                driverInfoRef.child(FirebaseAuth.getInstance().currentUser?.uid?:"")
                    .setValue(model)
                    .addOnFailureListener{
                        Toast.makeText(this, "" + it.message, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        progressBar.visibility = View.GONE
                    }
                    .addOnSuccessListener {
                        Toast.makeText(this, "Register SuccessFully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        progressBar.visibility = View.GONE
                        gotoMapsActivity(model)
                    }
            }
        }

        //register atur rule database firebase
//            {
//                    "rules": {
//                    ".read": "auth !=null",  // 2021-5-7
//                    ".write": "auth !=null",  // 2021-5-7
//                }
//            }
    }

    private fun gotoMapsActivity(model: DriverInfoModel?) {
        Common.currentUser = model
        startActivity(Intent(this, MapsActivity::class.java))
        finish()
    }

    //todo 10 login
    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_login)
            .setPhoneButtonId(R.id.btn_phone_sign_in)
            .setGoogleButtonId(R.id.btn_google_sign_in)
            .build()

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false)
                .build(), LOGIN_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LOGIN_REQUEST_CODE) {
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser
            } else {
                Toast.makeText(this, "" + response?.error?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}