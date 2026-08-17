package com.expensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.data.model.ExpenseGroup
import com.expensetracker.data.model.GroupExpense
import com.expensetracker.data.model.GroupExpenseSplit
import com.expensetracker.data.repository.GroupRepository
import com.expensetracker.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GroupUiState {
    data object Idle : GroupUiState()
    data object Loading : GroupUiState()
    data class Success(val message: String) : GroupUiState()
    data class Error(val message: String) : GroupUiState()
}

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val repository: GroupRepository,
    private val auth: FirebaseAuth,
    private val userPreferences: UserPreferences,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _myGroups = MutableStateFlow<List<ExpenseGroup>>(emptyList())
    val myGroups: StateFlow<List<ExpenseGroup>> = _myGroups.asStateFlow()

    // Holds a pending expense to be added after group creation/selection from Txns page
    var pendingExpenseDescription: String = ""
    var pendingExpenseAmount: Double = 0.0

    fun setPendingExpense(description: String, amount: Double) {
        pendingExpenseDescription = description
        pendingExpenseAmount = amount
    }

    fun clearPendingExpense() {
        pendingExpenseDescription = ""
        pendingExpenseAmount = 0.0
    }

    private val _activeGroup = MutableStateFlow<ExpenseGroup?>(null)
    val activeGroup: StateFlow<ExpenseGroup?> = _activeGroup.asStateFlow()

    private val _uiState = MutableStateFlow<GroupUiState>(GroupUiState.Idle)
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    val currentUid: String? get() = auth.currentUser?.uid
    val currentName: String get() = auth.currentUser?.displayName ?: "Me"
    val isSignedIn: Boolean get() = auth.currentUser != null

    init {
        if (isSignedIn) loadMyGroups()
    }

    fun loadMyGroups() {
        viewModelScope.launch {
            repository.observeMyGroups().collect { groups ->
                val uid = currentUid
                groups.forEach { group ->
                    val lastSeen = userPreferences.lastSeenExpenseTimestamp(group.code)
                    // Find expenses added by others since last time this user opened the app
                    val newExpenses = group.expenses.filter { expense ->
                        expense.timestamp > lastSeen && expense.paidByUid != uid
                    }
                    newExpenses.forEach { expense ->
                        notificationHelper.showGroupExpenseNotification(
                            groupName = group.name,
                            paidByName = expense.paidByName,
                            description = expense.description,
                            amount = expense.amount
                        )
                    }
                    // Mark all current expenses as seen
                    val latestTimestamp = group.expenses.maxOfOrNull { it.timestamp } ?: lastSeen
                    if (latestTimestamp > lastSeen) {
                        userPreferences.markGroupExpensesSeen(group.code, latestTimestamp)
                    }
                }
                _myGroups.value = groups
            }
        }
    }

    fun observeGroup(code: String) {
        viewModelScope.launch {
            repository.observeGroup(code).collect { _activeGroup.value = it }
        }
    }

    fun createGroup(name: String, onSuccess: (code: String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = GroupUiState.Loading
            repository.createGroup(name).fold(
                onSuccess = { code ->
                    // Auto-add pending expense from Txns page if present
                    if (pendingExpenseDescription.isNotBlank() && pendingExpenseAmount > 0) {
                        val desc = pendingExpenseDescription
                        val amt = pendingExpenseAmount
                        clearPendingExpense()
                        addExpense(code, desc, amt, emptyList()) {}
                    }
                    _uiState.value = GroupUiState.Success("Group created!")
                    onSuccess(code)
                },
                onFailure = { _uiState.value = GroupUiState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun joinGroup(code: String, onSuccess: (name: String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = GroupUiState.Loading
            repository.joinGroup(code.trim().uppercase()).fold(
                onSuccess = { name ->
                    _uiState.value = GroupUiState.Success("Joined $name!")
                    onSuccess(name)
                },
                onFailure = { _uiState.value = GroupUiState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun addExpense(
        code: String,
        description: String,
        amount: Double,
        members: List<com.expensetracker.data.model.GroupMember>,
        onDone: () -> Unit
    ) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            _uiState.value = GroupUiState.Loading
            val equalShare = if (members.isNotEmpty()) amount / (members.size + 1) else amount
            val splits = members.map { m ->
                GroupExpenseSplit(
                    uid = m.uid,
                    displayName = m.displayName,
                    share = equalShare,
                    settled = false
                )
            }
            val expense = GroupExpense(
                description = description,
                amount = amount,
                paidByUid = uid,
                paidByName = currentName,
                timestamp = System.currentTimeMillis(),
                splitAmong = splits
            )
            repository.addExpense(code, expense).fold(
                onSuccess = {
                    _uiState.value = GroupUiState.Success("Expense added")
                    onDone()
                },
                onFailure = { _uiState.value = GroupUiState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun deleteExpense(code: String, expenseId: String) {
        if (expenseId.isBlank()) {
            _uiState.value = GroupUiState.Error("Cannot delete: expense ID missing")
            return
        }
        viewModelScope.launch {
            _uiState.value = GroupUiState.Loading
            repository.deleteExpense(code, expenseId).fold(
                onSuccess = { _uiState.value = GroupUiState.Success("Expense deleted") },
                onFailure = { _uiState.value = GroupUiState.Error("Delete failed: ${it.message}") }
            )
        }
    }

    fun updateExpense(code: String, expenseId: String, description: String, amount: Double, onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = GroupUiState.Loading
            repository.updateExpense(code, expenseId, description, amount).fold(
                onSuccess = {
                    _uiState.value = GroupUiState.Success("Expense updated")
                    onDone()
                },
                onFailure = { _uiState.value = GroupUiState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun settleUp(code: String, expenseId: String, memberUid: String) {
        viewModelScope.launch {
            repository.settleUp(code, expenseId, memberUid)
        }
    }

    fun closeGroup(code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.closeGroup(code).fold(
                onSuccess = { onDone() },
                onFailure = { _uiState.value = GroupUiState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun resetUiState() { _uiState.value = GroupUiState.Idle }
}
