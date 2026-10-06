package com.centinel.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.centinel.app.data.repository.CentinelRepository

class ViewModelFactory(private val repo: CentinelRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return modelClass.getConstructor(CentinelRepository::class.java).newInstance(repo)
    }
}
