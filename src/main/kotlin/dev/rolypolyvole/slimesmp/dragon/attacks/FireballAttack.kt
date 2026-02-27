package dev.rolypolyvole.slimesmp.dragon.attacks

import dev.rolypolyvole.slimesmp.dragon.util.ExplosiveDragonFireball
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.*
import net.minecraft.world.phys.Vec3
import kotlin.reflect.KClass

class FireballAttack(dragon: EnderDragon) : AbstractDragonAttack(dragon) {

    object FireballAttackType : DragonAttackType {
        override fun invalidPhases(): List<KClass<out DragonPhaseInstance>> = listOf(
            DragonLandingApproachPhase::class,
            DragonLandingPhase::class,
            DragonDeathPhase::class,
            AbstractDragonSittingPhase::class
        )

        override fun canStart(dragon: EnderDragon, lastAttack: AbstractDragonAttack?): Boolean {
            return super.canStart(dragon, lastAttack) && lastAttack !is FireballAttack
        }

        override fun create(dragon: EnderDragon): AbstractDragonAttack {
            return FireballAttack(dragon)
        }
    }

    private val random: Double
        get() = Math.random()

    private lateinit var outpost: Vec3
    private var reachedOutpost = false

    private var ticks = 0

    private var ticksUntilChangeTarget = 0
    private var target: ServerPlayer? = null

    private var targeted = 0
    private var shouldEnd: Boolean = false

    override fun tick() {
        if (!reachedOutpost) return

        if (ticks == 0) {
            dragon.phaseManager.setPhase(EnderDragonPhase.HOVERING)
            broadcastSound(SoundEvents.ENDER_DRAGON_GROWL)
        }

        if (ticks++ < 40) return

        if (ticks > 15 * 20) {
            this.shouldEnd = true
            return
        }

        if (ticksUntilChangeTarget > 0) {
            this.ticksUntilChangeTarget--
        } else {
            if (random < (targeted / (targeted + 3.0))) {
                this.shouldEnd = true
                return
            }

            this.target = nearbyPlayers()
                .filter { !it.isCreative && !it.isSpectator && it.isAlive }
                .filter { it.position().distanceToSqr(dragon.position()) < 22500.0 }
                .filter { dragon.hasLineOfSight(it) }
                .randomOrNull() ?: run { shouldEnd = true; return }

            this.ticksUntilChangeTarget = (60..90).random()
            this.targeted++
        }

        val target = this.target
        if (target == null || target.hasDisconnected() || !dragon.hasLineOfSight(target)) {
            this.ticksUntilChangeTarget = 0
            return
        }

        val vector = target.position().subtract(dragon.position()).multiply(-1.0, -1.0, -1.0)
        dragon.lookAt(EntityAnchorArgument.Anchor.FEET, dragon.position().add(vector))

        if (ticks % 20 == 0) {
            shootFireballAt(target.position())
        }
    }

    override fun beforeMove() {
        if (reachedOutpost) {
            dragon.deltaMovement = Vec3.ZERO
            return
        }

        val delta = outpost.subtract(dragon.position())

        if (delta.lengthSqr() < 256.0) {
            reachedOutpost = true
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

        broadcastSound(SoundEvents.ENDER_DRAGON_GROWL)

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
            75.0 + random * 35.0,
            random * 100.0 - 50.0
        )
    }

    private fun shootFireballAt(targetPos: Vec3) {
        val facing = targetPos.subtract(dragon.position()).normalize()
        val mouthOffset = facing.multiply(1.0, 0.0, 1.0).normalize().scale(6.5)
        val mouth = dragon.position().add(mouthOffset).subtract(0.0, 0.5, 0.0)
        val velocity = targetPos.subtract(mouth).normalize()

        level.levelEvent(null, 1017, dragon.blockPosition(), 0)

        val fireball = ExplosiveDragonFireball(level, dragon, velocity)
        fireball.snapTo(mouth.x, mouth.y, mouth.z, 0.0f, 0.0f)
        fireball.deltaMovement = velocity.scale(2.5)
        level.addFreshEntity(fireball)
    }
}