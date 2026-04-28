package com.example.glicose.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.example.glicose.data.GlucoseDatabase
import com.example.glicose.data.GlucoseRecord
import com.example.glicose.data.Reminder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest

class GlucoseViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = GlucoseDatabase.getDatabase(application).glucoseDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val appContext = application.applicationContext

    private fun triggerWidgetUpdate() {
        val intent = Intent("com.example.glicose.UPDATE_WIDGET")
        intent.setPackage(appContext.packageName)
        appContext.sendBroadcast(intent)
    }
    
    // The currently viewed user ID (starts as the logged-in user)
    val currentUserId = MutableStateFlow(auth.currentUser?.uid ?: "")
    
    private val prefs = application.getSharedPreferences("glucose_prefs", Context.MODE_PRIVATE)
    
    val targetMin = MutableStateFlow(prefs.getFloat("target_min", 70f))
    val targetMax = MutableStateFlow(prefs.getFloat("target_max", 140f))

    fun updateTargetRange(min: Float, max: Float) {
        targetMin.value = min
        targetMax.value = max
        prefs.edit().putFloat("target_min", min).putFloat("target_max", max).apply()
    }
    
    init {
        // Create user profile on Firestore and start sync for whoever is logged in
        auth.currentUser?.let { user ->
            updateUserProfile(user)
            startCloudToLocalSync(user.uid)
            migrateFollowersData(user.uid)
        }
    }

    private fun updateUserProfile(user: com.google.firebase.auth.FirebaseUser) {
        val userCode = user.uid.take(6).uppercase()
        val userData = hashMapOf(
            "uid" to user.uid,
            "name" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "userCode" to userCode
        )
        firestore.collection("users").document(user.uid).set(userData)
            .addOnSuccessListener { android.util.Log.d("Firestore", "Profile created/updated for ${user.uid}") }
            .addOnFailureListener { android.util.Log.e("Firestore", "Failed to create profile", it) }
    }

    fun setCurrentUserId(uid: String) {
        currentUserId.value = uid
        // If the new UID is the logged-in user, ensure their profile and sync are active
        auth.currentUser?.let { user ->
            if (uid == user.uid) {
                updateUserProfile(user)
                startCloudToLocalSync(user.uid)
            }
        }
    }

    private var syncListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun startCloudToLocalSync(uid: String) {
        syncListener?.remove()
        syncListener = firestore.collection("glucose_records")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    viewModelScope.launch {
                        snapshot.documents.forEach { doc ->
                            val value = doc.getDouble("value")?.toFloat() ?: return@forEach
                            val note = doc.getString("note") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val record = GlucoseRecord(id = timestamp, value = value, note = note, timestamp = timestamp, userId = uid)
                            dao.insertIgnore(record)
                        }
                    }
                }
            }
    }

    /**
     * Migrates old "following" data to also create "followers" entries on the target user.
     * This fixes connections made before the bidirectional follow system was implemented.
     */
    private fun migrateFollowersData(myUid: String) {
        val myName = auth.currentUser?.displayName ?: "Usuário"
        firestore.collection("users").document(myUid).collection("following").get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { doc ->
                    val targetUid = doc.getString("uid") ?: return@forEach
                    val followerData = hashMapOf("uid" to myUid, "name" to myName)
                    // Write to their "followers" list only if not already there
                    firestore.collection("users").document(targetUid)
                        .collection("followers").document(myUid)
                        .set(followerData)
                }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allRecords: StateFlow<List<GlucoseRecord>> = currentUserId.flatMapLatest { uid ->
        if (uid == auth.currentUser?.uid) {
            dao.getAll(uid)
        } else {
            // Shared profile: observe Firestore directly
            callbackFlow {
                val listener = firestore.collection("glucose_records")
                    .whereEqualTo("userId", uid)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null) {
                            val records = snapshot.documents.mapNotNull { doc ->
                                val value = doc.getDouble("value")?.toFloat() ?: return@mapNotNull null
                                val note = doc.getString("note") ?: ""
                                val timestamp = doc.getLong("timestamp") ?: 0L
                                GlucoseRecord(id = timestamp, value = value, note = note, timestamp = timestamp, userId = uid)
                            }
                            trySend(records)
                        }
                    }
                awaitClose { listener.remove() }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestRecord: StateFlow<GlucoseRecord?> = currentUserId.flatMapLatest { uid ->
        if (uid == auth.currentUser?.uid) {
            dao.getLatest(uid)
        } else {
            callbackFlow {
                val listener = firestore.collection("glucose_records")
                    .whereEqualTo("userId", uid)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null) {
                            val doc = snapshot.documents.firstOrNull()
                            if (doc != null) {
                                val value = doc.getDouble("value")?.toFloat() ?: 0f
                                val note = doc.getString("note") ?: ""
                                val timestamp = doc.getLong("timestamp") ?: 0L
                                trySend(GlucoseRecord(id = timestamp, value = value, note = note, timestamp = timestamp, userId = uid))
                            } else {
                                trySend(null)
                            }
                        }
                    }
                awaitClose { listener.remove() }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val allReminders: StateFlow<List<Reminder>> = currentUserId.flatMapLatest { uid ->
        dao.getAllReminders(uid)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val followedUsers: StateFlow<List<Pair<String, String>>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: ""
        if (uid.isEmpty()) {
            trySend(emptyList())
            Unit
        } else {
            val listener = firestore.collection("users").document(uid).collection("following")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val users = snapshot.documents.map { 
                            (it.getString("name") ?: "Desconhecido") to (it.getString("uid") ?: "")
                        }
                        trySend(users)
                    }
                }
            awaitClose { listener.remove() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val followers: StateFlow<List<Pair<String, String>>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: ""
        if (uid.isEmpty()) {
            trySend(emptyList())
            Unit
        } else {
            val listener = firestore.collection("users").document(uid).collection("followers")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val users = snapshot.documents.map { 
                            (it.getString("name") ?: "Desconhecido") to (it.getString("uid") ?: "")
                        }
                        trySend(users)
                    }
                }
            awaitClose { listener.remove() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun followUser(code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val myUid = auth.currentUser?.uid ?: return
        val myName = auth.currentUser?.displayName ?: "Usuário"
        
        firestore.collection("users").whereEqualTo("userCode", code.uppercase()).get()
            .addOnSuccessListener { snapshot ->
                val targetDoc = snapshot.documents.firstOrNull()
                if (targetDoc != null) {
                    val targetUid = targetDoc.getString("uid") ?: ""
                    val targetName = targetDoc.getString("name") ?: "Paciente"
                    
                    if (targetUid == myUid) {
                        onError("Você não pode seguir a si mesmo!")
                        return@addOnSuccessListener
                    }

                    val followingData = hashMapOf("uid" to targetUid, "name" to targetName)
                    val followerData = hashMapOf("uid" to myUid, "name" to myName)
                    
                    val batch = firestore.batch()
                    
                    // Add to my "following" list
                    val myFollowingRef = firestore.collection("users").document(myUid)
                        .collection("following").document(targetUid)
                    batch.set(myFollowingRef, followingData)
                    
                    // Add to their "followers" list
                    val targetFollowersRef = firestore.collection("users").document(targetUid)
                        .collection("followers").document(myUid)
                    batch.set(targetFollowersRef, followerData)
                    
                    batch.commit()
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onError("Erro ao salvar: ${it.message}") }
                } else {
                    onError("Código não encontrado!")
                }
            }
            .addOnFailureListener { onError("Erro na busca: ${it.message}") }
    }

    fun unfollow(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val batch = firestore.batch()
        
        batch.delete(firestore.collection("users").document(myUid).collection("following").document(targetUid))
        batch.delete(firestore.collection("users").document(targetUid).collection("followers").document(myUid))
        
        batch.commit()
    }

    fun removeFollower(followerUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val batch = firestore.batch()
        
        batch.delete(firestore.collection("users").document(myUid).collection("followers").document(followerUid))
        batch.delete(firestore.collection("users").document(followerUid).collection("following").document(myUid))
        
        batch.commit()
    }

    fun addRecord(value: Float, note: String, timestamp: Long = System.currentTimeMillis()) {
        val uid = auth.currentUser?.uid ?: return
        val record = GlucoseRecord(value = value, note = note, timestamp = timestamp, userId = uid)
        viewModelScope.launch {
            dao.insert(record)
            firestore.collection("glucose_records").document(record.timestamp.toString()).set(record)
            triggerWidgetUpdate()
        }
    }

    fun deleteRecord(record: GlucoseRecord) {
        viewModelScope.launch {
            dao.delete(record)
            if (record.userId == auth.currentUser?.uid) {
                firestore.collection("glucose_records").document(record.timestamp.toString()).delete()
            }
            triggerWidgetUpdate()
        }
    }

    fun updateRecord(record: GlucoseRecord, value: Float, note: String) {
        viewModelScope.launch {
            dao.delete(record)
            val newRecord = GlucoseRecord(value = value, note = note, timestamp = record.timestamp, userId = record.userId)
            dao.insert(newRecord)
            if (record.userId == auth.currentUser?.uid) {
                firestore.collection("glucose_records").document(record.timestamp.toString()).set(newRecord)
            }
            triggerWidgetUpdate()
        }
    }

    fun addReminder(hour: Int, minute: Int) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            dao.insertReminder(Reminder(hour = hour, minute = minute, userId = uid))
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            dao.deleteReminder(reminder)
        }
    }

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            dao.updateReminderStatus(reminder.id, !reminder.enabled)
        }
    }

    fun updateReminder(reminder: Reminder, hour: Int, minute: Int) {
        viewModelScope.launch {
            dao.deleteReminder(reminder)
            dao.insertReminder(Reminder(hour = hour, minute = minute, enabled = reminder.enabled, userId = reminder.userId))
        }
    }

    fun syncLocalDataToCloud(onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            // Get all local records for this user
            val localRecords = dao.getAllSync(uid)
            localRecords.forEach { record ->
                firestore.collection("glucose_records")
                    .document(record.timestamp.toString())
                    .set(record)
            }
            onComplete()
        }
    }

    fun clearAllData() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            // 1. Clear local
            dao.deleteAllForUser(uid)
            dao.deleteAllRemindersForUser(uid)
            triggerWidgetUpdate()
            
            // 2. Clear Cloud (Firestore)
            firestore.collection("glucose_records")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val batch = firestore.batch()
                    snapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                }
        }
    }
}
