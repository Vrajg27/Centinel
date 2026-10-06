package com.centinel.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.centinel.app.data.model.UserOut
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: CentinelRepository) : ViewModel() {
    val settingsStore = repo.settingsStore

    private val _userState = MutableStateFlow<UiState<UserOut>>(UiState.Idle)
    val userState: StateFlow<UiState<UserOut>> = _userState

    private val _updateState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val updateState: StateFlow<UiState<Unit>> = _updateState

    private val _passwordState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val passwordState: StateFlow<UiState<Unit>> = _passwordState

    fun loadUser() {
        _userState.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.me()) {
                is ApiResult.Success -> _userState.value = UiState.Success(res.data)
                is ApiResult.Error -> _userState.value = UiState.Error(res.message)
            }
        }
    }

    fun updateProfile(fullName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _updateState.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.updateProfile(fullName)) {
                is ApiResult.Success -> {
                    _userState.value = UiState.Success(res.data)
                    _updateState.value = UiState.Success(Unit)
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _updateState.value = UiState.Error(res.message)
                    onError(res.message)
                }
            }
        }
    }

    fun changePassword(oldPass: String, newPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _passwordState.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.changePassword(oldPass, newPass)) {
                is ApiResult.Success -> {
                    _passwordState.value = UiState.Success(Unit)
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _passwordState.value = UiState.Error(res.message)
                    onError(res.message)
                }
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            repo.logout()
            onComplete()
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            when (repo.deleteAccount()) {
                is ApiResult.Success -> {
                    repo.tokenStore.clear()
                    onComplete()
                }
                else -> {}
            }
        }
    }
}
