package com.javafied.villagernews.content;

import com.javafied.villagernews.VillagerNewsJavafied;


import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The add-on's items, under readable ids ({@code handbook}, {@code mayor_hat};
 * the converter writes the player's add-on's icons, models and names under
 * the same ones), plus spawn eggs for its characters - all in a "Villager
 * News" creative tab.
 */
public final class ModItems {
 private static final net.neoforged.neoforge.registries.DeferredRegister<Item> ITEMS=net.neoforged.neoforge.registries.DeferredRegister.create(Registries.ITEM,VillagerNewsJavafied.MOD_ID);
 private static final net.neoforged.neoforge.registries.DeferredRegister<CreativeModeTab> TABS=net.neoforged.neoforge.registries.DeferredRegister.create(Registries.CREATIVE_MODE_TAB,VillagerNewsJavafied.MOD_ID);
	private static final List<net.neoforged.neoforge.registries.DeferredHolder<Item, Item>> TAB_CONTENTS = new ArrayList<>();

	/** Villager News Handbook: the add-on's guide, read from the player's converted add-on. */
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> HANDBOOK = register("handbook", p -> AttachableItem.create(p, true), new Item.Properties().stacksTo(1));
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> MAYOR_HAT = wearable("mayor_hat");
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> TESTIFICATE_MAN_HELMET = wearable("testificate_man_helmet");
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> MOUSTACHE = wearable("moustache");
	/** Held up to speak into while used: at full speed, as in the add-on. */
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> MICROPHONE = register("microphone", p -> AttachableItem.create(p, false), new Item.Properties().stacksTo(1));
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> VILLAGER_NOSE = wearable("villager_nose");

	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> MAYOR_SPAWN_EGG = spawnEgg("mayor", EntityType.VILLAGER);
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> TESTIFICATE_MAN_SPAWN_EGG = spawnEgg("testificate_man", EntityType.VILLAGER);
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> VILLAGER_5_SPAWN_EGG = spawnEgg("villager_5", EntityType.VILLAGER);
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> VILLAGER_9_SPAWN_EGG = spawnEgg("villager_9", EntityType.VILLAGER);
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> UNTOUCHABLE_SPAWN_EGG = spawnEgg("untouchable", EntityType.VILLAGER);
	public static final net.neoforged.neoforge.registries.DeferredHolder<Item, Item> WOOLY_SPAWN_EGG = spawnEgg("wooly", EntityType.SHEEP);

	public static final net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab,CreativeModeTab> TAB = TABS.register("villager_news", () -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.villagernewsjavafied"))
					.icon(() -> new ItemStack(MAYOR_HAT.get()))
					.displayItems((parameters, output) -> TAB_CONTENTS.forEach(item -> output.accept(item.get())))
					.build());

	private ModItems() {
	}

	/** Forces this class (and its static registrations) to load. */
	public static void init(net.neoforged.bus.api.IEventBus bus) { ITEMS.register(bus); TABS.register(bus);
	}

	/**
	 * Worn on the head. The equipment asset is only a marker: vanilla hands a
	 * head item with one to the armor layer (where GeckoLib draws the 3D
	 * model) rather than drawing its flat icon on the head; nothing loads it.
	 */
	private static net.neoforged.neoforge.registries.DeferredHolder<Item, Item> wearable(String path) {
		return register(path, WearableItem::create, new Item.Properties().stacksTo(1));
	}

	/** Named like the add-on's own eggs ("item.spawn_egg.entity.<ns>.<id>" in its converted lang). */
	private static net.neoforged.neoforge.registries.DeferredHolder<Item, Item> spawnEgg(String variant, EntityType<? extends Mob> type) {
		return register(variant + "_spawn_egg", properties -> new VariantSpawnEggItem(type, variant, properties),
				new Item.Properties());
	}

	private static net.neoforged.neoforge.registries.DeferredHolder<Item, Item> register(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceLocation id = VillagerNewsJavafied.id(path);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		net.neoforged.neoforge.registries.DeferredHolder<Item, Item> item = ITEMS.register(path, () -> factory.apply(properties));
		TAB_CONTENTS.add(item);
		return item;
	}
}
