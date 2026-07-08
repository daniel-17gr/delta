package com.example.elta

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.elta.data.AppDatabase
import com.example.elta.data.CustomCurrency
import com.example.elta.data.FirestoreSyncManager
import com.example.elta.data.SyncStatus
import com.example.elta.data.Transaction
import com.example.elta.data.TransactionRepository
import com.example.elta.data.TransactionType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository
    val syncManager: FirestoreSyncManager

    val transactions: StateFlow<List<Transaction>>
    val deletedTransactions: StateFlow<List<Transaction>>
    val customCurrencies: StateFlow<List<CustomCurrency>>

    val netBalance: StateFlow<Double>
    val totalIncome: StateFlow<Double>
    val totalExpense: StateFlow<Double>

    val syncStatus: StateFlow<SyncStatus>
    val authStatus: StateFlow<String?>

    /** Epoch-ms of the last successful sync; 0 = never. */
    val lastSyncTime: StateFlow<Long>

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username

    companion object {
        /** How often to run a background periodic sync while the app is open. */
        private const val PERIODIC_SYNC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }

    init {
        val db = AppDatabase.getInstance(application)
        repository = TransactionRepository(db.transactionDao(), db.customCurrencyDao())
        val settingsManager = SettingsManager(application)
        syncManager = FirestoreSyncManager(repository, settingsManager)

        transactions = repository.allTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        deletedTransactions = repository.deletedTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        customCurrencies = repository.allCustomCurrencies
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        netBalance = repository.netBalance
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        totalIncome = repository.totalIncome
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        totalExpense = repository.totalExpense
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        syncStatus = syncManager.syncStatus
        authStatus = syncManager.authStatus
        lastSyncTime = syncManager.lastSyncTime

        // ── Auto sign-in then perform initial sync on app start ──────────────
        viewModelScope.launch {
            val uid = syncManager.ensureSignedIn()
            if (uid != null) {
                syncManager.sync(localUsername = _username.value, force = true)
            }
        }

        // ── Auto-sync whenever transactions change (debounced 800 ms) ────────
        // Drop the first emission (initial DB load) so we don't double-sync on start.
        viewModelScope.launch {
            transactions
                .drop(1)
                .debounce(800L)
                .collect {
                    syncManager.sync(localUsername = _username.value)
                }
        }

        // ── Periodic 15-minute background sync ───────────────────────────────
        viewModelScope.launch {
            while (isActive) {
                delay(PERIODIC_SYNC_INTERVAL_MS)
                syncManager.sync(localUsername = _username.value)
            }
        }
    }

    // ─── Transactions ────────────────────────────────────────────────────────

    fun addTransaction(amount: Double, category: String, type: TransactionType) {
        viewModelScope.launch {
            repository.insert(
                Transaction(
                    amount = if (type == TransactionType.INCOME || type == TransactionType.BORROWED)
                        kotlin.math.abs(amount) else -kotlin.math.abs(amount),
                    category = category,
                    type = type
                )
            )
            // Transaction change will be caught by the debounced collector above
        }
    }


    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.softDelete(transaction.uuid) }
    }

    fun insertTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.insert(transaction) }
    }

    fun deleteTransactionById(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val signed = if (transaction.type == TransactionType.INCOME || transaction.type == TransactionType.BORROWED)
                kotlin.math.abs(transaction.amount) else -kotlin.math.abs(transaction.amount)
            repository.insert(transaction.copy(amount = signed, isSynced = false))
        }
    }

    // ─── Trash ───────────────────────────────────────────────────────────────

    fun restoreTransaction(uuid: String) {
        viewModelScope.launch { repository.restoreTransaction(uuid) }
    }

    fun restoreTransactions(uuids: List<String>) {
        viewModelScope.launch {
            uuids.forEach { uuid ->
                repository.restoreTransaction(uuid)
            }
        }
    }

    fun deleteTransactionPermanently(uuid: String) {
        viewModelScope.launch {
            repository.deleteTransactionPermanently(uuid)
            syncManager.deleteFromFirestorePermanently(uuid)
        }
    }

    fun deleteTransactionsPermanently(uuids: List<String>) {
        viewModelScope.launch {
            uuids.forEach { uuid ->
                repository.deleteTransactionPermanently(uuid)
                syncManager.deleteFromFirestorePermanently(uuid)
            }
        }
    }

    fun clearTrash(uuids: List<String>) {
        viewModelScope.launch {
            syncManager.clearFirestoreTrashPermanently(uuids)
            repository.clearTrash()
        }
    }

    // ─── Cloud Sync ───────────────────────────────────────────────────────────

    /** Manual sync — bypasses the 30-second guard so it fires immediately. */
    fun sync() {
        viewModelScope.launch { syncManager.sync(localUsername = _username.value, force = true) }
    }

    /** Load all data from another user's UUID */
    fun loadFromUid(uid: String) {
        viewModelScope.launch {
            val trimmed = uid.trim()
            if (trimmed.length < 10) {
                _errorMessage.value = "Enter a valid UUID"
                return@launch
            }
            _errorMessage.value = null
            val success = syncManager.restoreFromUid(trimmed)
            if (!success) {
                _errorMessage.value = "No data found or permission denied"
            }
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch { repository.clearAllLocalData() }
    }

    suspend fun clearLocalData() {
        repository.clearAllLocalData()
    }

    fun signOut() {
        viewModelScope.launch {
            syncManager.signOut()
            _username.value = null
        }
    }

    fun updateLocalUsername(name: String?) {
        _username.value = name
    }

    fun clearError() { _errorMessage.value = null }

    // ─── Custom Currencies ───────────────────────────────────────────────────

    fun insertCustomCurrency(customCurrency: CustomCurrency) {
        viewModelScope.launch { repository.insertCustomCurrency(customCurrency) }
    }

    fun deleteCustomCurrency(customCurrency: CustomCurrency) {
        viewModelScope.launch { repository.deleteCustomCurrency(customCurrency) }
    }
}
