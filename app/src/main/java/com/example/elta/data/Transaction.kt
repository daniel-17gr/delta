package com.example.elta.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class TransactionType {
    INCOME, EXPENSE, BORROWED, LENT
}

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val type: TransactionType,
    val timestamp: Long = System.currentTimeMillis(),
    val uuid: String = UUID.randomUUID().toString(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val lastModified: Long = System.currentTimeMillis()
)
