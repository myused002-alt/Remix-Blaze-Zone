package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BlazeRepository
import com.example.data.GameMode
import com.example.data.MatchRecord
import com.example.data.UserProfile
import com.example.data.SurvivorCharacter
import com.example.data.WeaponSkin
import com.example.game.MatchResultSummary
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface BlazeDestination {
    data object Lobby : BlazeDestination
    data class InMatch(
        val mode: GameMode,
        val callsign: String,
        val character: SurvivorCharacter,
        val skin: WeaponSkin
    ) : BlazeDestination
    data class Summary(val summary: MatchResultSummary) : BlazeDestination
}

class BlazeViewModel(
    private val repository: BlazeRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(GameMode.CLASSIC)
    val selectedMode: StateFlow<GameMode> = _selectedMode.asStateFlow()

    private val _destination = MutableStateFlow<BlazeDestination>(BlazeDestination.Lobby)
    val destination: StateFlow<BlazeDestination> = _destination.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private var ensuredInitialProfile = false

    val profileState: StateFlow<UiState<UserProfile>> = repository.observeProfile(currentUserId)
        .onEach { profile ->
            if (profile == null && !ensuredInitialProfile) {
                ensuredInitialProfile = true
                val defaultName = Firebase.auth.currentUser?.displayName
                    ?.split(" ")
                    ?.firstOrNull()
                    ?: "BlazeStriker"
                repository.ensureProfileExists(currentUserId, defaultName)
            }
        }
        .map { profile ->
            if (profile != null) {
                UiState.Success(profile)
            } else {
                UiState.Loading
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load survivor profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val matchHistoryState: StateFlow<UiState<List<MatchRecord>>> =
        repository.observeMatchHistory(currentUserId)
            .map<List<MatchRecord>, UiState<List<MatchRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing match history", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load match history"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun selectMode(mode: GameMode) {
        _selectedMode.value = mode
    }

    fun startMatch(profile: UserProfile) {
        _destination.value = BlazeDestination.InMatch(
            mode = _selectedMode.value,
            callsign = profile.callsign,
            character = SurvivorCharacter.fromName(profile.selectedCharacter),
            skin = WeaponSkin.fromName(profile.selectedStarterSkin)
        )
    }

    fun abandonMatch() {
        _destination.value = BlazeDestination.Lobby
    }

    fun finishMatch(profile: UserProfile?, summary: MatchResultSummary) {
        _destination.value = BlazeDestination.Summary(summary)
        if (profile != null) {
            viewModelScope.launch {
                val record = MatchRecord(
                    userId = currentUserId,
                    mode = summary.mode.name,
                    placement = summary.placement.toLong(),
                    kills = summary.kills.toLong(),
                    damageDealt = summary.damageDealt.toLong(),
                    survivalSeconds = summary.survivalSeconds.toLong(),
                    rankPointsDelta = summary.rankPointsDelta.toLong(),
                    coinsEarned = summary.coinsEarned.toLong(),
                    victory = summary.victory
                )
                repository.recordMatchOutcome(currentUserId, profile, record)
                    .onFailure { err ->
                        _actionMessage.value = err.localizedMessage ?: "Failed to sync match result"
                    }
            }
        }
    }

    fun returnToLobby() {
        _destination.value = BlazeDestination.Lobby
    }

    fun updateCallsign(profile: UserProfile, newCallsign: String) {
        val trimmed = newCallsign.trim()
        if (trimmed.length !in 3..24) {
            _actionMessage.value = "Callsign must be 3 to 24 characters."
            return
        }
        viewModelScope.launch {
            repository.updateLoadout(
                userId = currentUserId,
                callsign = trimmed,
                character = SurvivorCharacter.fromName(profile.selectedCharacter),
                skin = WeaponSkin.fromName(profile.selectedStarterSkin),
                unlockedCharacters = profile.unlockedCharacters.ifEmpty { listOf(SurvivorCharacter.HAYATO.name) },
                goldCoins = profile.goldCoins
            ).onSuccess {
                _actionMessage.value = "Callsign updated to $trimmed"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Could not update callsign"
            }
        }
    }

    fun selectOrUnlockCharacter(profile: UserProfile, target: SurvivorCharacter) {
        val unlocked = profile.unlockedCharacters.toMutableSet()
        if (unlocked.isEmpty()) unlocked.add(SurvivorCharacter.HAYATO.name)

        if (target.name in unlocked) {
            viewModelScope.launch {
                repository.updateLoadout(
                    userId = currentUserId,
                    callsign = profile.callsign,
                    character = target,
                    skin = WeaponSkin.fromName(profile.selectedStarterSkin),
                    unlockedCharacters = unlocked.toList(),
                    goldCoins = profile.goldCoins
                ).onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to equip ${target.displayName}"
                }
            }
        } else {
            if (profile.goldCoins < target.unlockCost) {
                _actionMessage.value = "Need ${target.unlockCost - profile.goldCoins} more Gold Coins to unlock ${target.displayName}."
                return
            }
            unlocked.add(target.name)
            val remainingCoins = profile.goldCoins - target.unlockCost
            viewModelScope.launch {
                repository.updateLoadout(
                    userId = currentUserId,
                    callsign = profile.callsign,
                    character = target,
                    skin = WeaponSkin.fromName(profile.selectedStarterSkin),
                    unlockedCharacters = unlocked.toList(),
                    goldCoins = remainingCoins
                ).onSuccess {
                    _actionMessage.value = "${target.displayName} unlocked and equipped!"
                }.onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to unlock ${target.displayName}"
                }
            }
        }
    }

    fun selectWeaponSkin(profile: UserProfile, skin: WeaponSkin) {
        viewModelScope.launch {
            repository.updateLoadout(
                userId = currentUserId,
                callsign = profile.callsign,
                character = SurvivorCharacter.fromName(profile.selectedCharacter),
                skin = skin,
                unlockedCharacters = profile.unlockedCharacters.ifEmpty { listOf(SurvivorCharacter.HAYATO.name) },
                goldCoins = profile.goldCoins
            ).onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to equip ${skin.displayName}"
            }
        }
    }

    private companion object {
        const val TAG = "BlazeVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
