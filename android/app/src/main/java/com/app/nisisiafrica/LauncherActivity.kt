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
import com.app.nisisiafrica.Utils.AppUpdateManager
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PENDING_APP_LINK = "extra_pending_app_link"
    }

    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isReady }

        super.onCreate(savedInstanceState)
        persistIncomingLink(intent)
        enableEdgeToEdge()
        setContentView(R.layout.activity_launcher)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        AppUpdateManager.scheduleBackgroundChecks(applicationContext)
        AppUpdateManager.checkForUpdates(
            activity = this,
            forceShow = false,
            onUpdateRequired = {
                // The mandatory update UI lives in this activity, so let the
                // system splash disappear without continuing into the app.
                isReady = true
            },
            onReady = {
                continueLaunch()
            },
        )
    }

    // The launcher is singleTop. A second shared URL arriving while startup is
    // in progress must replace the pending destination, not silently disappear.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        persistIncomingLink(intent)
    }

    private fun persistIncomingLink(source: Intent?) {
        // Keep canonical App Links through intro, sign-in, verification and PIN.
        source?.data?.takeIf {
            it.scheme == "https" && it.host == "nelsen-savannah.co.ke"
        }?.let { link ->
            getSharedPreferences("nelsen_app_links", MODE_PRIVATE)
                .edit().putString("pending_url", link.toString()).apply()
        }
    }

    private fun continueLaunch() {
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

    private fun navigateAndFinish(destination: Intent) {
        val incoming = intent?.data
        if (incoming != null && incoming.scheme == "https" && incoming.host == "nelsen-savannah.co.ke") {
            destination.putExtra(EXTRA_PENDING_APP_LINK, incoming.toString())
        }
        isReady = true
        startActivity(destination)
        finish()
    }
}
