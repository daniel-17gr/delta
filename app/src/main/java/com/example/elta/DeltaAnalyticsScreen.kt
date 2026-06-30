package com.example.elta
import com.example.elta.ui.theme.LocalDeltaColors
import com.example.elta.ui.theme.LocalAppCurrency
import androidx.activity.compose.BackHandler




import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elta.R
import com.example.elta.data.Transaction
import com.example.elta.data.TransactionType
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Load Custom Emoji Font
val EmojiFont = FontFamily(
    Font(R.font.emoji, FontWeight.Normal)
)

@Composable
fun DeltaAnalyticsScreen(
    transactionList: List<Transaction>,
    onNavigateBack: () -> Unit
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    val haptic = LocalHapticFeedback.current
    var selectedRange by remember { mutableStateOf("30D") }

    // State for tapping a category to view its trend details
    var selectedTrendCategory by remember { mutableStateOf<String?>(null) }

    // State for focused/clicked overview card to show details dialog
    var selectedOverviewDetail by remember { mutableStateOf<String?>(null) }

    // State for Cash Flow Graph type: FLOW (Income/Expenses) or TREND (Net progression)
    var graphType by remember { mutableStateOf("FLOW") }

    // State for selected point on the Cash Flow graph
    var selectedGraphPointIndex by remember { mutableStateOf<Int?>(null) }

    // Heatmap mode selector (EXPENSES vs INCOME)
    var heatmapMode by remember { mutableStateOf("EXPENSES") }

    // Export summary feedback notification toast overlay
    var showExportToast by remember { mutableStateOf(false) }

    // Handle system back navigation (back key/gesture)
    BackHandler(enabled = true) {
        if (selectedTrendCategory != null) {
            selectedTrendCategory = null
        } else if (selectedOverviewDetail != null) {
            selectedOverviewDetail = null
        } else {
            onNavigateBack()
        }
    }

    // Filter real transactions by the selected time range using the Room timestamp field
    val analyticsTransactions = remember(transactionList, selectedRange) {
        val now = System.currentTimeMillis()
        val cutoff: Long = when (selectedRange) {
            "7D"  -> now - 7L  * 24 * 60 * 60 * 1000
            "30D" -> now - 30L * 24 * 60 * 60 * 1000
            "3M"  -> now - 90L * 24 * 60 * 60 * 1000
            "6M"  -> now - 180L * 24 * 60 * 60 * 1000
            "1Y"  -> now - 365L * 24 * 60 * 60 * 1000
            else  -> 0L // ALL
        }
        transactionList.filter { it.timestamp >= cutoff }
    }

    // Dynamic computations wrapped in remember — only recomputed when the filtered list changes
    data class AnalyticsSummary(
        val netBalance: Double,
        val totalIncome: Double,
        val totalExpense: Double,
        val transactionsCount: Int,
        val totalBorrowed: Double,
        val totalLent: Double,
        val debtDifference: Double,
        val savings: Double,
        val incomeCount: Int,
        val expenseCount: Int,
        val borrowCount: Int,
        val lentCount: Int
    )

    val summary = remember(analyticsTransactions) {
        val netBalance = analyticsTransactions.sumOf { it.amount }
        val totalIncome = analyticsTransactions.filter { it.type == TransactionType.INCOME || it.type == TransactionType.BORROWED }.sumOf { it.amount }
        val totalExpense = analyticsTransactions.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT }.sumOf { abs(it.amount) }
        val totalBorrowed = analyticsTransactions.filter { it.type == TransactionType.BORROWED }.sumOf { it.amount }
        val totalLent = analyticsTransactions.filter { it.type == TransactionType.LENT }.sumOf { abs(it.amount) }
        AnalyticsSummary(
            netBalance = netBalance,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            transactionsCount = analyticsTransactions.size,
            totalBorrowed = totalBorrowed,
            totalLent = totalLent,
            debtDifference = totalBorrowed - totalLent,
            savings = totalIncome - totalExpense,
            incomeCount = analyticsTransactions.count { it.type == TransactionType.INCOME },
            expenseCount = analyticsTransactions.count { it.type == TransactionType.EXPENSE },
            borrowCount = analyticsTransactions.count { it.type == TransactionType.BORROWED },
            lentCount = analyticsTransactions.count { it.type == TransactionType.LENT }
        )
    }

    val netBalance = summary.netBalance
    val totalIncome = summary.totalIncome
    val totalExpense = summary.totalExpense
    val transactionsCount = summary.transactionsCount
    val totalBorrowed = summary.totalBorrowed
    val totalLent = summary.totalLent
    val debtDifference = summary.debtDifference
    val savings = summary.savings
    val incomeCount = summary.incomeCount
    val expenseCount = summary.expenseCount
    val borrowCount = summary.borrowCount
    val lentCount = summary.lentCount

    val context = androidx.compose.ui.platform.LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val success = writeTransactionsToUri(context, uri, transactionList)
                if (success) {
                    showExportToast = true
                }
            }
        }
    }

    val rangeDays = remember(analyticsTransactions, selectedRange) {
        when (selectedRange) {
            "7D"  -> 7
            "30D" -> 30
            "3M"  -> 90
            "6M"  -> 180
            "1Y"  -> 365
            else  -> {
                if (analyticsTransactions.isNotEmpty()) {
                    val oldest = analyticsTransactions.minOf { it.timestamp }
                    val diffMs = System.currentTimeMillis() - oldest
                    val days = (diffMs / (24L * 60 * 60 * 1000)).toInt()
                    days.coerceAtLeast(1)
                } else {
                    1
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val isLandscape = maxWidth > maxHeight

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.buttonBackground)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigateBack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Use resource drawable for back arrow, centered perfectly
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = "Analytics",
                        fontFamily = NothingGlyph,
                        fontSize = 24.sp,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                }

                // Export summary action icon button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            exportLauncher.launch("Delta_Summary.csv")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.export),
                        contentDescription = "Export",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Divider
            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))

            // Scrollable content
            if (isLandscape) {
                // Tablet/Landscape mode: two column dashboard
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TimeRangeSelector(
                            selectedRange = selectedRange,
                            onRangeSelected = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedRange = it
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp)
                        ) {
                            OverviewCardsScrollableRow(
                                net = netBalance,
                                income = totalIncome,
                                expense = totalExpense,
                                savings = savings,
                                borrowed = totalBorrowed,
                                lent = totalLent,
                                txCount = transactionsCount,
                                focusedCard = selectedOverviewDetail ?: "",
                                onCardClick = { selectedOverviewDetail = it },
                                transactions = analyticsTransactions
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                CashFlowCard(
                                    transactions = analyticsTransactions,
                                    selectedRange = selectedRange,
                                    graphType = graphType,
                                    selectedPointIndex = selectedGraphPointIndex,
                                    onTypeToggle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        graphType = it
                                        selectedGraphPointIndex = null
                                    },
                                    onPointClick = { idx ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedGraphPointIndex = if (selectedGraphPointIndex == idx) null else idx
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                SpendingByCategoryCard(
                                    transactions = analyticsTransactions,
                                    onCategoryClick = { category ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedTrendCategory = category
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(120.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            DailyAveragesCard(totalExpense = totalExpense, totalIncome = totalIncome, txCount = transactionsCount, rangeDays = rangeDays)
                            Spacer(modifier = Modifier.height(16.dp))
                            SummaryRowCards(
                                totalBorrowed = totalBorrowed,
                                totalLent = totalLent,
                                debtDiff = debtDifference,
                                incCount = incomeCount,
                                expCount = expenseCount,
                                borCount = borrowCount,
                                lenCount = lentCount,
                                totCount = transactionsCount
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SpendingCalendarCard(
                                transactions = transactionList,
                                heatmapMode = heatmapMode,
                                onModeToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    heatmapMode = it
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SpendingWeekdayCard(transactions = analyticsTransactions)
                            Spacer(modifier = Modifier.height(16.dp))
                            FinancialRatiosCard(savings = savings, income = totalIncome, expense = totalExpense, borrowed = totalBorrowed)
                            Spacer(modifier = Modifier.height(16.dp))
                            RecordsCard(transactions = transactionList)
                            Spacer(modifier = Modifier.height(16.dp))
                            InsightsCard(transactions = transactionList)
                            AnalyticsFooter()
                        }
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }
            } else {
                // Portrait Layout
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TimeRangeSelector(
                        selectedRange = selectedRange,
                        onRangeSelected = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedRange = it
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp)
                    ) {
                        OverviewCardsScrollableRow(
                            net = netBalance,
                            income = totalIncome,
                            expense = totalExpense,
                            savings = savings,
                            borrowed = totalBorrowed,
                            lent = totalLent,
                            txCount = transactionsCount,
                            focusedCard = selectedOverviewDetail ?: "",
                            onCardClick = { selectedOverviewDetail = it },
                            transactions = analyticsTransactions
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Full-width cards are aligned with horizontal padding
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            CashFlowCard(
                                transactions = analyticsTransactions,
                                selectedRange = selectedRange,
                                graphType = graphType,
                                selectedPointIndex = selectedGraphPointIndex,
                                onTypeToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    graphType = it
                                    selectedGraphPointIndex = null
                                },
                                onPointClick = { idx ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedGraphPointIndex = if (selectedGraphPointIndex == idx) null else idx
                                }
                            )
                            SpendingByCategoryCard(
                                transactions = analyticsTransactions,
                                onCategoryClick = { category ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedTrendCategory = category
                                }
                            )
                            DailyAveragesCard(totalExpense = totalExpense, totalIncome = totalIncome, txCount = transactionsCount, rangeDays = rangeDays)
                            SummaryRowCards(
                                totalBorrowed = totalBorrowed,
                                totalLent = totalLent,
                                debtDiff = debtDifference,
                                incCount = incomeCount,
                                expCount = expenseCount,
                                borCount = borrowCount,
                                lenCount = lentCount,
                                totCount = transactionsCount
                            )
                            SpendingCalendarCard(
                                transactions = transactionList,
                                heatmapMode = heatmapMode,
                                onModeToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    heatmapMode = it
                                }
                            )
                            SpendingWeekdayCard(transactions = analyticsTransactions)
                            FinancialRatiosCard(savings = savings, income = totalIncome, expense = totalExpense, borrowed = totalBorrowed)
                            RecordsCard(transactions = transactionList)
                            InsightsCard(transactions = transactionList)
                            AnalyticsFooter()
                        }
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }
            }
        }

        // Overview metric detail dialog overlay
        AnimatedVisibility(
            visible = selectedOverviewDetail != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val title = selectedOverviewDetail ?: ""
            val desc = when (title) {
                "NET" -> "Total incoming flows minus outgoing flows for the selected period. Standard measure of net cash generation."
                "INCOME" -> "Sum of all positive revenue flows logged in the app (includes wages, gifts, business gains, and loans)."
                "EXPENSES" -> "Sum of all outgoing transaction flows logged (includes grocery, shopping, utilities, rent, and money lent)."
                "SAVINGS" -> "Income minus Expenses. High savings indicate strong financial discipline."
                "BORROWED" -> "Total sum currently borrowed from banks or peers. Logged as positive incoming debt flow."
                "LENT" -> "Total sum currently lent out to friends or peers. Logged as outgoing debt flow."
                else -> "Total count of transactions recorded in Delta database within the selected time window."
            }
            val cardVal = when (title) {
                "NET" -> String.format("${currentCurrency.symbol}%,.2f", netBalance)
                "INCOME" -> String.format("${currentCurrency.symbol}%,.2f", totalIncome)
                "EXPENSES" -> String.format("${currentCurrency.symbol}%,.2f", totalExpense)
                "SAVINGS" -> String.format("${currentCurrency.symbol}%,.2f", savings)
                "BORROWED" -> String.format("${currentCurrency.symbol}%,.2f", totalBorrowed)
                "LENT" -> String.format("${currentCurrency.symbol}%,.2f", totalLent)
                else -> transactionsCount.toString()
            }

            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(280.dp)
                ) {
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = title,
                        fontFamily = NothingGlyph,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = cardVal,
                        fontFamily = NothingGlyph,
                        fontSize = 28.sp,
                        color = when (title) {
                            "INCOME", "SAVINGS" -> colors.positive
                            "EXPENSES" -> colors.negative
                            else -> colors.textPrimary
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = desc,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.textPrimary)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedOverviewDetail = null
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(text = "Close", color = colors.background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Category trend details dialog overlay
        AnimatedVisibility(
            visible = selectedTrendCategory != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val cat = selectedTrendCategory ?: ""
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = "$cat Trend",
                        fontFamily = NothingGlyph,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Show last 5 weeks of spending in this category from the full transaction list
                    val currentCurrencySymbol2 = LocalAppCurrency.current.symbol
                    val catWeeks = remember(cat, transactionList) {
                        val now = System.currentTimeMillis()
                        (0 until 5).map { w ->
                            val weekStart = now - (w + 1) * 7L * 86_400_000
                            val weekEnd   = now - w       * 7L * 86_400_000
                            val label = if (w == 0) "This week" else "${w}w ago"
                            val total = transactionList
                                .filter { it.category == cat && it.timestamp in weekStart..weekEnd }
                                .sumOf { abs(it.amount) }
                            label to total
                        }.reversed()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        catWeeks.forEach { (label, total) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = label, color = colors.textSecondary, fontSize = 13.sp)
                                Text(
                        fontWeight = FontWeight.Bold,
                                    text = if (total == 0.0) "—" else String.format("$currentCurrencySymbol2%,.2f", total),
                                    fontFamily = NothingGlyph,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.textPrimary)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTrendCategory = null
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(text = "Close", color = colors.background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Export summary feedback notification toast overlay
        AnimatedVisibility(
            visible = showExportToast,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        ) {
            LaunchedEffect(showExportToast) {
                if (showExportToast) {
                    kotlinx.coroutines.delay(2000)
                    showExportToast = false
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.textPrimary)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Summary exported to Delta_Summary.csv",
                    color = colors.background,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TimeRangeSelector(
    selectedRange: String,
    onRangeSelected: (String) -> Unit
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    // Bleed scrollable row to screen edges using contentPadding instead of trimming parent layout
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val ranges = listOf("7D", "30D", "3M", "6M", "1Y", "ALL")
        items(ranges) { range ->
            val isSelected = selectedRange == range
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) colors.textPrimary else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) colors.textPrimary else colors.border,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onRangeSelected(range) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                        fontWeight = FontWeight.Bold,
                    text = range,
                    fontSize = 11.sp,
                    fontFamily = NothingGlyph,
                    color = if (isSelected) colors.background else colors.textSecondary
                )
            }
        }
    }
}

// Helper: bucket a list of transactions into N equal time slots and return normalised dot-heights (1..6)
private fun sparkline(
    transactions: List<Transaction>,
    buckets: Int = 11,
    selector: (Transaction) -> Double
): List<Int> {
    if (transactions.isEmpty()) return List(buckets) { 1 }
    val now = System.currentTimeMillis()
    val oldest = transactions.minOf { it.timestamp }
    val span = (now - oldest).coerceAtLeast(1L)
    val sums = DoubleArray(buckets)
    transactions.forEach { tx ->
        val slot = ((tx.timestamp - oldest).toDouble() / span * (buckets - 1)).toInt().coerceIn(0, buckets - 1)
        sums[slot] += selector(tx)
    }
    val min = sums.min()
    val max = sums.max()
    val range = max - min
    return if (range < 1e-9) {
        List(buckets) { 3 }
    } else {
        sums.map { ((it - min) / range * 5).toInt() + 1 }
    }
}

@Composable
fun OverviewCardsScrollableRow(
    net: Double,
    income: Double,
    expense: Double,
    savings: Double,
    borrowed: Double,
    lent: Double,
    txCount: Int,
    focusedCard: String,
    onCardClick: (String) -> Unit,
    transactions: List<Transaction> = emptyList()
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    val incTx  = remember(transactions) { transactions.filter { it.type == TransactionType.INCOME || it.type == TransactionType.BORROWED } }
    val expTx  = remember(transactions) { transactions.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT } }
    val borTx  = remember(transactions) { transactions.filter { it.type == TransactionType.BORROWED } }
    val lentTx = remember(transactions) { transactions.filter { it.type == TransactionType.LENT } }

    val netSparkline  = remember(transactions) { sparkline(transactions, selector = { it.amount }) }
    val incSparkline  = remember(incTx)  { sparkline(incTx,  selector = { it.amount }) }
    val expSparkline  = remember(expTx)  { sparkline(expTx,  selector = { abs(it.amount) }) }
    val savSparkline  = remember(transactions) { sparkline(transactions, selector = { if (it.type == TransactionType.INCOME || it.type == TransactionType.BORROWED) it.amount else -abs(it.amount) }) }
    val borSparkline  = remember(borTx)  { sparkline(borTx,  selector = { it.amount }) }
    val lentSparkline = remember(lentTx) { sparkline(lentTx, selector = { abs(it.amount) }) }
    val txSparkline   = remember(transactions) {
        val buckets = 11
        val now = System.currentTimeMillis()
        val oldest = transactions.minOfOrNull { it.timestamp } ?: now
        val span = (now - oldest).coerceAtLeast(1L)
        val counts = IntArray(buckets)
        transactions.forEach { tx ->
            val slot = ((tx.timestamp - oldest).toDouble() / span * (buckets - 1)).toInt().coerceIn(0, buckets - 1)
            counts[slot]++
        }
        val max = counts.max().coerceAtLeast(1)
        counts.map { ((it.toDouble() / max * 5).toInt() + 1).coerceIn(1, 6) }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OverviewCard(
                title = "NET",
                value = String.format("${currentCurrency.symbol}%,.2f", net),
                subtext = if (net >= 0) "+${String.format("%.1f", if (income > 0) net / income * 100 else 0.0)}%" else "${String.format("%.1f", if (income > 0) net / income * 100 else 0.0)}%",
                subtextColor = if (net >= 0) colors.positive else colors.negative,
                sparklineData = netSparkline,
                sparklineColor = colors.textPrimary,
                isFocused = focusedCard == "NET",
                onClick = { onCardClick("NET") }
            )
        }
        item {
            OverviewCard(
                title = "INCOME",
                value = String.format("${currentCurrency.symbol}%,.2f", income),
                subtext = "${incTx.size} entries",
                subtextColor = colors.positive,
                sparklineData = incSparkline,
                sparklineColor = colors.positive,
                isFocused = focusedCard == "INCOME",
                onClick = { onCardClick("INCOME") }
            )
        }
        item {
            OverviewCard(
                title = "EXPENSES",
                value = String.format("${currentCurrency.symbol}%,.2f", expense),
                subtext = "${expTx.size} entries",
                subtextColor = colors.negative,
                sparklineData = expSparkline,
                sparklineColor = colors.negative,
                isFocused = focusedCard == "EXPENSES",
                onClick = { onCardClick("EXPENSES") }
            )
        }
        item {
            val savingsRate = if (income > 0) savings / income * 100 else 0.0
            OverviewCard(
                title = "SAVINGS",
                value = String.format("${currentCurrency.symbol}%,.2f", savings),
                subtext = String.format("%.1f%% rate", savingsRate.coerceAtLeast(0.0)),
                subtextColor = if (savings >= 0) colors.positive else colors.negative,
                sparklineData = savSparkline,
                sparklineColor = colors.positive,
                isFocused = focusedCard == "SAVINGS",
                onClick = { onCardClick("SAVINGS") }
            )
        }
        item {
            OverviewCard(
                title = "BORROWED",
                value = String.format("${currentCurrency.symbol}%,.2f", borrowed),
                subtext = "${borTx.size} entries",
                subtextColor = colors.textSecondary,
                sparklineData = borSparkline,
                sparklineColor = colors.textPrimary,
                isFocused = focusedCard == "BORROWED",
                onClick = { onCardClick("BORROWED") }
            )
        }
        item {
            OverviewCard(
                title = "LENT",
                value = String.format("${currentCurrency.symbol}%,.2f", lent),
                subtext = "${lentTx.size} entries",
                subtextColor = colors.textSecondary,
                sparklineData = lentSparkline,
                sparklineColor = colors.textPrimary,
                isFocused = focusedCard == "LENT",
                onClick = { onCardClick("LENT") }
            )
        }
        item {
            OverviewCard(
                title = "TRANSACTIONS",
                value = txCount.toString(),
                subtext = "in period",
                subtextColor = colors.textSecondary,
                sparklineData = txSparkline,
                sparklineColor = colors.textPrimary,
                isFocused = focusedCard == "TRANSACTIONS",
                onClick = { onCardClick("TRANSACTIONS") }
            )
        }
    }
}

@Composable
fun OverviewCard(
    title: String,
    value: String,
    subtext: String,
    subtextColor: Color,
    sparklineData: List<Int>,
    sparklineColor: Color,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    val haptic = LocalHapticFeedback.current
    val outlineColor = if (isFocused) colors.textPrimary else colors.border
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, outlineColor, RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                        fontWeight = FontWeight.Bold,
                text = title,
                fontFamily = NothingGlyph,
                fontSize = 10.sp,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                        fontWeight = FontWeight.Bold,
                text = value,
                fontFamily = NothingGlyph,
                fontSize = 18.sp,
                color = colors.textPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subtext.contains("%") || subtext.contains("1") || subtext.contains("2")) {
                    Text(
                        text = "↑ ",
                        fontSize = 11.sp,
                        color = subtextColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = subtext,
                    fontSize = 11.sp,
                    color = subtextColor
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            DotMatrixMiniChart(
                data = sparklineData,
                color = sparklineColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            )
        }
    }
}

@Composable
fun DotMatrixMiniChart(
    data: List<Int>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        val maxDots = 6
        data.forEach { height ->
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (h in (maxDots - 1) downTo 0) {
                    val isFilled = h < height
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (isFilled) color else colors.border)
                    )
                }
            }
        }
    }
}

@Composable
fun CashFlowCard(
    transactions: List<Transaction>,
    selectedRange: String,
    graphType: String,
    selectedPointIndex: Int?,
    onTypeToggle: (String) -> Unit,
    onPointClick: (Int) -> Unit
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        fontWeight = FontWeight.Bold,
                    text = if (graphType == "FLOW") "CASH FLOW TIMELINE" else "NET PROGRESSION TREND",
                    fontFamily = NothingGlyph,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    letterSpacing = 1.sp
                )
                // Tabs to toggle graph style between Cashflow Columns and Net progression line
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (graphType == "FLOW") colors.textPrimary else colors.buttonBackground)
                            .clickable { onTypeToggle("FLOW") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = "FLOW",
                            fontSize = 8.sp,
                            fontFamily = NothingGlyph,
                            color = if (graphType == "FLOW") colors.background else colors.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (graphType == "TREND") colors.textPrimary else colors.buttonBackground)
                            .clickable { onTypeToggle("TREND") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = "TREND",
                            fontSize = 8.sp,
                            fontFamily = NothingGlyph,
                            color = if (graphType == "TREND") colors.background else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Legends row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (graphType == "FLOW") {
                    LegendItem("Income", colors.positive)
                    Spacer(modifier = Modifier.width(8.dp))
                    LegendItem("Expenses", colors.negative)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                LegendItem("Net", colors.textPrimary)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bucket transactions into N time slots from real data
            val numSlots = 14
            data class SlotData(val incomeTotal: Double, val expenseTotal: Double, val netRunning: Double, val label: String)
            val slotDataList = remember(transactions, selectedRange) {
                if (transactions.isEmpty()) return@remember List(numSlots) { SlotData(0.0, 0.0, 0.0, "") }
                val now = System.currentTimeMillis()
                val oldest = transactions.minOf { it.timestamp }
                val span = (now - oldest).coerceAtLeast(1L)
                val incSlots = DoubleArray(numSlots)
                val expSlots = DoubleArray(numSlots)
                transactions.forEach { tx ->
                    val slot = ((tx.timestamp - oldest).toDouble() / span * (numSlots - 1)).toInt().coerceIn(0, numSlots - 1)
                    when (tx.type) {
                        TransactionType.INCOME, TransactionType.BORROWED -> incSlots[slot] += tx.amount
                        else -> expSlots[slot] += abs(tx.amount)
                    }
                }
                val slotDuration = span / numSlots
                var running = 0.0
                (0 until numSlots).map { i ->
                    val slotTime = oldest + i * slotDuration
                    val lbl = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(slotTime))
                    running += incSlots[i] - expSlots[i]
                    SlotData(incSlots[i], expSlots[i], running, lbl)
                }
            }
            val maxInc = slotDataList.maxOf { it.incomeTotal }.coerceAtLeast(1.0)
            val maxExp = slotDataList.maxOf { it.expenseTotal }.coerceAtLeast(1.0)
            val maxAbs = maxOf(maxInc, maxExp)
            val maxNet = slotDataList.maxOf { abs(it.netRunning) }.coerceAtLeast(1.0)

            // Y-axis max label
            val yMax = if (graphType == "FLOW") maxAbs else maxNet
            fun fmtY(v: Double) = if (v >= 1000) "${String.format("%.1f", v/1000)}K" else String.format("%.0f", v)

            // Graph representation: timeline coordinates
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Y-Axis Labels
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = fmtY(yMax),       fontSize = 10.sp, color = colors.textSecondary)
                        Text(text = fmtY(yMax * 0.5), fontSize = 10.sp, color = colors.textSecondary)
                        Text(text = "0",               fontSize = 10.sp, color = colors.textSecondary)
                        Text(text = "-${fmtY(yMax * 0.5)}", fontSize = 10.sp, color = colors.textSecondary)
                        Text(text = "-${fmtY(yMax)}", fontSize = 10.sp, color = colors.textSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Grid & Graph Plotted Canvas
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width  = size.width
                            val height = size.height
                            val centerY = height / 2
                            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 10f), 0f)

                            // Grid lines
                            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                                drawLine(
                                    color = if (frac == 0.5f) colors.textSecondary else colors.border,
                                    start = Offset(0f, height * frac),
                                    end   = Offset(width, height * frac),
                                    strokeWidth = if (frac == 0.5f) 1.5f else 1f,
                                    pathEffect  = if (frac == 0.5f) null else pathEffect
                                )
                            }

                            val pointsCount = slotDataList.size
                            val colWidth = width / pointsCount

                            if (selectedPointIndex != null && selectedPointIndex < pointsCount) {
                                val selectX = colWidth * selectedPointIndex + colWidth / 2
                                drawLine(
                                    color = colors.textPrimary.copy(alpha = 0.4f),
                                    start = Offset(selectX, 0f), end = Offset(selectX, height),
                                    strokeWidth = 1f, pathEffect = pathEffect
                                )
                            }

                            if (graphType == "FLOW") {
                                for (i in 0 until pointsCount) {
                                    val x = colWidth * i + colWidth / 2
                                    val slot = slotDataList[i]
                                    val incH = (slot.incomeTotal  / maxAbs * (centerY - 8)).toFloat()
                                    val expH = (slot.expenseTotal / maxAbs * (centerY - 8)).toFloat()

                                    // Income dots (above center)
                                    var dotY = centerY - 6f
                                    var drawn = 0f
                                    while (drawn < incH && dotY > 6f) {
                                        drawCircle(color = colors.positive, radius = 2.5f, center = Offset(x, dotY))
                                        dotY -= 8f; drawn += 8f
                                    }
                                    // Expense dots (below center)
                                    dotY = centerY + 6f; drawn = 0f
                                    while (drawn < expH && dotY < height - 6f) {
                                        drawCircle(color = colors.negative, radius = 2.5f, center = Offset(x, dotY))
                                        dotY += 8f; drawn += 8f
                                    }
                                    // Net dot
                                    val netY = (centerY - (slot.incomeTotal - slot.expenseTotal) / maxAbs * (centerY - 8)).toFloat()
                                    drawCircle(
                                        color  = if (selectedPointIndex == i) colors.textPrimary else colors.textPrimary.copy(alpha = 0.6f),
                                        radius = if (selectedPointIndex == i) 5f else 3f,
                                        center = Offset(x, netY.coerceIn(4f, height - 4f))
                                    )
                                }
                            } else {
                                var lastX = 0f; var lastY = centerY
                                for (i in 0 until pointsCount) {
                                    val x    = colWidth * i + colWidth / 2
                                    val netY = (centerY - slotDataList[i].netRunning / maxNet * (centerY - 8)).toFloat().coerceIn(4f, height - 4f)
                                    if (i > 0) {
                                        drawLine(
                                            color = colors.textPrimary.copy(alpha = 0.7f),
                                            start = Offset(lastX, lastY), end = Offset(x, netY), strokeWidth = 2f
                                        )
                                    }
                                    drawCircle(
                                        color  = if (selectedPointIndex == i) colors.textPrimary else colors.positive,
                                        radius = if (selectedPointIndex == i) 5.5f else 3.5f,
                                        center = Offset(x, netY)
                                    )
                                    lastX = x; lastY = netY
                                }
                            }
                        }

                        // Transparent clickable columns
                        Row(modifier = Modifier.fillMaxSize()) {
                            for (i in 0 until numSlots) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f).fillMaxHeight()
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPointClick(i) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // X-Axis Labels from real slot timestamps
            val xLabels = remember(slotDataList) {
                val step = (slotDataList.size - 1) / 4
                (0..4).map { slotDataList.getOrNull(it * step)?.label ?: "" }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                xLabels.forEach { lbl -> Text(text = lbl, fontSize = 10.sp, color = colors.textSecondary) }
            }

            // Interactive Tooltip
            AnimatedVisibility(
                visible = selectedPointIndex != null,
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut()
            ) {
                val idx  = (selectedPointIndex ?: 0).coerceIn(0, slotDataList.lastIndex)
                val slot = slotDataList[idx]
                Box(
                    modifier = Modifier
                        .fillMaxWidth().padding(top = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.buttonBackground)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,text = slot.label, fontSize = 11.sp, color = colors.textSecondary, fontFamily = NothingGlyph)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(text = String.format("Inc: +%,.0f", slot.incomeTotal),  fontSize = 11.sp, color = colors.positive, fontWeight = FontWeight.Bold)
                            Text(text = String.format("Exp: -%,.0f", slot.expenseTotal), fontSize = 11.sp, color = colors.negative, fontWeight = FontWeight.Bold)
                            val net = slot.incomeTotal - slot.expenseTotal
                            Text(text = String.format("Net: %s%,.0f", if (net >= 0) "+" else "", net), fontSize = 11.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpendingByCategoryCard(
    transactions: List<Transaction>,
    onCategoryClick: (String) -> Unit
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    val haptic = LocalHapticFeedback.current
    var categoryMode by remember { mutableStateOf("EXPENSES") }

    val totalExpense = remember(transactions) {
        transactions.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT }.sumOf { abs(it.amount) }
    }
    val totalIncome = remember(transactions) {
        transactions.filter { it.type == TransactionType.INCOME || it.type == TransactionType.BORROWED }.sumOf { it.amount }
    }

    val currentTotal = if (categoryMode == "EXPENSES") totalExpense else totalIncome

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        fontWeight = FontWeight.Bold,
                    text = if (categoryMode == "EXPENSES") "SPENDING BY CATEGORY" else "INCOME BY CATEGORY",
                    fontFamily = NothingGlyph,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    letterSpacing = 1.sp
                )

                // Tabs EXP vs INC
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (categoryMode == "EXPENSES") colors.textPrimary else colors.buttonBackground)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                categoryMode = "EXPENSES"
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = "EXP",
                            fontSize = 8.sp,
                            fontFamily = NothingGlyph,
                            color = if (categoryMode == "EXPENSES") colors.background else colors.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (categoryMode == "INCOME") colors.textPrimary else colors.buttonBackground)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                categoryMode = "INCOME"
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = "INC",
                            fontSize = 8.sp,
                            fontFamily = NothingGlyph,
                            color = if (categoryMode == "INCOME") colors.background else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                        fontWeight = FontWeight.Bold,
                text = String.format("Total ${currentCurrency.symbol}%,.2f", currentTotal),
                fontFamily = NothingGlyph,
                fontSize = 14.sp,
                color = if (categoryMode == "EXPENSES") colors.negative else colors.positive
            )

            Spacer(modifier = Modifier.height(16.dp))

            val categoriesMap = remember(transactions, categoryMode) {
                val filtered = if (categoryMode == "EXPENSES") {
                    transactions.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT }
                } else {
                    transactions.filter { it.type == TransactionType.INCOME || it.type == TransactionType.BORROWED }
                }
                filtered.groupBy { it.category }
                    .mapValues { entry -> abs(entry.value.sumOf { it.amount }) }
                    .toList()
                    .sortedByDescending { it.second }
            }

            val barColor = if (categoryMode == "EXPENSES") colors.negative else colors.positive

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                categoriesMap.forEach { (catName, amount) ->
                    val percentage = if (currentTotal > 0) amount / currentTotal else 0.0
                    CategorySpendingRow(
                        name = catName,
                        amount = amount,
                        percentage = percentage.toFloat(),
                        color = barColor,
                        onClick = { onCategoryClick(catName) }
                    )
                }
            }
        }
    }
}

@Composable
fun CategorySpendingRow(
    name: String,
    amount: Double,
    percentage: Float,
    color: Color,
    onClick: () -> Unit
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1.2f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.border),
                contentAlignment = Alignment.Center
            ) {
                val iconStr = when (name) {
                    "Grocery" -> "🛒"
                    "Dining" -> "🍽️"
                    "Shopping" -> "🛍️"
                    "Fuel" -> "⛽"
                    "Rent" -> "🏠"
                    "Subs" -> "💳"
                    "Utilities" -> "🔌"
                    "Lent" -> "⇧"
                    "Salary" -> "💰"
                    "Freelance" -> "💻"
                    "Gift" -> "🎁"
                    "Borrowed" -> "📥"
                    else -> "📁"
                }
                // Use the custom Emoji font resource for perfect color emoji rendering
                Text(
                    text = iconStr,
                    fontFamily = EmojiFont,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
        }

        DotsProgressBar(
            percentage = percentage,
            color = color,
            modifier = Modifier.weight(1.5f)
        )

        Row(
            modifier = Modifier.weight(1.1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                        fontWeight = FontWeight.Bold,
                text = String.format("${currentCurrency.symbol}%,.2f", amount),
                fontFamily = NothingGlyph,
                fontSize = 12.sp,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = String.format("%.1f%%", percentage * 100),
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
fun DailyAveragesCard(
    totalExpense: Double,
    totalIncome: Double,
    txCount: Int,
    rangeDays: Int
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                        fontWeight = FontWeight.Bold,
                text = "DAILY AVERAGES",
                fontFamily = NothingGlyph,
                fontSize = 11.sp,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Spent Daily", fontSize = 11.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = String.format("${currentCurrency.symbol}%,.0f", totalExpense / rangeDays),
                        fontFamily = NothingGlyph, fontSize = 20.sp, color = colors.negative
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Income Daily", fontSize = 11.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = String.format("${currentCurrency.symbol}%,.0f", totalIncome / rangeDays),
                        fontFamily = NothingGlyph, fontSize = 20.sp, color = colors.positive
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Count Daily", fontSize = 11.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = String.format("%.1f", txCount.toFloat() / rangeDays),
                        fontFamily = NothingGlyph, fontSize = 20.sp, color = colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryRowCards(
    totalBorrowed: Double,
    totalLent: Double,
    debtDiff: Double,
    incCount: Int,
    expCount: Int,
    borCount: Int,
    lenCount: Int,
    totCount: Int
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    val totalDebt = totalBorrowed + totalLent
    val borrowPercentage = if (totalDebt > 0) (totalBorrowed / totalDebt).toFloat() else 0f
    val lentPercentage = if (totalDebt > 0) (totalLent / totalDebt).toFloat() else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: BORROWED / LENT
        Box(
            modifier = Modifier
                .weight(1f)
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                        fontWeight = FontWeight.Bold,
                    text = "BORROWED / LENT",
                    fontFamily = NothingGlyph,
                    fontSize = 9.sp,
                    color = colors.textSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Borrowed to you", fontSize = 11.sp, color = colors.textSecondary)
                Text(
                        fontWeight = FontWeight.Bold,
                    text = String.format("${currentCurrency.symbol}%,.2f", totalBorrowed),
                    fontFamily = NothingGlyph,
                    fontSize = 14.sp,
                    color = colors.positive
                )
                DotsProgressBar(percentage = borrowPercentage, color = colors.positive, modifier = Modifier.padding(top = 4.dp))

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "You lent", fontSize = 11.sp, color = colors.textSecondary)
                Text(
                        fontWeight = FontWeight.Bold,
                    text = String.format("${currentCurrency.symbol}%,.2f", totalLent),
                    fontFamily = NothingGlyph,
                    fontSize = 14.sp,
                    color = colors.negative
                )
                DotsProgressBar(percentage = lentPercentage, color = colors.negative, modifier = Modifier.padding(top = 4.dp))

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = String.format("Net Diff: %s${currentCurrency.symbol}%,.2f", if (debtDiff >= 0) "+" else "", debtDiff),
                    fontSize = 10.sp,
                    color = if (debtDiff >= 0) colors.positive else colors.negative,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Card 2: TRANSACTIONS COUNTS
        Box(
            modifier = Modifier
                .weight(1f)
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(
                        fontWeight = FontWeight.Bold,
                    text = "TRANSACTIONS",
                    fontFamily = NothingGlyph,
                    fontSize = 9.sp,
                    color = colors.textSecondary,
                    letterSpacing = 0.5.sp
                )

                TransactionCountRow("Income", incCount, colors.positive)
                TransactionCountRow("Expense", expCount, colors.negative)
                TransactionCountRow("Borrowed", borCount, colors.textSecondary)
                TransactionCountRow("Lent", lenCount, colors.textSecondary)

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Total", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(
                        fontWeight = FontWeight.Bold,text = totCount.toString(), fontFamily = NothingGlyph, fontSize = 11.sp, color = colors.textPrimary)
                }
            }
        }
    }
}

@Composable
fun SpendingCalendarCard(
    transactions: List<Transaction>,
    heatmapMode: String,
    onModeToggle: (String) -> Unit
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        fontWeight = FontWeight.Bold,
                    text = "SPENDING HEATMAP (30D)",
                    fontFamily = NothingGlyph,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    letterSpacing = 1.sp
                )
                // Tabs to toggle calendar heatmaps for Income vs Expenses
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (heatmapMode == "EXPENSES") colors.textPrimary else colors.buttonBackground)
                            .clickable { onModeToggle("EXPENSES") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = "EXP",
                            fontSize = 8.sp,
                            fontFamily = NothingGlyph,
                            color = if (heatmapMode == "EXPENSES") colors.background else colors.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (heatmapMode == "INCOME") colors.textPrimary else colors.buttonBackground)
                            .clickable { onModeToggle("INCOME") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = "INC",
                            fontSize = 8.sp,
                            fontFamily = NothingGlyph,
                            color = if (heatmapMode == "INCOME") colors.background else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Build per-day totals from real transactions (last 35 days = 5 weeks)
            val now = System.currentTimeMillis()
            val dayTotals = remember(transactions, heatmapMode) {
                val map = mutableMapOf<Int, Double>() // dayOfYear -> total
                val cutoff = now - 35L * 86_400_000
                transactions.filter { it.timestamp >= cutoff }.forEach { tx ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    val key = cal.get(Calendar.YEAR) * 10000 + cal.get(Calendar.DAY_OF_YEAR)
                    val v = if (heatmapMode == "EXPENSES") {
                        if (tx.type == TransactionType.EXPENSE || tx.type == TransactionType.LENT) abs(tx.amount) else 0.0
                    } else {
                        if (tx.type == TransactionType.INCOME || tx.type == TransactionType.BORROWED) tx.amount else 0.0
                    }
                    map[key] = (map[key] ?: 0.0) + v
                }
                map
            }
            val maxDayTotal = dayTotals.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

            // Build 7×5 grid: day 0 = Mon 5 weeks ago
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 12) // set to noon to protect against DST adjustments
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val todayDow = cal.get(Calendar.DAY_OF_WEEK) // Sun=1
            val daysToLastMon = (todayDow + 5) % 7
            cal.add(Calendar.DAY_OF_YEAR, -(daysToLastMon + 28))
            val startOfGridCal = cal.clone() as Calendar

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                dayLabels.forEachIndexed { d, dayLabel ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = dayLabel, fontSize = 9.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                        for (w in 0 until 5) {
                            val cellCal = (startOfGridCal.clone() as Calendar).apply {
                                add(Calendar.DAY_OF_YEAR, w * 7 + d)
                            }
                            val key = cellCal.get(Calendar.YEAR) * 10000 + cellCal.get(Calendar.DAY_OF_YEAR)
                            val total = dayTotals[key] ?: 0.0
                            val intensity = (total / maxDayTotal).toFloat().coerceIn(0f, 1f)
                            val gridColor = if (heatmapMode == "EXPENSES") {
                                when {
                                    intensity == 0f  -> colors.heatmapExpenseLevels[0]
                                    intensity < 0.25f -> colors.heatmapExpenseLevels[1]
                                    intensity < 0.5f  -> colors.heatmapExpenseLevels[2]
                                    intensity < 0.75f -> colors.heatmapExpenseLevels[3]
                                    else              -> colors.heatmapExpenseLevels[4]
                                }
                            } else {
                                when {
                                    intensity == 0f  -> colors.heatmapIncomeLevels[0]
                                    intensity < 0.25f -> colors.heatmapIncomeLevels[1]
                                    intensity < 0.5f  -> colors.heatmapIncomeLevels[2]
                                    intensity < 0.75f -> colors.heatmapIncomeLevels[3]
                                    else              -> colors.heatmapIncomeLevels[4]
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(gridColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpendingWeekdayCard(transactions: List<Transaction> = emptyList()) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    val haptic = LocalHapticFeedback.current
    var weekdayMode by remember { mutableStateOf("EXPENSES") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        fontWeight = FontWeight.Bold,
                    text = if (weekdayMode == "EXPENSES") "SPENDING BY WEEKDAY" else "INCOME BY WEEKDAY",
                    fontFamily = NothingGlyph,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    letterSpacing = 1.sp
                )

                // Tabs EXP vs INC
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (weekdayMode == "EXPENSES") colors.textPrimary else colors.buttonBackground)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                weekdayMode = "EXPENSES"
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = "EXP",
                            fontSize = 8.sp,
                            fontFamily = NothingGlyph,
                            color = if (weekdayMode == "EXPENSES") colors.background else colors.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (weekdayMode == "INCOME") colors.textPrimary else colors.buttonBackground)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                weekdayMode = "INCOME"
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = "INC",
                            fontSize = 8.sp,
                            fontFamily = NothingGlyph,
                            color = if (weekdayMode == "INCOME") colors.background else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val weekdayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val weekdays = remember(transactions, weekdayMode) {
                val sums = DoubleArray(7)
                transactions.forEach { tx ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    val dow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
                    val v = if (weekdayMode == "EXPENSES") {
                        if (tx.type == TransactionType.EXPENSE || tx.type == TransactionType.LENT) abs(tx.amount) else 0.0
                    } else {
                        if (tx.type == TransactionType.INCOME || tx.type == TransactionType.BORROWED) tx.amount else 0.0
                    }
                    sums[dow] += v
                }
                // Mon-Sun order
                listOf(1,2,3,4,5,6,0).map { weekdayNames[it] to sums[it] }
            }

            val maxVal = weekdays.maxOf { it.second }
            val barColor = if (weekdayMode == "EXPENSES") colors.negative else colors.positive

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                weekdays.forEach { (day, amt) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = day, fontSize = 12.sp, color = colors.textPrimary, modifier = Modifier.width(90.dp))
                        
                        // Dots progress representing proportion
                        val ratio = if (maxVal > 0) (amt / maxVal).toFloat() else 0f
                        DotsProgressBar(percentage = ratio, color = barColor, modifier = Modifier.weight(1f))
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = String.format("%,.0f", amt),
                            fontFamily = NothingGlyph,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(50.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialRatiosCard(
    savings: Double,
    income: Double,
    expense: Double,
    borrowed: Double
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                        fontWeight = FontWeight.Bold,
                text = "FINANCIAL RATIOS",
                fontFamily = NothingGlyph,
                fontSize = 11.sp,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            val savingsRate = if (income > 0) (savings / income * 100).coerceAtLeast(0.0) else 0.0
            val expenseRatio = if (income > 0) (expense / income * 100) else 0.0
            val borrowRatio = if (income > 0) (borrowed / income * 100) else 0.0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Savings Rate", fontSize = 11.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = String.format("%.0f%%", savingsRate),
                        fontFamily = NothingGlyph,
                        fontSize = 22.sp,
                        color = colors.positive
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Expense Ratio", fontSize = 11.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = String.format("%.0f%%", expenseRatio),
                        fontFamily = NothingGlyph,
                        fontSize = 22.sp,
                        color = colors.negative
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Borrow Ratio", fontSize = 11.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = String.format("%.0f%%", borrowRatio),
                        fontFamily = NothingGlyph,
                        fontSize = 22.sp,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun RecordsCard(transactions: List<Transaction> = emptyList()) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                fontWeight = FontWeight.Bold,
                text = "LIFETIME RECORDS",
                fontFamily = NothingGlyph,
                fontSize = 11.sp,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            val currentCurrencyRec = LocalAppCurrency.current
            val records = remember(transactions) {
                if (transactions.isEmpty()) return@remember listOf(
                    "Highest single income" to "—",
                    "Highest single expense" to "—",
                    "Most active day" to "—"
                )
                val bigInc = transactions.filter { it.type == TransactionType.INCOME || it.type == TransactionType.BORROWED }.maxByOrNull { it.amount }
                val bigExp = transactions.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT }.minByOrNull { it.amount }
                // Most txns in a single day
                val dayCount = transactions.groupBy {
                    val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    c.get(Calendar.YEAR) * 10000 + c.get(Calendar.DAY_OF_YEAR)
                }.maxByOrNull { it.value.size }
                val dayLabel = dayCount?.value?.firstOrNull()?.let {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it.timestamp))
                } ?: "—"
                listOf(
                    "Highest single income"  to (bigInc?.let { "${it.category} (${currentCurrencyRec.symbol}${String.format("%,.0f", it.amount)})" } ?: "—"),
                    "Highest single expense" to (bigExp?.let { "${it.category} (${currentCurrencyRec.symbol}${String.format("%,.0f", abs(it.amount))})" } ?: "—"),
                    "Most active day"        to (dayCount?.let { "$dayLabel · ${it.value.size} tx" } ?: "—")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                records.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, fontSize = 12.sp, color = colors.textSecondary)
                        Text(text = value, fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun InsightsCard(transactions: List<Transaction> = emptyList()) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                fontWeight = FontWeight.Bold,
                text = "SMART INSIGHTS",
                fontFamily = NothingGlyph,
                fontSize = 11.sp,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Compute insights data
            data class InsightItem(val icon: String, val iconColor: Color, val text: String)

            val insightsList = remember(transactions) {
                val list = mutableListOf<InsightItem>()
                val now = System.currentTimeMillis()
                val monthStart     = now - 30L  * 86_400_000
                val prevMonthStart = now - 60L  * 86_400_000

                val thisMonth = transactions.filter { it.timestamp >= monthStart }
                val lastMonth = transactions.filter { it.timestamp in prevMonthStart..monthStart }

                fun inc(l: List<Transaction>) = l.filter { it.type == TransactionType.INCOME || it.type == TransactionType.BORROWED }.sumOf { it.amount }

                val thisInc = inc(thisMonth)
                val lastInc = inc(lastMonth)
                val thisExp = thisMonth.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT }.sumOf { abs(it.amount) }
                val lastExp = lastMonth.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT }.sumOf { abs(it.amount) }

                // 1. Income Insight
                if (thisInc == 0.0 && lastInc == 0.0) {
                    list.add(InsightItem("•", colors.textSecondary, "No income logged in the last 60 days."))
                } else if (lastInc == 0.0 && thisInc > 0.0) {
                    list.add(InsightItem("↑", colors.positive, "First income recorded this month: ${currentCurrency.symbol}${String.format("%,.2f", thisInc)}!"))
                } else if (lastInc > 0.0 && thisInc == 0.0) {
                    list.add(InsightItem("↓", colors.negative, "Income dropped to zero this month (was ${currentCurrency.symbol}${String.format("%,.2f", lastInc)} last month)."))
                } else {
                    val incDelta = (thisInc - lastInc) / lastInc * 100.0
                    if (incDelta > 0.1) {
                        list.add(InsightItem("↑", colors.positive, "Income up by ${String.format("%.1f", incDelta)}% vs last month."))
                    } else if (incDelta < -0.1) {
                        list.add(InsightItem("↓", colors.negative, "Income down by ${String.format("%.1f", abs(incDelta))}% vs last month."))
                    } else {
                        list.add(InsightItem("→", colors.textSecondary, "Income unchanged vs last month."))
                    }
                }

                // 2. Expense Insight
                if (thisExp == 0.0 && lastExp == 0.0) {
                    list.add(InsightItem("•", colors.textSecondary, "No expenses logged in the last 60 days."))
                } else if (lastExp == 0.0 && thisExp > 0.0) {
                    list.add(InsightItem("↑", colors.negative, "Expenses started this month at ${currentCurrency.symbol}${String.format("%,.2f", thisExp)}."))
                } else if (lastExp > 0.0 && thisExp == 0.0) {
                    list.add(InsightItem("↓", colors.positive, "Amazing! Expenses dropped to zero this month (was ${currentCurrency.symbol}${String.format("%,.2f", lastExp)} last month)."))
                } else {
                    val expDelta = (thisExp - lastExp) / lastExp * 100.0
                    if (expDelta > 0.1) {
                        list.add(InsightItem("↑", colors.negative, "Expenses up by ${String.format("%.1f", expDelta)}% vs last month."))
                    } else if (expDelta < -0.1) {
                        list.add(InsightItem("↓", colors.positive, "Expenses down by ${String.format("%.1f", abs(expDelta))}% vs last month."))
                    } else {
                        list.add(InsightItem("→", colors.textSecondary, "Expenses unchanged vs last month."))
                    }
                }

                // 3. Top spend category
                val topCat = thisMonth.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT }
                    .groupBy { it.category }.maxByOrNull { e -> e.value.sumOf { abs(it.amount) } }?.key
                if (topCat != null) {
                    list.add(InsightItem("★", colors.textPrimary, "Top spend category this month: $topCat."))
                } else {
                    list.add(InsightItem("★", colors.textSecondary, "Add transactions to see top spend category."))
                }

                // 4. Savings Rate Insight
                if (thisInc > 0.0) {
                    val savings = thisInc - thisExp
                    val savingsRate = savings / thisInc * 100.0
                    if (savingsRate >= 20.0) {
                        list.add(InsightItem("💰", colors.positive, "Healthy savings rate of ${String.format("%.1f", savingsRate)}% this month!"))
                    } else if (savingsRate < 0.0) {
                        list.add(InsightItem("⚠️", colors.negative, "Spending exceeded income this month (Savings rate: ${String.format("%.1f", savingsRate)}%)."))
                    } else {
                        list.add(InsightItem("⚖️", colors.textSecondary, "Modest savings rate of ${String.format("%.1f", savingsRate)}% this month. Try aiming for 20%."))
                    }
                }

                // 5. Weekend Splurge Analysis (comparing average spending per weekend day vs weekday)
                var weekendSum = 0.0
                var weekdaySum = 0.0
                var weekendCount = 0
                var weekdayCount = 0
                val cal = Calendar.getInstance()
                val todayMs = cal.timeInMillis
                for (i in 0 until 30) {
                    val dayMs = todayMs - i * 86_400_000L
                    cal.timeInMillis = dayMs
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                        weekendCount++
                    } else {
                        weekdayCount++
                    }
                }

                thisMonth.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT }.forEach { tx ->
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    val dow = txCal.get(Calendar.DAY_OF_WEEK)
                    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                        weekendSum += abs(tx.amount)
                    } else {
                        weekdaySum += abs(tx.amount)
                    }
                }
                val weekendAvg = if (weekendCount > 0) weekendSum / weekendCount else 0.0
                val weekdayAvg = if (weekdayCount > 0) weekdaySum / weekdayCount else 0.0
                if (weekdayAvg > 0.0 && weekendAvg > weekdayAvg * 1.25) {
                    val ratio = (weekendAvg - weekdayAvg) / weekdayAvg * 100.0
                    list.add(InsightItem("🍕", colors.textPrimary, "Weekend spending is ${String.format("%.0f", ratio)}% higher on average than weekdays."))
                }

                // 6. Logging consistency
                val activeDays = thisMonth.groupBy {
                    val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    c.get(Calendar.YEAR) * 10000 + c.get(Calendar.DAY_OF_YEAR)
                }.size
                if (activeDays >= 15) {
                    list.add(InsightItem("📅", colors.positive, "Consistent tracking! You logged transactions on $activeDays of the last 30 days."))
                } else if (activeDays in 1..5) {
                    list.add(InsightItem("✏️", colors.textSecondary, "Logged only on $activeDays days. Try logging daily to build a complete view."))
                }

                list
            }

            @Composable
            fun InsightRow(icon: String, iconColor: Color, text: String) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.size(32.dp).border(1.dp, colors.border, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(text = icon, color = iconColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = text, fontSize = 13.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                insightsList.forEach { item ->
                    InsightRow(icon = item.icon, iconColor = item.iconColor, text = item.text)
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 9.sp, color = colors.textSecondary)
    }
}

@Composable
fun DotsProgressBar(
    percentage: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val totalDots = 18
        val filledDots = (percentage * totalDots).toInt().coerceIn(0, totalDots)
        for (i in 0 until totalDots) {
            val isFilled = i < filledDots
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isFilled) color else colors.border)
            )
        }
    }
}

@Composable
fun TransactionCountRow(label: String, count: Int, color: Color) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 10.sp, color = colors.textSecondary)
        }
        Text(
                        fontWeight = FontWeight.Bold,text = count.toString(), fontFamily = NothingGlyph, fontSize = 10.sp, color = colors.textPrimary)
    }
}

@Composable
fun AnalyticsFooter() {
    val colors = LocalDeltaColors.current
    Spacer(modifier = Modifier.height(32.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "DELTA ANALYTICS · LOCAL ROOM DB",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary.copy(alpha = 0.8f),
            fontFamily = NothingGlyph,
            letterSpacing = 1.sp
        )
        Text(
            text = "All calculations are done securely on-device. Overview cards define key metrics when clicked. Exports are formatted as CSV spreadsheets.",
            fontSize = 10.sp,
            color = colors.textSecondary.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
            lineHeight = 14.sp
        )
    }
}

private fun writeTransactionsToUri(context: android.content.Context, uri: android.net.Uri, transactions: List<Transaction>): Boolean {
    return try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            java.io.BufferedWriter(java.io.OutputStreamWriter(outputStream)).use { writer ->
                writer.write("ID,Date,Amount,Category,Type\n")
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                transactions.forEach { tx ->
                    val dateStr = dateFormat.format(java.util.Date(tx.timestamp))
                    val escapedCategory = if (tx.category.contains(",")) "\"${tx.category}\"" else tx.category
                    writer.write("${tx.id},$dateStr,${tx.amount},$escapedCategory,${tx.type}\n")
                }
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
