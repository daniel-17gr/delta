package com.example.elta.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val customCurrencyDao: CustomCurrencyDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    val netBalance: Flow<Double> = transactionDao.getNetBalance()
    val totalIncome: Flow<Double> = transactionDao.getTotalByTypes(listOf("INCOME", "BORROWED"))
    val totalExpense: Flow<Double> = transactionDao.getAbsTotalByTypes(listOf("EXPENSE", "LENT"))

    val allCustomCurrencies: Flow<List<CustomCurrency>> = customCurrencyDao.getAllCustomCurrencies()

    suspend fun insert(transaction: Transaction) = transactionDao.insertTransaction(transaction)

    suspend fun delete(transaction: Transaction) = transactionDao.deleteTransaction(transaction)

    suspend fun deleteById(id: Long) = transactionDao.deleteTransactionById(id)

    suspend fun insertCustomCurrency(customCurrency: CustomCurrency) = customCurrencyDao.insertCustomCurrency(customCurrency)

    suspend fun deleteCustomCurrency(customCurrency: CustomCurrency) = customCurrencyDao.deleteCustomCurrency(customCurrency)

    suspend fun getCustomCurrencyCount(): Int = customCurrencyDao.getCustomCurrencyCount()
}
