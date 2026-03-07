package dev.rolypolyvole.slimesmp.dragon

import dev.rolypolyvole.slimesmp.dragon.entities.CrystalProtector
import dev.rolypolyvole.slimesmp.util.highestBlockY
import dev.rolypolyvole.slimesmp.worldgen.CustomEndSpikes
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt


class DragonAbilityManager(private val dragon: EnderDragon) {
    private val level = dragon.level() as ServerLevel

    private val crystalLocations = CustomEndSpikes.getCrystalLocations(level)
        .associateWith { 0 }
        .toMutableMap()

    private var ticks = 0
    private var ticksUntilCrystalRespawn = crystalRespawnTime()
    private var ticksUntilHunterSpawn = hunterRespawnTime()

    private fun crystalRespawnTime(): Int {
        val playerCount = nearbyPlayers().size.coerceAtLeast(1)
        val crystals = getTotalCrystals()

        val min = 500
        val scaled = (750 / sqrt(playerCount.toDouble())).toInt()
        val extra = crystals * 150

        return min + scaled + extra
    }

    private fun hunterRespawnTime(): Int {
        val playerCount = nearbyPlayers().size.coerceAtLeast(1)

        val base = (1200..1500).random()
        val scaled = (1200 / sqrt(playerCount.toDouble())).toInt()

        return base + scaled
    }

    fun tick () {
        this.ticks++

        crystalLocations.replaceAll { _, value ->
            (value - 1).coerceAtLeast(0)
        }

        if (ticksUntilCrystalRespawn > 0) {
            this.ticksUntilCrystalRespawn--
        } else {
            respawnCrystal()
            this.ticksUntilCrystalRespawn = crystalRespawnTime()
        }

        if (ticksUntilHunterSpawn > 0) {
            this.ticksUntilHunterSpawn--
        } else {
            spawnCrystalHunter()
            this.ticksUntilHunterSpawn = hunterRespawnTime()
        }
    }

    private fun nearbyPlayers(): List<ServerPlayer> {
        val center = dragon.fightOrigin.center
        return level.players().filter{ it.position().distanceToSqr(center) < 40000.0 }
    }

    private fun respawnCrystal() {
        val pos = crystalLocations
            .filter { it.value == 0 }.keys
            .filterNot(::doesCrystalExist)
            .randomOrNull() ?: return

        crystalLocations[pos] = 600

        val crystal = EntityType.END_CRYSTAL.create(level, EntitySpawnReason.EVENT) ?: return

        crystal.setPos(pos)
        crystal.setShowBottom(false)
        crystal.playSound(SoundEvents.BEACON_ACTIVATE)

        level.addFreshEntity(crystal)

        nearbyPlayers().forEach {
            it.sendSystemMessage(Component.literal("An End Crystal has respawned!").withColor(0xFF55FF))
        }
        sendCrystalCount()
    }

    private fun spawnCrystalHunter() {
        val offsetX = ((-40..40).random()).toDouble()
        val offsetZ = ((-40..40).random()).toDouble()
        val spawnX = dragon.fightOrigin.x + offsetX
        val spawnZ = dragon.fightOrigin.z + offsetZ
        val groundY = BlockPos(spawnX.toInt(), 0, spawnZ.toInt()).highestBlockY(level).y.toDouble()

        CrystalProtector(level).let {
            it.setPos(spawnX, groundY, spawnZ)
            it.spawnWithMount(level)
        }

        EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.MOB_SUMMONED)?.let {
            it.snapTo(spawnX, groundY, spawnZ, 0f, 0f)
            it.setVisualOnly(true)
            level.addFreshEntity(it)
        }

        level.sendParticles(
            ParticleTypes.EXPLOSION_EMITTER,
            true, true,
            spawnX, groundY + 1.0, spawnZ,
            1, 0.0, 0.0, 0.0, 0.0
        )

        nearbyPlayers().forEach {
            it.sendSystemMessage(Component.literal("A Crystal Protector joins the battle!").withColor(0xAA00AA))
        }
    }

    private fun doesCrystalExist(vec: Vec3): Boolean {
        val aabb = AABB(vec.add(-0.5, -0.5, -0.5), vec.add(0.5, 0.5, 0.5)).inflate(1.5)

        return !level.getEntitiesOfClass(EndCrystal::class.java, aabb).isEmpty()
    }

    private fun getTotalCrystals(): Int {
        return crystalLocations.keys.filter(::doesCrystalExist).size
    }

    fun sendCrystalCount() {
        val count = getTotalCrystals()
        val message = Component.literal("✦ Crystals Remaining: $count").withStyle(ChatFormatting.DARK_PURPLE)

        nearbyPlayers().forEach { it.sendSystemMessage(message, true) }
    }
}
