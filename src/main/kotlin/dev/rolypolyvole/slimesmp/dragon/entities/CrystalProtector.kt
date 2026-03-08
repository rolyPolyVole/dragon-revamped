package dev.rolypolyvole.slimesmp.dragon.entities

import dev.rolypolyvole.slimesmp.util.standingBlockPos
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.equine.Horse
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.equipment.trim.ArmorTrim
import net.minecraft.world.item.equipment.trim.TrimMaterials
import net.minecraft.world.item.equipment.trim.TrimPatterns
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BannerPatternLayers
import net.minecraft.world.level.block.entity.BannerPatterns
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class CrystalProtector(level: Level) : DragonSkeleton(level) {

    private var orbitCrystal: EndCrystal? = null
    private var horse: CrystalProtectorHorse? = null
    private var outOfReachTicks = 0

    init {
        getAttribute(Attributes.MAX_HEALTH)?.baseValue = 40.0
        getAttribute(Attributes.STEP_HEIGHT)?.baseValue = 3.0
        getAttribute(Attributes.FOLLOW_RANGE)?.baseValue = 196.0
        getAttribute(Attributes.SCALE)?.baseValue = 1.2
        getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue = 0.32
        getAttribute(Attributes.SAFE_FALL_DISTANCE)?.baseValue = 100.0

        this.health = maxHealth

        this.customName = Component.literal("Crystal Protector").withStyle(ChatFormatting.DARK_PURPLE)
        this.isCustomNameVisible = true

        EquipmentSlot.entries.forEach { setDropChance(it, 0f) }
    }

    override fun shouldBeSaved(): Boolean = false

    fun hasCrystal(): Boolean = orbitCrystal != null && orbitCrystal!!.isAlive

    fun spawnWithMount(serverLevel: ServerLevel) {
        serverLevel.addFreshEntity(this)

        val horse = CrystalProtectorHorse(serverLevel).apply {
            setPos(this@CrystalProtector.position())
            isTamed = true
            setItemSlot(EquipmentSlot.SADDLE, ItemStack(Items.SADDLE))
            setItemSlot(EquipmentSlot.BODY, ItemStack(Items.NETHERITE_HORSE_ARMOR))
        }

        serverLevel.addFreshEntity(horse)
        this.startRiding(horse, true, true)
        this.horse = horse

        val crystal = OrbitCrystal(serverLevel).apply {
            setShowBottom(false)
            isInvulnerable = true
            setPos(this@CrystalProtector.position())
        }

        serverLevel.addFreshEntity(crystal)
        this.orbitCrystal = crystal

        equipArmor()
    }

    fun equipArmor() {
        val registryAccess = level().registryAccess()

        val trimPatterns = registryAccess.lookupOrThrow(Registries.TRIM_PATTERN)
        val trimMaterials = registryAccess.lookupOrThrow(Registries.TRIM_MATERIAL)
        val enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
        val bannerPatterns = registryAccess.lookupOrThrow(Registries.BANNER_PATTERN)

        val amethyst = trimMaterials.get(TrimMaterials.AMETHYST).orElse(null) ?: return
        val sentryPattern = trimPatterns.get(TrimPatterns.SENTRY).orElse(null) ?: return
        val flowPattern = trimPatterns.get(TrimPatterns.FLOW).orElse(null) ?: return
        val protection = enchantments.getOrThrow(Enchantments.PROTECTION)
        val thorns = enchantments.getOrThrow(Enchantments.THORNS)
        val knockback = enchantments.getOrThrow(Enchantments.KNOCKBACK)
        val unbreaking = enchantments.getOrThrow(Enchantments.UNBREAKING)

        val chestplate = ItemStack(Items.NETHERITE_CHESTPLATE).apply {
            set(DataComponents.TRIM, ArmorTrim(amethyst, sentryPattern))
            enchant(protection, 4)
            enchant(thorns, 3)
        }

        val helmet = ItemStack(Items.NETHERITE_HELMET).apply {
            set(DataComponents.TRIM, ArmorTrim(amethyst, flowPattern))
            enchant(protection, 4)
            enchant(thorns, 3)
        }

        setItemSlot(EquipmentSlot.HEAD, helmet)
        setItemSlot(EquipmentSlot.CHEST, chestplate)

        setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.NETHERITE_SPEAR).apply {
            enchant(knockback, 2)
        })

        val shield = ItemStack(Items.SHIELD).apply {
            set(DataComponents.BASE_COLOR, DyeColor.BROWN)
            set(DataComponents.BANNER_PATTERNS, BannerPatternLayers.Builder()
                .add(bannerPatterns.getOrThrow(BannerPatterns.GRADIENT), DyeColor.BLACK)
                .add(bannerPatterns.getOrThrow(BannerPatterns.GRADIENT_UP), DyeColor.BLACK)
                .add(bannerPatterns.getOrThrow(BannerPatterns.RHOMBUS_MIDDLE), DyeColor.PURPLE)
                .add(bannerPatterns.getOrThrow(BannerPatterns.CROSS), DyeColor.GRAY)
                .add(bannerPatterns.getOrThrow(BannerPatterns.FLOWER), DyeColor.BLACK)
                .build()
            )
            enchant(unbreaking, 3)
        }

        setItemSlot(EquipmentSlot.OFFHAND, shield)
    }

    override fun setTarget(livingEntity: LivingEntity?) {
        if (livingEntity is EnderDragon || livingEntity is DragonSkeleton) return
        super.setTarget(livingEntity)
    }

    override fun tick() {
        updateOrbitCrystal()
        swapWeapon()
        super.tick()
    }

    override fun aiStep() {
        super.aiStep()

        if (vehicle is EndCrystal) {
            val target = this.target ?: return

            if (distanceToSqr(target) < 20.0 * 20.0) {
                stopRiding()
            }
        }
    }

    override fun getMaxFallDistance(): Int = Int.MAX_VALUE

    private fun swapWeapon() {
        val target = this.target ?: return
        val distSqr = distanceToSqr(target)
        val enchantments = level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)

        val standingPos = target.standingBlockPos()
        val path = navigation.createPath(standingPos, 0)
        val canReach = path != null && path.canReach()

        if (canReach) outOfReachTicks = 0 else outOfReachTicks++

        val desired = when {
            outOfReachTicks >= 20 -> Items.BOW
            distSqr < 2.0 * 2.0 -> Items.NETHERITE_SWORD
            distSqr < 30.0 * 30.0 -> Items.NETHERITE_SPEAR
            else -> Items.BOW
        }

        if (mainHandItem.item == desired) return

        setItemSlot(EquipmentSlot.MAINHAND, when (desired) {
            Items.NETHERITE_SWORD -> ItemStack(desired).apply {
                enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 3)
            }
            Items.NETHERITE_SPEAR -> ItemStack(desired).apply {
                enchant(enchantments.getOrThrow(Enchantments.KNOCKBACK), 2)
            }
            else -> ItemStack(desired).apply {
                enchant(enchantments.getOrThrow(Enchantments.POWER), 3)
                enchant(enchantments.getOrThrow(Enchantments.PUNCH), 1)
            }
        })
    }

    private fun updateOrbitCrystal() {
        val crystal = orbitCrystal ?: return

        if (!crystal.isAlive) {
            orbitCrystal = null
            return
        }

        val angle = tickCount * 0.2
        val radius = 1.9 + sin(tickCount * 0.18) * 0.2
        val bobY = sin(tickCount * 0.18) * 0.35

        val newX = x + cos(angle) * radius
        val newY = y + bobY + 0.5
        val newZ = z + sin(angle) * radius

        crystal.teleportTo(newX, newY, newZ)

        val serverLevel = level() as? ServerLevel ?: return
        val packet = ClientboundEntityPositionSyncPacket.of(crystal)
        serverLevel.chunkSource.sendToTrackingPlayers(crystal, packet)
    }

    override fun hurtServer(serverLevel: ServerLevel, damageSource: DamageSource, amount: Float): Boolean {
        val result = super.hurtServer(serverLevel, damageSource, amount)

        if (result && damageSource.entity is ServerPlayer) {
            val player = damageSource.entity as ServerPlayer
            val health = health.roundToInt()
            val message = Component.literal("❤ Protector Health: $health").withStyle(ChatFormatting.DARK_AQUA)

            player.sendSystemMessage(message, true)
        }

        return result
    }

    override fun remove(reason: RemovalReason) {
        orbitCrystal?.discard()
        orbitCrystal = null
        super.remove(reason)
    }

    override fun die(damageSource: DamageSource) {
        orbitCrystal?.let {
            it.isInvulnerable = false
            it.hurtServer(level() as ServerLevel, this.damageSources().mobAttack(this), 1.0F)
        }

        this.orbitCrystal = null

        super.die(damageSource)
    }

    class OrbitCrystal(level: Level) : EndCrystal(EntityType.END_CRYSTAL, level) {
        override fun shouldBeSaved(): Boolean = false
    }

    class CrystalProtectorHorse(level: Level) : Horse(EntityType.HORSE, level) {

        private var canStand: Boolean = true

        init {
            getAttribute(Attributes.STEP_HEIGHT)?.baseValue = 3.0
            getAttribute(Attributes.MAX_HEALTH)?.baseValue = 70.0
            getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue = 0.9
            getAttribute(Attributes.SCALE)?.baseValue = 1.2
            getAttribute(Attributes.SAFE_FALL_DISTANCE)?.baseValue = 100.0

            health = maxHealth

            EquipmentSlot.entries.forEach { setDropChance(it, 0f) }
        }

        override fun shouldBeSaved(): Boolean = false

        override fun setTarget(livingEntity: LivingEntity?) {
            if (livingEntity is EnderDragon || livingEntity is DragonSkeleton) return
            super.setTarget(livingEntity)
        }

        override fun tick() {
            val hasSkeleton = firstPassenger is DragonSkeleton
            val speed = if (hasSkeleton) 0.43 else 0.25
            val health = if (hasSkeleton) 70.0 else 25.0

            getAttribute(Attributes.MOVEMENT_SPEED)!!.baseValue = speed
            getAttribute(Attributes.MAX_HEALTH)!!.baseValue = health

            super.tick()
        }

        override fun hurtServer(serverLevel: ServerLevel, damageSource: DamageSource, f: Float): Boolean {
            this.canStand = false
            val result = super.hurtServer(serverLevel, damageSource, f)
            this.canStand = true
            return result
        }

        override fun canPerformRearing(): Boolean = canStand
    }
}
