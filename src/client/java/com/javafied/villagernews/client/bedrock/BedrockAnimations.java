package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.molang.MolangProgram;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import team.unnamed.mocha.runtime.Scope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Bedrock animations and animation controllers, parsed from the add-on's JSON. */
public final class BedrockAnimations {
	private BedrockAnimations() {
	}

	/** A number known at load time, or Molang evaluated per frame. */
	public record Num(double constant, String molang) {
		static Num of(JsonElement e) {
			if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
				return new Num(e.getAsDouble(), null);
			}
			if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
				return new Num(e.getAsBoolean() ? 1 : 0, null);
			}
			String s = e.getAsString().strip();
			try {
				return new Num(Double.parseDouble(s), null);
			} catch (NumberFormatException notConstant) {
				return new Num(0, s);
			}
		}

		double eval(Scope scope) {
			return molang == null ? constant : MolangProgram.of(molang).evalNumber(scope);
		}
	}

	/** Bedrock accepts a bare value (all three axes) or a 1- or 3-element array. */
	public record Vec(Num x, Num y, Num z) {
		static Vec of(JsonElement e) {
			if (e.isJsonArray()) {
				JsonArray a = e.getAsJsonArray();
				if (a.size() >= 3) {
					return new Vec(Num.of(a.get(0)), Num.of(a.get(1)), Num.of(a.get(2)));
				}
				Num all = Num.of(a.get(0));
				return new Vec(all, all, all);
			}
			Num all = Num.of(e);
			return new Vec(all, all, all);
		}

		void eval(Scope scope, double[] out) {
			out[0] = x.eval(scope);
			out[1] = y.eval(scope);
			out[2] = z.eval(scope);
		}
	}

	public enum Lerp { LINEAR, CATMULLROM, STEP }

	public record Keyframe(double time, Vec pre, Vec post, Lerp lerp) {
	}

	/** One of a bone's rotation/position/scale: a single (possibly animated-by-Molang) value, or keyframes. */
	public record Channel(Vec constant, List<Keyframe> keyframes) {
		static Channel of(JsonElement e) {
			if (!e.isJsonObject() || isKeyframeObject(e.getAsJsonObject())) {
				return new Channel(keyframeValue(e, true), null);
			}
			List<Keyframe> keyframes = new ArrayList<>();
			for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
				double time = Double.parseDouble(entry.getKey());
				JsonElement v = entry.getValue();
				Lerp lerp = Lerp.LINEAR;
				if (v.isJsonObject() && v.getAsJsonObject().has("lerp_mode")) {
					lerp = switch (v.getAsJsonObject().get("lerp_mode").getAsString()) {
						case "catmullrom" -> Lerp.CATMULLROM;
						case "step" -> Lerp.STEP;
						default -> Lerp.LINEAR;
					};
				}
				keyframes.add(new Keyframe(time, keyframeValue(v, true), keyframeValue(v, false), lerp));
			}
			keyframes.sort((a, b) -> Double.compare(a.time(), b.time()));
			return new Channel(null, List.copyOf(keyframes));
		}

		/** A keyframe value: plain vector, or {"pre": .., "post": .., "vector": ..}. */
		private static Vec keyframeValue(JsonElement v, boolean pre) {
			if (v.isJsonObject()) {
				JsonObject o = v.getAsJsonObject();
				String first = pre ? "pre" : "post";
				String second = pre ? "post" : "pre";
				if (o.has(first)) {
					return Vec.of(o.get(first));
				}
				if (o.has("vector")) {
					return Vec.of(o.get("vector"));
				}
				if (o.has(second)) {
					return Vec.of(o.get(second));
				}
			}
			return Vec.of(v);
		}

		private static boolean isKeyframeObject(JsonObject o) {
			return o.has("pre") || o.has("post") || o.has("vector");
		}

		double lastTime() {
			return keyframes == null || keyframes.isEmpty() ? 0 : keyframes.getLast().time();
		}

		/** Bedrock keyframe sampling: hold before the first / after the last, pre/post values, linear, step or Catmull-Rom. */
		void sample(double time, Scope scope, double[] out) {
			if (keyframes == null) {
				constant.eval(scope, out);
				return;
			}
			int n = keyframes.size();
			if (time <= keyframes.getFirst().time() || n == 1) {
				keyframes.getFirst().pre().eval(scope, out);
				return;
			}
			if (time >= keyframes.getLast().time()) {
				keyframes.getLast().post().eval(scope, out);
				return;
			}
			int i = 0;
			while (i < n - 2 && keyframes.get(i + 1).time() <= time) {
				i++;
			}
			Keyframe a = keyframes.get(i);
			Keyframe b = keyframes.get(i + 1);
			double t = (time - a.time()) / (b.time() - a.time());
			double[] from = new double[3];
			double[] to = new double[3];
			a.post().eval(scope, from);
			if (a.lerp() == Lerp.STEP) {
				System.arraycopy(from, 0, out, 0, 3);
				return;
			}
			b.pre().eval(scope, to);
			if (a.lerp() == Lerp.CATMULLROM || b.lerp() == Lerp.CATMULLROM) {
				double[] before = new double[3];
				double[] after = new double[3];
				if (i > 0) {
					keyframes.get(i - 1).post().eval(scope, before);
				} else {
					System.arraycopy(from, 0, before, 0, 3);
				}
				if (i + 2 < n) {
					keyframes.get(i + 2).pre().eval(scope, after);
				} else {
					System.arraycopy(to, 0, after, 0, 3);
				}
				for (int k = 0; k < 3; k++) {
					out[k] = catmullRom(before[k], from[k], to[k], after[k], t);
				}
				return;
			}
			for (int k = 0; k < 3; k++) {
				out[k] = from[k] + (to[k] - from[k]) * t;
			}
		}

		private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
			double t2 = t * t;
			double t3 = t2 * t;
			return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
		}
	}

	public record BoneAnimation(Channel rotation, Channel position, Channel scale) {
	}

	public enum Loop { ONCE, LOOP, HOLD }

	public record Animation(String name, Loop loop, double length, String animTimeUpdate, String blendWeight,
			Map<String, BoneAnimation> bones, NavigableMap<Double, List<String>> timeline) {
	}

	public record AnimationRef(String name, String blend) {
	}

	public record Transition(String target, String condition) {
	}

	/** @param soundEffects short names from the client entity's {@code sound_effects}, played on entering the state */
	public record State(List<AnimationRef> animations, List<Transition> transitions, List<String> onEntry,
			List<String> onExit, List<String> soundEffects) {
	}

	public record Controller(String name, String initialState, Map<String, State> states) {
	}

	static void parseAnimations(JsonObject file, Map<String, Animation> out) {
		JsonObject animations = file.getAsJsonObject("animations");
		if (animations == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
			JsonObject a = entry.getValue().getAsJsonObject();
			Loop loop = Loop.ONCE;
			if (a.has("loop")) {
				JsonElement l = a.get("loop");
				if (l.isJsonPrimitive() && l.getAsJsonPrimitive().isBoolean()) {
					loop = l.getAsBoolean() ? Loop.LOOP : Loop.ONCE;
				} else if ("hold_on_last_frame".equals(l.getAsString())) {
					loop = Loop.HOLD;
				}
			}
			Map<String, BoneAnimation> bones = new HashMap<>();
			double length = 0;
			if (a.has("bones")) {
				for (Map.Entry<String, JsonElement> bone : a.getAsJsonObject("bones").entrySet()) {
					JsonObject b = bone.getValue().getAsJsonObject();
					Channel rotation = b.has("rotation") ? Channel.of(b.get("rotation")) : null;
					Channel position = b.has("position") ? Channel.of(b.get("position")) : null;
					Channel scale = b.has("scale") ? Channel.of(b.get("scale")) : null;
					for (Channel c : new Channel[] {rotation, position, scale}) {
						if (c != null) {
							length = Math.max(length, c.lastTime());
						}
					}
					bones.put(bone.getKey().toLowerCase(Locale.ROOT), new BoneAnimation(rotation, position, scale));
				}
			}
			NavigableMap<Double, List<String>> timeline = new TreeMap<>();
			if (a.has("timeline")) {
				for (Map.Entry<String, JsonElement> t : a.getAsJsonObject("timeline").entrySet()) {
					double time = Double.parseDouble(t.getKey());
					timeline.put(time, strings(t.getValue()));
					length = Math.max(length, time);
				}
			}
			if (a.has("animation_length")) {
				length = a.get("animation_length").getAsDouble();
			}
			out.put(entry.getKey().toLowerCase(Locale.ROOT), new Animation(entry.getKey(), loop, length,
					a.has("anim_time_update") ? molang(a.get("anim_time_update")) : null,
					a.has("blend_weight") ? molang(a.get("blend_weight")) : null,
					Map.copyOf(bones), timeline));
		}
	}

	static void parseControllers(JsonObject file, Map<String, Controller> out) {
		JsonObject controllers = file.getAsJsonObject("animation_controllers");
		if (controllers == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : controllers.entrySet()) {
			JsonObject c = entry.getValue().getAsJsonObject();
			Map<String, State> states = new HashMap<>();
			if (c.has("states")) {
				for (Map.Entry<String, JsonElement> s : c.getAsJsonObject("states").entrySet()) {
					JsonObject state = s.getValue().getAsJsonObject();
					List<AnimationRef> animations = new ArrayList<>();
					if (state.get("animations") instanceof JsonArray array) {
						for (JsonElement e : array) {
							if (e.isJsonObject()) {
								e.getAsJsonObject().entrySet().forEach(r -> animations.add(new AnimationRef(r.getKey(), molang(r.getValue()))));
							} else {
								animations.add(new AnimationRef(e.getAsString(), null));
							}
						}
					}
					List<Transition> transitions = new ArrayList<>();
					if (state.get("transitions") instanceof JsonArray array) {
						for (JsonElement e : array) {
							e.getAsJsonObject().entrySet().forEach(t -> transitions.add(new Transition(t.getKey(), molang(t.getValue()))));
						}
					}
					List<String> soundEffects = new ArrayList<>();
					if (state.get("sound_effects") instanceof JsonArray array) {
						for (JsonElement e : array) {
							if (e.isJsonObject() && e.getAsJsonObject().has("effect")) {
								soundEffects.add(e.getAsJsonObject().get("effect").getAsString().toLowerCase(Locale.ROOT));
							}
						}
					}
					states.put(s.getKey(), new State(List.copyOf(animations), List.copyOf(transitions),
							state.has("on_entry") ? strings(state.get("on_entry")) : List.of(),
							state.has("on_exit") ? strings(state.get("on_exit")) : List.of(), List.copyOf(soundEffects)));
				}
			}
			String initial = c.has("initial_state") ? c.get("initial_state").getAsString() : "default";
			out.put(entry.getKey().toLowerCase(Locale.ROOT), new Controller(entry.getKey(), initial, Map.copyOf(states)));
		}
	}

	private static List<String> strings(JsonElement e) {
		List<String> out = new ArrayList<>();
		if (e.isJsonArray()) {
			e.getAsJsonArray().forEach(x -> out.add(molang(x)));
		} else {
			out.add(molang(e));
		}
		return List.copyOf(out);
	}

	private static String molang(JsonElement e) {
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
			return e.getAsBoolean() ? "1" : "0";
		}
		return e.getAsString();
	}
}
