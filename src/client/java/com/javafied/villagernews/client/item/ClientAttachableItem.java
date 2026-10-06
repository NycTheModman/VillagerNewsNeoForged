package com.javafied.villagernews.client.item;

import com.javafied.villagernews.content.AttachableItem;

import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.function.Consumer;

/** {@link AttachableItem} as the client builds it: with its attachable renderer. */
public class ClientAttachableItem extends AttachableItem {
	public ClientAttachableItem(Properties properties, boolean guide) {
		super(properties, guide);
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		consumer.accept(new GeoRenderProvider() {
			private AttachableItemRenderer renderer;

			@Override
			public GeoItemRenderer<?> getGeoItemRenderer() {
				if (renderer == null) {
					renderer = new AttachableItemRenderer(ClientAttachableItem.this);
				}
				return renderer;
			}
		});
	}
}
