package com.example.elta.data

import com.example.elta.SettingsManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

enum class SyncStatus { IDLE, SYNCING, SUCCESS, ERROR }

class FirestoreSyncManager(
    private val repository: TransactionRepository,
    private val settingsManager: SettingsManager
) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private val _authUid = MutableStateFlow<String?>(null)
    val authStatus: StateFlow<String?> = _authUid

    /** Epoch-ms of the last successful sync (0 = never synced). */
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    // Overridden UID for loading data from another device
    private var syncUidOverride: String? = null

    /** Minimum interval between full syncs to avoid hammering Firestore. */
    private val minSyncIntervalMs = 30_000L
    private var lastSyncStartMs = 0L

    init {
        auth.addAuthStateListener { fa ->
            _authUid.value = fa.currentUser?.uid
        }
    }

    suspend fun ensureSignedIn(): String? {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                return null
            }
        }
        val uid = auth.currentUser?.uid
        _authUid.value = uid
        return uid
    }

    suspend fun signOut() {
        auth.signOut()
        syncUidOverride = null
        _authUid.value = null
        _syncStatus.value = SyncStatus.IDLE
        _lastSyncTime.value = 0L
    }

    fun setSyncUidOverride(uid: String?) {
        syncUidOverride = uid
        _authUid.value = uid ?: auth.currentUser?.uid
    }

    fun getEffectiveUid(): String? = syncUidOverride ?: auth.currentUser?.uid

    /**
     * Full two-way sync:
     * 1. Sync Username between local Settings and Firestore.
     * 2. Fetch all remote documents in a single round-trip (eliminates N+1 reads).
     * 3. Upload local changes where `isSynced = false`, using Last-Write-Wins on `lastModified`.
     * 4. Download remote changes and merge them locally.
     *
     * A [minSyncIntervalMs] guard prevents spamming Firestore on rapid successive calls.
     * Pass [force] = true to bypass the guard (e.g. manual sync tap).
     */
    suspend fun sync(localUsername: String? = null, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncStartMs) < minSyncIntervalMs) return

        val uid = ensureSignedIn() ?: run {
            _syncStatus.value = SyncStatus.ERROR
            return
        }
        val targetUid = getEffectiveUid() ?: uid
        lastSyncStartMs = now
        _syncStatus.value = SyncStatus.SYNCING

        try {
            val userDocRef = db.collection("users").document(targetUid)
            val col = userDocRef.collection("transactions")

            // ── 1. Sync Username ─────────────────────────────────────────────
            val userDoc = userDocRef.get().await()
            val remoteUsername = userDoc.getString("username")
            val usernameToUse = localUsername ?: settingsManager.username.first()

            if (!usernameToUse.isNullOrBlank()) {
                if (remoteUsername != usernameToUse) {
                    userDocRef.set(mapOf("username" to usernameToUse), SetOptions.merge()).await()
                }
            } else if (!remoteUsername.isNullOrBlank()) {
                settingsManager.setUsername(remoteUsername)
            }

            // ── 2. Fetch ALL remote docs in ONE round-trip ───────────────────
            val remoteSnap = col.get().await()
            // Build a map uuid → remote doc data for O(1) lookup later
            data class RemoteDoc(
                val uuid: String,
                val amount: Double,
                val category: String,
                val type: TransactionType,
                val timestamp: Long,
                val isDeleted: Boolean,
                val deletedAt: Long?,
                val lastModified: Long
            )

            val remoteMap: Map<String, RemoteDoc> = buildMap {
                for (doc in remoteSnap.documents) {
                    val uuid = doc.getString("uuid") ?: doc.id
                    val amount = doc.getDouble("amount") ?: continue
                    val category = doc.getString("category") ?: continue
                    val typeStr = doc.getString("type") ?: continue
                    val type = try { TransactionType.valueOf(typeStr) } catch (_: Exception) { continue }
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val isDeleted = doc.getBoolean("isDeleted") ?: false
                    val deletedAt = doc.getLong("deletedAt")
                    val lastModified = doc.getLong("lastModified") ?: timestamp
                    put(uuid, RemoteDoc(uuid, amount, category, type, timestamp, isDeleted, deletedAt, lastModified))
                }
            }

            // ── 3. Upload unsynced local transactions (Last-Write-Wins) ──────
            val unsynced = repository.getUnsyncedTransactions()
            for (t in unsynced) {
                val remote = remoteMap[t.uuid]
                if (remote != null && remote.lastModified > t.lastModified) {
                    // Remote is newer → update local copy and mark synced
                    repository.insert(
                        t.copy(
                            amount = remote.amount,
                            category = remote.category,
                            type = remote.type,
                            timestamp = remote.timestamp,
                            isSynced = true,
                            isDeleted = remote.isDeleted,
                            deletedAt = remote.deletedAt,
                            lastModified = remote.lastModified
                        )
                    )
                } else {
                    // Local is newer or remote doesn't exist → upload
                    col.document(t.uuid).set(
                        mapOf(
                            "amount"       to t.amount,
                            "category"     to t.category,
                            "type"         to t.type.name,
                            "timestamp"    to t.timestamp,
                            "uuid"         to t.uuid,
                            "isDeleted"    to t.isDeleted,
                            "deletedAt"    to t.deletedAt,
                            "lastModified" to t.lastModified
                        ),
                        SetOptions.merge()
                    ).await()
                    repository.insert(t.copy(isSynced = true))
                }
            }

            // ── 4. Download remote transactions not in local DB ──────────────
            for ((uuid, remote) in remoteMap) {
                val localTx = repository.getTransactionByUuid(uuid)
                if (localTx != null) {
                    if (remote.lastModified > localTx.lastModified) {
                        repository.insert(
                            localTx.copy(
                                amount = remote.amount,
                                category = remote.category,
                                type = remote.type,
                                timestamp = remote.timestamp,
                                isSynced = true,
                                isDeleted = remote.isDeleted,
                                deletedAt = remote.deletedAt,
                                lastModified = remote.lastModified
                            )
                        )
                    }
                } else {
                    repository.insert(
                        Transaction(
                            amount = remote.amount,
                            category = remote.category,
                            type = remote.type,
                            timestamp = remote.timestamp,
                            uuid = uuid,
                            isSynced = true,
                            isDeleted = remote.isDeleted,
                            deletedAt = remote.deletedAt,
                            lastModified = remote.lastModified
                        )
                    )
                }
            }

            _lastSyncTime.value = System.currentTimeMillis()
            _syncStatus.value = SyncStatus.SUCCESS
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.ERROR
        }
    }

    /** Restore all data from a different UUID (Clears local first, then downloads from user's collection) */
    suspend fun restoreFromUid(targetUid: String): Boolean {
        val trimmed = targetUid.trim()
        if (trimmed.length < 10) return false
        return try {
            val col = db.collection("users").document(trimmed).collection("transactions")
            val snap = col.get().await()
            if (snap.isEmpty) return false

            repository.clearAllLocalData()
            syncUidOverride = trimmed
            _authUid.value = trimmed
            settingsManager.setSyncUid(trimmed)

            // Fetch and set remote username (or null if empty) to clear previous user's username
            val userDoc = db.collection("users").document(trimmed).get().await()
            val remoteUsername = userDoc.getString("username")
            settingsManager.setUsername(if (remoteUsername.isNullOrBlank()) null else remoteUsername)

            for (doc in snap.documents) {
                val uuid = doc.getString("uuid") ?: doc.id
                val amount = doc.getDouble("amount") ?: continue
                val category = doc.getString("category") ?: continue
                val typeStr = doc.getString("type") ?: continue
                val type = try { TransactionType.valueOf(typeStr) } catch (_: Exception) { continue }
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                val isDeleted = doc.getBoolean("isDeleted") ?: false
                val deletedAt = doc.getLong("deletedAt")
                val lastModified = doc.getLong("lastModified") ?: timestamp

                repository.insert(
                    Transaction(
                        amount = amount, category = category, type = type,
                        timestamp = timestamp, uuid = uuid, isSynced = true,
                        isDeleted = isDeleted, deletedAt = deletedAt, lastModified = lastModified
                    )
                )
            }
            _lastSyncTime.value = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteFromFirestorePermanently(uuid: String) {
        val uid = getEffectiveUid() ?: return
        try {
            db.collection("users").document(uid)
                .collection("transactions").document(uuid).delete().await()
        } catch (_: Exception) {}
    }

    suspend fun clearFirestoreTrashPermanently(uuids: List<String>) {
        val uid = getEffectiveUid() ?: return
        if (uuids.isEmpty()) return
        val col = db.collection("users").document(uid).collection("transactions")
        val batch = db.batch()
        uuids.forEach { batch.delete(col.document(it)) }
        try { batch.commit().await() } catch (_: Exception) {}
    }
}
