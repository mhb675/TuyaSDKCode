
package com.app.ohmplug

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.TaskStackBuilder
import com.app.ohmplug.base.Navigator
import com.app.ohmplug.base.network.LiveResponse
import com.app.ohmplug.auth.common.model.AuthRepository
import com.app.ohmplug.auth.common.view.AuthActivity
import com.app.ohmplug.auth.common.view.TermsActivity
import com.app.ohmplug.common.BizBundleFamilyServiceImpl
import com.app.ohmplug.common.PreferencesHelper
import com.app.ohmplug.common.analytics.SnowplowTrackerBuilder
import com.app.ohmplug.dashboard.view.MainActivity
import com.app.ohmplug.splash.view.SplashActivity
import com.mlykotom.valifi.ValiFi
import com.thingclips.smart.api.MicroContext
import com.thingclips.smart.api.router.UrlBuilder
import com.thingclips.smart.api.service.RedirectService
import com.thingclips.smart.bizbundle.initializer.BizBundleInitializer
import com.thingclips.smart.commonbiz.bizbundle.family.api.AbsBizBundleFamilyService
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.optimus.sdk.ThingOptimusSdk
import com.thingclips.smart.thingpackconfig.PackConfig
import com.thingclips.smart.wrapper.api.ThingWrapper
import com.tuya.smart.bizubundle.demo.AppConfig
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
        PackConfig.addValueDelegate(AppConfig::class.java)
        initTuyaNewBizBundle()
        initSnowplowWithUserId()

    }

    private fun initSnowplowWithUserId() {
        val userId = preferencesHelper.getUserId()
        if (userId != null) {
            initSnowPlow(userId)
        } else if (preferencesHelper.getAccessToken() != null) {
            repository.getOhmConnectUserId().observeForever {
                when (it) {
                    is LiveResponse.Error -> {
                        initSnowPlow()
                    }
                    is LiveResponse.Status -> {}
                    is LiveResponse.Success -> {
                        initSnowPlow(it.data?.data?.viewer?.id)
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
    private fun initTuyaNewBizBundle(){
        BizBundleInitializer.init(
            this,
            { errorCode, urlBuilder ->
                Log.e(
                    "Router not implement",
                    urlBuilder.target + " : " + urlBuilder.params.toString()
                )
            }
        ) { serviceName -> Log.e("service not implement", serviceName) }

        BizBundleInitializer.registerService(
            AbsBizBundleFamilyService::class.java, BizBundleFamilyServiceImpl()
        )

        val service = MicroContext.getServiceManager().findServiceByInterface(RedirectService::class.java.name) as? RedirectService

        service?.registerUrlInterceptor(object : RedirectService.UrlInterceptor {
            override fun forUrlBuilder(
                urlBuilder: UrlBuilder,
                interceptorCallback: RedirectService.InterceptorCallback
            ) {
                interceptorCallback.onContinue(urlBuilder)
            }
        })
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
