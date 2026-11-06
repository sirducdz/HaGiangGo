package com.reed.hagianggo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sub-Tab Switcher
            PrimaryTabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = SunsetOrange
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Chi tiêu", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Checklist", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("Nhật ký", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 3,
                    onClick = { selectedSubTab = 3 },
                    text = { Text("Sao lưu", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
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
            title = { Text("Nhập dữ liệu JSON") },
            text = {
                Column {
                    Text(
                        "Dán chuỗi JSON đã sao lưu để khôi phục danh sách địa điểm, nhật ký, chi tiêu và checklist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        placeholder = { Text("{\n  \"trip_config\": ...\n}") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (jsonText.isNotBlank()) {
                            viewModel.importBackupJson(jsonText)
                            showImportJsonDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
                ) {
                    Text("Khôi phục")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportJsonDialog = false }) {
                    Text("Hủy")
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExpenseClick,
                containerColor = SunsetOrange,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm khoản chi")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Budget Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "TỔNG ĐÃ CHI",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "${currencyFormatter.format(totalSpent)} đ",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                    color = if (totalSpent > tripConfig.totalBudget && tripConfig.totalBudget > 0) AlertRed else HighlandGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "NGÂN SÁCH",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "${currencyFormatter.format(tripConfig.totalBudget)} đ",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val progress = if (tripConfig.totalBudget > 0) (totalSpent.toFloat() / tripConfig.totalBudget).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (progress >= 1f) AlertRed else SunsetOrange
                        )
                    }
                }
            }

            // Category breakdown pills
            item {
                val groupTotals = remember(expenses) {
                    expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(ExpenseCategory.FUEL, ExpenseCategory.FOOD, ExpenseCategory.STAY, ExpenseCategory.TICKET).forEach { cat ->
                        val amount = groupTotals[cat] ?: 0L
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cat.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                Text(
                                    "${amount / 1000}k",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = SunsetOrange
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "LỊCH SỬ CHI TIÊU (${expenses.size})",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (expenses.isEmpty()) {
                item {
                    Text(
                        text = "Chưa có khoản chi nào.\nBấm nút + bên dưới để ghi nhận tiền xăng, ăn uống, homestay!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(expenses, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "${item.category.label} • Ngày ${item.dayNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                "${currencyFormatter.format(item.amount)} đ",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = SunsetOrange
                            )
                            IconButton(onClick = { onDeleteExpense(item.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = SunsetOrange,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm đồ cần mang")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Progress
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = HighlandGreen.copy(alpha = 0.1f))
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
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = HighlandGreen
                            )
                            Text(
                                "$checkedCount / ${checklist.size} món đồ đã chuẩn bị",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        CircularProgressIndicator(
                            progress = { if (checklist.isNotEmpty()) checkedCount.toFloat() / checklist.size else 0f },
                            modifier = Modifier.size(36.dp),
                            color = HighlandGreen,
                            strokeWidth = 4.dp
                        )
                    }
                }
            }

            grouped.forEach { (groupName, itemsInGroup) ->
                item(key = "group_$groupName") {
                    Text(
                        text = groupName.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = SunsetOrange,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(itemsInGroup, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(item.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isChecked,
                                onCheckedChange = { onToggle(item.id) },
                                colors = CheckboxDefaults.colors(checkedColor = HighlandGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddJournalClick,
                containerColor = SunsetOrange,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm nhật ký")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "NHẬT KÝ CHUYẾN ĐI (${journal.size} mục)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (journal.isEmpty()) {
                item {
                    Text(
                        text = "Chưa có ghi chép nhật ký nào.\nLưu lại cảm xúc, kinh nghiệm đổ đèo hoặc ảnh kỷ niệm tại mỗi chặng!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(journal, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = SunsetOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Ngày ${entry.dayNumber} • ${zonesById[entry.zoneId]?.name ?: "Hà Giang"}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateFormatter.format(Date(entry.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = { onDeleteJournal(entry.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = entry.content, style = MaterialTheme.typography.bodyMedium)

                            if (!entry.photoPath.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Photo, contentDescription = null, tint = SunsetOrange, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Đã đính kèm ảnh: ${File(entry.photoPath).name}",
                                            style = MaterialTheme.typography.bodySmall,
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
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateBlue.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = SlateBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("100% OFFLINE & AN TOÀN", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Dữ liệu chuyến đi, địa điểm và nhật ký được lưu hoàn toàn trên điện thoại của bạn, không gửi đi bất kỳ máy chủ nào. Bạn có thể sao lưu ra chuỗi JSON để lưu trữ hoặc gửi sang máy khác.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Button(
                onClick = onExportJson,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sao lưu / Xuất file JSON chuyến đi", fontWeight = FontWeight.Bold)
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenImportDialog,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null)
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
        title = { Text("Thêm khoản chi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Số tiền (VNĐ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Mục chi tiêu (VD: Đổ xăng 95, Bánh cuốn trứng)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Danh mục:", style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(ExpenseCategory.FUEL, ExpenseCategory.FOOD, ExpenseCategory.STAY, ExpenseCategory.TICKET).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Ngày thứ:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..durationDays).forEach { day ->
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text("N$day") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (amount > 0 && title.isNotBlank()) {
                        onConfirm(amount, selectedCategory, title.trim(), selectedDay)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
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
        title = { Text("Thêm đồ cần mang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên món đồ (VD: Bộ vá xe không săm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Nhóm:", style = MaterialTheme.typography.bodySmall)
                Column {
                    defaultGroups.forEach { grp ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { group = grp }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = group == grp, onClick = { group = grp })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(grp, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(group, title.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
            ) {
                Text("Thêm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
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
        title = { Text("Viết nhật ký chuyến đi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Ghi chú, cảm xúc, trải nghiệm...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ngày:", style = MaterialTheme.typography.bodySmall)
                    (1..durationDays).forEach { day ->
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text("N$day") }
                        )
                    }
                }

                Button(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (savedPhotoPath != null) "Đã chọn 1 ảnh (Đổi ảnh)" else "Đính kèm ảnh")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        onConfirm(null, selectedDay, selectedZoneId, content.trim(), savedPhotoPath)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
            ) {
                Text("Lưu nhật ký")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
