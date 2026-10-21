package com.pescasub.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Crea un ViewModel con dependencias manuales (sin Hilt). */
inline fun <reified VM : ViewModel> vmFactory(crossinline build: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
    }
