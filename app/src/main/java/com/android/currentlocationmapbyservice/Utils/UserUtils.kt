package com.android.currentlocationmapbyservice.Utils

import android.content.Context
import android.widget.Toast
import com.android.currentlocationmapbyservice.Common
import com.android.currentlocationmapbyservice.Model.TokenModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object UserUtils {
    fun updateToken(context: Context, token: String) {
        val tokenModel = TokenModel()
        tokenModel.token = token

        FirebaseDatabase.getInstance().getReference(Common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser?.uid?:"")
            .setValue(tokenModel)
            .addOnFailureListener { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
    }
}