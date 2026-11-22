package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BattleArenaCanvas(
    state: BattleState,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val player = state.player
        val zoom = when (state.phase) {
            MatchPhase.DROP_PLANE -> 0.42f
            MatchPhase.PARACHUTE -> 0.65f + (1f - (player?.parachuteAltitude ?: 0f)) * 0.35f
            else -> 1.0f
        }
        val camX = if (state.phase == MatchPhase.DROP_PLANE) state.planeX else (player?.x ?: 1200f)
        val camY = if (state.phase == MatchPhase.DROP_PLANE) state.planeY else (player?.y ?: 1200f)

        val halfW = size.width / 2f
        val halfH = size.height / 2f

        fun toScreen(wx: Float, wy: Float): Offset = Offset(
            x = halfW + (wx - camX) * zoom,
            y = halfH + (wy - camY) * zoom
        )

        // 1. Ocean background
        drawRect(color = Color(0xFF0A2239))

        // 2. Island landmass & sandy beach
        val beachTopLeft = toScreen(140f, 140f)
        val beachSize = (state.worldSize - 280f) * zoom
        drawRoundRect(
            color = Color(0xFFC8A96A),
            topLeft = beachTopLeft,
            size = Size(beachSize, beachSize),
            cornerRadius = CornerRadius(110f * zoom, 110f * zoom)
        )

        val grassTopLeft = toScreen(185f, 185f)
        val grassSize = (state.worldSize - 370f) * zoom
        drawRoundRect(
            color = Color(0xFF273F2B),
            topLeft = grassTopLeft,
            size = Size(grassSize, grassSize),
            cornerRadius = CornerRadius(85f * zoom, 85f * zoom)
        )

        // 3. Tactical road network & grid lines
        val roadColor = Color(0xFF393E46)
        val roadStroke = 44f * zoom
        drawLine(
            color = roadColor,
            start = toScreen(320f, 1200f),
            end = toScreen(2080f, 1200f),
            strokeWidth = roadStroke
        )
        drawLine(
            color = roadColor,
            start = toScreen(1200f, 320f),
            end = toScreen(1200f, 2080f),
            strokeWidth = roadStroke
        )
        drawLine(
            color = Color(0xFF2D4831),
            start = toScreen(650f, 720f),
            end = toScreen(1750f, 1680f),
            strokeWidth = 22f * zoom
        )

        // Subtle tactical grid
        val gridStep = 240f
        var g = 240f
        while (g < state.worldSize - 200f) {
            drawLine(
                color = Color(0x14FFFFFF),
                start = toScreen(g, 190f),
                end = toScreen(g, state.worldSize - 190f),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0x14FFFFFF),
                start = toScreen(190f, g),
                end = toScreen(state.worldSize - 190f, g),
                strokeWidth = 1f
            )
            g += gridStep
        }

        // 4. POI ground labels
        if (state.phase == MatchPhase.DROP_PLANE || state.phase == MatchPhase.PARACHUTE) {
            state.pois.forEach { poi ->
                val pScreen = toScreen(poi.x, poi.y)
                drawSafeCanvasText(
                    textMeasurer = textMeasurer,
                    text = poi.name,
                    topLeft = Offset(pScreen.x - 52f, pScreen.y - 10f),
                    style = TextStyle(
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // 5. Loot items
        state.lootItems.forEach { item ->
            val pos = toScreen(item.x, item.y)
            if (pos.x < -60f || pos.x > size.width + 60f || pos.y < -60f || pos.y > size.height + 60f) {
                return@forEach
            }
            when (item.kind) {
                LootKind.AIRDROP_CRATE -> {
                    val pulse = 26f + (kotlin.math.sin(state.elapsedSec * 5f) * 6f)
                    drawCircle(
                        color = Color(0x44FF6B00),
                        radius = pulse * zoom,
                        center = pos
                    )
                    drawRoundRect(
                        color = Color(0xFFFFB800),
                        topLeft = Offset(pos.x - 16f * zoom, pos.y - 16f * zoom),
                        size = Size(32f * zoom, 32f * zoom),
                        cornerRadius = CornerRadius(6f * zoom)
                    )
                    drawRoundRect(
                        color = Color(0xFFFF3B5C),
                        topLeft = Offset(pos.x - 16f * zoom, pos.y - 4f * zoom),
                        size = Size(32f * zoom, 8f * zoom)
                    )
                }
                LootKind.WEAPON -> {
                    val c = item.weaponType?.accentColor ?: Color.White
                    drawCircle(color = c.copy(alpha = 0.25f), radius = 16f * zoom, center = pos)
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(pos.x - 13f * zoom, pos.y - 5f * zoom),
                        size = Size(26f * zoom, 10f * zoom),
                        cornerRadius = CornerRadius(3f * zoom)
                    )
                }
                LootKind.MEDKIT -> {
                    drawRoundRect(
                        color = Color(0xFF00E676),
                        topLeft = Offset(pos.x - 10f * zoom, pos.y - 10f * zoom),
                        size = Size(20f * zoom, 20f * zoom),
                        cornerRadius = CornerRadius(4f * zoom)
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(pos.x - 5f * zoom, pos.y),
                        end = Offset(pos.x + 5f * zoom, pos.y),
                        strokeWidth = 3f * zoom
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(pos.x, pos.y - 5f * zoom),
                        end = Offset(pos.x, pos.y + 5f * zoom),
                        strokeWidth = 3f * zoom
                    )
                }
                LootKind.GLOO_WALL -> {
                    drawCircle(color = Color(0xFF00E5FF), radius = 10f * zoom, center = pos)
                    drawCircle(color = Color.White, radius = 4f * zoom, center = pos)
                }
                LootKind.ARMOR_VEST -> {
                    drawRoundRect(
                        color = Color(0xFF60A5FA),
                        topLeft = Offset(pos.x - 10f * zoom, pos.y - 11f * zoom),
                        size = Size(20f * zoom, 22f * zoom),
                        cornerRadius = CornerRadius(5f * zoom)
                    )
                }
                LootKind.AMMO_PACK -> {
                    drawRoundRect(
                        color = Color(0xFFFBBF24),
                        topLeft = Offset(pos.x - 8f * zoom, pos.y - 8f * zoom),
                        size = Size(16f * zoom, 16f * zoom),
                        cornerRadius = CornerRadius(3f * zoom)
                    )
                }
            }
        }

        // 6. Obstacles (Buildings & tactical crates)
        state.obstacles.forEach { obs ->
            val topLeft = toScreen(obs.x, obs.y)
            val w = obs.width * zoom
            val h = obs.height * zoom
            if (topLeft.x + w < -40f || topLeft.x > size.width + 40f ||
                topLeft.y + h < -40f || topLeft.y > size.height + 40f
            ) return@forEach

            // Shadow
            drawRoundRect(
                color = Color(0x55000000),
                topLeft = Offset(topLeft.x + 6f * zoom, topLeft.y + 8f * zoom),
                size = Size(w, h),
                cornerRadius = CornerRadius(8f * zoom)
            )
            // Structure body
            drawRoundRect(
                color = if (obs.isBuilding) Color(0xFF475569) else Color(0xFF6B5B45),
                topLeft = topLeft,
                size = Size(w, h),
                cornerRadius = CornerRadius(8f * zoom)
            )
            // Roof / top highlight
            drawRoundRect(
                color = if (obs.isBuilding) Color(0xFF64748B) else Color(0xFF8C765A),
                topLeft = Offset(topLeft.x + 5f * zoom, topLeft.y + 5f * zoom),
                size = Size((w - 10f * zoom).coerceAtLeast(2f), (h - 10f * zoom).coerceAtLeast(2f)),
                cornerRadius = CornerRadius(5f * zoom)
            )
        }

        // 7. Gloo Walls (Cyan energy barriers)
        state.glooWalls.forEach { wall ->
            val center = toScreen(wall.x, wall.y)
            val deg = wall.angleRad * (180f / PI.toFloat())
            val healthRatio = (wall.hp / wall.maxHp).coerceIn(0f, 1f)
            rotate(degrees = deg, pivot = center) {
                drawRoundRect(
                    color = Color(0x5500E5FF),
                    topLeft = Offset(center.x - (wall.halfLength + 6f) * zoom, center.y - 12f * zoom),
                    size = Size((wall.halfLength * 2 + 12f) * zoom, 24f * zoom),
                    cornerRadius = CornerRadius(8f * zoom)
                )
                drawRoundRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.55f + 0.4f * healthRatio),
                    topLeft = Offset(center.x - wall.halfLength * zoom, center.y - 8f * zoom),
                    size = Size(wall.halfLength * 2 * zoom, 16f * zoom),
                    cornerRadius = CornerRadius(5f * zoom)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.7f),
                    topLeft = Offset(center.x - (wall.halfLength - 6f) * zoom, center.y - 2f * zoom),
                    size = Size((wall.halfLength * 2 - 12f) * zoom, 4f * zoom)
                )
            }
        }

        // 8. Bullets & Tracers
        state.bullets.forEach { b ->
            val pos = toScreen(b.x, b.y)
            val speed = kotlin.math.hypot(b.vx, b.vy).coerceAtLeast(1f)
            val tailLen = if (b.isSniper) 42f else 24f
            val tail = Offset(
                pos.x - (b.vx / speed) * tailLen * zoom,
                pos.y - (b.vy / speed) * tailLen * zoom
            )
            drawLine(
                color = b.color,
                start = tail,
                end = pos,
                strokeWidth = (if (b.isSniper) 5f else 3.2f) * zoom,
                cap = StrokeCap.Round
            )
            drawCircle(color = Color.White, radius = 2.6f * zoom, center = pos)
        }

        // 9. Combatants (Bots first, then Player on top)
        state.combatants.filter { it.alive && !it.isPlayer }.forEach { bot ->
            if (state.phase == MatchPhase.DROP_PLANE && bot.airborne) return@forEach
            val pos = toScreen(bot.x, bot.y)
            if (pos.x < -80f || pos.x > size.width + 80f || pos.y < -80f || pos.y > size.height + 80f) {
                return@forEach
            }
            drawCombatant(
                combatant = bot,
                pos = pos,
                zoom = zoom,
                accentColor = Color(0xFFFF3B5C),
                textMeasurer = textMeasurer
            )
        }

        if (player != null && player.alive && state.phase != MatchPhase.DROP_PLANE) {
            val pPos = toScreen(player.x, player.y)
            drawCombatant(
                combatant = player,
                pos = pPos,
                zoom = zoom,
                accentColor = state.character.accentColor,
                textMeasurer = textMeasurer
            )
        }

        // 10. Trees (semi-transparent canopy overhead)
        state.trees.forEach { tree ->
            val pos = toScreen(tree.x, tree.y)
            if (pos.x < -60f || pos.x > size.width + 60f || pos.y < -60f || pos.y > size.height + 60f) {
                return@forEach
            }
            drawCircle(
                color = Color(0xAA1B4332),
                radius = tree.radius * zoom,
                center = pos
            )
            drawCircle(
                color = Color(0x882D6A4F),
                radius = tree.radius * 0.68f * zoom,
                center = Offset(pos.x - 4f * zoom, pos.y - 4f * zoom)
            )
        }

        // 11. Next Zone (White dashed) & Current Safe Zone (Electric Cyan/Red)
        val nextCenter = toScreen(state.nextZoneX, state.nextZoneY)
        drawCircle(
            color = Color(0xAAFFFFFF),
            radius = state.nextZoneRadius * zoom,
            center = nextCenter,
            style = Stroke(
                width = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
            )
        )

        val zoneCenter = toScreen(state.safeZoneX, state.safeZoneY)
        drawCircle(
            color = if (state.zoneShrinking) Color(0xFFFF3B5C) else Color(0xFF00E5FF),
            radius = state.safeZoneRadius * zoom,
            center = zoneCenter,
            style = Stroke(width = 4.5f)
        )

        // 12. Drop Plane (when in plane phase)
        if (state.phase == MatchPhase.DROP_PLANE) {
            val planePos = toScreen(state.planeX, state.planeY)
            drawLine(
                color = Color(0x88FFFFFF),
                start = toScreen(280f, 2120f),
                end = toScreen(state.planeEndX, state.planeEndY),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
            )
            rotate(degrees = -45f, pivot = planePos) {
                // Wings
                drawRoundRect(
                    color = Color(0xFFCBD5E1),
                    topLeft = Offset(planePos.x - 14f, planePos.y - 54f),
                    size = Size(28f, 108f),
                    cornerRadius = CornerRadius(6f)
                )
                // Fuselage
                drawRoundRect(
                    color = Color(0xFFF8FAFC),
                    topLeft = Offset(planePos.x - 44f, planePos.y - 12f),
                    size = Size(88f, 24f),
                    cornerRadius = CornerRadius(12f)
                )
                // Tail
                drawRoundRect(
                    color = Color(0xFFFF6B00),
                    topLeft = Offset(planePos.x - 44f, planePos.y - 24f),
                    size = Size(14f, 48f),
                    cornerRadius = CornerRadius(4f)
                )
            }
        }

        // 13. Floating Damage Popups
        state.floatingDamages.forEach { dmg ->
            val pos = toScreen(dmg.x, dmg.y)
            drawSafeCanvasText(
                textMeasurer = textMeasurer,
                text = dmg.text,
                topLeft = Offset(pos.x - 24f, pos.y - 18f),
                style = TextStyle(
                    color = if (dmg.isCrit) Color(0xFFFFB800) else Color.White,
                    fontSize = if (dmg.isCrit) 15.sp else 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
        }

        // 14. Storm Screen Vignette if player is outside zone
        if (player != null && player.alive && state.phase == MatchPhase.COMBAT) {
            val dZone = kotlin.math.hypot(player.x - state.safeZoneX, player.y - state.safeZoneY)
            if (dZone > state.safeZoneRadius) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x77FF1744)),
                        center = Offset(halfW, halfH),
                        radius = size.maxDimension * 0.72f
                    )
                )
            }
        }

        // 15. Hit Direction Indicators around player
        state.hitIndicators.forEach { ind ->
            val r = 74f
            val ix = halfW + cos(ind.angleFromPlayerRad) * r
            val iy = halfH + sin(ind.angleFromPlayerRad) * r
            rotate(
                degrees = ind.angleFromPlayerRad * (180f / PI.toFloat()) + 90f,
                pivot = Offset(ix, iy)
            ) {
                val path = Path().apply {
                    moveTo(ix, iy - 12f)
                    lineTo(ix - 10f, iy + 8f)
                    lineTo(ix + 10f, iy + 8f)
                    close()
                }
                drawPath(path, color = Color(0xDDFF3B5C))
            }
        }
    }
}

private fun DrawScope.drawCombatant(
    combatant: Combatant,
    pos: Offset,
    zoom: Float,
    accentColor: Color,
    textMeasurer: TextMeasurer
) {
    val scale = zoom * (1f + combatant.parachuteAltitude * 0.45f)
    val bodyRadius = BattleEngine.PLAYER_RADIUS * scale

    // Dash trail aura
    if (combatant.dashTimerSec > 0f) {
        drawCircle(
            color = accentColor.copy(alpha = 0.35f),
            radius = bodyRadius * 1.55f,
            center = pos
        )
    }

    // Shadow
    drawCircle(
        color = Color(0x55000000),
        radius = bodyRadius,
        center = Offset(pos.x + 3f * scale, pos.y + 5f * scale)
    )

    // Gun barrel & muzzle flash
    val deg = combatant.angleRad * (180f / PI.toFloat())
    rotate(degrees = deg, pivot = pos) {
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(pos.x + bodyRadius * 0.4f, pos.y - 4f * scale),
            size = Size(20f * scale, 8f * scale),
            cornerRadius = CornerRadius(3f * scale)
        )
        if (combatant.muzzleFlashSec > 0f) {
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = 9f * scale,
                center = Offset(pos.x + bodyRadius + 16f * scale, pos.y)
            )
        }
    }

    // Body circle
    val fill = if (combatant.hitFlashSec > 0f) Color.White else accentColor
    drawCircle(
        color = fill,
        radius = bodyRadius,
        center = pos
    )
    drawCircle(
        color = Color(0xFF0F172A),
        radius = bodyRadius,
        center = pos,
        style = Stroke(width = 2.5f * scale)
    )

    // Parachute canopy if airborne
    if (combatant.airborne) {
        drawCircle(
            color = Color(0xCCFF6B00),
            radius = bodyRadius * 1.7f,
            center = Offset(pos.x, pos.y - 16f * scale)
        )
        drawCircle(
            color = Color.White,
            radius = bodyRadius * 1.7f,
            center = Offset(pos.x, pos.y - 16f * scale),
            style = Stroke(width = 2f)
        )
    }

    // Health bar for bots when damaged
    if (!combatant.isPlayer && !combatant.airborne) {
        val barW = 36f * zoom
        val barH = 4.5f * zoom
        val hpRatio = (combatant.hp / combatant.maxHp).coerceIn(0f, 1f)
        drawRoundRect(
            color = Color(0xAA000000),
            topLeft = Offset(pos.x - barW / 2f, pos.y - bodyRadius - 12f * zoom),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(2f)
        )
        drawRoundRect(
            color = Color(0xFFFF3B5C),
            topLeft = Offset(pos.x - barW / 2f, pos.y - bodyRadius - 12f * zoom),
            size = Size(barW * hpRatio, barH),
            cornerRadius = CornerRadius(2f)
        )
        drawSafeCanvasText(
            textMeasurer = textMeasurer,
            text = combatant.name,
            topLeft = Offset(pos.x - 26f, pos.y - bodyRadius - 26f * zoom),
            style = TextStyle(
                color = Color(0xCCFFFFFF),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

private fun DrawScope.drawSafeCanvasText(
    textMeasurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    style: TextStyle
) {
    if (size.width <= 0f || size.height <= 0f) return
    val layoutResult = textMeasurer.measure(
        text = text,
        style = style,
        softWrap = false
    )
    if (topLeft.x + layoutResult.size.width < 0f ||
        topLeft.x >= size.width ||
        topLeft.y + layoutResult.size.height < 0f ||
        topLeft.y >= size.height
    ) {
        return
    }
    drawText(
        textLayoutResult = layoutResult,
        topLeft = topLeft
    )
}
