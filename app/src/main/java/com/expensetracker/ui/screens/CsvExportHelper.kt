package com.expensetracker.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionType
import java.io.File
import java.time.format.DateTimeFormatter

object CsvExportHelper {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun exportAndShare(context: Context, transactions: List<Transaction>) {
        val csv = buildCsv(transactions)
        val file = writeToCacheFile(context, csv)
        shareFile(context, file)
    }

    private fun buildCsv(transactions: List<Transaction>): String {
        val sb = StringBuilder()
        sb.appendLine("Date,Type,Merchant,Category,Amount,Payment Source,Account,Split,Notes")
        transactions.forEach { txn ->
            sb.appendLine(
                listOf(
                    txn.timestamp.format(dateFormatter),
                    if (txn.type == TransactionType.DEBIT) "Debit" else "Credit",
                    txn.merchant.escapeCsv(),
                    txn.category.displayName.escapeCsv(),
                    String.format("%.2f", txn.amount),
                    txn.source.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    txn.accountInfo.escapeCsv(),
                    if (txn.isSplit) "Yes" else "No",
                    ""
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun writeToCacheFile(context: Context, csv: String): File {
        val file = File(context.cacheDir, "autoexpense_transactions.csv")
        file.writeText(csv, Charsets.UTF_8)
        return file
    }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AutoExpense Transactions")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export CSV via…")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun String.escapeCsv(): String {
        return if (contains(",") || contains("\"") || contains("\n")) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }
}
