package com.example.elta.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    suspend fun getDeletedTransactionsSync(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE isSynced = 0")
    suspend fun getUnsyncedTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE uuid = :uuid LIMIT 1")
    suspend fun getTransactionByUuid(uuid: String): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("UPDATE transactions SET isDeleted = 1, deletedAt = :deletedAt, isSynced = 0, lastModified = :deletedAt WHERE uuid = :uuid")
    suspend fun softDeleteTransactionByUuid(uuid: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isDeleted = 0, deletedAt = NULL, isSynced = 0, lastModified = :now WHERE uuid = :uuid")
    suspend fun restoreTransactionByUuid(uuid: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE uuid = :uuid")
    suspend fun cleanPermanentDelete(uuid: String)

    @Query("UPDATE transactions SET isSynced = 0 WHERE isDeleted = 1 AND uuid IN (:uuids)")
    suspend fun markTrashForPermanentSync(uuids: List<String>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT COUNT(*) FROM transactions WHERE isDeleted = 0")
    suspend fun getTransactionCount(): Int

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE isDeleted = 0")
    fun getNetBalance(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE isDeleted = 0 AND type IN (:types)")
    fun getTotalByTypes(types: List<String>): Flow<Double>

    @Query("SELECT COALESCE(SUM(ABS(amount)), 0.0) FROM transactions WHERE isDeleted = 0 AND type IN (:types)")
    fun getAbsTotalByTypes(types: List<String>): Flow<Double>
}
