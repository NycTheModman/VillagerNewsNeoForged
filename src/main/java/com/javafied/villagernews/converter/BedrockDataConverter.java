package com.javafied.villagernews.converter;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Carries the add-on's own client-side logic across verbatim, for the mod's
 * Bedrock runtime to interpret: client entity definitions (scripts, render
 * controller lists, resource tables), render controllers, animation
 * controllers and materials - plus each behavior-pack entity's property
 * defaults, which that logic reads through {@code q.property(...)}.
 *
 * <p>Interpreted rather than translated into Java: the add-on's identifiers
 * are obfuscated and can change between releases, and none of it should end
 * up baked into the mod itself.
 */
public final class BedrockDataConverter {
	private BedrockDataConverter() {
	}

	public static int convert(Path resourcePack, Path behaviorPack, Path outputAssetsDir) throws IOException {
		Path outDir = outputAssetsDir.resolve("bedrock");
		int count = 0;
		count += copyDir(resourcePack.resolve("entity"), outDir.resolve("entity"), ".json");
		fixSheepMaterialTextures(resourcePack, outputAssetsDir);
		count += copyDir(resourcePack.resolve("render_controllers"), outDir.resolve("render_controllers"), ".json");
		count += copyDir(resourcePack.resolve("animation_controllers"), outDir.resolve("animation_controllers"), ".json");
		count += copyDir(resourcePack.resolve("animations"), outDir.resolve("animations"), ".json");
		count += copyDir(resourcePack.resolve("materials"), outDir.resolve("materials"), ".material");
		// Attachables: how held items are drawn and animated in the hand.
		count += copyDir(resourcePack.resolve("attachables"), outDir.resolve("attachables"), ".json");
		if (behaviorPack != null) {
			writePropertyDefaults(behaviorPack.resolve("entities"), outDir.resolve("properties.json"));
			writeTraits(behaviorPack.resolve("entities"), outDir.resolve("traits.json"));
			count += 2;
			// Behavior definitions (sensors, events, properties) for the server-side interpreter.
			Path server = outputAssetsDir.getParent().getParent().resolve("server");
			count += copyDir(behaviorPack.resolve("entities"), server.resolve("entities"), ".json");
			// Trade tables, referenced by path from the entities' economy_trade_table component.
			count += copyDir(behaviorPack.resolve("trading"), server.resolve("trading"), ".json");
		}
		return count;
	}

	/** Resolve every texture selected by a sheep-material controller, including alternate skins. */
	static void fixSheepMaterialTextures(Path resourcePack, Path outputAssetsDir) throws IOException {
		Path entityDir=resourcePack.resolve("entity"), controllerDir=resourcePack.resolve("render_controllers");
		if (!Files.isDirectory(entityDir) || !Files.isDirectory(controllerDir)) return;
		var controllers=new java.util.HashMap<String,JsonObject>();
		try(var files=Files.walk(controllerDir)) {
			for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()) {
				JsonObject all=ConverterUtil.readJson(file).getAsJsonObject("render_controllers");
				if(all!=null)all.entrySet().forEach(e->controllers.put(e.getKey(),e.getValue().getAsJsonObject()));
			}
		}
		var converted=new java.util.HashSet<String>();
		var textureReference=java.util.regex.Pattern.compile("(?i)texture\\.([a-z0-9_]+)");
		try(var files=Files.walk(entityDir)) {
			for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()) {
				JsonObject ce=ConverterUtil.readJson(file).getAsJsonObject("minecraft:client_entity");
				if(ce==null)continue;
				JsonObject d=ce.getAsJsonObject("description"), materials=d.getAsJsonObject("materials"), textures=d.getAsJsonObject("textures");
				if(materials==null || textures==null || !d.has("render_controllers"))continue;
				for(var ref:d.getAsJsonArray("render_controllers")) {
					var names=ref.isJsonPrimitive()?java.util.Set.of(ref.getAsString()):ref.getAsJsonObject().keySet();
					for(String name:names) {
						JsonObject rc=controllers.get(name);
						if(rc==null || !rc.has("materials") || !rc.has("textures"))continue;
						boolean sheep=false;
						for(var assignment:rc.getAsJsonArray("materials"))for(var value:assignment.getAsJsonObject().entrySet()) {
							String alias=value.getValue().getAsString().replaceFirst("(?i)^material\\.","");
							if(materials.has(alias) && materials.get(alias).getAsString().equals("sheep"))sheep=true;
						}
						if(!sheep)continue;
						for(var expression:rc.getAsJsonArray("textures")) {
							var matcher=textureReference.matcher(expression.getAsString());
							while(matcher.find()) {
								String alias=matcher.group(1).toLowerCase(java.util.Locale.ROOT);
								if(textures.has(alias)) {
									String path=textures.get(alias).getAsString();
									if(converted.add(path))TextureConverter.opaqueDyeMask(outputAssetsDir.resolve(path+".png"));
								}
							}
						}
					}
				}
			}
		}
	}

	private static int copyDir(Path from, Path to, String extension) throws IOException {
		if (!Files.isDirectory(from)) {
			return 0;
		}
		int count = 0;
		try (var stream = Files.walk(from)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(extension)).toList()) {
				Path target = to.resolve(from.relativize(file).toString());
				Files.createDirectories(target.getParent());
				Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				count++;
			}
		}
		return count;
	}

	/** { "ns:entity": { "p:prop": default, ... }, ... } */
	/**
	 * What the client needs to know about an entity's behavior-pack definition
	 * to draw it: {@code { "ns:entity": { "baby": true, "scale": 0.5 } }} for
	 * entities that are always babies ({@code minecraft:is_baby}) or always
	 * scaled ({@code minecraft:scale}) - the Mayor is both.
	 */
	private static void writeTraits(Path entitiesDir, Path target) throws IOException {
		JsonObject all = new JsonObject();
		if (Files.isDirectory(entitiesDir)) {
			try (var stream = Files.walk(entitiesDir)) {
				for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
					JsonObject entity = ConverterUtil.readJson(file).getAsJsonObject("minecraft:entity");
					JsonObject components = entity == null ? null : entity.getAsJsonObject("components");
					if (components == null) {
						continue;
					}
					JsonObject traits = new JsonObject();
					if (components.has("minecraft:is_baby")) {
						traits.addProperty("baby", true);
					}
					JsonObject scale = components.getAsJsonObject("minecraft:scale");
					if (scale != null && scale.has("value") && scale.get("value").isJsonPrimitive()) {
						traits.add("scale", scale.get("value"));
					}
					if (traits.size() > 0) {
						all.add(entity.getAsJsonObject("description").get("identifier").getAsString(), traits);
					}
				}
			}
		}
		ConverterUtil.writeJson(target, all);
	}

	private static void writePropertyDefaults(Path entitiesDir, Path target) throws IOException {
		JsonObject all = new JsonObject();
		if (Files.isDirectory(entitiesDir)) {
			try (var stream = Files.walk(entitiesDir)) {
				for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
					JsonObject entity = ConverterUtil.readJson(file).getAsJsonObject("minecraft:entity");
					if (entity == null) {
						continue;
					}
					JsonObject description = entity.getAsJsonObject("description");
					JsonObject properties = description.getAsJsonObject("properties");
					JsonObject defaults = new JsonObject();
					if (properties != null) {
						for (String name : properties.keySet()) {
							JsonObject property = properties.getAsJsonObject(name);
							if (property.has("default")) {
								defaults.add(name, property.get("default"));
							}
						}
					}
					all.add(description.get("identifier").getAsString(), defaults);
				}
			}
		}
		ConverterUtil.writeJson(target, all);
	}
}
