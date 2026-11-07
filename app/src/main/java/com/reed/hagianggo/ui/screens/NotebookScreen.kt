package com.reed.hagianggo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.*
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
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
import com.reed.hagianggo.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Chi tiêu, 1: Checklist, 2: Nhật ký, 3: Sao lưu

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddChecklistDialog by remember { mutableStateOf(false) }
    var showAddJournalDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sub-Tab Switcher phong cách shadcn light segmented
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            when (selectedSubTab) {
                0 -> ExpenseTabContent(
                    expenses = expenses,
                    tripConfig = tripConfig,
                    onAddExpenseClick = { showAddExpenseDialog = true },
                    onDeleteExpense = { viewModel.deleteExpense(it) }
                )
                1 -> ChecklistTabContent(
                    checklist = checklist,
                    onToggle = { viewModel.toggleChecklistItem(it) },
                    onAddClick = { showAddChecklistDialog = true },
                    onDelete = { viewModel.deleteChecklistItem(it) }
                )
                2 -> JournalTabContent(
                    journal = journal,
                    zones = zones,
                    onAddJournalClick = { showAddJournalDialog = true },
                    onDeleteJournal = { viewModel.deleteJournalEntry(it) }
                )
                3 -> BackupTabContent(
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
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { amount, category, title, day ->
                viewModel.addExpense(amount, category, title, day)
                showAddExpenseDialog = false
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
            containerColor = SurfaceWhite,
            title = { Text("Khôi phục dữ liệu từ JSON", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column {
                    Text("Dán chuỗi JSON đã sao lưu vào ô bên dưới:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        placeholder = { Text("{\"places\": [...], \"expenses\": [...]}", color = TextSubtle, fontSize = 12.sp) },
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

// -------------------------------------------------------------
// SUB-TAB 1: CHI TIÊU
// -------------------------------------------------------------
@Composable
fun ExpenseTabContent(
    expenses: List<Expense>,
    tripConfig: TripConfig,
    onAddExpenseClick: () -> Unit,
    onDeleteExpense: (Long) -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale("vi", "VN")) }
    val totalSpent = remember(expenses) { expenses.sumOf { it.amount } }

    Scaffold(
        containerColor = AppBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExpenseClick,
                containerColor = ElectricOrange,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm khoản chi")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Budget Summary Card
            item {
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "TỔNG ĐÃ CHI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = "${currencyFormatter.format(totalSpent)} đ",
                                    style = TelemetryTextStyle.copy(fontSize = 22.sp),
                                    color = if (totalSpent > tripConfig.totalBudget && tripConfig.totalBudget > 0) RoseDestructiveText else EmeraldGreenText
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "NGÂN SÁCH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = "${currencyFormatter.format(tripConfig.totalBudget)} đ",
                                    style = TelemetryTextStyle.copy(fontSize = 16.sp),
                                    color = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val progress = if (tripConfig.totalBudget > 0) (totalSpent.toFloat() / tripConfig.totalBudget).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (progress >= 1f) RoseDestructive else ElectricOrange,
                            trackColor = SurfaceMuted
                        )
                    }
                }
            }

            // Category breakdown cards
            item {
                val groupTotals = remember(expenses) {
                    expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(ExpenseCategory.FUEL, ExpenseCategory.FOOD, ExpenseCategory.STAY, ExpenseCategory.TICKET).forEach { cat ->
                        val amount = groupTotals[cat] ?: 0L
                        ShadcnCard(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = SurfaceWhite,
                            borderColor = BorderSubtle
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cat.label, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1)
                                Text(
                                    "${amount / 1000}k",
                                    style = TelemetryTextStyle.copy(fontSize = 13.sp),
                                    color = ElectricOrangeHover
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LỊCH SỬ CHI TIÊU (${expenses.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            if (expenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có khoản chi nào.\nBấm nút + để ghi nhận tiền xăng, ăn uống, homestay!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(expenses, key = { it.id }) { item ->
                    ShadcnCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = BorderSubtle
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                                Text(
                                    "${item.category.label} • Ngày ${item.dayNumber}",
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
                            IconButton(onClick = { onDeleteExpense(item.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextSubtle, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SUB-TAB 2: CHECKLIST
// -------------------------------------------------------------
@Composable
fun ChecklistTabContent(
    checklist: List<ChecklistItem>,
    onToggle: (Long) -> Unit,
    onAddClick: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val checkedCount = remember(checklist) { checklist.count { it.isChecked } }
    val grouped = remember(checklist) { checklist.groupBy { it.groupName } }

    Scaffold(
        containerColor = AppBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = ElectricOrange,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm đồ cần mang")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Progress Card
            item {
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = EmeraldGreenBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "CHUẨN BỊ HÀNH TRANG",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldGreenText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "$checkedCount / ${checklist.size} món đồ đã chuẩn bị",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                        CircularProgressIndicator(
                            progress = { if (checklist.isNotEmpty()) checkedCount.toFloat() / checklist.size else 0f },
                            modifier = Modifier.size(32.dp),
                            color = EmeraldGreen,
                            trackColor = SurfaceMuted,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            grouped.forEach { (groupName, itemsInGroup) ->
                item(key = "group_$groupName") {
                    Text(
                        text = groupName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricOrangeHover,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                items(itemsInGroup, key = { it.id }) { item ->
                    ShadcnCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        backgroundColor = if (item.isChecked) SurfaceMuted.copy(alpha = 0.5f) else SurfaceWhite,
                        borderColor = BorderSubtle
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(item.id) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isChecked,
                                onCheckedChange = { onToggle(item.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = EmeraldGreen,
                                    uncheckedColor = BorderMedium,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                ),
                                color = if (item.isChecked) TextMuted else TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextSubtle, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SUB-TAB 3: NHẬT KÝ
// -------------------------------------------------------------
@Composable
fun JournalTabContent(
    journal: List<JournalEntry>,
    zones: List<Zone>,
    onAddJournalClick: () -> Unit,
    onDeleteJournal: (Long) -> Unit
) {
    val zonesById = remember(zones) { zones.associateBy { it.id } }
    val dateFormatter = remember { SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()) }

    Scaffold(
        containerColor = AppBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddJournalClick,
                containerColor = ElectricOrange,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm nhật ký")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "NHẬT KÝ CHUYẾN ĐI (${journal.size} mục)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            if (journal.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có ghi chép nhật ký nào.\nLưu lại cảm xúc, kinh nghiệm đổ đèo hoặc ảnh kỷ niệm tại mỗi chặng!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(journal, key = { it.id }) { entry ->
                    ShadcnCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = BorderSubtle
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Ngày ${entry.dayNumber} • ${zonesById[entry.zoneId]?.name ?: "Hà Giang"}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextPrimary
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateFormatter.format(Date(entry.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = { onDeleteJournal(entry.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa", tint = RoseDestructiveText, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = entry.content, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

                            if (!entry.photoPath.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceMuted)
                                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Photo, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ảnh: ${File(entry.photoPath).name}",
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
    }
}

// -------------------------------------------------------------
// SUB-TAB 4: SAO LƯU
// -------------------------------------------------------------
@Composable
fun BackupTabContent(
    onExportJson: () -> Unit,
    onOpenImportDialog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = SurfaceWhite,
                borderColor = SkyTelemetryBorder
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = SkyTelemetry, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("100% OFFLINE & AN TOÀN", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Dữ liệu chuyến đi, địa điểm và nhật ký được lưu hoàn toàn trên điện thoại của bạn, không gửi đi bất kỳ máy chủ nào. Bạn có thể sao lưu ra chuỗi JSON để lưu trữ hoặc gửi sang máy khác.",
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
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sao lưu / Xuất file JSON chuyến đi", fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            ShadcnButton(
                onClick = onOpenImportDialog,
                variant = ShadcnButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Khôi phục dữ liệu từ JSON", fontWeight = FontWeight.SemiBold)
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
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, category: ExpenseCategory, title: String, day: Int) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var selectedDay by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Thêm khoản chi tiêu", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Số tiền (VNĐ)", color = TextSecondary) },
                    singleLine = true,
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(ExpenseCategory.FUEL, ExpenseCategory.FOOD, ExpenseCategory.STAY, ExpenseCategory.TICKET).forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ElectricOrangeSoft else SurfaceMuted)
                                .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(6.dp))
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ElectricOrangeSoft else SurfaceMuted)
                                .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(6.dp))
                                .clickable { selectedDay = day }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("N$day", fontSize = 11.sp, color = if (isSelected) ElectricOrangeHover else TextSecondary, fontWeight = FontWeight.SemiBold)
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
                        onConfirm(amount, selectedCategory, title.trim(), selectedDay)
                    }
                },
                variant = ShadcnButtonVariant.Primary
            ) {
                Text("Lưu khoản chi")
            }
        },
        dismissButton = {
            ShadcnButton(onClick = onDismiss, variant = ShadcnButtonVariant.Ghost) { Text("Hủy", color = TextSecondary) }
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
        containerColor = SurfaceWhite,
        title = { Text("Thêm đồ cần mang", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên món đồ (VD: Bộ vá xe không săm)", color = TextSecondary) },
                    singleLine = true,
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
            ShadcnButton(onClick = onDismiss, variant = ShadcnButtonVariant.Ghost) { Text("Hủy", color = TextSecondary) }
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
    var selectedZoneId by remember { mutableStateOf(zones.firstOrNull()?.id ?: "dong_van") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var savedPhotoPath by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        photoUri = uri
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
        containerColor = SurfaceWhite,
        title = { Text("Viết nhật ký chuyến đi", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Ghi chú, cảm xúc, trải nghiệm...", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricOrange,
                        unfocusedBorderColor = BorderMedium
                    ),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ngày:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    (1..durationDays).forEach { day ->
                        val isSelected = selectedDay == day
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ElectricOrangeSoft else SurfaceMuted)
                                .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(6.dp))
                                .clickable { selectedDay = day }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("N$day", fontSize = 11.sp, color = if (isSelected) ElectricOrangeHover else TextSecondary, fontWeight = FontWeight.SemiBold)
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
                    Text(if (savedPhotoPath != null) "Đã chọn 1 ảnh (Đổi ảnh)" else "Đính kèm ảnh", fontSize = 12.sp)
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
            ShadcnButton(onClick = onDismiss, variant = ShadcnButtonVariant.Ghost) { Text("Hủy", color = TextSecondary) }
        }
    )
}
