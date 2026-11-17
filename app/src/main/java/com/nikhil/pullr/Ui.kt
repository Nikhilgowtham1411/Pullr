package com.nikhil.pullr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** The three sections at the bottom of the screen. */
enum class Tab(val label: String, val icon: Int) {
    SAVED("Saved links", R.drawable.ic_tab_saved),
    DOWNLOADING("Downloading", R.drawable.ic_tab_downloading),
    DOWNLOADED("Downloaded", R.drawable.ic_tab_done),
}

private val Violet = Color(0xFF7C5CFF)
private val Pink = Color(0xFFFF4D9D)
private val Mint = Color(0xFF2EE6A6)
private val Ink = Color(0xFF0B0A12)
private val Panel = Color(0xFF15141E)
private val PanelHigh = Color(0xFF1E1C2A)
private val Line = Color(0xFF2B2940)
private val TextMain = Color(0xFFF2F0FA)
private val TextDim = Color(0xFFA19EB8)
private val Danger = Color(0xFFFF6B7A)
private val Brand = Brush.horizontalGradient(listOf(Violet, Pink))

private val PullrColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = TextMain,
    secondary = Pink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A2350),
    onSecondaryContainer = TextMain,
    background = Ink,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextDim,
    outline = Line,
    error = Danger,
)

@Composable
fun PullrScreen(
    vm: MainViewModel,
    onDownload: (Post, Int, Variant) -> Unit,
    onDownloadAll: (Post) -> Unit,
    onPanelToggle: (Boolean) -> Unit,
    onBatterySettings: () -> Unit,
) {
    val history by DownloadStore.items.collectAsState()
    val links by LinkStore.links.collectAsState()
    val activeCount = history.count { it.isActive }

    MaterialTheme(colorScheme = PullrColors) {
        Scaffold(
            containerColor = Ink,
            topBar = { TopBar(onSettings = { vm.showSettings = true }) },
            bottomBar = {
                BottomTabs(
                    selected = vm.tab,
                    activeCount = activeCount,
                    savedCount = links.size,
                    onSelect = { vm.tab = it },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .fillMaxSize(),
            ) {
                when (vm.tab) {
                    Tab.SAVED -> SavedTab(vm, links, onDownload, onDownloadAll)
                    Tab.DOWNLOADING -> DownloadingTab(history.filter { it.status != Status.DONE })
                    Tab.DOWNLOADED -> DownloadedTab(history.filter { it.status == Status.DONE })
                }
            }
        }
        if (vm.showSettings) SettingsDialog(vm, onPanelToggle, onBatterySettings)
    }
}

// ---- chrome ------------------------------------------------------------------------------

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Pullr", style = TextStyle(brush = Brand, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold))
            Text("Grab any video. Keep it.", fontSize = 13.sp, color = TextDim)
        }
        TextButton(onClick = onSettings) { Text("Settings", color = TextDim) }
    }
}

@Composable
private fun BottomTabs(selected: Tab, activeCount: Int, savedCount: Int, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Panel, contentColor = TextMain, tonalElevation = 0.dp) {
        Tab.values().forEach { tab ->
            val badge = when (tab) {
                Tab.DOWNLOADING -> activeCount
                Tab.SAVED -> savedCount
                else -> 0
            }
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    if (badge > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = if (tab == Tab.DOWNLOADING) Pink else Violet,
                                    contentColor = Color.White,
                                ) { Text("$badge") }
                            },
                        ) { Icon(painterResource(tab.icon), tab.label) }
                    } else {
                        Icon(painterResource(tab.icon), tab.label)
                    }
                },
                label = { Text(tab.label, fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(0xFF3A2F7A),
                    unselectedIconColor = TextDim,
                    unselectedTextColor = TextDim,
                ),
            )
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(Brand, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyState(icon: Int, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(PanelHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = TextDim, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
        Spacer(Modifier.height(6.dp))
        Text(text, color = TextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

// ---- tab 1: saved links ------------------------------------------------------------------

@Composable
private fun SavedTab(
    vm: MainViewModel,
    links: List<SavedLink>,
    onDownload: (Post, Int, Variant) -> Unit,
    onDownloadAll: (Post) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PasteCard(
                value = vm.input,
                onValue = { vm.input = it },
                showClear = vm.input.isNotEmpty() || vm.error != null,
                onPaste = {
                    val pasted = clipboard.getText()?.text
                    if (!pasted.isNullOrBlank()) {
                        vm.input = pasted
                        focus.clearFocus()
                        vm.fetch()
                    }
                },
                onAdd = {
                    focus.clearFocus()
                    vm.fetch()
                },
                onClear = { vm.reset() },
            )
        }

        if (vm.loading > 0) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Violet)
                    Text(
                        if (vm.loading == 1) "Reading the link…" else "Reading ${vm.loading} links…",
                        color = TextDim,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        val err = vm.error
        if (err != null) item { Text(err, color = Danger, fontSize = 14.sp) }

        if (links.isEmpty() && vm.loading == 0) {
            item {
                EmptyState(
                    icon = Tab.SAVED.icon,
                    title = "No saved links yet",
                    text = "Paste a link above, or tap Share in YouTube, Instagram, Facebook, X… and pick Pullr. " +
                        "Pick \"Pullr · Quick save\" to download straight away.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                )
            }
        }

        items(links, key = { it.tweet.id }) { link ->
            LinkCard(
                link = link,
                onDownload = { index, variant -> onDownload(link.tweet, index, variant) },
                onDownloadAll = { onDownloadAll(link.tweet) },
                onRemove = { LinkStore.remove(link.tweet.id) },
            )
        }
    }
}

@Composable
private fun PasteCard(
    value: String,
    onValue: (String) -> Unit,
    showClear: Boolean,
    onPaste: () -> Unit,
    onAdd: () -> Unit,
    onClear: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Paste any video link", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("youtube.com, instagram.com, facebook.com, x.com…") },
            maxLines = 3,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet,
                unfocusedBorderColor = Line,
                cursorColor = Violet,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onAdd() }),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onPaste, shape = RoundedCornerShape(50)) { Text("Paste") }
            GradientButton("Add link", Modifier.weight(1f), onClick = onAdd)
            if (showClear) TextButton(onClick = onClear) { Text("Clear", color = TextDim) }
        }
        Text(
            "YouTube · Instagram · Facebook · X · TikTok · Reddit · Vimeo and 1000+ more sites",
            color = TextDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun LinkCard(
    link: SavedLink,
    onDownload: (Int, Variant) -> Unit,
    onDownloadAll: () -> Unit,
    onRemove: () -> Unit,
) {
    val post = link.tweet
    val multi = post.media.size > 1
    val handle = if (post.authorHandle.isNotEmpty()) "@${post.authorHandle}" else ""
    val who = listOf(post.authorName, handle).filter { it.isNotEmpty() }.joinToString("  ")
    val title = post.text.ifBlank { post.pageUrl }
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape),
    ) {
        Box {
            AsyncImage(
                model = post.media.firstOrNull()?.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(PanelHigh),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(post.source, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xB3000000))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            ) {
                Text("✕", color = Color.White, fontSize = 14.sp)
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TextMain,
            )
            if (who.isNotEmpty()) Text(who, color = TextDim, fontSize = 13.sp)

            if (multi) {
                val total = post.media.sumOf { it.best().size }
                val extra = if (total > 0) " · ${formatSize(total)}" else ""
                GradientButton(
                    text = "Download all (${post.media.size})$extra",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDownloadAll,
                )
            }

            post.media.forEachIndexed { index, media ->
                if (multi) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                MediaBlock(
                    postId = post.id,
                    index = index,
                    multi = multi,
                    media = media,
                    doneLabel = link.downloaded[index],
                    onDownload = { variant -> onDownload(index, variant) },
                )
            }
        }
    }
}

private fun chipLabel(v: Variant): String =
    if (v.size > 0) "${v.label} · ${formatSize(v.size)}" else v.label

@Composable
private fun MediaBlock(
    postId: String,
    index: Int,
    multi: Boolean,
    media: MediaItem,
    doneLabel: String?,
    onDownload: (Variant) -> Unit,
) {
    var selected by rememberSaveable(postId, index) { mutableStateOf(media.variants.indexOf(media.best()).coerceAtLeast(0)) }
    val variant = media.variants[selected.coerceIn(0, media.variants.lastIndex)]
    val kindLabel = when (media.kind) {
        MediaKind.VIDEO -> "Video"
        MediaKind.GIF -> "GIF"
        MediaKind.PHOTO -> "Photo"
        MediaKind.AUDIO -> "Audio"
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (multi) {
            AsyncImage(
                model = media.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PanelHigh),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (multi) "$kindLabel ${index + 1}" else "Choose quality",
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
            )
            if (media.variants.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    media.variants.forEachIndexed { i, v ->
                        FilterChip(
                            selected = i == selected,
                            onClick = { selected = i },
                            label = { Text(chipLabel(v)) },
                        )
                    }
                }
            }
            if (variant.size > 0) {
                Text("Uses about ${formatSize(variant.size)} of data", color = TextDim, fontSize = 13.sp)
            }
            GradientButton(
                text = when {
                    doneLabel != null -> "Download again"
                    variant.isAudio -> "Download MP3"
                    else -> "Download"
                },
                onClick = { onDownload(variant) },
            )
            if (doneLabel != null) {
                Text("Saved before: $doneLabel. Pick another quality to get it again.", color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

// ---- tab 2: downloading ------------------------------------------------------------------

@Composable
private fun DownloadingTab(items: List<HistoryItem>) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        EmptyState(
            icon = Tab.DOWNLOADING.icon,
            title = "Nothing downloading",
            text = "Videos you download show up here with live progress. Cancel or retry any of them.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${items.count { it.isActive }} active", color = TextDim, fontSize = 14.sp)
                Row {
                    if (items.any { it.isActive }) {
                        TextButton(onClick = { DownloadStore.cancelAll() }) { Text("Cancel all") }
                    }
                    if (items.any { !it.isActive }) {
                        TextButton(onClick = { DownloadStore.clearFailed() }) { Text("Clear failed", color = TextDim) }
                    }
                }
            }
        }
        items(items, key = { it.id }) { item ->
            ProgressCard(
                item = item,
                onCancel = { DownloadStore.requestCancel(item.id) },
                onRetry = { DownloadService.retry(context, item) },
                onRemove = { DownloadStore.remove(item.id) },
            )
        }
    }
}

@Composable
private fun ProgressCard(item: HistoryItem, onCancel: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    val failed = item.status == Status.FAILED
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = item.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PanelHigh),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
            )
            Text(
                statusText(item),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                color = if (failed) Danger else TextDim,
            )
            if (item.status == Status.DOWNLOADING && item.progress > 0) {
                LinearProgressIndicator(
                    progress = item.progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50)),
                    color = Violet,
                    trackColor = Line,
                )
            } else if (item.status == Status.DOWNLOADING || item.status == Status.FETCHING) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50)),
                    color = Violet,
                    trackColor = Line,
                )
            }
            Row {
                if (item.isActive) {
                    TextButton(onClick = onCancel) { Text("Cancel", color = Danger) }
                } else {
                    if (item.url != null) TextButton(onClick = onRetry) { Text("Retry") }
                    TextButton(onClick = onRemove) { Text("Remove", color = TextDim) }
                }
            }
        }
    }
}

private fun statusText(item: HistoryItem): String = when (item.status) {
    Status.FETCHING -> "Reading the link…"
    Status.QUEUED -> "Waiting in queue"
    Status.DOWNLOADING -> when {
        item.error != null -> item.error
        item.progress > 0 -> if (item.bytes > 0) "${item.progress}% · ${formatSize(item.bytes)}" else "${item.progress}%"
        item.selector != null -> "Starting… (big videos can take a minute to start)"
        else -> "Starting… ${formatSize(item.bytes)}"
    }
    Status.DONE -> "Saved · ${folderLabel(item.kind)}"
    Status.FAILED -> "Failed: ${item.error ?: "unknown error"}"
    Status.CANCELLED -> "Cancelled"
}

// ---- tab 3: downloaded -------------------------------------------------------------------

@Composable
private fun DownloadedTab(items: List<HistoryItem>) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        EmptyState(
            icon = Tab.DOWNLOADED.icon,
            title = "No downloads yet",
            text = "Finished videos appear here. They are also in your gallery under Movies/Pullr.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${items.size} saved", color = TextDim, fontSize = 14.sp)
                TextButton(onClick = { DownloadStore.clearDone() }) { Text("Clear list", color = TextDim) }
            }
        }
        items(items, key = { it.id }) { item ->
            DoneCard(
                item = item,
                onOpen = { openFile(context, item) },
                onShare = { shareFile(context, item) },
                onDelete = { deleteFile(context, item) },
            )
        }
    }
}

@Composable
private fun DoneCard(item: HistoryItem, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val meta = listOfNotNull(
        if (item.bytes > 0) formatSize(item.bytes) else null,
        folderLabel(item.kind),
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = item.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PanelHigh),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
            )
            Text(meta, color = Mint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GradientButton("Open", onClick = onOpen)
                TextButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = onDelete) { Text("Delete", color = Danger) }
            }
        }
    }
}

private fun openFile(context: Context, item: HistoryItem) {
    val uri = item.uri?.let { Uri.parse(it) } ?: return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, item.mime ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't open it. The file may have been moved or deleted.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, item: HistoryItem) {
    val uri = item.uri?.let { Uri.parse(it) } ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType(item.mime ?: "*/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, "Share"))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share it.", Toast.LENGTH_SHORT).show()
    }
}

/** Deletes the file from the gallery as well as from the list. */
private fun deleteFile(context: Context, item: HistoryItem) {
    item.uri?.let { runCatching { context.contentResolver.delete(Uri.parse(it), null, null) } }
    DownloadStore.remove(item.id)
}

// ---- settings ----------------------------------------------------------------------------

@Composable
private fun SettingsDialog(
    vm: MainViewModel,
    onPanelToggle: (Boolean) -> Unit,
    onBatterySettings: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember(vm.engineMessage) { Engine.version(context) }

    AlertDialog(
        onDismissRequest = { vm.showSettings = false },
        containerColor = Panel,
        title = { Text("Settings", fontWeight = FontWeight.Bold, color = TextMain) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Notification panel", fontWeight = FontWeight.SemiBold, color = TextMain)
                        Text(
                            "Adds a panel to your notification shade. Copy a link, tap Paste & save and it " +
                                "appears in Saved links. No need to open the app.",
                            color = TextDim,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(checked = vm.panelOn, onCheckedChange = onPanelToggle)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Background downloads", fontWeight = FontWeight.SemiBold, color = TextMain)
                    if (vm.batteryOk) {
                        Text("Allowed. Downloads keep going with the screen off.", color = Mint, fontSize = 13.sp)
                    } else {
                        Text(
                            "Some phones pause apps to save battery. Allow Pullr so big downloads finish.",
                            color = TextDim,
                            fontSize = 13.sp,
                        )
                        OutlinedButton(onClick = onBatterySettings) { Text("Allow") }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Downloader engine", fontWeight = FontWeight.SemiBold, color = TextMain)
                    Text(
                        "Version $version. Sites change often, so it updates itself daily. " +
                            "If a link stops working, update it now.",
                        color = TextDim,
                        fontSize = 13.sp,
                    )
                    val message = vm.engineMessage
                    if (message != null) Text(message, color = Violet, fontSize = 13.sp)
                    OutlinedButton(onClick = { vm.updateEngine(context) }, enabled = !vm.engineBusy) {
                        Text(if (vm.engineBusy) "Updating…" else "Update engine")
                    }
                }

                Text(
                    "Only save videos you own or have permission to download, and follow each site's terms.",
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.showSettings = false }) { Text("Done") } },
    )
}
