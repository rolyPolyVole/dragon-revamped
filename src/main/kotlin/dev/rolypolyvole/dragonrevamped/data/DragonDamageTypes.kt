package dev.rolypolyvole.dragonrevamped.data

import dev.rolypolyvole.dragonrevamped.DragonRevamped
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.boss.enderdragon.EnderDragon

object DragonDamageTypes {
    val DRAGON_LIGHTNING: ResourceKey<DamageType> = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath(DragonRevamped.MOD_ID, "dragon_lightning")
    )

    val DRAGON_BODY_HIT: ResourceKey<DamageType> = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath(DragonRevamped.MOD_ID, "dragon_body_hit")
    )

    fun dragonLightning(level: ServerLevel): Holder<DamageType> {
        return level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE)
            .get(DRAGON_LIGHTNING.identifier())
            .orElseThrow()
    }

    fun dragonLightning(level: ServerLevel, dragon: EnderDragon): DamageSource {
        return DamageSource(dragonLightning(level), dragon)
    }

    fun dragonBodyHit(level: ServerLevel): Holder<DamageType> {
        return level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE)
            .get(DRAGON_BODY_HIT.identifier())
            .orElseThrow()
    }

    fun dragonBodyHit(level: ServerLevel, dragon: EnderDragon): DamageSource {
        return DamageSource(dragonBodyHit(level), dragon)
    }
}