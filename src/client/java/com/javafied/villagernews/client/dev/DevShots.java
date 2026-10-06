package com.javafied.villagernews.client.dev;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.guide.GuideBook;
import com.javafied.villagernews.client.guide.GuideScreen;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.ModItems;
import com.javafied.villagernews.names.AddonNames;

import com.javafied.villagernews.platform.ClientTickEvents;
import com.javafied.villagernews.platform.LoaderEnvironment;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Development aid: with a {@code villagernewsjavafied-devshots} file in the
 * game directory, the client (once in a world) holds the add-on's items in
 * each view and walks the handbook, taking a screenshot of each
 * ({@code screenshots/devshot-<n>-<step>.png}), then quits. The file is
 * removed first, so it runs once per request. If it names steps (say
 * {@code mannequin, guide}), only the steps starting with those run. Does
 * nothing otherwise.
 *
 * <p>It changes the world it runs in (items, mobs, time), so it only runs
 * in its own game directory, {@code run-devshots}, on a throwaway world -
 * never in {@code run}, where the real test worlds are.
 */
public final class DevShots {
	private static final String FLAG = VillagerNewsJavafied.MOD_ID + "-devshots";
	private static final String GAME_DIR = "run-devshots";
	private static final int SETTLE_TICKS = 40;

	private record Step(String name, Runnable action, int settleTicks) {
		Step(String name, Runnable action) {
			this(name, action, SETTLE_TICKS);
		}
	}

	private static final List<Step> steps = new ArrayList<>();
	private static int step = -1;
	private static int wait;
	private static GuideScreen guide;
	private static net.minecraft.world.entity.LivingEntity mannequin;
	private static Boolean pauseOnLostFocus;

	private DevShots() {
	}

	public static void init() {
		Path gameDir = LoaderEnvironment.getInstance().getGameDir();
		Path flag = gameDir.resolve(FLAG);
		if (!Files.exists(flag)) {
			return;
		}
		// It gives, takes and spawns things in whatever world it's in: never anywhere but its own game directory.
		if (!gameDir.toAbsolutePath().normalize().endsWith(GAME_DIR)) {
			VillagerNewsJavafied.LOGGER.warn("DevShots only runs in its own game directory ({}), not {}", GAME_DIR, gameDir);
			return;
		}
		String only;
		try {
			only = Files.readString(flag).trim();
			Files.delete(flag);
		} catch (Exception e) {
			return;
		}
		steps.add(new Step("scene", DevShots::scene));
        steps.add(new Step("lighting-villager-level", () -> lightingScene("villager", 0, 6000)));
        steps.add(new Step("lighting-villager-down", () -> lightingPose(40, 6000)));
        steps.add(new Step("lighting-villager-steep", () -> lightingPose(65, 6000)));
        steps.add(new Step("lighting-mayor-level", () -> lightingScene("mayor", 0, 6000)));
        steps.add(new Step("lighting-mayor-down", () -> lightingPose(40, 6000)));
        steps.add(new Step("lighting-mayor-talking", () -> {
            onServer(player -> player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                player.getBoundingBox().inflate(8)).forEach(v -> com.javafied.villagernews.dialog.DialogEngine.get()
                    .speakNow(v,"start_work",com.javafied.villagernews.dialog.DialogEngine.Options.DEFAULT)));
        }, 10));
        steps.add(new Step("lighting-mayor-night", () -> lightingPose(40, 18000)));
        steps.add(new Step("wooly", () -> {
            var mc=Minecraft.getInstance();hideHud(mc,true);mc.player.setXRot(20);
            onServer(player -> { clear(player);var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(player.serverLevel());sheep.setPos(player.getX(),player.getY(),player.getZ()+3);sheep.setData(ModAttachments.VILLAGER_VARIANT,"wooly");sheep.setNoAi(true);sheep.setYRot(180);sheep.setYBodyRot(180);sheep.setYHeadRot(180);player.serverLevel().addFreshEntity(sheep); });
        }));
        steps.add(new Step("villager-close", () -> {
            var mc=Minecraft.getInstance();hideHud(mc,true);mc.player.setXRot(4);
            onServer(player -> {clear(player);spawnVillager(player.serverLevel(),player.getX(),player.getY(),player.getZ()+2.5,"villager");});
        }));
        steps.add(new Step("villager-talking", () -> onServer(player -> {
            var engine=com.javafied.villagernews.dialog.DialogEngine.get();
            player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,player.getBoundingBox().inflate(8)).forEach(v -> { engine.stop(v);boolean started=engine.speakNow(v,"start_work",com.javafied.villagernews.dialog.DialogEngine.Options.DEFAULT);VillagerNewsJavafied.LOGGER.info("PORT TALK TEST started={}",started);});
        }), 10));
        steps.add(new Step("villager-talking-next", () -> {}, 7));
		steps.add(new Step("mannequin-front", () -> mannequin(180)));
		steps.add(new Step("mannequin-side", () -> mannequin(90)));
		steps.add(new Step("mannequin-back", () -> mannequin(0)));
		steps.add(new Step("mannequin-talk-side", () -> {
			mannequin(90);
			talk(true);
		}));
		steps.add(new Step("mannequin-talk-front", () -> {
			mannequin(180);
			talk(true);
		}));
		steps.add(new Step("sleep", DevShots::sleep));
		steps.add(new Step("sleep-wake", () -> onServer(player -> player.serverLevel().getEntitiesOfClass(
				net.minecraft.world.entity.npc.Villager.class, player.getBoundingBox().inflate(8)).forEach(
				net.minecraft.world.entity.LivingEntity::stopSleeping))));
		steps.add(new Step("trader", DevShots::trader));
		steps.add(new Step("trader-hit", () -> onServer(player -> {
			var trader = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.WanderingTrader.class,
					player.getBoundingBox().inflate(8)).stream().findFirst().orElse(null);
			if (trader != null) {
				trader.hurt( player.damageSources().playerAttack(player), 0.5f);
			}
		}), 15));
		steps.add(new Step("trader-idle", () -> {
		}, 45 * 20));
		hold("mic-third-front", ModItems.MICROPHONE.get(), ModItems.HANDBOOK.get(), CameraType.THIRD_PERSON_FRONT);
		hold("mic-third-back", ModItems.MICROPHONE.get(), ModItems.HANDBOOK.get(), CameraType.THIRD_PERSON_BACK);
		hold("mic-first", ModItems.MICROPHONE.get(), ModItems.HANDBOOK.get(), CameraType.FIRST_PERSON);
		hold("book-third-front", ModItems.HANDBOOK.get(), ModItems.MICROPHONE.get(), CameraType.THIRD_PERSON_FRONT);
		hold("book-first", ModItems.HANDBOOK.get(), ModItems.MICROPHONE.get(), CameraType.FIRST_PERSON);
		hold("talk-first", ModItems.MICROPHONE.get(), ModItems.HANDBOOK.get(), CameraType.FIRST_PERSON);
		hold("talk-third-front", ModItems.MICROPHONE.get(), ModItems.HANDBOOK.get(), CameraType.THIRD_PERSON_FRONT);
		steps.add(new Step("guide-home", () -> GuideBook.load().ifPresent(book -> {
			guide = new GuideScreen(book);
			Minecraft.getInstance().setScreen(guide);
		})));
		page("guide-menu", "guide");
		page("guide-overview", "overview");
		page("guide-triggers", "triggers");
		steps.add(new Step("guide-search", () -> guide.search("shear")));
		page("guide-settings", "settings");
		if (!only.isEmpty()) {
			List<String> wanted = List.of(only.split("[,\\s]+"));
			steps.removeIf(s -> !s.name().equals("scene") && wanted.stream().noneMatch(s.name()::startsWith));
		}
		steps.add(new Step("done", () -> {
			Minecraft.getInstance().options.pauseOnLostFocus = pauseOnLostFocus;
			Minecraft.getInstance().stop();
		}));
		ClientTickEvents.END_CLIENT_TICK.register(DevShots::tick);
	}

	/**
	 * Faces the player south, wearing the Mayor Hat, with a Mayor, a
	 * red-dyed Wooly and a plain villager standing still a few blocks ahead.
	 */
	private static void scene() {
		Minecraft minecraft = Minecraft.getInstance();
		var server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.player == null) {
			return;
		}
		minecraft.player.setYRot(0);
		minecraft.player.setXRot(10);
		var uuid = minecraft.player.getUUID();
		server.execute(() -> {
			var player = server.getPlayerList().getPlayer(uuid);
			if (player == null) {
				return;
			}
			var level = player.serverLevel();
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);level.setDayTime(6000);
			// What earlier runs left behind (the world is kept): everything spawned here is AI-less.
			clear(player);
			player.setYRot(0);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.MAYOR_HAT.get()));
			spawnVillager(level, player.getX() - 1.5, player.getY(), player.getZ() + 3.5, "mayor");
			spawnVillager(level, player.getX() + 1.5, player.getY(), player.getZ() + 3.5, "villager");
			var wooly = net.minecraft.world.entity.EntityType.SHEEP.create(level);
			if (wooly != null) {
				wooly.setPos(player.getX(), player.getY(), player.getZ() + 5);
				wooly.setData(ModAttachments.VILLAGER_VARIANT, "wooly");
				wooly.setColor(net.minecraft.world.item.DyeColor.RED);
				wooly.setNoAi(true);
				wooly.setYRot(180);
				level.addFreshEntity(wooly);
			}
		});
	}

    /** Fixed pose/UUID and reference blocks for actual in-game lighting regression screenshots. */
    private static void lightingScene(String character, float pitch, long time) {
        var mc = Minecraft.getInstance();
        hideHud(mc, true);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.player.setYRot(0);
        mc.player.setXRot(character.equals("mayor") ? 17 : 0);
        onServer(player -> {
            clear(player);
            var level = player.serverLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            villager.setUUID(java.util.UUID.fromString("00000000-0000-0000-0000-000000000004"));
            villager.setPos(player.getX(), player.getY(), player.getZ()+2.5);
            villager.setData(ModAttachments.VILLAGER_VARIANT, character);
            villager.setNoAi(true);
            villager.setYRot(180);villager.setYHeadRot(180);villager.setYBodyRot(180);
            villager.setXRot(pitch);
            level.addFreshEntity(villager);
            level.setDayTime(time);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.setBlockAndUpdate(net.minecraft.core.BlockPos.containing(player.getX()-1.5, player.getY(), player.getZ()+2.5),
                net.minecraft.world.level.block.Blocks.SMOOTH_SANDSTONE.defaultBlockState());
            level.setBlockAndUpdate(net.minecraft.core.BlockPos.containing(player.getX()+1.5, player.getY(), player.getZ()+2.5),
                net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
        });
    }

    private static void lightingPose(float pitch, long time) {
        LightingRegressionChecks.run();
        onServer(player -> {
            player.serverLevel().setDayTime(time);
            player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                player.getBoundingBox().inflate(8)).forEach(v -> v.setXRot(pitch));
        });
    }

	/**
	 * A close-up of the worn and held items: a mannequin (the player's
	 * model) just ahead, turned to the given yaw, wearing the Mayor Hat with
	 * the microphone in its right hand and the handbook in its left.
	 */
	private static void mannequin(float yaw) {
		Minecraft minecraft = Minecraft.getInstance();
		var server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.player == null) {
			return;
		}
		hideHud(minecraft, true);
		minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		minecraft.player.setYRot(0);
		minecraft.player.setXRot(12);
		var uuid = minecraft.player.getUUID();
		server.execute(() -> {
			var player = server.getPlayerList().getPlayer(uuid);
			if (player == null) {
				return;
			}
			var level = player.serverLevel();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			if (mannequin != null) {
				mannequin.discard();
			}
			mannequin = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
			if (mannequin == null) {
				return;
			}
			mannequin.setPos(player.getX(), player.getY(), player.getZ() + 2.2);
			mannequin.setNoGravity(true);
			mannequin.setYRot(yaw);
			mannequin.setYHeadRot(yaw);
			mannequin.setYBodyRot(yaw);
			mannequin.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.MAYOR_HAT.get()));
			mannequin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MICROPHONE.get()));
			mannequin.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.HANDBOOK.get()));
			level.addFreshEntity(mannequin);
		});
	}

	/** Speaks into the microphone (holds "use") - the mannequin, once it's spawned, or the player. */
	private static void talk(boolean byMannequin) {
		Minecraft minecraft = Minecraft.getInstance();
		var server = minecraft.getSingleplayerServer();
		if (byMannequin && server != null) {
			server.execute(() -> {
				if (mannequin != null) {
					mannequin.startUsingItem(InteractionHand.MAIN_HAND);
				}
			});
		} else {
			minecraft.options.keyUse.setDown(true);
		}
	}

	/**
	 * A wandering trader a few blocks ahead, looking at the player, with the
	 * dialog debug overlay on: the next steps hit it, then wait out its idle
	 * chatter timer.
	 */
	private static void trader() {
		Minecraft minecraft = Minecraft.getInstance();
		hideHud(minecraft, false);
		minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		if (minecraft.player != null) {
			minecraft.player.setYRot(0);
			minecraft.player.setXRot(10);
		}
		onServer(player -> {
			clear(player);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			var level = player.serverLevel();
			var trader = net.minecraft.world.entity.EntityType.WANDERING_TRADER.create(level);
			if (trader == null) {
				return;
			}
			trader.setPos(player.getX(), player.getY(), player.getZ() + 3);
			trader.setYRot(180);
			trader.setYHeadRot(180);
			trader.setYBodyRot(180);
			trader.setDespawnDelay(20 * 60 * 10);
			level.addFreshEntity(trader);
			server(player).getCommands().performPrefixedCommand(player.createCommandSourceStack(), "villagernews debug");
		});
	}

	/**
	 * A villager and the Mayor asleep in beds a few blocks ahead, lying
	 * across the view (heads to the left); the next step wakes them.
	 */
	private static void sleep() {
		Minecraft minecraft = Minecraft.getInstance();
		hideHud(minecraft, true);
		minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		if (minecraft.player != null) {
			minecraft.player.setYRot(0);
			minecraft.player.setXRot(35);
		}
		onServer(player -> {
			clear(player);
			var level = player.serverLevel();
			net.minecraft.core.BlockPos base = player.blockPosition();
			bedWithSleeper(level, base.offset(0, 0, 3), "villager");
			bedWithSleeper(level, base.offset(0, 0, 5), "mayor");
		});
	}

	private static void bedWithSleeper(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos foot, String character) {
		var facing = net.minecraft.core.Direction.EAST;
		var head = foot.relative(facing);
		var bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
				.setValue(net.minecraft.world.level.block.BedBlock.FACING, facing);
		level.setBlockAndUpdate(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
				net.minecraft.world.level.block.state.properties.BedPart.FOOT));
		level.setBlockAndUpdate(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
				net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		if (villager == null) {
			return;
		}
		villager.setPos(head.getX() + 0.5, head.getY() + 0.6875, head.getZ() + 0.5);
		villager.setData(ModAttachments.VILLAGER_VARIANT, character);
		villager.setNoAi(true);
		level.addFreshEntity(villager);
		villager.startSleeping(head);
	}

	private static void onServer(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
		Minecraft minecraft = Minecraft.getInstance();
		var server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.player == null) {
			return;
		}
		var uuid = minecraft.player.getUUID();
		server.execute(() -> {
			var player = server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				action.accept(player);
			}
		});
	}

	private static net.minecraft.server.MinecraftServer server(net.minecraft.server.level.ServerPlayer player) {
		return player.serverLevel().getServer();
	}

	private static void hideHud(Minecraft minecraft, boolean hidden) {
		minecraft.options.hideGui = hidden;
	}

	/** Removes what earlier steps and runs left (the world is kept): everything spawned here is AI-less. */
	private static void clear(net.minecraft.server.level.ServerPlayer player) {
		player.serverLevel().getEntities((net.minecraft.world.entity.Entity) null, player.getBoundingBox().inflate(24),
				e -> e instanceof net.minecraft.world.entity.decoration.ArmorStand || e instanceof net.minecraft.world.entity.Mob)
				.forEach(net.minecraft.world.entity.Entity::discard);
		mannequin = null;
	}

	private static void spawnVillager(net.minecraft.server.level.ServerLevel level, double x, double y, double z, String character) {
		var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		if (villager == null) {
			return;
		}
		villager.setPos(x, y, z);
		villager.setData(ModAttachments.VILLAGER_VARIANT, character);
		villager.setNoAi(true);
		villager.setYRot(180);
		villager.setYHeadRot(180);
		villager.setYBodyRot(180);
		level.addFreshEntity(villager);
	}

	private static void hold(String name, Item main, Item off, CameraType camera) {
		steps.add(new Step(name, () -> {
			Minecraft minecraft = Minecraft.getInstance();
			var server = minecraft.getSingleplayerServer();
			if (server != null && minecraft.player != null) {
				var uuid = minecraft.player.getUUID();
				server.execute(() -> {
					if (mannequin != null) {
						mannequin.discard();
						mannequin = null;
					}
					var player = server.getPlayerList().getPlayer(uuid);
					if (player != null) {
                        clear(player);
						player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(main));
						player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(off));
					}
				});
				minecraft.player.setXRot(10);
				minecraft.player.setYRot(0);
			}
			hideHud(minecraft, false);
			minecraft.options.setCameraType(camera);
			minecraft.options.keyUse.setDown(false);
			if (name.startsWith("talk")) {
				talk(false);
			}
		}));
	}

	private static void page(String name, String page) {
		steps.add(new Step(name, () -> {
			if (guide != null) {
				String id = AddonNames.current().id(AddonNames.Kind.GUIDE_PAGE, page);
				guide.openPage(id != null ? id : page);
			}
		}));
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		// Unattended: losing the window's focus mustn't pause the game.
		if (pauseOnLostFocus == null) {
			pauseOnLostFocus = minecraft.options.pauseOnLostFocus;
		}
		minecraft.options.pauseOnLostFocus = false;
		if (minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			minecraft.setScreen(null);
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (step >= 0 && step < steps.size() && !steps.get(step).name().equals("done")) {
			String file = (steps.get(step).name().startsWith("lighting-") ? "lightingfix-" : "devshot-") + step + "-" + steps.get(step).name() + ".png";
			Screenshot.grab(minecraft.gameDirectory, file, minecraft.getMainRenderTarget(), message -> {
			});
		}
		step++;
		if (step >= steps.size()) {
			return;
		}
		steps.get(step).action().run();
		wait = step == 0 ? SETTLE_TICKS * 3 : steps.get(step).settleTicks();
	}
}
