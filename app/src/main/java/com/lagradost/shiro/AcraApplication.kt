package com.lagradost.shiro

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import com.google.auto.service.AutoService
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.utils.mvvm.logError
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.config.toast
import org.acra.data.CrashReportData
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderFactory
import kotlin.concurrent.thread

class CustomReportSender : ReportSender {
    // Sends all your crashes to google forms
    override fun send(context: Context, errorContent: CrashReportData) {
        try {
            println("Report sent")
            val url =
                "https://docs.google.com/forms/u/0/d/e/1FAIpQLSf8U6zVn4YPGhbCQXUBNH4k5wlYC2KmmGuUZz4O6TL2o62cAw/formResponse"
            val data = mapOf(
                "entry.1083318133" to errorContent.toJSON()
            )
            thread {
                khttp.post(url, data = data)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }
}

@AutoService(ReportSenderFactory::class)
class CustomSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender {
        return CustomReportSender()
    }

    override fun enabled(config: CoreConfiguration): Boolean {
        return true
    }
}

class AcraApplication : MultiDexApplication() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            initAcra {
                //core configuration:
                buildConfigClass = BuildConfig::class.java
                reportFormat = StringFormat.JSON
                reportContent = arrayOf(
                    ReportField.BUILD_CONFIG, ReportField.USER_CRASH_DATE,
                    ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL,
                    ReportField.STACK_TRACE, ReportField.LOGCAT
                )

                //each plugin you chose above can be configured in a block like this:
                toast {
                    text = getString(R.string.acra_report_toast)
                    //opening this block automatically enables the plugin.
                }

            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Cyanea.init(this, resources)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }
}