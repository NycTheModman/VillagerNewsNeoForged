package com.javafied.villagernews.platform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
public final class PayloadTypeRegistry {
 private static final java.util.List<java.util.function.Consumer<net.neoforged.neoforge.network.registration.PayloadRegistrar>> registrations=new java.util.ArrayList<>();
 private final boolean clientbound;
 private PayloadTypeRegistry(boolean cb){clientbound=cb;}
 public static PayloadTypeRegistry clientboundPlay(){return new PayloadTypeRegistry(true);}
 public static PayloadTypeRegistry serverboundPlay(){return new PayloadTypeRegistry(false);}
 public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec){
  if(clientbound)registrations.add(r->r.playToClient(type,codec,(payload,context)->ClientPlayNetworking.receive(payload)));
  else registrations.add(r->r.playToServer(type,codec,(payload,context)->ServerPlayNetworking.receive(payload,(net.minecraft.server.level.ServerPlayer)context.player())));
 }
 public static void registerAll(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){var r=event.registrar("1");registrations.forEach(c->c.accept(r));}
}
