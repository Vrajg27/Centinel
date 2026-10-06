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
fun TerminateAccountScreen(
    repo: CentinelRepository,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    var isConfirmed by remember { mutableStateOf(false) }
    var confirmInput by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val canPurge = isConfirmed && confirmInput.trim().equals("TERMINATE", ignoreCase = true)

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
                    "Account Termination Interface",
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
                    .align(Alignment.TopCenter)
                    .offset(y = (-50).dp)
                    .background(Brush.radialGradient(listOf(DangerRed.copy(alpha = 0.2f), Color.Transparent)), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Warning Hero Card
                Surface(
                    color = DangerRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, DangerRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(DangerRed.copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, DangerRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(36.dp))
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "CRITICAL WARNING",
                            style = MaterialTheme.typography.labelSmall,
                            color = DangerRed,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )

                        Text(
                            "PERMANENT ACCOUNT PURGE",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Terminating your account will permanently wipe your profile identity, cryptographic authorization tokens, FCM push registration, and all decentralized scan history records. This action is IRREVERSIBLE.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Purge Check & Re-Authentication Form
                CentinelGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isConfirmed = !isConfirmed }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = isConfirmed,
                                onCheckedChange = { isConfirmed = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = DangerRed,
                                    uncheckedColor = TextTertiary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "I understand that this action is permanent and all data will be purged.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Type 'TERMINATE' to confirm account purge:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = confirmInput,
                            onValueChange = { confirmInput = it },
                            placeholder = { Text("TERMINATE", color = TextTertiary) },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = DangerRed,
                                unfocusedBorderColor = GlassBorder,
                                cursorColor = DangerRed
                            )
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Purge Action Button
                CentinelButton(
                    text = "Purge Account Permanently",
                    onClick = {
                        if (canPurge) {
                            isDeleting = true
                            vm.deleteAccount(onLogout)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Please check the confirmation box and type TERMINATE.")
                            }
                        }
                    },
                    loading = isDeleting,
                    enabled = canPurge && !isDeleting,
                    colors = listOf(DangerRed, Color(0xFF880000)),
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.DeleteForever
                )

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onBack,
                    border = BorderStroke(1.dp, GlassBorder),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Abort Termination & Return", color = TextPrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
