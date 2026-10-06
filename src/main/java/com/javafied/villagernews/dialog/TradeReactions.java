package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;
import com.javafied.villagernews.dialog.Speakers.Kind;

import com.javafied.villagernews.platform.UseEntityCallback;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's trading reactions: a greeting when the
 * trade window opens (friendlier the better the player's reputation - the
 * special characters and the trader have their own), a comment on each
 * purchase, a goodbye that depends on whether anything was bought, and a
 * cheer when a villager levels up. Plus two refusals to trade the add-on
 * ships without using: during a raid, and with a player it detests.
 */
public final class TradeReactions {
	/** Greetings by the player's reputation with the villager; the one closest to it is used. */
	private static final Map<Integer, String> GREETING_BY_REPUTATION = Map.of(
			100, "start_trading_with_extremely_high_reputation",
			50, "start_trading_with_high_reputation",
			0, "start_trading_with_neutral_reputation",
			-150, "start_trading_with_low_reputation",
			-300, "start_trading_with_extremely_low_reputation");

	/** Per speaker kind: own greeting (said half the time), goodbye, goodbye after buying, after not buying, per purchase. */
	private record Lines(String greeting, String bye, String boughtBye, String noPurchaseBye, String purchase) {
	}

	private static final Map<Kind, Lines> LINES = Map.of(
			Kind.VILLAGER, new Lines(null, "close_the_trading_window",
					"close_trading_after_buying", "close_trading_without_buying", "complete_a_trade"),
			Kind.MAYOR, new Lines("mayor_trade_greeting", null,
					"mayor_trade_bye_bought", "mayor_trade_bye_no_purchase", null),
			Kind.TESTIFICATE_MAN, new Lines("testificate_man_trade_greeting", "testificate_man_trade_bye",
					"testificate_man_trade_bye_bought", "testificate_man_trade_bye_no_purchase", "complete_a_trade"),
			Kind.NUMBER_5, new Lines("villager_5_trade_greeting", "villager_5_trade_bye",
					"villager_5_trade_bye_bought", "close_trading_without_buying", "complete_a_trade"),
			Kind.NUMBER_9, new Lines("villager_9_trade_greeting", "villager_9_trade_bye",
					"close_trading_after_buying", "close_trading_without_buying", "complete_a_trade"),
			Kind.TRADER, new Lines("wandering_trader_trade_greeting", "close_the_trading_window",
					"wandering_trader_trade_bye_bought", "wandering_trader_trade_bye_no_purchase", "wandering_trader_complete_a_trade"));
	private static final String LEVELLED_UP = "level_up";
	private static final String REACHED_MASTER = "reach_master_level";
	/** Refusals the add-on ships but never uses: during a raid, and to a player the villager can't stand. */
	private static final String REFUSES_DURING_RAID = "refuses_to_trade_during_a_raid";
	private static final String REFUSES_DISLIKED_PLAYER = "refuses_a_low_reputation_player";
	/**
	 * At or below this reputation a villager won't trade with the player at
	 * all. Java's reputation runs down to -700 (iron golems turn on a player
	 * below -100); this takes killing a few villagers in front of the others.
	 */
	static final int REFUSES_AT_REPUTATION = -450;
	/** Trading dialogs don't interrupt each other (the script's zcphsg). */
	private static final Set<String> TRADE_DIALOGS = new HashSet<>(GREETING_BY_REPUTATION.values());

	static {
		for (Lines lines : LINES.values()) {
			for (String line : new String[] {lines.greeting(), lines.bye(), lines.boughtBye(), lines.noPurchaseBye(), lines.purchase()}) {
				if (line != null) {
					TRADE_DIALOGS.add(line);
				}
			}
		}
		TRADE_DIALOGS.addAll(List.of(LEVELLED_UP, REACHED_MASTER, REFUSES_DURING_RAID, REFUSES_DISLIKED_PLAYER));
	}

	private static final Map<AbstractVillager, Boolean> boughtSomething = new WeakHashMap<>();
	private static final Map<AbstractVillager, Long> lastPurchaseComment = new WeakHashMap<>();

	private TradeReactions() {
	}

	/** Before the trade window opens: a villager may refuse (and shake its head, as with nothing to sell). */
	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(level instanceof ServerLevel server) || !(entity instanceof Villager villager)
					|| Speakers.kindOf(villager) != Kind.VILLAGER || Speakers.isBaby(villager) || villager.isSleeping() || villager.isTrading()
					|| !employed(villager) || player.isSecondaryUseActive() || DialogEngine.get() == null) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.is(Items.NAME_TAG) || held.is(Items.LEAD) || held.getItem() instanceof SpawnEggItem) {
				return InteractionResult.PASS;
			}
			Raid raid = server.getRaidAt(villager.blockPosition());
			String refusal = raid != null && !raid.isOver() ? REFUSES_DURING_RAID
					: villager.getPlayerReputation(player) <= REFUSES_AT_REPUTATION ? REFUSES_DISLIKED_PLAYER : null;
			if (refusal == null) {
				return InteractionResult.PASS;
			}
			villager.setUnhappyCounter(40);
			say(villager, player, refusal);
			return InteractionResult.SUCCESS;
		});
	}

	public static void tradingChanged(AbstractVillager merchant, Player before, Player after) {
		Kind kind = Speakers.kindOf(merchant);
		Lines lines = LINES.get(kind);
		if (lines == null || kind == Kind.VILLAGER && !employed((Villager) merchant)) {
			return;
		}
		if (before == null && after != null) {
			boughtSomething.put(merchant, false);
			String greeting;
			if (kind == Kind.MAYOR) {
				greeting = lines.greeting();
			} else if (lines.greeting() != null && ThreadLocalRandom.current().nextBoolean()) {
				greeting = lines.greeting();
			} else {
				greeting = greeting(merchant instanceof Villager villager ? villager.getPlayerReputation(after) : 0);
			}
			say(merchant, after, greeting);
		} else if (before != null && after == null) {
			boolean bought = Boolean.TRUE.equals(boughtSomething.remove(merchant));
			if (!say(merchant, before, bought ? lines.boughtBye() : lines.noPurchaseBye())) {
				say(merchant, before, lines.bye());
			}
		}
	}

	public static void traded(AbstractVillager merchant) {
		Lines lines = LINES.get(Speakers.kindOf(merchant));
		if (lines == null || merchant.getTradingPlayer() == null) {
			return;
		}
		boughtSomething.put(merchant, true);
		DialogEngine engine = DialogEngine.get();
		Long last = lastPurchaseComment.get(merchant);
		DialogEngine.Speech speech = engine == null ? null : engine.speech(merchant);
		boolean levelling = speech != null && (speech.dialog().id().equals(LEVELLED_UP) || speech.dialog().id().equals(REACHED_MASTER));
		if (engine != null && lines.purchase() != null && !levelling && (last == null || last != engine.now())) {
			lastPurchaseComment.put(merchant, engine.now());
			say(merchant, merchant.getTradingPlayer(), lines.purchase());
		}
	}

	/** Called (through a mixin) when a villager's trading level goes up: a cheer - a bigger one at master. */
	public static void levelledUp(Villager villager) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null) {
			return;
		}
		Player player = villager.level().getNearestPlayer(villager, Reactions.NEARBY);
		Options options = Options.DEFAULT.forced().asUrgent();
		if (player != null) {
			options = options.facing(player);
		}
		if (villager.getVillagerData().getLevel() >= 5 && engine.speakNow(villager, REACHED_MASTER, options)) {
			return;
		}
		engine.speakNow(villager, LEVELLED_UP, options);
	}

	static String greeting(int reputation) {
		int best = 0;
		for (int threshold : GREETING_BY_REPUTATION.keySet()) {
			if (Math.abs(reputation - threshold) < Math.abs(reputation - best)) {
				best = threshold;
			}
		}
		return GREETING_BY_REPUTATION.get(best);
	}

	/** Said to the player right away - unless the speaker is already mid trading line. */
	private static boolean say(LivingEntity merchant, Player player, String dialog) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null || dialog == null) {
			return false;
		}
		DialogEngine.Speech speech = engine.speech(merchant);
		if (speech != null && TRADE_DIALOGS.contains(speech.dialog().id())) {
			return false;
		}
		Options options = Options.DEFAULT.facing(player).forced().asUrgent();
		if (Speakers.kindOf(merchant) == Kind.MAYOR) {
			options = options.withKinds(Kind.MAYOR).withStates(State.BABY);
		}
		return engine.request(merchant, dialog, options);
	}

	/** Nitwits and the unemployed don't trade, and the add-on gives them no trading lines. */
	private static boolean employed(Villager villager) {
		String profession = Speakers.profession(villager);
		return !profession.equals("none") && !profession.equals("nitwit");
	}
}
