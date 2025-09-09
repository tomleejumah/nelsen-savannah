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

    private val TAG = "SplashActivity"
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

        Util.init(this)

        lifecycleScope.launch {
            val isFirstTime = Util.getState("is-FirstTime", true)
//            val isFirstTime = true
            val intent: Intent = when {
                isFirstTime -> {
                    Log.d(TAG, "onCreate: Intro called")
                    Intent(this@LauncherActivity, IntroActivity::class.java)
                }
                FirebaseAuth.getInstance().currentUser != null -> {
                    Intent(this@LauncherActivity, MainActivity::class.java)
                }
                else -> {
                    Log.d(TAG, "onCreate: Login Called")
                    Intent(this@LauncherActivity, LoginSignUpActivity::class.java)
                }
            }

            isReady = true

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }
}
