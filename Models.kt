package com.example.data

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SurvivorCharacter(
    val displayName: String,
    val title: String,
    val skillName: String,
    val skillDesc: String,
    val unlockCost: Long,
    val accentColor: Color,
    val moveSpeedMultiplier: Float = 1f,
    val damageBonusMultiplier: Float = 1f,
    val startingGlooWalls: Int = 2,
    val regenPerTwoSec: Int = 0,
    val maxArmorBonus: Int = 0
) {
    HAYATO(
        displayName = "Hayato",
        title = "Blaze Samurai",
        skillName = "Bushido Edge",
        skillDesc = "+25 Max Armor & +12% close-range damage",
        unlockCost = 0L,
        accentColor = Color(0xFFFF6B00),
        damageBonusMultiplier = 1.12f,
        maxArmorBonus = 25
    ),
    KELLY(
        displayName = "Kelly",
        title = "Swift Sprinter",
        skillName = "Dash Velocity",
        skillDesc = "+18% sprint speed across the island",
        unlockCost = 300L,
        accentColor = Color(0xFFFFB800),
        moveSpeedMultiplier = 1.18f
    ),
    ALOK(
        displayName = "Alok",
        title = "Pulse Medic",
        skillName = "Drop the Beat",
        skillDesc = "Regenerates +3 HP every 2s during combat",
        unlockCost = 600L,
        accentColor = Color(0xFF00E676),
        regenPerTwoSec = 3
    ),
    CHRONO(
        displayName = "Chrono",
        title = "Vanguard Shield",
        skillName = "Time Turner",
        skillDesc = "Starts with 4 Gloo Walls (+2 extra shields)",
        unlockCost = 900L,
        accentColor = Color(0xFF00E5FF),
        startingGlooWalls = 4
    );

    companion object {
        fun fromName(raw: String): SurvivorCharacter =
            entries.firstOrNull { it.name == raw } ?: HAYATO
    }
}

enum class WeaponSkin(
    val displayName: String,
    val subtitle: String,
    val tracerColor: Color
) {
    SCORCH("Molten Scorch", "Inferno Orange Tracers", Color(0xFFFF6B00)),
    CYBER("Neon Cyber", "Electric Cyan Tracers", Color(0xFF00E5FF)),
    SHADOW("Void Shadow", "Crimson Plasma Tracers", Color(0xFFFF3B5C)),
    VIPER("Venom Viper", "Toxic Emerald Tracers", Color(0xFF00E676));

    companion object {
        fun fromName(raw: String): WeaponSkin =
            entries.firstOrNull { it.name == raw } ?: SCORCH
    }
}

enum class GameMode(
    val displayName: String,
    val subtitle: String,
    val totalCombatants: Int,
    val zoneSpeedMultiplier: Float
) {
    CLASSIC("Classic BR", "20 Survivors • Full Island Drop", 20, 1.0f),
    CLASH_RUSH("Clash Rush", "10 Survivors • Rapid Storm", 10, 1.45f);

    companion object {
        fun fromName(raw: String): GameMode =
            entries.firstOrNull { it.name == raw } ?: CLASSIC
    }
}

enum class RankTier(val title: String, val minRp: Long, val badgeColor: Color) {
    BRONZE("Bronze", 0L, Color(0xFFCD7F32)),
    SILVER("Silver", 1100L, Color(0xFFC0C7D6)),
    GOLD("Gold", 1300L, Color(0xFFFFB800)),
    PLATINUM("Platinum", 1600L, Color(0xFF38BDF8)),
    DIAMOND("Diamond", 2000L, Color(0xFFA855F7)),
    HEROIC("Heroic", 2500L, Color(0xFFFF3B5C)),
    GRANDMASTER("Grandmaster", 3200L, Color(0xFFFF6B00));

    companion object {
        fun fromPoints(rp: Long): RankTier =
            entries.lastOrNull { rp >= it.minRp } ?: BRONZE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "BlazeStriker",
    val rankPoints: Long = 1000L,
    val totalMatches: Long = 0L,
    val booyahs: Long = 0L,
    val totalKills: Long = 0L,
    val totalDamage: Long = 0L,
    val goldCoins: Long = 350L,
    val selectedCharacter: String = SurvivorCharacter.HAYATO.name,
    val selectedStarterSkin: String = WeaponSkin.SCORCH.name,
    val unlockedCharacters: List<String> = listOf(SurvivorCharacter.HAYATO.name),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val matchesPlayed: Long
        get() = totalMatches

    @get:Exclude
    val winRatePercent: Float
        get() = if (totalMatches > 0L) (booyahs.toFloat() / totalMatches.toFloat()) * 100f else 0f

    @get:Exclude
    val formattedWinRate: String
        get() = if (totalMatches > 0L) String.format("%.1f%%", winRatePercent) else "0.0%"

    @get:Exclude
    val kdRatio: String
        get() = if (totalMatches > 0L) {
            val deaths = (totalMatches - booyahs).coerceAtLeast(1L)
            String.format("%.2f", totalKills.toDouble() / deaths.toDouble())
        } else "0.00"

    @get:Exclude
    val avgDamagePerMatch: Long
        get() = if (totalMatches > 0L) totalDamage / totalMatches else 0L

    @get:Exclude
    val rankTier: RankTier
        get() = RankTier.fromPoints(rankPoints)

    @get:Exclude
    val character: SurvivorCharacter
        get() = SurvivorCharacter.fromName(selectedCharacter)

    @get:Exclude
    val weaponSkin: WeaponSkin
        get() = WeaponSkin.fromName(selectedStarterSkin)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "callsign" to callsign,
        "rankPoints" to rankPoints,
        "totalMatches" to totalMatches,
        "booyahs" to booyahs,
        "totalKills" to totalKills,
        "totalDamage" to totalDamage,
        "goldCoins" to goldCoins,
        "selectedCharacter" to selectedCharacter,
        "selectedStarterSkin" to selectedStarterSkin,
        "unlockedCharacters" to unlockedCharacters,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class MatchRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = GameMode.CLASSIC.name,
    val placement: Long = 1L,
    val kills: Long = 0L,
    val damageDealt: Long = 0L,
    val survivalSeconds: Long = 0L,
    val rankPointsDelta: Long = 0L,
    val coinsEarned: Long = 0L,
    val victory: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "mode" to mode,
        "placement" to placement,
        "kills" to kills,
        "damageDealt" to damageDealt,
        "survivalSeconds" to survivalSeconds,
        "rankPointsDelta" to rankPointsDelta,
        "coinsEarned" to coinsEarned,
        "victory" to victory,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
