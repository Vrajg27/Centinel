package com.centinel.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.centinel.app.data.model.AnalyticsOut
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _analyticsState = MutableStateFlow<UiState<AnalyticsOut>>(UiState.Idle)
    val analyticsState: StateFlow<UiState<AnalyticsOut>> = _analyticsState

    fun loadAnalytics() {
        _analyticsState.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.getAnalytics()) {
                is ApiResult.Success -> _analyticsState.value = UiState.Success(res.data)
                is ApiResult.Error -> _analyticsState.value = UiState.Error(res.message)
            }
        }
    }
}
