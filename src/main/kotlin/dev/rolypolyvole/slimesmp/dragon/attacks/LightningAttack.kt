package dev.rolypolyvole.slimesmp.dragon.attacks

import dev.rolypolyvole.slimesmp.data.DragonDamageTypes
import dev.rolypolyvole.slimesmp.util.highestBlockY
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.reflect.KClass

class LightningAttack(dragon: EnderDragon) : AbstractDragonAttack(dragon) {

    object LightningAttackType : DragonAttackType {
        override fun invalidPhases(): List<KClass<out DragonPhaseInstance>> = listOf(
            DragonLandingApproachPhase::class,
            DragonLandingPhase::class,
            DragonDeathPhase::class,
            AbstractDragonSittingPhase::class
        )

        override fun canStart(dragon: EnderDragon, lastAttack: AbstractDragonAttack?): Boolean {
            return super.canStart(dragon, lastAttack) && lastAttack !is LightningAttack
        }

        override fun create(dragon: EnderDragon): AbstractDragonAttack {
            return LightningAttack(dragon)
        }
    }

    private val random: Double
        get() = Math.random()

    private lateinit var outpost: Vec3
    private var reachedOutpost = false

    private var ticks = 0
    private var shouldEnd: Boolean = false

    override fun tick() {
        if (!reachedOutpost) return

        if (this.ticks++ == 0) {
            dragon.phaseManager.setPhase(EnderDragonPhase.HOVERING)
            broadcastSound(SoundEvents.ENDER_DRAGON_GROWL)
        }

        nearbyPlayers().filter(::isPlayerExposed).forEach {
            playSound(it, SoundEvents.FLINTANDSTEEL_USE, pitch = 2.0F)
        }

        if (ticks < 50) return

        playEffect()

        nearbyPlayers()
            .filter(::isPlayerExposed)
            .forEach(::strikePlayer)

        this.shouldEnd = true
    }

    override fun beforeMove() {
        if (reachedOutpost) {
            dragon.deltaMovement = Vec3.ZERO
            return
        }

        val delta = outpost.subtract(dragon.position())

        if (delta.lengthSqr() < 256.0) {
            this.reachedOutpost = true
            dragon.deltaMovement = Vec3.ZERO
            return
        }

        val speed = 1.0
        val direction = delta.normalize()
        dragon.deltaMovement = direction.scale(speed)
    }

    override fun start(): Boolean {
        this.outpost = this.getOutpostLocation()

        dragon.phaseManager.setPhase(EnderDragonPhase.CHARGING_PLAYER)
        dragon.phaseManager.getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(outpost)

        broadcastSound(SoundEvents.LIGHTNING_BOLT_THUNDER)

        return true
    }

    override fun end() {
        dragon.phaseManager.setPhase(EnderDragonPhase.HOLDING_PATTERN)
    }

    override fun getSpeedMultiplier(): Float = 1.0f
    override fun getTurnSpeedMultiplier(): Float = 1.0f

    override fun shouldEnd(): Boolean = shouldEnd

    private fun getOutpostLocation(): Vec3 {
        return dragon.fightOrigin.center.add(
            random * 100.0 - 50.0,
            115.0 + random * 35.0,
            random * 100.0 - 50.0
        )
    }

    private fun isPlayerExposed(player: Player): Boolean {
        val blockY = player.blockPosition().highestBlockY(level).y
        val playerY = player.blockPosition().y

        return playerY >= blockY
    }

    private fun strikePlayer(player: Player) {
        EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.MOB_SUMMONED)?.let {
            it.snapTo(player.position())
            it.setVisualOnly(true)
            level.addFreshEntity(it)
        }

        val source = DragonDamageTypes.dragonLightning(level, dragon)
        player.hurtServer(level, source, 11.5f)
    }

    private fun playEffect() {
        EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.MOB_SUMMONED)?.let {
            it.snapTo(dragon.position())
            it.setVisualOnly(true)
            level.addFreshEntity(it)
        }

        broadcastSound(SoundEvents.LIGHTNING_BOLT_THUNDER)
    }
}