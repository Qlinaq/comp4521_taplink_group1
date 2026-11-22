# TapLink Android integrations

All APIs below live in `com.taplink.app.platform`. They use the repository in `API_CONTRACT.md`, existing AndroidX Core/Lifecycle/WorkManager, and no API keys or network service. Platform code never calls `pay`, `topUp`, or any other financial mutation. QR/camera scanning is deliberately omitted.

## Startup and repository changes

```kotlin
// MainActivity.onCreate, or Application.onCreate. Idempotent, keeps application context only.
TapLinkPlatform.start(this)

// MainActivity.onResume and after a notification permission result.
TapLinkPlatform.onPermissionsChanged(this)
```

The coordinator observes cards and preferences to refresh installed widgets, observes reminder preferences to enqueue/cancel unique persistent jobs, and observes messages to post permitted notifications. It is process-scoped, not tied to a Compose recomposition. No custom Application subclass is required. An explicit alternative after a committed mutation is `TapLinkWidgetProvider.refresh(context)` from a coroutine. `PlatformWorkScheduler.refreshWidget(context)` enqueues durable refresh without a coroutine. Neither commits a wallet operation.

Use repository preferences as the source of truth. Widget visibility (`widgetBalanceVisible`, default false) is independent of screen visibility (`balanceVisible`). All widget instances show the selected card, at most the last four identifier characters, frozen/demo status, and either the opted-in balance or “Balance hidden”. The layout and preview are private by default.

```kotlin
val requested = TapLinkWidgetProvider.requestPin(activity)
// false: explain how to add TapLink using the launcher's Widgets menu.
// true: launcher accepted the request, NOT proof the user confirmed placement.
```

## Notification permission and background work

```kotlin
val permission: String? = TapLinkNotifications.permissionToRequest() // null below Android 13
val allowed: Boolean = TapLinkNotifications.canPost(context) // permission + app + channel
// Use an ActivityResult RequestPermission launcher after the user opts in.
// On result, call TapLinkPlatform.onPermissionsChanged(context).
```

`TapLinkNotifications.createChannel(context)` is idempotent. The coordinator and publisher call it. Channel ID is `taplink_reminders_v1`. Lock-screen public content is generic; full message content has PRIVATE visibility.

WorkManager 2.10.0 runs reminders approximately every six hours and local demo-content refresh approximately every 24 hours. Work persists across process death/reboot with the default WorkManager initializer. Android may defer execution; no exact alarms or foreground service are used. Unique names and UPDATE avoid duplicate periodic jobs. Each worker reads current persisted preferences before work; reminders read them again before posting. Disabled jobs are cancelled, and refresh does not require connectivity because repository refresh is local.

The reminder master preference gates OS notifications; each type additionally respects `lowBalanceAlerts` or `expiryRemindersEnabled`. Informational/read/future/older-than-24-hour messages are not posted. Newest-first batches are capped at five. Message IDs persist in private SharedPreferences only after a permitted post, and a process mutex serializes worker/foreground delivery. Stable notification tags and only-alert-once reduce duplicate alerts after a process crash. This is best-effort deduplication: Android posting and SharedPreferences cannot form an atomic transaction. Permission denial does not consume IDs. Already shown alerts are cancelled when read, stale, or disabled on the next reconciliation. Repository generation independently deduplicates reminder content.

## Navigation from widgets and notifications

Both use explicit immutable PendingIntents to `com.taplink.app.MainActivity`, with no payment action:

```kotlin
val destination = intent.getStringExtra(TapLinkNavigation.EXTRA_DESTINATION)
// TapLinkNavigation.DASHBOARD == "dashboard"
// TapLinkNavigation.MESSAGES == "messages"
```

Handle the extra on initial launch and `onNewIntent`; call `setIntent(intent)` as appropriate and update Compose navigation state. Normal launcher starts need no extra. The dashboard shortcut is the widget's root and its “Open dashboard” button.

## Shake: balance visibility only

```kotlin
val shake = LifecycleShakeObserver(activity, lifecycleOwner) { toggleBalanceVisibility() }
shake.setEnabled(dashboardVisible && preferences.shakeToToggleEnabled)
val supported = shake.isAvailable
// close from activity destruction or DisposableEffect.onDispose
shake.close()
```

Create/use/close on the main thread. The helper automatically registers only while RESUMED, unregisters on pause/destruction, and resets partial gestures. No sensor is an ordinary unsupported capability. Two separate >2.7g peaks within 80–600ms, separated by a quiet sample, count as one gesture; a 1.5-second debounce prevents repeated toggles. The callback must only toggle balance visibility and should use the latest UI state/callback. Do not bind payment to it. `PlatformActions.dashboardVisibility` can update a retained callback plus `setEnabled` as screens change.

## Approximate location and manual district

```kotlin
CoarseLocationHelper.PERMISSION // android.permission.ACCESS_COARSE_LOCATION
CoarseLocationHelper.hasPermission(context)
// Run in lifecycleScope or another caller-owned foreground coroutine; cancel previous requests.
when (val result = CoarseLocationHelper.locate(context, preferences.manualDistrict)) {
    is LocationResult.Approximate -> showNearby(result.latitude, result.longitude)
    is LocationResult.Manual -> showDistrict(result.district, result.reason)
}
```

The suspend function does not request permission itself. Request it from the UI after a user action; denial still yields a useful Manual result. It uses AndroidX's asynchronous network-provider current-location API with a ten-second timeout and a CancellationSignal connected to coroutine cancellation. It supports API 29, returns no stale cached fallback, and never requests fine/background location. Disabled services, missing providers, denial, or no fix return the selected district and an English reason. Caller cancellation propagates. Output is rounded to 0.01 degrees with an accuracy floor of 1km, even if fine permission was granted previously. No geocoder/network API or district inference is needed. Nearby repository locations remain explicitly demo data; do not claim live availability or routes.

For `PlatformActions.requestLocation`, map Approximate to `(latitude, longitude, "Approximate device location; demo locations")` and Manual to `(null, null, reason)`, retaining the selected district in UI preferences.

## Safe maps and sharing

```kotlin
ExternalIntents.openMap(context, serviceLocation): Boolean
ExternalIntents.shareReceipt(context, receipt): Boolean
ExternalIntents.shareText(context, text): Boolean
```

Maps use an encoded `geo:` point and demo label. Invalid coordinates and missing/blocked handlers return false so UI can show a message. No package queries, map keys, or embedded map SDK are needed. Share uses `ACTION_SEND` text/plain and the system chooser; the user chooses the recipient. Receipt text comes from `Receipt.shareText()` and is already labelled simulated. UI owns review before handing content to the chooser.

## Build and manifest owner snippets

These have been sent to the build/manifest owner; this agent does not edit shared build files or manifest.

```kotlin
implementation("androidx.work:work-runtime-ktx:2.10.0")
// Already present: core-ktx 1.15.0, lifecycle-runtime-ktx 2.8.7,
// kotlinx-coroutines-android 1.9.0, JUnit 4.13.2, AndroidX test runner/ext-junit.
```

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-feature android:name="android.hardware.sensor.accelerometer" android:required="false" />
<uses-feature android:name="android.hardware.location.network" android:required="false" />
<!-- Inside application: -->
<receiver android:name=".platform.TapLinkWidgetProvider"
    android:exported="false" android:label="TapLink demo card">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
        android:resource="@xml/taplink_widget_info" />
</receiver>
```

Keep the default WorkManager AndroidX Startup initializer. No custom worker factory, boot receiver, camera, Internet, fine/background location, or exact-alarm permission is required by this platform package. Widget metadata also requests a 30-minute Android refresh as a process-death fallback.

## Validation

Owned JVM tests are `PlatformPoliciesTest` and `ShakeDetectorTest`; Android instrumentation tests are `PlatformAndroidTest`. They cover privacy defaults/reapply, masking, notification preference/age/dedup/batch policy, shake impulses/debounce/reset, coordinate validation, actual RemoteViews inflation, denied-location fallback, and safe map/share intent construction. Run with JDK 21:

```text
gradlew.bat :app:testDebugUnitTest --tests "com.taplink.app.platform.*"
gradlew.bat :app:compileDebugAndroidTestKotlin
gradlew.bat :app:connectedDebugAndroidTest
```

The last command requires an Android device/emulator. Compilation alone is not proof of physical sensor, launcher, notification-permission or reboot behavior. Device checks: add/pin widget; select/rename/top-up a card; opt in/out of widget balance; deny/grant notifications; disable reminder types; background/reopen; shake only on dashboard; deny/disable/cancel location; open a demo point with/without a map handler.

Official references: [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work), [widget updates and broadcast limits](https://developer.android.com/develop/ui/views/appwidgets/advanced), [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission), [cancellable current location](https://developer.android.com/reference/androidx/core/location/LocationManagerCompat).
