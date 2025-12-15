package com.app.ohmplug

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject


@HiltAndroidApp
class OhmApp : Application(), /*Configuration.Provider,*/ Navigator {

    companion object{
        lateinit var instance: OhmApp
    }

    /* @Inject
     lateinit var workerFactory: HiltWorkerFactory
 */
    @Inject
    lateinit var repository: AuthRepository

    @Inject
    lateinit var preferencesHelper: PreferencesHelper

    @Inject
    lateinit var okHttpClient: OkHttpClient


    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    override fun onCreate() {
        super.onCreate()
        instance = this

        configureFacebook()
        configureStrictMode()

        appScope.launch {
            initValiFi()
            initTuya()
            initSnowplow()
        }
    }

    private fun configureFacebook() {
        FacebookSdk.setAutoInitEnabled(false)
        FacebookSdk.setAdvertiserIDCollectionEnabled(false)
        FacebookSdk.setAutoLogAppEventsEnabled(false)
        FacebookSdk.setCodelessDebugLogEnabled(false)
    }

    private fun configureStrictMode() {
        if (BuildConfig.DEBUG) {
            val strictPolicy = StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
            StrictMode.setThreadPolicy(strictPolicy)
        }
    }

    private suspend fun initValiFi() = withContext(Dispatchers.IO) {
        runCatching {
            ValiFi.install(applicationContext)
        }.onFailure {
            Log.e("OhmApp", "ValiFi init failed", it)
        }
    }

    private suspend fun initTuya() = withContext(Dispatchers.IO) {
        runCatching {
            ThingHomeSdk.init(this@OhmApp)
        }.onFailure {
            Log.e("OhmApp", "Tuya init failed", it)
        }
    }

    private suspend fun initSnowplow() = withContext(Dispatchers.Default) {
        runCatching {
            initSnowplowWithUserId()
        }.onFailure {
            Log.e("OhmApp", "Snowplow init failed", it)
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level>= 80) {
            AppCleaner.cleanup(okHttpClient)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

}
