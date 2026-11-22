package com.taplink.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taplink.app.domain.TapLinkRepository
import com.taplink.app.ui.theme.TapLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapLinkApp(repository: TapLinkRepository, platform: PlatformActions = PlatformActions(), destination: String? = null) {
    val vm: WalletViewModel = viewModel(factory = viewModelFactory { initializer { WalletViewModel(repository, createSavedStateHandle()) } })
    val route by vm.route.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val roots = listOf("Home", "Activity", "Explore", "More")
    val root = route in roots
    val holder = rememberSaveableStateHolder()
    LaunchedEffect(feedback) { feedback?.let { snackbar.showSnackbar(it); vm.feedback.value = null } }
    LaunchedEffect(destination) { when (destination?.substringBefore('|')) { "messages" -> vm.navigate("Messages"); "dashboard" -> vm.root("Home") } }
    DisposableEffect(route, prefs, platform.dashboardVisibility) {
        platform.dashboardVisibility?.invoke(route == "Home", prefs) { vm.updatePreferences { it.copy(balanceVisible = !it.balanceVisible) } }
        onDispose { platform.dashboardVisibility?.invoke(false, prefs) {} }
    }
    LaunchedEffect(prefs) { platform.preferencesChanged?.invoke(prefs) }
    BackHandler(enabled = route != "Home" || busy) { vm.back() }
    TapLinkTheme(prefs.theme) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(if (root) "TapLink" else when (route) { "Form" -> "Your transaction"; "Review" -> "Review & confirm"; "Transaction" -> "Activity details"; else -> route }, style = MaterialTheme.typography.titleLarge) }, navigationIcon = { if (!root) IconButton({ vm.back() }, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }, actions = {
                if (root) IconButton({ vm.navigate("Messages") }, enabled = !busy) {
                    BadgedBox(badge = { val unread = messages.count { !it.isRead }; if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") } }) { Icon(Icons.Outlined.NotificationsNone, "Messages") }
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
            bottomBar = {
                if (root) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    roots.forEachIndexed { index, destinationName -> NavigationBarItem(selected = route == destinationName, onClick = { vm.root(destinationName) }, enabled = !busy, icon = { Icon(listOf(Icons.Outlined.Home, Icons.Outlined.ReceiptLong, Icons.Outlined.Explore, Icons.Outlined.GridView)[index], null) }, label = { Text(destinationName) }) }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                when {
                    loadError != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Notice(loadError!!, true); PrimaryButton("Try again") { vm.initialize() } }
                    !ready -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(); Text("Getting your wallet ready…") }
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
                            else -> MoreScreen(vm)
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}
