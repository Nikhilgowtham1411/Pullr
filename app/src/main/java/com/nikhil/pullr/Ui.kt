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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** The three sections at the bottom of the screen. */
enum class Tab(val label: String, val icon: Int) {
    SAVED("Saved", R.drawable.ic_tab_saved),
    DOWNLOADING("Downloading", R.drawable.ic_tab_downloading),
    DOWNLOADED("Downloaded", R.drawable.ic_tab_done),
}

private val Lime = Color(0xFFC8F135)
private val OnLime = Color(0xFF10140A)
private val Bg = Color(0xFF0B0B0C)
private val Card = Color(0xFF151517)
private val CardHigh = Color(0xFF212125)
private val Line = Color(0xFF2A2A2F)
private val TextMain = Color(0xFFF5F5F7)
private val TextDim = Color(0xFF8E8E96)
private val Danger = Color(0xFFFF6B6B)

/** Poppins is bundled so the app looks the same on every phone, whatever system font is set. */
private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_medium, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_bold, FontWeight.ExtraBold),
)

private fun poppinsTypography(): Typography {
    val b = Typography()
    fun TextStyle.p() = copy(fontFamily = Poppins)
    return Typography(
        displayLarge = b.displayLarge.p(), displayMedium = b.displayMedium.p(), displaySmall = b.displaySmall.p(),
        headlineLarge = b.headlineLarge.p(), headlineMedium = b.headlineMedium.p(), headlineSmall = b.headlineSmall.p(),
        titleLarge = b.titleLarge.p(), titleMedium = b.titleMedium.p(), titleSmall = b.titleSmall.p(),
        bodyLarge = b.bodyLarge.p(), bodyMedium = b.bodyMedium.p(), bodySmall = b.bodySmall.p(),
        labelLarge = b.labelLarge.p(), labelMedium = b.labelMedium.p(), labelSmall = b.labelSmall.p(),
    )
}

private val PullrColors = darkColorScheme(
    primary = Lime,
    onPrimary = OnLime,
    primaryContainer = CardHigh,
    onPrimaryContainer = TextMain,
    secondary = Lime,
    onSecondary = OnLime,
    background = Bg,
    onBackground = TextMain,
    surface = Card,
    onSurface = TextMain,
    surfaceVariant = CardHigh,
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

    MaterialTheme(colorScheme = PullrColors, typography = poppinsTypography()) {
        Scaffold(
            containerColor = Bg,
            topBar = { TopBar(onSettings = { vm.showSettings = true }) },
            bottomBar = {
                FloatingNav(selected = vm.tab, activeCount = activeCount, onSelect = { vm.tab = it })
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
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
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Pullr", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            Box(
                Modifier
                    .padding(start = 3.dp, bottom = 9.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Lime),
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Card)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_settings), "Settings", tint = TextMain, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun FloatingNav(selected: Tab, activeCount: Int, onSelect: (Tab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Card)
                .border(1.dp, Line, RoundedCornerShape(50))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.values().forEach { tab ->
                val on = tab == selected
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Lime else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = if (on) 18.dp else 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box {
                        Icon(
                            painterResource(tab.icon),
                            tab.label,
                            tint = if (on) OnLime else TextDim,
                            modifier = Modifier.size(22.dp),
                        )
                        if (tab == Tab.DOWNLOADING && activeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (on) OnLime else Lime),
                            )
                        }
                    }
                    if (on) Text(tab.label, color = OnLime, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LimeButton(
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
            .background(Lime)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = OnLime, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SoftButton(text: String, color: Color = TextMain, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(CardHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Lime else CardHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (selected) OnLime else TextMain,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyState(icon: Int, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Lime, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
        Spacer(Modifier.height(6.dp))
        Text(text, color = TextDim, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PasteBar(
                value = vm.input,
                onValue = { vm.input = it },
                onAdd = {
                    focus.clearFocus()
                    vm.fetch()
                },
                onPaste = {
                    val pasted = clipboard.getText()?.text
                    if (!pasted.isNullOrBlank()) {
                        vm.input = pasted
                        focus.clearFocus()
                        vm.fetch()
                    }
                },
                showClear = vm.input.isNotEmpty() || vm.error != null,
                onClear = { vm.reset() },
            )
        }

        if (vm.loading > 0) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Lime)
                    Text(
                        if (vm.loading == 1) "Reading the link…" else "Reading ${vm.loading} links…",
                        color = TextDim,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        val err = vm.error
        if (err != null) {
            item {
                Text(
                    err,
                    color = Danger,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22FF6B6B))
                        .padding(14.dp),
                )
            }
        }

        if (links.isEmpty() && vm.loading == 0) {
            item {
                EmptyState(
                    icon = Tab.SAVED.icon,
                    title = "Nothing saved yet",
                    text = "Paste a link above, or tap Share in Instagram, YouTube, Facebook, X… and pick Pullr. " +
                        "\"Pullr · Quick save\" downloads straight away.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
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
private fun PasteBar(
    value: String,
    onValue: (String) -> Unit,
    onAdd: () -> Unit,
    onPaste: () -> Unit,
    showClear: Boolean,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste a video or photo link", color = TextDim) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Card,
                unfocusedContainerColor = Card,
                focusedBorderColor = Lime,
                unfocusedBorderColor = Line,
                cursorColor = Lime,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onAdd() }),
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Lime)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_arrow), "Add link", tint = OnLime, modifier = Modifier.size(20.dp))
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CardHigh)
                    .clickable(onClick = onPaste)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_paste), null, tint = Lime, modifier = Modifier.size(16.dp))
                Text("Paste & save", color = TextMain, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            if (showClear) SoftButton("Clear", TextDim, onClear)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("Instagram", "YouTube", "Facebook", "X", "TikTok", "Reddit", "Vimeo", "+1000 sites").forEach {
                Text(
                    it,
                    color = TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Line, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
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
    val shape = RoundedCornerShape(26.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, Line, shape),
    ) {
        Box {
            AsyncImage(
                model = post.media.firstOrNull()?.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(CardHigh),
            )
            Text(
                if (multi) "${post.source} · ${post.media.size} items" else post.source,
                color = OnLime,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Lime)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_close), "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextMain,
                )
                if (who.isNotEmpty()) Text(who, color = TextDim, fontSize = 12.sp, maxLines = 1)
            }

            if (multi) {
                val total = post.media.sumOf { it.best().size }
                val extra = if (total > 0) " · ${formatSize(total)}" else ""
                LimeButton(
                    text = "Download all ${post.media.size}$extra",
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

private fun pillLabel(v: Variant): String =
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
    var selected by rememberSaveable(postId, index) {
        mutableStateOf(media.variants.indexOf(media.best()).coerceAtLeast(0))
    }
    val variant = media.variants[selected.coerceIn(0, media.variants.lastIndex)]
    val kindLabel = when (media.kind) {
        MediaKind.VIDEO -> "Video"
        MediaKind.GIF -> "GIF"
        MediaKind.PHOTO -> "Photo"
        MediaKind.AUDIO -> "Audio"
    }
    val sizeText = if (variant.size > 0) " · ${formatSize(variant.size)}" else ""
    val buttonText = when {
        variant.isAudio -> "MP3$sizeText"
        doneLabel != null -> "Again$sizeText"
        media.kind == MediaKind.PHOTO -> "Save photo$sizeText"
        else -> "Download$sizeText"
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        if (multi) {
            AsyncImage(
                model = media.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardHigh),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (multi) Text("$kindLabel ${index + 1}", fontWeight = FontWeight.SemiBold, color = TextMain, fontSize = 14.sp)
            if (media.variants.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    media.variants.forEachIndexed { i, v ->
                        Pill(pillLabel(v), selected = i == selected, onClick = { selected = i })
                    }
                }
            }
            LimeButton(
                text = buttonText,
                modifier = if (multi) Modifier else Modifier.fillMaxWidth(),
                onClick = { onDownload(variant) },
            )
            if (doneLabel != null) {
                Text("Saved before ($doneLabel)", color = TextDim, fontSize = 11.sp)
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
            text = "Files you download show up here with live progress. Cancel or retry any of them.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${items.count { it.isActive }} active", color = TextDim, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (items.any { it.isActive }) SoftButton("Cancel all", Danger) { DownloadStore.cancelAll() }
                    if (items.any { !it.isActive }) SoftButton("Clear failed", TextDim) { DownloadStore.clearFailed() }
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
private fun Thumb(url: String?, size: Int = 72) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardHigh),
    )
}

@Composable
private fun ProgressCard(item: HistoryItem, onCancel: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    val failed = item.status == Status.FAILED
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, Line, shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(item.thumb)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
            Text(
                statusText(item),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = if (failed) Danger else TextDim,
            )
            if (item.status == Status.DOWNLOADING && item.progress > 0) {
                LinearProgressIndicator(
                    progress = item.progress / 100f,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                    color = Lime,
                    trackColor = Line,
                )
            } else if (item.status == Status.DOWNLOADING || item.status == Status.FETCHING) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                    color = Lime,
                    trackColor = Line,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.isActive) {
                    SoftButton("Cancel", Danger, onCancel)
                } else {
                    if (item.url != null) SoftButton("Retry", Lime, onRetry)
                    SoftButton("Remove", TextDim, onRemove)
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
            text = "Finished files appear here. They are also in your gallery under Movies/Pullr and Pictures/Pullr.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${items.size} saved", color = TextDim, fontSize = 14.sp)
                SoftButton("Clear list", TextDim) { DownloadStore.clearDone() }
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
    val shape = RoundedCornerShape(22.dp)
    val meta = listOfNotNull(
        if (item.bytes > 0) formatSize(item.bytes) else null,
        folderLabel(item.kind),
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, Line, shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(item.thumb)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
            Text(meta, color = Lime, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LimeButton("Open", onClick = onOpen)
                SoftButton("Share", onClick = onShare)
                SoftButton("Delete", Danger, onDelete)
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
        containerColor = Card,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Settings", fontWeight = FontWeight.Bold, color = TextMain) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Notification panel", fontWeight = FontWeight.SemiBold, color = TextMain)
                        Text(
                            "Adds a panel to your notification shade. Copy a link, tap Paste & save and it " +
                                "appears in Saved. No need to open the app.",
                            color = TextDim,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    Switch(
                        checked = vm.panelOn,
                        onCheckedChange = onPanelToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnLime,
                            checkedTrackColor = Lime,
                            uncheckedThumbColor = TextDim,
                            uncheckedTrackColor = CardHigh,
                            uncheckedBorderColor = Line,
                        ),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Background downloads", fontWeight = FontWeight.SemiBold, color = TextMain)
                    if (vm.batteryOk) {
                        Text("Allowed. Downloads keep going with the screen off.", color = Lime, fontSize = 12.sp)
                    } else {
                        Text(
                            "Some phones pause apps to save battery. Allow Pullr so big downloads finish.",
                            color = TextDim,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                        SoftButton("Allow", Lime, onBatterySettings)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Video engine", fontWeight = FontWeight.SemiBold, color = TextMain)
                    Text(
                        "Version $version. Sites change often, so it updates itself daily. " +
                            "If a link stops working, update it now.",
                        color = TextDim,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    val message = vm.engineMessage
                    if (message != null) Text(message, color = Lime, fontSize = 12.sp)
                    SoftButton(if (vm.engineBusy) "Updating…" else "Update engine", Lime) {
                        if (!vm.engineBusy) vm.updateEngine(context)
                    }
                }

                Text(
                    "Only save content you own or have permission to download, and follow each site's terms.",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.showSettings = false }) { Text("Done", color = Lime, fontWeight = FontWeight.Bold) } },
    )
}
