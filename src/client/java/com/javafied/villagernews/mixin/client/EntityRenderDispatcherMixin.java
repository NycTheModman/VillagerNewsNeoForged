package com.javafied.villagernews.mixin.client;

import com.javafied.villagernews.client.bedrock.VariantRenderers;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets single entities of a vanilla type be drawn as an add-on character
 * (Wooly among ordinary sheep) - both when rendering starts from the entity
 * and when it continues from its render state.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
			at = @At("HEAD"), cancellable = true)
	private void villagernewsjavafied$rendererForEntity(Entity entity, CallbackInfoReturnable cir) {
		EntityRenderer<?> renderer = VariantRenderers.forEntity(entity);
		if (renderer != null) {
			cir.setReturnValue(renderer);
		}
	}

}
