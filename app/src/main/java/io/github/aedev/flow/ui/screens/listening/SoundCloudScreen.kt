package io.github.aedev.flow.ui.screens.listening

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.listening.SoundCloudLinks
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.layout.flowBottomContentPadding

@Composable
internal fun SoundCloudScreen(vm: SoundCloudViewModel = hiltViewModel()) {
    val mixes by vm.mixes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val invalid by vm.invalid.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = flowBottomContentPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.hm_soundcloud), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.hm_soundcloud_help))
            if (offline) Text(stringResource(R.string.hm_soundcloud_offline), style = MaterialTheme.typography.bodySmall)
        }
        item {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                enabled = !busy,
                label = { Text(stringResource(R.string.hm_soundcloud_link)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.add(link) }, enabled = !busy && link.isNotBlank()) { Text(stringResource(R.string.hm_soundcloud_save)) }
            if (busy) Text(stringResource(R.string.hm_loading_sources))
            if (invalid) Text(stringResource(R.string.hm_soundcloud_invalid))
            if (mixes.isEmpty()) Text(stringResource(R.string.hm_soundcloud_empty))
        }
        items(mixes, key = { it.url }) { mix ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(mix.title, style = MaterialTheme.typography.titleMedium)
                    Text(mix.url, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !offline, onClick = {
                            EnhancedMusicPlayerManager.pause()
                            selected = mix.url
                        }) { Text(stringResource(R.string.hm_soundcloud_play)) }
                        TextButton(onClick = { vm.remove(mix.url) }) { Text(stringResource(R.string.hm_soundcloud_remove)) }
                    }
                }
            }
        }
    }
    selected?.let { SoundCloudWidget(it, onDismiss = { selected = null }) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SoundCloudWidget(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // The free widget is foreground-only. Closing/backgrounding destroys it and stops its audio.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hm_soundcloud)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.hm_soundcloud_limits), style = MaterialTheme.typography.bodySmall)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.mediaPlaybackRequiresUserGesture = true
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        if (!request.isForMainFrame) return false
                                        if (request.url.scheme == "https" && request.url.host == "w.soundcloud.com") return false
                                        if (request.isForMainFrame && request.url.scheme == "https") {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                        }
                                        return true
                                    }
                                }
                            val player = SoundCloudLinks.widget(url).replace("&", "&amp;")
                            loadDataWithBaseURL(
                                "https://heer-music.invalid/",
                                """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><iframe title="SoundCloud player" width="100%" height="300" scrolling="yes" frameborder="0" allow="autoplay" src="$player"></iframe></body></html>""",
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                    onRelease = {
                        it.stopLoading()
                        it.loadUrl("about:blank")
                        it.destroy()
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hm_soundcloud_close)) } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }) { Text(stringResource(R.string.hm_soundcloud_open)) }
        },
    )
}
