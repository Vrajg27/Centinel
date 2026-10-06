package com.centinel.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.centinel.app.ui.navigation.Screen
import com.centinel.app.ui.theme.*

/**
 * Dynamic Theme Glassmorphism Card that adapts noticeably to the active color theme.
 */
@Composable
fun CentinelGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    glowColor: Color = Color.Transparent,
    isSelected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeSurface = MaterialTheme.colorScheme.surface
    val isActive = isSelected || glowColor != Color.Transparent

    val activeGlow = if (glowColor != Color.Transparent) glowColor else themePrimary

    val borderBrush = if (isActive) {
        Brush.linearGradient(
            listOf(
                activeGlow.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.1f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                themePrimary.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                this.shadowElevation = if (isActive) 12.dp.toPx() else 4.dp.toPx()
                this.shape = RoundedCornerShape(cornerRadius)
                this.clip = true
                if (isActive) {
                    this.ambientShadowColor = activeGlow.copy(alpha = 0.3f)
                    this.spotShadowColor = activeGlow.copy(alpha = 0.5f)
                }
            }
            .background(themeSurface)
            .border(
                width = if (isActive) 1.2.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            content()
        }
    }
}

/**
 * High-Gloss Button that dynamically adopts the active theme color palette.
 */
@Composable
fun CentinelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    colors: List<Color>? = null
) {
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeSecondary = MaterialTheme.colorScheme.secondary
    val activeColors = colors ?: listOf(themePrimary, themeSecondary)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "scale")

    val borderBrush = if (isPressed) {
        Brush.linearGradient(listOf(themePrimary, themeSecondary))
    } else {
        Brush.linearGradient(listOf(themePrimary.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f)))
    }

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        enabled = enabled && !loading,
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                shadowElevation = if (isPressed) 12.dp.toPx() else 4.dp.toPx()
                shape = RoundedCornerShape(20.dp)
                clip = true
            }
            .border(
                width = if (isPressed) 1.2.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                brush = Brush.linearGradient(if (enabled) activeColors else listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)),
                shape = RoundedCornerShape(20.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextTertiary
        ),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                }
                Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Stat Card adapting dynamically to active theme color.
 */
@Composable
fun CentinelStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color? = null,
    modifier: Modifier = Modifier
) {
    val themeColor = color ?: MaterialTheme.colorScheme.primary

    CentinelGlassCard(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(themeColor.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, themeColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = themeColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

/**
 * Tool Card that visibly adapts to the active visual theme.
 */
@Composable
fun CentinelToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themePrimary = MaterialTheme.colorScheme.primary

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")

    val iconBgAlpha by animateFloatAsState(if (isPressed) 0.25f else 0.12f, label = "bg")
    val borderAlpha by animateFloatAsState(if (isPressed) 0.8f else 0.35f, label = "border")

    CentinelGlassCard(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        glowColor = if (isPressed) themePrimary else Color.Transparent,
        isSelected = isPressed
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = themePrimary.copy(alpha = iconBgAlpha),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = if (isPressed) 1.5.dp else 1.dp,
                            color = themePrimary.copy(alpha = borderAlpha),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = themePrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(themePrimary.copy(alpha = if (isPressed) 0.2f else 0.1f), CircleShape)
                    .border(1.dp, themePrimary.copy(alpha = if (isPressed) 0.6f else 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = themePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Floating Navigation Dock that noticeably transforms tab colors when theme changes.
 */
@Composable
fun CentinelFloatingNavBar(
    items: List<Screen>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeSurface = MaterialTheme.colorScheme.surface
    val themeUnselected = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .height(78.dp)
                .fillMaxWidth(0.96f),
            color = themeSurface,
            shape = CircleShape,
            border = BorderStroke(1.2.dp, themePrimary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val iconColor by animateColorAsState(if (isSelected) themePrimary else themeUnselected, label = "color")
                    val iconScale by animateFloatAsState(if (isSelected) 1.2f else 1f, label = "scale")

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) themePrimary.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.2.dp else 0.dp,
                                color = if (isSelected) themePrimary.copy(alpha = 0.7f) else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigate(screen.route) }
                            )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (screen.route) {
                                    Screen.Dashboard.route -> Icons.Default.Home
                                    Screen.History.route -> Icons.Default.History
                                    Screen.Analytics.route -> Icons.Default.Analytics
                                    Screen.Notifications.route -> Icons.Default.Notifications
                                    else -> Icons.Default.Home
                                },
                                contentDescription = screen.route,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(iconScale)
                            )
                            if (isSelected) {
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(themePrimary, CircleShape)
                                        .shadow(6.dp, CircleShape, spotColor = themePrimary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Text Field adapting to active theme primary colors on focus.
 */
@Composable
fun CentinelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val themePrimary = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val glowAlpha by animateFloatAsState(if (isFocused) 0.25f else 0f, label = "glow")

    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) themePrimary else TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            interactionSource = interactionSource,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isFocused) themePrimary.copy(alpha = 0.08f) else Color.Transparent,
                    RoundedCornerShape(18.dp)
                )
                .shadow(
                    elevation = if (isFocused) 10.dp else 0.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = themePrimary.copy(alpha = glowAlpha),
                    spotColor = themePrimary.copy(alpha = glowAlpha)
                ),
            shape = RoundedCornerShape(18.dp),
            leadingIcon = icon?.let {
                {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = if (isFocused) themePrimary else TextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = themePrimary,
                unfocusedBorderColor = GlassBorder,
                cursorColor = themePrimary,
                focusedLabelColor = themePrimary,
                unfocusedLabelColor = TextSecondary
            )
        )
    }
}

/**
 * Scanner Base with ambient radial glows adapting to active theme primary color.
 */
@Composable
fun CentinelScannerBase(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeBackground = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(themeBackground)) {
        // Subtle ambient radial glow adapting to theme
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-110).dp, y = (-110).dp)
                .graphicsLayer { alpha = 0.6f }
                .background(Brush.radialGradient(listOf(themePrimary.copy(alpha = 0.2f), Color.Transparent)), CircleShape)
        )

        val columnModifier = if (scrollable) {
            Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxSize().padding(horizontal = 24.dp)
        }

        Column(modifier = columnModifier) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .border(1.dp, themePrimary.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = themePrimary)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = themePrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(1.2.dp, themePrimary.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = themePrimary, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(32.dp))
            if (scrollable) {
                content()
                Spacer(Modifier.height(100.dp))
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun CentinelDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
    )
}

@Composable
fun AnimatedCounter(
    targetValue: Int,
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color = TextPrimary
) {
    val count by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
        label = "counter"
    )
    Text(text = "$count$suffix", style = style, color = color)
}

// Deprecated component aliases for compatibility during migration
@Composable
fun GlassCard(modifier: Modifier = Modifier, cornerRadius: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    CentinelGlassCard(modifier, cornerRadius, content = content)
}

@Composable
fun ToolCardPremium(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CentinelToolCard(title, subtitle, icon, onClick, modifier)
}

@Composable
fun ScannerScreenBase(title: String, subtitle: String, icon: ImageVector, onBack: () -> Unit, scrollable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    CentinelScannerBase(title, subtitle, icon, onBack, scrollable, content)
}
