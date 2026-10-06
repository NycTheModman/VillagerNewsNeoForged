package com.javafied.villagernews;

import com.javafied.villagernews.behavior.BehaviorSensors;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.ModItems;
import com.javafied.villagernews.content.SpecialCharacters;
import com.javafied.villagernews.content.VillagerCommand;
import com.javafied.villagernews.dialog.BlockUseReactions;
import com.javafied.villagernews.dialog.DialogDebug;
import com.javafied.villagernews.dialog.DialogEngine;
import com.javafied.villagernews.dialog.DialogPayloads;
import com.javafied.villagernews.dialog.HurtReactions;
import com.javafied.villagernews.dialog.NoticeReactions;
import com.javafied.villagernews.dialog.PlayerActionReactions;
import com.javafied.villagernews.dialog.TradeReactions;
import com.javafied.villagernews.dialog.UntouchableReactions;
import com.javafied.villagernews.dialog.VillagerItemReactions;
import com.javafied.villagernews.dialog.VillagerLifeReactions;
import com.javafied.villagernews.dialog.VillagerReactions;
import com.javafied.villagernews.dialog.VillagerRoutineReactions;
import com.javafied.villagernews.dialog.WorldReactions;
import com.javafied.villagernews.guide.GuidePayloads;
import com.javafied.villagernews.guide.GuideSettings;
import com.javafied.villagernews.names.AddonNames;

import com.javafied.villagernews.platform.ModInitializer;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@net.neoforged.fml.common.Mod("villagernewsjavafied")
public class VillagerNewsJavafied implements ModInitializer {
 public VillagerNewsJavafied(net.neoforged.bus.api.IEventBus bus) { com.javafied.villagernews.platform.Platform.init(bus); ModItems.init(bus); onInitialize(); if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) com.javafied.villagernews.platform.ClientPlatform.init(bus); }
	public static final String MOD_ID = "villagernewsjavafied";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Villager News: Javafied initializing");
		AddonNames.setVersionSource(ConvertedPack::addonVersion);
		ModAttachments.init();
		VillagerCommand.init();
		DialogPayloads.register();
		GuidePayloads.register();
		GuideSettings.init();
		DialogEngine.init();
		DialogDebug.init();
		VillagerReactions.init();
		PlayerActionReactions.init();
		BlockUseReactions.init();
		VillagerLifeReactions.init();
		VillagerRoutineReactions.init();
		WorldReactions.init();
		NoticeReactions.init();
		HurtReactions.init();
		BehaviorSensors.init();
		VillagerItemReactions.init();
		TradeReactions.init();
		UntouchableReactions.init();
		SpecialCharacters.init();
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
