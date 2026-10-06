package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.behavior.BehaviorProperties;
import com.javafied.villagernews.client.bedrock.BedrockAnimations.Animation;
import com.javafied.villagernews.client.bedrock.BedrockAnimations.AnimationRef;
import com.javafied.villagernews.client.bedrock.BedrockAnimations.BoneAnimation;
import com.javafied.villagernews.client.bedrock.BedrockAnimations.Controller;
import com.javafied.villagernews.client.bedrock.BedrockAnimations.State;
import com.javafied.villagernews.client.bedrock.BedrockAnimations.Transition;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions.BonePattern;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions.ClientEntity;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions.ControllerRef;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions.RenderController;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.converter.ConverterUtil;
import com.javafied.villagernews.converter.TextureConverter;
import com.javafied.villagernews.molang.MolangProgram;
import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.ArrayValue;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.StringValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Runs an add-on client entity's own logic for a Java entity, once per frame:
 * {@code initialize} scripts the first time, {@code pre_animation} scripts
 * every frame, then its render controllers and animations - producing a
 * {@link RenderPlan}: which geometry and textures to draw in which order,
 * which bones are hidden, and how every bone is posed. Entity variables
 * ({@code v.*}), controller states and animation times persist per entity
 * between frames, like in Bedrock.
 */
public final class BedrockRuntime {
	/** One textured pass over the model: Bedrock renders a render controller once per texture it lists. */
	/**
	 * @param uOffset,vOffset {@code uv_anim} scrolling, in fractions of the texture
	 * @param dyed            drawn tinted with the entity's dye colour (the wool of a {@code sheep}-material model)
	 */
	public record Layer(ResourceLocation model, ResourceLocation texture, BedrockMaterials.Kind kind, float uOffset, float vOffset, boolean dyed) {
		public Layer(ResourceLocation model, ResourceLocation texture, BedrockMaterials.Kind kind) {
			this(model, texture, kind, 0, 0, false);
		}

		public Layer(ResourceLocation model, ResourceLocation texture, BedrockMaterials.Kind kind, float uOffset, float vOffset) {
			this(model, texture, kind, uOffset, vOffset, false);
		}
	}

	public record BoneVisibility(BonePattern pattern, boolean visible) {
	}

	/** A bone's animated offset from its bind pose, in Bedrock units (degrees, pixels). */
	public static final class Pose {
		public double rx, ry, rz, px, py, pz, sx = 1, sy = 1, sz = 1;
	}

	/**
	 * @param poses keyed by lower-case bone name (Bedrock bone names are case-insensitive)
	 * @param lift  blocks to raise the whole model by (see {@link VillagerPuppetPort#lift})
	 */
	public record RenderPlan(List<Layer> layers, List<BoneVisibility> boneVisibility, Map<String, Pose> poses, float scale,
			float lift) {
		public boolean isBoneVisible(String bone) {
			boolean visible = true;
			for (BoneVisibility rule : boneVisibility) {
				if (rule.pattern().matches(bone)) {
					visible = rule.visible();
				}
			}
			return visible;
		}
	}

	/** Everything that persists for one entity between frames. */
	public static final class EntityState {
		final MutableObjectBinding variables = new MutableObjectBinding();
		final Map<String, ControllerState> controllers = new HashMap<>();
		final Map<String, AnimationState> animations = new HashMap<>();
		/** Played once over the {@code animate} list, like Bedrock's {@code entity.playAnimation(name)} (e.g. lip sync). */
		final Set<String> scriptAnimations = new java.util.LinkedHashSet<>();
		/** Bedrock sound events controller states asked for since the last frame; the caller plays them. */
		final List<String> pendingSounds = new ArrayList<>();
		String clientEntity;
		double lastTime = Double.NaN;

		public MutableObjectBinding variables() {
			return variables;
		}

		public List<String> pendingSounds() {
			return pendingSounds;
		}

		public void playAnimation(String name) {
			String key = name.toLowerCase(Locale.ROOT);
			animations.remove(SCRIPT_PREFIX + key); // replaying restarts it
			scriptAnimations.add(key);
		}
	}

	private static final String SCRIPT_PREFIX = "script/";

	private static final class ControllerState {
		String state;
		double enteredAt;

		ControllerState(String state, double enteredAt) {
			this.state = state;
			this.enteredAt = enteredAt;
		}
	}

	private static final class AnimationState {
		double time;
		/** Timeline events up to (and including) this time have fired; below 0 so a key at 0 fires on start. */
		double firedUpTo = -1;
		boolean finished;
	}

	// Render-thread only. Weak keys: state goes away with the entity.
	private static final Map<Entity, EntityState> STATES = new WeakHashMap<>();
	/** Per holder, per attachable and way of holding it (first/third person, which hand): each runs its own controllers. */
	private static final Map<Entity, Map<String, EntityState>> ATTACHABLE_STATES = new WeakHashMap<>();

	private BedrockRuntime() {
	}

	/** Starts a one-shot animation on the entity's model, e.g. the lip sync of a line it's speaking. */
	public static void playAnimation(Entity entity, String animation) {
		STATES.computeIfAbsent(entity, e -> new EntityState()).playAnimation(animation);
	}

	/**
	 * A held item's attachable as its holder carries it, animated like the
	 * add-on's: queries answer for the holder, and {@code c.is_first_person} /
	 * {@code c.item_slot} say how it's held. Null if the add-on has no such
	 * attachable (or isn't converted).
	 */
	public static RenderPlan evaluateAttachable(Entity holder, String attachable, boolean firstPerson, boolean mainHand, float partialTick) {
		return evaluateAttachable(holder, attachable, firstPerson, mainHand ? "main_hand" : "off_hand", partialTick);
	}

	/**
	 * A worn item's attachable as its wearer has it on ({@code c.item_slot}
	 * "head"): the villager nose swings on its spring like a villager's own.
	 */
	public static RenderPlan evaluateWorn(Entity wearer, String attachable, float partialTick) {
		return evaluateAttachable(wearer, attachable, false, "head", partialTick);
	}

	private static RenderPlan evaluateAttachable(Entity holder, String attachable, boolean firstPerson, String slot, float partialTick) {
		BedrockDefinitions.Snapshot defs = BedrockDefinitions.get();
		ClientEntity ce = defs.attachable(attachable);
		if (ce == null) {
			return null;
		}
		String key = attachable + (firstPerson ? "/first_person" : "/third_person") + "/" + slot;
		EntityState state = ATTACHABLE_STATES.computeIfAbsent(holder, e -> new HashMap<>()).computeIfAbsent(key, k -> new EntityState());
		MutableObjectBinding context = new MutableObjectBinding();
		context.set("is_first_person", Value.of(firstPerson));
		context.set("item_slot", StringValue.of(slot));
		EntityQueries queries = new EntityQueries(holder, partialTick, Map.of(), Map.of(), false);
		RenderPlan plan = plan(defs, ce, state, (holder.tickCount + partialTick) / 20.0, queries, 0, context);
		state.pendingSounds.clear();
		return plan;
	}

	/** An attachable's default geometry and texture, for when its render controllers draw nothing. */
	public static Layer attachableBaseLayer(String attachable) {
		ClientEntity ce = BedrockDefinitions.get().attachable(attachable);
		if (ce == null || !ce.geometry().containsKey("default") || !ce.textures().containsKey("default")) {
			return null;
		}
		return new Layer(modelId(ce.geometry().get("default")), textureId(ce.textures().get("default")), BedrockMaterials.Kind.CUTOUT);
	}

	/** Null if the add-on hasn't been converted (or doesn't define this client entity). */
	public static RenderPlan evaluate(Entity entity, String clientEntityId, float partialTick) {
		BedrockDefinitions.Snapshot defs = BedrockDefinitions.get();
		ClientEntity ce = defs.clientEntity(clientEntityId);
		if (ce == null) {
			return null;
		}
		EntityState state = STATES.computeIfAbsent(entity, e -> new EntityState());
		boolean puppet = ce.identifier().endsWith(":" + VillagerPuppetPort.puppet());
		Map<String, JsonElement> defaults = defs.properties(ce.identifier());
		Map<String, Value> overrides = new HashMap<>(puppet ? VillagerPuppetPort.hostDrivenProperties(entity) : Map.of());
		// Properties the server's port of the add-on changed (what it wears, its nose, ...).
		BehaviorProperties.readAll(entity).forEach((name, value) -> overrides.putIfAbsent(name, typed(defaults.get(name), value)));
		// The handbook's "Villager Style", which the add-on sets per player as a property override.
		String style = AddonNames.property("style");
		if (defaults.containsKey(style)) {
			overrides.put(style, Value.of(com.javafied.villagernews.client.guide.ClientSettings.style()));
		}
		EntityQueries queries = new EntityQueries(entity, partialTick, defaults, overrides, defs.alwaysBaby(ce.identifier()));
		RenderPlan plan = plan(defs, ce, state, (entity.tickCount + partialTick) / 20.0, queries,
				puppet ? VillagerPuppetPort.lift(entity) : 0);
		float entityScale = defs.entityScale(ce.identifier());
		if (entityScale != 1f) {
			// Bedrock scales the whole entity, model included (the Mayor is half size).
			plan = new RenderPlan(plan.layers(), plan.boneVisibility(), plan.poses(), plan.scale() * entityScale, plan.lift());
		}
		for (String sound : state.pendingSounds) {
			BedrockSounds.playFrom(entity, sound);
		}
		state.pendingSounds.clear();
		return plan;
	}

	/** A synced property's text, typed like the property's default. */
	private static Value typed(JsonElement defaultValue, String value) {
		if (defaultValue != null && defaultValue.isJsonPrimitive() && defaultValue.getAsJsonPrimitive().isBoolean()) {
			return Value.of(Boolean.parseBoolean(value));
		}
		if (defaultValue != null && defaultValue.isJsonPrimitive() && defaultValue.getAsJsonPrimitive().isNumber()) {
			try {
				return Value.of(Double.parseDouble(value));
			} catch (NumberFormatException e) {
				return Value.of(0);
			}
		}
		return StringValue.of(value);
	}

	/**
	 * The entity-independent core, so it can run against the real add-on data
	 * in tests.
	 *
	 * @param lifeTime seconds since the entity appeared; drives animation time
	 * @param lift     blocks to raise the model by, see {@link RenderPlan}
	 */
	public static RenderPlan plan(BedrockDefinitions.Snapshot defs, ClientEntity ce, EntityState state, double lifeTime,
			ObjectValue entityQueries, double lift) {
		return plan(defs, ce, state, lifeTime, entityQueries, lift, null);
	}

	/** @param context Molang's {@code c.*}/{@code context.*} (e.g. an attachable's {@code c.is_first_person}); may be null */
	public static RenderPlan plan(BedrockDefinitions.Snapshot defs, ClientEntity ce, EntityState state, double lifeTime,
			ObjectValue entityQueries, double lift, ObjectValue context) {
		if (!ce.identifier().equals(state.clientEntity)) {
			state.clientEntity = ce.identifier();
			state.controllers.clear();
			state.animations.clear();
			state.lastTime = Double.NaN;
		}
		boolean firstFrame = Double.isNaN(state.lastTime);
		// Clamped so a long pause (e.g. entity out of view) doesn't fast-forward a frame's worth of animation.
		double delta = firstFrame ? 0 : Math.max(0, Math.min(0.25, lifeTime - state.lastTime));
		state.lastTime = lifeTime;

		ContextQueries queries = new ContextQueries(entityQueries);
		queries.deltaTime = delta;
		Scope scope = MolangProgram.newScope();
		scope.set("variable", state.variables);
		scope.set("v", state.variables);
		scope.set("query", queries);
		scope.set("q", queries);
		MutableObjectBinding temps = new MutableObjectBinding();
		scope.set("temp", temps);
		scope.set("t", temps);
		scope.set("texture", table(ce.textures()));
		scope.set("geometry", table(ce.geometry()));
		scope.set("material", table(ce.materials()));
		if (context != null) {
			scope.set("context", context);
			scope.set("c", context);
		}

		if (firstFrame) {
			ce.initialize().forEach(script -> MolangProgram.of(script).eval(scope));
		}
		ce.preAnimation().forEach(script -> MolangProgram.of(script).eval(scope));

		List<Layer> layers = new ArrayList<>();
		List<BoneVisibility> boneVisibility = new ArrayList<>();
		evaluateRenderControllers(defs, ce, scope, layers, boneVisibility);

		Map<String, Pose> poses = new Animator(defs, ce, state, scope, queries, lifeTime, delta).run();

		double scale = MolangProgram.of(ce.scale()).evalNumber(scope);
		return new RenderPlan(List.copyOf(layers), List.copyOf(boneVisibility), poses, scale > 0 ? (float) scale : 1f,
				(float) lift);
	}

	private static void evaluateRenderControllers(BedrockDefinitions.Snapshot defs, ClientEntity ce, Scope scope,
			List<Layer> layers, List<BoneVisibility> boneVisibility) {
		for (ControllerRef ref : ce.renderControllers()) {
			if (ref.condition() != null && !MolangProgram.of(ref.condition()).evalBoolean(scope)) {
				continue;
			}
			RenderController rc = defs.renderController(ref.name());
			if (rc == null) {
				continue;
			}
			scope.set("array", new Arrays(rc.arrays(), scope));

			boolean layerVisible = true;
			for (BonePattern pattern : rc.partVisibility()) {
				boolean visible = MolangProgram.of(pattern.molang()).evalBoolean(scope);
				if (pattern.pattern().equals("*")) {
					layerVisible = visible;
				} else {
					boneVisibility.add(new BoneVisibility(pattern, visible));
				}
			}
			String materialName = material(rc, scope);
			BedrockMaterials.Kind kind = defs.materials().classify(materialName);
			boolean dyed = defs.materials().isDyed(materialName);
			if (!layerVisible || kind == BedrockMaterials.Kind.HIDDEN) {
				continue;
			}

			ResourceLocation model = modelId(MolangProgram.of(rc.geometry()).eval(scope).getAsString());
			float uOffset = rc.uvOffset().size() == 2 ? (float) MolangProgram.of(rc.uvOffset().get(0)).evalNumber(scope) : 0;
			float vOffset = rc.uvOffset().size() == 2 ? (float) MolangProgram.of(rc.uvOffset().get(1)).evalNumber(scope) : 0;
			for (String textureExpression : rc.textures()) {
				String texture = MolangProgram.of(textureExpression).eval(scope).getAsString();
				if (!texture.isEmpty()) {
					layers.add(new Layer(model, textureId(texture), kind, uOffset, vOffset));
					if (dyed) {
						// Bedrock's sheep material: the dye tints what the texture's alpha marks (the converter's mask).
						layers.add(new Layer(model, textureId(texture + TextureConverter.DYE_MASK_SUFFIX), BedrockMaterials.Kind.CUTOUT,
								uOffset, vOffset, true));
					}
				}
			}
		}
	}

	/**
	 * One frame of Bedrock animation: walks the client entity's {@code animate}
	 * list, stepping animation controllers (state machines) and advancing each
	 * animation it reaches, and sums their bone transforms - Bedrock layers all
	 * active animations additively, each scaled by its blend weight.
	 */
	private static final class Animator {
		private final BedrockDefinitions.Snapshot defs;
		private final ClientEntity ce;
		private final EntityState state;
		private final Scope scope;
		private final ContextQueries queries;
		private final double lifeTime;
		private final double delta;
		private final Map<String, Pose> poses = new HashMap<>();
		private final Set<String> activeAnimations = new HashSet<>();
		private final double[] sample = new double[3];

		Animator(BedrockDefinitions.Snapshot defs, ClientEntity ce, EntityState state, Scope scope, ContextQueries queries,
				double lifeTime, double delta) {
			this.defs = defs;
			this.ce = ce;
			this.state = state;
			this.scope = scope;
			this.queries = queries;
			this.lifeTime = lifeTime;
			this.delta = delta;
		}

		Map<String, Pose> run() {
			for (ControllerRef ref : ce.animate()) {
				double weight = ref.condition() == null ? 1 : MolangProgram.of(ref.condition()).evalNumber(scope);
				if (weight > 0) {
					play(ref.name(), "animate/" + ref.name(), weight);
				}
			}
			for (String name : List.copyOf(state.scriptAnimations)) {
				Animation animation = defs.animation(name);
				String key = SCRIPT_PREFIX + name;
				playAnimation(animation, key, 1);
				AnimationState as = state.animations.get(key);
				if (animation == null || as == null || as.finished) {
					state.scriptAnimations.remove(name);
				}
			}
			// Animations that stopped playing restart from 0 next time they're reached.
			state.animations.keySet().retainAll(activeAnimations);
			return poses;
		}

		/** A short name from the client entity's animations table: an animation or an animation controller. */
		private void play(String shortName, String key, double weight) {
			String target = ce.animations().get(shortName.toLowerCase(Locale.ROOT));
			if (target == null) {
				return;
			}
			if (target.toLowerCase(Locale.ROOT).startsWith("controller.")) {
				runController(defs.animationController(target), key, weight);
			} else {
				playAnimation(defs.animation(target), key, weight);
			}
		}

		private void runController(Controller controller, String key, double weight) {
			if (controller == null) {
				return;
			}
			ControllerState cs = state.controllers.get(key);
			if (cs == null) {
				cs = new ControllerState(controller.initialState(), lifeTime);
				state.controllers.put(key, cs);
				runScripts(controller.states().get(cs.state), true);
			}
			State current = controller.states().get(cs.state);
			if (current == null) {
				return;
			}

			setStateQueries(key + "/" + cs.state + "/", lifeTime - cs.enteredAt);
			for (Transition transition : current.transitions()) {
				if (controller.states().containsKey(transition.target())
						&& MolangProgram.of(transition.condition()).evalBoolean(scope)) {
					runScripts(current, false);
					cs.state = transition.target();
					cs.enteredAt = lifeTime;
					current = controller.states().get(cs.state);
					runScripts(current, true);
					break; // Bedrock takes at most one transition per frame.
				}
			}

			String prefix = key + "/" + cs.state + "/";
			setStateQueries(prefix, lifeTime - cs.enteredAt);
			for (AnimationRef ref : current.animations()) {
				double blend = ref.blend() == null ? 1 : MolangProgram.of(ref.blend()).evalNumber(scope);
				if (blend > 0) {
					play(ref.name(), prefix + ref.name(), weight * blend);
				}
			}
		}

		private void setStateQueries(String prefix, double stateTime) {
			boolean any = false;
			boolean all = true;
			boolean none = true;
			for (Map.Entry<String, AnimationState> entry : state.animations.entrySet()) {
				if (entry.getKey().startsWith(prefix)) {
					none = false;
					any |= entry.getValue().finished;
					all &= entry.getValue().finished;
				}
			}
			queries.stateTime = stateTime;
			queries.anyAnimationFinished = any;
			queries.allAnimationsFinished = !none && all;
		}

		private void runScripts(State s, boolean entry) {
			if (s == null) {
				return;
			}
			(entry ? s.onEntry() : s.onExit()).forEach(script -> MolangProgram.of(script).eval(scope));
			if (entry) {
				for (String effect : s.soundEffects()) {
					String sound = ce.soundEffects().get(effect);
					if (sound != null) {
						state.pendingSounds.add(sound);
					}
				}
			}
		}

		private void playAnimation(Animation animation, String key, double weight) {
			if (animation == null) {
				return;
			}
			activeAnimations.add(key);
			AnimationState as = state.animations.computeIfAbsent(key, k -> new AnimationState());

			queries.animTime = as.time;
			double time = animation.animTimeUpdate() != null
					? MolangProgram.of(animation.animTimeUpdate()).evalNumber(scope)
					: as.time + delta;

			double length = animation.length();
			double sampleTime = time;
			boolean finished = false;
			if (length > 0 && time >= length) {
				switch (animation.loop()) {
					case LOOP -> {
						fireTimeline(animation, as, length);
						as.firedUpTo = -1;
						time %= length;
						sampleTime = time;
					}
					case HOLD -> {
						sampleTime = length;
						finished = true;
					}
					case ONCE -> finished = true;
				}
			}
			fireTimeline(animation, as, Math.min(time, length > 0 ? length : time));
			as.time = time;
			as.finished = finished;
			if (finished && animation.loop() == BedrockAnimations.Loop.ONCE) {
				return; // a finished one-shot no longer poses anything
			}

			if (animation.blendWeight() != null) {
				weight *= MolangProgram.of(animation.blendWeight()).evalNumber(scope);
			}
			queries.animTime = sampleTime;
			for (Map.Entry<String, BoneAnimation> bone : animation.bones().entrySet()) {
				BoneAnimation b = bone.getValue();
				Pose pose = poses.computeIfAbsent(bone.getKey(), k -> new Pose());
				if (b.rotation() != null) {
					b.rotation().sample(sampleTime, scope, sample);
					pose.rx += sample[0] * weight;
					pose.ry += sample[1] * weight;
					pose.rz += sample[2] * weight;
				}
				if (b.position() != null) {
					b.position().sample(sampleTime, scope, sample);
					pose.px += sample[0] * weight;
					pose.py += sample[1] * weight;
					pose.pz += sample[2] * weight;
				}
				if (b.scale() != null) {
					b.scale().sample(sampleTime, scope, sample);
					pose.sx *= 1 + (sample[0] - 1) * weight;
					pose.sy *= 1 + (sample[1] - 1) * weight;
					pose.sz *= 1 + (sample[2] - 1) * weight;
				}
			}
		}

		/** Runs timeline instructions whose time was crossed since last frame. */
		private void fireTimeline(Animation animation, AnimationState as, double upTo) {
			if (animation.timeline().isEmpty() || upTo <= as.firedUpTo) {
				return;
			}
			queries.animTime = upTo;
			for (List<String> scripts : animation.timeline().subMap(as.firedUpTo, false, upTo, true).values()) {
				scripts.forEach(script -> MolangProgram.of(script).eval(scope));
			}
			as.firedUpTo = upTo;
		}
	}

	/**
	 * Queries whose answer depends on where in the animation pass they're
	 * asked - {@code q.anim_time} inside an animation, {@code q.state_time} in a
	 * controller - layered over the entity's own queries.
	 */
	private static final class ContextQueries implements ObjectValue {
		private final ObjectValue entity;
		double animTime;
		double deltaTime;
		double stateTime;
		boolean anyAnimationFinished;
		boolean allAnimationsFinished;

		ContextQueries(ObjectValue entity) {
			this.entity = entity;
		}

		@Override
		public ObjectProperty getProperty(String name) {
			return switch (name.toLowerCase(Locale.ROOT)) {
				case "anim_time" -> ObjectProperty.property(Value.of(animTime), true);
				case "delta_time" -> ObjectProperty.property(Value.of(deltaTime), true);
				case "state_time" -> ObjectProperty.property(Value.of(stateTime), true);
				case "any_animation_finished" -> ObjectProperty.property(Value.of(anyAnimationFinished), true);
				case "all_animations_finished" -> ObjectProperty.property(Value.of(allAnimationsFinished), true);
				default -> entity.getProperty(name);
			};
		}
	}

	/**
	 * The material a render controller asks for. Bedrock allows one per bone
	 * pattern; Java draws a whole pass with one render type, so the catch-all
	 * {@code "*"} entry (or the first one) decides.
	 */
	private static String material(RenderController rc, Scope scope) {
		BonePattern chosen = null;
		for (BonePattern pattern : rc.materials()) {
			if (chosen == null || pattern.pattern().equals("*")) {
				chosen = pattern;
			}
		}
		return chosen == null ? null : MolangProgram.of(chosen.molang()).eval(scope).getAsString();
	}

	/** "geometry.oreville_vn.-754165646" -> the GeckoLib model the converter produced for it. */
	private static ResourceLocation modelId(String geometry) {
		return VillagerNewsJavafied.id("entity/" + ConverterUtil.slug(geometry));
	}

	/**
	 * "textures/oreville/vn/dil" -> assets/&lt;modid&gt;/textures/oreville/vn/dil.png;
	 * the vanilla Bedrock sign textures the add-on borrows are made from Java's (see {@link SignTextures}).
	 */
	private static ResourceLocation textureId(String bedrockPath) {
		ResourceLocation sign = SignTextures.idFor(bedrockPath);
		return sign != null ? sign : VillagerNewsJavafied.id(bedrockPath.toLowerCase(Locale.ROOT) + ".png");
	}

	/** {@code Texture.x} / {@code Geometry.x} / {@code Material.x}: the client entity's name -> value tables. */
	private static ObjectValue table(Map<String, String> entries) {
		return name -> {
			String value = entries.get(name.toLowerCase(Locale.ROOT));
			return value == null ? null : ObjectProperty.property(StringValue.of(value), true);
		};
	}

	/** {@code Array.x}: a render controller's arrays, whose elements are themselves Molang (e.g. "Texture.x"). */
	private record Arrays(Map<String, List<String>> arrays, Scope scope) implements ObjectValue {
		@Override
		public ObjectProperty getProperty(String name) {
			List<String> elements = arrays.get(name.toLowerCase(Locale.ROOT));
			if (elements == null || elements.isEmpty()) {
				return null;
			}
			Value[] values = new Value[elements.size()];
			for (int i = 0; i < values.length; i++) {
				values[i] = MolangProgram.of(elements.get(i)).eval(scope);
			}
			return ObjectProperty.property(ArrayValue.of(values), true);
		}
	}
}
