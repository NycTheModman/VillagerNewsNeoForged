package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.VillagerNewsJavafied;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The add-on's own client-side definitions (carried over by the converter
 * into {@code assets/<modid>/bedrock/}), reloaded with resources.
 */
public final class BedrockDefinitions implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = VillagerNewsJavafied.id("bedrock_definitions");

	public record ControllerRef(String name, String condition) {
	}

	/** One {@code {"bone_or_*pattern": "molang"}} entry of a render controller's materials / part_visibility list. */
	public record BonePattern(String pattern, String molang) {
		public boolean matches(String bone) {
			if (pattern.equals("*")) {
				return true;
			}
			if (pattern.endsWith("*")) {
				return bone.startsWith(pattern.substring(0, pattern.length() - 1));
			}
			return bone.equalsIgnoreCase(pattern);
		}
	}

	/** @param soundEffects short name -> Bedrock sound event, for animation controllers' {@code sound_effects} */
	public record ClientEntity(String identifier, Map<String, String> geometry, Map<String, String> textures,
			Map<String, String> materials, Map<String, String> animations, List<String> initialize,
			List<String> preAnimation, String scale, List<ControllerRef> renderControllers, List<ControllerRef> animate,
			Map<String, String> soundEffects) {
	}

	/** @param uvOffset {@code uv_anim}'s offset (u, v; Molang, in fractions of the texture), empty if none */
	public record RenderController(Map<String, List<String>> arrays, String geometry, List<String> textures,
			List<BonePattern> materials, List<BonePattern> partVisibility, List<String> uvOffset) {
	}

	/** @param attachables how held items are drawn, keyed like client entities (same shape of definition) */
	public record Snapshot(Map<String, ClientEntity> clientEntities, Map<String, RenderController> renderControllers,
			Map<String, Map<String, JsonElement>> propertyDefaults, BedrockMaterials materials,
			Map<String, BedrockAnimations.Animation> animations, Map<String, BedrockAnimations.Controller> animationControllers,
			Map<String, ClientEntity> attachables, Map<String, JsonObject> traits) {
		/** Always a baby, per its behavior-pack definition (the Mayor). */
		public boolean alwaysBaby(String identifier) {
			JsonObject t = traits.get(identifier.toLowerCase(Locale.ROOT));
			return t != null && t.has("baby") && t.get("baby").getAsBoolean();
		}

		/** Its behavior-pack scale ({@code minecraft:scale}), 1 if none. */
		public float entityScale(String identifier) {
			JsonObject t = traits.get(identifier.toLowerCase(Locale.ROOT));
			return t != null && t.has("scale") ? t.get("scale").getAsFloat() : 1f;
		}

		/** Like {@link #clientEntity}: a full identifier or just its path. */
		public ClientEntity attachable(String identifier) {
			String key = identifier.toLowerCase(Locale.ROOT);
			ClientEntity exact = attachables.get(key);
			if (exact != null || key.contains(":")) {
				return exact;
			}
			for (Map.Entry<String, ClientEntity> entry : attachables.entrySet()) {
				if (entry.getKey().endsWith(":" + key)) {
					return entry.getValue();
				}
			}
			return null;
		}

		/** Accepts a full identifier or just its path ("villager"), since the add-on's namespace is its own business. */
		public ClientEntity clientEntity(String identifier) {
			String key = identifier.toLowerCase(Locale.ROOT);
			ClientEntity exact = clientEntities.get(key);
			if (exact != null || key.contains(":")) {
				return exact;
			}
			for (Map.Entry<String, ClientEntity> entry : clientEntities.entrySet()) {
				if (entry.getKey().endsWith(":" + key)) {
					return entry.getValue();
				}
			}
			return null;
		}

		public RenderController renderController(String name) {
			return renderControllers.get(name.toLowerCase(Locale.ROOT));
		}

		public Map<String, JsonElement> properties(String identifier) {
			return propertyDefaults.getOrDefault(identifier, Map.of());
		}

		public BedrockAnimations.Animation animation(String name) {
			return animations.get(name.toLowerCase(Locale.ROOT));
		}

		public BedrockAnimations.Controller animationController(String name) {
			return animationControllers.get(name.toLowerCase(Locale.ROOT));
		}
	}

	private static volatile Snapshot current =
			new Snapshot(Map.of(), Map.of(), Map.of(), BedrockMaterials.EMPTY, Map.of(), Map.of(), Map.of(), Map.of());

	public static Snapshot get() {
		return current;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		List<JsonObject> clientEntities = new ArrayList<>();
		List<JsonObject> renderControllers = new ArrayList<>();
		List<JsonObject> materials = new ArrayList<>();
		List<JsonObject> properties = new ArrayList<>();
		List<JsonObject> animations = new ArrayList<>();
		List<JsonObject> animationControllers = new ArrayList<>();
		forEach(manager, "bedrock/entity", ".json", clientEntities::add);
		forEach(manager, "bedrock/render_controllers", ".json", renderControllers::add);
		forEach(manager, "bedrock/materials", ".material", materials::add);
		forEach(manager, "bedrock", "properties.json", properties::add);
		forEach(manager, "bedrock/animations", ".json", animations::add);
		forEach(manager, "bedrock/animation_controllers", ".json", animationControllers::add);
		List<JsonObject> attachables = new ArrayList<>();
		forEach(manager, "bedrock/attachables", ".json", attachables::add);
		List<JsonObject> traits = new ArrayList<>();
		forEach(manager, "bedrock", "traits.json", traits::add);

		current = parse(clientEntities, renderControllers, materials, properties, animations, animationControllers, attachables, traits);
		VillagerNewsJavafied.LOGGER.info("Loaded {} Bedrock client entities, {} render controllers, {} animations, {} animation controllers",
				current.clientEntities().size(), current.renderControllers().size(),
				current.animations().size(), current.animationControllers().size());
	}

	/** Builds a snapshot from the carried-over files' JSON; separate from resource IO so it can be tested directly. */
	public static Snapshot parse(List<JsonObject> clientEntityFiles, List<JsonObject> renderControllerFiles,
			List<JsonObject> materialFiles, List<JsonObject> propertyFiles, List<JsonObject> animationFiles,
			List<JsonObject> animationControllerFiles) {
		return parse(clientEntityFiles, renderControllerFiles, materialFiles, propertyFiles, animationFiles, animationControllerFiles,
				List.of(), List.of());
	}

	public static Snapshot parse(List<JsonObject> clientEntityFiles, List<JsonObject> renderControllerFiles,
			List<JsonObject> materialFiles, List<JsonObject> propertyFiles, List<JsonObject> animationFiles,
			List<JsonObject> animationControllerFiles, List<JsonObject> attachableFiles, List<JsonObject> traitFiles) {
		Map<String, JsonObject> traits = new HashMap<>();
		for (JsonObject json : traitFiles) {
			json.entrySet().forEach(e -> traits.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsJsonObject()));
		}
		Map<String, ClientEntity> attachables = new HashMap<>();
		for (JsonObject json : attachableFiles) {
			JsonObject attachable = json.getAsJsonObject("minecraft:attachable");
			if (attachable != null && attachable.has("description")) {
				ClientEntity parsed = parseClientEntity(attachable.getAsJsonObject("description"));
				attachables.put(parsed.identifier().toLowerCase(Locale.ROOT), parsed);
			}
		}
		Map<String, ClientEntity> clientEntities = new HashMap<>();
		for (JsonObject json : clientEntityFiles) {
			JsonObject description = json.getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
			ClientEntity entity = parseClientEntity(description);
			clientEntities.put(entity.identifier().toLowerCase(Locale.ROOT), entity);
		}
		Map<String, RenderController> renderControllers = new HashMap<>();
		for (JsonObject json : renderControllerFiles) {
			JsonObject controllers = json.getAsJsonObject("render_controllers");
			for (String name : controllers.keySet()) {
				renderControllers.put(name.toLowerCase(Locale.ROOT), parseRenderController(controllers.getAsJsonObject(name)));
			}
		}
		JsonObject materials = new JsonObject();
		for (JsonObject json : materialFiles) {
			JsonObject defs = json.has("materials") ? json.getAsJsonObject("materials") : json;
			defs.entrySet().forEach(e -> materials.add(e.getKey(), e.getValue()));
		}
		Map<String, Map<String, JsonElement>> properties = new HashMap<>();
		for (JsonObject json : propertyFiles) {
			for (String identifier : json.keySet()) {
				Map<String, JsonElement> defaults = new HashMap<>();
				json.getAsJsonObject(identifier).entrySet().forEach(e -> defaults.put(e.getKey(), e.getValue()));
				properties.put(identifier, Map.copyOf(defaults));
			}
		}
		Map<String, BedrockAnimations.Animation> animations = new HashMap<>();
		animationFiles.forEach(json -> BedrockAnimations.parseAnimations(json, animations));
		Map<String, BedrockAnimations.Controller> controllers = new HashMap<>();
		animationControllerFiles.forEach(json -> BedrockAnimations.parseControllers(json, controllers));
		return new Snapshot(Map.copyOf(clientEntities), Map.copyOf(renderControllers), Map.copyOf(properties),
				BedrockMaterials.parse(materials), Map.copyOf(animations), Map.copyOf(controllers), Map.copyOf(attachables),
				Map.copyOf(traits));
	}

	private static void forEach(ResourceManager manager, String dir, String suffix, Consumer<JsonObject> consumer) {
		Map<ResourceLocation, Resource> found = manager.listResources(dir,
				id -> id.getNamespace().equals(VillagerNewsJavafied.MOD_ID) && id.getPath().endsWith(suffix));
		for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
			try (Reader reader = entry.getValue().openAsReader()) {
				consumer.accept(JsonParser.parseReader(reader).getAsJsonObject());
			} catch (IOException | RuntimeException e) {
				VillagerNewsJavafied.LOGGER.warn("Skipping unreadable Bedrock definition {}", entry.getKey(), e);
			}
		}
	}

	private static ClientEntity parseClientEntity(JsonObject d) {
		JsonObject scripts = d.has("scripts") ? d.getAsJsonObject("scripts") : new JsonObject();
		return new ClientEntity(
				d.get("identifier").getAsString(),
				stringMap(d.getAsJsonObject("geometry")),
				stringMap(d.getAsJsonObject("textures")),
				stringMap(d.getAsJsonObject("materials")),
				stringMap(d.getAsJsonObject("animations")),
				stringList(scripts.get("initialize")),
				stringList(scripts.get("pre_animation")),
				scripts.has("scale") ? molang(scripts.get("scale")) : "1",
				controllerRefs(d.get("render_controllers")),
				controllerRefs(scripts.get("animate")),
				stringMap(d.getAsJsonObject("sound_effects")));
	}

	private static RenderController parseRenderController(JsonObject rc) {
		Map<String, List<String>> arrays = new HashMap<>();
		if (rc.has("arrays")) {
			for (Map.Entry<String, JsonElement> kind : rc.getAsJsonObject("arrays").entrySet()) {
				for (Map.Entry<String, JsonElement> array : kind.getValue().getAsJsonObject().entrySet()) {
					String name = array.getKey().toLowerCase(Locale.ROOT);
					arrays.put(name.startsWith("array.") ? name.substring(6) : name, stringList(array.getValue()));
				}
			}
		}
		return new RenderController(
				Map.copyOf(arrays),
				rc.has("geometry") ? molang(rc.get("geometry")) : "Geometry.default",
				stringList(rc.get("textures")),
				bonePatterns(rc.get("materials")),
				bonePatterns(rc.get("part_visibility")),
				rc.has("uv_anim") ? stringList(rc.getAsJsonObject("uv_anim").get("offset")) : List.of());
	}

	/** Keys lower-cased: Molang identifiers are case-insensitive. */
	private static Map<String, String> stringMap(JsonObject obj) {
		Map<String, String> out = new HashMap<>();
		if (obj != null) {
			obj.entrySet().forEach(e -> out.put(e.getKey().toLowerCase(Locale.ROOT), molang(e.getValue())));
		}
		return Map.copyOf(out);
	}

	private static List<String> stringList(JsonElement element) {
		List<String> out = new ArrayList<>();
		if (element == null) {
			return out;
		}
		if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(e -> out.add(molang(e)));
		} else {
			out.add(molang(element));
		}
		return List.copyOf(out);
	}

	/** Entries are either a bare name or {@code {"name": "condition"}}. */
	private static List<ControllerRef> controllerRefs(JsonElement element) {
		List<ControllerRef> out = new ArrayList<>();
		if (element instanceof JsonArray array) {
			for (JsonElement e : array) {
				if (e.isJsonObject()) {
					e.getAsJsonObject().entrySet().forEach(c -> out.add(new ControllerRef(c.getKey(), molang(c.getValue()))));
				} else {
					out.add(new ControllerRef(e.getAsString(), null));
				}
			}
		}
		return List.copyOf(out);
	}

	private static List<BonePattern> bonePatterns(JsonElement element) {
		List<BonePattern> out = new ArrayList<>();
		if (element instanceof JsonArray array) {
			for (JsonElement e : array) {
				e.getAsJsonObject().entrySet().forEach(p -> out.add(new BonePattern(p.getKey(), molang(p.getValue()))));
			}
		}
		return List.copyOf(out);
	}

	/** JSON booleans/numbers are valid Molang constants too. */
	private static String molang(JsonElement e) {
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
			return e.getAsBoolean() ? "1" : "0";
		}
		return e.getAsString();
	}
}
