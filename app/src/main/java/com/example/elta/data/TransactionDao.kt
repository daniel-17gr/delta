package com.example.elta.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    // --- Aggregation queries: offload heavy math to SQLite ---

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions")
    fun getNetBalance(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type IN (:types)")
    fun getTotalByTypes(types: List<String>): Flow<Double>

    @Query("SELECT COALESCE(SUM(ABS(amount)), 0.0) FROM transactions WHERE type IN (:types)")
    fun getAbsTotalByTypes(types: List<String>): Flow<Double>
}
