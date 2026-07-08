package com.example.elta

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.elta.data.SyncStatus
import com.example.elta.ui.theme.LocalDeltaColors
import com.example.elta.ui.theme.NothingGlyph
import com.example.elta.ui.theme.DeltaShapes
import com.example.elta.ui.theme.DeltaDialog
import com.example.elta.ui.theme.DeltaButton
import com.example.elta.ui.theme.DeltaButtonStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DeltaProfileScreen(
    authUid: String?,
    username: String?,
    transactionCount: Int,
    deletedCount: Int,
    syncStatus: SyncStatus,
    isSyncPaused: Boolean,
    isCustomPassportActive: Boolean,
    onSetUsername: (String) -> Unit,
    onLoadFromUid: (String) -> Unit,
    onDisconnect: () -> Unit,
    onToggleSyncPause: () -> Unit,
    onClearAllLocalData: () -> Unit,
    onSync: () -> Unit,
    onNavigateBack: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var usernameInput by remember { mutableStateOf(username ?: "") }
    var uuidInput by remember { mutableStateOf("") }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var copiedUid by remember { mutableStateOf(false) }
    var usernameSaved by remember { mutableStateOf(false) }
    var showEditUsernameDialog by remember { mutableStateOf(false) }


    LaunchedEffect(username) { usernameInput = username ?: "" }
    LaunchedEffect(copiedUid) { if (copiedUid) { delay(2000); copiedUid = false } }
    LaunchedEffect(usernameSaved) { if (usernameSaved) { delay(1500); usernameSaved = false } }

    val colors = LocalDeltaColors.current
    val nothingRed = Color(0xFFFF2D00)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val syncIconRes = when {
        isSyncPaused -> R.drawable.ic_cloud_alert
        syncStatus == SyncStatus.SYNCING -> R.drawable.ic_cloud_sync
        syncStatus == SyncStatus.SUCCESS -> R.drawable.ic_cloud_check
        syncStatus == SyncStatus.ERROR -> R.drawable.ic_cloud_alert
        else -> R.drawable.ic_cloud_backup
    }
    val syncIconTint = when {
        isSyncPaused -> colors.textSecondary.copy(alpha = 0.5f)
        syncStatus == SyncStatus.SUCCESS -> colors.positive
        syncStatus == SyncStatus.ERROR -> colors.negative
        else -> colors.textPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // FIXED Top Bar
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onNavigateBack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "PROFILE",
                        fontSize = 24.sp,
                        fontFamily = NothingGlyph,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSync()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = syncIconRes),
                        contentDescription = "Sync Data",
                        tint = syncIconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Divider
            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.border))

            // Scrollable Content Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ─── Hero Section: Nothing Profile Avatar & Identity ────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlyphProfileAvatar(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape),
                        size = 110.dp,
                        activeColor = colors.textPrimary,
                        inactiveColor = colors.textSecondary.copy(alpha = 0.08f),
                        backgroundColor = colors.surface,
                        initialSeed = remember(authUid) { authUid?.hashCode()?.toLong() ?: Random.nextLong() }
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = (username ?: "ANONYMOUS").uppercase(),
                                fontSize = 22.sp,
                                fontFamily = NothingGlyph,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pencil),
                                contentDescription = "Edit Username",
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        usernameInput = username ?: ""
                                        showEditUsernameDialog = true
                                    }
                            )
                        }

                        // Truncated Copyable Passport UID
                        val displayUid = authUid ?: "CONNECTING..."
                        val shortUid = if (displayUid.length > 15) {
                            displayUid.take(8) + "..." + displayUid.takeLast(8)
                        } else displayUid

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = authUid != null) {
                                    clipboardManager.setText(AnnotatedString(displayUid))
                                    copiedUid = true
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.currencies),
                                contentDescription = "Passport key",
                                tint = colors.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (copiedUid) "COPIED TO CLIPBOARD" else "PASSPORT: ${shortUid.uppercase()}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (copiedUid) colors.positive else colors.textSecondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // ─── Stats Row ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileStatCard(
                        modifier = Modifier.weight(1f),
                        value = "$transactionCount",
                        label = "TRANSACTIONS",
                        color = colors.textPrimary
                    )
                    ProfileStatCard(
                        modifier = Modifier.weight(1f),
                        value = "$deletedCount",
                        label = "TRASH BIN",
                        color = nothingRed
                    )
                }



                // ─── Cloud Sync Card (With Pulsating Status Dot) ──────────────────
                ProfileSectionCard(title = "CLOUD SYNC STATUS") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pulsating Status Dot
                            val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "dotPulseAlpha"
                            )

                            val dotColor = when {
                                isSyncPaused -> colors.textSecondary.copy(alpha = 0.5f)
                                syncStatus == SyncStatus.SYNCING -> colors.textPrimary
                                syncStatus == SyncStatus.SUCCESS -> colors.positive
                                syncStatus == SyncStatus.ERROR -> nothingRed
                                else -> colors.textSecondary
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        dotColor.copy(
                                            alpha = if (syncStatus == SyncStatus.SYNCING) pulseAlpha else 1.0f
                                        )
                                    )
                            )
                            Text(
                                text = when {
                                    isSyncPaused -> "CLOUD SYNC IS PAUSED"
                                    syncStatus == SyncStatus.SYNCING -> "SYNCING RECENT CHANGES..."
                                    syncStatus == SyncStatus.SUCCESS -> "UP TO DATE"
                                    syncStatus == SyncStatus.ERROR -> "SYNC FAILURE / OFFLINE"
                                    else -> "IDLE"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.buttonBackground)
                                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                .clickable { onToggleSyncPause() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSyncPaused) "RESUME" else "PAUSE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSyncPaused) colors.positive else nothingRed,
                                letterSpacing = 1.sp
                            )
                        }

                        if (!isSyncPaused) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.buttonBackground)
                                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                    .clickable { onSync() }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SYNC NOW",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                // ─── Restore Passport ID Card ────────────────────────────────────
                ProfileSectionCard(title = "RESTORE ACCOUNT PASSPORT") {
                    Text(
                        text = "RESTORE ALL TRANSACTIONS AND PROFILE DATA BY ENTERING A PREVIOUS DEVICE PASSPORT ID BELOW.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    NothingTextField(
                        value = uuidInput,
                        onValueChange = {
                            uuidInput = it.trim()
                            if (errorMessage != null) onClearError()
                        },
                        placeholder = "PASTE PASSPORT ID HERE...",
                        isMonospace = true,
                        errorMessage = errorMessage,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (uuidInput.isNotBlank()) {
                                onLoadFromUid(uuidInput)
                            }
                        })
                    )

                    if (errorMessage != null) {
                        Text(
                            text = "ERROR: $errorMessage",
                            fontSize = 11.sp,
                            color = nothingRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    val restoreEnabled = uuidInput.length >= 10
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (restoreEnabled) colors.buttonBackground else colors.buttonBackground.copy(alpha = 0.3f))
                            .border(1.dp, if (restoreEnabled) colors.border else colors.border.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable(enabled = restoreEnabled) {
                                focusManager.clearFocus()
                                onClearError()
                                onLoadFromUid(uuidInput)
                                uuidInput = ""
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LOAD PASSPORT DATA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (restoreEnabled) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                            letterSpacing = 1.sp
                        )
                    }
                }

                // ─── Danger Zone Card ─────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeltaShapes.Card)
                        .border(1.dp, nothingRed.copy(alpha = 0.4f), DeltaShapes.Card)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "DANGER ZONE",
                        fontSize = 11.sp,
                        color = nothingRed,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isCustomPassportActive) {
                            DeltaButton(
                                text = "DISCONNECT",
                                onClick = { showDisconnectDialog = true },
                                modifier = Modifier.weight(1f),
                                style = DeltaButtonStyle.NegativeSecondary
                            )
                        }

                        DeltaButton(
                            text = "CLEAR LOCAL",
                            onClick = { showClearDataDialog = true },
                            modifier = Modifier.weight(1f),
                            style = DeltaButtonStyle.NegativeSecondary
                        )
                    }
                }
            }
        }
    }

    // ─── Dialogs (Unified custom Nothing dialog style) ──────────────────────
    if (showEditUsernameDialog) {
        DeltaDialog(onDismissRequest = { showEditUsernameDialog = false }) {
            Text(
                text = "EDIT USERNAME",
                fontFamily = NothingGlyph,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.textPrimary,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            
            NothingTextField(
                value = usernameInput,
                onValueChange = { if (it.length <= 12) usernameInput = it },
                placeholder = "ENTER USERNAME...",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    val name = usernameInput.trim()
                    if (name.length in 3..12) {
                        onSetUsername(name)
                        showEditUsernameDialog = false
                    }
                }),
                trailingContent = {
                    Text(
                        text = "${usernameInput.length}/12",
                        fontSize = 11.sp,
                        color = colors.textSecondary.copy(alpha = 0.5f)
                    )
                }
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DeltaButton(
                    text = "CANCEL",
                    onClick = { showEditUsernameDialog = false },
                    modifier = Modifier.weight(1f),
                    style = DeltaButtonStyle.Secondary
                )
                
                val isNameValid = usernameInput.trim().length in 3..12
                DeltaButton(
                    text = "SAVE",
                    onClick = {
                        onSetUsername(usernameInput.trim())
                        showEditUsernameDialog = false
                    },
                    enabled = isNameValid,
                    modifier = Modifier.weight(1f),
                    style = DeltaButtonStyle.Primary
                )
            }
        }
    }

    if (showDisconnectDialog) {
        DeltaDialog(onDismissRequest = { showDisconnectDialog = false }) {
            Text(
                text = "DISCONNECT PASSPORT?",
                fontFamily = NothingGlyph,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.textPrimary,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "YOUR LOCAL DATABASE WILL BE WIPED AND YOU WILL SWITCH BACK TO YOUR DEFAULT INDEPENDENT DEVICE PASSPORT.",
                fontSize = 12.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Default
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DeltaButton(
                    text = "CANCEL",
                    onClick = { showDisconnectDialog = false },
                    modifier = Modifier.weight(1f),
                    style = DeltaButtonStyle.Secondary
                )
                DeltaButton(
                    text = "DISCONNECT",
                    onClick = {
                        showDisconnectDialog = false
                        onDisconnect()
                    },
                    modifier = Modifier.weight(1f),
                    style = DeltaButtonStyle.NegativeSecondary
                )
            }
        }
    }

    if (showClearDataDialog) {
        DeltaDialog(onDismissRequest = { showClearDataDialog = false }) {
            Text(
                text = "CLEAR LOCAL DATABASE?",
                fontFamily = NothingGlyph,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.textPrimary,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ALL TRANSACTIONS WILL BE REMOVED FROM THIS DEVICE. CLOUD PASSPORT BACKUP REMAINS INTACT.",
                fontSize = 12.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Default
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DeltaButton(
                    text = "CANCEL",
                    onClick = { showClearDataDialog = false },
                    modifier = Modifier.weight(1f),
                    style = DeltaButtonStyle.Secondary
                )
                DeltaButton(
                    text = "CLEAR ALL",
                    onClick = {
                        showClearDataDialog = false
                        onClearAllLocalData()
                    },
                    modifier = Modifier.weight(1f),
                    style = DeltaButtonStyle.NegativeSecondary
                )
            }
        }
    }
}

// ─── Custom Reusable NothingTextField ────────────────────────────────────────
@Composable
fun NothingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isMonospace: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    val colors = LocalDeltaColors.current
    val nothingRed = Color(0xFFFF2D00)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = TextStyle(
            fontSize = 14.sp,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = colors.textPrimary
        ),
        singleLine = singleLine,
        cursorBrush = SolidColor(nothingRed),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.background)
                    .border(
                        1.dp,
                        if (errorMessage != null) nothingRed else colors.border,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = colors.textSecondary.copy(alpha = 0.35f),
                                fontSize = 14.sp,
                                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
                            )
                        }
                        inner()
                    }
                    if (trailingContent != null) {
                        trailingContent()
                    }
                }
            }
        }
    )
}

// ─── Styled Nothing Avatar ───────────────────────────────────────────────────
/**
 * A 12x12 Dot Matrix Glyph Profile Avatar.
 * Clicking triggers a staggered radial wave animation to morph into a new symmetric glyph pattern.
 */
@Composable
fun GlyphProfileAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    activeColor: Color = Color(0xFF00FF66),     // Neon active dot color
    inactiveColor: Color = Color(0x22FFFFFF),   // Subdued background dot color
    backgroundColor: Color = Color(0xFF0D0D0D), // Dark matrix background
    initialSeed: Long = remember { Random.nextLong() }
) {
    val matrixSize = 12
    var currentSeed by remember { mutableStateOf(initialSeed) }
    
    var oldPattern by remember { mutableStateOf(generateSymmetric12x12Pattern(currentSeed)) }
    var targetPattern by remember { mutableStateOf(oldPattern) }
    
    val transitionProgress = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    // Map your custom coordinate offsets (centered around 0,0)
    // Extra coordinates extending the 12x12 grid into a circle shape
    val extraDots = remember {
        listOf(
            // Right Side
            listOf(6.5f to -3.5f, 6.5f to -2.5f, 6.5f to -1.5f, 6.5f to -0.5f, 6.5f to 0.5f, 6.5f to 1.5f, 6.5f to 2.5f, 6.5f to 3.5f),
            listOf(7.5f to -1.5f, 7.5f to -0.5f, 7.5f to 0.5f, 7.5f to 1.5f),
            // Left Side
            listOf(-6.5f to -3.5f, -6.5f to -2.5f, -6.5f to -1.5f, -6.5f to -0.5f, -6.5f to 0.5f, -6.5f to 1.5f, -6.5f to 2.5f, -6.5f to 3.5f),
            listOf(-7.5f to -1.5f, -7.5f to -0.5f, -7.5f to 0.5f, -7.5f to 1.5f),
            // Top Side
            listOf(-3.5f to 6.5f, -2.5f to 6.5f, -1.5f to 6.5f, -0.5f to 6.5f, 0.5f to 6.5f, 1.5f to 6.5f, 2.5f to 6.5f, 3.5f to 6.5f),
            listOf(-1.5f to 7.5f, -0.5f to 7.5f, 0.5f to 7.5f, 1.5f to 7.5f),
            // Bottom Side
            listOf(-3.5f to -6.5f, -2.5f to -6.5f, -1.5f to -6.5f, -0.5f to -6.5f, 0.5f to -6.5f, 1.5f to -6.5f, 2.5f to -6.5f, 3.5f to -6.5f),
            listOf(-1.5f to -7.5f, -0.5f to -7.5f, 0.5f to -7.5f, 1.5f to -7.5f)
        ).flatten()
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!transitionProgress.isRunning) {
                    coroutineScope.launch {
                        oldPattern = targetPattern
                        currentSeed = Random.nextLong()
                        targetPattern = generateSymmetric12x12Pattern(currentSeed)
                        
                        transitionProgress.snapTo(0f)
                        transitionProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 650,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasWidth = size.toPx()
            val canvasHeight = size.toPx()
            
            drawRect(color = backgroundColor)
            
            // Adjust step scale to accommodate the expanded radius (16x16 coordinate space)
            val virtualGridSize = 16f 
            val step = canvasWidth / virtualGridSize
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight / 2f
            
            val maxRadius = step * 0.38f
            val maxDistance = 7.5f // Max radius from origin

            // Helper function to draw individual dots and handle animations
            fun drawDotAt(gridX: Float, gridY: Float, isActiveOld: Boolean, isActiveTarget: Boolean) {
                val dx = gridX
                val dy = gridY
                val distanceFromCenter = sqrt(dx * dx + dy * dy)
                val delayFraction = (distanceFromCenter / maxDistance) * 0.45f
                
                val localProgress = ((transitionProgress.value - delayFraction) / (1f - delayFraction))
                    .coerceIn(0f, 1f)
                
                val (scale, alpha, dotColor) = when {
                    isActiveOld && isActiveTarget -> {
                        val pulse = 1f - (0.35f * sin(localProgress * Math.PI).toFloat())
                        Triple(pulse, 1f, activeColor)
                    }
                    !isActiveOld && isActiveTarget -> Triple(localProgress, localProgress, activeColor)
                    isActiveOld && !isActiveTarget -> Triple(1f - localProgress, 1f - localProgress, activeColor)
                    else -> Triple(1f, 0.35f, inactiveColor)
                }
                
                val cx = centerX + gridX * step
                val cy = centerY - gridY * step // Invert Y for standard Cartesian coordinates
                
                if (isActiveOld || isActiveTarget) {
                    drawCircle(
                        color = inactiveColor,
                        radius = maxRadius * 0.6f,
                        center = Offset(cx, cy)
                    )
                }
                
                drawCircle(
                    color = dotColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = (maxRadius * scale).coerceAtLeast(1f),
                    center = Offset(cx, cy)
                )
            }

            // 1. Draw standard 12x12 Inner Grid
            for (row in 0 until matrixSize) {
                for (col in 0 until matrixSize) {
                    val index = row * matrixSize + col
                    val gridX = col - 5.5f
                    val gridY = 5.5f - row
                    
                    drawDotAt(
                        gridX = gridX,
                        gridY = gridY,
                        isActiveOld = oldPattern[index],
                        isActiveTarget = targetPattern[index]
                    )
                }
            }

            // 2. Draw Extra Border Dots (kept static/inactive or linked to state as preferred)
            extraDots.forEach { (x, y) ->
                drawDotAt(
                    gridX = x,
                    gridY = y,
                    isActiveOld = false,
                    isActiveTarget = false
                )
            }
        }
    }
}

/**
 * Generates a 12x12 horizontally symmetric boolean matrix (144 dots total).
 * Mirroring the left 6 columns onto the right 6 columns guarantees iconic glyph shapes.
 */
private fun generateSymmetric12x12Pattern(seed: Long): List<Boolean> {
    val random = Random(seed)
    val size = 12
    val halfSize = size / 2
    val matrix = BooleanArray(size * size)
    
    for (r in 0 until size) {
        for (c in 0 until halfSize) {
            // ~45% active density yields optimal, scannable pixel-art glyphs
            val isActive = random.nextFloat() < 0.45f
            
            matrix[r * size + c] = isActive
            matrix[r * size + (size - 1 - c)] = isActive // Mirror horizontally
        }
    }
    return matrix.toList()
}

@Composable
private fun ProfileSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalDeltaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = colors.textSecondary,
            letterSpacing = 1.sp
        )
        content()
    }
}

@Composable
private fun ProfileStatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    color: Color
) {
    val colors = LocalDeltaColors.current
    val calculatedFontSize = when {
        value.length > 5 -> 18.sp
        value.length > 3 -> 24.sp
        else -> 32.sp
    }
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = value,
            fontSize = calculatedFontSize,
            fontFamily = NothingGlyph,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = colors.textSecondary,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
