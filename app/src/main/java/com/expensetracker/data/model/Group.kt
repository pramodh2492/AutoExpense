package com.expensetracker.data.model

data class GroupMember(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val joinedAt: Long = 0L
)

data class GroupExpense(
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paidByUid: String = "",
    val paidByName: String = "",
    val category: String = "other",
    val timestamp: Long = 0L,
    val splitAmong: List<GroupExpenseSplit> = emptyList()
)

data class GroupExpenseSplit(
    val uid: String = "",
    val displayName: String = "",
    val share: Double = 0.0,
    val settled: Boolean = false
)

data class ExpenseGroup(
    val code: String = "",
    val name: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val createdAt: Long = 0L,
    val isClosed: Boolean = false,
    val members: List<GroupMember> = emptyList(),
    val expenses: List<GroupExpense> = emptyList()
) {
    fun settlementSummary(currentUid: String): List<Settlement> {
        val balances = mutableMapOf<String, Double>()
        expenses.forEach { expense ->
            expense.splitAmong.forEach { split ->
                if (!split.settled) {
                    if (split.uid == currentUid && expense.paidByUid != currentUid) {
                        // I owe paidBy
                        balances[expense.paidByUid] =
                            (balances[expense.paidByUid] ?: 0.0) - split.share
                    } else if (split.uid != currentUid && expense.paidByUid == currentUid) {
                        // split.uid owes me
                        balances[split.uid] =
                            (balances[split.uid] ?: 0.0) + split.share
                    }
                }
            }
        }
        return balances.map { (uid, amount) ->
            val name = members.find { it.uid == uid }?.displayName ?: uid
            Settlement(uid = uid, displayName = name, amount = amount)
        }.filter { it.amount != 0.0 }
    }
}

// Positive amount = they owe you; negative = you owe them
data class Settlement(
    val uid: String,
    val displayName: String,
    val amount: Double
)
