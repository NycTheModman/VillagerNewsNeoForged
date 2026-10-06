package com.javafied.villagernews.content;

import com.javafied.villagernews.dialog.DialogDebug;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.javafied.villagernews.platform.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /villagernews summon <variant>} - spawns a real vanilla Villager
 * tagged to render as one of the addon's reskins.
 * {@code /villagernews debug} - toggles an overlay showing what the villager
 * you look at is saying, its cooldowns, and why its last reaction didn't
 * happen (see {@link DialogDebug}).
 */
public final class VillagerCommand {
	private VillagerCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			SuggestionProvider<CommandSourceStack> suggestVariants = (ctx, builder) -> {
				VillagerVariantKeys.ALL.forEach(builder::suggest);
				return builder.buildFuture();
			};

			dispatcher.register(Commands.literal("villagernews")
					.then(Commands.literal("summon")
							.requires(source -> source.hasPermission(2))
							.then(Commands.argument("variant", StringArgumentType.word())
									.suggests(suggestVariants)
									.executes(ctx -> summon(ctx.getSource(), StringArgumentType.getString(ctx, "variant")))))
					.then(Commands.literal("debug")
							.requires(source -> source.hasPermission(2))
							.executes(ctx -> debug(ctx.getSource()))));
		});
	}

	private static int debug(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		boolean on = DialogDebug.toggle(source.getPlayerOrException());
		source.sendSuccess(() -> Component.literal(on
				? "Villager debug on: look at a villager to see its dialog state"
				: "Villager debug off"), false);
		return 1;
	}

	private static int summon(CommandSourceStack source, String variant) {
		if (!VillagerVariantKeys.isValid(variant)) {
			source.sendFailure(Component.literal("Unknown variant '" + variant + "'. Known: " + VillagerVariantKeys.ALL));
			return 0;
		}

		Villager villager = EntityType.VILLAGER.create(source.getLevel());
		if (villager == null) {
			source.sendFailure(Component.literal("Could not create a villager"));
			return 0;
		}

		Vec3 pos = source.getPosition();
		villager.setPos(pos.x, pos.y, pos.z);
		villager.setData(ModAttachments.VILLAGER_VARIANT, variant);
		if (SpecialTrades.hasOwnTrades(variant)) {
			SpecialTrades.makeTrader(source.getLevel(), villager);
		}
		source.getLevel().addFreshEntity(villager);

		source.sendSuccess(() -> Component.literal("Summoned villager variant '" + variant + "'"), true);
		return 1;
	}
}
