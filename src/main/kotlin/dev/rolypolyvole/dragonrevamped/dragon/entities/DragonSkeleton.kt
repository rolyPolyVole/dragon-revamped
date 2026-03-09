package dev.rolypolyvole.dragonrevamped.dragon.entities

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.SpearUseGoal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.pow
import kotlin.math.sqrt

open class DragonSkeleton(level: Level) : Skeleton(EntityType.SKELETON, level) {

    override fun shouldBeSaved(): Boolean = false

    override fun tick() {
        this.noPhysics = vehicle?.noPhysics ?: false

        super.tick()
    }

    override fun aiStep() {
        super.aiStep()

        if (this.vehicle == null) return

        val target = this.target ?: return
        lookAt(target, 360f, 360f)

        this.yBodyRot = this.yRot
        this.yHeadRot = this.yRot
    }

    override fun registerGoals() {
        goalSelector.addGoal(2, SpearUseGoal(this, 1.0, 1.0, 10.0F, 2.0F))
        super.registerGoals()
    }

    override fun setTarget(livingEntity: LivingEntity?) {
        if (livingEntity is EnderDragon || livingEntity is Skeleton) return

        super.setTarget(livingEntity)
    }

    override fun performRangedAttack(target: LivingEntity, pullProgress: Float) {
        val hand = ProjectileUtil.getWeaponHoldingHand(this, Items.BOW)
        val bow = this.getItemInHand(hand)

        val arrowItem = ItemStack(Items.TIPPED_ARROW).apply {
            set(DataComponents.POTION_CONTENTS, PotionContents.EMPTY.withEffectAdded(
                MobEffectInstance(MobEffects.WITHER, 85, 1)
            ))
        }

        val arrow = WitherArrow(this.level(), this, arrowItem, bow)

        val dx = target.x - this.x
        val dy = target.eyeY - arrow.y
        val dz = target.z - this.z

        val level = this.level()
        if (level is ServerLevel) {
            arrow.deltaMovement = computeExactVelocity(dx, dy, dz, 3.0)
            level.addFreshEntity(arrow)
        }

        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.random.nextFloat() * 0.4F + 0.8F))
    }

    private fun computeExactVelocity(dx: Double, dy: Double, dz: Double, maxSpeed: Double): Vec3 {
        val drag = 0.99
        val gravity = 0.05

        for (n in 1..200) {
            val s = (1.0 - drag.pow(n)) / (1.0 - drag)
            val vx = dx / s
            val vz = dz / s
            val vy = (dy + gravity / (1.0 - drag) * (n - s)) / s
            val v = sqrt(vx * vx + vy * vy + vz * vz)
            if (v <= maxSpeed) {
                return Vec3(vx, vy, vz)
            }
        }

        val s = (1.0 - drag.pow(1)) / (1.0 - drag)
        return Vec3(dx / s, (dy + gravity / (1.0 - drag) * (1 - s)) / s, dz / s)
    }
}
