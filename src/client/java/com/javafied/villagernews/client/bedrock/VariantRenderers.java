package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.content.ModAttachments;



import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Add-on characters that are one special entity of an otherwise vanilla-
 * looking type: Wooly is a sheep, but ordinary sheep stay sheep. Entities
 * tagged with one of these variants get the add-on renderer; everything else
 * keeps vanilla's.
 */
public final class VariantRenderers {
	/** Per vanilla type: the add-on variants drawn with the add-on's model. */
	private static final Map<EntityType<?>, Set<String>> VARIANTS = Map.of(EntityType.SHEEP, Set.of("wooly"));

	private static Map<EntityType<?>, EntityRenderer<?>> renderers = Map.of();

	private VariantRenderers() {
	}

	public static void rebuild(EntityRendererProvider.Context context) {
		Map<EntityType<?>, EntityRenderer<?>> built = new HashMap<>();
		for (EntityType<?> type : VARIANTS.keySet()) {
			built.put(type, new BedrockEntityRenderer<>(context, new BedrockGeoModel(
					entity -> entity.getExistingData(ModAttachments.VILLAGER_VARIANT).orElse("")), new BedrockAnimatable()));
		}
		renderers = Map.copyOf(built);
	}

	public static EntityRenderer<?> forEntity(Entity entity) {
		Set<String> variants = VARIANTS.get(entity.getType());
		if (variants == null) {
			return null;
		}
		String variant = entity.getExistingDataOrNull(ModAttachments.VILLAGER_VARIANT);
		return variant != null && variants.contains(variant) ? renderers.get(entity.getType()) : null;
	}

}
