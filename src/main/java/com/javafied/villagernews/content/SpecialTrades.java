package com.javafied.villagernews.content;

import com.javafied.villagernews.ConvertedPack;
import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.behavior.BehaviorDefinitions;
import com.javafied.villagernews.behavior.BehaviorSensors;
import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The add-on's special characters sell their own items (the Mayor his hat,
 * Villager #9 the microphone, ...) from trade tables in its behavior pack,
 * named by each entity's {@code minecraft:economy_trade_table}. A Java
 * villager standing in for one of them is a nitwit - so it never takes a job
 * or gets vanilla trades - whose offers come from that table instead.
 */
public final class SpecialTrades {
	private SpecialTrades() {
	}

	/** Whether this add-on variant has a trade table of its own in the converted add-on. */
	public static boolean hasOwnTrades(String variant) {
		return table(BehaviorSensors.definitionOfCharacter(variant)) != null;
	}

	/** Called for new special villagers: they don't work, they sell. */
	public static void makeTrader(ServerLevel level, Villager villager) {
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
	}

	/** The add-on's offers for this villager; empty if it's not one of the special characters. */
	public static List<MerchantOffer> offers(Villager villager) {
		JsonObject table = table(BehaviorSensors.definitionOf(villager));
		List<MerchantOffer> offers = new ArrayList<>();
		if (table == null || !table.has("tiers")) {
			return offers;
		}
		RandomSource random = villager.getRandom();
		for (JsonElement tier : table.getAsJsonArray("tiers")) {
			JsonObject t = tier.getAsJsonObject();
			if (t.has("total_exp_required") && t.get("total_exp_required").getAsInt() > villager.getVillagerXp()) {
				continue;
			}
			for (JsonElement group : t.has("groups") ? t.getAsJsonArray("groups") : new com.google.gson.JsonArray()) {
				List<JsonElement> trades = new ArrayList<>();
				group.getAsJsonObject().getAsJsonArray("trades").forEach(trades::add);
				int select = group.getAsJsonObject().has("num_to_select") ? group.getAsJsonObject().get("num_to_select").getAsInt() : trades.size();
				while (select-- > 0 && !trades.isEmpty()) {
					MerchantOffer offer = offer(trades.remove(random.nextInt(trades.size())).getAsJsonObject());
					if (offer != null) {
						offers.add(offer);
					}
				}
			}
		}
		return offers;
	}

	private static MerchantOffer offer(JsonObject trade) {
		var wants = trade.getAsJsonArray("wants");
		var gives = trade.getAsJsonArray("gives");
		if (wants == null || wants.isEmpty() || gives == null || gives.isEmpty()) {
			return null;
		}
		JsonObject first = wants.get(0).getAsJsonObject();
		Item costItem = item(first.get("item").getAsString());
		Item resultItem = item(gives.get(0).getAsJsonObject().get("item").getAsString());
		if (costItem == Items.AIR || resultItem == Items.AIR) {
			return null;
		}
		ItemCost cost = new ItemCost(costItem, quantity(first));
		java.util.Optional<ItemCost> second = wants.size() > 1
				? java.util.Optional.of(new ItemCost(item(wants.get(1).getAsJsonObject().get("item").getAsString()), quantity(wants.get(1).getAsJsonObject())))
				: java.util.Optional.empty();
		ItemStack result = new ItemStack(resultItem, quantity(gives.get(0).getAsJsonObject()));
		int maxUses = trade.has("max_uses") ? trade.get("max_uses").getAsInt() : 12;
		int xp = trade.has("trader_exp") ? trade.get("trader_exp").getAsInt() : 1;
		float multiplier = first.has("price_multiplier") ? first.get("price_multiplier").getAsFloat() : 0.05f;
		return new MerchantOffer(cost, second, result, 0, maxUses, xp, multiplier);
	}

	/** The add-on's items live in this mod's namespace, under their readable names; everything else is vanilla's. */
	private static Item item(String bedrockId) {
		String[] parts = bedrockId.split(":", 2);
		ResourceLocation id = parts.length == 2 && !parts[0].equals("minecraft")
				? VillagerNewsJavafied.id(AddonNames.nameOf(AddonNames.Kind.ITEM, parts[1])) : ResourceLocation.withDefaultNamespace(parts[parts.length - 1]);
		return BuiltInRegistries.ITEM.get(id);
	}

	private static int quantity(JsonObject entry) {
		JsonElement q = entry.get("quantity");
		if (q == null) {
			return 1;
		}
		return q.isJsonObject() ? q.getAsJsonObject().get("min").getAsInt() : q.getAsInt();
	}

	/** Small files, read only when a villager's offers are first generated. */
	private static JsonObject table(BehaviorDefinitions.Definition definition) {
		if (definition == null) {
			return null;
		}
		JsonObject component = definition.components().getAsJsonObject("minecraft:economy_trade_table");
		if (component == null || !component.has("table")) {
			return null;
		}
		Path file = ConvertedPack.dir().resolve("server").resolve(component.get("table").getAsString());
		try {
			return Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : null;
		} catch (IOException | RuntimeException e) {
			VillagerNewsJavafied.LOGGER.warn("Unreadable trade table {}", file, e);
			return null;
		}
	}
}
