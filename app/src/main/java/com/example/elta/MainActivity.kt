package com.example.elta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.elta.ui.theme.LocalAppCurrency
import com.example.elta.ui.theme.DeltaTheme
import com.example.elta.data.CustomCurrency
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.EaseInQuart
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

class MainActivity : ComponentActivity() {
    private val settingsManager by lazy { SettingsManager(applicationContext) }
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme by settingsManager.isDarkTheme.collectAsState(initial = true)

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }
            val currentCurrencyCode by settingsManager.currencyCode.collectAsState(initial = "EUR")
            val customCurrencies by viewModel.customCurrencies.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            val resolvedCurrency = remember(currentCurrencyCode, customCurrencies) {
                val custom = customCurrencies.firstOrNull { it.code.uppercase() == currentCurrencyCode.uppercase() }
                if (custom != null) {
                    CurrencyInfo(code = custom.code, symbol = custom.symbol, isCustom = true, id = custom.id)
                } else {
                    val defaultEnum = AppCurrency.entries.firstOrNull { it.code == currentCurrencyCode } ?: AppCurrency.EUR
                    CurrencyInfo(code = defaultEnum.code, symbol = defaultEnum.symbol, isCustom = false)
                }
            }

            val transactionList by viewModel.transactions.collectAsState()
            val deletedTransactions by viewModel.deletedTransactions.collectAsState()
            val netBalance by viewModel.netBalance.collectAsState()
            val totalIncome by viewModel.totalIncome.collectAsState()
            val totalExpense by viewModel.totalExpense.collectAsState()
            val syncStatus by viewModel.syncStatus.collectAsState()
            val authUid by viewModel.authStatus.collectAsState()
            val errorMessage by viewModel.errorMessage.collectAsState()
            val savedUsername by settingsManager.username.collectAsState(initial = null)
            val isSyncPaused by settingsManager.isSyncPaused.collectAsState(initial = false)
            val savedSyncUid by settingsManager.syncUid.collectAsState(initial = null)

            // Keep viewModel's username in sync with DataStore
            LaunchedEffect(savedUsername) { viewModel.updateLocalUsername(savedUsername) }

            // Keep viewModel's syncUidOverride in sync with DataStore
            LaunchedEffect(savedSyncUid) {
                viewModel.syncManager.setSyncUidOverride(savedSyncUid)
            }

            val snackbarHostState = remember { SnackbarHostState() }

            CompositionLocalProvider(LocalAppCurrency provides resolvedCurrency) {
                DeltaTheme(darkTheme = isDarkTheme) {
                    var currentScreen by rememberSaveable { mutableStateOf("home") }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.padding(bottom = 90.dp)
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = {
                                    val enterTransition = if (targetState == "home") {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                            animationSpec = tween(350, easing = EaseOutQuart),
                                            initialOffset = { it / 5 }
                                        ) + fadeIn(animationSpec = tween(250))
                                    } else {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                            animationSpec = tween(350, easing = EaseOutQuart)
                                        ) + fadeIn(animationSpec = tween(250))
                                    }

                                    val exitTransition = if (targetState == "home") {
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                            animationSpec = tween(350, easing = EaseOutQuart)
                                        ) + fadeOut(animationSpec = tween(200))
                                    } else {
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                            animationSpec = tween(350, easing = EaseOutQuart),
                                            targetOffset = { -it / 5 }
                                        ) + fadeOut(animationSpec = tween(200))
                                    }

                                    enterTransition togetherWith exitTransition
                                },
                                label = "ScreenNavigation"
                            ) { targetScreen ->
                                when (targetScreen) {
                                    "home" -> {
                                        DeltaHomeScreen(
                                            transactionList = transactionList,
                                            netBalance = netBalance,
                                            totalIncome = totalIncome,
                                            totalExpense = totalExpense,
                                            customCurrencies = customCurrencies,
                                            syncStatus = syncStatus,
                                            isSyncPaused = isSyncPaused,
                                            onAddTransaction = { amount, category, type ->
                                                viewModel.addTransaction(amount, category, type)
                                            },
                                            onDeleteTransaction = { transaction ->
                                                viewModel.deleteTransaction(transaction)
                                                coroutineScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "Moved to trash",
                                                        actionLabel = "UNDO",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        viewModel.restoreTransaction(transaction.uuid)
                                                    }
                                                }
                                            },
                                            onUpdateTransaction = { transaction ->
                                                viewModel.updateTransaction(transaction)
                                            },
                                            onAddCustomCurrency = { code, symbol ->
                                                viewModel.insertCustomCurrency(CustomCurrency(code = code, symbol = symbol))
                                            },
                                            onUpdateCustomCurrency = { custom ->
                                                viewModel.insertCustomCurrency(custom)
                                            },
                                            onDeleteCustomCurrency = { custom ->
                                                if (currentCurrencyCode.uppercase() == custom.code.uppercase()) {
                                                    coroutineScope.launch {
                                                        settingsManager.setCurrencyCode("EUR")
                                                    }
                                                }
                                                viewModel.deleteCustomCurrency(custom)
                                            },
                                            onNavigateToAnalytics = { currentScreen = "analytics" },
                                            onNavigateToTrash = { currentScreen = "trash" },
                                            onNavigateToProfile = { currentScreen = "profile" },
                                            onToggleTheme = {
                                                coroutineScope.launch { settingsManager.toggleTheme() }
                                            },
                                            onSelectCurrency = { code ->
                                                coroutineScope.launch { settingsManager.setCurrencyCode(code) }
                                            },
                                            onSync = {
                                                if (!isSyncPaused) viewModel.sync()
                                            }
                                        )
                                    }
                                    "analytics" -> {
                                        DeltaAnalyticsScreen(
                                            transactionList = transactionList,
                                            syncStatus = syncStatus,
                                            isSyncPaused = isSyncPaused,
                                            onSync = { viewModel.sync() },
                                            onNavigateBack = { currentScreen = "home" }
                                        )
                                    }
                                    "trash" -> {
                                        DeltaTrashScreen(
                                            deletedTransactions = deletedTransactions,
                                            syncStatus = syncStatus,
                                            isSyncPaused = isSyncPaused,
                                            onRestore = { uuids -> viewModel.restoreTransactions(uuids) },
                                            onDeletePermanently = { uuids -> viewModel.deleteTransactionsPermanently(uuids) },
                                            onClearAll = {
                                                viewModel.clearTrash(deletedTransactions.map { it.uuid })
                                            },
                                            onSync = { viewModel.sync() },
                                            onNavigateBack = { currentScreen = "home" }
                                        )
                                    }
                                    "profile" -> {
                                         DeltaProfileScreen(
                                             authUid = authUid,
                                             username = savedUsername,
                                             transactionCount = transactionList.size,
                                             deletedCount = deletedTransactions.size,
                                             syncStatus = syncStatus,
                                             isSyncPaused = isSyncPaused,
                                             isCustomPassportActive = savedSyncUid != null,
                                             onSetUsername = { name ->
                                                 coroutineScope.launch {
                                                     settingsManager.setUsername(name)
                                                     viewModel.sync()
                                                 }
                                             },
                                             onLoadFromUid = { uid ->
                                                 viewModel.loadFromUid(uid)
                                             },
                                             onDisconnect = {
                                                 coroutineScope.launch {
                                                     viewModel.syncManager.setSyncUidOverride(null)
                                                     settingsManager.setSyncUid(null)
                                                     settingsManager.setUsername(null)
                                                     viewModel.clearLocalData()
                                                     viewModel.sync()
                                                 }
                                             },
                                             onToggleSyncPause = {
                                                 coroutineScope.launch {
                                                     settingsManager.setSyncPaused(!isSyncPaused)
                                                 }
                                             },
                                             onClearAllLocalData = { viewModel.clearAllLocalData() },
                                             onSync = { viewModel.sync() },
                                             onNavigateBack = { currentScreen = "home" },
                                             errorMessage = errorMessage,
                                             onClearError = { viewModel.clearError() }
                                         )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}