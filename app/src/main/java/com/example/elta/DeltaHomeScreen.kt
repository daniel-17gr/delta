package com.example.elta

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.example.elta.R
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import com.example.elta.CurrencyInfo
import com.example.elta.data.Transaction
import com.example.elta.data.TransactionType
import com.example.elta.data.CustomCurrency
import com.example.elta.ui.theme.LocalDeltaColors
import com.example.elta.ui.theme.LocalAppCurrency
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ArrowDirection {
    UP, DOWN
}

enum class HistoryFilter { ALL, INCOME, EXPENSE }

// Returns a human-readable day label: "TODAY", "YESTERDAY", or "MMM DD"
fun dayLabel(timestamp: Long): String {
    val cal = Calendar.getInstance()
    val today = cal.clone() as Calendar
    cal.timeInMillis = timestamp
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "TODAY"
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) == 1 -> "YESTERDAY"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp)).uppercase()
    }
}

// Returns a sort key (epoch day) for grouping by day
fun epochDay(timestamp: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    return cal.get(Calendar.YEAR) * 10000 + cal.get(Calendar.DAY_OF_YEAR)
}

data class CategoryItem(
    val name: String,
    val type: TransactionType
)

// Load Nothing Dot Matrix Font
val NothingGlyph = FontFamily(
    Font(R.font.nothing_glyph, FontWeight.Normal)
)

val allCategories = listOf(
    // === IN
    CategoryItem("Salary", TransactionType.INCOME),
    CategoryItem("Business", TransactionType.INCOME),
    CategoryItem("Gifts", TransactionType.INCOME),
    CategoryItem("Side Gig", TransactionType.INCOME),
    CategoryItem("Payouts", TransactionType.INCOME), // Investments, refunds
    CategoryItem("Other", TransactionType.INCOME),

    CategoryItem("Borrowed", TransactionType.BORROWED), // From peers
    CategoryItem("Loans", TransactionType.BORROWED),    // From banks

    // === OUT
    CategoryItem("Food", TransactionType.EXPENSE),      // Groceries & dining
    CategoryItem("Transit", TransactionType.EXPENSE),   // Fuel, bus, Uber
    CategoryItem("Bills", TransactionType.EXPENSE),     // Rent, utilities
    CategoryItem("Shopping", TransactionType.EXPENSE),
    CategoryItem("Fun", TransactionType.EXPENSE),       // Entertainment, travel
    CategoryItem("Subs", TransactionType.EXPENSE),       // Keep short for UI
    CategoryItem("Health", TransactionType.EXPENSE),
    CategoryItem("Education", TransactionType.EXPENSE),
    CategoryItem("Insurance", TransactionType.EXPENSE),
    CategoryItem("Other", TransactionType.EXPENSE),

    CategoryItem("Lent", TransactionType.LENT),
    CategoryItem("Loans Out", TransactionType.LENT)
)

// Custom Expression Evaluator supporting Addition, Subtraction, Multiplication, Division, and Parentheses
// Returns null if the expression is mathematically invalid
fun evaluateMath(expr: String): Double? {
    try {
        val sanitized = expr.replace(" ", "")
        if (sanitized.isEmpty()) return null

        class Parser(val str: String) {
            var pos = -1
            var ch = 0

            fun nextChar() {
                pos++
                ch = if (pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else break
                }
                return x
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) {
                        val divisor = parseFactor()
                        x = if (divisor != 0.0) x / divisor else 0.0
                    } else break
                }
                return x
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = this.pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, this.pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }
                return x
            }
        }
        val res = Parser(sanitized).parse()
        return if (res.isNaN() || res.isInfinite()) null else res
    } catch (e: Exception) {
        return null
    }
}

// Preprocessor to detect and inject explicit '*' multiplication symbols for implicit multiplications (e.g. (3+4)6 -> (3+4)*6)
fun preprocessImplicitMultiplication(expr: String): String {
    val sb = StringBuilder()
    for (i in 0 until expr.length) {
        val c = expr[i]
        sb.append(c)
        if (i < expr.length - 1) {
            val next = expr[i + 1]
            val currentIsFactorEnd = c.isDigit() || c == ')' || c == '.'
            val nextIsFactorStart = next == '('
            val currentIsClosedParen = c == ')'
            val nextIsFactorStart2 = next.isDigit() || next == '.'
            if ((currentIsFactorEnd && nextIsFactorStart) || (currentIsClosedParen && nextIsFactorStart2)) {
                sb.append('*')
            }
        }
    }
    return sb.toString()
}

// Spacing formatting for expressions, e.g. "10 + 20 * (3 - 1)"
fun formatExpression(expr: String): String {
    var result = ""
    for (i in 0 until expr.length) {
        val c = expr[i]
        if (c == '+' || c == '-' || c == '*' || c == '/') {
            val isUnary = i == 0 || expr[i - 1] == '+' || expr[i - 1] == '-' || expr[i - 1] == '*' || expr[i - 1] == '/' || expr[i - 1] == '('
            if (isUnary) {
                result += c
            } else {
                result += " $c "
            }
        } else if (c == '(') {
            result += "$c "
        } else if (c == ')') {
            result += " $c"
        } else {
            result += c
        }
    }
    return result.replace("\\s+".toRegex(), " ").trim()
}

@Composable
fun CustomCurrencyEditDialog(
    customCurrency: CustomCurrency?,
    customCurrencies: List<CustomCurrency>,
    onSave: (code: String, symbol: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalDeltaColors.current
    var codeText by remember { mutableStateOf(customCurrency?.code ?: "") }
    var symbolText by remember { mutableStateOf(customCurrency?.symbol ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(260.dp)
            ) {
                Text(
                    text = if (customCurrency == null) "ADD CUSTOM" else "EDIT CUSTOM",
                    fontWeight = FontWeight.Bold,
                    fontFamily = NothingGlyph,
                    fontSize = 16.sp,
                    color = colors.textPrimary,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                BasicTextField(
                    value = codeText,
                    onValueChange = { newValue ->
                        if (newValue.length <= 3 && newValue.all { it.isLetter() }) {
                            codeText = newValue.uppercase()
                        }
                    },
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NothingGlyph
                    ),
                    cursorBrush = SolidColor(colors.textPrimary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.buttonBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            if (codeText.isEmpty()) {
                                Text(
                                    text = "CODE (E.G. USD)",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = NothingGlyph
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))

                BasicTextField(
                    value = symbolText,
                    onValueChange = { newValue ->
                        if (newValue.length < 4) {
                            symbolText = newValue
                        }
                    },
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NothingGlyph
                    ),
                    cursorBrush = SolidColor(colors.textPrimary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.buttonBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            if (symbolText.isEmpty()) {
                                Text(
                                    text = "SYMBOL (E.G. $)",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = NothingGlyph
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = colors.negative,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NothingGlyph
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.buttonBackground)
                            .clickable { onDismiss() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CANCEL",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NothingGlyph
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.textPrimary)
                            .clickable {
                                val codeTrimmed = codeText.trim().uppercase()
                                val symbolTrimmed = symbolText.trim()

                                if (codeTrimmed.length != 3) {
                                    errorMsg = "CODE MUST BE 3 LETTERS"
                                } else if (symbolTrimmed.isEmpty()) {
                                    errorMsg = "SYMBOL CANNOT BE EMPTY"
                                } else if (AppCurrency.entries.any { it.code == codeTrimmed }) {
                                    errorMsg = "DEFAULT CODE ALREADY EXISTS"
                                } else if (customCurrencies.any { it.id != (customCurrency?.id ?: -1) && it.code.uppercase() == codeTrimmed }) {
                                    errorMsg = "CUSTOM CODE ALREADY EXISTS"
                                } else {
                                    onSave(codeTrimmed, symbolTrimmed)
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SAVE",
                            color = colors.background,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NothingGlyph
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CurrencyPickerDialog(
    currentCurrency: CurrencyInfo,
    customCurrencies: List<CustomCurrency>,
    onCurrencySelect: (String) -> Unit,
    onAddCustomCurrency: (code: String, symbol: String) -> Unit,
    onUpdateCustomCurrency: (CustomCurrency) -> Unit,
    onDeleteCustomCurrency: (CustomCurrency) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalDeltaColors.current
    val defaultCurrencies = remember {
        AppCurrency.entries.map {
            CurrencyInfo(code = it.code, symbol = it.symbol, isCustom = false)
        }
    }
    
    var editingCustomCurrency by remember { mutableStateOf<CustomCurrency?>(null) }
    var showAddCustomCurrency by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
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
                    text = "SELECT CURRENCY",
                    fontFamily = NothingGlyph,
                    fontSize = 16.sp,
                    color = colors.textPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "DEFAULTS",
                        fontFamily = NothingGlyph,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp)
                    )
                    
                    defaultCurrencies.forEach { info ->
                        val isSelected = currentCurrency.code.uppercase() == info.code.uppercase() && !currentCurrency.isCustom
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.textPrimary else Color.Transparent)
                                .clickable {
                                    onCurrencySelect(info.code)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (info.code == "NONE") "NONE" else "${info.code} (${info.symbol})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.background else colors.textPrimary,
                                    fontFamily = NothingGlyph
                                )
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.background
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "CUSTOM (${customCurrencies.size}/10)",
                        fontFamily = NothingGlyph,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (customCurrencies.isEmpty()) {
                        Text(
                            text = "NO CUSTOM CURRENCIES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary,
                            fontFamily = NothingGlyph,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                        )
                    } else {
                        customCurrencies.forEach { custom ->
                            val isSelected = currentCurrency.isCustom && currentCurrency.code.uppercase() == custom.code.uppercase()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) colors.textPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) colors.textPrimary else colors.border, RoundedCornerShape(8.dp))
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onCurrencySelect(custom.code)
                                                onDismiss()
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${custom.code} (${custom.symbol})",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary,
                                            fontFamily = NothingGlyph
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "✓",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.buttonBackground)
                                                .clickable { editingCustomCurrency = custom }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "EDIT",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary,
                                                fontFamily = NothingGlyph
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.negative.copy(alpha = 0.15f))
                                                .clickable { onDeleteCustomCurrency(custom) }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "DEL",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.negative,
                                                fontFamily = NothingGlyph
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val canAdd = customCurrencies.size < 10
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (canAdd) colors.textPrimary else colors.buttonBackground)
                        .clickable(enabled = canAdd) { showAddCustomCurrency = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (canAdd) "+ ADD CUSTOM" else "LIMIT REACHED (10/10)",
                        color = if (canAdd) colors.background else colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NothingGlyph
                    )
                }
            }
        }
    }

    if (showAddCustomCurrency) {
        CustomCurrencyEditDialog(
            customCurrency = null,
            customCurrencies = customCurrencies,
            onSave = onAddCustomCurrency,
            onDismiss = { showAddCustomCurrency = false }
        )
    }

    if (editingCustomCurrency != null) {
        CustomCurrencyEditDialog(
            customCurrency = editingCustomCurrency,
            customCurrencies = customCurrencies,
            onSave = { code, symbol ->
                onUpdateCustomCurrency(editingCustomCurrency!!.copy(code = code, symbol = symbol))
            },
            onDismiss = { editingCustomCurrency = null }
        )
    }
}

@Composable
fun TransactionOptionsDialog(
    transaction: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalDeltaColors.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(260.dp)
            ) {
                Text(
                        fontWeight = FontWeight.Bold,
                    text = "TRANSACTION OPTIONS",
                    fontFamily = NothingGlyph,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${transaction.category} • ${if (transaction.amount >= 0) "+" else ""}${String.format("%,.2f", transaction.amount)}",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                // EDIT Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.buttonBackground)
                        .clickable {
                            onEdit()
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EDIT",
                        fontFamily = NothingGlyph,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // DELETE Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF2D00)) // Signature Nothing red
                        .clickable {
                            onDelete()
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DELETE",
                        fontFamily = NothingGlyph,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // CANCEL Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = "CANCEL",
                        fontFamily = NothingGlyph,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DeltaHomeScreen(
    transactionList: List<Transaction>,
    netBalance: Double,
    totalIncome: Double,
    totalExpense: Double,
    customCurrencies: List<CustomCurrency>,
    onAddTransaction: (amount: Double, category: String, type: TransactionType) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onUpdateTransaction: (Transaction) -> Unit,
    onAddCustomCurrency: (code: String, symbol: String) -> Unit,
    onUpdateCustomCurrency: (CustomCurrency) -> Unit,
    onDeleteCustomCurrency: (CustomCurrency) -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectCurrency: (String) -> Unit
) {
    val colors = LocalDeltaColors.current
    val currentCurrency = LocalAppCurrency.current
    val isDark = colors.background == Color.Black

    val historyListState = rememberLazyListState()
    val isCollapsed by remember {
        derivedStateOf {
            historyListState.firstVisibleItemIndex > 0 || historyListState.firstVisibleItemScrollOffset > 50
        }
    }

    // Quick logging state
    var isEditing by remember { mutableStateOf(false) }
    var typedAmountString by remember { mutableStateOf("0") }
    var tempCategory by remember { mutableStateOf<String?>(null) }
    var tempType by remember { mutableStateOf(TransactionType.INCOME) }
    var tempFlowType by remember { mutableStateOf(TransactionType.INCOME) }
    
    // Validation error state
    var validationError by remember { mutableStateOf<String?>(null) }
    
    // Currency picker dialog visibility
    var showCurrencyPicker by remember { mutableStateOf(false) }

    // Edit/Delete state variables
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showOptionsDialogFor by remember { mutableStateOf<Transaction?>(null) }
    var showAmounts by remember { mutableStateOf(true) }

    // Intercept back key to close editing pane if open
    BackHandler(enabled = isEditing) {
        isEditing = false
        editingTransaction = null
        validationError = null
    }

    // Coroutine scope for scale animations
    val coroutineScope = rememberCoroutineScope()

    // Bounce animation triggers for balances
    val netScale = remember { Animatable(1f) }
    val incomeScale = remember { Animatable(1f) }
    val expenseScale = remember { Animatable(1f) }

    var showSavedAnimation by remember { mutableStateOf(false) }
    var savedAnimationAmount by remember { mutableStateOf("") }
    var checkButtonPosition by remember { mutableStateOf(Offset.Zero) }

    // netBalance, totalIncome, totalExpense are now pre-computed by SQLite and passed in as parameters

    // Calculate total net change (based on real data)
    val monthlyChange = netBalance

    val haptic = LocalHapticFeedback.current

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            currentCurrency = currentCurrency,
            customCurrencies = customCurrencies,
            onCurrencySelect = onSelectCurrency,
            onAddCustomCurrency = onAddCustomCurrency,
            onUpdateCustomCurrency = onUpdateCustomCurrency,
            onDeleteCustomCurrency = onDeleteCustomCurrency,
            onDismiss = { showCurrencyPicker = false }
        )
    }

    if (showOptionsDialogFor != null) {
        TransactionOptionsDialog(
            transaction = showOptionsDialogFor!!,
            onEdit = {
                val tx = showOptionsDialogFor!!
                editingTransaction = tx
                typedAmountString = if (kotlin.math.abs(tx.amount) % 1.0 == 0.0) {
                    kotlin.math.abs(tx.amount).toLong().toString()
                } else {
                    String.format(Locale.US, "%.2f", kotlin.math.abs(tx.amount))
                }
                tempFlowType = if (tx.type == TransactionType.INCOME || tx.type == TransactionType.BORROWED) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                }
                tempCategory = tx.category
                tempType = tx.type
                isEditing = true
                validationError = null
            },
            onDelete = {
                onDeleteTransaction(showOptionsDialogFor!!)
            },
            onDismiss = { showOptionsDialogFor = null }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // Adaptive horizontal split layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Left Column: Overview Details
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = stringResource(R.string.app_name),
                            fontFamily = NothingGlyph,
                            fontSize = 28.sp,
                            color = colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showCurrencyPicker = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.currencies),
                                    contentDescription = "Currency selector",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showAmounts = !showAmounts
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = if (showAmounts) R.drawable.eye else R.drawable.eye_off),
                                    contentDescription = "Toggle Show/Hide Amounts",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleTheme()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = if (isDark) R.drawable.sun else R.drawable.moon),
                                    contentDescription = "Toggle Theme",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.textPrimary)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onNavigateToAnalytics()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.analytics),
                                    contentDescription = "Navigate to analytics",
                                    tint = colors.background,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Divider
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))

                    // Net Balance Block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .graphicsLayer {
                                scaleX = netScale.value
                                scaleY = netScale.value
                            }
                    ) {
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = "NET",
                            fontFamily = NothingGlyph,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                        fontWeight = FontWeight.Bold,
                            text = if (showAmounts) String.format("${currentCurrency.symbol}%,.2f", netBalance) else "${currentCurrency.symbol}••••",
                            fontFamily = NothingGlyph,
                            fontSize = 36.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${if (monthlyChange >= 0) "+" else "-"}${currentCurrency.symbol}${if (showAmounts) String.format("%,.0f", kotlin.math.abs(monthlyChange)) else "••••"} This Month",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.positive
                        )
                    }

                    // Divider
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))

                    // Income / Expenses Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                                .graphicsLayer {
                                    scaleX = incomeScale.value
                                    scaleY = incomeScale.value
                                }
                        ) {
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = "INCOME",
                                fontFamily = NothingGlyph,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = if (showAmounts) String.format("${currentCurrency.symbol}%,.2f", totalIncome) else "${currentCurrency.symbol}••••",
                                fontFamily = NothingGlyph,
                                fontSize = 18.sp,
                                color = colors.textPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(colors.border)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                                .graphicsLayer {
                                    scaleX = expenseScale.value
                                    scaleY = expenseScale.value
                                }
                        ) {
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = "EXPENSES",
                                fontFamily = NothingGlyph,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = if (showAmounts) String.format("${currentCurrency.symbol}%,.2f", totalExpense) else "${currentCurrency.symbol}••••",
                                fontFamily = NothingGlyph,
                                fontSize = 18.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))
                Spacer(modifier = Modifier.width(1.dp).fillMaxHeight().background(colors.border))
                Spacer(modifier = Modifier.width(24.dp))

                // Right Column: Transactions List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 180.dp)
                ) {
                    TransactionHistoryList(
                        transactions = transactionList,
                        onTransactionClick = { showOptionsDialogFor = it },
                        showAmounts = showAmounts,
                        listState = historyListState,
                        bottomPadding = 96
                    )
                }
            }
        } else {
            // Portrait Layout: Stacked vertically
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // 1. Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = "Delta",
                        fontFamily = NothingGlyph,
                        fontSize = 28.sp,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCurrencyPicker = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.currencies),
                                contentDescription = "Currency selector",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAmounts = !showAmounts
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = if (showAmounts) R.drawable.eye else R.drawable.eye_off),
                                contentDescription = "Toggle Show/Hide Amounts",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleTheme()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = if (isDark) R.drawable.sun else R.drawable.moon),
                                contentDescription = "Toggle Theme",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.textPrimary)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onNavigateToAnalytics()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.analytics),
                                contentDescription = "Navigate to analytics",
                                tint = colors.background,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Divider
                Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))

                AnimatedVisibility(
                    visible = !isCollapsed,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        // 2. Net Balance Block
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                                .graphicsLayer {
                                    scaleX = netScale.value
                                    scaleY = netScale.value
                                }
                        ) {
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = "NET",
                                fontFamily = NothingGlyph,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = if (showAmounts) String.format("${currentCurrency.symbol}%,.2f", netBalance) else "${currentCurrency.symbol}••••",
                                fontFamily = NothingGlyph,
                                fontSize = 44.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${if (monthlyChange >= 0) "+" else "-"}${currentCurrency.symbol}${if (showAmounts) String.format("%,.0f", kotlin.math.abs(monthlyChange)) else "••••"} This Month",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.positive
                            )
                        }

                        // Divider
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))

                        // 3. Income / Expenses Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                                    .graphicsLayer {
                                        scaleX = incomeScale.value
                                        scaleY = incomeScale.value
                                    }
                            ) {
                                Text(
                        fontWeight = FontWeight.Bold,
                                    text = "INCOME",
                                    fontFamily = NothingGlyph,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                        fontWeight = FontWeight.Bold,
                                    text = if (showAmounts) String.format("${currentCurrency.symbol}%,.2f", totalIncome) else "${currentCurrency.symbol}••••",
                                    fontFamily = NothingGlyph,
                                    fontSize = 20.sp,
                                    color = colors.textPrimary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(colors.border)
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp)
                                    .graphicsLayer {
                                        scaleX = expenseScale.value
                                        scaleY = expenseScale.value
                                    }
                            ) {
                                Text(
                        fontWeight = FontWeight.Bold,
                                    text = "EXPENSES",
                                    fontFamily = NothingGlyph,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                        fontWeight = FontWeight.Bold,
                                    text = if (showAmounts) String.format("${currentCurrency.symbol}%,.2f", totalExpense) else "${currentCurrency.symbol}••••",
                                    fontFamily = NothingGlyph,
                                    fontSize = 20.sp,
                                    color = colors.textPrimary
                                )
                            }
                        }

                        // Divider
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))
                    }
                }

                // 4. Transactions List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    TransactionHistoryList(
                        transactions = transactionList,
                        onTransactionClick = { showOptionsDialogFor = it },
                        showAmounts = showAmounts,
                        listState = historyListState,
                        bottomPadding = 96
                    )
                }
            }
        }

        // Scrim overlay to prevent clicking content underneath while editing
        if (isEditing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Consume click events without closing. Only close icon button can dismiss.
                    }
            )
        }

        // Floating Keyboard Panel (Centered or Left-aligned side panel in landscape)
        val panelAlignment = if (isLandscape) Alignment.CenterStart else Alignment.BottomCenter
        val panelPadding = if (isLandscape) Modifier.padding(start = 40.dp) else Modifier.padding(horizontal = 24.dp).padding(bottom = 148.dp)

        AnimatedVisibility(
            visible = isEditing,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(panelAlignment)
                .then(panelPadding)
        ) {
            FloatingLoggerPanel(
                isLandscape = isLandscape,
                amountString = typedAmountString,
                selectedCategory = tempCategory,
                flowType = tempFlowType,
                validationError = validationError,
                onCategorySelect = { category, type ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    tempCategory = category
                    tempType = type
                    validationError = null
                },
                onKeyPress = { char ->
                    validationError = null
                    if (typedAmountString.length < 24) {
                        if (typedAmountString == "0" && char != "." && char != "+" && char != "-" && char != "*" && char != "/") {
                            typedAmountString = char
                        } else {
                            typedAmountString += char
                        }
                    }
                },
                onDelete = {
                    validationError = null
                    if (typedAmountString.isNotEmpty()) {
                        typedAmountString = if (typedAmountString.length == 1) {
                            "0"
                        } else {
                            typedAmountString.dropLast(1)
                        }
                    }
                },
                onClear = {
                    validationError = null
                    typedAmountString = "0"
                },
                onEvaluate = {
                    validationError = null
                    val processedExpr = preprocessImplicitMultiplication(typedAmountString)
                    val evaluated = evaluateMath(processedExpr)
                    if (evaluated != null) {
                        typedAmountString = if (evaluated % 1.0 == 0.0) {
                            evaluated.toLong().toString()
                        } else {
                            String.format("%.2f", evaluated)
                        }
                    } else {
                        validationError = "Invalid expression"
                    }
                }
            )
        }

        // 5. Adaptive Floating Dock personal Finance Logger
        val dockAlignment = if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
        val dockPadding = if (isLandscape) Modifier.padding(end = 24.dp) else Modifier.padding(bottom = 24.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(dockPadding),
            contentAlignment = dockAlignment
        ) {
            AdaptiveTwoStateDock(
                isLandscape = isLandscape,
                isEditing = isEditing,
                onUpClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    typedAmountString = "0"
                    tempFlowType = TransactionType.INCOME
                    tempCategory = null
                    tempType = TransactionType.INCOME
                    isEditing = true
                    editingTransaction = null
                    validationError = null
                },
                onDownClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    typedAmountString = "0"
                    tempFlowType = TransactionType.EXPENSE
                    tempCategory = null
                    tempType = TransactionType.EXPENSE
                    isEditing = true
                    editingTransaction = null
                    validationError = null
                },
                onCloseClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isEditing = false
                    editingTransaction = null
                    validationError = null
                },
                onCheckClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    
                    val processedExpr = preprocessImplicitMultiplication(typedAmountString)
                    val evaluatedAmount = evaluateMath(processedExpr)
                    
                    if (tempCategory == null) {
                        validationError = "Please select a category"
                    } else if (evaluatedAmount == null) {
                        validationError = "Invalid expression"
                    } else if (evaluatedAmount < 0) {
                        validationError = "Amount cannot be negative"
                    } else {
                        val parsedAmount = evaluatedAmount
                        if (parsedAmount <= 0) {
                            validationError = "Amount must be > 0"
                        } else if (parsedAmount > 99999999.99) {
                            validationError = "Amount too large"
                        } else {
                            // Persist to Room via callback
                            val editTx = editingTransaction
                            if (editTx != null) {
                                onUpdateTransaction(editTx.copy(amount = parsedAmount, category = tempCategory!!, type = tempType))
                                editingTransaction = null
                            } else {
                                onAddTransaction(parsedAmount, tempCategory!!, tempType)
                            }

                            // Playful spring wiggles on updated totals
                            val bounceSpec = spring<Float>(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)
                            coroutineScope.launch {
                                netScale.animateTo(1.15f, bounceSpec)
                                netScale.animateTo(1.0f, bounceSpec)
                            }
                            if (tempType == TransactionType.INCOME || tempType == TransactionType.BORROWED) {
                                coroutineScope.launch {
                                    incomeScale.animateTo(1.2f, bounceSpec)
                                    incomeScale.animateTo(1.0f, bounceSpec)
                                }
                            } else {
                                coroutineScope.launch {
                                    expenseScale.animateTo(1.2f, bounceSpec)
                                    expenseScale.animateTo(1.0f, bounceSpec)
                                }
                            }

                            // Capture details for Nothing Phone saving animation
                            val amountPrefix = if (tempType == TransactionType.INCOME || tempType == TransactionType.BORROWED) "+" else "-"
                            savedAnimationAmount = if (showAmounts) {
                                String.format(Locale.US, "%s%s%,.2f", amountPrefix, currentCurrency.symbol, parsedAmount)
                            } else {
                                "%s%s••••".format(amountPrefix, currentCurrency.symbol)
                            }
                            showSavedAnimation = true

                            isEditing = false
                            validationError = null
                        }
                    }
                },
                onCheckButtonPositioned = { checkButtonPosition = it }
            )
        }

        NothingGlyphSavingAnimation(
            trigger = showSavedAnimation,
            startOffset = checkButtonPosition,
            amountText = savedAnimationAmount,
            onFinished = { showSavedAnimation = false }
        )
    }
}

@Composable
fun TransactionRow(
    tx: Transaction,
    showAmounts: Boolean = true,
    onClick: () -> Unit = {}
) {
    val colors = LocalDeltaColors.current
    val timeStr = remember(tx.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tx.timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: category + time
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.category,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                if (tx.type == TransactionType.BORROWED || tx.type == TransactionType.LENT) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (tx.type == TransactionType.BORROWED) "⇩" else "⇧",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                        fontWeight = FontWeight.Bold,
                text = timeStr,
                fontFamily = NothingGlyph,
                fontSize = 11.sp,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )
        }
        // Right: amount
        Text(
            text = if (showAmounts) {
                when (tx.type) {
                    TransactionType.BORROWED -> String.format("+%,.2f", tx.amount)
                    TransactionType.LENT -> String.format("-%,.2f", kotlin.math.abs(tx.amount))
                    else -> "${if (tx.amount >= 0) "+" else "-"}${String.format("%,.2f", kotlin.math.abs(tx.amount))}"
                }
            } else {
                when (tx.type) {
                    TransactionType.BORROWED -> "+••••"
                    TransactionType.LENT -> "-••••"
                    else -> "${if (tx.amount >= 0) "+" else "-"}••••"
                }
            },
            fontFamily = NothingGlyph,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = when (tx.type) {
                TransactionType.INCOME -> colors.positive
                TransactionType.EXPENSE -> colors.negative
                else -> colors.textPrimary
            }
        )
    }
}

// ─────────────────────────────────────────────
// History List: filter chips + sticky headers + go-to jump bar
// ─────────────────────────────────────────────
@Composable
fun TransactionHistoryList(
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
    showAmounts: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    bottomPadding: Int = 120
) {
    val colors = LocalDeltaColors.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var activeFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var showJumpBar by remember { mutableStateOf(false) }

    // Apply filter
    val filtered = remember(transactions, activeFilter) {
        when (activeFilter) {
            HistoryFilter.ALL -> transactions
            HistoryFilter.INCOME -> transactions.filter {
                it.type == TransactionType.INCOME || it.type == TransactionType.BORROWED
            }
            HistoryFilter.EXPENSE -> transactions.filter {
                it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT
            }
        }
    }

    // Group by day (newest first — DAO returns DESC order)
    val grouped = remember(filtered) {
        filtered.groupBy { epochDay(it.timestamp) }
            .entries
            .sortedByDescending { it.key }
    }

    // Build a flat index map: dateKey -> index of its header inside the LazyColumn
    // Structure: [topPadding item(0)] [header(1)] [row(2)..row(n)] [header(n+1)]...
    val headerIndexMap = remember(grouped) {
        val map = mutableMapOf<Int, Int>()
        var idx = 1 // item 0 = top spacer
        grouped.forEach { (dateKey, txList) ->
            map[dateKey] = idx
            idx += 1 + txList.size // header + rows
        }
        map
    }

    // Visible date keys for jump bar — derived from scroll position
    val visibleDateLabels by remember(grouped, headerIndexMap) {
        derivedStateOf {
            grouped.map { (key, _) ->
                key to dayLabel(// reconstruct a timestamp from the key is complex — use the group's first tx
                    grouped.firstOrNull { it.key == key }?.value?.firstOrNull()?.timestamp ?: 0L
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Filter chips row ──
            if (isLandscape) {
                // Symmetrical FlowRow layout for landscape/horizontal/tablet mode
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        val isActive = filter == activeFilter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isActive) colors.textPrimary else colors.buttonBackground)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeFilter = filter
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = filter.name,
                                fontFamily = NothingGlyph,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = if (isActive) colors.background else colors.textSecondary
                            )
                        }
                    }

                    // "Go to" toggle button
                    if (grouped.size > 1) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (showJumpBar) colors.textPrimary else colors.buttonBackground)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showJumpBar = !showJumpBar
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = "GO TO",
                                fontFamily = NothingGlyph,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = if (showJumpBar) colors.background else colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                // Row layout with space-between spacer for portrait mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        val isActive = filter == activeFilter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isActive) colors.textPrimary else colors.buttonBackground)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeFilter = filter
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = filter.name,
                                fontFamily = NothingGlyph,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = if (isActive) colors.background else colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // "Go to" toggle button
                    if (grouped.size > 1) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (showJumpBar) colors.textPrimary else colors.buttonBackground)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showJumpBar = !showJumpBar
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = "GO TO",
                                fontFamily = NothingGlyph,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = if (showJumpBar) colors.background else colors.textSecondary
                            )
                        }
                    }
                }
            }

            // ── Animated jump bar ──
            AnimatedVisibility(
                visible = showJumpBar,
                enter = fadeIn() + scaleIn(initialScale = 0.92f),
                exit = fadeOut() + scaleOut(targetScale = 0.92f)
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleDateLabels, key = { it.first }) { (dateKey, label) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val targetIdx = headerIndexMap[dateKey] ?: return@clickable
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(index = targetIdx)
                                    }
                                    showJumpBar = false
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                        fontWeight = FontWeight.Bold,
                                text = label,
                                fontFamily = NothingGlyph,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }

            // ── Grouped transaction list ──
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = "NO TRANSACTIONS",
                        fontFamily = NothingGlyph,
                        fontSize = 13.sp,
                        letterSpacing = 2.sp,
                        color = colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    grouped.forEach { (_, txList) ->
                        val label = dayLabel(txList.first().timestamp)
                        stickyHeader(key = "header_${epochDay(txList.first().timestamp)}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.background)
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                        fontWeight = FontWeight.Bold,
                                    text = label,
                                    fontFamily = NothingGlyph,
                                    fontSize = 11.sp,
                                    letterSpacing = 2.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                        items(txList, key = { it.id }) { tx ->
                            TransactionRow(tx, showAmounts = showAmounts, onClick = { onTransactionClick(tx) })
                        }
                    }
                    item { Spacer(modifier = Modifier.height(bottomPadding.dp)) }
                }
            }
        }
    }
}

@Composable
fun FloatingLoggerPanel(
    isLandscape: Boolean,
    amountString: String,
    selectedCategory: String?,
    flowType: TransactionType,
    validationError: String?,
    onCategorySelect: (String, TransactionType) -> Unit,
    onKeyPress: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onEvaluate: () -> Unit
) {
    val colors = LocalDeltaColors.current
    val processedExpr = preprocessImplicitMultiplication(amountString)
    val evaluatedValue = evaluateMath(processedExpr)

    // Filter categories dynamically based on active flow type (Income vs Expense)
    val filteredCategories = if (flowType == TransactionType.INCOME) {
        allCategories.filter { it.type == TransactionType.INCOME || it.type == TransactionType.BORROWED }
    } else {
        allCategories.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.LENT }
    }

    val panelWidthModifier = if (isLandscape) Modifier.width(500.dp) else Modifier.fillMaxWidth()

    Column(
        modifier = panelWidthModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLandscape) {
            // Adaptive Landscape/Tablet: Categories card on Left, Calculator card on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side Card: Displays (Amount & Live Result) + Categories
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(24.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Live formatted expression display
                        Text(
                            fontWeight = FontWeight.Bold,
                            text = formatExpression(amountString),
                            fontFamily = NothingGlyph,
                            fontSize = 16.sp,
                            color = colors.textPrimary.copy(alpha = 0.5f),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val displayValueText = when {
                            validationError != null -> validationError!!
                            evaluatedValue == null -> "Invalid"
                            else -> String.format("%,.2f", evaluatedValue)
                        }
                        val displayValueColor = when {
                            validationError != null || evaluatedValue == null -> colors.negative
                            flowType == TransactionType.INCOME -> colors.positive
                            else -> colors.negative
                        }

                        val displayFontSize = if (validationError != null) 14.sp else 24.sp
                        val displayMaxLines = if (validationError != null) 2 else 1
                        
                        // Formatted evaluated output (no currency symbols)
                        Text(
                            fontWeight = FontWeight.Bold,
                            text = displayValueText,
                            fontFamily = NothingGlyph,
                            fontSize = displayFontSize,
                            color = displayValueColor,
                            maxLines = displayMaxLines,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Categories Card interior (don't separate borrow and lent)
                        Text(
                            fontWeight = FontWeight.Bold,
                            text = "CATEGORIES",
                            fontFamily = NothingGlyph,
                            fontSize = 9.sp,
                            color = colors.textSecondary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 90.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredCategories.forEach { cat ->
                                val isSelected = selectedCategory == cat.name
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) colors.textPrimary else colors.buttonBackground)
                                        .clickable {
                                            onCategorySelect(cat.name, cat.type)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat.name.uppercase(),
                                        fontSize = 9.sp,
                                        fontFamily = NothingGlyph,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.background else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Side Card: 4x5 Calculator Keyboard Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val keys = listOf(
                        listOf("(", ")", "⌫", "/"),
                        listOf("7", "8", "9", "*"),
                        listOf("4", "5", "6", "-"),
                        listOf("1", "2", "3", "+"),
                        listOf(".", "0", "CLEAR")
                    )
                    keys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                val weight = if (key == "CLEAR") 2f else 1f
                                val isHighlighted = key in listOf("(", ")", "⌫", "/", "*", "-", "+", "CLEAR")
                                KeypadButton(
                                    label = key,
                                    onClick = {
                                        when (key) {
                                            "⌫" -> onDelete()
                                            "CLEAR" -> onClear()
                                            else -> onKeyPress(key)
                                        }
                                    },
                                    isHighlighted = isHighlighted,
                                    modifier = Modifier.weight(weight)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Portrait Layout: Stacked vertically
            
            // Card 1: Categories Card (exterior to the calculator)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            ) {
                Text(
                    fontWeight = FontWeight.Bold,
                    text = "CATEGORIES",
                    fontFamily = NothingGlyph,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 110.dp) // Scrollable to prevent breaking the UI on small screens
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredCategories.forEach { cat ->
                            val isSelected = selectedCategory == cat.name
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) colors.textPrimary else colors.buttonBackground)
                                    .clickable {
                                        onCategorySelect(cat.name, cat.type)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat.name.uppercase(),
                                    fontSize = 11.sp,
                                    fontFamily = NothingGlyph,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.background else colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Calculator Card (Keypad, displays)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Expression and Amount Display
                Text(
                    fontWeight = FontWeight.Bold,
                    text = formatExpression(amountString),
                    fontFamily = NothingGlyph,
                    fontSize = 20.sp,
                    color = colors.textPrimary.copy(alpha = 0.5f),
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                val displayValueText = when {
                    validationError != null -> validationError!!
                    evaluatedValue == null -> "Invalid"
                    else -> String.format("%,.2f", evaluatedValue)
                }
                val displayValueColor = when {
                    validationError != null || evaluatedValue == null -> colors.negative
                    flowType == TransactionType.INCOME -> colors.positive
                    else -> colors.negative
                }
                
                val displayFontSize = if (validationError != null) 16.sp else 32.sp
                val displayMaxLines = if (validationError != null) 2 else 1
                
                Text(
                    fontWeight = FontWeight.Bold,
                    text = displayValueText,
                    fontFamily = NothingGlyph,
                    fontSize = displayFontSize,
                    color = displayValueColor,
                    maxLines = displayMaxLines,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4x5 Keyboard Grid
                val keys = listOf(
                    listOf("(", ")", "⌫", "/"),
                    listOf("7", "8", "9", "*"),
                    listOf("4", "5", "6", "-"),
                    listOf("1", "2", "3", "+"),
                    listOf(".", "0", "CLEAR")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                val weight = if (key == "CLEAR") 2f else 1f
                                val isHighlighted = key in listOf("(", ")", "⌫", "/", "*", "-", "+", "CLEAR")
                                KeypadButton(
                                    label = key,
                                    onClick = {
                                        when (key) {
                                            "⌫" -> onDelete()
                                            "CLEAR" -> onClear()
                                            else -> onKeyPress(key)
                                        }
                                    },
                                    isHighlighted = isHighlighted,
                                    modifier = Modifier.weight(weight)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    val colors = LocalDeltaColors.current
    val haptic = LocalHapticFeedback.current
    val isDark = colors.background == Color.Black
    val bg = if (isHighlighted) {
        if (isDark) Color(0xFF2C2C2C) else Color(0xFFD8D8D8)
    } else {
        colors.buttonBackground
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable {
                // Strong haptic ticks on all calculator buttons, including operators, backspace, and parens
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AdaptiveTwoStateDock(
    isLandscape: Boolean,
    isEditing: Boolean,
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
    onCloseClick: () -> Unit,
    onCheckClick: () -> Unit,
    onCheckButtonPositioned: (Offset) -> Unit = {}
) {
    AnimatedContent(
        targetState = isEditing,
        transitionSpec = {
            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
             scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow))).togetherWith(
             fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
             scaleOut(targetScale = 0.7f)
            )
        },
        label = "dockStateMorph"
    ) { editing ->
        if (editing) {
            // State 2: Close and Check (Nothing style: solid white/charcoal buttons, no borders)
            if (isLandscape) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    CloseSignButton(onClick = onCloseClick)
                    Spacer(modifier = Modifier.height(24.dp))
                    CheckSignButton(
                        onClick = onCheckClick,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            val size = coordinates.size
                            onCheckButtonPositioned(
                                Offset(
                                    x = position.x + size.width / 2f,
                                    y = position.y + size.height / 2f
                                )
                            )
                        }
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    CloseSignButton(onClick = onCloseClick)
                    Spacer(modifier = Modifier.width(24.dp))
                    CheckSignButton(
                        onClick = onCheckClick,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            val size = coordinates.size
                            onCheckButtonPositioned(
                                Offset(
                                    x = position.x + size.width / 2f,
                                    y = position.y + size.height / 2f
                                )
                            )
                        }
                    )
                }
            }
        } else {
            // State 1: Up and Down buttons
            if (isLandscape) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    BlockArrowButton(direction = ArrowDirection.UP, onClick = onUpClick)
                    Spacer(modifier = Modifier.height(24.dp))
                    BlockArrowButton(direction = ArrowDirection.DOWN, onClick = onDownClick)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    BlockArrowButton(direction = ArrowDirection.DOWN, onClick = onDownClick)
                    Spacer(modifier = Modifier.width(24.dp))
                    BlockArrowButton(direction = ArrowDirection.UP, onClick = onUpClick)
                }
            }
        }
    }
}

@Composable
fun BlockArrowButton(
    direction: ArrowDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDeltaColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "arrowScale"
    )

    val drawableId = if (direction == ArrowDirection.UP) R.drawable.formkit_up else R.drawable.formkit_down

    // Nothing Design style: solid high-contrast buttons, no borders, charcoal/white fill
    Box(
        modifier = modifier
            .size(width = 136.dp, height = 80.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.buttonBackground) // Solid charcoal background
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = drawableId),
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun CloseSignButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDeltaColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium),
        label = "closeScale"
    )

    Box(
        modifier = modifier
            .size(width = 136.dp, height = 80.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.buttonBackground) // Solid charcoal
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.close),
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Signature Nothing Red Dot indicator
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFFF2D00))
            )
        }
    }
}

@Composable
fun CheckSignButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDeltaColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium),
        label = "checkScale"
    )

    Box(
        modifier = modifier
            .size(width = 136.dp, height = 80.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.checkBackground) // High contrast white check button
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.check),
            contentDescription = null,
            tint = colors.checkContent, // Black icon on white background
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun NothingGlyphSavingAnimation(
    trigger: Boolean,
    startOffset: Offset,
    amountText: String,
    onFinished: () -> Unit
) {
    if (!trigger) return

    val animProgress = remember { Animatable(0f) }
    val colors = LocalDeltaColors.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(trigger) {
        // Trigger rhythmic tactile clicks to simulate wave ripples propagating physically
        launch {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(220)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(220)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1500,
                easing = LinearOutSlowInEasing
            )
        )
        onFinished()
    }

    val progress = animProgress.value

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Dotted ripple circles expanding from startOffset (save button center)
        // First wave: covers massive screen area
        val wave1Progress = progress
        if (wave1Progress > 0f) {
            val radius = wave1Progress * 1200.dp.toPx()
            val alpha = (1f - wave1Progress) * 0.45f
            drawCircle(
                color = colors.textPrimary.copy(alpha = alpha),
                radius = radius,
                center = startOffset,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(8.dp.toPx(), 10.dp.toPx()),
                        0f
                    )
                )
            )
        }

        // Second wave (delayed, slower start)
        val wave2Progress = ((progress - 0.25f).coerceAtLeast(0f) / 0.75f)
        if (wave2Progress > 0f) {
            val radius = wave2Progress * 1400.dp.toPx()
            val alpha = (1f - wave2Progress) * 0.3f
            drawCircle(
                color = colors.textPrimary.copy(alpha = alpha),
                radius = radius,
                center = startOffset,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 8.dp.toPx()),
                        0f
                    )
                )
            )
        }

        // Third wave (delayed, extra depth)
        val wave3Progress = ((progress - 0.5f).coerceAtLeast(0f) / 0.5f)
        if (wave3Progress > 0f) {
            val radius = wave3Progress * 1600.dp.toPx()
            val alpha = (1f - wave3Progress) * 0.15f
            drawCircle(
                color = colors.textPrimary.copy(alpha = alpha),
                radius = radius,
                center = startOffset,
                style = Stroke(
                    width = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 6.dp.toPx()),
                        0f
                    )
                )
            )
        }
    }

    // 2. Dot-Matrix Floating Saved Text
    val textAlpha by animateFloatAsState(
        targetValue = if (progress < 0.85f) 0.95f else 0.0f,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
    )
    val textYOffset = (progress * -160.dp.value)

    if (textAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = textYOffset
                    alpha = textAlpha
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TRANSACTION SAVED",
                    fontFamily = NothingGlyph,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = amountText,
                    fontFamily = NothingGlyph,
                    fontSize = 32.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
