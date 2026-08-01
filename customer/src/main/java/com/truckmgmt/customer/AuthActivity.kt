package com.truckmgmt.customer

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
                try {
                    val emailStr = email.text.toString().trim()
                    val pass = password.text.toString()
                    val fid = fleetId.text.toString().trim()
                    val result = auth.createUserWithEmailAndPassword(emailStr, pass).await()
                    val uid = result.user?.uid ?: return@launch
                    val fleetIds = if (fid.isNotBlank()) listOf(fid) else emptyList()
                    db.collection(TruckMgmtConstants.COL_CUSTOMER_PROFILES).document(uid).set(
                        mapOf(
                            "email" to emailStr,
                            "displayName" to emailStr.substringBefore("@"),
                            "fleetIds" to fleetIds,
                            "primaryFleetId" to fid.ifBlank { null },
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                    if (fid.isNotBlank()) {
                        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                            .collection(TruckMgmtConstants.COL_CUSTOMERS).document(uid)
                            .set(
                                mapOf(
                                    "email" to emailStr,
                                    "displayName" to emailStr.substringBefore("@"),
                                    "linkedAt" to FieldValue.serverTimestamp(),
                                )
                            ).await()
                    }
                    goHome()
                } catch (e: Exception) {
                    Toast.makeText(this@AuthActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, CustomerHomeActivity::class.java))
        finish()
    }
}
