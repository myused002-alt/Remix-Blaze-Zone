package com.example.game

import androidx.compose.ui.graphics.Color
import com.example.data.GameMode
import com.example.data.SurvivorCharacter
import com.example.data.WeaponSkin

enum class WeaponType(
    val displayName: String,
    val shortCode: String,
    val damage: Int,
    val pellets: Int,
    val spreadRad: Float,
    val fireIntervalSec: Float,
    val range: Float,
    val bulletSpeed: Float,
    val magSize: Int,
    val reloadSec: Float,
    val accentColor: Color
) {
    USP(
        displayName = "USP Tactical",
        shortCode = "USP",
        damage = 26,
        pellets = 1,
        spreadRad = 0.05f,
        fireIntervalSec = 0.34f,
        range = 390f,
        bulletSpeed = 1050f,
        magSize = 12,
        reloadSec = 1.1f,
        accentColor = Color(0xFF94A3B8)
    ),
    MP40(
        displayName = "MP40 Blitz",
        shortCode = "MP40",
        damage = 19,
        pellets = 1,
        spreadRad = 0.11f,
        fireIntervalSec = 0.11f,
        range = 430f,
        bulletSpeed = 1150f,
        magSize = 24,
        reloadSec = 1.3f,
        accentColor = Color(0xFF38BDF8)
    ),
    M1887(
        displayName = "M1887 Breaker",
        shortCode = "M1887",
        damage = 22,
        pellets = 5,
        spreadRad = 0.24f,
        fireIntervalSec = 0.45f,
        range = 290f,
        bulletSpeed = 1000f,
        magSize = 2,
        reloadSec = 1.2f,
        accentColor = Color(0xFFFF6B00)
    ),
    SCAR_L(
        displayName = "SCAR Titan",
        shortCode = "SCAR",
        damage = 31,
        pellets = 1,
        spreadRad = 0.04f,
        fireIntervalSec = 0.17f,
        range = 600f,
        bulletSpeed = 1320f,
        magSize = 30,
        reloadSec = 1.5f,
        accentColor = Color(0xFFFFB800)
    ),
    AWM(
        displayName = "AWM Dragon",
        shortCode = "AWM",
        damage = 115,
        pellets = 1,
        spreadRad = 0.008f,
        fireIntervalSec = 0.92f,
        range = 880f,
        bulletSpeed = 1900f,
        magSize = 5,
        reloadSec = 1.9f,
        accentColor = Color(0xFFFF3B5C)
    )
}

data class WeaponSlot(
    val type: WeaponType,
    val ammoInMag: Int = type.magSize,
    val reserveAmmo: Int = type.magSize * 3
)

enum class LootKind {
    WEAPON,
    MEDKIT,
    GLOO_WALL,
    ARMOR_VEST,
    AMMO_PACK,
    AIRDROP_CRATE
}

data class LootItem(
    val id: Int,
    val x: Float,
    val y: Float,
    val kind: LootKind,
    val weaponType: WeaponType? = null,
    val amount: Int = 1
)

data class ObstacleRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val isBuilding: Boolean = false,
    val label: String? = null
)

data class TreeSpot(
    val x: Float,
    val y: Float,
    val radius: Float
)

data class PoiZone(
    val name: String,
    val x: Float,
    val y: Float
)

data class GlooWall(
    val id: Int,
    val x: Float,
    val y: Float,
    val angleRad: Float,
    val halfLength: Float = 56f,
    val hp: Float,
    val maxHp: Float,
    val ttlSec: Float = 12f
)

data class Bullet(
    val id: Int,
    val ownerId: Int,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Int,
    val remainingRange: Float,
    val color: Color,
    val weaponName: String,
    val isSniper: Boolean = false
)

data class Combatant(
    val id: Int,
    val name: String,
    val isPlayer: Boolean,
    val x: Float,
    val y: Float,
    val angleRad: Float = 0f,
    val hp: Float = 200f,
    val maxHp: Float = 200f,
    val armor: Float = 0f,
    val maxArmor: Float = 100f,
    val alive: Boolean = true,
    val parachuteAltitude: Float = 1f, // 1f in plane/high, 0f landed
    val airborne: Boolean = true,
    val primarySlot: WeaponSlot = WeaponSlot(WeaponType.USP),
    val secondarySlot: WeaponSlot? = null,
    val activeSlotIndex: Int = 0,
    val medkits: Int = 1,
    val glooWalls: Int = 2,
    val fireCooldownSec: Float = 0f,
    val reloadTimerSec: Float = 0f,
    val healTimerSec: Float = 0f,
    val dashTimerSec: Float = 0f,
    val dashCooldownSec: Float = 0f,
    val muzzleFlashSec: Float = 0f,
    val hitFlashSec: Float = 0f,
    val kills: Int = 0,
    val aiTargetId: Int? = null,
    val aiStrafeSign: Float = 1f,
    val aiDecisionTimer: Float = 0f
) {
    val activeWeapon: WeaponSlot
        get() = if (activeSlotIndex == 1 && secondarySlot != null) secondarySlot else primarySlot
}

data class FloatingDamage(
    val id: Int,
    val x: Float,
    val y: Float,
    val text: String,
    val isCrit: Boolean,
    val ttlSec: Float = 0.75f
)

data class KillFeedEntry(
    val id: Int,
    val killer: String,
    val victim: String,
    val weapon: String,
    val involvesPlayer: Boolean,
    val ttlSec: Float = 5.0f
)

data class HitDirectionIndicator(
    val angleFromPlayerRad: Float,
    val ttlSec: Float = 0.85f
)

enum class MatchPhase {
    DROP_PLANE,
    PARACHUTE,
    COMBAT,
    FINISHED
}

data class MatchResultSummary(
    val mode: GameMode,
    val placement: Int,
    val totalCombatants: Int,
    val kills: Int,
    val damageDealt: Int,
    val survivalSeconds: Int,
    val rankPointsDelta: Int,
    val coinsEarned: Int,
    val victory: Boolean
)

data class BattleState(
    val mode: GameMode,
    val character: SurvivorCharacter,
    val skin: WeaponSkin,
    val phase: MatchPhase = MatchPhase.DROP_PLANE,
    val worldSize: Float = 2400f,
    val elapsedSec: Float = 0f,
    val combatTimeSec: Float = 0f,
    val planeX: Float = 250f,
    val planeY: Float = 2150f,
    val planeEndX: Float = 2150f,
    val planeEndY: Float = 250f,
    val planeProgress: Float = 0f,
    val combatants: List<Combatant> = emptyList(),
    val obstacles: List<ObstacleRect> = emptyList(),
    val trees: List<TreeSpot> = emptyList(),
    val pois: List<PoiZone> = emptyList(),
    val lootItems: List<LootItem> = emptyList(),
    val bullets: List<Bullet> = emptyList(),
    val glooWalls: List<GlooWall> = emptyList(),
    val floatingDamages: List<FloatingDamage> = emptyList(),
    val killFeed: List<KillFeedEntry> = emptyList(),
    val hitIndicators: List<HitDirectionIndicator> = emptyList(),
    val safeZoneX: Float = 1200f,
    val safeZoneY: Float = 1200f,
    val safeZoneRadius: Float = 1150f,
    val nextZoneX: Float = 1200f,
    val nextZoneY: Float = 1200f,
    val nextZoneRadius: Float = 820f,
    val zonePhase: Int = 1,
    val zoneTimerSec: Float = 22f,
    val zoneShrinking: Boolean = false,
    val aliveCount: Int = 20,
    val playerKills: Int = 0,
    val playerDamage: Int = 0,
    val nearbyWeaponLoot: LootItem? = null,
    val eliminationBanner: String? = null,
    val eliminationBannerTtl: Float = 0f,
    val airdropWarningTtl: Float = 0f,
    val resultSummary: MatchResultSummary? = null
) {
    val player: Combatant?
        get() = combatants.firstOrNull { it.isPlayer }
}
