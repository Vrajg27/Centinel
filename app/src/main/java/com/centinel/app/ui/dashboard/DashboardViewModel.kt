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
    val analyticsState: StateFlow<AnalyticsOut?> = repo.analyticsCache

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun loadAnalytics() {
        if (_isRefreshing.value) return
        
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repo.getAnalytics()
            } catch (e: Exception) {
                // Ignore background errors
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
