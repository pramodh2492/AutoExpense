package com.expensetracker.data.repository

import com.expensetracker.data.model.ExpenseGroup
import com.expensetracker.data.model.GroupExpense
import com.expensetracker.data.model.GroupExpenseSplit
import com.expensetracker.data.model.GroupMember
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class GroupRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val groups = firestore.collection("groups")

    private val currentUid get() = auth.currentUser?.uid
    private val currentName get() = auth.currentUser?.displayName ?: "Unknown"
    private val currentPhoto get() = auth.currentUser?.photoUrl?.toString() ?: ""

    fun generateCode(): String {
        val letters = ('A'..'Z').toList()
        val digits = ('0'..'9').toList()
        val prefix = (1..4).map { letters.random() }.joinToString("")
        val suffix = (1..4).map { digits.random() }.joinToString("")
        return "$prefix-$suffix"
    }

    suspend fun createGroup(name: String): Result<String> {
        val uid = currentUid ?: return Result.failure(Exception("Not signed in"))
        return try {
            var code = generateCode()
            // Ensure code is unique
            while (groups.document(code).get().await().exists()) {
                code = generateCode()
            }
            val member = hashMapOf(
                "uid" to uid,
                "displayName" to currentName,
                "photoUrl" to currentPhoto,
                "joinedAt" to System.currentTimeMillis()
            )
            val group = hashMapOf(
                "code" to code,
                "name" to name,
                "createdByUid" to uid,
                "createdByName" to currentName,
                "createdAt" to System.currentTimeMillis(),
                "isClosed" to false,
                "memberUids" to listOf(uid)
            )
            groups.document(code).set(group).await()
            groups.document(code).collection("members").document(uid).set(member).await()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGroup(code: String): Result<String> {
        val uid = currentUid ?: return Result.failure(Exception("Not signed in"))
        return try {
            val doc = groups.document(code).get().await()
            if (!doc.exists()) return Result.failure(Exception("Group not found"))
            val isClosed = doc.getBoolean("isClosed") ?: false
            if (isClosed) return Result.failure(Exception("This group is closed"))
            val member = hashMapOf(
                "uid" to uid,
                "displayName" to currentName,
                "photoUrl" to currentPhoto,
                "joinedAt" to System.currentTimeMillis()
            )
            groups.document(code).collection("members").document(uid).set(member).await()
            // Add uid to memberUids array
            groups.document(code).update(
                "memberUids", com.google.firebase.firestore.FieldValue.arrayUnion(uid)
            ).await()
            val name = doc.getString("name") ?: code
            Result.success(name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeMyGroups(): Flow<List<ExpenseGroup>> = callbackFlow {
        val uid = currentUid
        if (uid == null) { trySend(emptyList()); close(); return@callbackFlow }

        // Track per-group member + expense listeners so they are cleaned up on close
        val groupRegs = mutableMapOf<String, List<ListenerRegistration>>()
        val membersByCode = mutableMapOf<String, List<GroupMember>>()
        val expensesByCode = mutableMapOf<String, List<GroupExpense>>()
        val baseGroupsByCode = mutableMapOf<String, ExpenseGroup>()

        fun emit() {
            val result = baseGroupsByCode.values.map { g ->
                g.copy(
                    members = membersByCode[g.code] ?: emptyList(),
                    expenses = expensesByCode[g.code] ?: emptyList()
                )
            }
            trySend(result)
        }

        fun attachSubListeners(code: String) {
            if (groupRegs.containsKey(code)) return
            val docRef = groups.document(code)
            val membersReg = docRef.collection("members").addSnapshotListener { snap, _ ->
                membersByCode[code] = snap?.documents?.mapNotNull { d ->
                    GroupMember(
                        uid = d.getString("uid") ?: return@mapNotNull null,
                        displayName = d.getString("displayName") ?: "",
                        photoUrl = d.getString("photoUrl") ?: "",
                        joinedAt = d.getLong("joinedAt") ?: 0L
                    )
                } ?: emptyList()
                emit()
            }
            val expensesReg = docRef.collection("expenses")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snap, _ ->
                    expensesByCode[code] = snap?.documents?.mapNotNull { d ->
                        @Suppress("UNCHECKED_CAST")
                        val splits = (d.get("splitAmong") as? List<Map<String, Any>>)?.map { s ->
                            GroupExpenseSplit(
                                uid = s["uid"] as? String ?: "",
                                displayName = s["displayName"] as? String ?: "",
                                share = (s["share"] as? Number)?.toDouble() ?: 0.0,
                                settled = s["settled"] as? Boolean ?: false
                            )
                        } ?: emptyList()
                        GroupExpense(
                            id = d.id,
                            description = d.getString("description") ?: "",
                            amount = d.getDouble("amount") ?: 0.0,
                            paidByUid = d.getString("paidByUid") ?: "",
                            paidByName = d.getString("paidByName") ?: "",
                            category = d.getString("category") ?: "other",
                            timestamp = d.getLong("timestamp") ?: 0L,
                            splitAmong = splits
                        )
                    } ?: emptyList()
                    emit()
                }
            groupRegs[code] = listOf(membersReg, expensesReg)
        }

        val topReg = groups.whereArrayContains("memberUids", uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { trySend(emptyList()); return@addSnapshotListener }
                val current = snap.documents.mapNotNull { it.toExpenseGroup() }
                // Remove stale sub-listeners for groups no longer present
                val currentCodes = current.map { it.code }.toSet()
                groupRegs.keys.filter { it !in currentCodes }.forEach { code ->
                    groupRegs.remove(code)?.forEach { it.remove() }
                    membersByCode.remove(code)
                    expensesByCode.remove(code)
                    baseGroupsByCode.remove(code)
                }
                current.forEach { g ->
                    baseGroupsByCode[g.code] = g
                    attachSubListeners(g.code)
                }
                emit()
            }

        awaitClose {
            topReg.remove()
            groupRegs.values.flatten().forEach { it.remove() }
        }
    }

    fun observeGroup(code: String): Flow<ExpenseGroup?> = callbackFlow {
        val docRef = groups.document(code)
        var groupReg: ListenerRegistration? = null
        var membersReg: ListenerRegistration? = null
        var expensesReg: ListenerRegistration? = null

        var currentGroup: ExpenseGroup? = null
        var currentMembers: List<GroupMember> = emptyList()
        var currentExpenses: List<GroupExpense> = emptyList()

        fun emit() {
            currentGroup?.let {
                trySend(it.copy(members = currentMembers, expenses = currentExpenses))
            }
        }

        groupReg = docRef.addSnapshotListener { snap, _ ->
            currentGroup = snap?.toExpenseGroup()
            emit()
        }
        membersReg = docRef.collection("members").addSnapshotListener { snap, _ ->
            currentMembers = snap?.documents?.mapNotNull { d ->
                GroupMember(
                    uid = d.getString("uid") ?: return@mapNotNull null,
                    displayName = d.getString("displayName") ?: "",
                    photoUrl = d.getString("photoUrl") ?: "",
                    joinedAt = d.getLong("joinedAt") ?: 0L
                )
            } ?: emptyList()
            emit()
        }
        expensesReg = docRef.collection("expenses")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                currentExpenses = snap?.documents?.mapNotNull { d ->
                    @Suppress("UNCHECKED_CAST")
                    val splits = (d.get("splitAmong") as? List<Map<String, Any>>)?.map { s ->
                        GroupExpenseSplit(
                            uid = s["uid"] as? String ?: "",
                            displayName = s["displayName"] as? String ?: "",
                            share = (s["share"] as? Number)?.toDouble() ?: 0.0,
                            settled = s["settled"] as? Boolean ?: false
                        )
                    } ?: emptyList()
                    GroupExpense(
                        id = d.id,
                        description = d.getString("description") ?: "",
                        amount = d.getDouble("amount") ?: 0.0,
                        paidByUid = d.getString("paidByUid") ?: "",
                        paidByName = d.getString("paidByName") ?: "",
                        category = d.getString("category") ?: "other",
                        timestamp = d.getLong("timestamp") ?: 0L,
                        splitAmong = splits
                    )
                } ?: emptyList()
                emit()
            }
        awaitClose {
            groupReg?.remove(); membersReg?.remove(); expensesReg?.remove()
        }
    }

    suspend fun addExpense(code: String, expense: GroupExpense): Result<Unit> {
        return try {
            // Dedup: reject if same paidBy + description + amount added within last 10 seconds
            val tenSecondsAgo = expense.timestamp - 10_000L
            val existing = groups.document(code).collection("expenses")
                .whereEqualTo("paidByUid", expense.paidByUid)
                .whereEqualTo("description", expense.description)
                .whereEqualTo("amount", expense.amount)
                .whereGreaterThan("timestamp", tenSecondsAgo)
                .get().await()
            if (!existing.isEmpty) return Result.failure(Exception("Duplicate expense"))

            val data = hashMapOf(
                "description" to expense.description,
                "amount" to expense.amount,
                "paidByUid" to expense.paidByUid,
                "paidByName" to expense.paidByName,
                "category" to expense.category,
                "timestamp" to expense.timestamp,
                "splitAmong" to expense.splitAmong.map { s ->
                    hashMapOf(
                        "uid" to s.uid,
                        "displayName" to s.displayName,
                        "share" to s.share,
                        "settled" to s.settled
                    )
                }
            )
            groups.document(code).collection("expenses").add(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun settleUp(code: String, expenseId: String, memberUid: String): Result<Unit> {
        return try {
            val expRef = groups.document(code).collection("expenses").document(expenseId)
            val doc = expRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val splits = (doc.get("splitAmong") as? List<Map<String, Any>>)?.map { s ->
                if (s["uid"] == memberUid) s.toMutableMap().apply { put("settled", true) } else s
            } ?: return Result.failure(Exception("Expense not found"))
            expRef.update("splitAmong", splits).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeGroup(code: String): Result<Unit> {
        return try {
            groups.document(code).update("isClosed", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toExpenseGroup(): ExpenseGroup? {
        return try {
            ExpenseGroup(
                code = getString("code") ?: id,
                name = getString("name") ?: "",
                createdByUid = getString("createdByUid") ?: "",
                createdByName = getString("createdByName") ?: "",
                createdAt = getLong("createdAt") ?: 0L,
                isClosed = getBoolean("isClosed") ?: false
            )
        } catch (_: Exception) { null }
    }
}
