package dev.rolypolyvole.dragonrevamped.dragon.abilities

import dev.rolypolyvole.dragonrevamped.dragon.DragonAbilityManager
import dev.rolypolyvole.dragonrevamped.dragon.entities.CrystalProtector
import dev.rolypolyvole.dragonrevamped.util.highestBlockY
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import kotlin.math.sqrt

class HunterSpawnAbility(
    dragon: EnderDragon,
    manager: DragonAbilityManager
) : AbstractDragonAbility(dragon, manager) {

    private val level = this.dragon.level() as ServerLevel

    private var cooldown = calculateCooldown()

    private fun calculateCooldown(): Int {
        val playerCount = this.manager.nearbyPlayers().size.coerceAtLeast(1)

        val base = (1200..1500).random()
        val scaled = (1200 / sqrt(playerCount.toDouble())).toInt()

        return base + scaled
    }

    override fun tick() {
        if (this.cooldown > 0) {
            this.cooldown--
        } else {
            spawnHunter()
            this.cooldown = calculateCooldown()
        }
    }

    private fun spawnHunter() {
        val offsetX = ((-40..40).random()).toDouble()
        val offsetZ = ((-40..40).random()).toDouble()
        val spawnX = this.dragon.fightOrigin.x + offsetX
        val spawnZ = this.dragon.fightOrigin.z + offsetZ
        val groundY = BlockPos(spawnX.toInt(), 0, spawnZ.toInt()).highestBlockY(this.level).y.toDouble()

        CrystalProtector(this.level).let {
            it.setPos(spawnX, groundY, spawnZ)
            it.spawnWithMount(this.level)
        }

        EntityType.LIGHTNING_BOLT.create(this.level, EntitySpawnReason.MOB_SUMMONED)?.let {
            it.snapTo(spawnX, groundY, spawnZ, 0f, 0f)
            it.setVisualOnly(true)
            this.level.addFreshEntity(it)
        }

        this.manager.sendParticles(
            ParticleTypes.EXPLOSION_EMITTER,
            spawnX, groundY + 1.0, spawnZ,
            1
        )

        this.manager.broadcastMessage(
            Component.literal("A Crystal Protector joins the battle!").withColor(0xAA00AA)
        )

        this.manager.crystalRespawnAbility.sendCrystalCount()
    }
}