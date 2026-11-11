package io.github.aedev.flow.ui.screens.listening

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R

@Composable
internal fun ListeningLinkDialog(
    link: String,
    books: Boolean,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hm_add_link)) },
        text = {
            OutlinedTextField(value = link, onValueChange = onChange, label = {
                Text(stringResource(if (books) R.string.hm_youtube_book_link else R.string.hm_feed_link))
            }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = onAdd, enabled = link.isNotBlank()) { Text(stringResource(R.string.hm_add_link)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
