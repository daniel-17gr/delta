package com.example.elta

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elta.data.Transaction
import com.example.elta.data.TransactionType
import com.example.elta.ui.theme.LocalAppCurrency
import com.example.elta.ui.theme.LocalDeltaColors
import com.example.elta.ui.theme.NothingGlyph
import com.example.elta.ui.theme.DeltaShapes
import com.example.elta.ui.theme.DeltaDialog
import com.example.elta.ui.theme.DeltaButton
import com.example.elta.ui.theme.DeltaButtonStyle
import androidx.compose.ui.text.font.FontFamily
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DeltaTrashScreen(
    deletedTransactions: List<Transaction>,
    syncStatus: com.example.elta.data.SyncStatus = com.example.elta.data.SyncStatus.IDLE,
    isSyncPaused: Boolean = false,
    onRestore: (List<String>) -> Unit,
    onDeletePermanently: (List<String>) -> Unit,
    onClearAll: () -> Unit,
    onSync: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val colors = LocalDeltaColors.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val syncIconRes = when {
        isSyncPaused -> R.drawable.ic_cloud_alert
        syncStatus == com.example.elta.data.SyncStatus.SYNCING -> R.drawable.ic_cloud_sync
        syncStatus == com.example.elta.data.SyncStatus.SUCCESS -> R.drawable.ic_cloud_check
        syncStatus == com.example.elta.data.SyncStatus.ERROR -> R.drawable.ic_cloud_alert
        else -> R.drawable.ic_cloud_backup
    }
    val syncIconTint = when {
        isSyncPaused -> colors.textSecondary.copy(alpha = 0.5f)
        syncStatus == com.example.elta.data.SyncStatus.SUCCESS -> colors.positive
        syncStatus == com.example.elta.data.SyncStatus.ERROR -> colors.negative
        else -> colors.textPrimary
    }
    val nothingRed = Color(0xFFFF2D00)
    val currency = LocalAppCurrency.current

    // Dialog state management
    var activeTransactionForOptions by remember { mutableStateOf<Transaction?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Group action dialog state management
    var groupActionTitle by remember { mutableStateOf("") }
    var groupActionMessage by remember { mutableStateOf("") }
    var groupActionLabel by remember { mutableStateOf("") }
    var groupActionIsDelete by remember { mutableStateOf(false) }
    var groupActionUuids by remember { mutableStateOf<List<String>>(emptyList()) }
    var showGroupActionDialog by remember { mutableStateOf(false) }

    // Group transactions by original transaction timestamp
    val grouped = remember(deletedTransactions) {
        deletedTransactions.groupBy { epochDay(it.timestamp) }
            .entries
            .sortedByDescending { it.key }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // ─── Top Bar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(DeltaShapes.Button)
                            .background(colors.buttonBackground)
                            .border(1.dp, colors.border, DeltaShapes.Button)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigateBack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "TRASH",
                        fontSize = 24.sp,
                        fontFamily = NothingGlyph,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                    
                    if (deletedTransactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(DeltaShapes.Badge)
                                .border(1.dp, nothingRed, DeltaShapes.Badge)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${deletedTransactions.size}",
                                fontSize = 10.sp,
                                fontFamily = NothingGlyph,
                                fontWeight = FontWeight.Bold,
                                color = nothingRed
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(DeltaShapes.Button)
                            .background(colors.buttonBackground)
                            .border(1.dp, colors.border, DeltaShapes.Button)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSync()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = syncIconRes),
                            contentDescription = "Sync Data",
                            tint = syncIconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (deletedTransactions.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(DeltaShapes.Button)
                                .background(nothingRed.copy(alpha = 0.1f))
                                .border(1.dp, nothingRed.copy(alpha = 0.3f), DeltaShapes.Button)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showClearDialog = true
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "CLEAR ALL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = nothingRed
                            )
                        }
                    }
                }
            }

            // Divider matching Nothing style
            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                if (deletedTransactions.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_trash),
                            contentDescription = null,
                            tint = colors.textSecondary.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "TRASH IS EMPTY",
                            fontSize = 13.sp,
                            fontFamily = NothingGlyph,
                            color = colors.textSecondary.copy(alpha = 0.5f),
                            letterSpacing = 2.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                    ) {
                        grouped.forEach { (_, txList) ->
                            val label = dayLabel(txList.first().timestamp)
                            
                            // Sticky Date Header with Group Action Icon Buttons
                            item(key = "header_${epochDay(txList.first().timestamp)}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.background)
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = NothingGlyph,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = colors.textSecondary
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    groupActionTitle = "RESTORE ALL?"
                                                    groupActionMessage = "RESTORE ALL ${txList.size} TRANSACTIONS FROM $label?"
                                                    groupActionLabel = "RESTORE"
                                                    groupActionIsDelete = false
                                                    groupActionUuids = txList.map { it.uuid }
                                                    showGroupActionDialog = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_archive_restore),
                                                contentDescription = "Restore All",
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    groupActionTitle = "DELETE ALL?"
                                                    groupActionMessage = "PERMANENTLY DELETE ALL ${txList.size} TRANSACTIONS FROM $label? THIS CANNOT BE UNDONE."
                                                    groupActionLabel = "DELETE"
                                                    groupActionIsDelete = true
                                                    groupActionUuids = txList.map { it.uuid }
                                                    showGroupActionDialog = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_trash),
                                                contentDescription = "Delete All",
                                                tint = nothingRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Transactions under this date header (exact homescreen row replica)
                            items(txList, key = { it.id }) { transaction ->
                                TrashTransactionRow(
                                    tx = transaction,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        activeTransactionForOptions = transaction
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Custom Nothing-styled Dialogs ─────────────────────────────────────

    // Individual Transaction Options Dialog
    if (activeTransactionForOptions != null) {
        val tx = activeTransactionForOptions!!
        TrashOptionsDialog(
            transaction = tx,
            colors = colors,
            nothingRed = nothingRed,
            onRestore = {
                onRestore(listOf(tx.uuid))
            },
            onDeletePermanently = {
                onDeletePermanently(listOf(tx.uuid))
            },
            onDismiss = { activeTransactionForOptions = null }
        )
    }

    // Clear All Trash Confirmation Dialog
    if (showClearDialog) {
        ClearTrashConfirmDialog(
            count = deletedTransactions.size,
            colors = colors,
            nothingRed = nothingRed,
            onConfirm = {
                onClearAll()
                onSync()
            },
            onDismiss = { showClearDialog = false }
        )
    }

    // Group Action Confirmation Dialog
    if (showGroupActionDialog) {
        GroupActionConfirmDialog(
            title = groupActionTitle,
            message = groupActionMessage,
            actionLabel = groupActionLabel,
            isDelete = groupActionIsDelete,
            colors = colors,
            nothingRed = nothingRed,
            onConfirm = {
                if (groupActionIsDelete) {
                    onDeletePermanently(groupActionUuids)
                } else {
                    onRestore(groupActionUuids)
                }
            },
            onDismiss = { showGroupActionDialog = false }
        )
    }
}

@Composable
private fun TrashTransactionRow(
    tx: Transaction,
    onClick: () -> Unit
) {
    val colors = LocalDeltaColors.current
    val timeStr = remember(tx.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tx.timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeltaShapes.RowItem)
            .background(colors.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: icon + category + time (matching TransactionRow style)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = categoryIcon(tx.category)),
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tx.category,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
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
        
        // Right: amount (matching TransactionRow style)
        val isIncome = tx.type == TransactionType.INCOME || tx.type == TransactionType.BORROWED
        val amountColor = if (isIncome) colors.positive else colors.negative
        Text(
            text = when (tx.type) {
                TransactionType.BORROWED -> String.format("+%,.2f", tx.amount)
                TransactionType.LENT -> String.format("-%,.2f", kotlin.math.abs(tx.amount))
                else -> "${if (tx.amount >= 0) "+" else "-"}${String.format("%,.2f", kotlin.math.abs(tx.amount))}"
            },
            fontFamily = NothingGlyph,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = amountColor
        )
    }
}

@Composable
fun TrashOptionsDialog(
    transaction: Transaction,
    colors: com.example.elta.ui.theme.DeltaColorScheme,
    nothingRed: Color,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    DeltaDialog(onDismissRequest = onDismiss) {
        Text(
            fontWeight = FontWeight.Bold,
            text = "TRASH OPTIONS",
            fontFamily = NothingGlyph,
            fontSize = 15.sp,
            color = colors.textPrimary,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        val isIncome = transaction.type == TransactionType.INCOME || transaction.type == TransactionType.BORROWED
        val amountText = "${if (isIncome) "+" else "-"}${String.format("%,.2f", kotlin.math.abs(transaction.amount))}"
        Text(
            text = "${transaction.category} • $amountText",
            fontSize = 13.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Default
        )
        Spacer(modifier = Modifier.height(20.dp))

        // RESTORE Button
        DeltaButton(
            text = "RESTORE",
            onClick = {
                onRestore()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            style = DeltaButtonStyle.Secondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // DELETE PERMANENTLY Button
        DeltaButton(
            text = "DELETE PERMANENTLY",
            onClick = {
                onDeletePermanently()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            style = DeltaButtonStyle.Negative
        )

        Spacer(modifier = Modifier.height(10.dp))

        // CANCEL Button
        DeltaButton(
            text = "CANCEL",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = DeltaButtonStyle.Text
        )
    }
}

@Composable
fun ClearTrashConfirmDialog(
    count: Int,
    colors: com.example.elta.ui.theme.DeltaColorScheme,
    nothingRed: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    DeltaDialog(onDismissRequest = onDismiss) {
        Text(
            fontWeight = FontWeight.Bold,
            text = "CLEAR ALL TRASH?",
            fontFamily = NothingGlyph,
            fontSize = 15.sp,
            color = colors.textPrimary,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ALL $count TRANSACTIONS WILL BE PERMANENTLY REMOVED.",
            fontSize = 12.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Default
        )
        Spacer(modifier = Modifier.height(20.dp))

        // CLEAR ALL Button
        DeltaButton(
            text = "CLEAR ALL",
            onClick = {
                onConfirm()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            style = DeltaButtonStyle.Negative
        )

        Spacer(modifier = Modifier.height(10.dp))

        // CANCEL Button
        DeltaButton(
            text = "CANCEL",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = DeltaButtonStyle.Secondary
        )
    }
}

@Composable
fun GroupActionConfirmDialog(
    title: String,
    message: String,
    actionLabel: String,
    isDelete: Boolean,
    colors: com.example.elta.ui.theme.DeltaColorScheme,
    nothingRed: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    DeltaDialog(onDismissRequest = onDismiss) {
        Text(
            fontWeight = FontWeight.Bold,
            text = title,
            fontFamily = NothingGlyph,
            fontSize = 15.sp,
            color = colors.textPrimary,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 12.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Default
        )
        Spacer(modifier = Modifier.height(20.dp))

        // ACTION Button
        DeltaButton(
            text = actionLabel,
            onClick = {
                onConfirm()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            style = if (isDelete) DeltaButtonStyle.Negative else DeltaButtonStyle.Primary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // CANCEL Button
        DeltaButton(
            text = "CANCEL",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = DeltaButtonStyle.Secondary
        )
    }
}
