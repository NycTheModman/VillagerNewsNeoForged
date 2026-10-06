package com.javafied.villagernews.mixin.client;

import com.javafied.villagernews.client.bedrock.VariantRenderers;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Builds the per-entity add-on renderers whenever vanilla (re)builds its own. */
@Mixin(EntityRenderers.class)
abstract class EntityRenderersMixin {
	@Inject(method = "createEntityRenderers", at = @At("RETURN"))
	private static void villagernewsjavafied$createVariantRenderers(EntityRendererProvider.Context context,
			CallbackInfoReturnable<Map<EntityType<?>, EntityRenderer<?>>> cir) {
		VariantRenderers.rebuild(context);
	}
}
