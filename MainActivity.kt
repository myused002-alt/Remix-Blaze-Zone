package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BlazeRepository
import com.example.ui.AuthScreen
import com.example.ui.BattleScreen
import com.example.ui.BlazeDestination
import com.example.ui.BlazeMainHub
import com.example.ui.BlazeViewModel
import com.example.ui.MatchSummaryScreen
import com.example.ui.UiState
import com.example.ui.theme.BlazeZoneTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlazeZoneTheme {
                BlazeAppContent()
            }
        }
    }
}

@Composable
fun BlazeAppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            AuthenticatedBlazeApp(currentUserId = user.uid)
        }
    }
}

@Composable
fun AuthenticatedBlazeApp(currentUserId: String) {
    val viewModel: BlazeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BlazeViewModel(BlazeRepository(db), currentUserId)
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val matchHistoryState by viewModel.matchHistoryState.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val currentProfile = (profileState as? UiState.Success)?.data

    when (val dest = destination) {
        is BlazeDestination.Lobby -> {
            BlazeMainHub(
                profileState = profileState,
                matchHistoryState = matchHistoryState,
                selectedMode = selectedMode,
                actionMessage = actionMessage,
                onClearActionMessage = viewModel::clearActionMessage,
                onSelectMode = viewModel::selectMode,
                onDeployMatch = viewModel::startMatch,
                onSelectOrUnlockCharacter = viewModel::selectOrUnlockCharacter,
                onSelectWeaponSkin = viewModel::selectWeaponSkin,
                onUpdateCallsign = viewModel::updateCallsign
            )
        }
        is BlazeDestination.InMatch -> {
            BattleScreen(
                mode = dest.mode,
                callsign = dest.callsign,
                character = dest.character,
                skin = dest.skin,
                onMatchFinished = { summary ->
                    viewModel.finishMatch(currentProfile, summary)
                },
                onAbandonMatch = viewModel::abandonMatch
            )
        }
        is BlazeDestination.Summary -> {
            MatchSummaryScreen(
                summary = dest.summary,
                onPlayAgain = {
                    if (currentProfile != null) {
                        viewModel.startMatch(currentProfile)
                    } else {
                        viewModel.returnToLobby()
                    }
                },
                onReturnToLobby = viewModel::returnToLobby
            )
        }
    }
}
