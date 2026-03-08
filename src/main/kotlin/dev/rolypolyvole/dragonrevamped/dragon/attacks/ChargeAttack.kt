package dev.rolypolyvole.dragonrevamped.dragon.attacks

import dev.rolypolyvole.dragonrevamped.util.xz
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.reflect.KClass

class ChargeAttack(dragon: EnderDragon) : AbstractDragonAttack(dragon) {

    object ChargeAttackType : DragonAttackType {
        override fun invalidPhases(): List<KClass<out DragonPhaseInstance>> = listOf(
            DragonChargePlayerPhase::class,
            DragonLandingApproachPhase::class,
            DragonLandingPhase::class,
            DragonDeathPhase::class,
            AbstractDragonSittingPhase::class
        )

        override fun create(dragon: EnderDragon): AbstractDragonAttack {
            return ChargeAttack(dragon)
        }
    }

    private var ticks = 0

    private var target: ServerPlayer? = null
    private var targetLocation: Vec3? = null
    private var direction: Vec3? = null

    private var resurfacing = false
    private var shouldEnd = false

    var speedMultiplier: Double = 1.0

    override fun tick() {
        this.ticks++

        if (targetLocation != null) {
            val vector = targetLocation!!.subtract(dragon.position()).reverse()
            dragon.lookAt(EntityAnchorArgument.Anchor.FEET, dragon.position().add(vector))
        }
    }

    override fun beforeMove() {
        if (!resurfacing && target == null || target!!.hasDisconnected()) run { this.shouldEnd = true; return }
        if (ticks < 20) return

        if (ticks == 20) {
            this.targetLocation = this.getTargetLocation()
            this.direction = targetLocation!!.subtract(dragon.position()).normalize()
        }

        if (!resurfacing && ticks > 20 && ticks % 5 == 0) {
            val player = target ?: return
            if (!player.hasDisconnected() && player.isAlive) {
                val newTarget = getTargetLocation()
                if (newTarget.distanceToSqr(targetLocation!!) < 100.0) {
                    this.targetLocation = newTarget
                    this.direction = targetLocation!!.subtract(dragon.position()).normalize()

                    chargeTowards(targetLocation!!)
                }
            }
        }

        if (!resurfacing && ticks > 20) {
            val currentTarget = targetLocation ?: return
            val currentDirection = direction ?: return
            val remaining = currentTarget.subtract(dragon.position())

            if (remaining.dot(currentDirection) <= 0 || dragon.position().distanceTo(currentTarget) < 5.0) {
                this.resurfacing = true

                val horizontal = currentDirection.multiply(1.0, 0.0, 1.0).normalize()
                val newDirection = Vec3(horizontal.x, 1.0, horizontal.z).normalize()

                this.targetLocation = dragon.position().add(newDirection.scale(50.0))

                chargeTowards(targetLocation!!)
            }
        }

        val to = (targetLocation ?: return).subtract(dragon.position())
        val movement = to.normalize()
        val speed = (if (resurfacing) 1.2 else 1.8) * speedMultiplier

        dragon.deltaMovement = movement.scale(speed)
    }

    override fun getTurnSpeedMultiplier(): Float {
        return 2.5F
    }

    override fun shouldEnd(): Boolean {
        if (shouldEnd || ticks > 200) return true
        if (resurfacing) return dragon.position().distanceTo(targetLocation!!) < 5.0
        return false
    }

    override fun start(): Boolean {
        this.target = nearbyPlayers()
            .filter(::isTargetValid)
            .randomOrNull()
            ?: run { this.shouldEnd = true; return false }

        this.targetLocation = this.getTargetLocation()

        chargeTowards(targetLocation!!)
        broadcastSound(SoundEvents.ENDER_DRAGON_GROWL, pitch = 0.8F + random * 0.3F)

        return true
    }

    override fun end() {
        restoreNormalPhase()
    }

    private fun isTargetValid(player: Player): Boolean =
        !player.isCreative && !player.isSpectator && player.isAlive &&
        player.position().xz().distanceToSqr(dragon.position().xz()) in 625.0..22500.0

    private fun getTargetLocation(): Vec3 {
        val dragonPos = dragon.position()
        val followThrough = target!!.position().subtract(dragonPos).normalize().scale(10.0)

        return target!!.position().subtract(0.0, 0.5, 0.0).add(followThrough)
    }
}