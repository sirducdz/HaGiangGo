package com.reed.hagianggo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.GoldenHourAdvisor
import com.reed.hagianggo.domain.ItineraryEditorLogic
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.domain.TimePacerEngine
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
import com.reed.hagianggo.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private val ApplePanelExpand = expandVertically(
    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
    expandFrom = Alignment.Top
) + fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))

private val ApplePanelCollapse = shrinkVertically(
    animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
    shrinkTowards = Alignment.Top
) + fadeOut(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.tripConfig.collectAsState()
    val zones by viewModel.zones.collectAsState()
    val places by viewModel.places.collectAsState()
    val itinerarySlots by viewModel.itinerarySlots.collectAsState()
    val isItineraryCustomized by viewModel.isItineraryCustomized.collectAsState()
    val nightStayVariant by viewModel.nightStayVariant.collectAsState()
    val activeZone by viewModel.activeZone.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isOutsideHaGiang by viewModel.isOutsideHaGiang.collectAsState()
    val distanceToHaGiangKm by viewModel.distanceToHaGiangKm.collectAsState()
    val liveRideState by viewModel.liveRideState.collectAsState()
    val fuelState by viewModel.fuelState.collectAsState()
    val fuelAdvisory by viewModel.fuelAdvisory.collectAsState()
    val weatherForecasts by viewModel.weatherForecasts.collectAsState()
    val dayStartMinutes by viewModel.dayStartMinutes.collectAsState()
    val useCurrentClockForPacer by viewModel.useCurrentClockForPacer.collectAsState()
    val liveClockMinutes by viewModel.liveClockMinutes.collectAsState()
    val riderPaceFactor by viewModel.riderPaceFactor.collectAsState()
    val isAutoGeofenceEnabled by viewModel.isAutoGeofenceEnabled.collectAsState()
    val skippedTodayPlaceIds by viewModel.skippedTodayPlaceIds.collectAsState()
    val activeStopTimer by viewModel.activeStopTimer.collectAsState()

    var selectedDay by remember { mutableIntStateOf(1) }
    LaunchedEffect(config.durationDays) {
        if (selectedDay > config.durationDays) {
            selectedDay = config.durationDays
        }
    }
    var highlightedPlaceId by remember { mutableStateOf<String?>(null) }
    var isEditItineraryMode by remember { mutableStateOf(false) }
    var showAddPlacePicker by remember { mutableStateOf(false) }
    var pickerDefaultSlot by remember { mutableStateOf(TimeSlot.MORNING) }
    var movingPlaceState by remember { mutableStateOf<Pair<Place, TimeSlot>?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val currentTotalMinutes = liveClockMinutes
    val sunsetMinutes = config.sunsetCutoffHour * 60 + config.sunsetCutoffMinute
    val minutesUntilSunset = sunsetMinutes - currentTotalMinutes
    val isNearDark = currentTotalMinutes >= (16 * 60 + 30)
    val isNight = currentTotalMinutes >= sunsetMinutes

    val placesById = remember(places) { places.associateBy { it.id } }

    val daySlots = remember(itinerarySlots, selectedDay) {
        itinerarySlots.filter { it.dayNumber == selectedDay }
    }
    val orderedDayStops = remember(daySlots, placesById) {
        val seenIds = HashSet<String>()
        val result = ArrayList<TimePacerEngine.DayStopInput>()
        for (slot in listOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON, TimeSlot.EVENING)) {
            for (item in daySlots.filter { it.slot == slot }) {
                for (pid in item.suggestedPlaceIds) {
                    val p = placesById[pid]
                    if (p != null && seenIds.add(p.id)) {
                        result.add(TimePacerEngine.DayStopInput(place = p, customNote = item.title, slot = slot))
                    }
                }
            }
        }
        result
    }
    val orderedDayPlaces = remember(orderedDayStops) {
        orderedDayStops.map { it.place }
    }

    val dayPacingSummary = remember(
        orderedDayStops,
        config.vehicle,
        dayStartMinutes,
        useCurrentClockForPacer,
        liveClockMinutes,
        currentLocation,
        isOutsideHaGiang,
        activeStopTimer,
        riderPaceFactor,
        skippedTodayPlaceIds,
        sunsetMinutes
    ) {
        TimePacerEngine.computeDaySchedule(
            dayStops = orderedDayStops,
            vehicle = config.vehicle,
            startMinutesOfDay = dayStartMinutes,
            isLiveNowMode = useCurrentClockForPacer,
            skippedPlaceIds = skippedTodayPlaceIds,
            sunsetCutoffMinutes = sunsetMinutes,
            currentClockMinutes = liveClockMinutes,
            liveGpsLat = currentLocation?.first,
            liveGpsLng = currentLocation?.second,
            isOutsideHaGiang = isOutsideHaGiang,
            activeStopTimer = activeStopTimer,
            riderPaceFactor = riderPaceFactor
        )
    }
    val scheduledNodeById = remember(dayPacingSummary) {
        dayPacingSummary.nodes.associateBy { it.place.id }
    }

    val topClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 58.dp
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 92.dp
    val screenScrollState = rememberScrollState()

    val groupedDaySlots = remember(daySlots, placesById) {
        listOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON, TimeSlot.EVENING).map { slot ->
            val slotsInThisTime = daySlots.filter { it.slot == slot }
            val placesForSlot = slotsInThisTime.flatMap { item ->
                item.suggestedPlaceIds.mapNotNull { pid -> placesById[pid]?.let { p -> item to p } }
            }
            Triple(slot, slotsInThisTime.firstOrNull()?.title.orEmpty(), placesForSlot)
        }
    }

    val dayRouteHint = remember(orderedDayPlaces, dayPacingSummary.totalRoadKm) {
        ItineraryEditorLogic.buildDynamicDayRouteSubtitle(
            places = orderedDayPlaces,
            totalRoadKm = dayPacingSummary.totalRoadKm
        )
    }
    val maxAlt = remember(orderedDayPlaces, selectedDay) {
        orderedDayPlaces.maxOfOrNull { it.altitudeMeters } ?: 1500
    }
    val activeWeather = activeZone?.id?.let { weatherForecasts[it] } ?: weatherForecasts.values.firstOrNull()

    // Dùng Column + verticalScroll thay cho LazyColumn (mỗi ngày chỉ ~10-15 điểm):
    // Tránh LazyColumn hủy/tạo lại các thẻ địa điểm bên dưới ngay giữa lúc panel "Độ cao & Giờ" đang thu gọn,
    // giữ 100% khung hình mượt mà không bị khựng khi mở rộng/thu nhỏ panel.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(screenScrollState)
            .padding(
                top = topClearance,
                bottom = bottomClearance,
                start = 16.dp,
                end = 16.dp
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =====================================================================
        // 1. TỔNG QUAN NHANH (THỜI TIẾT • HOÀNG HÔN/HUD/SOS • TẦM XĂNG)
        // =====================================================================
        AppleMorningBriefSection(
            activeZoneName = activeZone?.name?.substringBefore("(")?.trim() ?: "Hà Giang",
            activeWeather = activeWeather,
            isNight = isNight,
            isNearDark = isNearDark,
            minutesUntilSunset = minutesUntilSunset,
            isOutsideHaGiang = isOutsideHaGiang,
            distanceToHaGiangKm = distanceToHaGiangKm,
            liveRideState = liveRideState,
            fuelState = fuelState,
            fuelAdvisory = fuelAdvisory,
            onOpenWeather = { viewModel.openWeatherSheet() },
            onOpenSos = { viewModel.openSosSheet() },
            onOpenHud = {
                if (!liveRideState.isTracking) viewModel.startRide()
                viewModel.openCockpit()
            },
            onRefuelFull = { viewModel.markTankRefueled() },
            onSetFuelFraction = { frac -> viewModel.setFuelFraction(frac) },
            onNavigateToNearestFuel = { station -> viewModel.requestNavigationTo(station) }
        )

        // =====================================================================
        // 2. KHAY CHỌN NGÀY + LỘ TRÌNH LINH HOẠT + CÔNG CỤ SỬA
        // =====================================================================
        AppleScheduleHeader(
            duration = config.duration,
            selectedDay = selectedDay.coerceIn(1, config.durationDays),
            nightStayVariant = nightStayVariant,
            isCustomized = isItineraryCustomized,
            isEditMode = isEditItineraryMode,
            onSelectDay = { day ->
                selectedDay = day
                highlightedPlaceId = null
            },
            onSelectDuration = { days -> viewModel.updateTripConfig(config.withDurationDays(days)) },
            onSelectNightStay = { variant -> viewModel.setNightStayVariant(variant) },
            onToggleEditMode = { isEditItineraryMode = !isEditItineraryMode },
            onRequestReset = { showResetConfirmDialog = true },
            onAddPlace = {
                pickerDefaultSlot = TimeSlot.MORNING
                showAddPlacePicker = true
            }
        )

        // =====================================================================
        // 3. TÓM TẮT ĐỊA HÌNH & ĐIỀU ĐỘ GIỜ (THU GỌN / CHẠM MỞ BIỂU ĐỒ)
        // =====================================================================
        AppleRouteSummaryCard(
            dayNumber = selectedDay,
            dayPlaces = orderedDayPlaces,
            routeSubtitle = dayRouteHint,
            maxAltitudeMeters = maxAlt,
            summary = dayPacingSummary,
            isAutoGeofenceEnabled = isAutoGeofenceEnabled,
            isRideTracking = liveRideState.isTracking,
            onPlaceSelectedOnChart = { place -> highlightedPlaceId = place.id },
            onSelectStartMinutes = { min -> viewModel.setDayStartTime(min) },
            onEnableLiveNow = {
                val cal = Calendar.getInstance()
                val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                viewModel.enableLiveNowPacer(nowMin)
            },
            onSelectPaceFactor = { factor -> viewModel.setRiderPaceFactor(factor) },
            onToggleAutoGeofence = { viewModel.toggleAutoGeofencePacer() },
            onStartRideTracking = { viewModel.startRide() },
            onAutoTrim = {
                viewModel.applySmartAutoTrim(
                    dayStops = orderedDayStops,
                    startMinutes = dayStartMinutes,
                    isLiveNowMode = useCurrentClockForPacer
                )
            }
        )

        // Thanh đồng hồ đếm ngược khi đang dừng chân tại điểm (Pit-Stop Timer)
        activeStopTimer?.let { timer ->
            ActivePitStopBanner(
                timer = timer,
                onAdd10Min = { viewModel.addPitStopMinutes(10) },
                onComplete = { viewModel.completePitStop(timer.placeId) },
                onCancel = { viewModel.cancelPitStopTimer() }
            )
        }

        // =====================================================================
        // 4. DÒNG THỜI GIAN HÀNH TRÌNH SẠCH KIỂU APPLE MAPS
        // =====================================================================
        groupedDaySlots.forEach { (slot, slotSubtitle, placesForSlot) ->
            key("header_${selectedDay}_${slot.name}") {
                TimeSlotHeader(
                    slot = slot,
                    count = placesForSlot.size,
                    slotSubtitle = slotSubtitle
                )
            }

            if (placesForSlot.isEmpty()) {
                key("empty_${selectedDay}_${slot.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Linh hoạt nghỉ ngơi trong ${slot.label.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        if (isEditItineraryMode) {
                            Text(
                                text = "+ Thêm vào buổi này",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SkyTelemetry,
                                modifier = Modifier.clickable {
                                    pickerDefaultSlot = slot
                                    showAddPlacePicker = true
                                }
                            )
                        }
                    }
                }
            } else {
                placesForSlot.forEach { (slotItem, place) ->
                    key("day${selectedDay}_${slotItem.id}_${place.id}") {
                        val scheduledNode = scheduledNodeById[place.id]
                        val isTimerRunningHere = activeStopTimer?.placeId == place.id
                        val goldenHour = if (scheduledNode != null &&
                            scheduledNode.status != StopPacingStatus.SKIPPED &&
                            !place.isVisited
                        ) {
                            GoldenHourAdvisor.evaluate(
                                place = place,
                                arrivalMin = scheduledNode.arrivalMinutesOfDay,
                                departMin = scheduledNode.departureMinutesOfDay
                            )
                        } else null

                        ItineraryPlaceCard(
                            place = place,
                            customNote = slotItem.title,
                            scheduledNode = scheduledNode,
                            goldenHour = goldenHour,
                            vehicle = config.vehicle,
                            isTimerRunningHere = isTimerRunningHere,
                            isHighlighted = highlightedPlaceId == place.id,
                            isEditMode = isEditItineraryMode,
                            onNavigate = {
                                viewModel.requestNavigationTo(place)
                            },
                            onOpenGoogleMaps = {
                                LocationMath.openNavigationIntent(context, place.latitude, place.longitude, place.name)
                            },
                            onToggleVisited = { viewModel.toggleVisited(place.id) },
                            onAdjustStayMin = { delta -> viewModel.adjustPlaceStayDuration(place.id, delta) },
                            onStartPitStopTimer = { viewModel.startPitStopTimer(place) },
                            onToggleSkipToday = { viewModel.toggleSkipPlaceToday(place.id) },
                            onQuickSnap = {
                                viewModel.openQuickSnap(
                                    place = place,
                                    dayNumber = selectedDay,
                                    zoneId = place.zoneId
                                )
                            },
                            onMoveUp = { viewModel.moveItineraryPlace(selectedDay, place.id, -1) },
                            onMoveDown = { viewModel.moveItineraryPlace(selectedDay, place.id, 1) },
                            onMoveDaySlot = { movingPlaceState = place to slot },
                            onRemoveFromDay = { viewModel.removePlaceFromItinerary(selectedDay, place.id) },
                            onCall = {
                                if (place.phoneNumber.isNotBlank()) {
                                    LocationMath.openDialerIntent(context, place.phoneNumber)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddPlacePicker) {
        val existingDayIds = remember(orderedDayPlaces) { orderedDayPlaces.map { it.id }.toSet() }
        ItineraryPlacePickerSheet(
            dayNumber = selectedDay,
            initialSlot = pickerDefaultSlot,
            places = places,
            zones = zones,
            existingDayPlaceIds = existingDayIds,
            onSelectPlace = { placeId, targetSlot ->
                viewModel.addPlaceToItinerary(selectedDay, targetSlot, placeId)
                showAddPlacePicker = false
            },
            onCreateCustomPlace = { targetSlot ->
                showAddPlacePicker = false
                viewModel.openAddCustomPlaceForItinerary(selectedDay, targetSlot)
            },
            onDismiss = { showAddPlacePicker = false }
        )
    }

    movingPlaceState?.let { (placeToMove, currentSlotOfPlace) ->
        MoveStopDialog(
            placeName = placeToMove.name,
            currentDay = selectedDay,
            currentSlot = currentSlotOfPlace,
            totalDays = config.durationDays,
            onConfirm = { targetDay, targetSlot ->
                viewModel.moveItineraryPlaceToDaySlot(
                    fromDay = selectedDay,
                    toDay = targetDay,
                    toSlot = targetSlot,
                    placeId = placeToMove.id
                )
                movingPlaceState = null
            },
            onDismiss = { movingPlaceState = null }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = SurfaceWhite,
            title = {
                Text(
                    "Khôi phục lịch trình mẫu?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    "Toàn bộ các điểm bạn vừa thêm, xóa hoặc đảo thứ tự sẽ quay về lịch trình chuẩn (${config.duration.code} • Nghỉ ${nightStayVariant.shortTitle}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            },
            confirmButton = {
                ShadcnButton(
                    onClick = {
                        viewModel.resetItineraryToDefault()
                        isEditItineraryMode = false
                        showResetConfirmDialog = false
                    },
                    variant = ShadcnButtonVariant.Destructive
                ) {
                    Text("Khôi phục mẫu")
                }
            },
            dismissButton = {
                ShadcnButton(onClick = { showResetConfirmDialog = false }, variant = ShadcnButtonVariant.Ghost) {
                    Text("Giữ nguyên", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AppleMorningBriefSection(
    activeZoneName: String,
    activeWeather: ZoneWeatherForecast?,
    isNight: Boolean,
    isNearDark: Boolean,
    minutesUntilSunset: Int,
    isOutsideHaGiang: Boolean,
    distanceToHaGiangKm: Double?,
    liveRideState: LiveRideState,
    fuelState: FuelState,
    fuelAdvisory: FuelAdvisory,
    onOpenWeather: () -> Unit,
    onOpenSos: () -> Unit,
    onOpenHud: () -> Unit,
    onRefuelFull: () -> Unit,
    onSetFuelFraction: (Float) -> Unit,
    onNavigateToNearestFuel: (Place) -> Unit
) {
    val (sunBg, sunTint) = when {
        isNight -> RoseDestructiveSoft to RoseDestructiveText
        isNearDark -> AmberWarningSoft to AmberWarningText
        else -> SkyTelemetrySoft to SkyTelemetry
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hàng 1: 2 thẻ Glance ngang nhau (Thời tiết/Mây | Hoàng hôn/HUD/SOS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShadcnCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = if (activeWeather?.isFogHazard == true) RoseDestructiveBorder else BorderHairline,
                shadowElevation = 0.dp,
                onClick = onOpenWeather
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppleIconSquircle(
                            icon = Icons.Default.Cloud,
                            tint = SkyTelemetry,
                            backgroundColor = SkyTelemetrySoft,
                            size = 28.dp,
                            iconSize = 15.dp
                        )
                        Text(
                            text = "Mây ${activeWeather?.cloudHuntScore ?: 68}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = SkyTelemetry
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "${activeWeather?.currentTempC ?: 18}°C",
                            style = TelemetryTextStyle.copy(fontSize = 20.sp),
                            color = TextPrimary
                        )
                        Text(
                            text = activeZoneName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = activeWeather?.summaryLabel ?: "Dự báo 8 cụm đèo",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = if (activeWeather?.isFogHazard == true) RoseDestructiveText else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ShadcnCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = if (isNight) RoseDestructiveBorder else BorderHairline,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppleIconSquircle(
                            icon = if (isNight) Icons.Default.NightsStay else Icons.Default.WbTwilight,
                            tint = sunTint,
                            backgroundColor = sunBg,
                            size = 28.dp,
                            iconSize = 15.dp
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RoseDestructiveSoft)
                                .border(BorderStroke(0.8.dp, RoseDestructiveBorder), CircleShape)
                                .springClickable(onClick = onOpenSos)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "SOS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseDestructiveText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Column {
                        Text(
                            text = when {
                                isNight -> "Đèo đã tối"
                                minutesUntilSunset > 0 -> "Còn ${minutesUntilSunset / 60}h ${minutesUntilSunset % 60}p"
                                else -> "Sắp tối đèo"
                            },
                            style = TelemetryTextStyle.copy(fontSize = 18.sp),
                            color = if (isNight) RoseDestructiveText else TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = when {
                                isNight && isOutsideHaGiang -> "Nghỉ đêm • Cách ${distanceToHaGiangKm?.toInt() ?: 0}km"
                                isNight -> "Nghỉ đêm • Sau mốc 17:15"
                                isOutsideHaGiang -> "Mốc tối 17:15 • Cách ${distanceToHaGiangKm?.toInt() ?: 0}km"
                                else -> "Trước mốc tối đèo 17:15"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (liveRideState.isTracking) EmeraldGreenSoft else SurfaceMuted)
                            .springClickable(onClick = onOpenHud)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.TwoWheeler,
                                contentDescription = null,
                                tint = if (liveRideState.isTracking) EmeraldGreenText else TextPrimary,
                                modifier = Modifier.size(13.5.dp)
                            )
                            Text(
                                text = if (liveRideState.isTracking) {
                                    "LIVE ${liveRideState.currentSpeedKmh.roundToInt()} km/h"
                                } else {
                                    "Chế độ Phượt • HUD"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (liveRideState.isTracking) EmeraldGreenText else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Hàng 2: Thanh Tầm Xăng 1 dòng gọn (~46dp), chạm để bung lựa chọn
        SlimFuelRangeCard(
            fuelState = fuelState,
            fuelAdvisory = fuelAdvisory,
            onRefuelFull = onRefuelFull,
            onSetFuelFraction = onSetFuelFraction,
            onNavigateToNearestFuel = onNavigateToNearestFuel
        )
    }
}

@Composable
private fun SlimFuelRangeCard(
    fuelState: FuelState,
    fuelAdvisory: FuelAdvisory,
    onRefuelFull: () -> Unit,
    onSetFuelFraction: (Float) -> Unit,
    onNavigateToNearestFuel: (Place) -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val animFuelPct by animateFloatAsState(
        targetValue = fuelAdvisory.fuelPercent,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 320f),
        label = "SlimFuelBarAnim"
    )
    val barColor = when {
        animFuelPct <= 0.28f -> RoseDestructive
        animFuelPct <= 0.50f -> AmberWarning
        else -> EmeraldGreen
    }

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = SurfaceWhite,
        borderColor = if (fuelAdvisory.isCriticalLow) AmberWarningBorder else BorderHairline,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppleIconSquircle(
                    icon = Icons.Default.LocalGasStation,
                    tint = AmberWarningText,
                    backgroundColor = AmberWarningSoft,
                    size = 28.dp,
                    iconSize = 15.dp
                )
                Text(
                    text = "~${fuelState.remainingKm.toInt()} km",
                    style = TelemetryTextStyle.copy(fontSize = 13.sp),
                    color = if (fuelAdvisory.isCriticalLow) AmberWarningText else TextPrimary
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(SurfaceMuted)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animFuelPct.coerceIn(0.04f, 1f))
                            .clip(CircleShape)
                            .background(barColor)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(EmeraldGreenSoft)
                        .springClickable(onClick = onRefuelFull)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Đổ đầy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldGreenText
                    )
                }
                val fuelChevronRot by animateFloatAsState(
                    targetValue = if (isExpanded || fuelAdvisory.isCriticalLow) 180f else 0f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "FuelChevronRot"
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(SurfaceMuted)
                        .clickable { isExpanded = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Chi tiết xăng",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = fuelChevronRot }
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded || fuelAdvisory.isCriticalLow,
                enter = ApplePanelExpand,
                exit = ApplePanelCollapse
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = BorderHairline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vạch xe:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            listOf(0.25f to "1/4", 0.5f to "1/2", 0.75f to "3/4").forEach { (frac, label) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(SurfaceMuted)
                                        .clickable { onSetFuelFraction(frac) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        fuelAdvisory.nearestStation?.let { station ->
                            Text(
                                text = "Gần nhất ~${String.format(Locale.US, "%.1f", fuelAdvisory.distanceToNearestKm)} km ➔",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ElectricOrangeHover,
                                modifier = Modifier.clickable { onNavigateToNearestFuel(station) }
                            )
                        }
                    }

                    if (fuelAdvisory.gapWarningTitle != null &&
                        (fuelAdvisory.isCriticalLow || fuelAdvisory.nextGapAheadKm >= 40.0)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (fuelAdvisory.isCriticalLow) RoseDestructiveSoft else AmberWarningSoft)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "${fuelAdvisory.gapWarningTitle}: ${fuelAdvisory.gapWarningDetail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (fuelAdvisory.isCriticalLow) RoseDestructiveText else AmberWarningText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleScheduleHeader(
    duration: TripDuration,
    selectedDay: Int,
    nightStayVariant: NightStayVariant,
    isCustomized: Boolean,
    isEditMode: Boolean,
    onSelectDay: (Int) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onSelectNightStay: (NightStayVariant) -> Unit,
    onToggleEditMode: () -> Unit,
    onRequestReset: () -> Unit,
    onAddPlace: () -> Unit
) {
    var showVariantDrawer by rememberSaveable { mutableStateOf(false) }
    val nightNum = if (duration == TripDuration.DAYS_4_NIGHTS_3) 2 else 1
    val daysList = (1..duration.totalDays).toList()
    val variantChevronRot by animateFloatAsState(
        targetValue = if (showVariantDrawer) 180f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "VariantChevronRot"
    )

    Column {
        ShadcnSegmentedTabs(
            items = daysList,
            selectedItem = selectedDay,
            onItemSelected = onSelectDay,
            modifier = Modifier.fillMaxWidth()
        ) { day, isSelected ->
            Text(
                text = "Ngày $day",
                color = if (isSelected) TextPrimary else TextMuted,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Viên thuốc chọn Đêm Đồng Văn / Lô Lô Chải & 3N2Đ/4N3Đ (chạm mở ngăn kéo)
            Surface(
                modifier = Modifier.clickable { showVariantDrawer = !showVariantDrawer },
                shape = CircleShape,
                color = SurfaceWhite,
                border = BorderStroke(
                    0.8.dp,
                    if (showVariantDrawer) ElectricOrangeBorder else BorderHairline
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = ElectricOrange,
                        modifier = Modifier.size(15.dp)
                    )
                    val durationDisplay = when (duration) {
                        TripDuration.DAYS_2_NIGHTS_1 -> "2N1Đ"
                        TripDuration.DAYS_3_NIGHTS_2 -> "3N2Đ"
                        TripDuration.DAYS_4_NIGHTS_3 -> "4N3Đ"
                    }
                    Text(
                        text = "$durationDisplay • Đêm $nightNum: ${nightStayVariant.shortTitle}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    if (isCustomized) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SkyTelemetry)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = variantChevronRot }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditMode && isCustomized) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RoseDestructiveSoft)
                            .springClickable(onClick = onRequestReset)
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Khôi phục",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RoseDestructiveText
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isEditMode) ElectricOrange else SurfaceWhite)
                        .border(
                            BorderStroke(0.8.dp, if (isEditMode) ElectricOrange else BorderHairline),
                            CircleShape
                        )
                        .springClickable(onClick = onToggleEditMode)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (isEditMode) Color.White else TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isEditMode) "Xong" else "Sửa",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEditMode) Color.White else TextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SkyTelemetrySoft)
                        .springClickable(onClick = onAddPlace)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "+ Điểm",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SkyTelemetry
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showVariantDrawer,
            enter = ApplePanelExpand,
            exit = ApplePanelCollapse
        ) {
            Box(modifier = Modifier.padding(top = 8.dp)) {
                FlexibleItinerarySwitcherCard(
                    duration = duration,
                    nightStayVariant = nightStayVariant,
                    isCustomized = isCustomized,
                    onSelectDuration = onSelectDuration,
                    onSelectNightStay = onSelectNightStay
                )
            }
        }
    }
}

@Composable
private fun AppleRouteSummaryCard(
    dayNumber: Int,
    dayPlaces: List<Place>,
    routeSubtitle: String,
    maxAltitudeMeters: Int,
    summary: DayPacingSummary,
    isAutoGeofenceEnabled: Boolean,
    isRideTracking: Boolean,
    onPlaceSelectedOnChart: (Place) -> Unit,
    onSelectStartMinutes: (Int) -> Unit,
    onEnableLiveNow: () -> Unit,
    onSelectPaceFactor: (Float) -> Unit,
    onToggleAutoGeofence: () -> Unit,
    onStartRideTracking: () -> Unit,
    onAutoTrim: () -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val onToggleExpanded = { isExpanded = !isExpanded }
    val isOvertime = summary.overtimeMinutes > 0
    val finishStr = TimePacerEngine.formatMinutesOfDay(summary.finishMinutesOfDay)
    val cutoffStr = TimePacerEngine.formatMinutesOfDay(summary.sunsetCutoffMinutes)
    val bufferMin = (summary.sunsetCutoffMinutes - summary.finishMinutesOfDay).coerceAtLeast(0)
    val chevronRot by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "RouteSummaryChevronRot"
    )

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = SurfaceWhite,
        borderColor = if (isOvertime) RoseDestructiveBorder else BorderHairline,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            // Hàng trên cùng (chạm để mở rộng / thu gọn Biểu đồ Độ cao & Điều độ Giờ)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = routeSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Độ cao & Giờ",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ElectricOrangeHover
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                        tint = ElectricOrangeHover,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = chevronRot }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 chỉ số Apple Fitness nằm ngang ngăn bằng vạch dọc mảnh
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quãng đường",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "~${summary.totalRoadKm.roundToInt()} km",
                        style = TelemetryTextStyle.copy(fontSize = 15.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "${TimePacerEngine.formatDurationHoursMin(summary.totalRideMinutes)} chạy xe",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = TextMuted
                    )
                }

                VerticalDivider(color = BorderHairline, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Đỉnh đèo",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "${maxAltitudeMeters}m",
                        style = TelemetryTextStyle.copy(fontSize = 15.sp),
                        color = EmeraldGreenText
                    )
                    Text(
                        text = "${dayPlaces.size} điểm dừng",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = TextMuted
                    )
                }

                VerticalDivider(color = BorderHairline, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Về nghỉ đêm",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = finishStr,
                        style = TelemetryTextStyle.copy(fontSize = 15.sp),
                        color = if (isOvertime) RoseDestructiveText else EmeraldGreenText
                    )
                    Text(
                        text = if (isOvertime) {
                            "Trễ +${TimePacerEngine.formatDurationHoursMin(summary.overtimeMinutes)}"
                        } else {
                            "Trước $cutoffStr"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = if (isOvertime) RoseDestructiveText else TextMuted
                    )
                }
            }

            // Nếu lố giờ ngay ở trạng thái thu gọn -> hiện mượt không nhảy khung
            AnimatedVisibility(
                visible = isOvertime && !isExpanded && summary.canAutoTrim,
                enter = ApplePanelExpand,
                exit = ApplePanelCollapse
            ) {
                Box(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RoseDestructiveSoft)
                            .springClickable(onClick = onAutoTrim)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lố mốc $cutoffStr • Chạm để tự tối ưu giờ về",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = RoseDestructiveText,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Tối ưu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoseDestructiveText
                        )
                    }
                }
            }

            // Phần mở rộng: Biểu đồ độ cao tương tác + Bộ điều chỉnh giờ chạy (padding nằm TRONG AnimatedVisibility để thu gọn êm tuyệt đối)
            AnimatedVisibility(
                visible = isExpanded,
                enter = ApplePanelExpand,
                exit = ApplePanelCollapse
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = BorderHairline)

                    ElevationProfileCard(
                        dayNumber = dayNumber,
                        dayPlaces = dayPlaces,
                        routeSubtitle = routeSubtitle,
                        onPlaceSelected = onPlaceSelectedOnChart,
                        embedded = true
                    )

                    // Hàng chọn Giờ Xuất Phát (dùng clickable nhẹ thay cho springClickable để mở/đóng panel 0ms trễ)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (summary.isLiveNowMode) EmeraldGreen else EmeraldGreenSoft)
                                .clickable(onClick = onEnableLiveNow)
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (summary.isLiveNowMode) {
                                    "Live (${TimePacerEngine.formatMinutesOfDay(summary.startMinutesOfDay)})"
                                } else {
                                    "Theo giờ thực"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (summary.isLiveNowMode) Color.White else EmeraldGreenText
                            )
                        }

                        listOf(
                            390 to "06:30",
                            420 to "07:00",
                            450 to "07:30",
                            480 to "08:00",
                            510 to "08:30",
                            540 to "09:00"
                        ).forEach { (minVal, label) ->
                            val isSelected = !summary.isLiveNowMode && summary.startMinutesOfDay == minVal
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ElectricOrange else SurfaceMuted)
                                    .clickable { onSelectStartMinutes(minVal) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = TelemetryTextStyle.copy(fontSize = 11.sp),
                                    color = if (isSelected) Color.White else TextPrimary
                                )
                            }
                        }
                    }

                    // Hàng Nhịp độ tay lái + Auto Geofence
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nhịp chạy:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        listOf(
                            0.85f to "Nhanh 0.85x",
                            1.0f to "Chuẩn 1.0x",
                            1.20f to "Thong thả 1.2x"
                        ).forEach { (factorVal, label) ->
                            val isSelected = kotlin.math.abs(summary.riderPaceFactor - factorVal) < 0.06f
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SkyTelemetry else SurfaceMuted)
                                    .clickable { onSelectPaceFactor(factorVal) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else TextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAutoGeofenceEnabled) EmeraldGreenSoft else SurfaceMuted)
                                .clickable(onClick = onToggleAutoGeofence)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isAutoGeofenceEnabled) "GPS 250m: Bật" else "GPS 250m: Tắt",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isAutoGeofenceEnabled) EmeraldGreenText else TextMuted
                            )
                        }
                    }

                    if (isAutoGeofenceEnabled && !isRideTracking) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SkyTelemetrySoft)
                                .clickable(onClick = onStartRideTracking)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bật HUD Chế độ Phượt để tự check-in cả khi tắt màn hình",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = SkyTelemetry,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Bật HUD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricOrangeHover
                            )
                        }
                    }

                    if (isOvertime) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(RoseDestructiveSoft)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Lịch đang lố ${TimePacerEngine.formatDurationHoursMin(summary.overtimeMinutes)} sau $cutoffStr.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = RoseDestructiveText
                            )
                            if (!summary.autoTrimHint.isNullOrBlank()) {
                                Text(
                                    text = summary.autoTrimHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                            if (summary.canAutoTrim) {
                                ShadcnButton(
                                    onClick = onAutoTrim,
                                    variant = ShadcnButtonVariant.Primary,
                                    modifier = Modifier.fillMaxWidth().height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text("Tự động co giờ để kịp về trước $cutoffStr", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmeraldGreenSoft.copy(alpha = 0.7f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Tiến độ an toàn: dự kiến hoàn thành lúc $finishStr (dư $bufferMin phút trước khi tối).",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldGreenText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivePitStopBanner(
    timer: ActiveStopTimer,
    onAdd10Min: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.startedAtMillis, timer.targetDurationMin) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val elapsedSec = ((nowMillis - timer.startedAtMillis) / 1000L).coerceAtLeast(0L)
    val targetSec = timer.targetDurationMin * 60L
    val remainingSec = targetSec - elapsedSec
    val isOver = remainingSec < 0
    val absSec = kotlin.math.abs(remainingSec)
    val mm = absSec / 60L
    val ss = absSec % 60L
    val countdownText = String.format(Locale.US, "%s%02d:%02d", if (isOver) "+" else "", mm, ss)

    val leaveCalendar = remember(timer.startedAtMillis, timer.targetDurationMin) {
        Calendar.getInstance().apply {
            timeInMillis = timer.startedAtMillis + timer.targetDurationMin * 60_000L
        }
    }
    val leaveTimeStr = remember(leaveCalendar) {
        String.format(
            Locale.US,
            "%02d:%02d",
            leaveCalendar.get(Calendar.HOUR_OF_DAY),
            leaveCalendar.get(Calendar.MINUTE)
        )
    }

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = if (isOver) RoseDestructiveSoft else ElectricOrangeSoft,
        borderColor = if (isOver) RoseDestructive else ElectricOrange
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isOver -> "Đã quá giờ dừng • Đang đẩy lùi lịch trình"
                            timer.isAutoGeofence -> "GPS nhận diện bạn đang dừng tại điểm"
                            else -> "Đang đếm giờ dừng nghỉ tại điểm"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isOver) RoseDestructiveText else ElectricOrangeHover
                    )
                    Text(
                        text = timer.placeName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (timer.isAutoGeofence) {
                            "Nên đi tiếp lúc $leaveTimeStr • Rời bán kính 350m sẽ tự động chốt!"
                        } else {
                            "Nên lên xe đi tiếp lúc: $leaveTimeStr (Mục tiêu ${timer.targetDurationMin}p)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceWhite)
                        .border(
                            BorderStroke(1.dp, if (isOver) RoseDestructiveBorder else ElectricOrangeBorder),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isOver) "LỐ GIỜ" else "CÒN LẠI",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (isOver) RoseDestructiveText else TextMuted
                        )
                        Text(
                            text = countdownText,
                            style = TelemetryTextStyle.copy(fontSize = 18.sp),
                            color = if (isOver) RoseDestructiveText else ElectricOrangeHover
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = onAdd10Min,
                    variant = ShadcnButtonVariant.Secondary,
                    modifier = Modifier.weight(0.9f).height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("+10 phút ở thêm", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                ShadcnButton(
                    onClick = onComplete,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1.3f).height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xong • Lên xe đi tiếp", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Tắt", tint = TextMuted, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
fun TimeSlotHeader(
    slot: TimeSlot,
    count: Int = 0,
    slotSubtitle: String = ""
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, color, softBg) = when (slot) {
                    TimeSlot.MORNING -> Triple(Icons.Default.WbTwilight, ElectricOrange, ElectricOrangeSoft)
                    TimeSlot.NOON -> Triple(Icons.Default.WbSunny, AmberWarningText, AmberWarningSoft)
                    TimeSlot.AFTERNOON -> Triple(Icons.Default.Terrain, SkyTelemetry, SkyTelemetrySoft)
                    TimeSlot.EVENING -> Triple(Icons.Default.NightsStay, TextSecondary, SurfaceMuted)
                }
                AppleIconSquircle(
                    icon = icon,
                    tint = color,
                    backgroundColor = softBg,
                    size = 24.dp,
                    iconSize = 13.5.dp,
                    cornerRadius = 7.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${slot.label} • ${slot.timeRange}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = TextPrimary
                )
            }

            if (count > 0) {
                Text(
                    text = "$count điểm",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = TextMuted
                )
            }
        }

        if (slotSubtitle.isNotBlank()) {
            Text(
                text = slotSubtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
    }
}

@Composable
fun ItineraryPlaceCard(
    place: Place,
    customNote: String,
    scheduledNode: ScheduledStopNode? = null,
    goldenHour: GoldenHourAssessment? = null,
    vehicle: VehicleType = VehicleType.MOTORBIKE,
    isTimerRunningHere: Boolean = false,
    isHighlighted: Boolean = false,
    isEditMode: Boolean = false,
    onNavigate: () -> Unit,
    onOpenGoogleMaps: () -> Unit = {},
    onToggleVisited: () -> Unit,
    onAdjustStayMin: ((Int) -> Unit)? = null,
    onStartPitStopTimer: (() -> Unit)? = null,
    onToggleSkipToday: (() -> Unit)? = null,
    onQuickSnap: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onMoveDaySlot: (() -> Unit)? = null,
    onRemoveFromDay: (() -> Unit)? = null,
    onCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSkipped = scheduledNode?.status == StopPacingStatus.SKIPPED
    val isOvertime = scheduledNode?.status == StopPacingStatus.OVERTIME_LATE
    val isNearDusk = scheduledNode?.status == StopPacingStatus.NEAR_DUSK
    var isDetailsExpanded by remember(place.id) { mutableStateOf(false) }

    val checkScale by animateFloatAsState(
        targetValue = if (place.isVisited) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "TimelineCheckBounce"
    )
    val railColor by animateColorAsState(
        targetValue = when {
            place.isVisited -> EmeraldGreen
            isOvertime -> RoseDestructive
            else -> BorderHairline
        },
        animationSpec = tween(220),
        label = "TimelineRailColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Cột nối Timeline gọn bên trái
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(22.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(14.dp)
                    .background(railColor)
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                    }
                    .clip(CircleShape)
                    .background(
                        when {
                            place.isVisited -> EmeraldGreen
                            isSkipped -> SurfaceMuted
                            else -> SurfaceWhite
                        }
                    )
                    .border(
                        BorderStroke(
                            1.8.dp,
                            when {
                                place.isVisited -> EmeraldGreen
                                isSkipped -> BorderMedium
                                isOvertime -> RoseDestructive
                                else -> ElectricOrange
                            }
                        ),
                        CircleShape
                    )
                    .springClickable(onClick = onToggleVisited),
                contentAlignment = Alignment.Center
            ) {
                if (place.isVisited) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Đã ghé",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .weight(1f)
                    .background(railColor)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Thẻ địa điểm chuẩn Apple Maps: thoáng, ít chữ thừa, chạm mở tinh chỉnh sâu
        ShadcnCard(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(18.dp),
            backgroundColor = if (place.isVisited || isSkipped) SurfaceMuted.copy(alpha = 0.6f) else SurfaceWhite,
            borderColor = when {
                isTimerRunningHere -> ElectricOrange
                isHighlighted -> ElectricOrange
                isOvertime -> RoseDestructiveBorder
                else -> BorderHairline
            },
            borderWidth = if (isHighlighted || isTimerRunningHere) 1.4.dp else 0.8.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(13.dp)) {
                // Dòng 1: Giờ chạy & Cự ly leg trên cùng 1 dòng thanh thoát
                if (scheduledNode != null) {
                    if (isSkipped) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Đã tạm bỏ qua hôm nay",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = TextMuted
                            )
                            if (onToggleSkipToday != null) {
                                Text(
                                    text = "Đi lại",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ElectricOrangeHover,
                                    modifier = Modifier.clickable(onClick = onToggleSkipToday)
                                )
                            }
                        }
                    } else {
                        val arrivalStr = TimePacerEngine.formatMinutesOfDay(scheduledNode.arrivalMinutesOfDay)
                        val departStr = TimePacerEngine.formatMinutesOfDay(scheduledNode.departureMinutesOfDay)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$arrivalStr – $departStr",
                                    style = TelemetryTextStyle.copy(fontSize = 11.5.sp),
                                    color = when {
                                        isOvertime -> RoseDestructiveText
                                        isNearDusk -> AmberWarningText
                                        else -> ElectricOrangeHover
                                    }
                                )
                                if (scheduledNode.legTravelMin > 0) {
                                    val prefix = if (scheduledNode.isFromLiveGps) "  •  GPS" else "  •  Lái"
                                    Text(
                                        text = "$prefix ${scheduledNode.legTravelMin}p (${String.format(Locale.US, "%.1f", scheduledNode.legRoadKm)}km)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            when {
                                isOvertime -> Text(
                                    text = "Sau 17:15",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = RoseDestructiveText
                                )
                                goldenHour?.match == GoldenHourMatch.OPTIMAL -> Text(
                                    text = "Giờ vàng",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = EmeraldGreenText
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                }

                // Dòng 2: Tên địa điểm + Danh mục/Cao độ (Chạm để bung chi tiết)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDetailsExpanded = !isDetailsExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp,
                                letterSpacing = (-0.2).sp,
                                textDecoration = if (place.isVisited || isSkipped) {
                                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                                } else null
                            ),
                            color = if (place.isVisited || isSkipped) TextMuted else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${place.category.label}  •  Cao độ ${place.altitudeMeters}m",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = TextMuted
                        )
                    }
                    val detailsChevronRot by animateFloatAsState(
                        targetValue = if (isDetailsExpanded) 180f else 0f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "PlaceChevronRot"
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Chi tiết",
                        tint = TextSubtle,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = detailsChevronRot }
                    )
                }

                // Dòng 3: Mẹo thực chiến gọn 2 dòng
                val bodySummary = place.practicalTip.ifBlank { place.addressHint }
                if (bodySummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = bodySummary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSecondary,
                        maxLines = if (isDetailsExpanded) 8 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { isDetailsExpanded = !isDetailsExpanded }
                    )
                }

                // Khối mở rộng mượt: Địa chỉ + Giờ vàng + Chỉnh thời lượng dừng [-10p] [+10p] + Đếm giờ + Bỏ qua
                AnimatedVisibility(
                    visible = isDetailsExpanded && !isSkipped,
                    enter = ApplePanelExpand,
                    exit = ApplePanelCollapse
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = BorderHairline)

                        if (place.addressHint.isNotBlank() && place.addressHint != bodySummary) {
                            Text(
                                text = place.addressHint,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = TextPrimary
                            )
                        }

                        if (goldenHour != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (goldenHour.match == GoldenHourMatch.OPTIMAL) EmeraldGreenSoft else AmberWarningSoft)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "${goldenHour.statusLabel} • ${goldenHour.detailHint}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (goldenHour.match == GoldenHourMatch.OPTIMAL) EmeraldGreenText else AmberWarningText
                                )
                                Text(
                                    text = "Góc chụp: ${goldenHour.profile.photoAngleTip}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        if (scheduledNode != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceMuted)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Dừng ${scheduledNode.stayDurationMin}p",
                                        style = TelemetryTextStyle.copy(fontSize = 11.5.sp),
                                        color = TextPrimary
                                    )
                                    if (onAdjustStayMin != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SurfaceWhite)
                                                .springClickable { onAdjustStayMin(-10) }
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text("-10p", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SurfaceWhite)
                                                .springClickable { onAdjustStayMin(10) }
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text("+10p", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (onStartPitStopTimer != null && !place.isVisited) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isTimerRunningHere) ElectricOrange else ElectricOrangeSoft)
                                                .springClickable(onClick = onStartPitStopTimer)
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = if (isTimerRunningHere) "Đang đếm" else "Đếm giờ",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isTimerRunningHere) Color.White else ElectricOrangeHover
                                            )
                                        }
                                    }
                                    if (onToggleSkipToday != null && !place.isVisited) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SurfaceWhite)
                                                .springClickable(onClick = onToggleSkipToday)
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text("Bỏ qua", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = TextMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isEditMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple("Lên", SurfaceMuted to TextPrimary, onMoveUp),
                            Triple("Xuống", SurfaceMuted to TextPrimary, onMoveDown),
                            Triple("Đổi buổi", SkyTelemetrySoft to SkyTelemetry, onMoveDaySlot),
                            Triple("Xóa", RoseDestructiveSoft to RoseDestructiveText, onRemoveFromDay)
                        ).forEach { (label, colorsPair, action) ->
                            val (bg, fg) = colorsPair
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .springClickable { action?.invoke() }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = fg
                                )
                            }
                        }
                    }
                }

                // Dòng nút hành động đáy gọn 34dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShadcnButton(
                        onClick = onNavigate,
                        variant = ShadcnButtonVariant.Primary,
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chỉ đường", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                    }

                    ShadcnButton(
                        onClick = onOpenGoogleMaps,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier.width(82.dp).height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = "Mở Google Maps", tint = SkyTelemetry, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("G.Maps", fontWeight = FontWeight.Medium, fontSize = 11.5.sp, maxLines = 1)
                    }

                    if (onQuickSnap != null) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceMuted)
                                .springClickable(onClick = onQuickSnap),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Chụp Watermark",
                                tint = SkyTelemetry,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (place.phoneNumber.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmeraldGreenSoft)
                                .springClickable(onClick = onCall),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Gọi",
                                tint = EmeraldGreenText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlexibleItinerarySwitcherCard(
    duration: TripDuration,
    nightStayVariant: NightStayVariant,
    isCustomized: Boolean,
    onSelectDuration: (Int) -> Unit,
    onSelectNightStay: (NightStayVariant) -> Unit
) {
    val nightNum = if (duration == TripDuration.DAYS_4_NIGHTS_3) 2 else 1
    val is4N3D = duration == TripDuration.DAYS_4_NIGHTS_3

    val (tipBg, tipFg, tipText) = when {
        nightStayVariant == NightStayVariant.LO_LO_CHAI && is4N3D -> Triple(
            SkyTelemetrySoft,
            SkyTelemetry,
            "Đêm 1 Yên Minh • Đêm 2 ngủ bản Lô Lô Chải săn mây • Ngày 3 về Đồng Văn & Mã Pì Lèng."
        )
        nightStayVariant == NightStayVariant.LO_LO_CHAI -> Triple(
            AmberWarningSoft,
            AmberWarningText,
            "Mẹo: Xuất phát ~06:30 & đổ xăng Phố Cáo • Sáng Ngày 2 không lặp đường."
        )
        is4N3D -> Triple(
            EmeraldGreenSoft,
            EmeraldGreenText,
            "Đêm 1 Yên Minh • Đêm 2 Phố cổ Đồng Văn • Đêm 3 Du Già thong thả."
        )
        else -> Triple(
            EmeraldGreenSoft,
            EmeraldGreenText,
            "Dễ về kịp trước 17:15 • Tối dạo phố cổ, sáng Ngày 2 lên Cột cờ Lũng Cú."
        )
    }

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = SurfaceWhite,
        borderColor = BorderHairline
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Chọn lộ trình",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCustomized) {
                        ShadcnBadge(text = "Đã tùy biến", variant = ShadcnBadgeVariant.Sky)
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(SurfaceMuted)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf(3 to "3N2Đ", 4 to "4N3Đ").forEach { (days, label) ->
                        val selected = duration.totalDays == days
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (selected) ElectricOrange else Color.Transparent)
                                .springClickable { onSelectDuration(days) }
                                .padding(horizontal = 8.dp, vertical = 3.5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(SurfaceMuted)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NightStayVariant.entries.forEach { variant ->
                    val selected = nightStayVariant == variant
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) SurfaceWhite else Color.Transparent)
                            .springClickable { onSelectNightStay(variant) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "Đêm $nightNum: ${variant.shortTitle}",
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) TextPrimary else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = variant.tagline,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) ElectricOrangeHover else TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(tipBg)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) {
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = tipFg
                )
            }
        }
    }
}
