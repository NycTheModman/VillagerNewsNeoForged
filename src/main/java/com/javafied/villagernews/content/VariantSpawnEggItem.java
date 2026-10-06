package com.javafied.villagernews.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Spawns one of the add-on's characters: the vanilla mob it's drawn over,
 * tagged with the add-on variant and named after it (the add-on names its
 * special villagers when they spawn).
 */
public class VariantSpawnEggItem extends Item {
	private final EntityType<? extends Mob> type;
	private final String variant;

	public VariantSpawnEggItem(EntityType<? extends Mob> type, String variant, Properties properties) {
		super(properties);
		this.type = type;
		this.variant = variant;
	}

	@Override
	public String getDescriptionId() { return "item.spawn_egg.entity.villagernewsjavafied." + variant; }

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clicked = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
		Mob mob = type.spawn(level, spawned -> {
			spawned.setData(ModAttachments.VILLAGER_VARIANT, variant);
			spawned.setCustomName(Component.translatable("entity.villagernewsjavafied." + variant));
			if (spawned instanceof Villager villager && SpecialTrades.hasOwnTrades(variant)) {
				SpecialTrades.makeTrader(level, villager);
			}
		}, pos, MobSpawnType.SPAWN_EGG, true, face == Direction.UP);
		if (mob != null) {
			context.getItemInHand().consume(1, context.getPlayer());
			level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, pos);
		}
		return InteractionResult.SUCCESS;
	}
}
