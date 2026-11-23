package com.taplink.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taplink.app.data.auth.RoomAuthRepository
import com.taplink.app.domain.AppPreferences
import com.taplink.app.domain.TapLinkRepository
import com.taplink.app.domain.ThemeMode
import com.taplink.app.domain.WalletOwner
import com.taplink.app.ui.LocalizedContent
import com.taplink.app.ui.auth.AccountEntryButton
import com.taplink.app.ui.auth.AuthFlow
import com.taplink.app.ui.auth.AuthViewModel
import com.taplink.app.ui.auth.LocalAuthViewModel
import com.taplink.app.ui.settings.AccessibilityScreen
import com.taplink.app.ui.settings.ContactScreen
import com.taplink.app.ui.settings.NotificationSettingsScreen
import com.taplink.app.ui.support.SignLanguageScreen
import com.taplink.app.ui.support.SupportChatScreen
import com.taplink.app.ui.theme.TapLinkTheme

/**
 * Session gate. Signed out and not in the demo wallet, the account screens own the window;
 * otherwise the existing wallet shell renders unchanged.
 */
@Composable
fun TapLinkRoot(
    repository: TapLinkRepository,
    authRepository: RoomAuthRepository,
    platform: PlatformActions = PlatformActions(),
    destination: String? = null
) {
    val authVm: AuthViewModel = viewModel(
        factory = viewModelFactory { initializer { AuthViewModel(authRepository) } }
    )
    val session by authVm.session.collectAsStateWithLifecycle()
    val guest by authVm.guest.collectAsStateWithLifecycle()
    val preferences by repository.preferences.collectAsStateWithLifecycle(initialValue = AppPreferences())

    // The leading account button shows the signed-in name, so the account is loaded as soon as a
    // session exists rather than only when the account screen is opened.
    LaunchedEffect(session) { authVm.refreshAccount() }

    CompositionLocalProvider(LocalAuthViewModel provides authVm) {
        LocalizedContent(preferences.language) {
            if (session != null || guest) {
                // Keyed by owner: a different account gets a fresh wallet shell, so no route,
                // saved scroll position or cached card from the previous wallet is reused.
                val owner = session?.accountId ?: WalletOwner.GUEST
                key(owner) { TapLinkApp(repository, platform, destination, owner) }
            } else {
                // The account screens follow the system theme; the wallet keeps its own preference.
                TapLinkTheme(ThemeMode.SYSTEM) { AuthFlow(authVm) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapLinkApp(
    repository: TapLinkRepository,
    platform: PlatformActions = PlatformActions(),
    destination: String? = null,
    owner: String = WalletOwner.GUEST
) {
    val vm: WalletViewModel = viewModel(
        key = "wallet-$owner",
        factory = viewModelFactory { initializer { WalletViewModel(repository, createSavedStateHandle(), owner) } }
    )
    val route by vm.route.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val feedbackText = localizedMessage(feedback)
    val loadErrorText = localizedMessage(loadError)
    val busy by vm.busy.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val roots = listOf("Home", "Activity", "Explore", "More")
    val root = route in roots
    val holder = rememberSaveableStateHolder()
    // One sentence per screen, set by the visible screen and read by the speech button.
    val voiceSummary = remember { mutableStateOf("") }
    LaunchedEffect(feedbackText) { feedbackText?.let { snackbar.showSnackbar(it); vm.feedback.value = null } }
    LaunchedEffect(destination) { when (destination?.substringBefore('|')) { "messages" -> vm.navigate("Messages"); "dashboard" -> vm.root("Home") } }
    // A new screen must not keep speaking the previous one.
    LaunchedEffect(route) { platform.stopSpeaking?.invoke() }
    LaunchedEffect(prefs.voiceReading) { if (!prefs.voiceReading) platform.stopSpeaking?.invoke() }
    DisposableEffect(route, prefs, platform.dashboardVisibility) {
        platform.dashboardVisibility?.invoke(route == "Home", prefs) { vm.updatePreferences { it.copy(balanceVisible = !it.balanceVisible) } }
        onDispose { platform.dashboardVisibility?.invoke(false, prefs) {} }
    }
    LaunchedEffect(prefs) { platform.preferencesChanged?.invoke(prefs) }
    BackHandler(enabled = route != "Home" || busy) { vm.back() }
    TapLinkTheme(prefs.theme, largeText = prefs.largeText, highContrast = prefs.highContrast) {
        CompositionLocalProvider(LocalVoiceSummary provides voiceSummary) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(if (root) stringResource(R.string.app_name) else routeLabel(route), style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
                // On the four root screens the leading slot is free, so it carries the account the
                // wallet belongs to; every other screen needs it for Back.
                if (root) AccountEntryButton { vm.navigate("Account") }
                else IconButton({ vm.back() }, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.ui_back)) }
            }, actions = {
                // Read-aloud lives in the app bar rather than as a floating button: a FAB covered the
                // balance on the home card and the "frozen card" notice.
                if (prefs.voiceReading) platform.speak?.let { speak ->
                    val fallback = stringResource(R.string.a11y_voice_default)
                    IconButton(onClick = { speak(voiceSummary.value.ifBlank { fallback }) }) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.a11y_voice_action))
                    }
                }
                if (root) IconButton({ vm.navigate("Messages") }, enabled = !busy) {
                    BadgedBox(badge = { val unread = messages.count { !it.isRead }; if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") } }) { Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.label_messages)) }
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
            bottomBar = {
                if (root) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    roots.forEachIndexed { index, destinationName -> NavigationBarItem(selected = route == destinationName, onClick = { vm.root(destinationName) }, enabled = !busy, icon = { Icon(listOf(Icons.Outlined.Home, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.Outlined.Explore, Icons.Outlined.GridView)[index], null) }, label = { Text(routeLabel(destinationName)) }) }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                when {
                    loadErrorText != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Notice(loadErrorText!!, true); PrimaryButton(stringResource(R.string.ui_try_again)) { vm.initialize() } }
                    !ready -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(); Text(stringResource(R.string.ui_getting_your_wallet_ready)) }
                    else -> holder.SaveableStateProvider(route) {
                        when (route) {
                            "Home" -> Dashboard(vm)
                            "Activity" -> HistoryScreen(vm)
                            "Explore" -> ExploreScreen(vm, platform)
                            "More" -> MoreScreen(vm)
                            "Form" -> OperationForm(vm, platform)
                            "Review" -> ReviewScreen(vm)
                            "Receipt" -> ReceiptScreen(vm, platform)
                            "Transaction" -> ReceiptScreen(vm, platform, detail = true)
                            "Transport" -> TransportScreen(vm)
                            "Offers" -> OffersScreen(vm)
                            "Messages" -> MessagesScreen(vm)
                            "Settings" -> SettingsScreen(vm, platform)
                            "Account" -> AccountScreen()
                            "Notifications" -> NotificationSettingsScreen(vm, platform)
                            "Accessibility" -> AccessibilityScreen(vm, platform)
                            "Contact" -> ContactScreen(vm, platform)
                            "Support" -> SupportChatScreen(vm, platform)
                            "SignLanguage" -> SignLanguageScreen(vm, platform)
                            else -> MoreScreen(vm)
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
        }
    }
}
