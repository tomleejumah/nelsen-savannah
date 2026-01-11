package com.app.nisisiafrica

import android.content.Intent
import android.os.Bundle
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
        // Start splash screen
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isReady }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_launcher)

        // Handle system insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        lifecycleScope.launch {

            val isFirstTime = Util.getState("is-FirstTime", true)
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser

            val intent = when {
                isFirstTime -> {
                    Intent(this@LauncherActivity, IntroActivity::class.java)
                }

                user == null -> {
                    Intent(this@LauncherActivity, LoginSignUpActivity::class.java)
                }

                else -> {
                    // Force fresh truth from Firebase
                    user.reload().addOnSuccessListener {

                        if (user.isEmailVerified) {
                            startActivity(
                                Intent(this@LauncherActivity, MainActivity::class.java)
                            )
                        } else {
                            startActivity(
                                Intent(this@LauncherActivity, VerifyEmailActivity::class.java)
                            )
                        }

                        finish()
                    }
                    return@launch
                }
            }

            isReady = true
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
/*
        lifecycleScope.launch {
            val isFirstTime = Util.getState("is-FirstTime", true)
            val intent: Intent = when {
                isFirstTime -> {
                    Intent(this@LauncherActivity, IntroActivity::class.java)
                }
                FirebaseAuth.getInstance().currentUser != null -> {
                    Intent(this@LauncherActivity, MainActivity::class.java)
                }
                else -> {
                    Intent(this@LauncherActivity, LoginSignUpActivity::class.java)
                }
            }
            isReady = true
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

 */
    }
}
