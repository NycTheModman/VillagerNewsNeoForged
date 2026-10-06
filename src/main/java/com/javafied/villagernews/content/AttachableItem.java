package com.javafied.villagernews.content;

import com.javafied.villagernews.platform.EnvType;
import com.javafied.villagernews.platform.LoaderEnvironment;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;

/**
 * A held item the add-on draws in the hand with its own 3D model and
 * animations (its "attachable"): the handbook and the microphone. Everywhere
 * else it shows its icon. Using the handbook opens the guide; the
 * microphone is held up to speak into for as long as it's used (in the
 * add-on, a food that never finishes: its attachable and the holder's arm
 * animate while it's "eaten"). Java's goat horn pose raises the arm just as
 * the add-on's does.
 *
 * <p>As with {@link WearableItem}, the client builds a subclass that supplies
 * GeckoLib's renderer, which shared code can't name.
 */
public class AttachableItem extends Item implements GeoItem {
	private static final String CLIENT_CLASS = "com.javafied.villagernews.client.item.ClientAttachableItem";

	/** Set by the client when it starts: opens the guide. */
	public static volatile Runnable guideOpener = () -> {
	};

	private final boolean guide;
	private AnimatableInstanceCache cache;

	public AttachableItem(Properties properties, boolean guide) {
		super(properties);
		this.guide = guide;
	}

	public static AttachableItem create(Properties properties, boolean guide) {
		if (LoaderEnvironment.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			try {
				return (AttachableItem) Class.forName(CLIENT_CLASS).getConstructor(Properties.class, boolean.class)
						.newInstance(properties, guide);
			} catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
					| InvocationTargetException e) {
				throw new IllegalStateException("Client item class missing", e);
			}
		}
		return new AttachableItem(properties, guide);
	}

	@Override
	public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (!guide) {
			player.startUsingItem(hand);
			return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
		}
		if (level.isClientSide()) {
			guideOpener.run();
		}
		return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return guide ? super.getUseAnimation(stack) : UseAnim.TOOT_HORN;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return guide ? super.getUseDuration(stack, user) : 72000;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		if (cache == null) {
			cache = GeckoLibUtil.createInstanceCache(this);
		}
		return cache;
	}
}
