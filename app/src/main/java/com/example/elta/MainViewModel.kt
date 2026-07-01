package com.example.elta

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.elta.data.AppDatabase
import com.example.elta.data.CustomCurrency
import com.example.elta.data.Transaction
import com.example.elta.data.TransactionRepository
import com.example.elta.data.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository

    val transactions: StateFlow<List<Transaction>>
    val customCurrencies: StateFlow<List<CustomCurrency>>

    // Pre-computed aggregations backed by SQLite — no main-thread math
    val netBalance: StateFlow<Double>
    val totalIncome: StateFlow<Double>
    val totalExpense: StateFlow<Double>

    init {
        val db = AppDatabase.getInstance(application)
        repository = TransactionRepository(db.transactionDao(), db.customCurrencyDao())
        transactions = repository.allTransactions
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        customCurrencies = repository.allCustomCurrencies
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        netBalance = repository.netBalance
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0.0
            )

        totalIncome = repository.totalIncome
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0.0
            )

        totalExpense = repository.totalExpense
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0.0
            )
    }

    fun addTransaction(amount: Double, category: String, type: TransactionType) {
        viewModelScope.launch {
            repository.insert(
                Transaction(
                    amount = if (type == TransactionType.INCOME || type == TransactionType.BORROWED) {
                        kotlin.math.abs(amount)
                    } else {
                        -kotlin.math.abs(amount)
                    },
                    category = category,
                    type = type
                )
            )
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.delete(transaction)
        }
    }

    fun insertTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insert(transaction)
        }
    }

    fun deleteTransactionById(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val signedAmount = if (transaction.type == TransactionType.INCOME || transaction.type == TransactionType.BORROWED) {
                kotlin.math.abs(transaction.amount)
            } else {
                -kotlin.math.abs(transaction.amount)
            }
            repository.insert(transaction.copy(amount = signedAmount))
        }
    }

    fun insertCustomCurrency(customCurrency: CustomCurrency) {
        viewModelScope.launch {
            repository.insertCustomCurrency(customCurrency)
        }
    }

    fun deleteCustomCurrency(customCurrency: CustomCurrency) {
        viewModelScope.launch {
            repository.deleteCustomCurrency(customCurrency)
        }
    }
}

