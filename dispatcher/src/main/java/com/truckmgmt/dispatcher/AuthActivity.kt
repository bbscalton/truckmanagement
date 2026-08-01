package com.truckmgmt.dispatcher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
        val fleetName = findViewById<EditText>(R.id.inputFleetName)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            lifecycleScope.launch {
                try {
                    auth.signInWithEmailAndPassword(email.text.toString().trim(), password.text.toString()).await()
                    goDashboard()
                } catch (e: Exception) {
                    Toast.makeText(this@AuthActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val emailStr = email.text.toString().trim()
                    val pass = password.text.toString()
                    val name = fleetName.text.toString().trim().ifEmpty { "My Fleet" }
                    val result = auth.createUserWithEmailAndPassword(emailStr, pass).await()
                    val uid = result.user?.uid ?: return@launch
                    val fleetId = FleetCreateHelper.createFleetWithShortId(db, uid, name)
                    db.collection(TruckMgmtConstants.COL_DISPATCHER_PROFILES).document(uid).set(
                        mapOf(
                            "email" to emailStr,
                            "displayName" to emailStr.substringBefore("@"),
                            "fleetIds" to listOf(fleetId),
                            "primaryFleetId" to fleetId,
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                    Toast.makeText(
                        this@AuthActivity,
                        "Fleet created! Your fleet ID is $fleetId — share it with customers.",
                        Toast.LENGTH_LONG,
                    ).show()
                    goDashboard()
                } catch (e: Exception) {
                    Toast.makeText(this@AuthActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
