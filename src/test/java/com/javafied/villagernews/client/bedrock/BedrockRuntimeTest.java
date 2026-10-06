package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.client.bedrock.BedrockRuntime.Layer;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.Pose;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.RenderPlan;
import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.StringValue;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs the real add-on's client logic (as converted into dev/converted by the runConverter task). */
class BedrockRuntimeTest {
	private static final Path ASSETS = Path.of("dev/converted/assets/villagernewsjavafied");
	private static BedrockDefinitions.Snapshot defs;

	@BeforeAll
	static void load() throws IOException {
		Path bedrock = ASSETS.resolve("bedrock");
		assumeTrue(Files.isDirectory(bedrock), "run ./gradlew runConverter first");
		defs = BedrockDefinitions.parse(read(bedrock.resolve("entity")), read(bedrock.resolve("render_controllers")),
				read(bedrock.resolve("materials")), List.of(json(bedrock.resolve("properties.json"))),
				read(bedrock.resolve("animations")), read(bedrock.resolve("animation_controllers")),
				Files.isDirectory(bedrock.resolve("attachables")) ? read(bedrock.resolve("attachables")) : List.of(),
				Files.exists(bedrock.resolve("traits.json")) ? List.of(json(bedrock.resolve("traits.json"))) : List.of());
	}

	/** An adult, plains-biome farmer with skin 2 - what a freshly spawned vanilla villager could look like. */
	private static MutableObjectBinding queries(String identifier, int profession) {
		return queries(identifier, profession, Map.of());
	}

	private static MutableObjectBinding queries(String identifier, int profession, Map<String, JsonElement> overrides) {
		Map<String, JsonElement> properties = new java.util.HashMap<>(defs.properties(identifier));
		properties.putAll(overrides);
		MutableObjectBinding q = new MutableObjectBinding();
		q.set("is_alive", Value.of(1));
		q.set("skin_id", Value.of(2));
		q.set("variant", Value.of(profession));
		q.set("mark_variant", Value.of(0));
		q.set("graphics_mode_is_any", (Function<Object>) (ctx, args) -> Value.of(1));
		q.set("is_name_any", (Function<Object>) (ctx, args) -> Value.nil());
		q.set("has_property", (Function<Object>) (ctx, args) -> Value.of(properties.containsKey(args.next().eval().getAsString())));
		q.set("property", (Function<Object>) (ctx, args) -> {
			JsonElement v = properties.get(args.next().eval().getAsString());
			if (v == null) {
				return Value.nil();
			}
			if (v.getAsJsonPrimitive().isBoolean()) {
				return Value.of(v.getAsBoolean());
			}
			return v.getAsJsonPrimitive().isNumber() ? Value.of(v.getAsDouble()) : StringValue.of(v.getAsString());
		});
		q.set("any", (Function<Object>) (ctx, args) -> {
			double subject = args.next().eval().getAsNumber();
			for (int i = 1; i < args.length(); i++) {
				if (args.next().eval().getAsNumber() == subject) {
					return Value.of(1);
				}
			}
			return Value.nil();
		});
		q.set("position", (Function<Object>) (ctx, args) -> Value.nil());
		return q;
	}

	private static RenderPlan plan(String path, int profession) {
		BedrockDefinitions.ClientEntity ce = defs.clientEntity(path);
		RenderPlan plan = BedrockRuntime.plan(defs, ce, new BedrockRuntime.EntityState(), 0, queries(ce.identifier(), profession), 0);
		System.out.println(path + " (profession " + profession + "): scale " + plan.scale());
		plan.layers().forEach(layer -> System.out.println("  " + layer));
		return plan;
	}

	@Test
	void villagerIsSkinPlusClothingPlusProfession() {
		RenderPlan plan = plan("villager", 1);
		assertTrue(plan.layers().size() >= 3, "expected skin + biome clothing + profession layers");
		assertEquals("textures/oreville/vn/dil.png", plan.layers().getFirst().texture().getPath(),
				"normal adult villagers use the dil skin, not a special-state one");
		assertEquals(0.9375f, plan.scale(), 1e-6);
		assertTrue(plan.isBoneVisible("hat"), "the hat bone carries most job outfits");
		assertTexturesExist(plan);
	}

	@Test
	void unemployedVillagerHasNoProfessionOrBadgeLayer() {
		assertTrue(plan("villager", 0).layers().size() < plan("villager", 1).layers().size());
	}

	@Test
	void sheepShowsWoolUnlessSheared() {
		RenderPlan plan = plan(AddonNames.character("wooly"), 0);
		assertTexturesExist(plan);
		assertTrue(plan.isBoneVisible("oggd_head"), "wool bones visible on an unsheared sheep");
		assertTrue(plan.isBoneVisible("body"));
	}

	@Test
	void everyVillagerVariantProducesALayeredPlan() {
		for (String character : List.of("villager", "untouchable", "wandering_trader", "villager_5", "testificate_man", "villager_9", "mayor")) {
			String variant = AddonNames.character(character);
			RenderPlan plan = plan(variant, 1);
			assertFalse(plan.layers().isEmpty(), variant + " produced no layers");
			assertTexturesExist(plan);
		}
	}

	@Test
	void villagerAnimatesWhileWalkingAndLooking() {
		BedrockDefinitions.ClientEntity ce = defs.clientEntity("villager");
		MutableObjectBinding q = queries(ce.identifier(), 1);
		q.set("is_on_ground", Value.of(1));
		q.set("modified_move_speed", Value.of(0.8));
		q.set("target_x_rotation", Value.of(30));
		q.set("target_y_rotation", Value.of(25));
		BedrockRuntime.EntityState state = new BedrockRuntime.EntityState();
		RenderPlan plan = null;
		for (int frame = 0; frame <= 40; frame++) {
			double time = frame * 0.05;
			q.set("life_time", Value.of(time));
			plan = BedrockRuntime.plan(defs, ce, state, time, q, 0);
		}
		System.out.println("posed bones after 2s: " + plan.poses().size());
		plan.poses().entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(40).forEach(e -> {
			BedrockRuntime.Pose p = e.getValue();
			System.out.printf("  %-18s rot(%7.2f %7.2f %7.2f) pos(%6.2f %6.2f %6.2f) scale(%4.2f %4.2f %4.2f)%n",
					e.getKey(), p.rx, p.ry, p.rz, p.px, p.py, p.pz, p.sx, p.sy, p.sz);
		});
		assertFalse(plan.poses().isEmpty(), "nothing animated");
		for (BedrockRuntime.Pose p : plan.poses().values()) {
			for (double v : new double[] {p.rx, p.ry, p.rz, p.px, p.py, p.pz, p.sx, p.sy, p.sz}) {
				assertTrue(Double.isFinite(v), "non-finite bone value");
			}
		}
		assertTrue(plan.poses().values().stream().anyMatch(p -> Math.abs(p.rx) + Math.abs(p.ry) > 1),
				"expected the head/body to turn towards the target");
		// v.dzpjns ("mid-gesture") gates the walk cycle; it must settle back to 0 when no line is playing.
		assertEquals(0, state.variables().get(variable("gesturing")).getAsNumber(), "villager stuck in the gesture state");

		// The addon's "offset" animation drops the root to undo the script's puppet teleport;
		// with the ported lift applied, the model should end up standing on the villager's own feet.
		double rootDropWorldPixels = plan.poses().get("root").py * plan.scale();
		assertEquals(0, rootDropWorldPixels + VillagerPuppetPort.ADULT_LIFT * 16, 1.0,
				"offset animation and puppet lift should cancel out");
	}

	/** The add-on's nose physics: walking sideways, then stopping, swings the nose (bone fgk6) on its spring. */
	@Test
	void noseSwingsWhenAVillagerStops() {
		assertTrue(swing(defs.clientEntity("villager"), null) > 4, "the villager's nose didn't swing");
	}

	/** ...and the villager nose a player wears does the same. */
	@Test
	void wornNoseSwingsToo() {
		MutableObjectBinding context = new MutableObjectBinding();
		context.set("is_first_person", Value.of(false));
		context.set("item_slot", StringValue.of("head"));
		assertTrue(swing(defs.attachable(AddonNames.item("villager_nose")), context) > 4, "the worn nose didn't swing");
	}

	/** How far the nose rolls (degrees, peak to peak) after a second's walk to the side and a stop. */
	private static double swing(BedrockDefinitions.ClientEntity ce, MutableObjectBinding context) {
		MutableObjectBinding q = queries(ce.identifier(), 1);
		q.set("is_on_ground", Value.of(1));
		double[] x = {0};
		q.set("position", (Function<Object>) (ctx, args) -> {
			int axis = (int) args.next().eval().getAsNumber();
			return Value.of(axis == 0 ? x[0] : axis == 1 ? 64 : 0);
		});
		BedrockRuntime.EntityState state = new BedrockRuntime.EntityState();
		double min = 0;
		double max = 0;
		for (int frame = 0; frame <= 60; frame++) {
			double time = frame * 0.05;
			boolean walking = frame < 20;
			if (walking) {
				x[0] += 0.1;
			}
			q.set("modified_move_speed", Value.of(walking ? 0.8 : 0));
			q.set("life_time", Value.of(time));
			RenderPlan plan = context == null ? BedrockRuntime.plan(defs, ce, state, time, q, 0)
					: BedrockRuntime.plan(defs, ce, state, time, q, 0, context);
			BedrockRuntime.Pose nose = plan.poses().get("fgk6");
			if (nose != null) {
				min = Math.min(min, nose.rz);
				max = Math.max(max, nose.rz);
			}
		}
		System.out.println(ce.identifier() + ": nose roll " + min + " .. " + max);
		return max - min;
	}

	@Test
	void dyingVillagerCriesOut() {
		BedrockDefinitions.ClientEntity ce = defs.clientEntity("villager");
		MutableObjectBinding q = queries(ce.identifier(), 1);
		BedrockRuntime.EntityState state = new BedrockRuntime.EntityState();
		for (int frame = 0; frame < 5; frame++) {
			BedrockRuntime.plan(defs, ce, state, frame * 0.05, q, 0);
		}
		assertTrue(state.pendingSounds().isEmpty(), "no sounds while alive");
		q.set("is_alive", Value.of(0));
		for (int frame = 5; frame < 10; frame++) {
			BedrockRuntime.plan(defs, ce, state, frame * 0.05, q, 0);
		}
		assertEquals(1, state.pendingSounds().size(), "one death cry: " + state.pendingSounds());
		assertTrue(state.pendingSounds().getFirst().startsWith("oreville_vn:"));
	}

	@Test
	void speakingLineDrivesTheMouthThenEnds() {
		BedrockDefinitions.ClientEntity ce = defs.clientEntity("villager");
		MutableObjectBinding q = queries(ce.identifier(), 1);
		BedrockRuntime.EntityState state = new BedrockRuntime.EntityState();
		BedrockRuntime.plan(defs, ce, state, 0, q, 0);
		state.playAnimation("animation.oreville_vn.awlism"); // 1.81 s, "Aaghh!"
		boolean mouthMoved = false;
		for (int frame = 1; frame <= 60; frame++) {
			BedrockRuntime.plan(defs, ce, state, frame * 0.05, q, 0);
			if (frame * 0.05 < 1.8) {
				mouthMoved |= state.variables().get(variable("mouth_open")).getAsNumber() > 0;
			}
		}
		assertTrue(mouthMoved, "lip-sync timeline should open the mouth");
		assertEquals(0, state.variables().get(variable("mouth_open")).getAsNumber(), "mouth closed after the line");
		assertEquals("default", state.variables().get(variable("gesture")).getAsString(), "the line's gesture cue was picked up");
	}

	@Test
	void villagerWearsTheHatItWasGiven() {
		BedrockDefinitions.ClientEntity ce = defs.clientEntity("villager");
		ResourceLocation base = BedrockRuntime.plan(defs, ce, new BedrockRuntime.EntityState(), 0, queries(ce.identifier(), 1), 0)
				.layers().getFirst().model();
		RenderPlan plan = BedrockRuntime.plan(defs, ce, new BedrockRuntime.EntityState(), 0,
				queries(ce.identifier(), 1, Map.of(AddonNames.property("accessory"), new com.google.gson.JsonPrimitive(AddonNames.item("mayor_hat")))), 0);
		plan.layers().forEach(layer -> System.out.println("  with hat: " + layer));
		assertTrue(plan.layers().stream().anyMatch(layer -> !layer.model().equals(base)), "an accessory layer with its own geometry");
		assertTrue(plan.isBoneVisible(bone("mayor_hat")), "the Mayor Hat's bone");
		assertFalse(plan.isBoneVisible(bone("moustache")), "not the moustache");
		assertTexturesExist(plan);
	}

	/** Wooly's wool (Bedrock's sheep material) gets a layer of its own that the renderer tints with the sheep's colour. */
	@Test
	void woolyHasADyeableWoolLayer() {
		RenderPlan plan = plan(AddonNames.character("wooly"), 0);
		assertTrue(plan.layers().stream().anyMatch(layer -> layer.dyed() && layer.texture().getPath().endsWith("_dye.png")),
				"dyed wool layer");
		assertTexturesExist(plan);
	}

	/** The Mayor is a baby for good in the add-on, at half scale, which the renderer applies whatever the Java villager is. */
	@Test
	void theMayorIsAlwaysABaby() {
		String mayor = defs.clientEntity(AddonNames.character("mayor")).identifier();
		assertTrue(defs.alwaysBaby(mayor));
		assertEquals(0.5f, defs.entityScale(mayor), 1e-6);
		assertFalse(defs.alwaysBaby(defs.clientEntity("villager").identifier()));
	}

	/** The level badge follows the villager's trading level (which the add-on reads through its trade-tier probe). */
	@Test
	void levelBadgeShowsTheTradeTier() {
		BedrockDefinitions.ClientEntity ce = defs.clientEntity("villager");
		List<String> novice = textures(BedrockRuntime.plan(defs, ce, new BedrockRuntime.EntityState(), 0, queries(ce.identifier(), 1,
				Map.of(AddonNames.property("trade_tier"), new com.google.gson.JsonPrimitive(0))), 0));
		List<String> master = textures(BedrockRuntime.plan(defs, ce, new BedrockRuntime.EntityState(), 0, queries(ce.identifier(), 1,
				Map.of(AddonNames.property("trade_tier"), new com.google.gson.JsonPrimitive(4))), 0));
		System.out.println("  novice " + novice + "\n  master " + master);
		assertFalse(novice.equals(master), "a master's badge differs from a novice's");
	}

	private static List<String> textures(RenderPlan plan) {
		return plan.layers().stream().map(layer -> layer.texture().getPath()).toList();
	}

	@Test
	void villagerHoldsTheSignItWasGiven() {
		BedrockDefinitions.ClientEntity ce = defs.clientEntity("villager");
		int base = BedrockRuntime.plan(defs, ce, new BedrockRuntime.EntityState(), 0, queries(ce.identifier(), 1), 0).layers().size();
		RenderPlan plan = BedrockRuntime.plan(defs, ce, new BedrockRuntime.EntityState(), 0, queries(ce.identifier(), 1,
				Map.of(AddonNames.property("sign"), new com.google.gson.JsonPrimitive(3), AddonNames.property("sign_message"),
						new com.google.gson.JsonPrimitive(10))), 0);
		plan.layers().forEach(layer -> System.out.println("  with sign: " + layer));
		assertTrue(plan.layers().size() > base, "sign layers");
		assertTrue(plan.layers().stream().anyMatch(layer -> SignTextures.isGenerated(layer.texture())
				&& layer.texture().getPath().endsWith("/jungle.png")), "the jungle sign board, from Java's sign texture");
		assertTrue(plan.layers().stream().anyMatch(layer -> Math.abs(layer.vOffset() - 10 / 87f) < 1e-6),
				"its 11th message scrolled into view");
		assertTexturesExist(plan);
	}

	@Test
	void heldItemsTakeTheirFirstAndThirdPersonPoses() {
		for (String item : List.of("microphone", "handbook")) {
			BedrockDefinitions.ClientEntity attachable = defs.attachable(AddonNames.item(item));
			assumeTrue(attachable != null, "no attachables converted");
			Pose third = attachablePose(attachable, false);
			Pose first = attachablePose(attachable, true);
			System.out.printf("%s third: pos(%.2f %.2f %.2f) rot(%.1f %.1f %.1f)  first: pos(%.2f %.2f %.2f) rot(%.1f %.1f %.1f)%n", item,
					third.px, third.py, third.pz, third.rx, third.ry, third.rz, first.px, first.py, first.pz, first.rx, first.ry, first.rz);
			assertTrue(Math.abs(third.rx) > 45, item + " held forward in third person");
			assertTrue(Math.abs(first.px - third.px) + Math.abs(first.py - third.py) + Math.abs(first.pz - third.pz) > 0.1,
					item + " has a first-person pose of its own");
			assertTexturesExist(BedrockRuntime.plan(defs, attachable, new BedrockRuntime.EntityState(), 0, queries("minecraft:player", 0), 0,
					context(false)));
		}
	}

	private static Pose attachablePose(BedrockDefinitions.ClientEntity attachable, boolean firstPerson) {
		BedrockRuntime.EntityState state = new BedrockRuntime.EntityState();
		RenderPlan plan = null;
		for (int frame = 0; frame < 4; frame++) {
			plan = BedrockRuntime.plan(defs, attachable, state, frame * 0.05, queries("minecraft:player", 0), 0, context(firstPerson));
		}
		Pose pose = plan.poses().get("item");
		assertNotNull(pose, attachable.identifier() + " item bone posed");
		return pose;
	}

	private static MutableObjectBinding context(boolean firstPerson) {
		MutableObjectBinding context = new MutableObjectBinding();
		context.set("is_first_person", Value.of(firstPerson));
		context.set("item_slot", StringValue.of("main_hand"));
		return context;
	}

	private static String variable(String name) {
		return AddonNames.current().id(AddonNames.Kind.VARIABLE, name);
	}

	private static String bone(String name) {
		return AddonNames.current().id(AddonNames.Kind.BONE, name);
	}

	private static void assertTexturesExist(RenderPlan plan) {
		for (Layer layer : plan.layers()) {
			assertTrue(SignTextures.isGenerated(layer.texture()) || Files.exists(ASSETS.resolve(layer.texture().getPath())),
					"missing texture " + layer.texture());
			assertTrue(Files.exists(ASSETS.resolve("geckolib/models/" + layer.model().getPath() + ".geo.json")),
					"missing model " + layer.model());
		}
	}

	private static List<JsonObject> read(Path dir) throws IOException {
		List<JsonObject> out = new ArrayList<>();
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				out.add(json(file));
			}
		}
		return out;
	}

	private static JsonObject json(Path file) throws IOException {
		return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
	}
}
