package com.example.elta.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

object DeltaShapes {
    val Dialog = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(12.dp)
    val Button = RoundedCornerShape(8.dp)
    val Input = RoundedCornerShape(8.dp)
    val RowItem = RoundedCornerShape(8.dp)
    val Chip = RoundedCornerShape(6.dp)
    val Badge = RoundedCornerShape(4.dp)
}

@Composable
fun DeltaDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalDeltaColors.current
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .clip(DeltaShapes.Dialog)
                .background(colors.surface)
                .border(1.dp, colors.border, DeltaShapes.Dialog)
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(280.dp),
                content = content
            )
        }
    }
}

enum class DeltaButtonStyle {
    Primary,
    Secondary,
    Text,
    Negative,
    NegativeSecondary
}

@Composable
fun DeltaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: DeltaButtonStyle = DeltaButtonStyle.Primary
) {
    val colors = LocalDeltaColors.current
    val nothingRed = Color(0xFFFF2D00)
    
    val bg = when (style) {
        DeltaButtonStyle.Primary -> if (enabled) colors.textPrimary else colors.textPrimary.copy(alpha = 0.3f)
        DeltaButtonStyle.Secondary -> colors.buttonBackground
        DeltaButtonStyle.Text -> Color.Transparent
        DeltaButtonStyle.Negative -> if (enabled) nothingRed else nothingRed.copy(alpha = 0.5f)
        DeltaButtonStyle.NegativeSecondary -> nothingRed.copy(alpha = 0.1f)
    }
    
    val contentColor = when (style) {
        DeltaButtonStyle.Primary -> colors.background
        DeltaButtonStyle.Secondary -> colors.textPrimary
        DeltaButtonStyle.Text -> colors.textSecondary
        DeltaButtonStyle.Negative -> Color.White
        DeltaButtonStyle.NegativeSecondary -> nothingRed
    }

    val borderModifier = when (style) {
        DeltaButtonStyle.Secondary -> Modifier.border(1.dp, colors.border, DeltaShapes.Button)
        DeltaButtonStyle.NegativeSecondary -> Modifier.border(1.dp, nothingRed.copy(alpha = 0.3f), DeltaShapes.Button)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .clip(DeltaShapes.Button)
            .background(bg)
            .then(borderModifier)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Default,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            letterSpacing = 0.5.sp
        )
    }
}
