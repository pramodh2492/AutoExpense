package com.expensetracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One step in a spotlight tour.
 *
 * @param targetKey the key of the UI element to highlight (see [Modifier.spotlightTarget]).
 *   When null — or when the target hasn't been laid out — the step is a centered, full-scrim
 *   callout with no cutout, useful for an intro/outro message.
 * @param icon optional glyph shown in the callout's gradient badge.
 * @param primaryLabel overrides the primary button text (defaults to "Next", or "Done" on
 *   the final step).
 */
data class SpotlightStep(
    val targetKey: String? = null,
    val title: String,
    val description: String,
    val icon: ImageVector? = null,
    val primaryLabel: String? = null,
    val accent: Color = Color(0xFF7C4DFF),
    /**
     * Navigation route this step lives on. When set and it differs from the current route,
     * the host navigates there as the step becomes active (e.g. jump to the Transactions
     * page to spotlight a real row). Null means "stay wherever we are".
     */
    val route: String? = null
)

/**
 * Holds live target positions and the current step of a running tour. Create with
 * [rememberSpotlightState], register targets with [Modifier.spotlightTarget], and render the
 * overlay with [SpotlightOverlay].
 */
class SpotlightState {
    /** Absolute (window-root) bounds of each registered target, keyed by its string id. */
    internal val targets = mutableStateMapOf<String, Rect>()

    var steps by mutableStateOf<List<SpotlightStep>>(emptyList())
        private set
    var currentIndex by mutableStateOf(-1)
        private set

    val isActive: Boolean get() = currentIndex in steps.indices
    val stepCount: Int get() = steps.size

    fun start(steps: List<SpotlightStep>) {
        if (steps.isEmpty()) return
        this.steps = steps
        currentIndex = 0
    }

    /** Advance to the next step. @return true if the tour just finished. */
    fun next(): Boolean =
        if (currentIndex >= steps.lastIndex) {
            currentIndex = -1
            true
        } else {
            currentIndex++
            false
        }

    fun back() {
        if (currentIndex > 0) currentIndex--
    }

    fun dismiss() {
        currentIndex = -1
    }
}

@Composable
fun rememberSpotlightState(): SpotlightState = remember { SpotlightState() }

/** Register this composable as a spotlight target so a tour can highlight it. */
fun Modifier.spotlightTarget(key: String, state: SpotlightState): Modifier =
    this.onGloballyPositioned { state.targets[key] = it.boundsInRoot() }

/**
 * Premium full-screen coach-mark overlay: dims everything, glides an animated cutout with a
 * soft glow + pulsing ring over the current target, and shows a callout card with a gradient
 * icon badge, animated progress dots, and a gradient primary action. Place it as the last
 * child of a full-size [Box] that wraps the screen.
 */
@Composable
fun SpotlightOverlay(
    state: SpotlightState,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {}
) {
    if (!state.isActive) return

    // When the active step lives on another screen, ask the host to navigate there so its
    // target (e.g. a transaction row) can lay out and be spotlighted.
    val activeRoute = state.steps.getOrNull(state.currentIndex)?.route
    LaunchedEffect(state.currentIndex, activeRoute) {
        if (activeRoute != null) onNavigate(activeRoute)
    }

    val density = LocalDensity.current
    val padPx = with(density) { 10.dp.toPx() }
    val cornerPx = with(density) { 16.dp.toPx() }
    val ringWidthPx = with(density) { 2.5.dp.toPx() }
    val glowPx = with(density) { 10.dp.toPx() }

    val pulse = rememberInfiniteTransition(label = "spotlight")
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ring"
    )

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
            // Swallow all touches so the highlighted UI can't be interacted with mid-tour.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        val viewportH = constraints.maxHeight.toFloat()
        val step = state.steps.getOrNull(state.currentIndex)
        // Translate the target's window-root bounds into this overlay's local space.
        val targetRect = step?.targetKey
            ?.let { state.targets[it] }
            ?.translate(-overlayOrigin.x, -overlayOrigin.y)

        val accent = step?.accent ?: Color(0xFF7C4DFF)

        // Remember the last real target so the cutout can glide (not jump) between steps and
        // shrink away gracefully when a step has no target.
        var lastRect by remember { mutableStateOf(Rect.Zero) }
        if (targetRect != null) lastRect = targetRect

        val animatedRect by animateRectAsState(
            targetValue = if (lastRect == Rect.Zero) Rect.Zero else lastRect,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "cutout_rect"
        )
        // Fade the cutout out entirely on target-less steps.
        val cutoutAlpha by animateFloatAsState(
            targetValue = if (targetRect != null) 1f else 0f,
            animationSpec = tween(300),
            label = "cutout_alpha"
        )
        val hasCutout = cutoutAlpha > 0.01f && animatedRect != Rect.Zero

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Force an offscreen layer so BlendMode.Clear punches a real hole,
                // revealing the live UI beneath the scrim.
                .graphicsLayer(alpha = 0.99f)
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.74f))
            if (hasCutout) {
                val inflated = Rect(
                    left = animatedRect.left - padPx,
                    top = animatedRect.top - padPx,
                    right = animatedRect.right + padPx,
                    bottom = animatedRect.bottom + padPx
                )
                val topLeft = Offset(inflated.left, inflated.top)
                val rectSize = Size(inflated.width, inflated.height)

                // Punch the hole.
                drawRoundRect(
                    color = Color.Black.copy(alpha = cutoutAlpha),
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    blendMode = BlendMode.Clear
                )
                // Soft outer glow: a few concentric strokes fading outward.
                repeat(3) { i ->
                    val spread = glowPx * (i + 1) / 3f
                    drawRoundRect(
                        color = accent.copy(alpha = 0.12f * cutoutAlpha),
                        topLeft = Offset(topLeft.x - spread, topLeft.y - spread),
                        size = Size(rectSize.width + spread * 2, rectSize.height + spread * 2),
                        cornerRadius = CornerRadius(cornerPx + spread, cornerPx + spread),
                        style = Stroke(width = ringWidthPx * 2)
                    )
                }
                // Crisp pulsing ring hugging the cutout.
                drawRoundRect(
                    color = accent.copy(alpha = ringAlpha * cutoutAlpha),
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = ringWidthPx)
                )
            }
        }

        // Keep the callout clear of the cutout: below a top-half target, above a bottom-half
        // one, centered when there's no target to point at.
        val alignment = when {
            targetRect == null -> Alignment.Center
            targetRect.center.y < viewportH * 0.5f -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }

        AnimatedContent(
            targetState = state.currentIndex,
            transitionSpec = {
                (slideInVertically { it / 4 } + fadeIn(tween(250)))
                    .togetherWith(slideOutVertically { -it / 4 } + fadeOut(tween(150)))
            },
            label = "spotlight_step"
        ) { index ->
            val s = state.steps.getOrNull(index) ?: return@AnimatedContent
            SpotlightCallout(
                step = s,
                index = index,
                stepCount = state.stepCount,
                alignment = alignment,
                state = state,
                onComplete = onComplete,
                onSkip = onSkip
            )
        }
    }
}

@Composable
private fun SpotlightCallout(
    step: SpotlightStep,
    index: Int,
    stepCount: Int,
    alignment: Alignment,
    state: SpotlightState,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val isLast = index == stepCount - 1
    val accent = step.accent
    val accentLight = lerp(accent, Color.White, 0.28f)

    // Own full-size Box so this composable can align its card without relying on an outer
    // BoxScope receiver (AnimatedContent's content lambda provides none).
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(alignment)
                .padding(20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step.icon != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(accentLight, accent))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressDots(count = stepCount, current = index, accent = accent)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    state.dismiss()
                    onSkip()
                }) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index > 0) {
                    TextButton(onClick = { state.back() }) {
                        Text("Back")
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                GradientButton(
                    text = step.primaryLabel ?: if (isLast) "Done" else "Next",
                    gradient = listOf(accentLight, accent),
                    onClick = { if (state.next()) onComplete() }
                )
            }
        }
    }
}

/** A row of progress pills; the active one stretches into an accent-gradient bar. */
@Composable
private fun ProgressDots(count: Int, current: Int, accent: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val active = i == current
            val width by animateFloatAsState(
                targetValue = if (active) 22f else 7f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dot_$i"
            )
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) accent
                        else accent.copy(alpha = 0.22f)
                    )
            )
        }
    }
}

/** Filled pill button with a horizontal accent gradient. */
@Composable
private fun GradientButton(
    text: String,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(gradient))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 22.dp, vertical = 11.dp),
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
