package com.example.ui

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface InnLookupUiState {
    data object Idle : InnLookupUiState
    data object Loading : InnLookupUiState
    data class Found(val message: String) : InnLookupUiState
    data class Error(val message: String) : InnLookupUiState
}
