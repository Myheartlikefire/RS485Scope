package com.serialscope.rs485.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle aware collector used by every panel. */
@Composable
fun <T> StateFlow<T>.collectAsStateValue(): State<T> = collectAsStateWithLifecycle()