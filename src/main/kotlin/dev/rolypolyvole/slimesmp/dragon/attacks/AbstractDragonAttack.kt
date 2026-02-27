package dev.rolypolyvole.slimesmp.dragon.attacks

import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance
import kotlin.reflect.KClass

abstract class AbstractDragonAttack(protected val dragon: EnderDragon) {
    protected val level: ServerLevel
        get() = dragon.level() as ServerLevel

    protected fun nearbyPlayers(): List<ServerPlayer> {
        val center = dragon.fightOrigin.center
        return level.players().filter { it.position().distanceToSqr(center) < 40000.0 }
    }

    protected fun broadcastSound(sound: SoundEvent, volume: Float = 1.0F, pitch: Float = 1.0F) {
        nearbyPlayers().forEach { playSound(it, sound, volume, pitch) }
    }

    protected fun playSound(player: ServerPlayer, sound: SoundEvent, volume: Float = 1.0F, pitch: Float = 1.0F) {
        val packet = ClientboundSoundPacket(
            Holder.direct(sound), SoundSource.HOSTILE,
            player.x, player.y, player.z,
            volume, pitch, level.random.nextLong()
        )

        player.connection.send(packet)
    }

    abstract fun tick()
    open fun beforeMove() {}

    open fun getSpeedMultiplier(): Float = 1.0f
    open fun getTurnSpeedMultiplier(): Float = 1.0f

    abstract fun shouldEnd(): Boolean

    abstract fun start(): Boolean
    abstract fun end()
}

interface DragonAttackType {
    fun canStart(dragon: EnderDragon, lastAttack: AbstractDragonAttack?): Boolean {
        return invalidPhases().none { it.isInstance(dragon.phaseManager.currentPhase) }
    }

    fun invalidPhases(): List<KClass<out DragonPhaseInstance>>
    fun create(dragon: EnderDragon): AbstractDragonAttack
}