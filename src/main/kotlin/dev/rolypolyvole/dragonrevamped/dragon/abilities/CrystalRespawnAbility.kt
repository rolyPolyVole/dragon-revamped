package dev.rolypolyvole.dragonrevamped.dragon.abilities

import dev.rolypolyvole.dragonrevamped.dragon.DragonAbilityManager
import dev.rolypolyvole.dragonrevamped.dragon.entities.CrystalProtector
import dev.rolypolyvole.dragonrevamped.worldgen.CustomEndSpikes
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

class CrystalRespawnAbility(
    dragon: EnderDragon,
    manager: DragonAbilityManager
) : AbstractDragonAbility(dragon, manager) {

    private val level = this.dragon.level() as ServerLevel

    private val crystalLocations = CustomEndSpikes.getCrystalLocations(this.level)
        .associateWith { 0 }
        .toMutableMap()

    private var cooldown = calculateCooldown()

    private fun calculateCooldown(): Int {
        val playerCount = this.manager.nearbyPlayers().size.coerceAtLeast(1)
        val crystals = getTotalCrystals()

        val min = 400
        val scaled = (1200 / sqrt(playerCount.toDouble())).toInt()
        val extra = crystals * 150

        return min + scaled + extra
    }

    override fun tick() {
        this.crystalLocations.replaceAll { _, value ->
            (value - 1).coerceAtLeast(0)
        }

        if (this.cooldown > 0) {
            this.cooldown--
        } else {
            respawnCrystal()
            this.cooldown = calculateCooldown()
        }
    }

    private fun respawnCrystal() {
        val pos = this.crystalLocations
            .filter { it.value == 0 }.keys
            .filterNot(::doesCrystalExist)
            .randomOrNull() ?: return

        val crystal = EntityType.END_CRYSTAL.create(this.level, EntitySpawnReason.EVENT) ?: return

        crystal.setPos(pos)
        crystal.setShowBottom(false)
        crystal.playSound(SoundEvents.BEACON_ACTIVATE)

        this.level.addFreshEntity(crystal)

        this.manager.broadcastMessage(
            Component.literal("An End Crystal has respawned!").withColor(0xFF55FF)
        )

        sendCrystalCount()
    }

    fun doesCrystalExist(vec: Vec3): Boolean {
        val aabb = AABB(vec.add(-0.5, -0.5, -0.5), vec.add(0.5, 0.5, 0.5)).inflate(1.5)
        return this.level.getEntitiesOfClass(EndCrystal::class.java, aabb).isNotEmpty()
    }

    fun onCrystalDestroyed(pos: BlockPos) {
        val key = this.crystalLocations.keys.minByOrNull { it.distanceToSqr(pos.center) } ?: return
        this.crystalLocations[key] = 600
    }

    fun resetCooldowns() {
        this.crystalLocations.replaceAll { _, _ -> 0 }
    }

    fun getTotalCrystals(): Int {
        val pillarCrystals = this.crystalLocations.keys.count(::doesCrystalExist)
        val protectorCrystals = getProtectorCrystals()
        return pillarCrystals + protectorCrystals
    }

    private fun getProtectorCrystals(): Int {
        val center = this.dragon.fightOrigin.center
        val aabb = AABB.ofSize(center, 400.0, 400.0, 400.0)

        return this.level
            .getEntitiesOfClass(CrystalProtector::class.java, aabb)
            .filter(CrystalProtector::hasCrystal).size
    }

    fun sendCrystalCount() {
        val count = getTotalCrystals()
        val message = Component.literal("✦ Crystals Remaining: $count").withStyle(ChatFormatting.DARK_PURPLE)

        this.manager.broadcastMessage(message, true)
    }
}