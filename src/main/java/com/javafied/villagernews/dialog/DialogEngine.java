package com.javafied.villagernews.dialog;

import com.javafied.villagernews.ConvertedPack;
import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.dialog.DialogLibrary.Dialog;
import com.javafied.villagernews.dialog.DialogLibrary.Line;
import com.javafied.villagernews.dialog.DialogLibrary.TagCooldown;
import com.javafied.villagernews.guide.GuideSettings;
import com.javafied.villagernews.names.AddonNames;

import com.javafied.villagernews.platform.ServerLifecycleEvents;
import com.javafied.villagernews.platform.ServerTickEvents;
import com.javafied.villagernews.platform.PlayerLookup;
import com.javafied.villagernews.platform.ServerPlayNetworking;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Hand port of the add-on script's dialog system: who may say what, when.
 *
 * <p>A trigger {@link #request requests} a dialog for a speaker. The request
 * waits (up to 2 seconds) in a queue drained every 3 ticks, which keeps at
 * most {@value #MAX_SPEAKERS} villagers talking at once and starts at most
 * one line every {@value #START_GAP_TICKS} ticks. When it starts, one of the
 * dialog's lines is picked (by weight, never the one it said last time), sent
 * to nearby clients - which play the voice, lip sync and subtitles - and the
 * dialog's cooldowns kick in: per dialog, per speaker and per tag, globally
 * and for that speaker. While talking, the speaker stands still and looks at
 * whoever it's talking to.
 *
 * <p>Numbers are the script's, at its default "Chattiness" setting; the
 * handbook's settings ({@link GuideSettings}) change them as the add-on's do.
 */
public final class DialogEngine {
	/** Which speakers a request accepts; the script's {@code states} list. */
	public enum State {
		/** Grown-up ordinary villagers. */
		ADULT,
		/**
		 * Baby villagers - and the Mayor, whose lines the add-on keeps in the
		 * same "second voice" slot.
		 */
		BABY,
		/** Allowed while asleep (otherwise sleepers stay quiet). */
		SLEEPING,
		/** Allowed right after being hurt or with a monster close by (otherwise it waits). */
		EVEN_IN_DANGER
	}

	/**
	 * @param kinds                which speakers may say it (see {@link Speakers})
	 * @param interrupt            cut off whatever the speaker is saying
	 * @param ignoreEntityCooldown ...and the speaker's own cooldowns
	 * @param ignoreGlobalCooldown ...and the world-wide ones
	 * @param ignoreTagCooldown    ...and the tag ones
	 * @param facing               who the speaker turns to while talking
	 * @param facingPos            or where it looks, if not at an entity
	 * @param urgent               skip the "3 speakers / 10 ticks apart" pacing
	 * @param timeout              ticks the request may wait for its turn
	 * @param ready                while queued, only starts once this holds (null: any time)
	 */
	public record Options(Set<State> states, Set<Speakers.Kind> kinds, boolean interrupt, boolean ignoreEntityCooldown,
			boolean ignoreGlobalCooldown, boolean ignoreTagCooldown, Entity facing, Vec3 facingPos, boolean urgent, int timeout,
			java.util.function.Predicate<LivingEntity> ready) {
		public static final Options DEFAULT = new Options(EnumSet.of(State.ADULT), Speakers.DEFAULT_KINDS, false, false, false,
				false, null, null, false, 40, null);

		public Options readyWhen(java.util.function.Predicate<LivingEntity> condition) {
			return new Options(states, kinds, interrupt, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, facing,
					facingPos, urgent, timeout, condition);
		}

		public Options withStates(State first, State... rest) {
			return new Options(EnumSet.of(first, rest), kinds, interrupt, ignoreEntityCooldown, ignoreGlobalCooldown,
					ignoreTagCooldown, facing, facingPos, urgent, timeout, ready);
		}

		/** Also allowed while asleep / also allowed when in danger. */
		public Options alsoWhen(State extra) {
			EnumSet<State> more = EnumSet.copyOf(states);
			more.add(extra);
			return new Options(more, kinds, interrupt, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, facing,
					facingPos, urgent, timeout, ready);
		}

		public Options withKinds(Speakers.Kind first, Speakers.Kind... rest) {
			return withKinds(EnumSet.of(first, rest));
		}

		public Options withKinds(Set<Speakers.Kind> allowed) {
			return new Options(states, Set.copyOf(allowed), interrupt, ignoreEntityCooldown, ignoreGlobalCooldown,
					ignoreTagCooldown, facing, facingPos, urgent, timeout, ready);
		}

		public Options facing(Entity target) {
			return new Options(states, kinds, interrupt, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, target,
					null, urgent, timeout, ready);
		}

		public Options facing(Vec3 position) {
			return new Options(states, kinds, interrupt, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, null,
					position, urgent, timeout, ready);
		}

		public Options ignoringCooldowns(boolean entity, boolean global, boolean tags) {
			return new Options(states, kinds, interrupt, entity, global, tags, facing, facingPos, urgent, timeout, ready);
		}

		public Options interrupting() {
			return new Options(states, kinds, true, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, facing,
					facingPos, urgent, timeout, ready);
		}

		/** The script's "all four flags": ignore every cooldown and cut off whatever is being said. */
		public Options forced() {
			return ignoringCooldowns(true, true, true).interrupting();
		}

		public Options asUrgent() {
			return new Options(states, kinds, interrupt, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, facing,
					facingPos, true, timeout, ready);
		}

		public Options waitingAtMost(int ticks) {
			return new Options(states, kinds, interrupt, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, facing,
					facingPos, urgent, ticks, ready);
		}
	}

	/** A line being spoken; the speaker counts as busy until {@link #releaseTick}. */
	public record Speech(LivingEntity speaker, Dialog dialog, int line, long endTick, long releaseTick, Entity facing,
			Vec3 facingPos, Vec3 frozenAt) {
		public boolean talking(long now) {
			return now < endTick;
		}
	}

	private record Request(LivingEntity speaker, Dialog dialog, Options options, long expires) {
	}

	/** Why a speaker's last request didn't turn into a line - for the debug view. */
	private record Refusal(String dialog, String reason, long tick) {
	}

	/** Blocked-until ticks. */
	private static final class Cooldowns {
		long any;
		final Map<String, Long> dialogs = new HashMap<>();
		final Map<String, Long> tags = new HashMap<>();
	}

	public static final double RANGE = 16;
	private static final int MAX_SPEAKERS = 3;
	private static final int START_GAP_TICKS = 10;
	private static final int QUEUE_INTERVAL = 3;
	/** After its line ends, a speaker stays busy this long (the script clears subtitles then). */
	private static final int LINGER_TICKS = 10;
	private static final int HURT_SILENCE_TICKS = 40;
	/** How long after being hit a speaker counts as fleeing (panic starts on its brain's next tick). */
	private static final int RECENTLY_HURT_TICKS = 60;
	private static final double MONSTER_RADIUS = 8;
	/** A player this close to someone already talking won't hear a second villager start. */
	private static final double LISTENER_RADIUS = 10;
	private static final double OVERLAP_RADIUS = 5;

	private static DialogEngine current;
	/** Registered once at startup; every server's engine calls them. */
	private static final List<BiConsumer<Speech, Boolean>> LISTENERS = new ArrayList<>();

	private final MinecraftServer server;
	private final DialogLibrary library;
	private final Cooldowns global = new Cooldowns();
	private final Map<Entity, Cooldowns> speakerCooldowns = new WeakHashMap<>();
	private final Map<Entity, Long> lastHurt = new WeakHashMap<>();
	private final Map<Entity, Boolean> talksWhileFleeing = new WeakHashMap<>();
	private final Map<Entity, Speech> speeches = new HashMap<>();
	private final Map<String, Integer> lastLine = new HashMap<>();
	private final Set<String> warnedMissing = new java.util.HashSet<>();
	private final Map<Entity, Refusal> lastRefusal = new WeakHashMap<>();
	/** When each speaker last said each dialog; "the nearest villager reacts" prefers who said it longest ago. */
	private final Map<Entity, Map<String, Long>> lastSaid = new WeakHashMap<>();
	private final List<Request> queue = new ArrayList<>();
	private long lastStart = -START_GAP_TICKS;

	private DialogEngine(MinecraftServer server, DialogLibrary library) {
		this.server = server;
		this.library = library;
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			DialogLibrary library;
			try {
				library = DialogLibrary.load(ConvertedPack.serverData("dialogs.json"));
			} catch (IOException | RuntimeException e) {
				VillagerNewsJavafied.LOGGER.error("Couldn't read the converted add-on's dialogs; villagers will stay quiet", e);
				library = DialogLibrary.EMPTY;
			}
			VillagerNewsJavafied.LOGGER.info("Loaded {} villager dialogs", library.size());
			checkNames(library);
			current = new DialogEngine(server, library);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = null);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (current != null && current.server == server) {
				current.tick();
			}
		});
	}

	/** By readable name; a name the converted add-on doesn't have is logged once (a names file out of date). */
	private Dialog dialog(String name) {
		Dialog dialog = library.get(name);
		if (dialog == null && !library.isEmpty() && warnedMissing.add(name)) {
			VillagerNewsJavafied.LOGGER.warn("No dialog named '{}' in the converted add-on (version {})", name, AddonNames.current().version());
		}
		return dialog;
	}

	/**
	 * Says so in the log if the converted add-on is a version the mod has no
	 * names file for, or if dialogs the names file lists aren't in it: then
	 * some reactions will stay quiet until the names file is updated.
	 */
	private static void checkNames(DialogLibrary library) {
		AddonNames names = AddonNames.current();
		if (library.isEmpty()) {
			return;
		}
		if (!names.known()) {
			VillagerNewsJavafied.LOGGER.warn("No names file for this add-on version; using {}'s. Dialogs named from the add-on's"
					+ " guide still work; reactions that need the others may stay quiet.", names.version());
		}
		List<String> missing = names.all(AddonNames.Kind.DIALOG).keySet().stream().filter(name -> library.get(name) == null)
				.sorted().toList();
		if (!missing.isEmpty()) {
			VillagerNewsJavafied.LOGGER.warn("{} dialogs from the names file aren't in the converted add-on: {}", missing.size(), missing);
		}
	}

	/** Called when a line ends: {@code true} if it played out, {@code false} if it was cut short. */
	public static void onFinished(BiConsumer<Speech, Boolean> listener) {
		LISTENERS.add(listener);
	}

	/** Null while no server is running. */
	public static DialogEngine get() {
		return current;
	}

	public DialogLibrary library() {
		return library;
	}

	public long now() {
		return server.getTickCount();
	}

	/**
	 * Queues {@code dialogId} for {@code speaker}; it starts once pacing allows,
	 * or is dropped after 2 seconds. False if it can't be said at all right now.
	 */
	public boolean request(LivingEntity speaker, String dialogId, Options options) {
		Dialog dialog = dialog(dialogId);
		String refusal = dialog == null ? "not in the add-on" : whyNot(speaker, options);
		if (refusal == null) {
			refusal = cooldownBlocking(speaker, dialog, options);
		}
		if (refusal != null) {
			return refuse(speaker, dialogId, refusal);
		}
		queue.add(new Request(speaker, dialog, options, now() + options.timeout()));
		return true;
	}

	private boolean refuse(Entity speaker, String dialogId, String reason) {
		lastRefusal.put(speaker, new Refusal(dialogId, reason, now()));
		return false;
	}

	/** Starts {@code dialogId} right away if it can, skipping the queue (replies in a conversation). */
	public boolean speakNow(LivingEntity speaker, String dialogId, Options options) {
		Dialog dialog = dialog(dialogId);
		String refusal = dialog == null ? "not in the add-on" : whyNot(speaker, options);
		return refusal == null ? start(speaker, dialog, options) : refuse(speaker, dialogId, refusal);
	}

	/** What the speaker is currently saying, if anything. */
	public Speech speech(Entity speaker) {
		Speech speech = speeches.get(speaker);
		return speech != null && speech.talking(now()) ? speech : null;
	}

	public boolean isTalking(Entity speaker) {
		return speech(speaker) != null;
	}

	/** Cuts the speaker off (the voice stops for everyone listening). */
	public void stop(Entity speaker) {
		Speech speech = speeches.remove(speaker);
		if (speech == null) {
			return;
		}
		for (ServerPlayer player : PlayerLookup.tracking(speaker)) {
			if (ServerPlayNetworking.canSend(player, DialogPayloads.Stop.TYPE)) {
				ServerPlayNetworking.send(player, new DialogPayloads.Stop(speaker.getId()));
			}
		}
		LISTENERS.forEach(listener -> listener.accept(speech, false));
	}

	/** A few lines about the speaker's dialog state, for the debug view. */
	public List<String> describe(LivingEntity speaker) {
		long now = now();
		List<String> lines = new ArrayList<>();
		Speech speech = speeches.get(speaker);
		if (speech != null && speech.talking(now)) {
			lines.add("Talking: " + speech.dialog().id() + " (line " + (speech.line() + 1) + " of "
					+ speech.dialog().lines().size() + ", " + seconds(speech.endTick() - now) + " left)");
		} else {
			lines.add("Quiet (" + speakers() + " of " + MAX_SPEAKERS + " villagers talking nearby)");
		}
		Cooldowns own = speakerCooldowns.get(speaker);
		List<String> cooldowns = new ArrayList<>();
		if (own != null) {
			if (own.any > now) {
				cooldowns.add("any " + seconds(own.any - now));
			}
			own.dialogs.forEach((id, until) -> {
				if (until > now) {
					cooldowns.add(id + " " + seconds(until - now));
				}
			});
			own.tags.forEach((tag, until) -> {
				if (until > now) {
					cooldowns.add("tag " + AddonNames.nameOf(AddonNames.Kind.DIALOG_TAG, tag) + " " + seconds(until - now));
				}
			});
		}
		lines.add(cooldowns.isEmpty() ? "No cooldowns" : "Cooldowns: " + String.join(", ", cooldowns));
		List<String> waiting = new ArrayList<>();
		for (Request request : queue) {
			if (request.speaker() == speaker) {
				waiting.add(request.dialog().id() + " " + seconds(request.expires() - now));
			}
		}
		if (!waiting.isEmpty()) {
			lines.add("Waiting: " + String.join(", ", waiting));
		}
		Refusal refusal = lastRefusal.get(speaker);
		if (refusal != null) {
			lines.add("Last refused: " + refusal.dialog() + " - " + refusal.reason() + " (" + seconds(now - refusal.tick()) + " ago)");
		}
		return lines;
	}

	private static String seconds(long ticks) {
		return String.format(java.util.Locale.ROOT, "%.1fs", ticks / 20.0);
	}

	/** Free to strike up something new: not talking, and no "any dialog" cooldown running. */
	public boolean idle(LivingEntity speaker) {
		long now = now();
		Cooldowns own = speakerCooldowns.get(speaker);
		return !isTalking(speaker) && global.any <= now && (own == null || own.any <= now);
	}

	/** When this speaker last said this dialog (0 if never). */
	public long lastSaid(Entity speaker, String dialogId) {
		Map<String, Long> said = lastSaid.get(speaker);
		return said == null ? 0 : said.getOrDefault(dialogId, 0L);
	}

	/**
	 * The script's {@code westjl}: could this speaker take this dialog now?
	 * Used to pick among nearby candidates before queueing.
	 */
	public boolean available(LivingEntity speaker, String dialogId, Options options) {
		Dialog dialog = library.get(dialogId);
		return dialog != null && speaker.isAlive() && !isTalking(speaker)
				&& ((ServerLevel) speaker.level()).getNearestPlayer(speaker, RANGE) != null
				&& cooldownBlocking(speaker, dialog, options) == null;
	}

	/**
	 * Villagers keep quiet for 2 seconds after being hurt. And, as the script
	 * has it, one last hurt by a player, a projectile or the world (not a mob)
	 * may still talk while it runs away.
	 */
	public void markHurt(Entity speaker, DamageSource source) {
		lastHurt.put(speaker, now());
		Entity attacker = source.getEntity();
		talksWhileFleeing.put(speaker, attacker == null || source.getDirectEntity() != attacker || attacker instanceof Player);
	}

	/** The script's per-speaker checks ({@code ihylcx}): age, sleep, danger. Null if it may talk, else why not. */
	private String whyNot(LivingEntity speaker, Options options) {
		if (!speaker.isAlive()) {
			return "dead";
		}
		if (GuideSettings.current().chattiness() == GuideSettings.MUTED) {
			return "villagers are muted in the handbook's settings";
		}
		Speakers.Kind kind = Speakers.kindOf(speaker);
		if (kind == null || !options.kinds().contains(kind)) {
			return "not a line for " + (kind == null ? "this mob" : kind.name().toLowerCase(java.util.Locale.ROOT));
		}
		// The script's age check applies to ordinary villagers; the Mayor always counts as the "second voice".
		State age = kind == Speakers.Kind.MAYOR || kind == Speakers.Kind.VILLAGER && Speakers.isBaby(speaker) ? State.BABY
				: kind == Speakers.Kind.VILLAGER ? State.ADULT : null;
		if (age != null && !options.states().contains(age)) {
			return age == State.BABY ? "a line for grown-ups" : "a line for babies";
		}
		if (speaker.isSleeping() && !options.states().contains(State.SLEEPING)) {
			return "asleep";
		}
		if (options.states().contains(State.EVEN_IN_DANGER)) {
			return null;
		}
		// The script turns away every other line while a villager is running from something (its panicking property).
		if (panicking(speaker) && !Boolean.TRUE.equals(talksWhileFleeing.get(speaker))) {
			return "running away";
		}
		Long hurt = lastHurt.get(speaker);
		if (hurt != null && now() <= hurt + HURT_SILENCE_TICKS) {
			return "just got hurt";
		}
		boolean monster = !speaker.level().getEntitiesOfClass(Mob.class, speaker.getBoundingBox().inflate(MONSTER_RADIUS),
				mob -> mob instanceof Enemy && mob.distanceTo(speaker) <= MONSTER_RADIUS).isEmpty();
		return monster ? "monster nearby" : null;
	}

	/** Null if no cooldown blocks this dialog for this speaker, else which one does. */
	private String cooldownBlocking(LivingEntity speaker, Dialog dialog, Options options) {
		long now = now();
		Cooldowns own = speakerCooldowns.get(speaker);
		if (!options.ignoreEntityCooldown() && own != null) {
			if (own.any > now) {
				return "speaker cooldown";
			}
			if (own.dialogs.getOrDefault(dialog.id(), 0L) > now) {
				return "speaker cooldown for this dialog";
			}
		}
		if (!options.ignoreGlobalCooldown()) {
			if (global.any > now) {
				return "world cooldown";
			}
			if (global.dialogs.getOrDefault(dialog.id(), 0L) > now) {
				return "world cooldown for this dialog";
			}
		}
		if (!options.ignoreTagCooldown()) {
			for (String tag : dialog.tags().keySet()) {
				if (global.tags.getOrDefault(tag, 0L) > now || own != null && own.tags.getOrDefault(tag, 0L) > now) {
					return "cooldown on tag " + AddonNames.nameOf(AddonNames.Kind.DIALOG_TAG, tag);
				}
			}
		}
		return null;
	}

	private void tick() {
		long now = now();
		List<Speech> finished = new ArrayList<>();
		for (Iterator<Speech> it = speeches.values().iterator(); it.hasNext(); ) {
			Speech speech = it.next();
			LivingEntity speaker = speech.speaker();
			if (!speaker.isAlive() || speaker.isRemoved()) {
				it.remove();
				continue;
			}
			if (now >= speech.releaseTick()) {
				it.remove();
				finished.add(speech);
				continue;
			}
			holdStill(speech);
		}
		// After the loop: a listener may start the next line (a conversation's answer) straight away.
		for (Speech speech : finished) {
			LISTENERS.forEach(listener -> listener.accept(speech, true));
		}
		if (now % QUEUE_INTERVAL == 0) {
			drainQueue(now);
		}
	}

	/**
	 * While talking: face the listener, and don't wander off mid-sentence -
	 * unless running from something (a villager you hit says "ouch" as it
	 * flees, it doesn't stop to face you).
	 */
	private static void holdStill(Speech speech) {
		LivingEntity speaker = speech.speaker();
		if (fleeing(speaker)) {
			return;
		}
		Entity facing = speech.facing();
		if (facing != null && facing.isAlive() && facing.level() == speaker.level()) {
			speaker.lookAt(EntityAnchorArgument.Anchor.EYES, facing.getEyePosition());
			if (speaker instanceof Mob mob) {
				mob.getLookControl().setLookAt(facing, 30, 30);
			}
		} else if (speech.facingPos() != null) {
			speaker.lookAt(EntityAnchorArgument.Anchor.EYES, speech.facingPos());
			if (speaker instanceof Mob mob) {
				mob.getLookControl().setLookAt(speech.facingPos());
			}
		}
		if (speech.frozenAt() != null) {
			if (speaker instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			Vec3 motion = speaker.getDeltaMovement();
			speaker.setDeltaMovement(0, Math.min(motion.y, 0), 0);
		}
	}

	/** Over a copy: starting a line can cut another short, and its listeners may queue new requests. */
	private void drainQueue(long now) {
		for (Request request : List.copyOf(queue)) {
			LivingEntity speaker = request.speaker();
			if (now >= request.expires() || !speaker.isAlive()) {
				queue.remove(request);
				if (speaker.isAlive()) {
					refuse(speaker, request.dialog().id(), "gave up waiting for its turn");
				}
				continue;
			}
			boolean paced = request.options().urgent()
					|| now - lastStart >= START_GAP_TICKS && (superChatty() || speakers() < MAX_SPEAKERS);
			boolean ready = request.options().ready() == null || request.options().ready().test(speaker);
			if (paced && ready && steady(speaker) && start(speaker, request.dialog(), request.options())) {
				queue.remove(request);
			}
		}
	}

	/** Panicking, keeping away from something, or hit a moment ago (it's about to panic). */
	private static boolean fleeing(LivingEntity speaker) {
		return panicking(speaker)
				|| speaker.getLastHurtByMob() != null && speaker.tickCount - speaker.getLastHurtByMobTimestamp() < RECENTLY_HURT_TICKS;
	}

	/** Running from something: panicking, or keeping away (Bedrock's is_panicking / is_avoiding_mobs). */
	public static boolean panicking(LivingEntity speaker) {
		return Boolean.TRUE.equals(speaker.getExistingDataOrNull(ModAttachments.AVOIDING))
				|| speaker instanceof Villager villager && villager.getBrain().isActive(Activity.PANIC);
	}

	/** On the ground (or riding), head above water. */
	private static boolean steady(LivingEntity speaker) {
		return (speaker.onGround() || speaker.isPassenger()) && !speaker.isEyeInFluid(FluidTags.WATER);
	}

	private int speakers() {
		return speeches.size();
	}

	/** The script's {@code izbnnu}: final checks, pick a line, broadcast it, start the cooldowns. */
	private boolean start(LivingEntity speaker, Dialog dialog, Options options) {
		if (!speaker.isAlive() || dialog.lines().isEmpty()) {
			return false;
		}
		long now = now();
		if (isTalking(speaker)) {
			if (!options.interrupt()) {
				return refuse(speaker, dialog.id(), "already talking");
			}
			stop(speaker);
		}
		ServerLevel level = (ServerLevel) speaker.level();
		Player nearest = level.getNearestPlayer(speaker, RANGE);
		if (nearest == null) {
			return refuse(speaker, dialog.id(), "no player within " + (int) RANGE + " blocks");
		}
		String cooldown = cooldownBlocking(speaker, dialog, options);
		if (cooldown != null) {
			return refuse(speaker, dialog.id(), cooldown);
		}
		if (!options.interrupt() && !superChatty()) {
			if (speakers() >= MAX_SPEAKERS) {
				return refuse(speaker, dialog.id(), MAX_SPEAKERS + " villagers already talking");
			}
			if (nearest.distanceTo(speaker) < LISTENER_RADIUS && someoneElseTalkingNear(nearest, speaker)) {
				return refuse(speaker, dialog.id(), "someone else is talking to that player");
			}
		}
		if (global.any > now) {
			return refuse(speaker, dialog.id(), "world cooldown");
		}

		int index = pickLine(dialog);
		Line line = dialog.lines().get(index);
		lastLine.put(dialog.id(), index);
		lastStart = now;
		speeches.put(speaker, new Speech(speaker, dialog, index, now + line.durationTicks(),
				now + line.durationTicks() + LINGER_TICKS, options.facing(), options.facingPos(),
				speaker.onGround() && !fleeing(speaker) ? speaker.position() : null));
		lastSaid.computeIfAbsent(speaker, e -> new HashMap<>()).put(dialog.id(), now);
		startCooldowns(speaker, dialog, line);
		broadcast(speaker, line);
		return true;
	}

	/**
	 * A line blurted out on the spot, with none of the engine's bookkeeping -
	 * no queue, cooldowns or standing still: the hurt voices, and a dying
	 * villager's last gasp (so a speaker at 0 health still may). False if the
	 * add-on has no such dialog.
	 */
	public boolean exclaim(LivingEntity speaker, String dialogId) {
		Dialog dialog = dialog(dialogId);
		if (dialog == null || dialog.lines().isEmpty() || speaker.isRemoved()) {
			return false;
		}
		int index = pickLine(dialog);
		lastLine.put(dialog.id(), index);
		broadcast(speaker, dialog.lines().get(index));
		return true;
	}

	private static void broadcast(LivingEntity speaker, Line line) {
		DialogPayloads.Line payload = new DialogPayloads.Line(speaker.getId(), line.sound(), line.animation(),
				line.durationTicks(), line.subtitles());
		for (ServerPlayer player : PlayerLookup.tracking(speaker)) {
			if (ServerPlayNetworking.canSend(player, DialogPayloads.Line.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private boolean someoneElseTalkingNear(Player listener, Entity speaker) {
		for (Speech speech : speeches.values()) {
			LivingEntity other = speech.speaker();
			if (other != speaker && other.level() == listener.level() && other.distanceTo(listener) < OVERLAP_RADIUS) {
				return true;
			}
		}
		return false;
	}

	/** By weight, never repeating the dialog's previous line when it has others. */
	private int pickLine(Dialog dialog) {
		List<Line> lines = dialog.lines();
		Integer previous = lastLine.get(dialog.id());
		int skip = previous == null || lines.size() <= 1 ? -1 : previous;
		double[] weights = weights(lines, GuideSettings.current().rareLines());
		double total = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (i != skip) {
				total += weights[i];
			}
		}
		if (total <= 0) {
			return skip == 0 && lines.size() > 1 ? 1 : 0;
		}
		double roll = ThreadLocalRandom.current().nextDouble() * total;
		for (int i = 0; i < lines.size(); i++) {
			if (i != skip && (roll -= weights[i]) <= 0) {
				return i;
			}
		}
		return skip == 0 && lines.size() > 1 ? 1 : 0;
	}

	/**
	 * The "Rare Villager Voicelines" setting: lines' weights as they are; or,
	 * for "Never", only those within 80% of the dialog's heaviest; or, for
	 * "Often", turned upside down (the rarest become the likeliest).
	 */
	static double[] weights(List<Line> lines, int rareLines) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (Line line : lines) {
			min = Math.min(min, line.weight());
			max = Math.max(max, line.weight());
		}
		double[] weights = new double[lines.size()];
		for (int i = 0; i < weights.length; i++) {
			double w = lines.get(i).weight();
			double adjusted = switch (rareLines) {
				case GuideSettings.RARE_NEVER -> w >= 0.8 * max ? w : 0;
				case GuideSettings.RARE_OFTEN -> max + min - w;
				default -> w;
			};
			weights[i] = Double.isFinite(adjusted) && adjusted > 0 ? adjusted : 0;
		}
		return weights;
	}

	private void startCooldowns(LivingEntity speaker, Dialog dialog, Line line) {
		long now = now();
		Cooldowns own = speakerCooldowns.computeIfAbsent(speaker, e -> new Cooldowns());
		double globalAny = Double.isNaN(dialog.globalCooldown().any()) ? GuideSettings.current().defaultGlobalAny()
				: dialog.globalCooldown().any();
		if (globalAny > 0) {
			global.any = now + cooldownTicks(globalAny, line);
		}
		if (dialog.globalCooldown().same() > 0) {
			global.dialogs.put(dialog.id(), now + cooldownTicks(dialog.globalCooldown().same(), line));
		}
		if (dialog.entityCooldown().any() > 0) {
			own.any = now + cooldownTicks(dialog.entityCooldown().any(), line);
		}
		if (dialog.entityCooldown().same() > 0) {
			own.dialogs.put(dialog.id(), now + cooldownTicks(dialog.entityCooldown().same(), line));
		}
		for (Map.Entry<String, TagCooldown> tag : dialog.tags().entrySet()) {
			if (tag.getValue().global() > 0) {
				global.tags.put(tag.getKey(), now + cooldownTicks(tag.getValue().global(), line));
			}
			if (tag.getValue().entity() > 0) {
				own.tags.put(tag.getKey(), now + cooldownTicks(tag.getValue().entity(), line));
			}
		}
	}

	/** A cooldown counts from the end of the line: its length in ticks (divided by the chattiness multiplier), plus the line's own. */
	private static long cooldownTicks(double seconds, Line line) {
		double chattiness = GuideSettings.current().multiplier();
		return (long) Math.floor(20 * seconds / (chattiness > 0 ? chattiness : 1)) + line.durationTicks();
	}

	/** At "Super Chatty" any number of villagers may talk at once, even over each other. */
	private static boolean superChatty() {
		return GuideSettings.current().chattiness() == GuideSettings.SUPER_CHATTY;
	}
}
