package dev.rolypolyvole.slimesmp.commands

import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments

class GearUpCommand {
    private val name = "gearup"

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
            if (!environment.includeDedicated) return@register

            val command = Commands
                .literal(name)
                .executes(this::onExecution)

            dispatcher.register(command)
        }
    }

    private fun onExecution(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val player = source.playerOrException
        val enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)

        player.inventory.clearContent()

        val helmet = ItemStack(Items.NETHERITE_HELMET).apply {
            enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4)
        }
        val chestplate = ItemStack(Items.NETHERITE_CHESTPLATE).apply {
            enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4)
        }
        val leggings = ItemStack(Items.NETHERITE_LEGGINGS).apply {
            enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4)
        }
        val boots = ItemStack(Items.NETHERITE_BOOTS).apply {
            enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4)
            enchant(enchantments.getOrThrow(Enchantments.FEATHER_FALLING), 4)
        }

        player.setItemSlot(EquipmentSlot.HEAD, helmet)
        player.setItemSlot(EquipmentSlot.CHEST, chestplate)
        player.setItemSlot(EquipmentSlot.LEGS, leggings)
        player.setItemSlot(EquipmentSlot.FEET, boots)

        val sword = ItemStack(Items.NETHERITE_SWORD).apply {
            enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 5)
            enchant(enchantments.getOrThrow(Enchantments.LOOTING), 3)
        }
        val bow = ItemStack(Items.BOW).apply {
            enchant(enchantments.getOrThrow(Enchantments.POWER), 5)
        }
        val mace = ItemStack(Items.MACE).apply {
            enchant(enchantments.getOrThrow(Enchantments.DENSITY), 5)
            enchant(enchantments.getOrThrow(Enchantments.WIND_BURST), 3)
        }

        player.inventory.add(sword)
        player.inventory.add(bow)
        player.inventory.add(mace)

        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack(Items.TOTEM_OF_UNDYING))
        player.inventory.add(ItemStack(Items.ENDER_PEARL, 16))
        player.inventory.add(ItemStack(Items.GOLDEN_APPLE, 64))
        player.inventory.add(ItemStack(Items.COBBLESTONE, 64))
        player.inventory.add(ItemStack(Items.WIND_CHARGE, 64))
        player.inventory.add(ItemStack(Items.END_CRYSTAL, 64))
        player.inventory.add(ItemStack(Items.WATER_BUCKET))
        player.inventory.add(ItemStack(Items.ARROW, 64))
        player.inventory.add(ItemStack(Items.ARROW, 64))
        player.inventory.add(ItemStack(Items.ARROW, 64))
        player.inventory.add(ItemStack(Items.TOTEM_OF_UNDYING))
        player.inventory.add(ItemStack(Items.TOTEM_OF_UNDYING))
        player.inventory.add(ItemStack(Items.TOTEM_OF_UNDYING))
        player.inventory.add(ItemStack(Items.TOTEM_OF_UNDYING))
        player.inventory.add(ItemStack(Items.ENDER_PEARL, 16))
        player.inventory.add(ItemStack(Items.ENDER_PEARL, 16))
        player.inventory.add(ItemStack(Items.WIND_CHARGE, 64))

        source.sendSuccess({ Component.literal("You have been given maxed gear!").withColor(0x55FF55) }, false)

        return 1
    }
}
