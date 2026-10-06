package com.centinel.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

@Composable
fun VisualInterfaceScreen(
    repo: CentinelRepository,
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val settingsStore = vm.settingsStore

    val themes = listOf(
        "Cobalt Blue" to "Deep midnight navy & royal cobalt blue palette",
        "Crimson Red" to "Charcoal dark background with vivid crimson red accents",
        "Obsidian Black" to "Pure pitch black (#000000) for OLED power efficiency",
        "Electric Purple" to "Deep night purple & vibrant violet accent palette",
        "Emerald Green" to "Dark cyber emerald & green matrix security theme",
        "Cyber Amber" to "Steampunk copper background with warm brass & amber accents"
    )

    var selectedTheme by remember { mutableStateOf(settingsStore.themeMode) }
    var orbs by remember { mutableStateOf(settingsStore.backgroundOrbs) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun selectTheme(themeName: String) {
        selectedTheme = themeName
        settingsStore.themeMode = themeName
        scope.launch {
            snackbarHostState.showSnackbar("Visual theme set to $themeName.")
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(SurfaceVariant, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Visual Interface Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 100.dp, y = (-50).dp)
                    .background(Brush.radialGradient(listOf(GlowPurple.copy(alpha = 0.15f), Color.Transparent)), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Palette Hero Card
                CentinelGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = PrimaryGlow.copy(alpha = 0.2f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), Color.Transparent)),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("COLOR PALETTE CONTROL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("Visual Theme Palette", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Tap any theme option below to instantly switch the UI theme.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Theme Mode Cards
                Text(
                    "INTERFACE COLOR THEME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )

                themes.forEach { (themeName, description) ->
                    val isSelected = selectedTheme == themeName
                    CentinelGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable { selectTheme(themeName) },
                        cornerRadius = 18.dp,
                        glowColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectTheme(themeName) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    themeName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Particle & Glow Controls
                Text(
                    "GRAPHICAL EFFECTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )

                CentinelGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    SettingsToggleRow(
                        title = "Background Ambient Glows",
                        subtitle = "Render soft radial color glows on card headers",
                        checked = orbs,
                        onCheckedChange = {
                            orbs = it
                            settingsStore.backgroundOrbs = it
                        }
                    )
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
