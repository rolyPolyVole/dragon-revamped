package dev.rolypolyvole.slimesmp.dragon

import dev.rolypolyvole.slimesmp.dragon.abilities.CrystalRespawnAbility
import dev.rolypolyvole.slimesmp.dragon.abilities.DragonRageAbility
import dev.rolypolyvole.slimesmp.dragon.abilities.HunterSpawnAbility
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.boss.enderdragon.EnderDragon

class DragonAbilityManager(private val dragon: EnderDragon) {
    private val level = dragon.level() as ServerLevel

    val rageAbility = DragonRageAbility(dragon, this)
    val crystalRespawnAbility = CrystalRespawnAbility(dragon, this)
    val hunterSpawnAbility = HunterSpawnAbility(dragon, this)

    fun tick() {
        rageAbility.tick()
        if (rageAbility.isActive()) return

        crystalRespawnAbility.tick()
        hunterSpawnAbility.tick()
    }

    fun nearbyPlayers(): List<ServerPlayer> {
        val center = dragon.fightOrigin.center
        return level.players().filter { it.position().distanceToSqr(center) < 40000.0 }
    }

    fun broadcastSound(sound: SoundEvent, volume: Float = 1.0F, pitch: Float = 1.0F) {
        nearbyPlayers().forEach {
            val packet = ClientboundSoundPacket(
                Holder.direct(sound), SoundSource.HOSTILE,
                it.x, it.y, it.z,
                volume, pitch, level.random.nextLong()
            )

            it.connection.send(packet)
        }
    }

    fun broadcastMessage(message: Component, actionBar: Boolean = false) {
        nearbyPlayers().forEach { it.sendSystemMessage(message, actionBar) }
    }

    fun sendParticles(
        particle: ParticleOptions,
        x: Double, y: Double, z: Double,
        count: Int, dx: Double = 0.0, dy: Double = 0.0, dz: Double = 0.0, speed: Double = 0.0
    ) {
        level.sendParticles(particle, true, true, x, y, z, count, dx, dy, dz, speed)
    }
}
