package com.javafied.villagernews.dialog;

import com.javafied.villagernews.VillagerNewsJavafied;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Maps the add-on's sound event names onto the ones the converter wrote into
 * {@code assets/villagernewsjavafied/sounds.json}: {@code oreville_vn:qosovr}
 * becomes {@code villagernewsjavafied:oreville_vn.qosovr}. The events aren't
 * registered (they come from the player's own converted pack), so they travel
 * as direct holders - the sound packet carries the id itself.
 */
public final class BedrockSoundIds {
	private BedrockSoundIds() {
	}

	public static ResourceLocation of(String bedrockEvent) {
		return VillagerNewsJavafied.id(bedrockEvent.replace(':', '.'));
	}

	public static SoundEvent event(String bedrockEvent) {
		return SoundEvent.createVariableRangeEvent(of(bedrockEvent));
	}

	public static Holder<SoundEvent> holder(String bedrockEvent) {
		return Holder.direct(event(bedrockEvent));
	}
}
