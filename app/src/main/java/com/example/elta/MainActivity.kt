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
import com.example.elta.ui.theme.ΔeltaTheme
import com.example.elta.data.CustomCurrency
import kotlinx.coroutines.launch

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
            val customCurrencies by viewModel.customCurrencies.collectAsState(initial = emptyList())
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

            // Collect real transaction data from Room via ViewModel
            val transactionList by viewModel.transactions.collectAsState()

            // Pre-computed aggregations from SQLite — no main-thread math
            val netBalance by viewModel.netBalance.collectAsState()
            val totalIncome by viewModel.totalIncome.collectAsState()
            val totalExpense by viewModel.totalExpense.collectAsState()

            val snackbarHostState = remember { SnackbarHostState() }

            CompositionLocalProvider(LocalAppCurrency provides resolvedCurrency) {
                ΔeltaTheme(darkTheme = isDarkTheme) {
                    var currentScreen by rememberSaveable { mutableStateOf("home") }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
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
                                .padding(innerPadding)
                        ) {
                            when (currentScreen) {
                                "home" -> {
                                    DeltaHomeScreen(
                                        transactionList = transactionList,
                                        netBalance = netBalance,
                                        totalIncome = totalIncome,
                                        totalExpense = totalExpense,
                                        customCurrencies = customCurrencies,
                                        onAddTransaction = { amount, category, type ->
                                            viewModel.addTransaction(amount, category, type)
                                        },
                                        onDeleteTransaction = { transaction ->
                                            viewModel.deleteTransaction(transaction)
                                            coroutineScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Transaction deleted",
                                                    actionLabel = "UNDO",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.insertTransaction(transaction)
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
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                settingsManager.toggleTheme()
                                            }
                                        },
                                        onSelectCurrency = { code ->
                                            coroutineScope.launch {
                                                settingsManager.setCurrencyCode(code)
                                            }
                                        }
                                    )
                                }
                                "analytics" -> {
                                    DeltaAnalyticsScreen(
                                        transactionList = transactionList,
                                        onNavigateBack = { currentScreen = "home" }
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