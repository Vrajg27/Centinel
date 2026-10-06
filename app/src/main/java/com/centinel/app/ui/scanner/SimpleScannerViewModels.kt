package com.centinel.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.centinel.app.data.model.ScanResult
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UrlScannerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ScanResult>>(UiState.Idle)
    val state: StateFlow<UiState<ScanResult>> = _state

    fun scan(url: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanUrl(url)) {
                is ApiResult.Success -> {
                    _state.value = UiState.Success(res.data)
                    repo.getAnalytics() // Live update global stats
                }
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}

class SmsScannerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ScanResult>>(UiState.Idle)
    val state: StateFlow<UiState<ScanResult>> = _state

    fun scan(message: String, sender: String?) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanSms(message, sender)) {
                is ApiResult.Success -> {
                    _state.value = UiState.Success(res.data)
                    repo.getAnalytics() // Live update global stats
                }
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}

class QrScannerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ScanResult>>(UiState.Idle)
    val state: StateFlow<UiState<ScanResult>> = _state

    fun scan(decodedText: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanQr(decodedText)) {
                is ApiResult.Success -> {
                    _state.value = UiState.Success(res.data)
                    repo.getAnalytics() // Live update global stats
                }
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}
