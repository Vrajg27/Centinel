package com.centinel.app.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.BreachScanResponse
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.PrimaryButton
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.theme.threatLevelColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BreachCheckerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<BreachScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<BreachScanResponse>> = _state

    fun scan(email: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanBreach(email)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreachCheckerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: BreachCheckerViewModel = viewModel(factory = ViewModelFactory(repo))
    var email by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Data Breach Checker") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Check whether an email address has appeared in known data breaches.")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Email address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Check for Breaches", loading = state is UiState.Loading, enabled = email.isNotBlank()) {
                vm.scan(email.trim())
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is UiState.Success -> {
                    val color = threatLevelColor(s.data.threat_level)
                    val breaches = s.data.details?.jsonObject?.get("breaches_found")?.jsonArray
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${s.data.risk_score}/100 • ${s.data.threat_level}", fontWeight = FontWeight.Bold, color = color)
                            Spacer(Modifier.height(8.dp))
                            Text(s.data.explanation)
                            if (breaches != null && breaches.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Breaches found:", fontWeight = FontWeight.SemiBold)
                                breaches.forEach { Text("• ${it.jsonPrimitive.content}") }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Recommendations", fontWeight = FontWeight.SemiBold)
                            s.data.recommendations.forEach { Text("• $it") }
                        }
                    }
                }
                is UiState.Error -> ErrorBanner(s.message)
                else -> {}
            }
        }
    }
}
