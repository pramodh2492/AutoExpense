package com.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.ui.theme.AppTheme

/**
 * Full-screen glass backdrop: paints the theme's background gradient and two large blurred
 * color "orbs" for depth. Wrap a screen's content in this; children render above the glow.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val glass = AppTheme.glass
    Box(modifier = modifier.fillMaxSize().background(glass.backgroundGradient)) {
        // Decorative blurred orbs. Kept behind content; drawn on a Canvas then blurred.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(90.dp)
        ) {
            drawCircle(
                color = glass.orbPrimary.copy(alpha = if (glass.dark) 0.38f else 0.30f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.12f, size.height * 0.08f)
            )
            drawCircle(
                color = glass.orbSecondary.copy(alpha = if (glass.dark) 0.22f else 0.22f),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.95f, size.height * 0.35f)
            )
        }
        content()
    }
}

/**
 * Frosted-glass surface: translucent gradient fill with a gradient hairline border and
 * rounded corners. The core building block of the premium re-skin — use in place of Card.
 *
 * @param onClick when non-null, the whole card is clickable with a ripple.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val glass = AppTheme.glass
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val base = modifier
        .clip(shape)
        .background(glass.glassFill)
        .border(BorderStroke(1.dp, glass.glassBorder), shape)
    val withClick = if (onClick != null) base.clickable { onClick() } else base
    Box(modifier = withClick.padding(contentPadding)) {
        content()
    }
}

/** Section header with an accent-gradient bar — used to title groups of content. */
@Composable
fun GlassSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val glass = AppTheme.glass
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(glass.accentGradient))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * Pill-shaped gradient action button. Reusable premium CTA used across screens.
 */
@Composable
fun GradientPillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val glass = AppTheme.glass
    val interaction = remember { MutableInteractionSource() }
    val colors = if (enabled) glass.accentGradient
        else listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.3f))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(colors))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) { onClick() }
            .padding(horizontal = 24.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
