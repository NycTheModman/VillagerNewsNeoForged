package com.javafied.villagernews.behavior;

import com.javafied.villagernews.ConvertedPack;
import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.behavior.BehaviorDefinitions.Definition;
import com.javafied.villagernews.behavior.BehaviorDefinitions.Subsensor;
import com.javafied.villagernews.behavior.BehaviorDefinitions.Trigger;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.VillagerVariantKeys;
import com.javafied.villagernews.dialog.DialogEngine;
import com.javafied.villagernews.dialog.DialogLibrary;
import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.javafied.villagernews.platform.ServerLifecycleEvents;
import com.javafied.villagernews.platform.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs the sensors of the add-on's behavior-pack entities on the Java
 * villagers and wandering traders that stand in for them: {@code entity_sensor} subsensors (players
 * in range matching filters, e.g. wearing iron armour or holding an emerald)
 * and {@code environment_sensor} triggers, firing the entity's events - which
 * set properties and, through a {@code /scriptevent} naming a dialog by its
 * add-on id, ask the {@link DialogEngine} for a line.
 */
public final class BehaviorSensors {
	/** The script events the behavior pack uses to request a dialog (adult / baby speaker). */
	private static final String DIALOG_EVENT = "dialog";
	private static final String BABY_DIALOG_EVENT = "baby_dialog";
	private static final int MAX_EVENT_DEPTH = 8;
	private static final String TRADER = "wandering_trader";

	private static BehaviorDefinitions definitions = BehaviorDefinitions.EMPTY;
	/** Per villager, per subsensor: the tick it may sense again. */
	private static final Map<LivingEntity, Map<Subsensor, Long>> cooldowns = new WeakHashMap<>();
	private static final Set<String> warnedCommands = new HashSet<>();

	private BehaviorSensors() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			try {
				definitions = BehaviorDefinitions.load(ConvertedPack.serverData("entities"));
			} catch (IOException | RuntimeException e) {
				VillagerNewsJavafied.LOGGER.error("Couldn't read the converted add-on's behavior definitions", e);
				definitions = BehaviorDefinitions.EMPTY;
			}
			cooldowns.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(BehaviorSensors::tick);
	}

	public static BehaviorDefinitions definitions() {
		return definitions;
	}

	/** The behavior definition a Java villager (by its character) or wandering trader stands in for. */
	public static Definition definitionOf(AbstractVillager villager) {
		return definitionOfCharacter(villager instanceof WanderingTrader ? TRADER
				: villager.getExistingData(ModAttachments.VILLAGER_VARIANT).orElse(VillagerVariantKeys.DEFAULT));
	}

	/**
	 * Bedrock's {@code is_baby}: a Java baby, or a character its definition
	 * makes a baby for good (the Mayor, who stays an adult in Java so he can
	 * trade, but talks, cries out and is drawn as a baby).
	 */
	public static boolean isBaby(Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return false;
		}
		if (living.isBaby()) {
			return true;
		}
		Definition definition = entity instanceof Villager villager ? definitionOf(villager) : null;
		return definition != null && definition.alwaysBaby();
	}

	/** A character's behavior definition, by its readable name ({@code mayor}). */
	public static Definition definitionOfCharacter(String character) {
		return definitions.get(AddonNames.character(character));
	}

	private static void tick(MinecraftServer server) {
		if (definitions.size() == 0 || DialogEngine.get() == null) {
			return;
		}
		long now = server.getTickCount();
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			Set<AbstractVillager> villagers = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				villagers.addAll(level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(DialogEngine.RANGE)));
			}
			for (AbstractVillager villager : villagers) {
				Definition definition = definitionOf(villager);
				if (definition != null && villager.isAlive()) {
					sense(level, villager, definition, now);
				}
			}
		}
	}

	private static void sense(ServerLevel level, AbstractVillager villager, Definition definition, long now) {
		BehaviorProperties properties = new BehaviorProperties(villager, definition);
		BedrockFilters.Context self = new BedrockFilters.Context(villager, null, properties);
		for (Trigger trigger : definition.environmentTriggers()) {
			if (BedrockFilters.test(trigger.filters(), self)) {
				fire(villager, definition, properties, trigger.event(), 0);
			}
		}
		Map<Subsensor, Long> ready = cooldowns.computeIfAbsent(villager, v -> new HashMap<>());
		for (Subsensor sensor : definition.subsensors()) {
			if (now < ready.getOrDefault(sensor, 0L)) {
				continue;
			}
			int count = 0;
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || !player.isAlive()) {
					continue;
				}
				double dx = player.getX() - villager.getX();
				double dz = player.getZ() - villager.getZ();
				if (Math.abs(player.getY() - villager.getY()) <= sensor.vertical()
						&& dx * dx + dz * dz <= sensor.horizontal() * sensor.horizontal()
						&& BedrockFilters.test(sensor.filters(), new BedrockFilters.Context(villager, player, properties))) {
					count++;
				}
			}
			if (count >= sensor.minimumCount() && (sensor.maximumCount() < 0 || count <= sensor.maximumCount())) {
				fire(villager, definition, properties, sensor.event(), 0);
				if (sensor.cooldown() > 0) {
					ready.put(sensor, now + Math.round(sensor.cooldown() * 20));
				}
			}
		}
	}

	private static void fire(AbstractVillager villager, Definition definition, BehaviorProperties properties, String event, int depth) {
		JsonElement body = definition.events().get(event);
		if (body != null && depth < MAX_EVENT_DEPTH) {
			run(villager, definition, properties, body, depth);
		}
	}

	/** An event body: filters, sequence, randomize, set_property, queue_command, trigger. Component groups are ignored. */
	private static void run(AbstractVillager villager, Definition definition, BehaviorProperties properties, JsonElement body, int depth) {
		if (!body.isJsonObject()) {
			return;
		}
		JsonObject action = body.getAsJsonObject();
		if (action.has("filters") && !BedrockFilters.test(action.get("filters"),
				new BedrockFilters.Context(villager, null, properties))) {
			return;
		}
		if (action.has("sequence")) {
			for (JsonElement step : action.getAsJsonArray("sequence")) {
				run(villager, definition, properties, step, depth);
			}
		}
		if (action.has("randomize")) {
			double total = 0;
			for (JsonElement option : action.getAsJsonArray("randomize")) {
				total += weight(option);
			}
			double roll = ThreadLocalRandom.current().nextDouble() * total;
			for (JsonElement option : action.getAsJsonArray("randomize")) {
				if ((roll -= weight(option)) <= 0) {
					run(villager, definition, properties, option, depth);
					break;
				}
			}
		}
		if (action.has("set_property")) {
			action.getAsJsonObject("set_property").entrySet().forEach(e -> properties.set(e.getKey(), e.getValue()));
		}
		if (action.has("queue_command")) {
			JsonElement command = action.getAsJsonObject("queue_command").get("command");
			if (command != null && command.isJsonArray()) {
				command.getAsJsonArray().forEach(c -> command(villager, c.getAsString()));
			} else if (command != null) {
				command(villager, command.getAsString());
			}
		}
		if (action.has("trigger")) {
			JsonElement trigger = action.get("trigger");
			String event = trigger.isJsonObject() ? trigger.getAsJsonObject().get("event").getAsString() : trigger.getAsString();
			fire(villager, definition, properties, event, depth + 1);
		}
	}

	private static double weight(JsonElement option) {
		return option.isJsonObject() && option.getAsJsonObject().has("weight") ? option.getAsJsonObject().get("weight").getAsDouble() : 1;
	}

	/** Only the dialog requests are meaningful here; the script handles them as {@code scriptevent}s. */
	private static void command(AbstractVillager villager, String command) {
		String[] words = command.strip().replaceFirst("^/", "").split("\\s+");
		DialogEngine engine = DialogEngine.get();
		String event = words.length >= 3 && words[0].equals("scriptevent")
				? AddonNames.current().name(AddonNames.Kind.SCRIPT_EVENT, words[1]) : null;
		if ((DIALOG_EVENT.equals(event) || BABY_DIALOG_EVENT.equals(event)) && engine != null) {
			boolean baby = BABY_DIALOG_EVENT.equals(event);
			boolean forced = words.length > 3 && words[3].equals("true");
			DialogEngine.Options options = DialogEngine.Options.DEFAULT.withStates(baby ? DialogEngine.State.BABY : DialogEngine.State.ADULT);
			if (forced) {
				options = options.ignoringCooldowns(true, true, true).interrupting();
			}
			DialogLibrary.Dialog dialog = engine.library().byAddonId(words[2]);
			engine.request(villager, dialog != null ? dialog.id() : words[2], options);
			return;
		}
		if (warnedCommands.add(words[0] + " " + (words.length > 1 ? words[1] : ""))) {
			VillagerNewsJavafied.LOGGER.debug("Behavior command not ported: {}", command);
		}
	}
}
