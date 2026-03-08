package dev.rolypolyvole.dragonrevamped.dragon.attacks

import dev.rolypolyvole.dragonrevamped.dragon.entities.ExplosiveDragonFireball
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.*
import net.minecraft.world.phys.Vec3
import kotlin.math.*
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

    private lateinit var outpost: Vec3
    private var reachedOutpost = false

    private var roarTicks = 0
    private var ticks = 0

    private val minTargets = ceil(sqrt(nearbyPlayers().size.toDouble()))
    private var ticksUntilChangeTarget = 0
    private var target: ServerPlayer? = null

    private var targeted = 0
    private var shouldEnd: Boolean = false

    override fun tick() {
        if (roarTicks++ <= 10) {
            broadcastSound(SoundEvents.ENDER_DRAGON_GROWL, pitch = 0.8F + random * 0.3F)
        }

        if (!reachedOutpost) return

        if (ticks == 0) {
            hover()
            broadcastSound(SoundEvents.ENDER_DRAGON_GROWL, pitch = 0.8F + random * 0.3F)
        }

        spawnMouthBreathParticles()

        if (ticks++ < 40) return

        if (ticks > 15 * 20) {
            this.shouldEnd = true
            return
        }

        if (ticksUntilChangeTarget > 0) {
            this.ticksUntilChangeTarget--
        } else {
            if (targeted > minTargets && random < (targeted / (targeted + 3.0))) {
                this.shouldEnd = true
                return
            }

            this.target = nearbyPlayers()
                .filter { !it.isCreative && !it.isSpectator && it.isAlive }
                .filter { it.position().distanceToSqr(dragon.position()) < 22500.0 }
                .filter { dragon.hasLineOfSight(it) }
                .randomOrNull() ?: run { shouldEnd = true; return }

            this.ticksUntilChangeTarget = (45..65).random()
            this.targeted++
        }

        val target = this.target
        if (target == null || target.hasDisconnected() || !dragon.hasLineOfSight(target)) {
            this.ticksUntilChangeTarget = 0
            return
        }

        smoothLookAt(target.position())

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

        chargeTowards(outpost)
        broadcastSound(SoundEvents.ENDER_DRAGON_GROWL, pitch = 0.8F + random * 0.3F)

        return true
    }

    override fun end() {
        restoreNormalPhase()
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

    private fun smoothLookAt(targetPos: Vec3) {
        val dx = targetPos.x - dragon.x
        val dz = targetPos.z - dragon.z
        val targetYaw = 180.0F - (atan2(dx, dz) * (180.0 / Math.PI)).toFloat()
        dragon.yRot = Mth.approachDegrees(dragon.yRot, targetYaw, 9.0f)
    }

    private fun spawnMouthBreathParticles() {
        val mouth = Vec3(dragon.head.x, dragon.head.getY(0.5), dragon.head.z)

        val radius = 0.7

        repeat(20) {
            val theta = random * Math.PI * 2
            val phi = acos(2.0 * random - 1.0)
            val r = radius * cbrt(random)
            val x = r * sin(phi) * cos(theta)
            val y = r * sin(phi) * sin(theta)
            val z = r * cos(phi)

            level.sendParticles(
                PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F),
                true, true,
                mouth.x + x, mouth.y + y, mouth.z + z,
                1, 0.0, 0.0, 0.0, 0.0
            )
        }
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