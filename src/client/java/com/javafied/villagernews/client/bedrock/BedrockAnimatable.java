package com.javafied.villagernews.client.bedrock;

import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * GeckoLib's replaced-entity system builds one shared animatable per vanilla
 * EntityType; everything that differs per entity (which add-on client entity,
 * its variables, layers) lives in {@link BedrockRuntime} and reaches the
 * renderer through the render state instead.
 */
public final class BedrockAnimatable implements GeoReplacedEntity {
	@Override public net.minecraft.world.entity.EntityType<?> getReplacingEntityType(){return net.minecraft.world.entity.EntityType.VILLAGER;}
 private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// Animation controllers come with the next stage of the runtime.
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
