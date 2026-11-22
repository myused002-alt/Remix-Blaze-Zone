package com.example.game

import androidx.compose.ui.graphics.Color
import com.example.data.GameMode
import com.example.data.SurvivorCharacter
import com.example.data.WeaponSkin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class BattleEngine(
    private val mode: GameMode,
    private val playerCallsign: String,
    private val character: SurvivorCharacter,
    private val skin: WeaponSkin
) {
    companion object {
        const val WORLD_SIZE = 2400f
        const val PLAYER_RADIUS = 19f
        const val BASE_SPEED = 198f
        const val PARACHUTE_STEER_SPEED = 240f
        const val GLOO_WALL_DISTANCE = 64f

        private val BOT_NAMES = listOf(
            "ViperX", "Kobra99", "ShadowFox", "BlazeKai", "NovaStrike",
            "GhostOps", "ApexWolf", "CrimsonAce", "TitanZero", "RazeByte",
            "SniperVex", "Phantom7", "StormLynx", "IronFang", "EchoDrift",
            "VenomJay", "VoltKuro", "FuryRex", "Solaris"
        )
    }

    private var nextId = 100
    private fun allocId(): Int = nextId++

    private val obstacles = buildMapObstacles()
    private val trees = buildTrees()
    private val pois = listOf(
        PoiZone("PEAK CITADEL", 1200f, 1120f),
        PoiZone("CLOCK TOWER", 720f, 760f),
        PoiZone("FACTORY YARD", 1740f, 780f),
        PoiZone("RIM VILLAGE", 680f, 1680f),
        PoiZone("SHIPYARD DOCKS", 1720f, 1690f)
    )

    private var state: BattleState
    private var moveInputX = 0f
    private var moveInputY = 0f
    private var aimInputX = 0f
    private var aimInputY = 0f
    private var isFiring = false
    private var nextAirdropAtSec = 26f
    private var regenAccumSec = 0f
    private var zoneDamageAccumSec = 0f

    init {
        val startX = 240f + Random.nextFloat() * 180f
        val startY = 2050f + Random.nextFloat() * 150f
        val endX = 2000f + Random.nextFloat() * 180f
        val endY = 240f + Random.nextFloat() * 180f

        val playerCombatant = Combatant(
            id = 1,
            name = playerCallsign,
            isPlayer = true,
            x = startX,
            y = startY,
            hp = 200f,
            maxHp = 200f,
            armor = 0f,
            maxArmor = 100f + character.maxArmorBonus,
            glooWalls = character.startingGlooWalls,
            medkits = 1,
            primarySlot = WeaponSlot(WeaponType.USP, 12, 36)
        )

        val botCount = mode.totalCombatants - 1
        val shuffledNames = BOT_NAMES.shuffled()
        val bots = (0 until botCount).map { idx ->
            val t = 0.14f + (idx.toFloat() / botCount) * 0.72f
            val bx = lerp(startX, endX, t) + (Random.nextFloat() - 0.5f) * 420f
            val by = lerp(startY, endY, t) + (Random.nextFloat() - 0.5f) * 420f
            val botWeapon = when (idx % 4) {
                0 -> WeaponType.MP40
                1 -> WeaponType.SCAR_L
                2 -> WeaponType.M1887
                else -> WeaponType.USP
            }
            Combatant(
                id = 10 + idx,
                name = shuffledNames[idx % shuffledNames.size],
                isPlayer = false,
                x = bx.coerceIn(240f, WORLD_SIZE - 240f),
                y = by.coerceIn(240f, WORLD_SIZE - 240f),
                hp = 165f,
                maxHp = 165f,
                armor = if (idx % 3 == 0) 40f else 0f,
                maxArmor = 100f,
                airborne = true,
                parachuteAltitude = 0.85f - Random.nextFloat() * 0.35f,
                primarySlot = WeaponSlot(botWeapon, botWeapon.magSize, botWeapon.magSize * 4),
                medkits = 1,
                glooWalls = 1,
                aiStrafeSign = if (idx % 2 == 0) 1f else -1f
            )
        }

        val initialTargetX = 1200f + (Random.nextFloat() - 0.5f) * 280f
        val initialTargetY = 1200f + (Random.nextFloat() - 0.5f) * 280f

        state = BattleState(
            mode = mode,
            character = character,
            skin = skin,
            phase = MatchPhase.DROP_PLANE,
            worldSize = WORLD_SIZE,
            planeX = startX,
            planeY = startY,
            planeEndX = endX,
            planeEndY = endY,
            combatants = listOf(playerCombatant) + bots,
            obstacles = obstacles,
            trees = trees,
            pois = pois,
            lootItems = spawnInitialLoot(),
            safeZoneX = 1200f,
            safeZoneY = 1200f,
            safeZoneRadius = if (mode == GameMode.CLASH_RUSH) 920f else 1120f,
            nextZoneX = initialTargetX,
            nextZoneY = initialTargetY,
            nextZoneRadius = if (mode == GameMode.CLASH_RUSH) 580f else 740f,
            zoneTimerSec = if (mode == GameMode.CLASH_RUSH) 14f else 22f,
            aliveCount = mode.totalCombatants
        )
    }

    fun getState(): BattleState = state

    fun setMoveInput(dx: Float, dy: Float) {
        val len = hypot(dx, dy)
        if (len > 1f) {
            moveInputX = dx / len
            moveInputY = dy / len
        } else {
            moveInputX = dx
            moveInputY = dy
        }
    }

    fun setAimFireInput(dx: Float, dy: Float, firing: Boolean) {
        aimInputX = dx
        aimInputY = dy
        isFiring = firing
    }

    fun ejectFromPlane() {
        if (state.phase != MatchPhase.DROP_PLANE) return
        val updated = state.combatants.map { c ->
            if (c.isPlayer) {
                c.copy(
                    x = state.planeX,
                    y = state.planeY,
                    airborne = true,
                    parachuteAltitude = 1f
                )
            } else c
        }
        state = state.copy(
            phase = MatchPhase.PARACHUTE,
            combatants = updated
        )
    }

    fun switchWeaponSlot(slotIndex: Int) {
        val player = state.player ?: return
        if (!player.alive || state.phase != MatchPhase.COMBAT) return
        if (slotIndex == 1 && player.secondarySlot == null) return
        updatePlayer { it.copy(activeSlotIndex = slotIndex, reloadTimerSec = 0f, healTimerSec = 0f) }
    }

    fun swapWithNearbyWeapon() {
        val player = state.player ?: return
        val nearby = state.nearbyWeaponLoot ?: return
        val newWeaponType = nearby.weaponType ?: return
        if (!player.alive || state.phase != MatchPhase.COMBAT) return

        val newSlot = WeaponSlot(newWeaponType, newWeaponType.magSize, newWeaponType.magSize * 3)
        val remainingLoot = state.lootItems.filterNot { it.id == nearby.id }.toMutableList()

        val updatedPlayer = if (player.secondarySlot == null) {
            player.copy(secondarySlot = newSlot, activeSlotIndex = 1, reloadTimerSec = 0f)
        } else {
            val droppedType = player.activeWeapon.type
            remainingLoot += LootItem(
                id = allocId(),
                x = player.x + 28f,
                y = player.y + 18f,
                kind = LootKind.WEAPON,
                weaponType = droppedType
            )
            if (player.activeSlotIndex == 1) {
                player.copy(secondarySlot = newSlot, reloadTimerSec = 0f)
            } else {
                player.copy(primarySlot = newSlot, reloadTimerSec = 0f)
            }
        }
        state = state.copy(
            combatants = state.combatants.map { if (it.isPlayer) updatedPlayer else it },
            lootItems = remainingLoot
        )
    }

    fun triggerReload() {
        val player = state.player ?: return
        if (!player.alive || state.phase != MatchPhase.COMBAT) return
        val slot = player.activeWeapon
        if (player.reloadTimerSec > 0f || slot.ammoInMag >= slot.type.magSize || slot.reserveAmmo <= 0) return
        updatePlayer { it.copy(reloadTimerSec = slot.type.reloadSec, healTimerSec = 0f) }
    }

    fun triggerMedkit() {
        val player = state.player ?: return
        if (!player.alive || state.phase != MatchPhase.COMBAT) return
        if (player.medkits <= 0 || player.hp >= player.maxHp || player.healTimerSec > 0f) return
        val healDuration = if (character == SurvivorCharacter.ALOK) 1.05f else 1.45f
        updatePlayer { it.copy(healTimerSec = healDuration, reloadTimerSec = 0f) }
    }

    fun deployGlooWall() {
        val player = state.player ?: return
        if (!player.alive || state.phase != MatchPhase.COMBAT) return
        if (player.glooWalls <= 0) return

        val wx = (player.x + cos(player.angleRad) * GLOO_WALL_DISTANCE)
            .coerceIn(190f, WORLD_SIZE - 190f)
        val wy = (player.y + sin(player.angleRad) * GLOO_WALL_DISTANCE)
            .coerceIn(190f, WORLD_SIZE - 190f)
        val wallHp = if (character == SurvivorCharacter.CHRONO) 340f else 250f
        val newWall = GlooWall(
            id = allocId(),
            x = wx,
            y = wy,
            angleRad = player.angleRad + (PI.toFloat() / 2f),
            hp = wallHp,
            maxHp = wallHp,
            ttlSec = 12f
        )
        state = state.copy(
            glooWalls = state.glooWalls + newWall,
            combatants = state.combatants.map {
                if (it.isPlayer) it.copy(glooWalls = it.glooWalls - 1) else it
            }
        )
    }

    fun triggerDash() {
        val player = state.player ?: return
        if (!player.alive || state.phase != MatchPhase.COMBAT) return
        if (player.dashCooldownSec > 0f) return
        updatePlayer {
            it.copy(
                dashTimerSec = 0.24f,
                dashCooldownSec = 3.2f,
                healTimerSec = 0f
            )
        }
    }

    fun step(dtRaw: Float) {
        if (state.phase == MatchPhase.FINISHED) return
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        val newElapsed = state.elapsedSec + dt

        when (state.phase) {
            MatchPhase.DROP_PLANE -> stepDropPlane(dt, newElapsed)
            MatchPhase.PARACHUTE -> stepParachute(dt, newElapsed)
            MatchPhase.COMBAT -> stepCombat(dt, newElapsed)
            MatchPhase.FINISHED -> Unit
        }
    }

    private fun stepDropPlane(dt: Float, newElapsed: Float) {
        val nextProgress = (state.planeProgress + dt / 7.5f).coerceAtMost(1f)
        val px = lerp(280f, state.planeEndX, nextProgress)
        val py = lerp(2120f, state.planeEndY, nextProgress)

        val updatedCombatants = state.combatants.map { c ->
            if (c.isPlayer) {
                c.copy(x = px, y = py)
            } else if (c.airborne && c.parachuteAltitude > 0f) {
                val nextAlt = (c.parachuteAltitude - dt * 0.22f).coerceAtLeast(0f)
                c.copy(
                    parachuteAltitude = nextAlt,
                    airborne = nextAlt > 0f
                )
            } else c
        }

        state = state.copy(
            elapsedSec = newElapsed,
            planeProgress = nextProgress,
            planeX = px,
            planeY = py,
            combatants = updatedCombatants
        )

        if (nextProgress >= 0.96f) {
            ejectFromPlane()
        }
    }

    private fun stepParachute(dt: Float, newElapsed: Float) {
        val updated = state.combatants.map { c ->
            if (c.isPlayer && c.airborne) {
                val nextAlt = (c.parachuteAltitude - dt / 2.6f).coerceAtLeast(0f)
                val nx = (c.x + moveInputX * PARACHUTE_STEER_SPEED * dt)
                    .coerceIn(210f, WORLD_SIZE - 210f)
                val ny = (c.y + moveInputY * PARACHUTE_STEER_SPEED * dt)
                    .coerceIn(210f, WORLD_SIZE - 210f)
                val angle = if (hypot(moveInputX, moveInputY) > 0.1f) {
                    atan2(moveInputY, moveInputX)
                } else c.angleRad
                c.copy(
                    x = nx,
                    y = ny,
                    angleRad = angle,
                    parachuteAltitude = nextAlt,
                    airborne = nextAlt > 0f
                )
            } else if (!c.isPlayer && c.airborne) {
                val nextAlt = (c.parachuteAltitude - dt * 0.35f).coerceAtLeast(0f)
                c.copy(parachuteAltitude = nextAlt, airborne = nextAlt > 0f)
            } else c
        }

        val playerLanded = updated.firstOrNull { it.isPlayer }?.airborne == false
        val settled = if (playerLanded) {
            updated.map { c ->
                val resolved = resolveObstacleCollision(c.x, c.y, PLAYER_RADIUS, state.glooWalls)
                c.copy(
                    x = resolved.first,
                    y = resolved.second,
                    airborne = false,
                    parachuteAltitude = 0f
                )
            }
        } else updated

        state = state.copy(
            elapsedSec = newElapsed,
            phase = if (playerLanded) MatchPhase.COMBAT else MatchPhase.PARACHUTE,
            combatants = settled
        )
    }

    private fun stepCombat(dt: Float, newElapsed: Float) {
        val combatTime = state.combatTimeSec + dt
        val combatants = state.combatants.toMutableList()
        val bullets = state.bullets.toMutableList()
        val glooWalls = state.glooWalls.mapNotNull { wall ->
            val rem = wall.ttlSec - dt
            if (rem > 0f && wall.hp > 0f) wall.copy(ttlSec = rem) else null
        }.toMutableList()
        val lootItems = state.lootItems.toMutableList()
        val damages = state.floatingDamages.mapNotNull { d ->
            val rem = d.ttlSec - dt
            if (rem > 0f) d.copy(y = d.y - 26f * dt, ttlSec = rem) else null
        }.toMutableList()
        val feed = state.killFeed.mapNotNull { f ->
            val rem = f.ttlSec - dt
            if (rem > 0f) f.copy(ttlSec = rem) else null
        }.toMutableList()
        val indicators = state.hitIndicators.mapNotNull { h ->
            val rem = h.ttlSec - dt
            if (rem > 0f) h.copy(ttlSec = rem) else null
        }.toMutableList()

        // 1. Airdrop spawn
        var airdropWarning = (state.airdropWarningTtl - dt).coerceAtLeast(0f)
        if (combatTime >= nextAirdropAtSec) {
            nextAirdropAtSec += 42f
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val dist = Random.nextFloat() * (state.safeZoneRadius * 0.55f)
            val ax = (state.safeZoneX + cos(angle) * dist).coerceIn(260f, WORLD_SIZE - 260f)
            val ay = (state.safeZoneY + sin(angle) * dist).coerceIn(260f, WORLD_SIZE - 260f)
            lootItems += LootItem(
                id = allocId(),
                x = ax,
                y = ay,
                kind = LootKind.AIRDROP_CRATE,
                weaponType = WeaponType.AWM
            )
            airdropWarning = 4.5f
        }

        // 2. Safe zone progression
        var zoneX = state.safeZoneX
        var zoneY = state.safeZoneY
        var zoneRadius = state.safeZoneRadius
        var nextZoneX = state.nextZoneX
        var nextZoneY = state.nextZoneY
        var nextZoneRadius = state.nextZoneRadius
        var zonePhase = state.zonePhase
        var zoneTimer = state.zoneTimerSec - dt
        var zoneShrinking = state.zoneShrinking

        if (!zoneShrinking) {
            if (zoneTimer <= 0f) {
                zoneShrinking = true
                zoneTimer = 14f / mode.zoneSpeedMultiplier
            }
        } else {
            val shrinkRate = (zoneRadius - nextZoneRadius) / max(zoneTimer, 0.25f)
            zoneRadius = max(nextZoneRadius, zoneRadius - shrinkRate * dt)
            zoneX = lerp(zoneX, nextZoneX, (dt * 0.4f).coerceAtMost(1f))
            zoneY = lerp(zoneY, nextZoneY, (dt * 0.4f).coerceAtMost(1f))
            if (zoneTimer <= 0f || zoneRadius <= nextZoneRadius + 1f) {
                zoneShrinking = false
                zoneRadius = nextZoneRadius
                zoneX = nextZoneX
                zoneY = nextZoneY
                zonePhase += 1
                zoneTimer = max(10f, (20f - zonePhase * 2.5f) / mode.zoneSpeedMultiplier)
                nextZoneRadius = when (zonePhase) {
                    2 -> 420f
                    3 -> 210f
                    4 -> 75f
                    else -> 25f
                }
                val shiftAngle = Random.nextFloat() * 2f * PI.toFloat()
                val shiftDist = Random.nextFloat() * max(20f, (zoneRadius - nextZoneRadius) * 0.45f)
                nextZoneX = (zoneX + cos(shiftAngle) * shiftDist).coerceIn(320f, WORLD_SIZE - 320f)
                nextZoneY = (zoneY + sin(shiftAngle) * shiftDist).coerceIn(320f, WORLD_SIZE - 320f)
            }
        }

        // 3. Passive character regeneration (Alok)
        regenAccumSec += dt
        val shouldRegen = regenAccumSec >= 2.0f
        if (shouldRegen) regenAccumSec = 0f

        // 4. Zone storm tick
        zoneDamageAccumSec += dt
        val applyZoneTick = zoneDamageAccumSec >= 1.0f
        if (applyZoneTick) zoneDamageAccumSec = 0f

        var playerKills = state.playerKills
        var playerDamage = state.playerDamage
        var banner = state.eliminationBanner
        var bannerTtl = (state.eliminationBannerTtl - dt).coerceAtLeast(0f)
        if (bannerTtl <= 0f) banner = null

        // 5. Update player movement, reload, heal, fire
        val pIdx = combatants.indexOfFirst { it.isPlayer }
        var nearbyWeapon: LootItem? = null

        if (pIdx >= 0 && combatants[pIdx].alive) {
            var p = combatants[pIdx]
            p = tickCombatantTimers(p, dt)

            if (shouldRegen && character.regenPerTwoSec > 0 && p.hp < p.maxHp) {
                p = p.copy(hp = (p.hp + character.regenPerTwoSec).coerceAtMost(p.maxHp))
            }

            // Movement
            val speedMult = character.moveSpeedMultiplier *
                (if (p.dashTimerSec > 0f) 2.45f else 1f) *
                (if (p.healTimerSec > 0f) 0.52f else 1f)
            val vx = moveInputX * BASE_SPEED * speedMult
            val vy = moveInputY * BASE_SPEED * speedMult
            val resolved = resolveObstacleCollision(
                p.x + vx * dt,
                p.y + vy * dt,
                PLAYER_RADIUS,
                glooWalls
            )
            p = p.copy(x = resolved.first, y = resolved.second)

            // Aiming: manual aim stick OR auto-assist toward nearest target when firing OR movement facing
            val aimMag = hypot(aimInputX, aimInputY)
            val aimAngle = if (aimMag > 0.18f) {
                atan2(aimInputY, aimInputX)
            } else if (isFiring) {
                val autoTarget = findBestAutoAimTarget(p, combatants, glooWalls)
                if (autoTarget != null) {
                    atan2(autoTarget.y - p.y, autoTarget.x - p.x)
                } else if (hypot(moveInputX, moveInputY) > 0.1f) {
                    atan2(moveInputY, moveInputX)
                } else p.angleRad
            } else if (hypot(moveInputX, moveInputY) > 0.15f) {
                atan2(moveInputY, moveInputX)
            } else {
                p.angleRad
            }
            p = p.copy(angleRad = aimAngle)

            // Fire
            if (isFiring && p.fireCooldownSec <= 0f && p.reloadTimerSec <= 0f) {
                val active = p.activeWeapon
                if (active.ammoInMag > 0) {
                    val spawned = spawnBulletsForShot(
                        shooter = p,
                        weapon = active.type,
                        tracerColor = skin.tracerColor
                    )
                    bullets += spawned
                    val nextSlot = active.copy(ammoInMag = active.ammoInMag - 1)
                    p = updateActiveSlot(p, nextSlot).copy(
                        fireCooldownSec = active.type.fireIntervalSec,
                        muzzleFlashSec = 0.08f,
                        healTimerSec = 0f
                    )
                    if (nextSlot.ammoInMag == 0 && nextSlot.reserveAmmo > 0) {
                        p = p.copy(reloadTimerSec = active.type.reloadSec)
                    }
                } else if (active.reserveAmmo > 0) {
                    p = p.copy(reloadTimerSec = active.type.reloadSec)
                }
            }

            // Auto-loot resources + detect nearby weapon
            val lootIter = lootItems.listIterator()
            while (lootIter.hasNext()) {
                val item = lootIter.next()
                val dist = hypot(item.x - p.x, item.y - p.y)
                if (dist <= 44f) {
                    when (item.kind) {
                        LootKind.MEDKIT -> {
                            p = p.copy(medkits = (p.medkits + item.amount).coerceAtMost(6))
                            lootIter.remove()
                        }
                        LootKind.GLOO_WALL -> {
                            p = p.copy(glooWalls = (p.glooWalls + item.amount).coerceAtMost(6))
                            lootIter.remove()
                        }
                        LootKind.ARMOR_VEST -> {
                            if (p.armor < p.maxArmor) {
                                p = p.copy(armor = (p.armor + 50f).coerceAtMost(p.maxArmor))
                                lootIter.remove()
                            }
                        }
                        LootKind.AMMO_PACK -> {
                            val updatedPrimary = p.primarySlot.copy(
                                reserveAmmo = p.primarySlot.reserveAmmo + p.primarySlot.type.magSize * 2
                            )
                            val updatedSec = p.secondarySlot?.let {
                                it.copy(reserveAmmo = it.reserveAmmo + it.type.magSize * 2)
                            }
                            p = p.copy(primarySlot = updatedPrimary, secondarySlot = updatedSec)
                            lootIter.remove()
                        }
                        LootKind.AIRDROP_CRATE -> {
                            p = p.copy(
                                armor = p.maxArmor,
                                medkits = (p.medkits + 2).coerceAtMost(6),
                                glooWalls = (p.glooWalls + 2).coerceAtMost(6)
                            )
                            val awmLoot = item.copy(kind = LootKind.WEAPON, weaponType = WeaponType.AWM)
                            if (p.secondarySlot == null) {
                                p = p.copy(
                                    secondarySlot = WeaponSlot(WeaponType.AWM, 5, 20),
                                    activeSlotIndex = 1
                                )
                                lootIter.remove()
                            } else {
                                lootIter.set(awmLoot)
                                nearbyWeapon = awmLoot
                            }
                        }
                        LootKind.WEAPON -> {
                            if (p.secondarySlot == null && item.weaponType != null && item.weaponType != p.primarySlot.type) {
                                val wt = item.weaponType
                                p = p.copy(
                                    secondarySlot = WeaponSlot(wt, wt.magSize, wt.magSize * 3),
                                    activeSlotIndex = 1
                                )
                                lootIter.remove()
                            } else if (nearbyWeapon == null) {
                                nearbyWeapon = item
                            }
                        }
                    }
                }
            }

            combatants[pIdx] = p
        }

        // 6. Update AI Bots
        for (i in combatants.indices) {
            val bot = combatants[i]
            if (bot.isPlayer || !bot.alive) continue
            var b = tickCombatantTimers(bot, dt)

            if (b.hp < 85f && b.medkits > 0 && b.healTimerSec <= 0f) {
                b = b.copy(healTimerSec = 1.6f)
            }

            // Find closest living enemy within 580f
            var closestEnemy: Combatant? = null
            var closestDist = 600f
            for (other in combatants) {
                if (other.id == b.id || !other.alive) continue
                val d = hypot(other.x - b.x, other.y - b.y)
                if (d < closestDist) {
                    closestDist = d
                    closestEnemy = other
                }
            }

            val distFromZone = hypot(b.x - zoneX, b.y - zoneY)
            var dirX = 0f
            var dirY = 0f
            var desiredAngle = b.angleRad

            if (distFromZone > zoneRadius * 0.86f) {
                // Move urgently into safe zone
                val toZoneAngle = atan2(zoneY - b.y, zoneX - b.x)
                dirX = cos(toZoneAngle)
                dirY = sin(toZoneAngle)
                desiredAngle = toZoneAngle
            } else if (closestEnemy != null) {
                val enemyAngle = atan2(closestEnemy.y - b.y, closestEnemy.x - b.x)
                desiredAngle = enemyAngle
                val idealRange = b.activeWeapon.type.range * 0.68f
                val forwardBias = when {
                    closestDist > idealRange + 40f -> 0.75f
                    closestDist < idealRange - 80f -> -0.55f
                    else -> 0f
                }
                val strafeAngle = enemyAngle + (PI.toFloat() / 2f) * b.aiStrafeSign
                dirX = cos(enemyAngle) * forwardBias + cos(strafeAngle) * 0.7f
                dirY = sin(enemyAngle) * forwardBias + sin(strafeAngle) * 0.7f

                // Occasional strafe switch
                var decisionTimer = b.aiDecisionTimer - dt
                if (decisionTimer <= 0f) {
                    decisionTimer = 1.2f + Random.nextFloat() * 1.4f
                    b = b.copy(aiStrafeSign = -b.aiStrafeSign)
                }
                b = b.copy(aiDecisionTimer = decisionTimer)

                // Shoot if line of sight is clear
                if (b.fireCooldownSec <= 0f && b.reloadTimerSec <= 0f && b.healTimerSec <= 0f &&
                    closestDist <= b.activeWeapon.type.range * 0.95f &&
                    hasLineOfSight(b.x, b.y, closestEnemy.x, closestEnemy.y, glooWalls)
                ) {
                    val slot = b.activeWeapon
                    if (slot.ammoInMag > 0) {
                        val spread = (Random.nextFloat() - 0.5f) * 0.16f
                        val shotShooter = b.copy(angleRad = enemyAngle + spread)
                        bullets += spawnBulletsForShot(
                            shooter = shotShooter,
                            weapon = slot.type,
                            tracerColor = Color(0xFFFFD54F)
                        )
                        b = updateActiveSlot(b, slot.copy(ammoInMag = slot.ammoInMag - 1)).copy(
                            fireCooldownSec = slot.type.fireIntervalSec * 1.65f,
                            muzzleFlashSec = 0.08f
                        )
                    } else {
                        b = b.copy(reloadTimerSec = slot.type.reloadSec)
                    }
                }
            } else {
                // Patrol toward center/next zone
                var decisionTimer = b.aiDecisionTimer - dt
                var targetAngle = b.angleRad
                if (decisionTimer <= 0f) {
                    decisionTimer = 1.8f + Random.nextFloat() * 2.0f
                    val toCenter = atan2(nextZoneY - b.y, nextZoneX - b.x)
                    targetAngle = toCenter + (Random.nextFloat() - 0.5f) * 1.4f
                }
                b = b.copy(aiDecisionTimer = decisionTimer)
                desiredAngle = targetAngle
                dirX = cos(targetAngle) * 0.75f
                dirY = sin(targetAngle) * 0.75f
            }

            val dirLen = hypot(dirX, dirY)
            if (dirLen > 0.01f) {
                dirX /= dirLen
                dirY /= dirLen
            }
            val botSpeed = BASE_SPEED * 0.84f * (if (b.healTimerSec > 0f) 0.45f else 1f)
            val resolved = resolveObstacleCollision(
                b.x + dirX * botSpeed * dt,
                b.y + dirY * botSpeed * dt,
                PLAYER_RADIUS,
                glooWalls
            )
            combatants[i] = b.copy(x = resolved.first, y = resolved.second, angleRad = desiredAngle)
        }

        // 7. Storm damage tick
        if (applyZoneTick) {
            val stormDmg = (4 + zonePhase * 4).toFloat()
            for (i in combatants.indices) {
                val c = combatants[i]
                if (!c.alive) continue
                if (hypot(c.x - zoneX, c.y - zoneY) > zoneRadius) {
                    val nextHp = c.hp - stormDmg
                    if (nextHp <= 0f) {
                        combatants[i] = c.copy(hp = 0f, alive = false)
                        feed += KillFeedEntry(
                            id = allocId(),
                            killer = "Storm Zone",
                            victim = c.name,
                            weapon = "ZONE",
                            involvesPlayer = c.isPlayer
                        )
                        dropDeathLoot(c, lootItems)
                    } else {
                        combatants[i] = c.copy(hp = nextHp, hitFlashSec = 0.14f)
                    }
                }
            }
        }

        // 8. Bullet movement & collision
        val bulletIter = bullets.listIterator()
        while (bulletIter.hasNext()) {
            val bullet = bulletIter.next()
            val nx = bullet.x + bullet.vx * dt
            val ny = bullet.y + bullet.vy * dt
            val stepDist = hypot(nx - bullet.x, ny - bullet.y)
            val remRange = bullet.remainingRange - stepDist

            if (remRange <= 0f || nx < 100f || nx > WORLD_SIZE - 100f || ny < 100f || ny > WORLD_SIZE - 100f) {
                bulletIter.remove()
                continue
            }

            // Check obstacle hit
            if (pointHitsObstacle(nx, ny)) {
                bulletIter.remove()
                continue
            }

            // Check gloo wall hit
            val hitWallIndex = glooWalls.indexOfFirst { wall ->
                pointHitsGlooWall(nx, ny, wall)
            }
            if (hitWallIndex >= 0) {
                val wall = glooWalls[hitWallIndex]
                val nextWallHp = wall.hp - bullet.damage
                if (nextWallHp <= 0f) {
                    glooWalls.removeAt(hitWallIndex)
                } else {
                    glooWalls[hitWallIndex] = wall.copy(hp = nextWallHp)
                }
                bulletIter.remove()
                continue
            }

            // Check combatant hit
            var hitTargetIdx = -1
            for (idx in combatants.indices) {
                val target = combatants[idx]
                if (!target.alive || target.id == bullet.ownerId) continue
                if (hypot(target.x - nx, target.y - ny) <= PLAYER_RADIUS + 4f) {
                    hitTargetIdx = idx
                    break
                }
            }

            if (hitTargetIdx >= 0) {
                val target = combatants[hitTargetIdx]
                val isCrit = Random.nextFloat() < 0.2f
                val rawDmg = if (isCrit) (bullet.damage * 1.45f).toInt() else bullet.damage
                val armorMitigation = if (target.armor > 0f) 0.42f else 0f
                val absorbed = (rawDmg * armorMitigation).coerceAtMost(target.armor)
                val hpDmg = max(1f, rawDmg - absorbed)
                val nextHp = target.hp - hpDmg
                val nextArmor = (target.armor - absorbed).coerceAtLeast(0f)

                if (bullet.ownerId == 1) {
                    playerDamage += hpDmg.toInt()
                    damages += FloatingDamage(
                        id = allocId(),
                        x = target.x + (Random.nextFloat() - 0.5f) * 16f,
                        y = target.y - 24f,
                        text = if (isCrit) "CRIT -${hpDmg.toInt()}" else "-${hpDmg.toInt()}",
                        isCrit = isCrit
                    )
                }

                if (target.isPlayer) {
                    val shooter = combatants.firstOrNull { it.id == bullet.ownerId }
                    if (shooter != null) {
                        indicators += HitDirectionIndicator(
                            angleFromPlayerRad = atan2(shooter.y - target.y, shooter.x - target.x)
                        )
                    }
                }

                if (nextHp <= 0f) {
                    combatants[hitTargetIdx] = target.copy(
                        hp = 0f,
                        armor = 0f,
                        alive = false
                    )
                    val killerIdx = combatants.indexOfFirst { it.id == bullet.ownerId }
                    val killerName = if (killerIdx >= 0) {
                        val k = combatants[killerIdx]
                        combatants[killerIdx] = k.copy(kills = k.kills + 1)
                        if (k.isPlayer) {
                            playerKills += 1
                            banner = "ELIMINATED ${target.name} • ${playerKills} KILLS"
                            bannerTtl = 2.8f
                        }
                        k.name
                    } else "Unknown"

                    feed += KillFeedEntry(
                        id = allocId(),
                        killer = killerName,
                        victim = target.name,
                        weapon = bullet.weaponName,
                        involvesPlayer = (bullet.ownerId == 1 || target.isPlayer)
                    )
                    dropDeathLoot(target, lootItems)
                } else {
                    combatants[hitTargetIdx] = target.copy(
                        hp = nextHp,
                        armor = nextArmor,
                        hitFlashSec = 0.16f
                    )
                }
                bulletIter.remove()
            } else {
                bulletIter.set(bullet.copy(x = nx, y = ny, remainingRange = remRange))
            }
        }

        // 9. Check Victory / Elimination end conditions
        val aliveTotal = combatants.count { it.alive }
        val playerAlive = combatants.firstOrNull { it.isPlayer }?.alive == true
        var nextPhase = MatchPhase.COMBAT
        var summary: MatchResultSummary? = null

        if (!playerAlive) {
            nextPhase = MatchPhase.FINISHED
            val placement = (aliveTotal + 1).coerceIn(2, mode.totalCombatants)
            summary = buildMatchSummary(
                placement = placement,
                kills = playerKills,
                damage = playerDamage,
                survivalSeconds = combatTime.toInt()
            )
        } else if (aliveTotal <= 1) {
            nextPhase = MatchPhase.FINISHED
            summary = buildMatchSummary(
                placement = 1,
                kills = playerKills,
                damage = playerDamage,
                survivalSeconds = combatTime.toInt()
            )
        }

        state = state.copy(
            phase = nextPhase,
            elapsedSec = newElapsed,
            combatTimeSec = combatTime,
            combatants = combatants,
            bullets = bullets,
            glooWalls = glooWalls,
            lootItems = lootItems,
            floatingDamages = damages,
            killFeed = feed.takeLast(4),
            hitIndicators = indicators,
            safeZoneX = zoneX,
            safeZoneY = zoneY,
            safeZoneRadius = zoneRadius,
            nextZoneX = nextZoneX,
            nextZoneY = nextZoneY,
            nextZoneRadius = nextZoneRadius,
            zonePhase = zonePhase,
            zoneTimerSec = max(0f, zoneTimer),
            zoneShrinking = zoneShrinking,
            aliveCount = aliveTotal,
            playerKills = playerKills,
            playerDamage = playerDamage,
            nearbyWeaponLoot = nearbyWeapon,
            eliminationBanner = banner,
            eliminationBannerTtl = bannerTtl,
            airdropWarningTtl = airdropWarning,
            resultSummary = summary
        )
    }

    private fun buildMatchSummary(
        placement: Int,
        kills: Int,
        damage: Int,
        survivalSeconds: Int
    ): MatchResultSummary {
        val victory = placement == 1
        val placementBonus = ((mode.totalCombatants - placement) * 3) + if (victory) 35 else 0
        val rpDelta = (placementBonus + kills * 8 - 10).coerceIn(-25, 160)
        val coins = (25 + kills * 18 + (if (victory) 120 else (mode.totalCombatants - placement) * 4))
            .coerceIn(20, 600)
        return MatchResultSummary(
            mode = mode,
            placement = placement,
            totalCombatants = mode.totalCombatants,
            kills = kills,
            damageDealt = damage,
            survivalSeconds = survivalSeconds,
            rankPointsDelta = rpDelta,
            coinsEarned = coins,
            victory = victory
        )
    }

    private fun tickCombatantTimers(c: Combatant, dt: Float): Combatant {
        var next = c.copy(
            fireCooldownSec = (c.fireCooldownSec - dt).coerceAtLeast(0f),
            dashTimerSec = (c.dashTimerSec - dt).coerceAtLeast(0f),
            dashCooldownSec = (c.dashCooldownSec - dt).coerceAtLeast(0f),
            muzzleFlashSec = (c.muzzleFlashSec - dt).coerceAtLeast(0f),
            hitFlashSec = (c.hitFlashSec - dt).coerceAtLeast(0f)
        )

        if (next.reloadTimerSec > 0f) {
            val rem = next.reloadTimerSec - dt
            if (rem <= 0f) {
                val slot = next.activeWeapon
                val needed = (slot.type.magSize - slot.ammoInMag).coerceAtLeast(0)
                val loaded = min(needed, slot.reserveAmmo)
                val updatedSlot = slot.copy(
                    ammoInMag = slot.ammoInMag + loaded,
                    reserveAmmo = slot.reserveAmmo - loaded
                )
                next = updateActiveSlot(next, updatedSlot).copy(reloadTimerSec = 0f)
            } else {
                next = next.copy(reloadTimerSec = rem)
            }
        }

        if (next.healTimerSec > 0f) {
            val rem = next.healTimerSec - dt
            if (rem <= 0f) {
                next = next.copy(
                    hp = (next.hp + 75f).coerceAtMost(next.maxHp),
                    medkits = (next.medkits - 1).coerceAtLeast(0),
                    healTimerSec = 0f
                )
            } else {
                next = next.copy(healTimerSec = rem)
            }
        }
        return next
    }

    private fun updateActiveSlot(c: Combatant, updated: WeaponSlot): Combatant {
        return if (c.activeSlotIndex == 1 && c.secondarySlot != null) {
            c.copy(secondarySlot = updated)
        } else {
            c.copy(primarySlot = updated)
        }
    }

    private fun spawnBulletsForShot(
        shooter: Combatant,
        weapon: WeaponType,
        tracerColor: Color
    ): List<Bullet> {
        val dmgMultiplier = if (shooter.isPlayer && weapon.range <= 450f) {
            character.damageBonusMultiplier
        } else 1f
        val muzzleX = shooter.x + cos(shooter.angleRad) * (PLAYER_RADIUS + 8f)
        val muzzleY = shooter.y + sin(shooter.angleRad) * (PLAYER_RADIUS + 8f)
        return (0 until weapon.pellets).map {
            val offset = (Random.nextFloat() - 0.5f) * weapon.spreadRad * 2f
            val angle = shooter.angleRad + offset
            Bullet(
                id = allocId(),
                ownerId = shooter.id,
                x = muzzleX,
                y = muzzleY,
                vx = cos(angle) * weapon.bulletSpeed,
                vy = sin(angle) * weapon.bulletSpeed,
                damage = (weapon.damage * dmgMultiplier).toInt(),
                remainingRange = weapon.range,
                color = if (shooter.isPlayer) tracerColor else Color(0xFFFFD54F),
                weaponName = weapon.shortCode,
                isSniper = weapon == WeaponType.AWM
            )
        }
    }

    private fun findBestAutoAimTarget(
        player: Combatant,
        combatants: List<Combatant>,
        glooWalls: List<GlooWall>
    ): Combatant? {
        val maxRange = player.activeWeapon.type.range * 1.1f
        var bestVisible: Combatant? = null
        var bestVisibleDist = maxRange
        var bestFallback: Combatant? = null
        var bestFallbackDist = maxRange

        for (other in combatants) {
            if (other.isPlayer || !other.alive) continue
            val d = hypot(other.x - player.x, other.y - player.y)
            if (d <= maxRange) {
                if (hasLineOfSight(player.x, player.y, other.x, other.y, glooWalls)) {
                    if (d < bestVisibleDist) {
                        bestVisibleDist = d
                        bestVisible = other
                    }
                } else if (d < bestFallbackDist) {
                    bestFallbackDist = d
                    bestFallback = other
                }
            }
        }
        return bestVisible ?: bestFallback
    }

    private fun dropDeathLoot(victim: Combatant, lootItems: MutableList<LootItem>) {
        lootItems += LootItem(
            id = allocId(),
            x = victim.x - 16f,
            y = victim.y,
            kind = LootKind.AMMO_PACK
        )
        if (victim.primarySlot.type != WeaponType.USP) {
            lootItems += LootItem(
                id = allocId(),
                x = victim.x + 18f,
                y = victim.y + 8f,
                kind = LootKind.WEAPON,
                weaponType = victim.primarySlot.type
            )
        }
        lootItems += LootItem(
            id = allocId(),
            x = victim.x,
            y = victim.y - 18f,
            kind = if (Random.nextBoolean()) LootKind.MEDKIT else LootKind.GLOO_WALL
        )
    }

    private fun updatePlayer(transform: (Combatant) -> Combatant) {
        state = state.copy(
            combatants = state.combatants.map { if (it.isPlayer) transform(it) else it }
        )
    }

    private fun resolveObstacleCollision(
        targetX: Float,
        targetY: Float,
        radius: Float,
        glooWalls: List<GlooWall>
    ): Pair<Float, Float> {
        var x = targetX.coerceIn(170f, WORLD_SIZE - 170f)
        var y = targetY.coerceIn(170f, WORLD_SIZE - 170f)

        for (obs in obstacles) {
            val closestX = x.coerceIn(obs.x, obs.x + obs.width)
            val closestY = y.coerceIn(obs.y, obs.y + obs.height)
            val dx = x - closestX
            val dy = y - closestY
            val distSq = dx * dx + dy * dy
            if (distSq < radius * radius) {
                val dist = hypot(dx, dy)
                if (dist > 0.001f) {
                    val push = radius - dist
                    x += (dx / dist) * push
                    y += (dy / dist) * push
                } else {
                    x += radius
                }
            }
        }

        for (wall in glooWalls) {
            val wx = cos(wall.angleRad)
            val wy = sin(wall.angleRad)
            val relX = x - wall.x
            val relY = y - wall.y
            val along = (relX * wx + relY * wy).coerceIn(-wall.halfLength, wall.halfLength)
            val closestX = wall.x + wx * along
            val closestY = wall.y + wy * along
            val dx = x - closestX
            val dy = y - closestY
            val dist = hypot(dx, dy)
            val minDist = radius + 9f
            if (dist < minDist && dist > 0.001f) {
                val push = minDist - dist
                x += (dx / dist) * push
                y += (dy / dist) * push
            }
        }
        return x to y
    }

    private fun pointHitsObstacle(x: Float, y: Float): Boolean {
        for (obs in obstacles) {
            if (x >= obs.x && x <= obs.x + obs.width && y >= obs.y && y <= obs.y + obs.height) {
                return true
            }
        }
        return false
    }

    private fun pointHitsGlooWall(x: Float, y: Float, wall: GlooWall): Boolean {
        val wx = cos(wall.angleRad)
        val wy = sin(wall.angleRad)
        val relX = x - wall.x
        val relY = y - wall.y
        val along = relX * wx + relY * wy
        val perp = abs(-relX * wy + relY * wx)
        return abs(along) <= wall.halfLength && perp <= 12f
    }

    private fun hasLineOfSight(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        glooWalls: List<GlooWall>
    ): Boolean {
        val steps = 10
        for (s in 1 until steps) {
            val t = s.toFloat() / steps
            val px = lerp(x1, x2, t)
            val py = lerp(y1, y2, t)
            if (pointHitsObstacle(px, py)) return false
            if (glooWalls.any { pointHitsGlooWall(px, py, it) }) return false
        }
        return true
    }

    private fun spawnInitialLoot(): List<LootItem> {
        val items = mutableListOf<LootItem>()
        val weapons = listOf(
            WeaponType.MP40, WeaponType.SCAR_L, WeaponType.M1887,
            WeaponType.MP40, WeaponType.SCAR_L, WeaponType.M1887
        )
        // High-tier loot around POIs + random island spawns
        pois.forEachIndexed { idx, poi ->
            repeat(3) { j ->
                val angle = (j * 2.1f) + idx
                val lx = (poi.x + cos(angle) * (95f + j * 40f)).coerceIn(220f, WORLD_SIZE - 220f)
                val ly = (poi.y + sin(angle) * (95f + j * 40f)).coerceIn(220f, WORLD_SIZE - 220f)
                if (!pointHitsObstacle(lx, ly)) {
                    items += LootItem(
                        id = allocId(),
                        x = lx,
                        y = ly,
                        kind = LootKind.WEAPON,
                        weaponType = weapons[(idx + j) % weapons.size]
                    )
                }
            }
            items += LootItem(
                id = allocId(),
                x = (poi.x + 55f).coerceIn(220f, WORLD_SIZE - 220f),
                y = (poi.y - 55f).coerceIn(220f, WORLD_SIZE - 220f),
                kind = LootKind.ARMOR_VEST
            )
        }
        // Scattered medkits, gloo walls, ammo, and weapons
        repeat(42) { i ->
            val lx = 260f + Random.nextFloat() * (WORLD_SIZE - 520f)
            val ly = 260f + Random.nextFloat() * (WORLD_SIZE - 520f)
            if (!pointHitsObstacle(lx, ly)) {
                val kind = when (i % 5) {
                    0 -> LootKind.WEAPON
                    1 -> LootKind.MEDKIT
                    2 -> LootKind.GLOO_WALL
                    3 -> LootKind.ARMOR_VEST
                    else -> LootKind.AMMO_PACK
                }
                items += LootItem(
                    id = allocId(),
                    x = lx,
                    y = ly,
                    kind = kind,
                    weaponType = if (kind == LootKind.WEAPON) weapons[i % weapons.size] else null
                )
            }
        }
        return items
    }

    private fun buildMapObstacles(): List<ObstacleRect> = listOf(
        // Peak Citadel (Center)
        ObstacleRect(1080f, 1020f, 110f, 52f, isBuilding = true, label = "CITADEL"),
        ObstacleRect(1260f, 1060f, 90f, 95f, isBuilding = true),
        ObstacleRect(1130f, 1210f, 130f, 48f, isBuilding = true),
        // Clock Tower (NW)
        ObstacleRect(620f, 680f, 105f, 105f, isBuilding = true, label = "TOWER"),
        ObstacleRect(800f, 740f, 80f, 45f),
        ObstacleRect(650f, 840f, 95f, 42f),
        // Factory Yard (NE)
        ObstacleRect(1620f, 690f, 140f, 70f, isBuilding = true, label = "FACTORY"),
        ObstacleRect(1810f, 790f, 70f, 110f, isBuilding = true),
        ObstacleRect(1640f, 840f, 65f, 55f),
        // Rim Village (SW)
        ObstacleRect(580f, 1600f, 110f, 65f, isBuilding = true, label = "VILLAGE"),
        ObstacleRect(740f, 1710f, 90f, 75f, isBuilding = true),
        ObstacleRect(590f, 1740f, 60f, 45f),
        // Shipyard Docks (SE)
        ObstacleRect(1620f, 1600f, 150f, 55f, isBuilding = true, label = "DOCKS"),
        ObstacleRect(1650f, 1720f, 130f, 50f),
        ObstacleRect(1820f, 1640f, 55f, 120f),
        // Mid-lane tactical crates & cover rocks
        ObstacleRect(950f, 880f, 56f, 44f),
        ObstacleRect(1420f, 900f, 60f, 44f),
        ObstacleRect(940f, 1460f, 64f, 44f),
        ObstacleRect(1420f, 1450f, 58f, 48f),
        ObstacleRect(460f, 1180f, 75f, 55f),
        ObstacleRect(1880f, 1200f, 75f, 55f),
        ObstacleRect(1170f, 540f, 85f, 50f),
        ObstacleRect(1160f, 1840f, 85f, 50f)
    )

    private fun buildTrees(): List<TreeSpot> {
        val rng = Random(42)
        return (0 until 44).map {
            TreeSpot(
                x = 240f + rng.nextFloat() * (WORLD_SIZE - 480f),
                y = 240f + rng.nextFloat() * (WORLD_SIZE - 480f),
                radius = 24f + rng.nextFloat() * 14f
            )
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
