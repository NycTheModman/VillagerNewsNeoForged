package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.dialog.BedrockSoundIds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Client-side playback of the add-on's sounds, following the entity that makes them. */
public final class BedrockSounds {
	private BedrockSounds() {
	}

	/**
	 * @return the playing instance, so a line cut short can be stopped. A
	 *         sound the add-on names but doesn't have (its villagers' footstep,
	 *         {@code qbscfl}) plays nothing, as in Bedrock, without a warning.
	 */
	public static SoundInstance playFrom(Entity entity, String bedrockEvent) {
		SoundEvent event = BedrockSoundIds.event(bedrockEvent);
		// A dying speaker's body goes after a second; its last line stays where it fell rather than stopping with it.
		SoundInstance sound = entity instanceof LivingEntity living && living.isDeadOrDying()
				? new SimpleSoundInstance(event, entity.getSoundSource(), 1f, 1f, entity.getRandom(), entity.getX(), entity.getY(), entity.getZ())
				: new EntityBoundSoundInstance(event, entity.getSoundSource(), 1f, 1f, entity, entity.getRandom().nextLong());
		SoundManager sounds = Minecraft.getInstance().getSoundManager();
		if (sounds.getSoundEvent(event.getLocation()) != null) {
			sounds.play(sound);
		}
		return sound;
	}
}
