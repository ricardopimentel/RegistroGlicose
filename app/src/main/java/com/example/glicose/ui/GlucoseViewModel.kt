package com.example.glicose.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.example.glicose.data.GlucoseDatabase
import com.example.glicose.data.GlucoseRecord
import com.example.glicose.data.Reminder
import com.example.glicose.data.FoodItem
import com.ricardo.glicose.R
import org.json.JSONArray
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
    val carbRatio = MutableStateFlow(prefs.getFloat("carb_ratio", 0f)) // 0f means disabled
    
    // Theme selection: 0 = System, 1 = Light, 2 = Dark
    val appTheme = MutableStateFlow(prefs.getInt("app_theme", 0))

    fun updateAppTheme(theme: Int) {
        appTheme.value = theme
        prefs.edit().putInt("app_theme", theme).apply()
        triggerWidgetUpdate()
    }

    fun updateTargetRange(min: Float, max: Float) {
        targetMin.value = min
        targetMax.value = max
        prefs.edit().putFloat("target_min", min).putFloat("target_max", max).apply()
    }

    fun updateCarbRatio(ratio: Float) {
        carbRatio.value = ratio
        prefs.edit().putFloat("carb_ratio", ratio).apply()
    }
    
    val customFoods = MutableStateFlow<List<FoodItem>>(emptyList())
    
    private fun loadCustomFoods() {
        val json = prefs.getString("custom_foods", null)
        if (json != null) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<FoodItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        FoodItem(
                            id = obj.getInt("id"),
                            name = obj.getString("name"),
                            measure = obj.getString("measure"),
                            grams = obj.getDouble("grams").toFloat(),
                            carbs = obj.getDouble("carbs").toFloat(),
                            calories = obj.getDouble("calories").toFloat()
                        )
                    )
                }
                customFoods.value = list
            } catch (e: Exception) {
                android.util.Log.e("GlucoseViewModel", "Error loading custom foods", e)
            }
        }
    }
    
    fun addCustomFood(food: FoodItem) {
        val uid = auth.currentUser?.uid ?: return
        val current = customFoods.value.toMutableList()
        current.add(food)
        customFoods.value = current
        
        try {
            val array = JSONArray()
            current.forEach { item ->
                val obj = org.json.JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("measure", item.measure)
                    put("grams", item.grams.toDouble())
                    put("carbs", item.carbs.toDouble())
                    put("calories", item.calories.toDouble())
                }
                array.put(obj)
            }
            prefs.edit().putString("custom_foods", array.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("GlucoseViewModel", "Error saving custom foods", e)
        }
        
        // Save to Firestore for sync across devices
        val data = hashMapOf(
            "id" to food.id,
            "userId" to uid,
            "name" to food.name,
            "measure" to food.measure,
            "grams" to food.grams.toDouble(),
            "carbs" to food.carbs.toDouble(),
            "calories" to food.calories.toDouble()
        )
        firestore.collection("custom_foods")
            .document("${uid}_${food.id}")
            .set(data)
            .addOnSuccessListener { android.util.Log.d("Firestore", "Custom food synced: ${food.name}") }
            .addOnFailureListener { android.util.Log.e("Firestore", "Error syncing custom food", it) }
    }

    // Update an existing custom food (used by edit screen)
    fun updateCustomFood(updatedFood: FoodItem) {
        val uid = auth.currentUser?.uid ?: return
        val current = customFoods.value.toMutableList()
        val index = current.indexOfFirst { it.id == updatedFood.id }
        if (index != -1) {
            current[index] = updatedFood
            customFoods.value = current
            // Persist locally
            try {
                val array = JSONArray()
                current.forEach { item ->
                    val obj = org.json.JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("measure", item.measure)
                        put("grams", item.grams.toDouble())
                        put("carbs", item.carbs.toDouble())
                        put("calories", item.calories.toDouble())
                    }
                    array.put(obj)
                }
                prefs.edit().putString("custom_foods", array.toString()).apply()
            } catch (e: Exception) {
                android.util.Log.e("GlucoseViewModel", "Error updating custom foods", e)
            }
            // Sync to Firestore (replace existing document)
            val data = hashMapOf(
                "id" to updatedFood.id,
                "userId" to uid,
                "name" to updatedFood.name,
                "measure" to updatedFood.measure,
                "grams" to updatedFood.grams.toDouble(),
                "carbs" to updatedFood.carbs.toDouble(),
                "calories" to updatedFood.calories.toDouble()
            )
            firestore.collection("custom_foods")
                .document("${uid}_${updatedFood.id}")
                .set(data)
                .addOnSuccessListener { android.util.Log.d("Firestore", "Custom food updated: ${updatedFood.name}") }
                .addOnFailureListener { android.util.Log.e("Firestore", "Error updating custom food", it) }
        }
    }
    
    init {
        loadCustomFoods()
        // Create user profile on Firestore and start sync for whoever is logged in
        auth.currentUser?.let { user ->
            updateUserProfile(user)
            startCloudToLocalSync(user.uid)
            startCustomFoodsSync(user.uid)
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
                startCustomFoodsSync(user.uid)
            }
        }
    }

    private var syncListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var customFoodsSyncListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun startCloudToLocalSync(uid: String) {
        syncListener?.remove()
        syncListener = firestore.collection("glucose_records")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Firestore", "Sync error for $uid", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch {
                        snapshot.documents.forEach { doc ->
                            val value = doc.getDouble("value")?.toFloat() ?: return@forEach
                            val note = doc.getString("note") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val carbs = doc.getDouble("carbs")?.toFloat()
                            val calories = doc.getDouble("calories")?.toFloat()
                            val mealDetails = doc.getString("mealDetails")
                            val record = GlucoseRecord(
                                value = value,
                                note = note,
                                timestamp = timestamp,
                                userId = uid,
                                carbs = carbs,
                                calories = calories,
                                mealDetails = mealDetails
                            )
                            dao.insertIgnore(record)
                        }
                    }
                }
            }
    }

    private fun startCustomFoodsSync(uid: String) {
        customFoodsSyncListener?.remove()
        customFoodsSyncListener = firestore.collection("custom_foods")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Firestore", "Custom foods sync error for $uid", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getLong("id")?.toInt() ?: return@mapNotNull null
                        val name = doc.getString("name") ?: ""
                        val measure = doc.getString("measure") ?: ""
                        val grams = doc.getDouble("grams")?.toFloat() ?: 0f
                        val carbs = doc.getDouble("carbs")?.toFloat() ?: 0f
                        val calories = doc.getDouble("calories")?.toFloat() ?: 0f
                        FoodItem(id = id, name = name, measure = measure, grams = grams, carbs = carbs, calories = calories)
                    }
                    customFoods.value = list
                    
                    // Save to SharedPreferences for offline cache
                    try {
                        val array = JSONArray()
                        list.forEach { item ->
                            val obj = org.json.JSONObject().apply {
                                put("id", item.id)
                                put("name", item.name)
                                put("measure", item.measure)
                                put("grams", item.grams.toDouble())
                                put("carbs", item.carbs.toDouble())
                                put("calories", item.calories.toDouble())
                            }
                            array.put(obj)
                        }
                        prefs.edit().putString("custom_foods", array.toString()).apply()
                    } catch (e: Exception) {
                        android.util.Log.e("GlucoseViewModel", "Error saving cached custom foods", e)
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
            // Removed orderBy to avoid index requirements; sorting in memory.
            callbackFlow {
                val listener = firestore.collection("glucose_records")
                    .whereEqualTo("userId", uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.e("Firestore", "Records flow error for $uid", error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val records = snapshot.documents.mapNotNull { doc ->
                                val value = doc.getDouble("value")?.toFloat() ?: return@mapNotNull null
                                val note = doc.getString("note") ?: ""
                                val timestamp = doc.getLong("timestamp") ?: 0L
                                val carbs = doc.getDouble("carbs")?.toFloat()
                                val calories = doc.getDouble("calories")?.toFloat()
                                GlucoseRecord(value = value, note = note, timestamp = timestamp, userId = uid, carbs = carbs, calories = calories)
                            }.sortedByDescending { it.timestamp }
                            
                            android.util.Log.d("Firestore", "Received ${records.size} records for $uid")
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
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null) {
                            val records = snapshot.documents.mapNotNull { doc ->
                                val value = doc.getDouble("value")?.toFloat() ?: return@mapNotNull null
                                if (value <= 0f) return@mapNotNull null
                                val note = doc.getString("note") ?: ""
                                val timestamp = doc.getLong("timestamp") ?: 0L
                                val carbs = doc.getDouble("carbs")?.toFloat()
                                val calories = doc.getDouble("calories")?.toFloat()
                                GlucoseRecord(value = value, note = note, timestamp = timestamp, userId = uid, carbs = carbs, calories = calories)
                            }.sortedByDescending { it.timestamp }
                            
                            trySend(records.firstOrNull())
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
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("Firestore", "Following listener error", error)
                        return@addSnapshotListener
                    }
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

    fun addRecord(
        value: Float,
        note: String,
        timestamp: Long = System.currentTimeMillis(),
        carbs: Float? = null,
        calories: Float? = null,
        mealDetails: String? = null
    ) {
        val uid = auth.currentUser?.uid ?: return
        val record = GlucoseRecord(
            value = value,
            note = note,
            timestamp = timestamp,
            userId = uid,
            carbs = carbs,
            calories = calories,
            mealDetails = mealDetails
        )
        viewModelScope.launch {
            try {
                dao.insert(record)
            } catch (e: Exception) {
                android.util.Log.e("Room", "Collision or error inserting record", e)
            }
            val data = hashMapOf(
                "value" to value.toDouble(),
                "note" to note,
                "timestamp" to timestamp,
                "userId" to uid,
                "carbs" to carbs?.toDouble(),
                "calories" to calories?.toDouble(),
                "mealDetails" to mealDetails
            )
            firestore.collection("glucose_records")
                .document("${uid}_${record.timestamp}")
                .set(data)
            triggerWidgetUpdate()
        }
    }

    fun deleteRecord(record: GlucoseRecord) {
        viewModelScope.launch {
            dao.delete(record)
            if (record.userId == auth.currentUser?.uid) {
                firestore.collection("glucose_records").document("${record.userId}_${record.timestamp}").delete()
            }
            triggerWidgetUpdate()
        }
    }

    fun updateRecord(
        record: GlucoseRecord,
        value: Float,
        note: String,
        newTimestamp: Long,
        carbs: Float? = record.carbs,
        calories: Float? = record.calories,
        mealDetails: String? = record.mealDetails
    ) {
        viewModelScope.launch {
            val myUid = auth.currentUser?.uid ?: return@launch
            
            // If timestamp changed and it's our record, delete the old document
            if (newTimestamp != record.timestamp && record.userId == myUid) {
                firestore.collection("glucose_records").document("${record.userId}_${record.timestamp}").delete()
            }
            
            dao.delete(record)
            val newRecord = GlucoseRecord(
                value = value,
                note = note,
                timestamp = newTimestamp,
                userId = record.userId,
                carbs = carbs,
                calories = calories,
                mealDetails = mealDetails
            )
            dao.insert(newRecord)
            
            if (newRecord.userId == myUid) {
                val data = hashMapOf(
                    "value" to value.toDouble(),
                    "note" to note,
                    "timestamp" to newTimestamp,
                    "userId" to record.userId,
                    "carbs" to carbs?.toDouble(),
                    "calories" to calories?.toDouble(),
                    "mealDetails" to mealDetails
                )
                firestore.collection("glucose_records")
                    .document("${newRecord.userId}_${newRecord.timestamp}")
                    .set(data)
            }
            triggerWidgetUpdate()
        }
    }

    fun addReminder(hour: Int, minute: Int, frequency: String = "DAILY", daysOfWeek: String = "0,1,2,3,4,5,6", onIdGenerated: (Long) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val id = dao.insertReminder(Reminder(hour = hour, minute = minute, userId = uid, frequency = frequency, daysOfWeek = daysOfWeek))
            onIdGenerated(id)
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

    fun updateReminder(reminder: Reminder, hour: Int, minute: Int, frequency: String = "DAILY", daysOfWeek: String? = null) {
        viewModelScope.launch {
            dao.updateReminder(
                reminder.copy(
                    hour = hour, 
                    minute = minute, 
                    frequency = frequency,
                    daysOfWeek = daysOfWeek ?: reminder.daysOfWeek
                )
            )
        }
    }

    fun syncLocalDataToCloud(onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val localRecords = dao.getAllSync(uid)
            localRecords.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { record ->
                    batch.set(firestore.collection("glucose_records").document("${uid}_${record.timestamp}"), record)
                }
                batch.commit()
            }
            onComplete()
        }
    }

    /**
     * Efficiently imports a list of records.
     * Uses Room bulk insert and Firestore batching.
     */
    fun importRecords(recordsData: List<Triple<Float, String, Long>>, onComplete: (Int) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val records = recordsData.map { (v, n, t) -> 
                GlucoseRecord(value = v, note = n, timestamp = t, userId = uid) 
            }
            
            // 1. Local Insert (Ignore duplicates)
            dao.insertIgnoreAll(records)
            
            // 2. Cloud Upload (Batched)
            var successCount = 0
            val chunks = records.chunked(500)
            
            for (chunk in chunks) {
                val batch = firestore.batch()
                chunk.forEach { record ->
                    val docRef = firestore.collection("glucose_records")
                        .document("${uid}_${record.timestamp}")
                    batch.set(docRef, record)
                }
                
                // Using task completion listener to track progress
                batch.commit().addOnSuccessListener {
                    successCount += chunk.size
                    if (successCount >= records.size) {
                        triggerWidgetUpdate()
                        onComplete(records.size)
                    }
                }.addOnFailureListener {
                    android.util.Log.e("Firestore", "Batch import failed", it)
                    // If one batch fails, we should still notify completion for the others
                }
            }
            
            if (chunks.isEmpty()) onComplete(0)
        }
    }

    /**
     * Clears all local and cloud data strictly for the currently authenticated user.
     * Records belonging to followed/shared users are NOT affected.
     */
    fun clearAllData() {
        val uid = auth.currentUser?.uid ?: return
        android.util.Log.d("GlucoseApp", "Starting clearAllData for user: $uid")
        viewModelScope.launch {
            // 1. Clear local (explicitly ONLY records and custom foods)
            dao.deleteAllGlucoseRecordsForUser(uid)
            customFoods.value = emptyList()
            prefs.edit().remove("custom_foods").apply()
            android.util.Log.d("GlucoseApp", "Local glucose records and custom foods deleted for $uid")
            triggerWidgetUpdate()
            
            // 2. Clear Cloud (Firestore) glucose_records
            firestore.collection("glucose_records")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    android.util.Log.d("GlucoseApp", "Firestore found ${snapshot.size()} records to delete")
                    val batch = firestore.batch()
                    snapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().addOnSuccessListener {
                        android.util.Log.d("GlucoseApp", "Firestore clear committed")
                    }
                }

            // 3. Clear Cloud (Firestore) custom_foods
            firestore.collection("custom_foods")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    android.util.Log.d("GlucoseApp", "Firestore found ${snapshot.size()} custom foods to delete")
                    val batch = firestore.batch()
                    snapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().addOnSuccessListener {
                        android.util.Log.d("GlucoseApp", "Firestore custom foods clear committed")
                    }
                }
        }
    }

    fun refreshData(onComplete: () -> Unit) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: ""
            if (uid.isNotEmpty()) {
                auth.currentUser?.let { updateUserProfile(it) }
                startCloudToLocalSync(uid)
                startCustomFoodsSync(uid)
            }
            
            // Re-trigger the current userId to restart flatMapLatest flows
            val current = currentUserId.value
            currentUserId.value = ""
            kotlinx.coroutines.delay(100)
            currentUserId.value = current
            
            onComplete()
        }
    }

    fun loadFoodsList(context: Context): List<FoodItem> {
        val foods = mutableListOf<FoodItem>()
        try {
            val jsonString = context.resources.openRawResource(R.raw.foods).bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                foods.add(
                    FoodItem(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        measure = obj.getString("measure"),
                        grams = obj.getDouble("grams").toFloat(),
                        carbs = obj.getDouble("carbs").toFloat(),
                        calories = obj.getDouble("calories").toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("GlucoseApp", "Error reading foods.json", e)
        }
        return foods
    }
}
