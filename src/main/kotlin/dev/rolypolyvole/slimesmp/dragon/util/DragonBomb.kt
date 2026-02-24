package dev.rolypolyvole.slimesmp.dragon.util

import com.mojang.math.Transformation
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Brightness
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Marker
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

class DragonBomb(level: Level, private val dragon: EnderDragon) : Marker(EntityType.MARKER, level) {
    private val display: NonPersistentBlockDisplay = createDisplay()
    private var velocity = Vec3.ZERO

    init {
        this.noPhysics = false
        this.setPos(dragon.position())
    }

    fun setVelocity(velocity: Vec3) {
        this.velocity = velocity
    }

    override fun tick() {
        super.tick()

        applyDownwardsAcceleration()
        move()

        if (isCollidingWithGround()) {
            return explode()
        }

        animate()
        playParticles()
    }

    override fun remove(reason: RemovalReason) {
        display.discard()
        super.remove(reason)
    }

    override fun shouldBeSaved() = false

    private fun applyDownwardsAcceleration() {
        this.velocity = velocity.add(0.0, -0.04, 0.0)
    }

    private fun move() {
        setPos(x + velocity.x, y + velocity.y, z + velocity.z)
    }

    private fun isCollidingWithGround(): Boolean {
        val below = blockPosition().below()
        return !level().getBlockState(below).isAir
    }

    private fun explode() {
        val groundY = blockPosition().below().y + 1.1

        (level() as ServerLevel).explode(
            this,
            this.damageSources().explosion(this, dragon),
            null,
            x, groundY, z,
            5.5F,
            false,
            Level.ExplosionInteraction.TNT
        )

        remove(RemovalReason.DISCARDED)
    }

    private fun createDisplay(): NonPersistentBlockDisplay {
        val display = NonPersistentBlockDisplay(level())

        display.setPos(x, y, z)
        display.blockState = net.minecraft.world.level.block.Blocks.DRAGON_HEAD.defaultBlockState()
        display.brightnessOverride = Brightness(15, 15)
        display.setTransformation(createTransformation(0f))

        level().addFreshEntity(display)
        display.startRiding(this)

        return display
    }

    private fun animate() {
        display.posRotInterpolationDuration = 1
        display.transformationInterpolationDuration = 1

        display.setPos(position())
        display.setTransformation(createTransformation(tickCount * 0.1F))
    }

    private fun playParticles() {
        (level() as ServerLevel).sendParticles(
            PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f),
            x, y, z,
            1, 0.0, 0.0, 0.0, 0.0
        )
    }

    private fun createTransformation(angle: Float): Transformation {
        return Transformation(
            Matrix4f()
                .translationRotateScale(Vector3f(), Quaternionf().rotateY(angle), Vector3f(SCALE))
                .translate(OFFSET)
                .translate(0.35f, 0.1f, 0.4025f)
        )
    }

    companion object {
        private const val SCALE = 1.7f
        private val OFFSET = Vector3f(-0.85f, -0.2f, -1.0f)
    }
}

class NonPersistentBlockDisplay(level: Level) : Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level) {
    override fun shouldBeSaved(): Boolean = false
}
