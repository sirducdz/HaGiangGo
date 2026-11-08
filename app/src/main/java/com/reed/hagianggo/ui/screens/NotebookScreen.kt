package com.reed.hagianggo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
import com.reed.hagianggo.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val tripConfig by viewModel.tripConfig.collectAsState()
    val checklist by viewModel.checklist.collectAsState()
    val journal by viewModel.journal.collectAsState()
    val zones by viewModel.zones.collectAsState()
    val members by viewModel.members.collectAsState()
    val memberBalances by viewModel.memberBalances.collectAsState()
    val settlementTransfers by viewModel.settlementTransfers.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Chi tiêu, 1: Checklist, 2: Nhật ký, 3: Sao lưu

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showAddChecklistDialog by remember { mutableStateOf(false) }
    var showAddJournalDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }

    val topClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(top = topClearance)
    ) {
        // Sub-Tab Switcher với viên nang trắng trượt lò xo
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            ShadcnSegmentedTabs(
                items = listOf(0, 1, 2, 3),
                selectedItem = selectedSubTab,
                onItemSelected = { selectedSubTab = it },
                modifier = Modifier.fillMaxWidth()
            ) { tabIndex, isSelected ->
                val (label, icon) = when (tabIndex) {
                    0 -> "Chi tiêu" to Icons.Default.AccountBalanceWallet
                    1 -> "Checklist" to Icons.Default.Checklist
                    2 -> "Nhật ký" to Icons.Default.Book
                    else -> "Sao lưu" to Icons.Default.Backup
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) ElectricOrange else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        color = if (isSelected) TextPrimary else TextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // Directional Spatial AnimatedContent giữa 4 phân hệ Sổ tay
        AnimatedContent(
            targetState = selectedSubTab,
            transitionSpec = {
                val forward = targetState > initialState
                val slideIn = slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                    initialOffsetX = { fullWidth -> if (forward) fullWidth / 5 else -fullWidth / 5 }
                ) + fadeIn(tween(180))
                val slideOut = slideOutHorizontally(
                    animationSpec = tween(160),
                    targetOffsetX = { fullWidth -> if (forward) -fullWidth / 6 else fullWidth / 6 }
                ) + fadeOut(tween(140))
                slideIn togetherWith slideOut
            },
            modifier = Modifier.fillMaxSize(),
            label = "NotebookSubTabTransition"
        ) { tab ->
            when (tab) {
                0 -> ExpenseTabContent(
                    expenses = expenses,
                    tripConfig = tripConfig,
                    members = members,
                    memberBalances = memberBalances,
                    settlementTransfers = settlementTransfers,
                    bottomClearance = bottomClearance,
                    onAddExpenseClick = { showAddExpenseDialog = true },
                    onDeleteExpense = { viewModel.deleteExpense(it) },
                    onAddMemberClick = { showAddMemberDialog = true },
                    onRemoveMember = { viewModel.removeTripMember(it) },
                    onCopySplitSummary = {
                        val summary = viewModel.buildSplitbillSummaryText()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("HaGiangGo_Splitbill", summary))
                        viewModel.notifyMessage("Đã sao chép bảng chia tiền nhóm để dán vào Zalo!")
                    }
                )
                1 -> ChecklistTabContent(
                    checklist = checklist,
                    bottomClearance = bottomClearance,
                    onToggle = { viewModel.toggleChecklistItem(it) },
                    onAddClick = { showAddChecklistDialog = true },
                    onDelete = { viewModel.deleteChecklistItem(it) }
                )
                2 -> JournalTabContent(
                    journal = journal,
                    zones = zones,
                    bottomClearance = bottomClearance,
                    onAddJournalClick = { showAddJournalDialog = true },
                    onDeleteJournal = { viewModel.deleteJournalEntry(it) },
                    onOpenRecapPoster = { viewModel.openRecapSheet() }
                )
                else -> BackupTabContent(
                    bottomClearance = bottomClearance,
                    onOpenRecapPoster = { viewModel.openRecapSheet() },
                    onExportJson = {
                        val json = viewModel.exportBackupJson()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("HaGiangGo_Backup", json))
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_SUBJECT, "HaGiangGo_Backup.json")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Sao lưu dữ liệu chuyến đi"))
                    },
                    onOpenImportDialog = { showImportJsonDialog = true }
                )
            }
        }
    }

    // Dialog Thêm Chi Tiêu
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            durationDays = tripConfig.durationDays,
            members = members,
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { amount, category, title, day, paidById, splitIds ->
                viewModel.addExpense(amount, category, title, day, paidById, splitIds)
                showAddExpenseDialog = false
            }
        )
    }

    // Dialog Thêm Thành Viên Nhóm Phượt (Splitbill)
    if (showAddMemberDialog) {
        var memberName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = SurfaceWhite,
            title = {
                Text(
                    "Thêm bạn đồng hành",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                OutlinedTextField(
                    value = memberName,
                    onValueChange = { memberName = it },
                    label = { Text("Tên thành viên (VD: Hùng, Minh, Lan)", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricOrange,
                        unfocusedBorderColor = BorderMedium
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                ShadcnButton(
                    onClick = {
                        if (memberName.isNotBlank()) {
                            viewModel.addTripMember(memberName)
                            showAddMemberDialog = false
                        }
                    },
                    variant = ShadcnButtonVariant.Primary
                ) {
                    Text("Thêm vào đoàn")
                }
            },
            dismissButton = {
                ShadcnButton(onClick = { showAddMemberDialog = false }, variant = ShadcnButtonVariant.Ghost) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }

    // Dialog Thêm Checklist
    if (showAddChecklistDialog) {
        AddChecklistDialog(
            onDismiss = { showAddChecklistDialog = false },
            onConfirm = { group, title ->
                viewModel.addChecklistItem(group, title)
                showAddChecklistDialog = false
            }
        )
    }

    // Dialog Thêm Nhật Ký
    if (showAddJournalDialog) {
        AddJournalDialog(
            context = context,
            zones = zones,
            durationDays = tripConfig.durationDays,
            onDismiss = { showAddJournalDialog = false },
            onConfirm = { placeId, day, zoneId, content, photoPath ->
                viewModel.addJournalEntry(placeId, day, zoneId, content, photoPath)
                showAddJournalDialog = false
            }
        )
    }

    // Dialog Nhập JSON
    if (showImportJsonDialog) {
        var jsonText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = SurfaceWhite,
            title = {
                Text(
                    "Khôi phục dữ liệu từ JSON",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        "Dán chuỗi JSON đã sao lưu vào ô bên dưới:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = {
                            Text("{\"places\": [...], \"expenses\": [...]}", color = TextSubtle, fontSize = 12.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceMuted,
                            unfocusedContainerColor = SurfaceMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ElectricOrange,
                            unfocusedBorderColor = BorderSubtle
                        )
                    )
                }
            },
            confirmButton = {
                ShadcnButton(
                    onClick = {
                        if (jsonText.isNotBlank()) {
                            viewModel.importBackupJson(jsonText)
                            showImportJsonDialog = false
                        }
                    },
                    variant = ShadcnButtonVariant.Primary
                ) {
                    Text("Khôi phục ngay")
                }
            },
            dismissButton = {
                ShadcnButton(
                    onClick = { showImportJsonDialog = false },
                    variant = ShadcnButtonVariant.Ghost
                ) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun NotebookFloatingActionPill(
    label: String,
    icon: ImageVector,
    bottomClearance: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(end = 20.dp, bottom = bottomClearance - 4.dp)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = SoftShadowColor,
                spotColor = SoftShadowColor
            )
            .springClickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(ElectricOrange, ElectricOrangeHover)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
        }
    }
}

// -------------------------------------------------------------
// SUB-TAB 1: CHI TIÊU (Flighty Budget Bento + Multi-Segment Bar)
// -------------------------------------------------------------
@Composable
fun ExpenseTabContent(
    expenses: List<Expense>,
    tripConfig: TripConfig,
    members: List<TripMember>,
    memberBalances: List<MemberBalance>,
    settlementTransfers: List<SettlementTransfer>,
    bottomClearance: Dp,
    onAddExpenseClick: () -> Unit,
    onDeleteExpense: (Long) -> Unit,
    onAddMemberClick: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onCopySplitSummary: () -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")) }
    val membersById = remember(members) { members.associateBy { it.id } }
    val totalSpent = remember(expenses) { expenses.sumOf { it.amount } }
    val groupTotals = remember(expenses) {
        expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val budgetDenominator = remember(tripConfig.totalBudget, totalSpent) {
        maxOf(tripConfig.totalBudget, totalSpent, 1L).toFloat()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = bottomClearance + 64.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Flighty Budget Bento Card with RollingNumberText & Multi-Segment Bar
            item {
                val isOverBudget = totalSpent > tripConfig.totalBudget && tripConfig.totalBudget > 0
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = if (isOverBudget) RoseDestructiveBorder else BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "TỔNG ĐÃ CHI TIÊU",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                RollingNumberText(
                                    targetValue = totalSpent.toFloat(),
                                    useThousandSeparator = true,
                                    suffix = " đ",
                                    style = TelemetryTextStyle.copy(fontSize = 24.sp),
                                    color = if (isOverBudget) RoseDestructiveText else EmeraldGreenText
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "NGÂN SÁCH DỰ KIẾN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${currencyFormatter.format(tripConfig.totalBudget)} đ",
                                    style = TelemetryTextStyle.copy(fontSize = 15.sp),
                                    color = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Multi-Segment Animated Stacked Bar
                        val fuelFrac by animateFloatAsState(
                            targetValue = (groupTotals[ExpenseCategory.FUEL] ?: 0L) / budgetDenominator,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                            label = "FuelSegment"
                        )
                        val foodFrac by animateFloatAsState(
                            targetValue = (groupTotals[ExpenseCategory.FOOD] ?: 0L) / budgetDenominator,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                            label = "FoodSegment"
                        )
                        val stayFrac by animateFloatAsState(
                            targetValue = (groupTotals[ExpenseCategory.STAY] ?: 0L) / budgetDenominator,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                            label = "StaySegment"
                        )
                        val ticketFrac by animateFloatAsState(
                            targetValue = ((groupTotals[ExpenseCategory.TICKET] ?: 0L) + (groupTotals[ExpenseCategory.OTHER] ?: 0L)) / budgetDenominator,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                            label = "TicketSegment"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(SurfaceMuted),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (fuelFrac > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fuelFrac.coerceIn(0.02f, 1f))
                                        .clip(CircleShape)
                                        .background(AmberWarning)
                                )
                            }
                            if (foodFrac > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((foodFrac / (1f - fuelFrac).coerceAtLeast(0.05f)).coerceIn(0.02f, 1f))
                                        .clip(CircleShape)
                                        .background(ElectricOrange)
                                )
                            }
                            if (stayFrac > 0.005f) {
                                val rem = (1f - fuelFrac - foodFrac).coerceAtLeast(0.05f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((stayFrac / rem).coerceIn(0.02f, 1f))
                                        .clip(CircleShape)
                                        .background(SkyTelemetry)
                                )
                            }
                            if (ticketFrac > 0.005f) {
                                val rem = (1f - fuelFrac - foodFrac - stayFrac).coerceAtLeast(0.05f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((ticketFrac / rem).coerceIn(0.02f, 1f))
                                        .clip(CircleShape)
                                        .background(EmeraldGreen)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val remaining = tripConfig.totalBudget - totalSpent
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (remaining >= 0) "Còn dư: ${currencyFormatter.format(remaining)} đ"
                                else "Vượt ngân sách: ${currencyFormatter.format(-remaining)} đ",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (remaining >= 0) TextSecondary else RoseDestructiveText
                            )
                            val pct = if (tripConfig.totalBudget > 0) ((totalSpent * 100f) / tripConfig.totalBudget).roundToInt() else 0
                            ShadcnBadge(
                                text = "$pct% NGÂN SÁCH",
                                variant = if (isOverBudget) ShadcnBadgeVariant.Rose else ShadcnBadgeVariant.Emerald
                            )
                        }
                    }
                }
            }

            // 2. Category Breakdown Mini-Bento Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val catSpecs = listOf(
                        Triple(ExpenseCategory.FUEL, "Xăng xe", AmberWarning),
                        Triple(ExpenseCategory.FOOD, "Ăn uống", ElectricOrange),
                        Triple(ExpenseCategory.STAY, "Chỗ nghỉ", SkyTelemetry),
                        Triple(ExpenseCategory.TICKET, "Vé/Thuyền", EmeraldGreen)
                    )
                    catSpecs.forEach { (cat, shortLabel, dotColor) ->
                        val amount = groupTotals[cat] ?: 0L
                        ShadcnCard(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = SurfaceWhite,
                            borderColor = BorderSubtle
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${amount / 1000}k",
                                    style = TelemetryTextStyle.copy(fontSize = 14.sp),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 2B. Thẻ Thành viên Đoàn & Chia tiền Thông minh (Smart Group Splitbill)
            item(key = "group_splitbill_card") {
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = if (members.size >= 2) SkyTelemetryBorder else BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = SkyTelemetry,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = if (members.size >= 2) "CHIA TIỀN NHÓM (${members.size} NGƯỜI)"
                                    else "CHẾ ĐỘ ĐỘC HÀNH (SOLO)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(ElectricOrangeSoft)
                                    .border(BorderStroke(1.dp, ElectricOrangeBorder), CircleShape)
                                    .springClickable(onClick = onAddMemberClick)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "+ Thêm bạn",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricOrangeHover
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (members.size < 2) {
                            Text(
                                text = "Đi theo nhóm bạn bè? Chạm '+ Thêm bạn' để tự động tính ai ứng trước và chốt số lần chuyển khoản ít nhất cuối chuyến.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        } else {
                            // Danh sách thành viên & Cân đối ròng
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                memberBalances.forEach { mb ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceMuted)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (mb.member.isMe) "👤 ${mb.member.name} (Bạn)" else "👤 ${mb.member.name}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = TextPrimary
                                            )
                                            if (!mb.member.isMe) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Xóa",
                                                    tint = TextMuted,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { onRemoveMember(mb.member.id) }
                                                )
                                            }
                                        }
                                        val net = mb.netBalance
                                        val sign = if (net > 0) "+" else ""
                                        Text(
                                            text = "$sign${currencyFormatter.format(net)} đ",
                                            style = TelemetryTextStyle.copy(fontSize = 12.sp),
                                            color = when {
                                                net > 500L -> EmeraldGreenText
                                                net < -500L -> RoseDestructiveText
                                                else -> TextMuted
                                            }
                                        )
                                    }
                                }
                            }

                            if (settlementTransfers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(EmeraldGreenSoft.copy(alpha = 0.65f))
                                        .border(BorderStroke(1.dp, EmeraldGreenBorder), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "💸 CÁCH CHUYỂN KHOẢN GỌN NHẤT:",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = EmeraldGreenText
                                        )
                                        settlementTransfers.forEach { tr ->
                                            Text(
                                                text = "➡️ ${tr.fromMemberName} chuyển cho ${tr.toMemberName}: ${currencyFormatter.format(tr.amount)} đ",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            ShadcnButton(
                                onClick = onCopySplitSummary,
                                variant = ShadcnButtonVariant.Secondary,
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sao chép bảng chia tiền gửi Zalo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "LỊCH SỬ CHI TIÊU (${expenses.size})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (expenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có khoản chi nào.\nChạm '+ Khoản chi' góc dưới để ghi tiền xăng, ăn uống, homestay!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(expenses, key = { it.id }) { item ->
                    val (catIcon, catTint, catBg) = when (item.category) {
                        ExpenseCategory.FUEL -> Triple(Icons.Default.LocalGasStation, AmberWarningText, AmberWarningSoft)
                        ExpenseCategory.FOOD -> Triple(Icons.Default.Restaurant, ElectricOrangeHover, ElectricOrangeSoft)
                        ExpenseCategory.STAY -> Triple(Icons.Default.Hotel, SkyTelemetry, SkyTelemetrySoft)
                        ExpenseCategory.TICKET -> Triple(Icons.Default.ConfirmationNumber, EmeraldGreenText, EmeraldGreenSoft)
                        ExpenseCategory.OTHER -> Triple(Icons.Default.ReceiptLong, TextSecondary, SurfaceMuted)
                    }
                    ShadcnCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = BorderSubtle
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(catBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(catIcon, contentDescription = null, tint = catTint, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                val payerName = membersById[item.paidByMemberId]?.name ?: "Tôi"
                                val subtitleText = if (members.size >= 2) {
                                    val splitCount = if (item.splitAmongIds.isEmpty()) members.size else item.splitAmongIds.size
                                    "${item.category.label} • Ngày ${item.dayNumber} • Trả: $payerName (Chia $splitCount)"
                                } else {
                                    "${item.category.label} • Ngày ${item.dayNumber}"
                                }
                                Text(
                                    subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                "${currencyFormatter.format(item.amount)} đ",
                                style = TelemetryTextStyle.copy(fontSize = 14.sp),
                                color = ElectricOrangeHover
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceMuted)
                                    .springClickable { onDeleteExpense(item.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        NotebookFloatingActionPill(
            label = "Khoản chi",
            icon = Icons.Default.Add,
            bottomClearance = bottomClearance,
            onClick = onAddExpenseClick,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

// -------------------------------------------------------------
// SUB-TAB 2: CHECKLIST (Animated Readiness Ring + Tactile Check)
// -------------------------------------------------------------
@Composable
fun ChecklistTabContent(
    checklist: List<ChecklistItem>,
    bottomClearance: Dp,
    onToggle: (Long) -> Unit,
    onAddClick: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val checkedCount = remember(checklist) { checklist.count { it.isChecked } }
    val grouped = remember(checklist) { checklist.groupBy { it.groupName } }
    val targetProgress = if (checklist.isNotEmpty()) checkedCount.toFloat() / checklist.size else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 260f),
        label = "ChecklistRingProgress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = bottomClearance + 64.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Readiness Ring Card
            item {
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = EmeraldGreenBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "MỨC ĐỘ SẴN SÀNG LÊN ĐÈO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldGreenText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "$checkedCount / ${checklist.size} hành trang hoàn tất",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (checkedCount == checklist.size && checklist.isNotEmpty()) "Tuyệt vời! Bạn đã đủ đồ chinh phục Hà Giang!"
                                else "Kiểm tra kỹ giấy tờ xe, áo mưa bộ và thuốc cá nhân.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Custom Animated Ring Gauge
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val stroke = 5.5.dp.toPx()
                                val inset = stroke / 2f
                                val arcSize = Size(size.width - stroke, size.height - stroke)
                                drawArc(
                                    color = EmeraldGreenSoft,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = EmeraldGreen,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                text = "${(animatedProgress * 100).roundToInt()}%",
                                style = TelemetryTextStyle.copy(fontSize = 12.sp),
                                color = EmeraldGreenText
                            )
                        }
                    }
                }
            }

            grouped.forEach { (groupName, itemsInGroup) ->
                item(key = "group_$groupName") {
                    Text(
                        text = groupName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricOrangeHover,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp)
                    )
                }

                items(itemsInGroup, key = { it.id }) { item ->
                    val checkScale by animateFloatAsState(
                        targetValue = if (item.isChecked) 1.12f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
                        label = "CheckItemBounce"
                    )
                    val cardBg by animateColorAsState(
                        targetValue = if (item.isChecked) SurfaceMuted.copy(alpha = 0.65f) else SurfaceWhite,
                        animationSpec = tween(180),
                        label = "CheckCardBg"
                    )

                    ShadcnCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = tween(200),
                                fadeOutSpec = tween(150),
                                placementSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                            ),
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = cardBg,
                        borderColor = if (item.isChecked) EmeraldGreenBorder else BorderSubtle,
                        onClick = { onToggle(item.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = checkScale
                                        scaleY = checkScale
                                    }
                                    .clip(CircleShape)
                                    .background(if (item.isChecked) EmeraldGreen else SurfaceWhite)
                                    .border(
                                        BorderStroke(1.5.dp, if (item.isChecked) EmeraldGreen else BorderMedium),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.isChecked) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.Medium,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                                ),
                                color = if (item.isChecked) TextMuted else TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .springClickable { onDelete(item.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextSubtle, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }

        NotebookFloatingActionPill(
            label = "Món đồ",
            icon = Icons.Default.Add,
            bottomClearance = bottomClearance,
            onClick = onAddClick,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

// -------------------------------------------------------------
// SUB-TAB 3: NHẬT KÝ (Polarsteps Editorial Cards)
// -------------------------------------------------------------
@Composable
fun JournalTabContent(
    journal: List<JournalEntry>,
    zones: List<Zone>,
    bottomClearance: Dp,
    onAddJournalClick: () -> Unit,
    onDeleteJournal: (Long) -> Unit,
    onOpenRecapPoster: () -> Unit = {}
) {
    val zonesById = remember(zones) { zones.associateBy { it.id } }
    val dateFormatter = remember { SimpleDateFormat("HH:mm • dd/MM", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = bottomClearance + 64.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "journal_poster_banner") {
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = ElectricOrangeSoft,
                    borderColor = ElectricOrangeBorder,
                    onClick = onOpenRecapPoster
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ElectricOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesomeMotion, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "TẠO POSTER TỔNG KẾT CHUYẾN ĐI",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                    color = ElectricOrangeHover
                                )
                                Text(
                                    text = "Bản đồ vòng cung + Cột mốc đã ghé + Đỉnh cao độ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ElectricOrangeHover)
                    }
                }
            }

            item {
                Text(
                    text = "NHẬT KÝ HÀNH TRÌNH (${journal.size} ghi chép)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted
                )
            }

            if (journal.isEmpty()) {
                item {
                    ShadcnCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = BorderSubtle
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ElectricOrangeSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoStories, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Lưu giữ khoảnh khắc trên đèo",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ghi lại cảm xúc săn mây, quán phở ngon hay ảnh kỷ niệm ở mỗi chặng hoàn toàn offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(journal, key = { it.id }) { entry ->
                    ShadcnCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                            ),
                        shape = RoundedCornerShape(18.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = BorderSubtle
                    ) {
                        Column(modifier = Modifier.padding(15.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ShadcnBadge(text = "NGÀY ${entry.dayNumber}", variant = ShadcnBadgeVariant.Orange)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = zonesById[entry.zoneId]?.name ?: "Hà Giang",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateFormatter.format(Date(entry.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(RoseDestructiveSoft)
                                            .springClickable { onDeleteJournal(entry.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Xóa",
                                            tint = RoseDestructiveText,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = entry.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )

                            if (!entry.photoPath.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SurfaceMuted)
                                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Photo, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ảnh đính kèm: ${File(entry.photoPath).name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        NotebookFloatingActionPill(
            label = "Nhật ký",
            icon = Icons.Default.Edit,
            bottomClearance = bottomClearance,
            onClick = onAddJournalClick,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

// -------------------------------------------------------------
// SUB-TAB 4: SAO LƯU
// -------------------------------------------------------------
@Composable
fun BackupTabContent(
    bottomClearance: Dp,
    onOpenRecapPoster: () -> Unit = {},
    onExportJson: () -> Unit,
    onOpenImportDialog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, bottomClearance + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ShadcnButton(
                onClick = onOpenRecapPoster,
                variant = ShadcnButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.AutoAwesomeMotion, contentDescription = null, tint = ElectricOrangeHover, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Xuất Poster Ảnh Tổng kết Hành trình (PNG)", fontWeight = FontWeight.Bold, color = ElectricOrangeHover)
            }
        }

        item {
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = SkyTelemetryBorder
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SkyTelemetrySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = SkyTelemetry, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "KÉT SẮT OFFLINE 100%",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                "Không phụ thuộc sóng 4G trên đèo",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyTelemetry
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Toàn bộ lịch trình, tọa độ địa điểm tự thêm, chi tiêu và nhật ký được lưu an toàn ngay trên máy. Bạn có thể xuất chuỗi JSON để chia sẻ lịch trình cho bạn đồng hành hoặc sang máy mới.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        item {
            ShadcnButton(
                onClick = onExportJson,
                variant = ShadcnButtonVariant.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sao lưu & Chia sẻ file JSON chuyến đi", fontWeight = FontWeight.Bold)
            }
        }

        item {
            ShadcnButton(
                onClick = onOpenImportDialog,
                variant = ShadcnButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Khôi phục dữ liệu từ chuỗi JSON", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// -------------------------------------------------------------
// DIALOGS
// -------------------------------------------------------------
@Composable
fun AddExpenseDialog(
    durationDays: Int,
    members: List<TripMember> = listOf(TripMember("me", "Tôi", isMe = true)),
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, category: ExpenseCategory, title: String, day: Int, paidByMemberId: String, splitAmongIds: List<String>) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var selectedDay by remember { mutableIntStateOf(1) }
    var paidByMemberId by remember { mutableStateOf(members.firstOrNull()?.id ?: "me") }
    var splitMemberIds by remember(members) { mutableStateOf(members.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = SurfaceWhite,
        title = {
            Text(
                "Thêm khoản chi tiêu",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Số tiền (VNĐ)", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricOrange,
                        unfocusedBorderColor = BorderMedium
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Mục chi tiêu (VD: Đổ xăng, Bánh cuốn)", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricOrange,
                        unfocusedBorderColor = BorderMedium
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Danh mục:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ExpenseCategory.FUEL, ExpenseCategory.FOOD, ExpenseCategory.STAY, ExpenseCategory.TICKET).forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ElectricOrangeSoft else SurfaceMuted)
                                .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(10.dp))
                                .springClickable { selectedCategory = cat }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(cat.label, fontSize = 11.sp, color = if (isSelected) ElectricOrangeHover else TextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Text("Ngày thứ:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..durationDays).forEach { day ->
                        val isSelected = selectedDay == day
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ElectricOrangeSoft else SurfaceMuted)
                                .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(10.dp))
                                .springClickable { selectedDay = day }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("N$day", fontSize = 11.sp, color = if (isSelected) ElectricOrangeHover else TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (members.size >= 2) {
                    Text("Ai trả tiền khoản này?", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        members.forEach { m ->
                            val isPayer = paidByMemberId == m.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isPayer) EmeraldGreenSoft else SurfaceMuted)
                                    .border(BorderStroke(1.dp, if (isPayer) EmeraldGreenBorder else BorderSubtle), RoundedCornerShape(10.dp))
                                    .springClickable { paidByMemberId = m.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = m.name,
                                    fontSize = 11.sp,
                                    color = if (isPayer) EmeraldGreenText else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text("Chia đều cho ai?", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        members.forEach { m ->
                            val isIncluded = m.id in splitMemberIds
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isIncluded) SkyTelemetrySoft else SurfaceMuted)
                                    .border(BorderStroke(1.dp, if (isIncluded) SkyTelemetryBorder else BorderSubtle), RoundedCornerShape(10.dp))
                                    .springClickable {
                                        val next = if (isIncluded && splitMemberIds.size > 1) {
                                            splitMemberIds - m.id
                                        } else {
                                            splitMemberIds + m.id
                                        }
                                        splitMemberIds = next
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isIncluded) "✓ ${m.name}" else m.name,
                                    fontSize = 11.sp,
                                    color = if (isIncluded) SkyTelemetry else TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            ShadcnButton(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (amount > 0 && title.isNotBlank()) {
                        onConfirm(
                            amount,
                            selectedCategory,
                            title.trim(),
                            selectedDay,
                            paidByMemberId,
                            splitMemberIds.toList()
                        )
                    }
                },
                variant = ShadcnButtonVariant.Primary
            ) {
                Text("Lưu khoản chi")
            }
        },
        dismissButton = {
            ShadcnButton(onClick = onDismiss, variant = ShadcnButtonVariant.Ghost) {
                Text("Hủy", color = TextSecondary)
            }
        }
    )
}

@Composable
fun AddChecklistDialog(
    onDismiss: () -> Unit,
    onConfirm: (group: String, title: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Đồ bảo hộ lái xe") }

    val defaultGroups = listOf("Giấy tờ & Tiền", "Đồ bảo hộ lái xe", "Y tế & Thuốc men", "Thiết bị điện tử", "Quần áo ấm")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = SurfaceWhite,
        title = {
            Text(
                "Thêm đồ cần mang",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên món đồ (VD: Bộ vá xe không săm)", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricOrange,
                        unfocusedBorderColor = BorderMedium
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Nhóm phân loại:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Column {
                    defaultGroups.forEach { grp ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { group = grp }
                                .padding(vertical = 3.dp)
                        ) {
                            RadioButton(
                                selected = group == grp,
                                onClick = { group = grp },
                                colors = RadioButtonDefaults.colors(selectedColor = ElectricOrange, unselectedColor = BorderMedium)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(grp, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            ShadcnButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(group, title.trim())
                    }
                },
                variant = ShadcnButtonVariant.Primary
            ) {
                Text("Thêm món đồ")
            }
        },
        dismissButton = {
            ShadcnButton(onClick = onDismiss, variant = ShadcnButtonVariant.Ghost) {
                Text("Hủy", color = TextSecondary)
            }
        }
    )
}

@Composable
fun AddJournalDialog(
    context: Context,
    zones: List<Zone>,
    durationDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (placeId: String?, day: Int, zoneId: String, content: String, photoPath: String?) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedDay by remember { mutableIntStateOf(1) }
    var selectedZoneId by remember { mutableStateOf(zones.firstOrNull()?.id ?: "dong_van_lung_cu") }
    var savedPhotoPath by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val destFile = File(context.filesDir, "journal_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(destFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                savedPhotoPath = destFile.absolutePath
            } catch (_: Exception) {
                savedPhotoPath = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = SurfaceWhite,
        title = {
            Text(
                "Viết nhật ký chuyến đi",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Ghi chú, cảm xúc, trải nghiệm...", color = TextSecondary) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricOrange,
                        unfocusedBorderColor = BorderMedium
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ngày:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    (1..durationDays).forEach { day ->
                        val isSelected = selectedDay == day
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ElectricOrangeSoft else SurfaceMuted)
                                .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(10.dp))
                                .springClickable { selectedDay = day }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("N$day", fontSize = 11.sp, color = if (isSelected) ElectricOrangeHover else TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ShadcnButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    variant = ShadcnButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (savedPhotoPath != null) "Đã chọn 1 ảnh (Đổi ảnh)" else "Đính kèm ảnh kỷ niệm", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            ShadcnButton(
                onClick = {
                    if (content.isNotBlank()) {
                        onConfirm(null, selectedDay, selectedZoneId, content.trim(), savedPhotoPath)
                    }
                },
                variant = ShadcnButtonVariant.Primary
            ) {
                Text("Lưu nhật ký")
            }
        },
        dismissButton = {
            ShadcnButton(onClick = onDismiss, variant = ShadcnButtonVariant.Ghost) {
                Text("Hủy", color = TextSecondary)
            }
        }
    )
}
