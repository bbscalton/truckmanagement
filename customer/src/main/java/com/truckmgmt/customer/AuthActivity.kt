package com.truckmgmt.customer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.truckmgmt.customer.databinding.ActivityAuthBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.truckmgmt.shared.FleetIdGenerator
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            lifecycleScope.launch {
                try {
                    auth.signInWithEmailAndPassword(
                        binding.inputEmail.text.toString().trim(),
                        binding.inputPassword.text.toString(),
                    ).await()
                    goHome()
                } catch (e: Exception) {
                    Toast.makeText(this@AuthActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnRegister.setOnClickListener {
            lifecycleScope.launch {
                val emailStr = binding.inputEmail.text.toString().trim()
                val pass = binding.inputPassword.text.toString()
                val fid = FleetIdGenerator.normalize(binding.inputFleetId.text.toString())
                if (fid.isBlank()) {
                    Toast.makeText(
                        this@AuthActivity,
                        "Fleet ID is required — ask your dispatcher for the 6-character code",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                try {
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
                } catch (e: FleetLinkPermissionException) {
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
