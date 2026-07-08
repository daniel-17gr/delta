package com.example.elta.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val customCurrencyDao: CustomCurrencyDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val deletedTransactions: Flow<List<Transaction>> = transactionDao.getDeletedTransactions()

    val netBalance: Flow<Double> = transactionDao.getNetBalance()
    val totalIncome: Flow<Double> = transactionDao.getTotalByTypes(listOf("INCOME", "BORROWED"))
    val totalExpense: Flow<Double> = transactionDao.getAbsTotalByTypes(listOf("EXPENSE", "LENT"))

    val allCustomCurrencies: Flow<List<CustomCurrency>> = customCurrencyDao.getAllCustomCurrencies()

    suspend fun insert(transaction: Transaction) = transactionDao.insertTransaction(transaction)

    suspend fun delete(transaction: Transaction) = transactionDao.deleteTransaction(transaction)

    suspend fun deleteById(id: Long) = transactionDao.deleteTransactionById(id)

    suspend fun softDelete(uuid: String) = transactionDao.softDeleteTransactionByUuid(uuid)

    suspend fun restoreTransaction(uuid: String) = transactionDao.restoreTransactionByUuid(uuid)

    suspend fun deleteTransactionPermanently(uuid: String) = transactionDao.cleanPermanentDelete(uuid)

    suspend fun clearTrash() {
        val deleted = transactionDao.getDeletedTransactionsSync()
        if (deleted.isNotEmpty()) {
            transactionDao.markTrashForPermanentSync(deleted.map { it.uuid })
            deleted.forEach { transactionDao.cleanPermanentDelete(it.uuid) }
        }
    }

    suspend fun clearAllLocalData() {
        transactionDao.deleteAllTransactions()
        // Keep custom currencies — user defined those
    }

    suspend fun getUnsyncedTransactions() = transactionDao.getUnsyncedTransactions()

    suspend fun getTransactionByUuid(uuid: String) = transactionDao.getTransactionByUuid(uuid)

    suspend fun insertCustomCurrency(customCurrency: CustomCurrency) =
        customCurrencyDao.insertCustomCurrency(customCurrency)

    suspend fun deleteCustomCurrency(customCurrency: CustomCurrency) =
        customCurrencyDao.deleteCustomCurrency(customCurrency)

    suspend fun getCustomCurrencyCount(): Int = customCurrencyDao.getCustomCurrencyCount()
}
