package com.app.nisisiafrica

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.app.nisisiafrica.Auth.LoginSignUpActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import com.app.nisisiafrica.Utils.Util
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isReady }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_launcher)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        lifecycleScope.launch {
            val isFirstTime = Util.getState("is-FirstTime", true)
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser

            when {
                isFirstTime -> navigateAndFinish(Intent(this@LauncherActivity, IntroActivity::class.java))

                user == null -> navigateAndFinish(Intent(this@LauncherActivity, LoginSignUpActivity::class.java))

                else -> {
                    user.reload()
                        .addOnSuccessListener { navigateAuthenticatedUser(user) }
                        .addOnFailureListener { e ->
                            Log.w("LauncherActivity", "reload failed, using cached session", e)
                            navigateAuthenticatedUser(user)
                        }
                }
            }
        }
    }

    private fun navigateAuthenticatedUser(user: com.google.firebase.auth.FirebaseUser) {
        try {
            val destination = if (user.isEmailVerified) {
                if (PinManager.hasPin(this, user.uid)) {
                    Intent(this, LockScreenActivity::class.java)
                } else {
                    Intent(this, MainActivity::class.java)
                }
            } else {
                Intent(this, VerifyEmailActivity::class.java)
            }
            navigateAndFinish(destination)
        } catch (e: Exception) {
            Log.e("LauncherActivity", "PIN check failed, opening main", e)
            navigateAndFinish(Intent(this, MainActivity::class.java))
        }
    }

    private fun navigateAndFinish(intent: Intent) {
        isReady = true
        startActivity(intent)
        finish()
    }
}
