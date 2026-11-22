package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GameMode
import com.example.data.MatchRecord
import com.example.data.RankTier
import com.example.data.SurvivorCharacter
import com.example.data.UserProfile
import com.example.data.WeaponSkin
import com.example.game.WeaponType
import com.example.ui.theme.BlazeGold
import com.example.ui.theme.BlazeOrange
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ObsidianBg

@Composable
fun BlazeMainHub(
    profileState: UiState<UserProfile>,
    matchHistoryState: UiState<List<MatchRecord>>,
    selectedMode: GameMode,
    actionMessage: String?,
    onClearActionMessage: () -> Unit,
    onSelectMode: (GameMode) -> Unit,
    onDeployMatch: (UserProfile) -> Unit,
    onSelectOrUnlockCharacter: (UserProfile, SurvivorCharacter) -> Unit,
    onSelectWeaponSkin: (UserProfile, WeaponSkin) -> Unit,
    onUpdateCallsign: (UserProfile, String) -> Unit
) {
    var currentTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            snackbarHostState.showSnackbar(actionMessage)
            onClearActionMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.nav_lobby)) },
                            label = { Text(stringResource(R.string.nav_lobby)) },
                            modifier = Modifier.testTag("tab_lobby")
                        )
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = { Icon(Icons.Filled.Shield, contentDescription = stringResource(R.string.nav_armory)) },
                            label = { Text(stringResource(R.string.nav_armory)) },
                            modifier = Modifier.testTag("tab_armory")
                        )
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            icon = { Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_career)) },
                            label = { Text(stringResource(R.string.nav_career)) },
                            modifier = Modifier.testTag("tab_profile")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        NavigationRailItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.nav_lobby)) },
                            label = { Text(stringResource(R.string.nav_lobby)) }
                        )
                        NavigationRailItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = { Icon(Icons.Filled.Shield, contentDescription = stringResource(R.string.nav_armory)) },
                            label = { Text(stringResource(R.string.nav_armory)) }
                        )
                        NavigationRailItem(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            icon = { Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_career)) },
                            label = { Text(stringResource(R.string.nav_career)) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (profileState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = BlazeOrange)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "SYNCING DROP PROFILE...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Connection Alert",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = CrimsonDanger
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = profileState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is UiState.Success -> {
                            val profile = profileState.data
                            when (currentTab) {
                                0 -> LobbyScreen(
                                    profile = profile,
                                    selectedMode = selectedMode,
                                    onSelectMode = onSelectMode,
                                    onDeployMatch = { onDeployMatch(profile) },
                                    onOpenArmory = { currentTab = 1 },
                                    onOpenProfile = { currentTab = 2 }
                                )
                                1 -> ArmoryScreen(
                                    profile = profile,
                                    onSelectOrUnlockCharacter = { c -> onSelectOrUnlockCharacter(profile, c) },
                                    onSelectWeaponSkin = { s -> onSelectWeaponSkin(profile, s) },
                                    onUpdateCallsign = { name -> onUpdateCallsign(profile, name) }
                                )
                                else -> UserProfileScreen(
                                    profile = profile,
                                    matchHistoryState = matchHistoryState
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyScreen(
    profile: UserProfile,
    selectedMode: GameMode,
    onSelectMode: (GameMode) -> Unit,
    onDeployMatch: () -> Unit,
    onOpenArmory: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val character = SurvivorCharacter.fromName(profile.selectedCharacter)
    val skin = WeaponSkin.fromName(profile.selectedStarterSkin)
    val rank = RankTier.fromPoints(profile.rankPoints)

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed tactical key art backdrop
        Image(
            painter = painterResource(id = R.drawable.img_lobby_backdrop),
            contentDescription = "Blaze Zone Survivor Backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBg.copy(alpha = 0.72f),
                            ObsidianBg.copy(alpha = 0.28f),
                            ObsidianBg.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Callsign + Rank + Gold Coins
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ObsidianBg.copy(alpha = 0.84f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, rank.badgeColor.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable(onClick = onOpenProfile)
                        .testTag("lobby_profile_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(rank.badgeColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MilitaryTech,
                                contentDescription = rank.title,
                                tint = rank.badgeColor
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = profile.callsign,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${rank.title.uppercase()} • ${profile.rankPoints} RP",
                                style = MaterialTheme.typography.labelSmall,
                                color = rank.badgeColor
                            )
                        }
                    }
                }

                Surface(
                    color = ObsidianBg.copy(alpha = 0.84f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, BlazeGold.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MonetizationOn,
                            contentDescription = "Gold Coins",
                            tint = BlazeGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${profile.goldCoins}",
                            style = MaterialTheme.typography.titleLarge,
                            color = BlazeGold
                        )
                    }
                }
            }

            // Bottom Control Dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Equipped Survivor & Skin Card
                Surface(
                    color = ObsidianBg.copy(alpha = 0.86f),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, character.accentColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenArmory)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(character.accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = character.displayName,
                                    tint = character.accentColor
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${character.displayName.uppercase()} • ${character.skillName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "${character.skillDesc} • ${skin.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                        Text(
                            text = "CHANGE",
                            style = MaterialTheme.typography.labelMedium,
                            color = BlazeOrange,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Mode Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GameMode.entries.forEach { mode ->
                        val selected = mode == selectedMode
                        Surface(
                            color = if (selected) {
                                BlazeOrange.copy(alpha = 0.22f)
                            } else {
                                ObsidianBg.copy(alpha = 0.82f)
                            },
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) BlazeOrange else Color.White.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectMode(mode) }
                                .testTag("mode_${mode.name.lowercase()}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = mode.displayName.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) BlazeOrange else Color.White
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Primary Deploy Button
                Button(
                    onClick = onDeployMatch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("deploy_match_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlazeOrange,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.deploy_match),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ArmoryScreen(
    profile: UserProfile,
    onSelectOrUnlockCharacter: (SurvivorCharacter) -> Unit,
    onSelectWeaponSkin: (WeaponSkin) -> Unit,
    onUpdateCallsign: (String) -> Unit
) {
    var callsignDraft by remember(profile.callsign) { mutableStateOf(profile.callsign) }
    val equippedChar = SurvivorCharacter.fromName(profile.selectedCharacter)
    val equippedSkin = WeaponSkin.fromName(profile.selectedStarterSkin)
    val unlockedSet = profile.unlockedCharacters.toSet().ifEmpty { setOf(SurvivorCharacter.HAYATO.name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ARMORY & SURVIVORS",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Surface(
                color = BlazeGold.copy(alpha = 0.16f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BlazeGold.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.MonetizationOn,
                        contentDescription = null,
                        tint = BlazeGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${profile.goldCoins}",
                        style = MaterialTheme.typography.labelLarge,
                        color = BlazeGold
                    )
                }
            }
        }

        // Callsign Editor
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "COMBAT CALLSIGN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = callsignDraft,
                        onValueChange = { if (it.length <= 24) callsignDraft = it },
                        singleLine = true,
                        label = { Text("3-24 characters") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("callsign_input")
                    )
                    Button(
                        onClick = { onUpdateCallsign(callsignDraft) },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("save_callsign_button")
                    ) {
                        Text("SAVE")
                    }
                }
            }
        }

        // Survivors Section
        Text(
            text = "TACTICAL SURVIVORS",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        SurvivorCharacter.entries.forEach { character ->
            val isUnlocked = character.name in unlockedSet
            val isEquipped = character == equippedChar
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isEquipped) {
                        character.accentColor.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                border = BorderStroke(
                    width = if (isEquipped) 1.5.dp else 1.dp,
                    color = if (isEquipped) character.accentColor else Color.White.copy(alpha = 0.1f)
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectOrUnlockCharacter(character) }
                    .testTag("character_card_${character.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${character.displayName.uppercase()} • ${character.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = character.accentColor
                        )
                        Text(
                            text = "${character.skillName}: ${character.skillDesc}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    when {
                        isEquipped -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Equipped",
                                tint = character.accentColor
                            )
                        }
                        isUnlocked -> {
                            Text(
                                text = "EQUIP",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                        else -> {
                            Surface(
                                color = BlazeGold.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Locked",
                                        tint = BlazeGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${character.unlockCost}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BlazeGold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Weapon Tracer Skins
        Text(
            text = "BULLET TRACER SKINS",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WeaponSkin.entries.forEach { skin ->
                val selected = skin == equippedSkin
                Surface(
                    color = if (selected) skin.tracerColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) skin.tracerColor else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectWeaponSkin(skin) }
                        .testTag("skin_${skin.name.lowercase()}")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(skin.tracerColor)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = skin.displayName.split(" ").last().uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Island Weapons Field Guide
        Text(
            text = "ISLAND ARSENAL SPECS",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        listOf(WeaponType.M1887, WeaponType.MP40, WeaponType.SCAR_L, WeaponType.AWM).forEach { w ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FlashOn, contentDescription = null, tint = w.accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(w.displayName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                "DMG ${w.damage * w.pellets} • MAG ${w.magSize} • RNG ${w.range.toInt()}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = w.shortCode,
                        style = MaterialTheme.typography.labelLarge,
                        color = w.accentColor
                    )
                }
            }
        }
    }
}
