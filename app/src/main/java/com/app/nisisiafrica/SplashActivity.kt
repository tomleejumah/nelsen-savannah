package com.app.nisisiafrica

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.app.nisisiafrica.Auth.LoginSignUpActivity
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val TAG = "SplashActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //todo impl splashScreen
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        Utils.init(this)
        val isFirstTime = Utils.getState("is-FirstTime", true)
        val intent: Intent
        if (isFirstTime) {
            intent = Intent(this, IntroActivity::class.java)
            Log.d(TAG, "onCreate: Intro called")
        } else if (FirebaseAuth.getInstance().currentUser != null) {
            intent = Intent(this, MainActivity::class.java)
        } else {
            intent = Intent(this, LoginSignUpActivity::class.java)
            Log.d(TAG, "onCreate: Login Called")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}