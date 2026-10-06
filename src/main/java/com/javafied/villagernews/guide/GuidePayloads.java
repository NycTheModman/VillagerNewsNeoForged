package com.javafied.villagernews.guide;

import com.javafied.villagernews.VillagerNewsJavafied;

import com.javafied.villagernews.platform.PayloadTypeRegistry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The handbook's world settings: sent to players, and changed from its settings page. */
public final class GuidePayloads {
	private GuidePayloads() {
	}

	/** Server -> client: the world's settings, and whether this player may change them. */
	public record Settings(int chattiness, int rareLines, boolean specialVillagers, boolean canEdit) implements CustomPacketPayload {
		public static final Type<Settings> TYPE = new Type<>(VillagerNewsJavafied.id("guide_settings"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.ofMember(Settings::write, Settings::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(chattiness);
			buf.writeVarInt(rareLines);
			buf.writeBoolean(specialVillagers);
			buf.writeBoolean(canEdit);
		}

		private static Settings read(RegistryFriendlyByteBuf buf) {
			return new Settings(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
		}

		@Override
		public Type<Settings> type() {
			return TYPE;
		}
	}

	/** Client -> server: set one of the world settings ({@link GuideSettings}' ids). */
	public record Change(String setting, int value) implements CustomPacketPayload {
		public static final Type<Change> TYPE = new Type<>(VillagerNewsJavafied.id("guide_change"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Change> CODEC = StreamCodec.ofMember(Change::write, Change::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeUtf(setting);
			buf.writeVarInt(value);
		}

		private static Change read(RegistryFriendlyByteBuf buf) {
			return new Change(buf.readUtf(), buf.readVarInt());
		}

		@Override
		public Type<Change> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Settings.TYPE, Settings.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Change.TYPE, Change.CODEC);
	}
}
