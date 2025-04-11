package com.app.ohmplug

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.TaskStackBuilder
import com.app.base.Navigator
import com.app.ohmplug.auth.common.model.AuthRepository
import com.app.ohmplug.about.common.view.AboutActivity
import com.app.ohmplug.auth.common.view.AuthActivity
import com.app.ohmplug.auth.common.view.TermsActivity
import com.app.ohmplug.common.BizBundleFamilyServiceImpl
import com.app.base.network.LiveResponse
import com.app.ohmplug.common.PreferencesHelper
import com.app.ohmplug.common.analytics.SnowplowTrackerBuilder
import com.app.ohmplug.home.view.MainActivity
import com.app.ohmplug.networkdiagnosis.view.NetworkDiagnosisActivity
import com.app.ohmplug.account.view.AccountAndSecurityActivity
import com.app.ohmplug.splash.view.SplashActivity
import com.facebook.drawee.backends.pipeline.Fresco
import com.mlykotom.valifi.ValiFi
import com.thingclips.smart.api.MicroContext
import com.thingclips.smart.api.service.RedirectService
import com.thingclips.smart.commonbiz.bizbundle.family.api.AbsBizBundleFamilyService
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.optimus.sdk.ThingOptimusSdk
import com.thingclips.smart.wrapper.api.ThingWrapper
import dagger.hilt.android.HiltAndroidApp
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        ValiFi.install(applicationContext)
        Fresco.initialize(this)
        //ThingHomeSdk.init(this)
        ThingHomeSdk.setDebugMode(BuildConfig.DEBUG)
        initTuyaBizBundle()

    }

    
    private fun initTuyaBizBundle() {
        ThingHomeSdk.init(this)
        ThingWrapper.init(
            this,
            { errorCode, urlBuilder ->
                Log.e(
                    "router not implement",
                    urlBuilder.target + " : " + urlBuilder.params.toString()
                )
            }
        ) { serviceName -> Log.e("service not implement", serviceName) }
        ThingOptimusSdk.init(this)
        ThingWrapper.registerService<AbsBizBundleFamilyService, AbsBizBundleFamilyService>(
            AbsBizBundleFamilyService::class.java, BizBundleFamilyServiceImpl()
        )

        val service = MicroContext.getServiceManager().findServiceByInterface<RedirectService>(
            RedirectService::class.java.name
        )
        service.registerUrlInterceptor { urlBuilder, interceptorCallback ->
            //Such as:
            //Intercept the event of clicking the panel right menu and jump to the custom page with the parameters of urlBuilder
            //例如：拦截点击面板右上角按钮事件，通过 urlBuilder 的参数跳转至自定义页面
            // if (urlBuilder.target.equals("panelAction") && urlBuilder.params.getString("action").equals("gotoPanelMore")) {
            //     interceptorCallback.interceptor("interceptor");
            //     Log.e("interceptor", urlBuilder.params.toString());
            // } else {
            interceptorCallback.onContinue(urlBuilder)
            // }
        }
    }


    override fun onTerminate() {
        super.onTerminate()
        ThingHomeSdk.onDestroy()
    }
}