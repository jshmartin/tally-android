package com.example.tally.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.tally.TallyApp

fun CreationExtras.tallyApp(): TallyApp = this[APPLICATION_KEY] as TallyApp
