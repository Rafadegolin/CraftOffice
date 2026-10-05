package dev.rafadegolin.craftoffice;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Bloco-palco: quem pisa nele fala para a sala inteira, ou num raio grande fora delas. */
public final class OfficeBlocks {
	private static final Identifier STAGE_ID = CraftOffice.id("stage");

	public static final Block STAGE = Registry.register(BuiltInRegistries.BLOCK, STAGE_ID, new Block(
			BlockBehaviour.Properties.of()
					.setId(ResourceKey.create(Registries.BLOCK, STAGE_ID))
					.mapColor(MapColor.COLOR_RED)
					.sound(SoundType.WOOL)
					.strength(0.8f)));

	public static final Item STAGE_ITEM = Registry.register(BuiltInRegistries.ITEM, STAGE_ID, new BlockItem(STAGE,
			new Item.Properties().setId(ResourceKey.create(Registries.ITEM, STAGE_ID)).useBlockDescriptionPrefix()));

	private OfficeBlocks() {
	}

	public static void register() {
		ResourceKey<CreativeModeTab> functional = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
				Identifier.withDefaultNamespace("functional_blocks"));
		CreativeModeTabEvents.modifyOutputEvent(functional).register(output -> output.accept(new ItemStack(STAGE_ITEM)));
	}
}
