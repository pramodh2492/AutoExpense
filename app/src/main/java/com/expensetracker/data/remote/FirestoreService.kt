package com.expensetracker.data.remote

import com.expensetracker.data.model.PaymentSource
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    suspend fun ensureAuthenticated() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    private fun getUserCollection() =
        firestore.collection("users")
            .document(auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated"))
            .collection("transactions")

    private fun getMerchantMappingsCollection() =
        firestore.collection("users")
            .document(auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated"))
            .collection("merchant_mappings")

    suspend fun syncTransaction(transaction: Transaction) {
        ensureAuthenticated()
        val doc = getUserCollection().document(transaction.smsHash.ifBlank { transaction.id.toString() })
        doc.set(transaction.toFirestoreMap()).await()
    }

    suspend fun syncAllTransactions(transactions: List<Transaction>) {
        ensureAuthenticated()
        val collection = getUserCollection()

        // Firestore batch limit is 500
        transactions.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { transaction ->
                val docId = transaction.smsHash.ifBlank { transaction.id.toString() }
                val doc = collection.document(docId)
                batch.set(doc, transaction.toFirestoreMap())
            }
            batch.commit().await()
        }
    }

    suspend fun syncMerchantMappings(mappings: List<Pair<String, String>>) {
        ensureAuthenticated()
        val collection = getMerchantMappingsCollection()
        mappings.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (merchant, category) ->
                val doc = collection.document(merchant)
                batch.set(doc, mapOf("merchant" to merchant, "category" to category))
            }
            batch.commit().await()
        }
    }

    suspend fun fetchAllTransactions(): List<Transaction> {
        ensureAuthenticated()
        return getUserCollection()
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    Transaction(
                        amount = (data["amount"] as Number).toDouble(),
                        merchant = data["merchant"] as? String ?: "Unknown",
                        category = TransactionCategory.valueOf(data["category"] as? String ?: "OTHER"),
                        type = TransactionType.valueOf(data["type"] as? String ?: "DEBIT"),
                        source = PaymentSource.valueOf(data["source"] as? String ?: "UNKNOWN"),
                        accountInfo = data["accountInfo"] as? String ?: "",
                        rawSms = data["rawSms"] as? String ?: "",
                        smsHash = data["smsHash"] as? String ?: doc.id,
                        timestamp = LocalDateTime.parse(data["timestamp"] as String),
                        isSelfTransfer = data["isSelfTransfer"] as? Boolean ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    suspend fun fetchMerchantMappings(): List<Pair<String, String>> {
        ensureAuthenticated()
        return getMerchantMappingsCollection()
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val merchant = data["merchant"] as? String ?: return@mapNotNull null
                val category = data["category"] as? String ?: return@mapNotNull null
                merchant to category
            }
    }

    private fun Transaction.toFirestoreMap(): Map<String, Any> = mapOf(
        "amount" to amount,
        "merchant" to merchant,
        "category" to category.name,
        "type" to type.name,
        "source" to source.name,
        "accountInfo" to accountInfo,
        "rawSms" to rawSms,
        "smsHash" to smsHash,
        "timestamp" to timestamp.toString(),
        "isSelfTransfer" to isSelfTransfer
    )
}
