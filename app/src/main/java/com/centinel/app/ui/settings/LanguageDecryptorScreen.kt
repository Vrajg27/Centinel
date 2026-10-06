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
fun LanguageDecryptorScreen(
    repo: CentinelRepository,
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val settingsStore = vm.settingsStore

    val languages = listOf(
        "English (US)" to "Default Terminal Specification",
        "Spanish (Español)" to "Multilingual Decryptor Pack",
        "French (Français)" to "Multilingual Decryptor Pack",
        "German (Deutsch)" to "Multilingual Decryptor Pack",
        "Japanese (日本語)" to "Multilingual Decryptor Pack",
        "Cyber-Binary Mode" to "Translates headings into binary & hex cyber notation"
    )

    var selectedLang by remember { mutableStateOf(settingsStore.appLanguage) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                    "Language Decryptor Interface",
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
                    .background(Brush.radialGradient(listOf(GlowBlue.copy(alpha = 0.15f), Color.Transparent)), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Globe Hero Card
                CentinelGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = PrimaryGlow.copy(alpha = 0.2f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.linearGradient(listOf(AccentCyan.copy(alpha = 0.2f), Color.Transparent)),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("LOCALIZATION & DECRYPTION", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                            Text("Language Decryptor", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Select language translation matrix for terminal UI.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "AVAILABLE LANGUAGE PACKS",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )

                languages.forEach { (langName, description) ->
                    val isSelected = selectedLang == langName
                    CentinelGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable { selectedLang = langName },
                        cornerRadius = 18.dp,
                        glowColor = if (isSelected) AccentCyanGlow else Color.Transparent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedLang = langName },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    langName,
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

                Spacer(Modifier.height(32.dp))

                CentinelButton(
                    text = "Apply Language Decryption",
                    onClick = {
                        settingsStore.appLanguage = selectedLang
                        scope.launch {
                            snackbarHostState.showSnackbar("System language set to $selectedLang.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Save
                )

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
