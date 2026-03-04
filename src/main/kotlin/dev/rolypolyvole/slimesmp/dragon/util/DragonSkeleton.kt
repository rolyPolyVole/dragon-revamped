package dev.rolypolyvole.slimesmp.dragon.util

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.SpearUseGoal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import kotlin.math.sqrt

class DragonSkeleton(level: Level) : Skeleton(EntityType.SKELETON, level) {

    override fun shouldBeSaved(): Boolean = false

    override fun tick() {
        this.noPhysics = vehicle?.noPhysics ?: false

        super.tick()
    }

    override fun aiStep() {
        super.aiStep()

        val target = level().getNearestPlayer(this, 128.0) ?: return
        lookAt(target, 360f, 360f)

        this.yBodyRot = this.yRot
        this.yHeadRot = this.yRot
    }

    override fun registerGoals() {
        goalSelector.addGoal(2, SpearUseGoal(this, 1.0, 1.0, 30.0F, 2.0F))
        super.registerGoals()
    }

    override fun setTarget(livingEntity: LivingEntity?) {
        if (livingEntity is EnderDragon || livingEntity is Skeleton) return

        super.setTarget(livingEntity)
    }

    override fun performRangedAttack(target: LivingEntity, pullProgress: Float) {
        val hand = ProjectileUtil.getWeaponHoldingHand(this, Items.BOW)
        val bow = this.getItemInHand(hand)
        val arrow = BlindnessArrow(this.level(), this, ItemStack(Items.ARROW), bow)

        val dx = target.x - this.x
        val dy = target.getY(0.3333333333333333) - arrow.y
        val dz = target.z - this.z
        val horizontalDist = sqrt(dx * dx + dz * dz)

        val level = this.level()
        if (level is ServerLevel) {
            Projectile.spawnProjectileUsingShoot(
                arrow, level, ItemStack(Items.ARROW),
                dx, dy + horizontalDist * 0.2, dz,
                3.0F, 0.0F
            )
        }

        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.random.nextFloat() * 0.4F + 0.8F))
    }
}
