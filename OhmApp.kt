package com.app.ohmplug

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.widget.Toast
import androidx.core.app.TaskStackBuilder
import com.app.ohmplug.base.Navigator
import com.app.ohmplug.base.network.LiveResponse
import com.app.ohmplug.auth.common.model.AuthRepository
import com.app.ohmplug.auth.common.view.AuthActivity
import com.app.ohmplug.auth.common.view.TermsActivity
import com.app.ohmplug.common.PreferencesHelper
import com.app.ohmplug.common.analytics.SnowplowTrackerBuilder
import com.app.ohmplug.dashboard.view.MainActivity
import com.app.ohmplug.splash.view.SplashActivity
import com.facebook.FacebookSdk
import com.mlykotom.valifi.ValiFi
import com.thingclips.smart.home.sdk.ThingHomeSdk
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltAndroidApp
class OhmApp : Application(), Navigator {

    companion object{
        lateinit var instance: OhmApp
    }

    @Inject
    lateinit var repository: AuthRepository

    @Inject
    lateinit var preferencesHelper: PreferencesHelper

    override fun onCreate() {
        super.onCreate()
        instance = this


        // Disable auto Facebook stuff (it will still be available when you log in)
        FacebookSdk.setAutoInitEnabled(false)
        FacebookSdk.setAdvertiserIDCollectionEnabled(false)
        FacebookSdk.setAutoLogAppEventsEnabled(false)
        FacebookSdk.setCodelessDebugLogEnabled(false)

        // Setup your other SDKs
        ValiFi.install(applicationContext)

        // Enable StrictMode in debug builds
        if (BuildConfig.DEBUG) {
            val strictPolicy = StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
            StrictMode.setThreadPolicy(strictPolicy)
        }

        CoroutineScope(Dispatchers.IO).launch {
            cacheDir
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                ThingHomeSdk.init(instance)
            } catch (e: Exception) {
                Log.e("OhmApp", "Tuya init failed", e)
            }
        }

        // Init Snowplow a bit later, off main thread
        CoroutineScope(Dispatchers.Default).launch {
            delay(500)
            initSnowplowWithUserId()
        }

    }

    private suspend fun initSnowplowWithUserId() {
        val userId = preferencesHelper.getUserId()
        if (userId != null) {
            initSnowPlow(userId)
        } else if (preferencesHelper.getAccessToken() != null) {
            repository.getOhmConnectUserId().collect { response ->
                when (response) {
                    is LiveResponse.Error -> {
                        initSnowPlow()
                    }
                    is LiveResponse.Status -> {}
                    is LiveResponse.Success -> {
                        initSnowPlow(response.data?.data?.viewer?.id)
                    }
                }
            }
        }
    }

    private fun initSnowPlow(userId: String? = null) {
        SnowplowTrackerBuilder.customTrackerInitialization(
            applicationContext,
            userId
        )
    }

    /* override fun getWorkManagerConfiguration() = Configuration.Builder()
         .setWorkerFactory(workerFactory)
         .build()*/


    override fun startModule(
        activity: Activity,
        modules: Navigator.Modules,
        bundle: Bundle?,
        startForResult: Int?,
        fromNotification: Boolean
    ) {
        val intent = Intent()
        when (modules) {
            Navigator.Modules.SPLASH -> intent.setClass(activity, SplashActivity::class.java)
            Navigator.Modules.AUTH -> intent.setClass(activity, AuthActivity::class.java)
            Navigator.Modules.HOME -> intent.setClass(activity, MainActivity::class.java)
            Navigator.Modules.TERMS -> intent.setClass(activity, TermsActivity::class.java)
        }
        if (bundle != null) {
            intent.putExtras(bundle)
        }
        try {
            if (startForResult == null) {
                if (fromNotification) {
                    TaskStackBuilder.create(this).addNextIntentWithParentStack(intent)
                        .startActivities()
                } else activity.startActivity(intent)
            } else {
                activity.startActivityForResult(intent, startForResult)
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                getString(R.string.this_feature_is_under_development),
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    override fun onTerminate() {
        super.onTerminate()
        ThingHomeSdk.onDestroy()
    }
}
