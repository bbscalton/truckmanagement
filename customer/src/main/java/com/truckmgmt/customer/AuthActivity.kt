package com.truckmgmt.customer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.truckmgmt.shared.FleetIdGenerator
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val email = findViewById<EditText>(R.id.inputEmail)
        val password = findViewById<EditText>(R.id.inputPassword)
        val fleetId = findViewById<EditText>(R.id.inputFleetId)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            lifecycleScope.launch {
                try {
                    auth.signInWithEmailAndPassword(email.text.toString().trim(), password.text.toString()).await()
                    goHome()
                } catch (e: Exception) {
                    Toast.makeText(this@AuthActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            lifecycleScope.launch {
                val emailStr = email.text.toString().trim()
                val pass = password.text.toString()
                val fid = FleetIdGenerator.normalize(fleetId.text.toString())
                if (fid.isBlank()) {
                    Toast.makeText(this@AuthActivity, "Fleet ID is required — ask your dispatcher for the 6-character code", Toast.LENGTH_LONG).show()
                    return@launch
                }
                try {
                    val fleetSnap = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid).get().await()
                    if (!fleetSnap.exists()) {
                        Toast.makeText(this@AuthActivity, "Fleet \"$fid\" not found. Check the ID from your dispatcher.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val result = auth.createUserWithEmailAndPassword(emailStr, pass).await()
                    val uid = result.user?.uid ?: return@launch
                    try {
                        FleetLinkHelper.linkCustomerToFleet(db, uid, emailStr, fid)
                    } catch (e: Exception) {
                        auth.currentUser?.delete()
                        throw e
                    }
                    goHome()
                } catch (e: FleetNotFoundException) {
                    Toast.makeText(this@AuthActivity, e.message, Toast.LENGTH_LONG).show()
                } catch (e: FirebaseFirestoreException) {
                    val msg = when (e.code) {
                        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                            "Permission denied linking to fleet. Contact your dispatcher."
                        else -> e.message ?: "Registration failed"
                    }
                    Toast.makeText(this@AuthActivity, msg, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@AuthActivity, e.message ?: "Registration failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, CustomerHomeActivity::class.java))
        finish()
    }
}
