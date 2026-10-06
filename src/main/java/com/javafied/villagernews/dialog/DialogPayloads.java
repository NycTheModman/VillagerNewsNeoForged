package com.javafied.villagernews.dialog;

import com.javafied.villagernews.VillagerNewsJavafied;

import com.javafied.villagernews.platform.PayloadTypeRegistry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: a villager starts or stops speaking a line. */
public final class DialogPayloads {
	private DialogPayloads() {
	}

	/**
	 * Play {@code sound} from the entity, run {@code animation} on its model and
	 * show the subtitles at their times; the line lasts {@code durationTicks}.
	 */
	public record Line(int entityId, String sound, String animation, int durationTicks, List<DialogLibrary.Subtitle> subtitles)
			implements CustomPacketPayload {
		public static final Type<Line> TYPE = new Type<>(VillagerNewsJavafied.id("dialog_line"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Line> CODEC = StreamCodec.ofMember(Line::write, Line::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(entityId);
			buf.writeUtf(sound);
			buf.writeUtf(animation);
			buf.writeVarInt(durationTicks);
			buf.writeVarInt(subtitles.size());
			for (DialogLibrary.Subtitle subtitle : subtitles) {
				buf.writeFloat((float) subtitle.time());
				buf.writeUtf(subtitle.key());
			}
		}

		private static Line read(RegistryFriendlyByteBuf buf) {
			int entityId = buf.readVarInt();
			String sound = buf.readUtf();
			String animation = buf.readUtf();
			int duration = buf.readVarInt();
			int count = buf.readVarInt();
			List<DialogLibrary.Subtitle> subtitles = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				subtitles.add(new DialogLibrary.Subtitle(buf.readFloat(), buf.readUtf()));
			}
			return new Line(entityId, sound, animation, duration, subtitles);
		}

		@Override
		public Type<Line> type() {
			return TYPE;
		}
	}

	/** Cut the entity's current line short (it was hurt, or interrupted by a more urgent line). */
	public record Stop(int entityId) implements CustomPacketPayload {
		public static final Type<Stop> TYPE = new Type<>(VillagerNewsJavafied.id("dialog_stop"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Stop> CODEC = StreamCodec.ofMember(
				(stop, buf) -> buf.writeVarInt(stop.entityId), buf -> new Stop(buf.readVarInt()));

		@Override
		public Type<Stop> type() {
			return TYPE;
		}
	}

	/** The debug view: what the villager a player looks at is up to (no lines: nothing in view / debug off). */
	public record Debug(List<String> lines) implements CustomPacketPayload {
		public static final Type<Debug> TYPE = new Type<>(VillagerNewsJavafied.id("dialog_debug"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Debug> CODEC = StreamCodec.ofMember(
				(debug, buf) -> {
					buf.writeVarInt(debug.lines.size());
					debug.lines.forEach(buf::writeUtf);
				},
				buf -> {
					int count = buf.readVarInt();
					List<String> lines = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						lines.add(buf.readUtf());
					}
					return new Debug(lines);
				});

		@Override
		public Type<Debug> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Line.TYPE, Line.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Stop.TYPE, Stop.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Debug.TYPE, Debug.CODEC);
	}
}
