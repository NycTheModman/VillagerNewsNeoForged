package com.javafied.villagernews.dialog;

import com.javafied.villagernews.platform.ServerTickEvents;
import com.javafied.villagernews.platform.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /villagernews debug}: for players who turn it on, an overlay about
 * the villager (or trader, or Wooly) they look at - what it's saying, its cooldowns, what it's
 * waiting to say, and why its last reaction didn't happen.
 */
public final class DialogDebug {
	private static final int INTERVAL = 5;
	/** How closely a villager must be in the middle of the view to count as looked at (cosine). */
	private static final double LOOK_CONE = 0.97;
	private static final Set<UUID> watching = new HashSet<>();

	private DialogDebug() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DialogDebug::tick);
	}

	/** @return whether debug is now on for this player */
	public static boolean toggle(ServerPlayer player) {
		if (watching.remove(player.getUUID())) {
			ServerPlayNetworking.send(player, new DialogPayloads.Debug(List.of()));
			return false;
		}
		watching.add(player.getUUID());
		return true;
	}

	private static void tick(MinecraftServer server) {
		DialogEngine engine = DialogEngine.get();
		if (watching.isEmpty() || engine == null || server.getTickCount() % INTERVAL != 0) {
			return;
		}
		for (UUID id : Set.copyOf(watching)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				watching.remove(id);
				continue;
			}
			LivingEntity speaker = lookedAt(player);
			List<String> lines = new ArrayList<>();
			if (speaker != null) {
				lines.add(speaker.getName().getString() + " (" + Speakers.kindOf(speaker).name().toLowerCase(java.util.Locale.ROOT) + ")");
				lines.addAll(engine.describe(speaker));
			}
			ServerPlayNetworking.send(player, new DialogPayloads.Debug(lines));
		}
	}

	/** The one who could speak (a villager, the trader, Wooly) nearest the middle of the view. */
	private static LivingEntity lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDot = LOOK_CONE;
		for (LivingEntity speaker : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DialogEngine.RANGE),
				e -> Speakers.kindOf(e) != null)) {
			Vec3 to = speaker.getBoundingBox().getCenter().subtract(eye);
			double dot = look.dot(to.normalize());
			if (to.length() <= DialogEngine.RANGE && dot > bestDot) {
				best = speaker;
				bestDot = dot;
			}
		}
		return best;
	}
}
