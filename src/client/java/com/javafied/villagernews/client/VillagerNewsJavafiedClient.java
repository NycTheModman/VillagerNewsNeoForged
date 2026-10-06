package com.javafied.villagernews.client;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockAnimatable;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions;
import com.javafied.villagernews.client.bedrock.BedrockEntityRenderer;
import com.javafied.villagernews.client.bedrock.BedrockGeoModel;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.VillagerVariantKeys;
import com.javafied.villagernews.molang.MolangProgram;

import com.javafied.villagernews.platform.ClientModInitializer;
import com.javafied.villagernews.platform.ClientTickEvents;
import com.javafied.villagernews.platform.EntityRendererRegistry;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.EntityType;

public class VillagerNewsJavafiedClient implements ClientModInitializer {
	/** The add-on's one sheep reskin. Hand-picked for now; mapping vanilla mobs to reskins from the manifest comes later. */
	/** The add-on's wandering trader character. */
	private static final String WANDERING_TRADER_CLIENT_ENTITY = "wandering_trader";

	private boolean promptedThisSession = false;

	@Override
	public void onInitializeClient() {
		MolangProgram.setErrorReporter(VillagerNewsJavafied.LOGGER::warn);
		com.javafied.villagernews.platform.ClientPlatform.reload(BedrockDefinitions.ID, new BedrockDefinitions());
		DialogClient.init();
		com.javafied.villagernews.client.guide.GuideClient.init();
		
		DialogDebugOverlay.init();

		// Wooly is drawn through VariantRenderers, so ordinary sheep keep vanilla's renderer.
		// The add-on turns every wandering trader into its own character.
		EntityRendererRegistry.register(EntityType.WANDERING_TRADER, context -> new BedrockEntityRenderer<>(context,
				new BedrockGeoModel(entity -> WANDERING_TRADER_CLIENT_ENTITY), new BedrockAnimatable()));
		EntityRendererRegistry.register(EntityType.VILLAGER, context -> new BedrockEntityRenderer<>(context,
				new BedrockGeoModel(entity -> entity.getExistingData(ModAttachments.VILLAGER_VARIANT).orElse(VillagerVariantKeys.DEFAULT)),
				new BedrockAnimatable()));

	}
}
