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
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.reed.hagianggo.model.elevationMeters
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
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

                var showGlobalZonePicker by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }

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

                LaunchedEffect(notificationMessage) {
                    notificationMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearNotification()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground)
                ) {
                    // 1. Nội dung 4 màn hình với chuyển động trượt định hướng vật lý (Directional Spatial Slide)
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val forward = targetState > initialState
                            val slideSpring = spring<androidx.compose.ui.unit.IntOffset>(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 500f
                            )
                            if (forward) {
                                (slideInHorizontally(slideSpring) { width -> width / 6 } + fadeIn(tween(200)))
                                    .togetherWith(slideOutHorizontally(tween(180)) { width -> -width / 8 } + fadeOut(tween(150)))
                            } else {
                                (slideInHorizontally(slideSpring) { width -> -width / 6 } + fadeIn(tween(200)))
                                    .togetherWith(slideOutHorizontally(tween(180)) { width -> width / 8 } + fadeOut(tween(150)))
                            }.using(SizeTransform(clip = false))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "MainTabSpatialTransition"
                    ) { tab ->
                        when (tab) {
                            0 -> TripScreen(viewModel = viewModel)
                            1 -> TodayScreen(viewModel = viewModel)
                            2 -> NearbyScreen(viewModel = viewModel)
                            3 -> NotebookScreen(viewModel = viewModel)
                        }
                    }

                    // 2. Cụm Header nổi kiểu Flighty (Trạng thái GPS / Độ cao / Nút AI "Làm gì?")
                    FlightyFloatingHeader(
                        activeZoneName = activeZone?.name ?: "TP. Hà Giang",
                        activeAltitudeMeters = if (liveRideState.isTracking && liveRideState.currentAltitude > 10) {
                            liveRideState.currentAltitude.toInt()
                        } else {
                            activeZone?.elevationMeters ?: 120
                        },
                        isGpsLive = currentLocation != null,
                        isOutsideHaGiang = isOutsideHaGiang,
                        isTrackingRide = liveRideState.isTracking,
                        currentSpeedKmh = liveRideState.currentSpeedKmh,
                        onStatusClick = {
                            if (liveRideState.isTracking) {
                                viewModel.openCockpit()
                            } else {
                                showGlobalZonePicker = true
                            }
                        },
                        onWhatNowClick = { viewModel.openWhatNow() },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // 3. Snackbar thông báo nằm gọn ngay trên Thanh viên thuốc đáy
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 82.dp, start = 16.dp, end = 16.dp)
                    )

                    // 4. Thanh điều hướng viên thuốc nổi (Floating Pill Nav Bar)
                    FloatingPillNavBar(
                        selectedTab = selectedTab,
                        onTabSelected = { viewModel.selectTab(it) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Modal Đổi cụm chặng nhanh từ Header Pill
                if (showGlobalZonePicker) {
                    AlertDialog(
                        onDismissRequest = { showGlobalZonePicker = false },
                        containerColor = SurfaceWhite,
                        shape = RoundedCornerShape(20.dp),
                        title = {
                            Text(
                                "Chọn cụm chặng hiện tại",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        text = {
                            LazyColumn {
                                item {
                                    ListItem(
                                        headlineContent = { Text("Tự động theo GPS", color = EmeraldGreenText, fontWeight = FontWeight.SemiBold) },
                                        leadingContent = { Icon(Icons.Default.AutoMode, contentDescription = null, tint = EmeraldGreen) },
                                        colors = ListItemDefaults.colors(containerColor = SurfaceWhite),
                                        modifier = Modifier.clickable {
                                            viewModel.setManualZoneOverride(null)
                                            showGlobalZonePicker = false
                                        }
                                    )
                                    HorizontalDivider(color = BorderSubtle)
                                }
                                items(zones) { zone ->
                                    ListItem(
                                        headlineContent = { Text("${zone.seq}. ${zone.name}", color = TextPrimary, fontWeight = FontWeight.Medium) },
                                        supportingContent = { Text("Độ cao ~${zone.elevationMeters}m", color = TextSecondary) },
                                        leadingContent = { Icon(Icons.Default.Place, contentDescription = null, tint = ElectricOrange) },
                                        colors = ListItemDefaults.colors(containerColor = SurfaceWhite),
                                        modifier = Modifier.clickable {
                                            viewModel.setManualZoneOverride(zone.id)
                                            showGlobalZonePicker = false
                                        }
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            ShadcnButton(
                                onClick = { showGlobalZonePicker = false },
                                variant = ShadcnButtonVariant.Ghost
                            ) {
                                Text("Đóng", color = TextSecondary)
                            }
                        }
                    )
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
                        defaultZoneId = activeZone?.id ?: "tp_ha_giang",
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
