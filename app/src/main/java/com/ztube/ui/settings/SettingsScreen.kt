package com.ztube.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.ui.components.BackTopBar
import com.ztube.ui.components.SwitchRow

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val sponsorBlock by viewModel.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplay.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Settings", onBack)
        SwitchRow(
            title = "Skip sponsor segments",
            checked = sponsorBlock,
            onCheckedChange = viewModel::setSponsorBlockEnabled,
        )
        SwitchRow(
            title = "Autoplay",
            subtitle = "Play the next video when one ends",
            checked = autoplay,
            onCheckedChange = viewModel::setAutoplay,
        )
    }
}
