package com.reed.hagianggo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.LiveRideCockpitSheet
import com.reed.hagianggo.ui.components.PlaceFormDialog
import com.reed.hagianggo.ui.components.WhatNowBottomSheet
import com.reed.hagianggo.ui.screens.NearbyScreen
import com.reed.hagianggo.ui.screens.NotebookScreen
import com.reed.hagianggo.ui.screens.TodayScreen
import com.reed.hagianggo.ui.screens.TripScreen
import com.reed.hagianggo.ui.theme.*

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HaGiangGoTheme {
                val context = LocalContext.current
                val selectedTab by viewModel.selectedTab.collectAsState()
                val isWhatNowOpen by viewModel.isWhatNowOpen.collectAsState()
                val whatNowQuery by viewModel.whatNowQuery.collectAsState()
                val suggestions by viewModel.whatNowSuggestions.collectAsState()
                val isPlaceFormOpen by viewModel.isPlaceFormOpen.collectAsState()
                val editingPlace by viewModel.editingPlace.collectAsState()
                val zones by viewModel.zones.collectAsState()
                val activeZone by viewModel.activeZone.collectAsState()
                val currentLocation by viewModel.currentLocation.collectAsState()
                val isOutsideHaGiang by viewModel.isOutsideHaGiang.collectAsState()
                val liveRideState by viewModel.liveRideState.collectAsState()
                val isCockpitOpen by viewModel.isCockpitOpen.collectAsState()
                val notificationMessage by viewModel.notificationMessage.collectAsState()

                val snackbarHostState = remember { SnackbarHostState() }

                // Request Location & Notification Permission
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (fineGranted || coarseGranted) {
                        viewModel.locationTracker.startListening()
                    }
                }

                LaunchedEffect(Unit) {
                    val hasFine = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    val perms = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    if (!hasFine) {
                        permissionLauncher.launch(perms.toTypedArray())
                    }
                }

                // Show notification messages via Snackbar
                LaunchedEffect(notificationMessage) {
                    notificationMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearNotification()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "HÀ GIANG GO",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        ),
                                        color = SunsetOrange
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    // Status pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isOutsideHaGiang) AmberGlow else if (currentLocation != null) HighlandGreen else FuelGold)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = if (isOutsideHaGiang) "${activeZone?.name ?: "TP. Hà Giang"} (Xem trước)"
                                                else (activeZone?.name ?: "100% Offline"),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
                                }
                            },
                            actions = {
                                IconButton(onClick = { viewModel.openWhatNow() }) {
                                    Icon(
                                        Icons.Default.Explore,
                                        contentDescription = "Làm gì bây giờ?",
                                        tint = SunsetOrange
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            val tabs = listOf(
                                Triple(0, "Chuyến đi", Icons.Default.Map),
                                Triple(1, "Hôm nay", Icons.Default.Today),
                                Triple(2, "Gần đây", Icons.Default.NearMe),
                                Triple(3, "Sổ tay", Icons.Default.LibraryBooks)
                            )

                            tabs.forEach { (index, title, icon) ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { viewModel.selectTab(index) },
                                    icon = { Icon(icon, contentDescription = title) },
                                    label = {
                                        Text(
                                            text = title,
                                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SunsetOrange,
                                        selectedTextColor = SunsetOrange,
                                        indicatorColor = SunsetOrange.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Crossfade(
                        targetState = selectedTab,
                        modifier = Modifier.padding(innerPadding),
                        label = "TabCrossfade"
                    ) { tab ->
                        when (tab) {
                            0 -> TripScreen(viewModel = viewModel)
                            1 -> TodayScreen(viewModel = viewModel)
                            2 -> NearbyScreen(viewModel = viewModel)
                            3 -> NotebookScreen(viewModel = viewModel)
                        }
                    }
                }

                // Bottom Sheet: Làm gì bây giờ?
                if (isWhatNowOpen) {
                    WhatNowBottomSheet(
                        query = whatNowQuery,
                        zones = zones,
                        activeZone = activeZone,
                        suggestions = suggestions,
                        onQueryChanged = { updated -> viewModel.updateWhatNowQuery(updated) },
                        onZoneManualSelected = { zoneId -> viewModel.setManualZoneOverride(zoneId) },
                        onRefreshAlternative = { viewModel.refreshWhatNowAlternative() },
                        onToggleBookmark = { id -> viewModel.toggleBookmark(id) },
                        onToggleVisited = { id -> viewModel.toggleVisited(id) },
                        onDismiss = { viewModel.closeWhatNow() }
                    )
                }

                // Dialog: Thêm / Sửa địa điểm
                if (isPlaceFormOpen) {
                    PlaceFormDialog(
                        initialPlace = editingPlace,
                        zones = zones,
                        defaultZoneId = activeZone?.id ?: "dong_van",
                        onSave = { place -> viewModel.savePlace(place) },
                        onDelete = { id -> viewModel.deletePlace(id) },
                        onDismiss = { viewModel.closePlaceFormDialog() }
                    )
                }

                // Cockpit HUD Chạy xe (Live Ride Mode)
                if (isCockpitOpen) {
                    LiveRideCockpitSheet(
                        state = liveRideState,
                        onPause = { viewModel.pauseRide() },
                        onResume = { viewModel.resumeRide() },
                        onStop = { viewModel.stopRide() },
                        onDismiss = { viewModel.closeCockpit() }
                    )
                }
            }
        }
    }
}
