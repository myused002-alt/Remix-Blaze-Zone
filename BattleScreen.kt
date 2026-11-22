package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.GameMode
import com.example.data.SurvivorCharacter
import com.example.data.WeaponSkin
import com.example.game.BattleArenaCanvas
import com.example.game.BattleEngine
import com.example.game.BattleState
import com.example.game.LootKind
import com.example.game.MatchPhase
import com.example.game.MatchResultSummary
import com.example.ui.theme.BlazeGold
import com.example.ui.theme.BlazeOrange
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.EmeraldHeal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ZoneCyan
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun BattleScreen(
    mode: GameMode,
    callsign: String,
    character: SurvivorCharacter,
    skin: WeaponSkin,
    onMatchFinished: (MatchResultSummary) -> Unit,
    onAbandonMatch: () -> Unit
) {
    val engine = remember(mode, callsign, character, skin) {
        BattleEngine(mode, callsign, character, skin)
    }
    var battleState by remember(engine) { mutableStateOf(engine.getState()) }
    var showExitDialog by remember { mutableStateOf(false) }
    var reportedSummary by remember(engine) { mutableStateOf(false) }

    BackHandler {
        showExitDialog = true
    }

    LaunchedEffect(engine) {
        var lastFrameNs = 0L
        while (true) {
            withFrameNanos { frameNs ->
                if (lastFrameNs != 0L) {
                    val dt = (frameNs - lastFrameNs) / 1_000_000_000f
                    engine.step(dt)
                    val latest = engine.getState()
                    battleState = latest
                    if (latest.phase == MatchPhase.FINISHED && !reportedSummary) {
                        val summary = latest.resultSummary
                        if (summary != null) {
                            reportedSummary = true
                            onMatchFinished(summary)
                        }
                    }
                }
                lastFrameNs = frameNs
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A2239))
    ) {
        // 1. Live 60fps Top-Down Island Arena
        BattleArenaCanvas(state = battleState)

        // 2. HUD Overlay respecting safe areas (notches & nav pill)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
        ) {
            // Top Row: Radar Minimap + Zone Timer (Left) | Alive + Kills + Kill Feed + Exit (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TacticalMinimap(state = battleState)
                    Surface(
                        color = ObsidianBg.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (battleState.zoneShrinking) CrimsonDanger else ZoneCyan.copy(alpha = 0.55f)
                        )
                    ) {
                        val sec = battleState.zoneTimerSec.toInt().coerceAtLeast(0)
                        val label = if (battleState.zoneShrinking) "SHRINKING" else "ZONE ${battleState.zonePhase}"
                        Text(
                            text = "$label • 0:${sec.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (battleState.zoneShrinking) CrimsonDanger else ZoneCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HudStatPill(
                            label = "ALIVE",
                            value = "${battleState.aliveCount}",
                            accent = Color.White
                        )
                        HudStatPill(
                            label = "KILLS",
                            value = "${battleState.playerKills}",
                            accent = BlazeOrange
                        )
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.8f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            IconButton(
                                onClick = { showExitDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("pause_exit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Leave Match",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Live Kill Feed
                    battleState.killFeed.forEach { entry ->
                        Surface(
                            color = if (entry.involvesPlayer) {
                                BlazeOrange.copy(alpha = 0.85f)
                            } else {
                                ObsidianBg.copy(alpha = 0.72f)
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${entry.killer} [${entry.weapon}] ${entry.victim}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Center Banners (Airdrop / Elimination / Drop Plane Eject)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 66.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = battleState.airdropWarningTtl > 0f,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = BlazeGold.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "AIRDROP INCOMING — CHECK RADAR",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = battleState.eliminationBanner != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = BlazeOrange.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = battleState.eliminationBanner ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Drop Plane / Parachute Overlay
            if (battleState.phase == MatchPhase.DROP_PLANE) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = ObsidianBg.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "FLIGHT PATH ACTIVE — CHOOSE YOUR DROP ZONE",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Button(
                        onClick = { engine.ejectFromPlane() },
                        modifier = Modifier
                            .height(58.dp)
                            .testTag("eject_button"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BlazeOrange,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Filled.FlightTakeoff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EJECT & PARACHUTE", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else if (battleState.phase == MatchPhase.PARACHUTE) {
                Surface(
                    color = ObsidianBg.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 120.dp)
                ) {
                    Text(
                        text = "USE LEFT STICK TO STEER PARACHUTE",
                        style = MaterialTheme.typography.labelMedium,
                        color = BlazeGold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Controls & Player Vitals
            val player = battleState.player
            if (player != null && player.alive && battleState.phase != MatchPhase.DROP_PLANE) {
                // Left Virtual Movement Joystick
                VirtualJoystick(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 10.dp)
                        .testTag("move_joystick"),
                    ringColor = Color.White.copy(alpha = 0.45f),
                    knobColor = BlazeOrange,
                    onVectorChanged = { dx, dy -> engine.setMoveInput(dx, dy) }
                )

                if (battleState.phase == MatchPhase.COMBAT) {
                    // Bottom Center: Swap Loot Pill + HP/Armor + Weapon Slots
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .width(208.dp)
                            .padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val nearby = battleState.nearbyWeaponLoot
                        if (nearby?.weaponType != null) {
                            Surface(
                                color = BlazeGold,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable { engine.swapWithNearbyWeapon() }
                                    .testTag("swap_weapon_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.SwapHoriz,
                                        contentDescription = "Swap Weapon",
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "EQUIP ${nearby.weaponType.displayName.uppercase()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        // Reloading / Healing indicator
                        if (player.reloadTimerSec > 0f) {
                            Text(
                                text = "RELOADING...",
                                style = MaterialTheme.typography.labelMedium,
                                color = BlazeGold
                            )
                        } else if (player.healTimerSec > 0f) {
                            Text(
                                text = "APPLYING MEDKIT...",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldHeal
                            )
                        }

                        // Armor & HP Bars
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (player.armor > 0f) {
                                    LinearProgressIndicator(
                                        progress = { (player.armor / player.maxArmor).coerceIn(0f, 1f) },
                                        color = Color(0xFF60A5FA),
                                        trackColor = Color(0x33FFFFFF),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (player.hp / player.maxHp).coerceIn(0f, 1f) },
                                    color = if (player.hp > 60f) EmeraldHeal else CrimsonDanger,
                                    trackColor = Color(0x33FFFFFF),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "HP ${player.hp.toInt()}/${player.maxHp.toInt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "ARM ${player.armor.toInt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF93C5FD)
                                    )
                                }
                            }
                        }

                        // Dual Weapon Slots
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WeaponHudCard(
                                label = player.primarySlot.type.shortCode,
                                ammo = "${player.primarySlot.ammoInMag}/${player.primarySlot.reserveAmmo}",
                                active = player.activeSlotIndex == 0,
                                accent = player.primarySlot.type.accentColor,
                                onClick = { engine.switchWeaponSlot(0) },
                                modifier = Modifier.weight(1f)
                            )
                            val sec = player.secondarySlot
                            WeaponHudCard(
                                label = sec?.type?.shortCode ?: "EMPTY",
                                ammo = if (sec != null) "${sec.ammoInMag}/${sec.reserveAmmo}" else "--",
                                active = player.activeSlotIndex == 1 && sec != null,
                                accent = sec?.type?.accentColor ?: Color.Gray,
                                onClick = { engine.switchWeaponSlot(1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Right Tactical Cluster: Gloo Wall, Medkit, Dash, Reload + Aim/Fire Stick
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TacticalActionButton(
                                icon = Icons.Filled.MedicalServices,
                                badge = "${player.medkits}",
                                label = "MED",
                                tint = EmeraldHeal,
                                enabled = player.medkits > 0 && player.hp < player.maxHp,
                                onClick = { engine.triggerMedkit() },
                                testTag = "medkit_button"
                            )
                            TacticalActionButton(
                                icon = Icons.Filled.Shield,
                                badge = "${player.glooWalls}",
                                label = "GLOO",
                                tint = ZoneCyan,
                                enabled = player.glooWalls > 0,
                                onClick = { engine.deployGlooWall() },
                                testTag = "gloo_wall_button"
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TacticalActionButton(
                                    icon = Icons.Filled.Refresh,
                                    badge = null,
                                    label = "RLD",
                                    tint = BlazeGold,
                                    enabled = player.reloadTimerSec <= 0f,
                                    onClick = { engine.triggerReload() },
                                    testTag = "reload_button"
                                )
                                TacticalActionButton(
                                    icon = Icons.Filled.Bolt,
                                    badge = if (player.dashCooldownSec > 0f) {
                                        "${player.dashCooldownSec.toInt() + 1}s"
                                    } else null,
                                    label = "DASH",
                                    tint = BlazeOrange,
                                    enabled = player.dashCooldownSec <= 0f,
                                    onClick = { engine.triggerDash() },
                                    testTag = "dash_button"
                                )
                            }

                            // Aim & Fire Dual-Stick Control
                            FireAimStick(
                                onAimFire = { dx, dy, firing ->
                                    engine.setAimFireInput(dx, dy, firing)
                                },
                                modifier = Modifier.testTag("fire_aim_stick")
                            )
                        }
                    }
                }
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Retreat from Island?") },
                text = {
                    Text("Leaving an active match forfeits placement points and current match rewards.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            onAbandonMatch()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                    ) {
                        Text("Leave Match")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Keep Fighting")
                    }
                }
            )
        }
    }
}

@Composable
private fun TacticalMinimap(state: BattleState) {
    Surface(
        color = ObsidianBg.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.28f)),
        modifier = Modifier.size(104.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val scale = size.width / state.worldSize
            // Island outline
            drawRect(
                color = Color(0xFF1E3A2A),
                topLeft = Offset(180f * scale, 180f * scale),
                size = androidx.compose.ui.geometry.Size(
                    (state.worldSize - 360f) * scale,
                    (state.worldSize - 360f) * scale
                )
            )
            // Safe zone & next zone
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = state.nextZoneRadius * scale,
                center = Offset(state.nextZoneX * scale, state.nextZoneY * scale),
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = if (state.zoneShrinking) CrimsonDanger else ZoneCyan,
                radius = state.safeZoneRadius * scale,
                center = Offset(state.safeZoneX * scale, state.safeZoneY * scale),
                style = Stroke(width = 2.5f)
            )
            // Airdrop crates
            state.lootItems.filter { it.kind == LootKind.AIRDROP_CRATE }.forEach { drop ->
                drawCircle(
                    color = BlazeGold,
                    radius = 4f,
                    center = Offset(drop.x * scale, drop.y * scale)
                )
            }
            // Player blip
            state.player?.let { p ->
                drawCircle(
                    color = BlazeOrange,
                    radius = 4.5f,
                    center = Offset(p.x * scale, p.y * scale)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5f,
                    center = Offset(p.x * scale, p.y * scale),
                    style = Stroke(width = 1.5f)
                )
            }
        }
    }
}

@Composable
private fun HudStatPill(label: String, value: String, accent: Color) {
    Surface(
        color = ObsidianBg.copy(alpha = 0.82f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = accent)
        }
    }
}

@Composable
private fun WeaponHudCard(
    label: String,
    ammo: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (active) accent.copy(alpha = 0.24f) else ObsidianBg.copy(alpha = 0.78f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (active) 1.5.dp else 1.dp,
            color = if (active) accent else Color.White.copy(alpha = 0.18f)
        ),
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) accent else Color.LightGray
            )
            Text(
                text = ammo,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun TacticalActionButton(
    icon: ImageVector,
    badge: String?,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = ObsidianBg.copy(alpha = if (enabled) 0.85f else 0.45f),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, if (enabled) tint else Color.Gray.copy(alpha = 0.4f)),
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) tint else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = badge ?: label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) Color.White else Color.Gray
                )
            }
        }
    }
}

@Composable
private fun VirtualJoystick(
    modifier: Modifier = Modifier,
    ringColor: Color,
    knobColor: Color,
    onVectorChanged: (Float, Float) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = 110f

    Box(
        modifier = modifier
            .size(124.dp)
            .clip(CircleShape)
            .background(ObsidianBg.copy(alpha = 0.45f))
            .border(2.dp, ringColor, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val center = Offset(size.width / 2f, size.height / 2f)

                    fun update(pos: Offset) {
                        val delta = pos - center
                        val dist = hypot(delta.x, delta.y)
                        val clamped = if (dist > maxRadiusPx) {
                            Offset(delta.x / dist * maxRadiusPx, delta.y / dist * maxRadiusPx)
                        } else delta
                        knobOffset = clamped
                        onVectorChanged(clamped.x / maxRadiusPx, clamped.y / maxRadiusPx)
                    }

                    update(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            knobOffset = Offset.Zero
                            onVectorChanged(0f, 0f)
                            break
                        }
                        update(change.position)
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                .size(46.dp)
                .clip(CircleShape)
                .background(knobColor.copy(alpha = 0.9f))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun FireAimStick(
    onAimFire: (Float, Float, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    val maxRadiusPx = 92f

    Box(
        modifier = modifier
            .size(108.dp)
            .clip(CircleShape)
            .background(
                if (active) CrimsonDanger.copy(alpha = 0.72f) else BlazeOrange.copy(alpha = 0.85f)
            )
            .border(2.5.dp, Color.White, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val center = Offset(size.width / 2f, size.height / 2f)
                    active = true

                    fun update(pos: Offset) {
                        val delta = pos - center
                        val dist = hypot(delta.x, delta.y)
                        val clamped = if (dist > maxRadiusPx) {
                            Offset(delta.x / dist * maxRadiusPx, delta.y / dist * maxRadiusPx)
                        } else delta
                        knobOffset = clamped
                        onAimFire(clamped.x / maxRadiusPx, clamped.y / maxRadiusPx, true)
                    }

                    update(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            active = false
                            knobOffset = Offset.Zero
                            onAimFire(0f, 0f, false)
                            break
                        }
                        update(change.position)
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.GpsFixed,
                contentDescription = "Fire and Aim",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
