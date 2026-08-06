package com.centinel.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repo: CentinelRepository) : ViewModel() {

    private val _loginState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val loginState: StateFlow<UiState<Unit>> = _loginState

    private val _registerState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val registerState: StateFlow<UiState<Unit>> = _registerState

    fun login(email: String, password: String) {
        _loginState.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.login(email, password)) {
                is ApiResult.Success -> _loginState.value = UiState.Success(Unit)
                is ApiResult.Error -> _loginState.value = UiState.Error(res.message)
            }
        }
    }

    fun register(email: String, password: String, fullName: String?) {
        _registerState.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.register(email, password, fullName)) {
                is ApiResult.Success -> _registerState.value = UiState.Success(Unit)
                is ApiResult.Error -> _registerState.value = UiState.Error(res.message)
            }
        }
    }

    fun resetLoginState() { _loginState.value = UiState.Idle }
    fun resetRegisterState() { _registerState.value = UiState.Idle }
}
