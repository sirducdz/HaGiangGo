package com.reed.hagianggo.domain

import com.reed.hagianggo.model.Expense
import com.reed.hagianggo.model.MemberBalance
import com.reed.hagianggo.model.SettlementTransfer
import com.reed.hagianggo.model.TripMember
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

/**
 * Thuật toán Chia tiền nhóm phượt tối giản số lần chuyển khoản (Greedy Min-Cash-Flow Splitbill).
 */
object SplitbillEngine {

    fun calculateBalances(
        members: List<TripMember>,
        expenses: List<Expense>
    ): List<MemberBalance> {
        if (members.isEmpty()) return emptyList()
        val memberIds = members.map { it.id }.toSet()

        val paidMap = mutableMapOf<String, Long>().apply {
            members.forEach { put(it.id, 0L) }
        }
        val owedMap = mutableMapOf<String, Long>().apply {
            members.forEach { put(it.id, 0L) }
        }

        for (exp in expenses) {
            val payerId = if (exp.paidByMemberId in memberIds) exp.paidByMemberId else members.first().id
            paidMap[payerId] = (paidMap[payerId] ?: 0L) + exp.amount

            val validSplitIds = exp.splitAmongIds.filter { it in memberIds }
                .ifEmpty { members.map { it.id } }

            val count = validSplitIds.size.coerceAtLeast(1)
            val shareEach = exp.amount / count
            val remainder = exp.amount % count

            validSplitIds.forEachIndexed { idx, mId ->
                val extra = if (idx == 0) remainder else 0L
                owedMap[mId] = (owedMap[mId] ?: 0L) + shareEach + extra
            }
        }

        return members.map { m ->
            val paid = paidMap[m.id] ?: 0L
            val owed = owedMap[m.id] ?: 0L
            MemberBalance(
                member = m,
                totalPaid = paid,
                totalOwed = owed,
                netBalance = paid - owed
            )
        }
    }

    fun computeSettlements(balances: List<MemberBalance>): List<SettlementTransfer> {
        if (balances.size < 2) return emptyList()

        data class MutableNode(val name: String, var amount: Long)

        // Người âm tiền (cần trả thêm) & người dương tiền (đã ứng trước, cần nhận lại)
        val debtors = balances.filter { it.netBalance < -500L }
            .map { MutableNode(it.member.name, -it.netBalance) }
            .sortedByDescending { it.amount }
            .toMutableList()

        val creditors = balances.filter { it.netBalance > 500L }
            .map { MutableNode(it.member.name, it.netBalance) }
            .sortedByDescending { it.amount }
            .toMutableList()

        val transfers = mutableListOf<SettlementTransfer>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val d = debtors[i]
            val c = creditors[j]
            val settleAmt = min(d.amount, c.amount)
            if (settleAmt >= 1000L) {
                transfers.add(
                    SettlementTransfer(
                        fromMemberName = d.name,
                        toMemberName = c.name,
                        amount = settleAmt
                    )
                )
            }
            d.amount -= settleAmt
            c.amount -= settleAmt
            if (d.amount < 500L) i++
            if (c.amount < 500L) j++
        }
        return transfers
    }

    fun formatShareableSummary(
        expenses: List<Expense>,
        balances: List<MemberBalance>,
        transfers: List<SettlementTransfer>
    ): String {
        val fmt = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
        val total = expenses.sumOf { it.amount }
        val sb = StringBuilder()
        sb.appendLine("🏔️ TỔNG KẾT CHI PHÍ HÀ GIANG GO")
        sb.appendLine("• Tổng chi cả đoàn: ${fmt.format(total)} đ (${expenses.size} khoản)")
        sb.appendLine("• Số thành viên: ${balances.size} người")
        sb.appendLine("────────────────────")
        for (b in balances) {
            val sign = if (b.netBalance >= 0) "+" else ""
            sb.appendLine("👤 ${b.member.name}: Đã chi ${fmt.format(b.totalPaid)}đ | Phần mình ${fmt.format(b.totalOwed)}đ (${sign}${fmt.format(b.netBalance)}đ)")
        }
        if (transfers.isNotEmpty()) {
            sb.appendLine("────────────────────")
            sb.appendLine("💸 CHỐT CHUYỂN KHOẢN GỌN NHẤT:")
            for (t in transfers) {
                sb.appendLine("➡️ ${t.fromMemberName} chuyển cho ${t.toMemberName}: ${fmt.format(t.amount)} đ")
            }
        } else {
            sb.appendLine("✅ Mọi thành viên đã cân bằng thu chi!")
        }
        return sb.toString().trim()
    }
}
