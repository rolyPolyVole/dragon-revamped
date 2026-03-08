package dev.rolypolyvole.dragonrevamped.dragon.abilities

import dev.rolypolyvole.dragonrevamped.dragon.DragonAbilityManager
import dev.rolypolyvole.dragonrevamped.dragon.entities.CrystalProtector
import dev.rolypolyvole.dragonrevamped.worldgen.CustomEndSpikes
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

// needs format code

class DragonRageAbility(
    dragon: EnderDragon,
    manager: DragonAbilityManager
) : AbstractDragonAbility(dragon, manager) {
    enum class State {
        IDLE, ASCENDING, GATHERING, EXPLODING, ROARING, DONE
    }

    var state = State.IDLE
        private set

    private var stateTicks = 0
    private var hasPerformed = false
    private var firstTick = true

    private val level = this.dragon.level() as ServerLevel
    private val center get() = Vec3(0.0, 128.0, 0.0)

    private data class FloatingBlock(
        val display: Display.BlockDisplay,
        val blockState: BlockState,
        val startPos: Vec3
    )

    private val floatingBlocks = mutableListOf<FloatingBlock>()

    override fun isActive() = state != State.IDLE && state != State.DONE

    override fun onBeforeMove() {
        if (this.state == State.ASCENDING) {
            val current = this.dragon.position()
            val direction = this.center.subtract(current)
            val dist = direction.length()

            this.dragon.deltaMovement = if (dist > 2.0) {
                direction.normalize().scale(1.6)
            } else {
                Vec3.ZERO
            }
        } else if (this.isActive()) {
            this.dragon.deltaMovement = Vec3.ZERO
        }
    }

    override fun tick() {
        when (state) {
            State.IDLE -> checkTrigger()
            State.ASCENDING -> tickAscending()
            State.GATHERING -> tickGathering()
            State.EXPLODING -> tickExploding()
            State.ROARING -> tickRoaring()
            State.DONE -> checkReset()
        }
    }

    private fun checkTrigger() {
        if (this.firstTick) {
            this.firstTick = false

            if (this.dragon.health <= this.dragon.maxHealth * 0.5f) {
                this.hasPerformed = true
            }

            return
        }

        if (this.hasPerformed) return

        if (this.dragon.health <= this.dragon.maxHealth * 0.5f) {
            transition(State.ASCENDING)
            this.dragon.phaseManager.setPhase(EnderDragonPhase.HOVERING)
        }
    }

    private fun checkReset() {
        if (this.dragon.health >= this.dragon.maxHealth) {
            this.hasPerformed = false
            this.state = State.IDLE
        }
    }

    private fun transition(newState: State) {
        this.state = newState
        this.stateTicks = 0
    }

    private fun tickAscending() {
        this.stateTicks++

        this.dragon.phaseManager.setPhase(EnderDragonPhase.HOVERING)

        if (this.stateTicks <= 20) {
            this.manager.broadcastSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 1.5f - this.stateTicks * 0.05f)
        }

        val angle = this.stateTicks * 0.3
        val radius = 3.0
        val px = this.dragon.x + cos(angle) * radius
        val pz = this.dragon.z + sin(angle) * radius

        this.manager.sendParticles(
            PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F),
            px, this.dragon.y + 1.0, pz,
            5, 0.2, 0.5, 0.2, 0.0
        )

        if (this.stateTicks >= 80) {
            spawnBlockDisplays()
            transition(State.GATHERING)
        }
    }

    private fun tickGathering() {
        this.stateTicks++

        this.dragon.phaseManager.setPhase(EnderDragonPhase.HOVERING)

        val progress = this.stateTicks / 80.0
        val target = this.dragon.position().add(0.0, 2.0, 0.0)

        for (block in this.floatingBlocks) {
            val lerped = block.startPos.lerp(target, progress)
            block.display.teleportTo(lerped.x, lerped.y, lerped.z)
        }

        if (this.stateTicks % 20 == 0) {
            this.manager.broadcastSound(SoundEvents.BEACON_ACTIVATE, pitch = 0.5f + progress.toFloat())
        }

        this.manager.sendParticles(
            ParticleTypes.REVERSE_PORTAL,
            this.dragon.x, this.dragon.y + 2.0, this.dragon.z,
            20, 3.0, 3.0, 3.0, 0.1
        )

        if (this.stateTicks >= 80) {
            transition(State.EXPLODING)
        }
    }

    private fun tickExploding() {
        val explosionCenter = this.dragon.position().add(0.0, 2.0, 0.0)

        this.level.explode(
            this.dragon, this.dragon.damageSources().dragonBreath(),
            null,
            explosionCenter.x, explosionCenter.y, explosionCenter.z,
            12.0f, false,
            Level.ExplosionInteraction.NONE
        )

        for (block in this.floatingBlocks) {
            val display = block.display
            display.discard()

            val falling = FallingBlockEntity.fall(this.level, display.blockPosition(), block.blockState)
            falling.dropItem = false
            falling.time = 1
            falling.disableDrop()

            val direction = Vec3(
                1.0 - this.level.random.nextDouble() * 2.0,
                0.0,
                1.0 - this.level.random.nextDouble() * 2.0
            ).normalize()

            val speed = 1.0 + this.level.random.nextDouble() * 1.5

            falling.deltaMovement = direction
                .scale(speed)
                .add(0.0, 0.5 + this.level.random.nextDouble(), 0.0)
        }

        this.floatingBlocks.clear()

        this.manager.broadcastSound(SoundEvents.LIGHTNING_BOLT_THUNDER, pitch = 0.5f)
        this.manager.broadcastSound(SoundEvents.GENERIC_EXPLODE.value(), pitch = 0.5f)

        this.manager.sendParticles(
            ParticleTypes.EXPLOSION_EMITTER,
            explosionCenter.x, explosionCenter.y, explosionCenter.z,
            5, 2.0, 2.0, 2.0
        )

        transition(State.ROARING)
    }

    private fun tickRoaring() {
        this.stateTicks++

        this.dragon.phaseManager.setPhase(EnderDragonPhase.HOVERING)

        if (this.stateTicks <= 20) {
            this.manager.broadcastSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.5f + this.stateTicks * 0.05f)
        }

        if (this.stateTicks == 20) {
            respawnAllCrystals()

            this.manager.broadcastMessage(
                Component.empty()
                    .append(Component.literal("[Ender Dragon] ").withStyle(ChatFormatting.DARK_PURPLE))
                    .append(
                        Component.literal("My strongest soldiers have arrived to end this battle!").withStyle(
                            ChatFormatting.WHITE))
            )
        }

        if (this.stateTicks % 5 == 0) {
            this.manager.sendParticles(
                PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F),
                this.dragon.x, this.dragon.y + 1.0, this.dragon.z,
                30, 4.0, 2.0, 4.0
            )
        }

        if (this.stateTicks >= 80) {
            this.hasPerformed = true
            transition(State.DONE)
            this.dragon.phaseManager.setPhase(EnderDragonPhase.HOLDING_PATTERN)
        }
    }

    private fun spawnBlockDisplays() {
        val random = this.level.random

        repeat(150) {
            val num = random.nextFloat()

            val state = when {
                num > 0.35f -> Blocks.END_STONE.defaultBlockState()
                num > 0.175f -> Blocks.OBSIDIAN.defaultBlockState()
                else -> Blocks.CRYING_OBSIDIAN.defaultBlockState()
            }

            val angle = random.nextDouble() * Math.PI * 2
            val dist = 30.0 + random.nextDouble() * 45.0
            val startPos = this.center.add(
                cos(angle) * dist,
                -20.0 + random.nextDouble() * 40.0,
                sin(angle) * dist
            )

            val display = Display.BlockDisplay(EntityType.BLOCK_DISPLAY, this.level)
            display.blockState = state
            display.setPos(startPos.x, startPos.y, startPos.z)

            this.level.addFreshEntity(display)
            this.floatingBlocks.add(FloatingBlock(display, state, startPos))
        }
    }

    private fun respawnAllCrystals() {
        val crystalLocations = CustomEndSpikes.getCrystalLocations(this.level)

        for (pos in crystalLocations) {
            val existing = this.level.getEntitiesOfClass(
                EndCrystal::class.java,
                AABB.ofSize(pos, 3.0, 3.0, 3.0)
            )

            existing.forEach { it.discard() }

            val protectors = this.level.getEntitiesOfClass(
                Skeleton::class.java,
                AABB.ofSize(pos, 3.0, 3.0, 3.0)
            )

            protectors.forEach { it.discard() }

            val crystal = EntityType.END_CRYSTAL.create(this.level, EntitySpawnReason.EVENT) ?: continue
            crystal.setPos(pos)
            crystal.setShowBottom(false)
            this.level.addFreshEntity(crystal)

            EntityType.LIGHTNING_BOLT.create(this.level, EntitySpawnReason.MOB_SUMMONED)?.let {
                it.snapTo(pos.x, pos.y, pos.z, 0f, 0f)
                it.setVisualOnly(true)
                this.level.addFreshEntity(it)
            }

            val protector = CrystalProtector(this.level)
            protector.equipArmor()
            protector.startRiding(crystal, true, false)

            this.level.addFreshEntity(protector)
        }

        this.manager.crystalRespawnAbility.resetCooldowns()
        this.manager.crystalRespawnAbility.sendCrystalCount()
    }
}