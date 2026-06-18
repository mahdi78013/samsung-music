package com.mahdi.musicpro.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahdi.musicpro.data.PlaylistEntity
import com.mahdi.musicpro.data.Track
import com.mahdi.musicpro.data.TrackLibrary
import com.mahdi.musicpro.R
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

// Color parsing utility with a thread-safe cache for high-performance dynamic gradients
private val colorCache = java.util.concurrent.ConcurrentHashMap<String, Color>()

fun parseColor(hex: String): Color {
    return colorCache.getOrPut(hex) {
        try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            Color.Gray
        }
    }
}

@Composable
fun TrackCover(
    track: Track,
    modifier: Modifier = Modifier,
    fallbackIconSize: androidx.compose.ui.unit.Dp = 20.dp,
    contentDescription: String? = null
) {
    var isError by remember(track.id) { mutableStateOf(false) }
    
    val startColor = remember(track.coverGradientStart) { parseColor(track.coverGradientStart) }
    val endColor = remember(track.coverGradientEnd) { parseColor(track.coverGradientEnd) }
    val gradientBrush = remember(startColor, endColor) {
        Brush.linearGradient(colors = listOf(startColor, endColor))
    }

    Box(
        modifier = modifier.background(gradientBrush),
        contentAlignment = Alignment.Center
    ) {
        if (!track.albumArtUri.isNullOrEmpty() && !isError) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(track.albumArtUri)
                    .crossfade(200)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { isError = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(fallbackIconSize)
            )
        }
    }
}

@Composable
fun MiniPlayerProgressLine(
    durationMs: Long,
    progressMs: Long,
    modifier: Modifier = Modifier
) {
    val percentFinished = if (durationMs > 0) progressMs.toFloat() / durationMs else 0f
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.12f))
                    .align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percentFinished)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
                            ),
                            shape = RoundedCornerShape(2.5.dp)
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicProUi(
    viewModel: MusicViewModel,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsState()
    val progressMsState = viewModel.progressMs.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds = remember(favorites) { favorites.map { it.id }.toSet() }
    val playlists by viewModel.playlists.collectAsState()
    val playHistory by viewModel.playHistory.collectAsState()
    val activePlaylistTracks by viewModel.activePlaylistTracks.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val earphonesState by viewModel.earphonesState.collectAsState()

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val granted = permissionsMap.values.any { it }
        if (granted) {
            viewModel.loadTracksFromDevice()
        }
    }

    val pendingDeleteSender by viewModel.pendingDeleteSender.collectAsState()
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.loadTracksFromDevice()
        }
        viewModel.clearPendingDeleteSender()
    }

    LaunchedEffect(pendingDeleteSender) {
        val sender = pendingDeleteSender
        if (sender != null) {
            try {
                val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                deleteLauncher.launch(intentSenderRequest)
            } catch (e: Exception) {
                android.util.Log.e("MusicProUi", "Error launching pending delete intent sender", e)
                viewModel.clearPendingDeleteSender()
            }
        }
    }

    // Bottom Sheets & Dialog States
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddTrackToPlaylistDialog by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var trackSelectedForPlaylistAdding by remember { mutableStateOf<Track?>(null) }
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    var isMiniPlayerClosedBySwipe by rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(uiState.currentTrack?.id) {
        if (uiState.currentTrack != null) {
            isMiniPlayerClosedBySwipe = false
        }
    }
    
    LaunchedEffect(uiState.shouldExpandPlayer) {
        if (uiState.shouldExpandPlayer) {
            isPlayerExpanded = true
            viewModel.onPlayerExpansionHandled()
        }
    }
    var showQueuePanel by remember { mutableStateOf(false) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var sortOption by rememberSaveable { mutableStateOf(TrackSortOption.DATE_ADDED) }
    var showTopBarSortMenu by remember { mutableStateOf(false) }
    
    // Lifted top-level multi-select states
    var isMultiSelectActive by rememberSaveable { mutableStateOf(false) }
    val selectedTrackIds = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { mutableStateListOf<String>().apply { addAll(it) } }
        )
    ) {
        mutableStateListOf<String>()
    }
    var showBulkAddToPlaylistDialog by remember { mutableStateOf(false) }

    // Equalizer logic simulation info
    val eqState = uiState.equalizer

    // Background color theme: Elegant Dark Music Pro Slate
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F1016),
            Color(0xFF161824),
            Color(0xFF1B1D2E)
        )
    )

    BackHandler(enabled = isPlayerExpanded || showSettingsScreen || isMultiSelectActive) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (showSettingsScreen) {
            showSettingsScreen = false
        } else if (isMultiSelectActive) {
            isMultiSelectActive = false
            selectedTrackIds.clear()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                    start = innerPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
                )
        ) {
            // 1. Sleek Head/Toolbar (with collapsible search or multi-select state)
            if (isMultiSelectActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("multiselect_toolbar"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                isMultiSelectActive = false
                                selectedTrackIds.clear()
                            },
                            modifier = Modifier.testTag("action_cancel_multiselect")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${selectedTrackIds.size} انتخاب شده",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = Color.White
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (selectedTrackIds.isNotEmpty()) {
                                    viewModel.deleteTracks(selectedTrackIds.toList())
                                    isMultiSelectActive = false
                                    selectedTrackIds.clear()
                                }
                            },
                            modifier = Modifier.testTag("multi_delete")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF5350)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedTrackIds.isNotEmpty()) {
                                    showBulkAddToPlaylistDialog = true
                                }
                            },
                            modifier = Modifier.testTag("multi_add_to_playlist")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to playlist",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedTrackIds.isNotEmpty()) {
                                    val selectedTracks = allTracks.filter { selectedTrackIds.contains(it.id) }
                                    val shareText = selectedTracks.joinToString("\n") { "'${it.title}' by ${it.artist}" }
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, "Check out these songs:\n$shareText")
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                }
                            },
                            modifier = Modifier.testTag("multi_share")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                    }
                }
            } else if (!isSearchExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                fontSize = 22.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.smart_music_player),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray.copy(alpha = 0.7f)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isSearchExpanded = true },
                            modifier = Modifier.testTag("action_toggle_search")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { showEqualizerSheet = true },
                            modifier = Modifier.testTag("action_equalizer")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { showSleepTimerSheet = true },
                            modifier = Modifier.testTag("action_timer")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Sleep Timer",
                                tint = if (uiState.sleepTimerMinutesLeft != null) Color(0xFFFF4B2B) else Color.White
                            )
                        }

                        var showSettingsMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showSettingsMenu = true },
                                modifier = Modifier.testTag("action_settings_menu_trigger")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Menu",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showSettingsMenu,
                                onDismissRequest = { showSettingsMenu = false },
                                modifier = Modifier.background(Color(0xFF1F2133))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_title), color = Color.White, fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title), tint = Color.White) },
                                    onClick = {
                                        showSettingsMenu = false
                                        showSettingsScreen = true
                                    },
                                    modifier = Modifier.testTag("action_settings_item")
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isSearchExpanded = false
                            viewModel.updateSearchQuery("")
                        },
                        modifier = Modifier.testTag("action_collapse_search")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.search_placeholder), color = Color.Gray, fontSize = 14.sp) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.LightGray)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF416C),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_input")
                    )
                }
            }

            // 2. Scrollable Tabs Row with Pill/Capsule Styling
            val tabs = listOf("آهنگ‌ها", "لیست‌های پخش", "آلبوم‌ها", "هنرمندان", "علاقه‌مندی‌ها", "تاریخچه")
            ScrollableTabRow(
                selectedTabIndex = uiState.activeTab,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                edgePadding = 16.dp,
                indicator = {}, // Disable default indicator line
                divider = {} // Disable default bottom divider line
            ) {
                tabs.forEachIndexed { index, title ->
                    val selected = uiState.activeTab == index
                    Tab(
                        selected = selected,
                        onClick = {
                            viewModel.selectTab(index)
                            viewModel.deselectPlaylist() // reset inner playlist viewing when switching tabs
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                color = if (selected) Color.White else Color.LightGray,
                                maxLines = 1,
                                softWrap = false // No truncation allowed
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Color(0xFFFF416C) else Color.White.copy(alpha = 0.05f))
                            .testTag("tab_$index")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Tab Contents Layout
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (!uiState.hasMediaPermission) {
                    PermissionEmptyState(
                        onRequestPermission = {
                            val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
                            } else {
                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(permissions)
                        }
                    )
                } else {
                    when (uiState.activeTab) {
                        0 -> TracksTab(
                            viewModel = viewModel,
                            searchQuery = uiState.searchQuery,
                            currentTrack = uiState.currentTrack,
                            isPlaying = uiState.isPlaying,
                            favoriteIds = favoriteIds,
                            tracks = allTracks,
                            sortOption = sortOption,
                            onSortOptionChange = { sortOption = it },
                            onAddTrackToPlaylist = {
                                trackSelectedForPlaylistAdding = it
                                showAddTrackToPlaylistDialog = true
                            },
                            playHistory = playHistory,
                            isMultiSelectActive = isMultiSelectActive,
                            onMultiSelectActiveChange = { isMultiSelectActive = it },
                            selectedTrackIds = selectedTrackIds,
                            onToggleSelect = { trackId ->
                                if (selectedTrackIds.contains(trackId)) {
                                    selectedTrackIds.remove(trackId)
                                    if (selectedTrackIds.isEmpty()) {
                                        isMultiSelectActive = false
                                    }
                                } else {
                                    selectedTrackIds.add(trackId)
                                }
                            },
                            onStartMultiSelect = { trackId ->
                                isMultiSelectActive = true
                                selectedTrackIds.clear()
                                selectedTrackIds.add(trackId)
                            }
                        )
                        1 -> PlaylistsTab(
                            viewModel = viewModel,
                            playlists = playlists,
                            selectedPlaylist = selectedPlaylist,
                            playlistTracks = activePlaylistTracks,
                            onCreatePlaylist = { showCreatePlaylistDialog = true },
                            allTracks = allTracks
                        )
                        2 -> AlbumsTab(
                            viewModel = viewModel,
                            allTracks = allTracks,
                            searchQuery = uiState.searchQuery,
                            currentTrack = uiState.currentTrack,
                            isPlayerPlaying = uiState.isPlaying,
                            favoriteIds = favoriteIds,
                            onAddToPlaylist = {
                                trackSelectedForPlaylistAdding = it
                                showAddTrackToPlaylistDialog = true
                            }
                        )
                        3 -> ArtistsTab(
                            viewModel = viewModel,
                            allTracks = allTracks,
                            searchQuery = uiState.searchQuery,
                            currentTrack = uiState.currentTrack,
                            isPlayerPlaying = uiState.isPlaying,
                            favoriteIds = favoriteIds,
                            onAddToPlaylist = {
                                trackSelectedForPlaylistAdding = it
                                showAddTrackToPlaylistDialog = true
                            }
                        )
                        4 -> FavoritesTab(
                            viewModel = viewModel,
                            favorites = favorites,
                            searchQuery = uiState.searchQuery,
                            currentTrack = uiState.currentTrack,
                            isPlayerPlaying = uiState.isPlaying,
                            onAddToPlaylist = {
                                trackSelectedForPlaylistAdding = it
                                showAddTrackToPlaylistDialog = true
                            }
                        )
                        5 -> HistoryTab(
                            viewModel = viewModel,
                            playHistory = playHistory,
                            searchQuery = uiState.searchQuery,
                            currentTrack = uiState.currentTrack,
                            isPlayerPlaying = uiState.isPlaying,
                            favoriteIds = favoriteIds,
                            onAddToPlaylist = {
                                trackSelectedForPlaylistAdding = it
                                showAddTrackToPlaylistDialog = true
                            }
                        )
                    }
                }
            }

            // Buffer spacing above the Bottom Player
            if (uiState.currentTrack != null) {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // 4. Elegant Bottom Mini-Player
        AnimatedVisibility(
            visible = uiState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 8.dp)
        ) {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            var dragAmountY by remember { mutableStateOf(0f) }
            val track = uiState.currentTrack ?: return@AnimatedVisibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(68.dp)
                    .shadow(12.dp, shape = RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E2030).copy(alpha = 0.92f), shape = RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { isPlayerExpanded = true }
                    .testTag("mini_player")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small square thumbnail with small corner radius on the left
                    TrackCover(
                        track = track,
                        fallbackIconSize = 20.dp,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Artist - centered in the middle
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            color = Color.LightGray.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.skipPrevious() },
                                modifier = Modifier.testTag("mini_skip_prev_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Skip Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Mini Controls - Play/Pause and Skip Next only
                            IconButton(
                                onClick = { viewModel.togglePlayback() },
                                modifier = Modifier.testTag("mini_play_pause_button")
                            ) {
                                if (uiState.isBuffering) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFFF416C), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color(0xFFFF416C),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.skipNext() },
                                modifier = Modifier.testTag("mini_skip_next_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Skip Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Super-Thin Progress Slider Line along the bottom boundary
                MiniPlayerProgressLine(
                    durationMs = uiState.durationMs,
                    progressMs = progressMsState.value,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // 5. Majestic Full Expanded Player Sheet Overlay
        AnimatedVisibility(
            visible = isPlayerExpanded,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeOut()
        ) {
            val track = uiState.currentTrack
            if (track != null) {
                ExpandedPlayerContent(
                    track = track,
                    uiState = uiState,
                    viewModel = viewModel,
                    favorites = favorites,
                    showQueuePanel = showQueuePanel,
                    progressMs = progressMsState.value,
                    onToggleQueue = { showQueuePanel = !showQueuePanel },
                    onCollapse = { isPlayerExpanded = false },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onAddTrackToPlaylist = {
                        trackSelectedForPlaylistAdding = track
                        showAddTrackToPlaylistDialog = true
                    }
                )
            }
        }

        // 6. Dialogue modules
        if (showBulkAddToPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showBulkAddToPlaylistDialog = false },
                title = { Text(stringResource(R.string.add_selected_to_playlist), color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    if (playlists.isEmpty()) {
                        Text(stringResource(R.string.no_playlists_found_create), color = Color.LightGray)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(playlists, key = { it.id }) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedTrackIds.forEach { trackId ->
                                                viewModel.addTrackToPlaylist(playlist.id, trackId)
                                            }
                                            showBulkAddToPlaylistDialog = false
                                            isMultiSelectActive = false
                                            selectedTrackIds.clear()
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(playlist.name, color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showBulkAddToPlaylistDialog = false }) {
                        Text(stringResource(R.string.cancel), color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1B1D2E),
                textContentColor = Color.White
            )
        }

        if (showCreatePlaylistDialog) {
            var playlistNameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = { Text(stringResource(R.string.create_playlist), color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(stringResource(R.string.playlist_name_placeholder), color = Color.LightGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = playlistNameInput,
                            onValueChange = { playlistNameInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF416C),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("playlist_input_text")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C)),
                        onClick = {
                            if (playlistNameInput.isNotBlank()) {
                                viewModel.createPlaylist(playlistNameInput)
                            }
                            showCreatePlaylistDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text(stringResource(R.string.cancel), color = Color.White)
                    }
                },
                containerColor = Color(0xFF1F2133)
            )
        }

        if (showAddTrackToPlaylistDialog && trackSelectedForPlaylistAdding != null) {
            val pendingTrack = trackSelectedForPlaylistAdding!!
            AlertDialog(
                onDismissRequest = { showAddTrackToPlaylistDialog = false },
                title = { Text(stringResource(R.string.add_to_playlist), color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Box(modifier = Modifier.heightIn(max = 250.dp)) {
                        if (playlists.isEmpty()) {
                            Text("لیست پخشی در دسترس نیست. لطفا ابتدا یک لیست پخش جدید بسازید.", color = Color.LightGray)
                        } else {
                            LazyColumn {
                                items(playlists, key = { it.id }) { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.addTrackToPlaylist(playlist.id, pendingTrack.id)
                                                showAddTrackToPlaylistDialog = false
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                         Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color(0xFFFF416C))
                                         Spacer(modifier = Modifier.width(12.dp))
                                         Text(playlist.name, color = Color.White, fontSize = 16.sp)
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddTrackToPlaylistDialog = false }) {
                        Text(stringResource(R.string.close), color = Color.White)
                    }
                },
                containerColor = Color(0xFF1F2133)
            )
        }

        // 7. Equalizer Bottom Sheet Selector
        if (showEqualizerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showEqualizerSheet = false },
                containerColor = Color(0xFF161824),
                contentColor = Color.White
            ) {
                EqualizerContent(
                    eqState = eqState,
                    isSupported = uiState.isEqualizerSupported,
                    onBandChange = { band, value -> viewModel.updateEqualizerFreq(band, value) },
                    onPresetSelect = { viewModel.applyEqualizerPreset(it) }
                )
            }
        }

        // 8. Sleep Timer Bottom Sheet Selector
        if (showSleepTimerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSleepTimerSheet = false },
                containerColor = Color(0xFF161824),
                contentColor = Color.White
            ) {
                SleepTimerContent(
                    uiState = uiState,
                    onSelectMinutes = { mins ->
                        viewModel.startSleepTimer(mins)
                        showSleepTimerSheet = false
                    },
                    onCancelTimer = {
                        viewModel.cancelSleepTimer()
                        showSleepTimerSheet = false
                    }
                )
            }
        }

        // 9. Premium Settings Overlay Screen
        if (showSettingsScreen) {
            SettingsScreen(
                uiState = uiState,
                onBack = { showSettingsScreen = false },
                onSetAudioQuality = { viewModel.setAudioQuality(it) },
                onSetCrossfade = { viewModel.setCrossfade(it) },
                onClearHistory = { viewModel.clearHistory() },
                onClearCache = { viewModel.clearCache() }
            )
        }
    }
    }
}

// ======================== TABS IMPLEMENTATION ========================

enum class TrackSortOption(@androidx.annotation.StringRes val displayResId: Int) {
    DATE_ADDED(R.string.sort_date_added),
    TITLE_AZ(R.string.sort_title_az),
    ARTIST_AZ(R.string.sort_artist_az),
    ALBUM_AZ(R.string.sort_album_az),
    DURATION(R.string.sort_duration)
}

@Composable
fun RecentTrackItemCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            parseColor(track.coverGradientStart),
                            parseColor(track.coverGradientEnd)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            TrackCover(
                track = track,
                fallbackIconSize = 24.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = track.artist,
            color = Color.Gray,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TracksTab(
    viewModel: MusicViewModel,
    searchQuery: String,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    tracks: List<Track>,
    sortOption: TrackSortOption,
    onSortOptionChange: (TrackSortOption) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    playHistory: List<Track> = emptyList(),
    isMultiSelectActive: Boolean,
    onMultiSelectActiveChange: (Boolean) -> Unit,
    selectedTrackIds: List<String>,
    onToggleSelect: (String) -> Unit,
    onStartMultiSelect: (String) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    val sortedAndFilteredTracks = remember(tracks, searchQuery, sortOption) {
        val filtered = tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
        }
        when (sortOption) {
            TrackSortOption.DATE_ADDED -> filtered.sortedByDescending { it.dateAddedSecs }
            TrackSortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            TrackSortOption.ARTIST_AZ -> filtered.sortedBy { it.artist.lowercase() }
            TrackSortOption.ALBUM_AZ -> filtered.sortedBy { it.album.lowercase() }
            TrackSortOption.DURATION -> filtered.sortedBy { it.durationMs }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (sortedAndFilteredTracks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "آهنگی پیدا نشد 🎵",
                        color = Color.LightGray,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "فایلهای صوتی را در پوشه Music دستگاه قرار دهید یا از دکمه افزودن آهنگ استفاده کنید.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp)
                ) {
                    // 1. Horizontally styled distinct shortcuts cards
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Favorites Card
                            Card(
                                onClick = { viewModel.selectTab(4) },
                                colors = CardDefaults.cardColors(containerColor = Color(0x1AFF416C)),
                                border = BorderStroke(1.dp, Color(0xFFFF416C).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Color(0xFFFF416C),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.tab_favorites),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Playlists Card
                            Card(
                                onClick = { viewModel.selectTab(1) },
                                colors = CardDefaults.cardColors(containerColor = Color(0x1A2196F3)),
                                border = BorderStroke(1.dp, Color(0x332196F3)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.tab_playlists),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Recent Card
                            Card(
                                onClick = { viewModel.selectTab(5) },
                                colors = CardDefaults.cardColors(containerColor = Color(0x1A4CAF50)),
                                border = BorderStroke(1.dp, Color(0x334CAF50)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.recent),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // 2. Head Row: All Songs / Count & Action Buttons
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) stringResource(R.string.search_results) else stringResource(R.string.tab_tracks),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.songs_count, sortedAndFilteredTracks.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        if (sortedAndFilteredTracks.isNotEmpty()) {
                                            viewModel.setQueue(sortedAndFilteredTracks)
                                            viewModel.playTrackAtIndex(0)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF416C), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.play_all), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                TextButton(
                                    onClick = {
                                        if (sortedAndFilteredTracks.isNotEmpty()) {
                                            val shuffled = sortedAndFilteredTracks.shuffled()
                                            viewModel.setQueue(shuffled)
                                            viewModel.playTrackAtIndex(0)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = Color(0xFFFF416C), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.shuffle), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Box {
                                    IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Sort, contentDescription = "Sort Options", tint = Color.LightGray)
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        modifier = Modifier.background(Color(0xFF1F2133))
                                    ) {
                                        TrackSortOption.values().forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(option.displayResId), color = Color.White) },
                                                leadingIcon = {
                                                    if (sortOption == option) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFF416C))
                                                    }
                                                },
                                                onClick = {
                                                    onSortOptionChange(option)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Track Rows Item with multi-select support
                    items(sortedAndFilteredTracks, key = { it.id }) { track ->
                        val isSelected = selectedTrackIds.contains(track.id)
                        val isFav = track.id in favoriteIds
                        TrackRowItem(
                            track = track,
                            isCurrent = currentTrack?.id == track.id,
                            isPlaying = isPlaying && currentTrack?.id == track.id,
                            onPlay = {
                                if (isMultiSelectActive) {
                                    onToggleSelect(track.id)
                                } else {
                                    if (currentTrack?.id == track.id && isPlaying) {
                                        viewModel.triggerPlayerExpansion()
                                    } else {
                                        viewModel.playTrack(track)
                                    }
                                }
                            },
                            onAddToPlaylist = { onAddTrackToPlaylist(track) },
                            viewModel = viewModel,
                            isMultiSelectMode = isMultiSelectActive,
                            isSelected = isSelected,
                            onToggleSelect = { onToggleSelect(track.id) },
                            onStartMultiSelect = { onStartMultiSelect(track.id) },
                            isFav = isFav,
                            isBroken = viewModel.uiState.value.brokenTrackIds.contains(track.id)
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMultiSelectActive) stringResource(R.string.hold_or_click_to_manage) else stringResource(R.string.hold_to_multiselect),
                                color = Color.Gray.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(
    viewModel: MusicViewModel,
    playlists: List<PlaylistEntity>,
    selectedPlaylist: PlaylistEntity?,
    playlistTracks: List<Track>,
    onCreatePlaylist: () -> Unit,
    allTracks: List<Track>
) {
    val allPlaylistTracks by viewModel.allPlaylistTracks.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    val tracksByPlaylist = remember(allPlaylistTracks) {
        allPlaylistTracks.groupBy { it.playlistId }
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Dialog & Reorder states
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renameInputName by remember { mutableStateOf("") }
    var isReorderMode by remember { mutableStateOf(false) }

    val mutablePlaylists = remember(playlists) { playlists.toMutableStateList() }

    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    if (selectedPlaylist == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.my_playlists_header), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { isReorderMode = !isReorderMode },
                        modifier = Modifier.size(36.dp).testTag("toggle_reorder_mode")
                    ) {
                        Icon(
                            imageVector = if (isReorderMode) Icons.Default.CheckCircle else Icons.Default.Sort,
                            contentDescription = "Reorder mode",
                            tint = if (isReorderMode) Color(0xFFFF416C) else Color.White
                        )
                    }
                }
                Button(
                    onClick = onCreatePlaylist,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("create_playlist_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.new_playlist_btn))
                }
            }

            if (isReorderMode && playlists.isNotEmpty()) {
                Text(
                    text = "از فلشها برای مرتبسازی لیستها استفاده کنید. بعد از اتمام ✓ را بزنید.",
                    color = Color.LightGray.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_playlists_found_tap_new), color = Color.Gray, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
                ) {
                    items(mutablePlaylists, key = { it.id }) { playlist ->
                        val playlistTracksList = tracksByPlaylist[playlist.id] ?: emptyList()
                        val trackCount = playlistTracksList.size
                        
                        // Find the last added track for thumbnail
                        val lastAddedTrackRef = playlistTracksList.maxByOrNull { it.addedAt }
                        val lastTrack = lastAddedTrackRef?.let { ref ->
                            allTracks.find { it.id == ref.trackId }
                        }

                        val trackCountText = if (trackCount == 1) "۱ آهنگ" else "$trackCount آهنگ"
                        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale("fa"))
                        val dateStr = sdf.format(java.util.Date(playlist.createdAt))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .clickable {
                                    if (!isReorderMode) {
                                        viewModel.selectPlaylist(playlist)
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (lastTrack != null) {
                                    TrackCover(
                                        track = lastTrack,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(Color(0xFF38384D), Color(0xFF1E1E2C))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.Gray)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("$trackCountText • $dateStr", color = Color.Gray, fontSize = 12.sp)
                                }
                            }

                            // Interactive playlist controls (direct play, rename, delete, reorder)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isReorderMode) {
                                    val currentIndex = mutablePlaylists.indexOf(playlist)
                                    
                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            if (currentIndex > 0) {
                                                val temp = mutablePlaylists[currentIndex]
                                                mutablePlaylists[currentIndex] = mutablePlaylists[currentIndex - 1]
                                                mutablePlaylists[currentIndex - 1] = temp
                                                
                                                mutablePlaylists.forEachIndexed { idx, p ->
                                                    viewModel.updatePlaylistOrder(p.id, idx)
                                                }
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            }
                                        },
                                        enabled = currentIndex > 0,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = if (currentIndex > 0) Color.White else Color.DarkGray)
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            if (currentIndex < mutablePlaylists.lastIndex) {
                                                val temp = mutablePlaylists[currentIndex]
                                                mutablePlaylists[currentIndex] = mutablePlaylists[currentIndex + 1]
                                                mutablePlaylists[currentIndex + 1] = temp
                                                
                                                mutablePlaylists.forEachIndexed { idx, p ->
                                                    viewModel.updatePlaylistOrder(p.id, idx)
                                                }
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            }
                                        },
                                        enabled = currentIndex < mutablePlaylists.lastIndex,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = if (currentIndex < mutablePlaylists.lastIndex) Color.White else Color.DarkGray)
                                    }

                                    // Touch Drag handle
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .size(36.dp)
                                            .pointerInput(playlist.id) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingId = playlist.id
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggingId = null
                                                        dragOffsetY = 0f
                                                        mutablePlaylists.forEachIndexed { idx, p ->
                                                            viewModel.updatePlaylistOrder(p.id, idx)
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draggingId = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y
                                                        val itemHeight = with(density) { 80.dp.toPx() }
                                                        val idx = mutablePlaylists.indexOfFirst { it.id == playlist.id }
                                                        if (idx != -1) {
                                                            if (dragOffsetY > itemHeight / 2 && idx < mutablePlaylists.lastIndex) {
                                                                val temp = mutablePlaylists[idx]
                                                                mutablePlaylists[idx] = mutablePlaylists[idx + 1]
                                                                mutablePlaylists[idx + 1] = temp
                                                                dragOffsetY -= itemHeight
                                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                            } else if (dragOffsetY < -itemHeight / 2 && idx > 0) {
                                                                val temp = mutablePlaylists[idx]
                                                                mutablePlaylists[idx] = mutablePlaylists[idx - 1]
                                                                mutablePlaylists[idx - 1] = temp
                                                                dragOffsetY += itemHeight
                                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder", tint = if (draggingId == playlist.id) Color(0xFFFF416C) else Color.Gray)
                                    }
                                } else {
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(36.dp).testTag("playlist_menu_${playlist.id}")
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.LightGray)
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false },
                                            modifier = Modifier.background(Color(0xFF1B1D2E))
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.play_label), color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF416C)) },
                                                onClick = {
                                                    showMenu = false
                                                    val trackIds = allPlaylistTracks.filter { it.playlistId == playlist.id }.map { it.trackId }
                                                    val tracksToPlay = trackIds.mapNotNull { id -> allTracks.find { it.id == id } }
                                                    if (tracksToPlay.isNotEmpty()) {
                                                        viewModel.setQueue(tracksToPlay)
                                                        viewModel.playTrack(tracksToPlay.first())
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.rename), color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.LightGray) },
                                                onClick = {
                                                    showMenu = false
                                                    playlistToRename = playlist
                                                    renameInputName = playlist.name
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete), color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF1744)) },
                                                onClick = {
                                                    showMenu = false
                                                    playlistToDelete = playlist
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    } else {
        // Detailed playlist track browsing
        val playlistCoverTrack = playlistTracks.firstOrNull()
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.deselectPlaylist() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Beautiful Album/Playlist Artwork Cover (Problem 22)
                if (playlistCoverTrack != null) {
                    TrackCover(
                        track = playlistCoverTrack,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF38384D), Color(0xFF1E1E2C))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.LightGray)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedPlaylist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                playlistToRename = selectedPlaylist
                                renameInputName = selectedPlaylist.name
                            },
                            modifier = Modifier.size(36.dp).testTag("rename_detailed_playlist")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename Header", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(stringResource(R.string.selected_playlist_content), color = Color.LightGray, fontSize = 12.sp)
                }
            }

            if (playlistTracks.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.playlist_empty_msg), color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.use_add_option_to_add),
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
                ) {
                    items(playlistTracks, key = { it.id }) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (currentTrack?.id == track.id && uiState.isPlaying) {
                                            viewModel.triggerPlayerExpansion()
                                        } else {
                                            viewModel.setQueue(playlistTracks)
                                            viewModel.playTrack(track)
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackCover(
                                    track = track,
                                    fallbackIconSize = 20.dp,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(track.artist, color = Color.Gray, fontSize = 11.sp)
                                }
                            }

                            IconButton(onClick = { viewModel.removeTrackFromPlaylist(selectedPlaylist.id, track.id) }) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. Delete Confirmation Dialog Popup
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.delete_playlist), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "آیا مطمئن هستید که می‌خواهید لیست پخش «${playlistToDelete?.name}» را حذف کنید؟ این عمل غیرقابل بازگشت است.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C)),
                    onClick = {
                        playlistToDelete?.let {
                            viewModel.deletePlaylist(it.id)
                        }
                        playlistToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.cancel), color = Color.White)
                }
            },
            containerColor = Color(0xFF1F2133)
        )
    }

    // 2. Rename Playlist Dialog Popup
    if (playlistToRename != null) {
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text(stringResource(R.string.rename_playlist), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.playlist_name_placeholder), color = Color.LightGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInputName,
                        onValueChange = { renameInputName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                           focusedBorderColor = Color(0xFFFF416C),
                           unfocusedBorderColor = Color.Gray,
                           focusedTextColor = Color.White,
                           unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("playlist_rename_text_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C)),
                    onClick = {
                        playlistToRename?.let {
                            if (renameInputName.isNotBlank()) {
                                viewModel.renamePlaylist(it.id, renameInputName)
                            }
                        }
                        playlistToRename = null
                    }
                ) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text(stringResource(R.string.cancel), color = Color.White)
                }
            },
            containerColor = Color(0xFF1F2133)
        )
    }
}

@Composable
fun AlbumsTab(
    viewModel: MusicViewModel,
    allTracks: List<Track>,
    searchQuery: String,
    currentTrack: Track?,
    isPlayerPlaying: Boolean,
    favoriteIds: Set<String>,
    onAddToPlaylist: (Track) -> Unit
) {
    var selectedAlbumName by remember { mutableStateOf<String?>(null) }

    val albums = remember(allTracks, searchQuery) {
        allTracks.groupBy { it.album }.filter { entry ->
            entry.key.contains(searchQuery, ignoreCase = true) ||
                    entry.value.any { it.artist.contains(searchQuery, ignoreCase = true) }
        }
    }

    if (selectedAlbumName == null) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums.keys.toList(), key = { it }) { albumName ->
                val albumTracks = albums[albumName] ?: emptyList()
                val trackInAlbum = albumTracks.firstOrNull() ?: return@items
                val isCurrent = currentTrack?.album == albumName
                val isPlaying = isCurrent && isPlayerPlaying

                val trackCount = albumTracks.size
                val totalDurationMs = albumTracks.sumOf { it.durationMs }
                val totalDurationStr = formatLargeDuration(totalDurationMs)

                val albumGradient = Brush.horizontalGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) Color(0xFFFF416C).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (isCurrent) BorderStroke(1.5.dp, albumGradient) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedAlbumName = albumName }
                        .testTag("album_card_$albumName")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            parseColor(trackInAlbum.coverGradientStart),
                                            parseColor(trackInAlbum.coverGradientEnd)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPlaying) {
                                // Audio visualizer wave indicator
                                PlayingAnimation(
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(Icons.Default.Album, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(50.dp))
                            }

                            // Direct Play button as a bottom-right overlays (Improvement fix)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                FloatingActionButton(
                                    onClick = {
                                        if (currentTrack?.id == trackInAlbum.id && isPlayerPlaying) {
                                            viewModel.triggerPlayerExpansion()
                                        } else {
                                            viewModel.setQueue(albumTracks)
                                            viewModel.playTrack(trackInAlbum)
                                        }
                                    },
                                    containerColor = Color(0xFFFF416C),
                                    contentColor = Color.White,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("play_all_album_$albumName"),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Album",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = albumName,
                            color = if (isCurrent) Color(0xFFFF416C) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = trackInAlbum.artist,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "$trackCount آهنگ • $totalDurationStr",
                            color = Color.LightGray.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    } else {
        // Detailed album view
        val albumName = selectedAlbumName!!
        val albumTracks = allTracks.filter { it.album == albumName }
        val representativeTrack = albumTracks.firstOrNull()

        if (representativeTrack == null) {
            selectedAlbumName = null
            return
        }

        val totalDurationMs = albumTracks.sumOf { it.durationMs }
        val totalDurationStr = formatLargeDuration(totalDurationMs)

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedAlbumName = null },
                    modifier = Modifier.testTag("album_detail_back")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.album_details),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            // Beautiful Gradient Album Info card 
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                parseColor(representativeTrack.coverGradientStart).copy(alpha = 0.85f),
                                parseColor(representativeTrack.coverGradientEnd).copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.2f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = albumName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = representativeTrack.artist,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ژانر: ${representativeTrack.genre}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${albumTracks.size} آهنگ • $totalDurationStr",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            viewModel.setQueue(albumTracks)
                            viewModel.playTrack(representativeTrack)
                        },
                        containerColor = Color.White,
                        contentColor = Color(0xFFFF416C),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("album_detail_play_all"),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play All", modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp)
            ) {
                items(albumTracks, key = { it.id }) { track ->
                    val isCurrent = currentTrack?.id == track.id
                    val isPlaying = isCurrent && isPlayerPlaying

                    TrackRowItem(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                        onPlay = {
                            if (currentTrack?.id == track.id && isPlayerPlaying) {
                                viewModel.triggerPlayerExpansion()
                            } else {
                                viewModel.setQueue(albumTracks)
                                viewModel.playTrack(track)
                            }
                        },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        viewModel = viewModel,
                        isFav = track.id in favoriteIds,
                        isBroken = viewModel.uiState.value.brokenTrackIds.contains(track.id)
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistsTab(
    viewModel: MusicViewModel,
    allTracks: List<Track>,
    searchQuery: String,
    currentTrack: Track?,
    isPlayerPlaying: Boolean,
    favoriteIds: Set<String>,
    onAddToPlaylist: (Track) -> Unit
) {
    var selectedArtistName by remember { mutableStateOf<String?>(null) }

    val artists = remember(allTracks, searchQuery) {
        allTracks.groupBy { it.artist }.filter { entry ->
            entry.key.contains(searchQuery, ignoreCase = true)
        }
    }

    if (selectedArtistName == null) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists.keys.toList(), key = { it }) { artistName ->
                val artistTracks = artists[artistName] ?: emptyList()
                val trackInArtist = artistTracks.firstOrNull() ?: return@items
                val isCurrent = currentTrack?.artist == artistName
                val isPlaying = isCurrent && isPlayerPlaying

                val uniqueAlbumsCount = artistTracks.map { it.album }.distinct().size
                val primaryGenre = artistTracks.firstOrNull()?.genre ?: stringResource(R.string.unknown_genre)

                val initials = artistName.split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .map { it.first().uppercase() }
                    .joinToString("")

                val artistGradient = Brush.horizontalGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedArtistName = artistName }
                        .padding(8.dp)
                        .testTag("artist_card_$artistName"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        parseColor(trackInArtist.coverGradientEnd),
                                        parseColor(trackInArtist.coverGradientStart)
                                    )
                                )
                            )
                            .then(
                                if (isCurrent) Modifier.border(1.5.dp, artistGradient, CircleShape) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            PlayingAnimation(
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = initials,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = artistName,
                        color = if (isCurrent) Color(0xFFFF416C) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = primaryGenre,
                        color = Color.LightGray.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isCurrent) stringResource(R.string.now_playing) else stringResource(R.string.now_playing_stats, uniqueAlbumsCount, artistTracks.size),
                        color = if (isCurrent) Color(0xFFFF416C).copy(alpha = 0.8f) else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    } else {
        // Detailed Artist page
        val artistName = selectedArtistName!!
        val artistTracks = allTracks.filter { it.artist == artistName }
        val albumsByArtist = artistTracks.groupBy { it.album }
        val representativeTrack = artistTracks.firstOrNull()

        if (representativeTrack == null) {
            selectedArtistName = null
            return
        }

        val primaryGenre = representativeTrack.genre
        val countAlbums = albumsByArtist.size
        val countSongs = artistTracks.size

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedArtistName = null },
                    modifier = Modifier.testTag("artist_detail_back")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.artist_details),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                parseColor(representativeTrack.coverGradientStart).copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.4f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val initials = artistName.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .map { it.first().uppercase() }
                        .joinToString("")

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artistName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ژانر اصلی: $primaryGenre",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$countAlbums آلبوم • $countSongs آهنگ",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            viewModel.setQueue(artistTracks)
                            viewModel.playTrack(representativeTrack)
                        },
                        containerColor = Color.White,
                        contentColor = Color(0xFFFF416C),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("artist_detail_play_all"),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Artist", modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp)
            ) {
                item {
                    Text(
                        text = "آلبوم‌های $artistName",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        items(albumsByArtist.keys.toList(), key = { it }) { albumName ->
                            val albumTracks = albumsByArtist[albumName] ?: emptyList()
                            val representative = albumTracks.firstOrNull() ?: return@items

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.04f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .width(130.dp)
                                    .clickable {
                                        viewModel.setQueue(albumTracks)
                                        viewModel.playTrack(representative)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        parseColor(representative.coverGradientStart),
                                                        parseColor(representative.coverGradientEnd)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Album, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = albumName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${albumTracks.size} songs",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.tab_tracks),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(artistTracks, key = { it.id }) { track ->
                    val isCurrent = currentTrack?.id == track.id
                    val isPlaying = isCurrent && isPlayerPlaying

                    TrackRowItem(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                        onPlay = {
                            if (currentTrack?.id == track.id && isPlayerPlaying) {
                                viewModel.triggerPlayerExpansion()
                            } else {
                                viewModel.setQueue(artistTracks)
                                viewModel.playTrack(track)
                            }
                        },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        viewModel = viewModel,
                        isFav = track.id in favoriteIds,
                        isBroken = viewModel.uiState.value.brokenTrackIds.contains(track.id)
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesTab(
    viewModel: MusicViewModel,
    favorites: List<Track>,
    searchQuery: String,
    currentTrack: Track?,
    isPlayerPlaying: Boolean,
    onAddToPlaylist: (Track) -> Unit
) {
    val filteredFavorites = remember(favorites, searchQuery) {
        favorites.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    if (filteredFavorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                val msg = if (searchQuery.isNotEmpty()) stringResource(R.string.empty_favorites_search) else stringResource(R.string.empty_favorites)
                Text(msg, color = Color.Gray, fontSize = 14.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF416C),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.favorite_tracks),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val totalDurationMs = filteredFavorites.sumOf { it.durationMs }
                        Text(
                            text = "${stringResource(R.string.songs_count, filteredFavorites.size)} • ${formatLargeDuration(totalDurationMs)}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.setQueue(filteredFavorites)
                            viewModel.playTrack(filteredFavorites.first())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("play_all_favorites")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.play_all), fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }

            items(filteredFavorites, key = { it.id }) { track ->
                val isCurrent = currentTrack?.id == track.id
                val isPlaying = isCurrent && isPlayerPlaying
                TrackRowItem(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    onPlay = {
                        if (currentTrack?.id == track.id && isPlayerPlaying) {
                            viewModel.triggerPlayerExpansion()
                        } else {
                            viewModel.setQueue(filteredFavorites)
                            viewModel.playTrack(track)
                        }
                    },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    viewModel = viewModel,
                    isFavoriteTab = true,
                    isFav = true,
                    isBroken = viewModel.uiState.value.brokenTrackIds.contains(track.id)
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.hold_track_to_add_to_playlist_toast),
                        color = Color.Gray.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    viewModel: MusicViewModel,
    playHistory: List<Track>,
    searchQuery: String,
    currentTrack: Track?,
    isPlayerPlaying: Boolean,
    favoriteIds: Set<String>,
    onAddToPlaylist: (Track) -> Unit
) {
    val context = LocalContext.current
    val filteredHistory = remember(playHistory, searchQuery) {
        playHistory.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    if (filteredHistory.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                val msg = if (searchQuery.isNotEmpty()) stringResource(R.string.empty_history_search) else stringResource(R.string.empty_history)
                Text(msg, color = Color.Gray, fontSize = 14.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredHistory.size} آهنگ پخش شده",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = {
                            viewModel.clearHistory()
                            android.widget.Toast.makeText(context, context.getString(R.string.history_cleared), android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = Color(0xFFFF416C),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.clear_history),
                                color = Color(0xFFFF416C),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            itemsIndexed(filteredHistory, key = { index, track -> "${index}_${track.id}" }) { _, track ->
                val isCurrent = currentTrack?.id == track.id
                val isPlaying = isCurrent && isPlayerPlaying
                TrackRowItem(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    onPlay = {
                        if (currentTrack?.id == track.id && isPlayerPlaying) {
                            viewModel.triggerPlayerExpansion()
                        } else {
                            viewModel.playTrack(track)
                        }
                    },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    viewModel = viewModel,
                    isFav = track.id in favoriteIds,
                    isBroken = viewModel.uiState.value.brokenTrackIds.contains(track.id)
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.hold_track_to_add_to_playlist_toast),
                        color = Color.Gray.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }
    }
}

@Composable
fun PlayingAnimation(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "music_wave")
    
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bar1"
    )
    
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bar2"
    )
    
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = scale1
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = scale2
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = scale3
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}

// Custom reusable Track Row
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRowItem(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    viewModel: MusicViewModel,
    isFavoriteTab: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onStartMultiSelect: () -> Unit = {},
    isFav: Boolean = false,
    isBroken: Boolean = false
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var showMoreMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) Color(0x2BFF416C)
                    else if (isCurrent) Color.White.copy(alpha = 0.08f)
                    else Color.Transparent
                )
                .combinedClickable(
                    onClick = {
                        if (isMultiSelectMode) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onToggleSelect()
                        } else {
                            if (isBroken) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                android.widget.Toast.makeText(context, "فایل این آهنگ یافت نشد یا غیرقابل دسترس است.", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                onPlay()
                            }
                        }
                    },
                    onLongClick = {
                        if (!isMultiSelectMode) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onStartMultiSelect()
                        }
                    }
                )
                .padding(start = 8.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox inside Multi-Select mode
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFFFF416C),
                        uncheckedColor = Color.LightGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("row_checkbox_${track.id}")
                )
            }

            // Square Cover Art Image (small corner radius)
            TrackCover(
                track = track,
                fallbackIconSize = 20.dp,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)) // Small corner radius for sleek squared look
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Metadata Info (Bold title, clean pairing)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isBroken) Color.Gray.copy(alpha = 0.6f) else if (isCurrent) Color(0xFFFF416C) else Color.White,
                    fontWeight = FontWeight.Bold, // Bold title
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBroken) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Broken file",
                            tint = Color(0xFFFF416C),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "${track.artist}  •  ${track.album}",
                        color = if (isBroken) Color.Gray.copy(alpha = 0.4f) else Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Animated Equalizer on the right side if currently playing
            if (isPlaying) {
                PlayingAnimation(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(16.dp, 16.dp)
                )
            } else if (isCurrent) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color(0xFFFF416C),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(18.dp)
                )
            }

            // Track Duration
            Text(
                text = formatTime(track.durationMs),
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Single Elegant Three-Dots Button (only shows in non-multi-select mode)
            if (!isMultiSelectMode) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { showMoreMenu = true }
                            .testTag("row_more_options_${track.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.LightGray.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(Color(0xFF1F2133))
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_to_playlist), color = Color.White) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Color.LightGray) },
                            onClick = {
                                showMoreMenu = false
                                onAddToPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isFav) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite), color = Color.White) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isFav) Color(0xFFFF416C) else Color.LightGray
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                viewModel.toggleFavorite(track.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_track), color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.LightGray) },
                            onClick = {
                                showMoreMenu = false
                                // Trigger quick share
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    type = "audio/*"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Sharing '${track.title}'")
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Listen to '${track.title}' by ${track.artist}!")
                                    try {
                                        val uri = android.net.Uri.parse(track.contentUri)
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    } catch (_: Exception) {}
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF1744)) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.deleteTracks(listOf(track.id))
                            }
                        )
                    }
                }
            }
        }

        // Subtly rendered horizontal divider line between rows
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// ======================== MAJESTIC FULLSCREEN PLAYER ========================

@Composable
fun ExpandedPlayerContent(
    track: Track,
    uiState: PlayerUiState,
    viewModel: MusicViewModel,
    favorites: List<Track>,
    showQueuePanel: Boolean,
    progressMs: Long,
    onToggleQueue: () -> Unit,
    onCollapse: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onAddTrackToPlaylist: () -> Unit
) {
    val isFav = favorites.any { it.id == track.id }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dialog state variables
    var showEditSongDialog by remember { mutableStateOf(false) }
    var showEarphonesDialog by remember { mutableStateOf(false) }
    var showWidgetDialog by remember { mutableStateOf(false) }

    // Smooth vibrant background matching the song's gradients with high-contrast bottom elements
    val trackColor = parseColor(track.coverGradientStart)
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            trackColor.copy(alpha = 0.85f),
            Color(0xFF1D2034),
            Color(0xFF121421)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Header Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleQueue,
                        modifier = Modifier.testTag("toggle_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Queue",
                            tint = if (showQueuePanel) Color(0xFFFF416C) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // THREE-DOT VERTICAL OPTION MENU
                    var showMoreMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.testTag("player_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(Color(0xFF1F2133))
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sleep_timer_title), color = Color.White, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenSleepTimer()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_track_info_title), color = Color.White, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    showEditSongDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_label), color = Color.White, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    val shareIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        type = "audio/*"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "My favorite track on Music")
                                        putExtra(android.content.Intent.EXTRA_TEXT, "I am listening to the amazing song '${track.title}' by ${track.artist} on Music! Check it out!")
                                        try {
                                            val uri = android.net.Uri.parse(track.contentUri)
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        } catch (_: Exception) {}
                                    }
                                    try {
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_track)))
                                    } catch (_: Exception) {}
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.earphones_title), color = Color.White, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Headset, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    showEarphonesDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_from_queue), color = Color.White, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.removeCurrentTrackFromQueue()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_screen_widget), color = Color.White, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    showWidgetDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // Upper Content Area (fills available space)
            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (showQueuePanel) {
                    // Render the interactive playlist queue directly
                    QueuePanel(viewModel = viewModel, uiState = uiState)
                } else {
                    // Rendering standard player page
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
                    ) {
                        // Massive Rotating Album Cover
                        val rotation = remember { Animatable(0f) }
                        LaunchedEffect(uiState.isPlaying) {
                            if (uiState.isPlaying) {
                                while (true) {
                                    rotation.animateTo(
                                        targetValue = rotation.value + 360f,
                                        animationSpec = tween(18000, easing = LinearEasing)
                                    )
                                }
                            } else {
                                rotation.stop()
                            }
                        }

                        TrackCover(
                            track = track,
                            fallbackIconSize = 46.dp,
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    rotationZ = rotation.value
                                    shadowElevation = 16f
                                    shape = CircleShape
                                    clip = true
                                }
                        )

                        // Metadata Titles Center-Symmetric Layout
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left decoration (PlaylistAdd to make it perfectly symmetrical and functional)
                            IconButton(
                                onClick = onAddTrackToPlaylist,
                                modifier = Modifier.size(48.dp).testTag("expanded_playlist_add_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = "Add to playlist",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Centered Column for Song Title / Artist
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${track.artist}  •  ${track.album}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            }

                            // Right Favorite Button
                            IconButton(
                                onClick = { viewModel.toggleFavorite(track.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("expanded_favorite_btn")
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFav) Color(0xFFFF416C) else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // 10. REAL SYNCHRONIZED SCROLLING LYRICS BAR (PERSISTENT & MAJESTIC!)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Find current active lyric line
                            val currentLyric = track.lyrics.lastOrNull { it.timeMs <= progressMs }
                                ?: track.lyrics.firstOrNull()

                            AnimatedContent(
                                targetState = currentLyric,
                                transitionSpec = {
                                    slideInVertically { height -> height } + fadeIn() togetherWith
                                            slideOutVertically { height -> -height } + fadeOut()
                                }
                            ) { lyric ->
                                if (lyric != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = lyric.text,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        if (lyric.translation.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = lyric.translation,
                                                color = Color(0xFFFF416C),
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    Text(stringResource(R.string.no_synchronized_lyrics), color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Seekbar / Slider Controls (ALWAYS VISIBLE!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                CustomSeekBar(
                    value = progressMs.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..uiState.durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.testTag("player_progress_slider"),
                    isBuffering = uiState.isBuffering
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(progressMs), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    Text(text = formatTime(uiState.durationMs), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                }
            }

            // Main Interactive Action Row (ALWAYS VISIBLE!) - Perfectly Balanced Space Centration
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button container
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleShuffle() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("shuffle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (uiState.shuffleMode) Color(0xFFFF416C) else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Skip Previous container
                    Box(
                        modifier = Modifier.weight(1.1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.skipPrevious() },
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("skip_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Skip Previous",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // Play / Pause Circle container
                    Box(
                        modifier = Modifier.weight(1.3f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
                                    )
                                )
                                .clickable { viewModel.togglePlayback() }
                                .testTag("play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isBuffering) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                            } else {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }

                    // Skip Next container
                    Box(
                        modifier = Modifier.weight(1.1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Skip Next",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // Repeat Cycle Button container
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.cycleRepeatMode() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("repeat_button")
                        ) {
                            val tintColor = if (uiState.repeatMode != RepeatMode.OFF) Color(0xFFFF416C) else Color.White
                            val iconVec = when (uiState.repeatMode) {
                                RepeatMode.OFF, RepeatMode.ALL -> Icons.Default.Repeat
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                            }
                            Icon(
                                imageVector = iconVec,
                                contentDescription = "Repeat Mode",
                                tint = tintColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        // ======================== MODAL DIALOGS FOR 3-DOT OPTIONS ========================
        
        // 1. EDIT SONG INFO DIALOG
        if (showEditSongDialog) {
            var editTitle by remember { mutableStateOf(track.title) }
            var editArtist by remember { mutableStateOf(track.artist) }
            var editAlbum by remember { mutableStateOf(track.album) }
            
            AlertDialog(
                onDismissRequest = { showEditSongDialog = false },
                title = { Text(stringResource(R.string.edit_track_info_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.label_track_title), color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF416C),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(stringResource(R.string.label_artist_name), color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editArtist,
                            onValueChange = { editArtist = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF416C),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(stringResource(R.string.label_album_name), color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editAlbum,
                            onValueChange = { editAlbum = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF416C),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateTrackInfo(track.id, editTitle, editArtist, editAlbum)
                            showEditSongDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C))
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditSongDialog = false }) {
                        Text(stringResource(R.string.cancel), color = Color.White)
                    }
                },
                containerColor = Color(0xFF161824)
            )
        }

        // 2. EARPHONES DOLBY AUDIO SUITE
        if (showEarphonesDialog) {
            val savedState by viewModel.earphonesState.collectAsState()
            var dolbyAtmosEnabled by remember(showEarphonesDialog) { mutableStateOf(savedState.dolbyEnabled) }
            var uhqUpscalerEnabled by remember(showEarphonesDialog) { mutableStateOf(savedState.uhqEnabled) }
            var selectedProfile by remember(showEarphonesDialog) { mutableStateOf(savedState.dolbyProfile) }
            var soundTubeBoost by remember(showEarphonesDialog) { mutableStateOf(savedState.tubeBoost) }
            var testProgress by remember { mutableStateOf(0f) }
            var isTestingTone by remember { mutableStateOf(false) }
            
            AlertDialog(
                onDismissRequest = {
                    com.mahdi.musicpro.util.AudioToneGenerator.stop()
                    showEarphonesDialog = false
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headset, contentDescription = null, tint = Color(0xFFFF416C), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.earphones_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.earphones_dialog_desc), color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Dolby Atmos Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(stringResource(R.string.dolby_atmos_mode), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("شبیه‌سازی صدای سه‌بعدی و فراگیر سینمایی", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = dolbyAtmosEnabled,
                                onCheckedChange = { dolbyAtmosEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF416C), checkedTrackColor = Color(0xFFFF416C).copy(alpha = 0.4f))
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Profile selectors if Dolby is active
                        if (dolbyAtmosEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Auto", "Movie", "Music", "Voice").forEach { profile ->
                                    val active = selectedProfile == profile
                                    val displayName = when(profile) {
                                        "Auto" -> "خودکار"
                                        "Movie" -> "فیلم"
                                        "Music" -> "موسیقی"
                                        "Voice" -> "وکال"
                                        else -> profile
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) Color(0xFFFF416C) else Color.White.copy(alpha = 0.05f))
                                            .clickable { selectedProfile = profile }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(displayName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        // UHQ Upscaler
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(stringResource(R.string.uhq_upscaler), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("افزایش فوق‌العاده کیفیت و وضوح فرکانس‌های بالا", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = uhqUpscalerEnabled,
                                onCheckedChange = { uhqUpscalerEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF416C), checkedTrackColor = Color(0xFFFF416C).copy(alpha = 0.4f))
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Tube Sound Slider
                        Text(stringResource(R.string.tube_amp) + ": ${(soundTubeBoost * 100f).toInt()}%", color = Color.LightGray, fontSize = 13.sp)
                        Slider(
                            value = soundTubeBoost,
                            onValueChange = { soundTubeBoost = it },
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF416C), activeTrackColor = Color(0xFFFF416C))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Interactive fit frequency sweep simulator
                        Button(
                            onClick = {
                                if (isTestingTone) {
                                    com.mahdi.musicpro.util.AudioToneGenerator.stop()
                                    isTestingTone = false
                                } else {
                                    isTestingTone = true
                                    testProgress = 0f
                                    scope.launch {
                                        com.mahdi.musicpro.util.AudioToneGenerator.playTestTone(
                                            leftFreq = 440.0,
                                            rightFreq = 880.0,
                                            durationMs = 5000
                                        ) { progress ->
                                            testProgress = progress
                                        }
                                        isTestingTone = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isTestingTone) Color.DarkGray else Color(0xFFFF416C).copy(alpha = 0.2f))
                        ) {
                            if (isTestingTone) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("در حال پخش صدای تست کانال چپ و راست...", color = Color.White, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(progress = testProgress, color = Color(0xFFFF416C), modifier = Modifier.fillMaxWidth(0.8f))
                                }
                            } else {
                                Text(stringResource(R.string.earphones_test_tone), color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            com.mahdi.musicpro.util.AudioToneGenerator.stop()
                            viewModel.applyEarphonesEffects(
                                dolbyAtmos = dolbyAtmosEnabled,
                                dolbyProfile = selectedProfile,
                                uhqUpscaler = uhqUpscalerEnabled,
                                tubeBoost = soundTubeBoost
                            )
                            showEarphonesDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C))
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            com.mahdi.musicpro.util.AudioToneGenerator.stop()
                            viewModel.resetEarphonesSettings()
                            dolbyAtmosEnabled = true
                            selectedProfile = "Music"
                            uhqUpscalerEnabled = false
                            soundTubeBoost = 0.6f
                            showEarphonesDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.reset), color = Color(0xFFFF416C))
                    }
                },
                containerColor = Color(0xFF161824)
            )
        }

        // 3. HOME SCREEN WIDGET SIMULATOR & SETUP
        if (showWidgetDialog) {
            val prefs = context.getSharedPreferences("music_pro_prefs", android.content.Context.MODE_PRIVATE)
            var transparency by remember { mutableStateOf(prefs.getFloat("widget_transparency", 0.4f)) }
            val savedStyle = prefs.getString("widget_style", "wide") ?: "wide"
            var isCompactWidget by remember { mutableStateOf(savedStyle == "compact" || prefs.getBoolean("widget_is_compact", false)) }
            var useRoundShape by remember { mutableStateOf(prefs.getBoolean("widget_use_round", true)) }
            
            AlertDialog(
                onDismissRequest = { showWidgetDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Widgets, contentDescription = null, tint = Color(0xFFFF416C), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.widget_preview_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.widget_preview_desc), color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Real synchronised widget viewer container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(if (useRoundShape) 16.dp else 4.dp))
                                .background(Color(0xFF0F1016).copy(alpha = transparency))
                                .padding(12.dp)
                        ) {
                            if (isCompactWidget) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                                    )
                                    Icon(
                                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                            Text(track.artist, color = Color.LightGray, fontSize = 11.sp, maxLines = 1)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(stringResource(R.string.home_screen_widget), color = Color(0xFFFF416C), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Brush.linearGradient(colors = listOf(parseColor(track.coverGradientStart), parseColor(track.coverGradientEnd))))
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    val progressMultiplier = if (uiState.durationMs > 0) progressMs.toFloat() / uiState.durationMs else 0.4f
                                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
                                        Box(modifier = Modifier.fillMaxWidth(progressMultiplier).fillMaxHeight().background(Color(0xFFFF416C)))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Icon(if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.widget_type_label), color = Color.LightGray, fontSize = 13.sp)
                            Row {
                                FilterChip(
                                    selected = !isCompactWidget,
                                    onClick = { isCompactWidget = false },
                                    label = { Text(stringResource(R.string.widget_type_wide)) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFF416C), labelColor = Color.White, selectedLabelColor = Color.White)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = isCompactWidget,
                                    onClick = { isCompactWidget = true },
                                    label = { Text(stringResource(R.string.widget_type_compact)) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFF416C), labelColor = Color.White, selectedLabelColor = Color.White)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.widget_rounded_corners), color = Color.LightGray, fontSize = 13.sp)
                            Switch(
                                checked = useRoundShape,
                                onCheckedChange = { useRoundShape = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF416C), checkedTrackColor = Color(0xFFFF416C).copy(alpha = 0.4f))
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("شفافیت پس‌زمینه: ${(transparency * 100).toInt()}%", color = Color.LightGray, fontSize = 13.sp)
                        Slider(
                            value = transparency,
                            onValueChange = { transparency = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF416C), activeTrackColor = Color(0xFFFF416C))
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showWidgetDialog = false
                            
                            val prefs = context.getSharedPreferences("music_pro_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit()
                                .putFloat("widget_transparency", transparency)
                                .putBoolean("widget_use_round", useRoundShape)
                                .putBoolean("widget_is_compact", isCompactWidget)
                                .putString("widget_style", if (isCompactWidget) "compact" else "wide")
                                .apply()

                            com.mahdi.musicpro.widget.MusicProWidgetProvider.updateWidgetDirectly(
                                context,
                                track.title,
                                track.artist,
                                uiState.isPlaying,
                                track.albumArtUri
                            )

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val appWidgetManager = context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                                val myProvider = android.content.ComponentName(context, com.mahdi.musicpro.widget.MusicProWidgetProvider::class.java)
                                if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                                    val pinnedWidgetCallbackIntent = android.content.Intent(context, com.mahdi.musicpro.widget.MusicProWidgetProvider::class.java)
                                    val successCallback = android.app.PendingIntent.getBroadcast(
                                        context, 0, pinnedWidgetCallbackIntent,
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                    )
                                    appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
                                } else {
                                    android.widget.Toast.makeText(context, context.getString(R.string.widget_pinning_not_supported), android.widget.Toast.LENGTH_LONG).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.feature_requires_android_8), android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C))
                    ) {
                        Text(stringResource(R.string.add_widget_to_homescreen_btn))
                    }
                },
                containerColor = Color(0xFF161824)
            )
        }
    }
}

@Composable
fun QueuePanel(viewModel: MusicViewModel, uiState: PlayerUiState) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxSize()) {
        val currentTrack = uiState.currentTrack
        if (currentTrack != null) {
            // Compact Currently Playing banner at top (always visible when Queue is open)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    TrackCover(track = currentTrack, fallbackIconSize = 20.dp, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "در حال پخش",
                        color = Color(0xFFFF416C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentTrack.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = currentTrack.artist,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.pending_queue_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            TextButton(
                onClick = { viewModel.clearQueue() },
                modifier = Modifier.testTag("clear_queue_button")
            ) {
                Text(stringResource(R.string.clear_queue), color = Color(0xFFFF416C))
            }
        }

        if (uiState.queue.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pending_queue_empty), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                itemsIndexed(uiState.queue, key = { idx, track -> "${idx}_${track.id}" }) { idx, track ->
                    val isPlayingNow = uiState.queueIndex == idx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (draggingIndex == idx) Color.White.copy(alpha = 0.15f)
                                else if (isPlayingNow) Color.White.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .clickable {
                                if (!isPlayingNow) {
                                    viewModel.playTrackAtIndex(idx)
                                }
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = if (isPlayingNow) Color(0xFFFF416C) else Color.Gray,
                            modifier = Modifier.width(28.dp),
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            TrackCover(track = track, fallbackIconSize = 18.dp, modifier = Modifier.fillMaxSize())
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isPlayingNow) Color(0xFFFF416C) else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(track.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                        }

                        if (isPlayingNow) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFFFF416C))
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Drag handle icon for reordering
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .pointerInput(idx) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingIndex = idx
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val itemHeight = with(density) { 56.dp.toPx() }
                                            val currentIdx = draggingIndex ?: return@detectDragGestures
                                            if (dragOffsetY > itemHeight / 2 && currentIdx < uiState.queue.lastIndex) {
                                                viewModel.reorderQueue(currentIdx, currentIdx + 1)
                                                draggingIndex = currentIdx + 1
                                                dragOffsetY -= itemHeight
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            } else if (dragOffsetY < -itemHeight / 2 && currentIdx > 0) {
                                                viewModel.reorderQueue(currentIdx, currentIdx - 1)
                                                draggingIndex = currentIdx - 1
                                                dragOffsetY += itemHeight
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = if (draggingIndex == idx) Color(0xFFFF416C) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0f) }

    LaunchedEffect(value) {
        if (!isDragging) {
            sliderValue = value
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "buffering_transition")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "buffering_alpha"
    )

    val finalAlpha = if (isBuffering) alphaAnim else 1.0f

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                isDragging = true
                sliderValue = newValue
            },
            onValueChangeFinished = {
                isDragging = false
                onValueChange(sliderValue)
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White.copy(alpha = finalAlpha),
                activeTrackColor = Color(0xFFFF416C).copy(alpha = finalAlpha),
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = modifier
                .fillMaxWidth()
                .height(36.dp)
        )
    }
}

// Format time string to MM:SS
fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

fun formatLargeDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

@Composable
fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    isPercentage: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }

    val displayValue by animateFloatAsState(
        targetValue = value,
        animationSpec = if (isDragging) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "eq_fader_anim"
    )

    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        // 1. Band Label (Bold)
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 2. Value indicator as digital dB (+6dB to -6dB) or percentage
        val indicatorText = if (isPercentage) {
            String.format(java.util.Locale.US, "%d%%", (value * 100).toInt())
        } else {
            val dbValue = (value * 12f) - 6f
            if (dbValue > 0f) String.format(java.util.Locale.US, "+%.1fdB", dbValue) else if (dbValue == 0f) "0.0dB" else String.format(java.util.Locale.US, "%.1fdB", dbValue)
        }
        Text(
            text = indicatorText,
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 3. Fader track and drag knob
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(180.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isDragging = true
                        val heightPx = size.height
                        if (heightPx > 0) {
                            val newValue = 1f - (down.position.y / heightPx)
                            currentOnValueChange(newValue.coerceIn(0f, 1f))
                        }
                        
                        // Drag continuously!
                        var dragId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.find { it.id == dragId }
                            if (change == null || !change.pressed) {
                                break
                            }
                            change.consume()
                            val currentHeightPx = size.height
                            if (currentHeightPx > 0) {
                                val newValue = 1f - (change.position.y / currentHeightPx)
                                currentOnValueChange(newValue.coerceIn(0f, 1f))
                            }
                        }
                        isDragging = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Track base line
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            )

            // Dynamic level fill and drag knob
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val height = maxHeight
                val thumbOffset = height * (1f - displayValue)

                // Colored level fill from bottom up
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(4.dp)
                        .height(height * displayValue)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
                            )
                        )
                )

                // The physical metallic knob handle
                Box(
                    modifier = Modifier
                        .offset(y = thumbOffset - 12.dp)
                        .align(Alignment.TopCenter)
                        .size(24.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(2.dp, Color(0xFFFF416C), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFF416C), CircleShape)
                    )
                }
            }
        }
    }
}

// ======================== EQUALIZER SELECTOR MODULE ========================

@Composable
fun EqualizerContent(
    eqState: EqualizerState,
    isSupported: Boolean,
    onBandChange: (String, Float) -> Unit,
    onPresetSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.equalizer_dsp),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (!isSupported) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚠️",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "اکولایزر سخت‌افزاری توسط دستگاه پشتیبانی نمی‌شود. تغییرات فیدرهای فرکانسی ممکن است اعمال نشوند.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Horizontal Presets selectors
        Text(
            text = stringResource(R.string.sound_effects),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.equalizer_desc),
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))
        val presets = listOf("Normal", "Pop", "Classic", "Jazz", "Rock")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = eqState.selectedPreset == preset
                val displayLabel = when (preset) {
                    "Normal" -> stringResource(R.string.preset_normal)
                    "Pop" -> stringResource(R.string.preset_pop)
                    "Classic" -> stringResource(R.string.preset_classic)
                    "Jazz" -> stringResource(R.string.preset_jazz)
                    "Rock" -> stringResource(R.string.preset_rock)
                    else -> preset
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFFFF416C) else Color.White.copy(alpha = 0.05f))
                        .clickable { onPresetSelect(preset) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(displayLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Fine divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.1f))
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Vertical Frequency Faders
        Text(
            text = stringResource(R.string.analog_band_tuning),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.fine_tune_individual),
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bands = listOf("Bass" to eqState.bass, "Treble" to eqState.treble, "Vocal" to eqState.vocal, "Spatial" to eqState.spatial3d)
            bands.forEach { (band, value) ->
                val displayLabel = when (band) {
                    "Bass" -> stringResource(R.string.slider_bass)
                    "Treble" -> stringResource(R.string.slider_treble)
                    "Vocal" -> stringResource(R.string.slider_vocal)
                    "Spatial" -> stringResource(R.string.slider_spatial)
                    else -> band
                }
                VerticalFader(
                    value = value,
                    onValueChange = { onBandChange(band, it) },
                    label = displayLabel,
                    isPercentage = (band == "Spatial"),
                    modifier = Modifier.weight(1f).testTag("slider_band_$band")
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Full width Reset button with distinct background color
        Button(
            onClick = { onPresetSelect("Normal") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072ff)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("reset_equalizer_button")
        ) {
            Text(
                text = stringResource(R.string.reset),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ======================== SLEEP TIMER SELECTOR MODULE ========================

@Composable
fun SleepTimerContent(
    uiState: PlayerUiState,
    onSelectMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.auto_sleep_timer),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "پخش موسیقی را پس از اتمام زمان تعیین‌شده به صورت خودکار متوقف می‌کند.",
            color = Color.Gray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Active state tracker
        if (uiState.sleepTimerMinutesLeft != null && uiState.sleepTimerSecondsLeft != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFF416C).copy(alpha = 0.15f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("تایمر خواب فعال است", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(R.string.time_remaining, uiState.sleepTimerMinutesLeft ?: 0, uiState.sleepTimerSecondsLeft ?: 0),
                            color = Color(0xFFFF416C),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }

                    Button(
                        onClick = onCancelTimer,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B2B))
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Text("تنظیم مستقیم زمان:", color = Color.LightGray, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(10.dp))

        val timerOptions = listOf(5, 15, 30, 45, 60)
        timerOptions.forEach { minutes ->
            val isCurrentTimer = uiState.selectedSleepTimerMinutes == minutes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrentTimer) Color(0xFFFF416C).copy(alpha = 0.15f) else Color.Transparent)
                    .then(
                        if (isCurrentTimer) Modifier.border(1.dp, Color(0xFFFF416C), RoundedCornerShape(12.dp)) else Modifier
                    )
                    .clickable { onSelectMinutes(minutes) }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (isCurrentTimer) Color(0xFFFF416C) else Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "$minutes " + stringResource(R.string.minutes_label),
                        color = if (isCurrentTimer) Color(0xFFFF416C) else Color.White,
                        fontSize = 16.sp,
                        fontWeight = if (isCurrentTimer) FontWeight.Bold else FontWeight.Normal
                    )
                }
                if (isCurrentTimer) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Active",
                        tint = Color(0xFFFF416C)
                    )
                } else {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom time input setup (Manual type manual entry options)
        val isCustomTimerActive = uiState.selectedSleepTimerMinutes != null && uiState.selectedSleepTimerMinutes !in timerOptions
        var customMinutesStr by remember { mutableStateOf("") }

        LaunchedEffect(uiState.selectedSleepTimerMinutes) {
            if (isCustomTimerActive) {
                customMinutesStr = uiState.selectedSleepTimerMinutes.toString()
            }
        }

        Text(stringResource(R.string.custom_duration), color = Color.LightGray, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isCustomTimerActive) Color(0xFFFF416C).copy(alpha = 0.1f) else Color.Transparent)
                .then(
                    if (isCustomTimerActive) Modifier.border(1.dp, Color(0xFFFF416C), RoundedCornerShape(12.dp)) else Modifier
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = customMinutesStr,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 4) {
                        customMinutesStr = input
                    }
                },
                placeholder = { Text(stringResource(R.string.minutes_label), color = Color.Gray, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF416C),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                    focusedLabelColor = Color(0xFFFF416C),
                    unfocusedLabelColor = Color.Gray
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sleep_timer_custom_input"),
                shape = RoundedCornerShape(10.dp)
            )

            val customMinutes = customMinutesStr.toIntOrNull()
            Button(
                onClick = {
                    if (customMinutes != null && customMinutes > 0) {
                        onSelectMinutes(customMinutes)
                    }
                },
                enabled = customMinutes != null && customMinutes > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF416C),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.2f)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("sleep_timer_custom_btn")
            ) {
                Text(stringResource(R.string.apply), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ======================== APP SETTINGS SCREEN MODULE ========================

@Composable
fun SettingsScreen(
    uiState: PlayerUiState,
    onBack: () -> Unit,
    onSetAudioQuality: (String) -> Unit,
    onSetCrossfade: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    if (showAboutDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.about_music),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.version_label) + ": ${com.mahdi.musicpro.BuildConfig.VERSION_NAME}",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "یک پخش‌کننده موسیقی زیبا، پیشرفته و با کیفیت صدای فوق‌العاده بالا.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.confirm), color = Color(0xFFFF416C))
                }
            },
            containerColor = Color(0xFF161826)
        )
    }

    if (showPrivacyPolicyDialog) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
        ) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPrivacyPolicyDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFFFF416C),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.privacy_policy_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    val pScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .heightIn(max = 350.dp)
                            .verticalScroll(pScrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "سیاست حفظ حریم خصوصی کاربران Music Pro",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)

                        val itemShape = RoundedCornerShape(12.dp)

                        // Item 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF222538), shape = itemShape)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = Color(0xFFFF4B2B),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "۱. دسترسی به فایل‌های صوتی:",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "این برنامه به منظور فهرست‌بندی و پخش آهنگ‌های ذخیره شده روی دستگاه شما، نیاز مبرم به دسترسی خواندن فایل‌های صوتی (READ_MEDIA_AUDIO یا READ_EXTERNAL_STORAGE) دارد. اطلاعات فایل‌های صوتی شما به صورت کاملاً آفلاین فقط خوانده می‌شود و هرگز به هیچ سرور خارجی منتقل نمی‌گردد.",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Item 2
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF222538), shape = itemShape)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFFF4B2B),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "۲. عدم ردیابی یا گردآوری داده‌های حساس:",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ما هیچ‌گونه اطلاعات شخصی یا محرمانه‌ای (مانند نام، آدرس، ایمیل، شماره تماس یا شماره سریال دستگاه) را جمع‌آوری یا مخابره نمی‌کنیم.",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Item 3
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF222538), shape = itemShape)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color(0xFFFF4B2B),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "۳. ذخیره‌سازی محلی:",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "تمام داده‌های تولیدی داخل اپ نظیر لیست‌های پخش انتخابی (Playlists)، علاقه‌مندی‌ها (Favorites) و کش آهنگ‌ها به صورت کاملاً ایزوله در پایگاه‌داده داخلی (Room) دستگاه ذخیره می‌گردند.",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Item 4
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF222538), shape = itemShape)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFFFF4B2B),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "۴. سرویس‌های پس‌زمینه:",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "مجوز سرویس پس‌زمینه صرفاً برای ادامه جریان پخش صوتی در زمان بسته‌شدن نمای برنامه یا خاموش شدن صفحه تعبیه شده است.",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPrivacyPolicyDialog = false }) {
                        Text(stringResource(R.string.confirm), color = Color(0xFFFF416C), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF161826)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F101A))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.music_settings_header),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable list of settings
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {

                // 4. History Cleanser Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161826)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFFF416C))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(stringResource(R.string.tab_history), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("پاک کردن الگوهای شنیداری اخیر شما به صورت ایمن.", color = Color.Gray, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    onClearHistory()
                                    android.widget.Toast.makeText(context, context.getString(R.string.history_cleared), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B2B)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("clear_history_settings_btn")
                            ) {
                                Text(stringResource(R.string.delete), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 5. Cache Management Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161826)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFFFF416C))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(stringResource(R.string.cache_storage_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = String.format(java.util.Locale.US, context.getString(R.string.current_cache_size, uiState.simulatedCacheSizeMb)),
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = {
                                    onClearCache()
                                    android.widget.Toast.makeText(context, context.getString(R.string.cache_cleared_toast), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                enabled = uiState.simulatedCacheSizeMb > 0.0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    disabledContainerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("clear_cache_settings_btn")
                            ) {
                                Text(
                                    text = stringResource(R.string.clean_btn),
                                    color = if (uiState.simulatedCacheSizeMb > 0.0) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 5.5. Privacy Policy Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161826)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPrivacyPolicyDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFFFF416C)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.privacy_policy_title),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.privacy_policy_desc),
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // 6. About App Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161826)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAboutDialog = true }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.app_name),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "پلتفرم پخش پرمیوم نسخه v" + com.mahdi.musicpro.BuildConfig.VERSION_NAME,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "کلیک کنید برای مشاهده جزئیات\nموتور پخش: Ultra-HIFI Smart Core Synth™\nتمامی حقوق محفوظ است © Music Pro ۲۰۲۶",
                                color = Color.LightGray.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionEmptyState(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF0F1016)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.audio_access_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.audio_access_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .widthIn(min = 220.dp)
                .height(48.dp)
                .testTag("grant_permission_btn")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color(0xFFE94057), Color(0xFFF27121))))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.grant_permission_btn),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
