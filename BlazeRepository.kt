package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BlazeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMatchHistory(userId: String): Flow<List<MatchRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/matches"
        emitAll(
            db.collection("users").document(uid).collection("matches")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MatchRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureProfileExists(userId: String, defaultCallsign: String): Result<Unit> {
        var docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val sanitizedCallsign = defaultCallsign
                    .replace(Regex("[^a-zA-Z0-9_ ]"), "")
                    .trim()
                    .take(20)
                    .let { if (it.length < 3) "BlazeStriker" else it }

                val initialProfile = UserProfile(
                    userId = uid,
                    callsign = sanitizedCallsign,
                    rankPoints = 1000L,
                    totalMatches = 0L,
                    booyahs = 0L,
                    totalKills = 0L,
                    totalDamage = 0L,
                    goldCoins = 350L,
                    selectedCharacter = SurvivorCharacter.HAYATO.name,
                    selectedStarterSkin = WeaponSkin.SCORCH.name,
                    unlockedCharacters = listOf(SurvivorCharacter.HAYATO.name)
                )
                docRef.set(initialProfile.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateLoadout(
        userId: String,
        callsign: String,
        character: SurvivorCharacter,
        skin: WeaponSkin,
        unlockedCharacters: List<String>,
        goldCoins: Long
    ): Result<Unit> {
        var docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val trimmedCallsign = callsign.trim()
            require(trimmedCallsign.length in 3..24) { "Callsign must be 3-24 characters." }
            require(unlockedCharacters.isNotEmpty() && unlockedCharacters.size <= 10) {
                "Invalid unlocked character list."
            }
            require(goldCoins >= 0L) { "Gold coins cannot be negative." }

            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "callsign" to trimmedCallsign,
                    "selectedCharacter" to character.name,
                    "selectedStarterSkin" to skin.name,
                    "unlockedCharacters" to unlockedCharacters.distinct().take(10),
                    "goldCoins" to goldCoins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordMatchOutcome(
        userId: String,
        currentProfile: UserProfile,
        match: MatchRecord
    ): Result<Unit> {
        var docPath = "users/$userId/matches"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }

            val userRef = db.collection("users").document(uid)
            val matchRef = userRef.collection("matches").document()
            docPath = matchRef.path

            val safePlacement = match.placement.coerceIn(1L, 20L)
            val safeKills = match.kills.coerceIn(0L, 25L)
            val safeDamage = match.damageDealt.coerceIn(0L, 50000L)
            val safeSurvival = match.survivalSeconds.coerceIn(0L, 3600L)
            val safeDelta = match.rankPointsDelta.coerceIn(-100L, 250L)
            val safeCoins = match.coinsEarned.coerceIn(0L, 2000L)

            val matchPayload = MatchRecord(
                userId = uid,
                mode = GameMode.fromName(match.mode).name,
                placement = safePlacement,
                kills = safeKills,
                damageDealt = safeDamage,
                survivalSeconds = safeSurvival,
                rankPointsDelta = safeDelta,
                coinsEarned = safeCoins,
                victory = safePlacement == 1L
            ).toCreateMap()

            val nextRp = (currentProfile.rankPoints + safeDelta).coerceIn(0L, 1_000_000L)
            val nextMatches = (currentProfile.totalMatches + 1L).coerceAtMost(1_000_000L)
            val nextBooyahs = (currentProfile.booyahs + if (safePlacement == 1L) 1L else 0L)
                .coerceAtMost(1_000_000L)
            val nextKills = (currentProfile.totalKills + safeKills).coerceAtMost(10_000_000L)
            val nextDamage = (currentProfile.totalDamage + safeDamage).coerceAtMost(1_000_000_000L)
            val nextCoins = (currentProfile.goldCoins + safeCoins).coerceAtMost(10_000_000L)

            val batch = db.batch()
            batch.set(matchRef, matchPayload)
            batch.update(
                userRef,
                mapOf(
                    "rankPoints" to nextRp,
                    "totalMatches" to nextMatches,
                    "booyahs" to nextBooyahs,
                    "totalKills" to nextKills,
                    "totalDamage" to nextDamage,
                    "goldCoins" to nextCoins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
