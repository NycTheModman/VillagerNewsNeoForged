package com.javafied.villagernews.client.item;

import com.javafied.villagernews.content.WearableItem;

import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** {@link WearableItem} as the client builds it: with its armor renderer. */
public class ClientWearableItem extends WearableItem {
	public ClientWearableItem(Properties properties) {
		super(properties);
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		consumer.accept(new GeoRenderProvider() {
			private WornItemRenderer renderer;

			@Override
			public <T extends net.minecraft.world.entity.LivingEntity> net.minecraft.client.model.HumanoidModel<?> getGeoArmorRenderer(T entity,ItemStack stack,EquipmentSlot slot,net.minecraft.client.model.HumanoidModel<T> original) {
				if (renderer == null) {
					renderer = new WornItemRenderer(ClientWearableItem.this);
				}
				renderer.prepForRender(entity,stack,slot,original);return renderer;
			}
		});
	}
}
