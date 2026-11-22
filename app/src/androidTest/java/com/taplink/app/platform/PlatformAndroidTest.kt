package com.taplink.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.taplink.app.R
import com.taplink.app.domain.AppPreferences
import com.taplink.app.domain.Card
import com.taplink.app.domain.LocationType
import com.taplink.app.domain.ServiceLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val card = Card("test", "Daily", "•••• 1234", 45678, false, 0, "#000000")

    @Test fun widgetRemoteViewsInflateAndHideBalanceByDefault() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val views = TapLinkWidgetProvider.remoteViews(context, WidgetContent.from(card, AppPreferences()))
            val view = views.apply(context, FrameLayout(context))
            assertEquals("Balance hidden", view.findViewById<TextView>(R.id.widget_balance).text.toString())
            assertEquals("•••• 1234", view.findViewById<TextView>(R.id.widget_card_number).text.toString())
        }
    }

    @Test fun widgetReapplyRemovesPreviouslyVisibleBalance() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val visible = TapLinkWidgetProvider.remoteViews(context, WidgetContent.from(card, AppPreferences(widgetBalanceVisible = true)))
            val view = visible.apply(context, FrameLayout(context))
            assertEquals("HK$456.78", view.findViewById<TextView>(R.id.widget_balance).text.toString())
            TapLinkWidgetProvider.remoteViews(context, WidgetContent.from(card, AppPreferences())).reapply(context, view)
            assertEquals("Balance hidden", view.findViewById<TextView>(R.id.widget_balance).text.toString())
        }
    }

    @Test fun deniedLocationUsesManualDistrictWithoutRequestingProvider() = runBlocking {
        val denied = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_DENIED
        }
        val result = CoarseLocationHelper.locate(denied, "  Sha Tin  ")
        assertTrue(result is LocationResult.Manual)
        assertEquals("Sha Tin", (result as LocationResult.Manual).district)
    }

    @Test fun mapIntentEncodesLabelAndMissingHandlerReturnsFalse() {
        var launched: Intent? = null
        val capture = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        val location = ServiceLocation("x", "Demo & station #1", LocationType.TOP_UP, "Sai Kung", "Demo", 22.33, 114.26, "Demo", "Demo")
        assertTrue(ExternalIntents.openMap(capture, location))
        assertEquals("geo", launched?.data?.scheme)
        assertTrue(launched?.data.toString().contains("%26"))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, launched!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        val unavailable = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        assertFalse(ExternalIntents.openMap(unavailable, location))
    }

    @Test fun shareOpensChooserWithPlainText() {
        var launched: Intent? = null
        val capture = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        assertTrue(ExternalIntents.shareText(capture, "TapLink simulated receipt"))
        assertEquals(Intent.ACTION_CHOOSER, launched?.action)
        assertFalse(ExternalIntents.shareText(capture, "  "))
    }
}
