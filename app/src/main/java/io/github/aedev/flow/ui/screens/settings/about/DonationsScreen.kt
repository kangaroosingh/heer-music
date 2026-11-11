package io.github.aedev.flow.ui.screens.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage

@Composable
fun DonationsScreen(onNavigateBack: () -> Unit) {
    val links = LocalUriHandler.current
    SettingsPage(title = stringResource(R.string.hm_project_support), onBack = onNavigateBack) {
        item("heer.support") {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.hm_project_support_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { links.openUri("https://github.com/kangaroosingh/heer-music/issues") }) {
                    Text(stringResource(R.string.hm_report_feedback))
                }
            }
        }
    }
}
