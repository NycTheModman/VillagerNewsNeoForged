package com.javafied.villagernews.platform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
public final class ServerPlayNetworking {
 public record Context(ServerPlayer player){}
 private static final java.util.Map<CustomPacketPayload.Type<?>,java.util.function.BiConsumer<CustomPacketPayload,Context>> handlers=new java.util.HashMap<>();
 public static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type,java.util.function.BiConsumer<T,Context> handler){handlers.put(type,(p,c)->handler.accept((T)p,c));}
 public static void receive(CustomPacketPayload p,ServerPlayer player){var h=handlers.get(p.type());if(h!=null)h.accept(p,new Context(player));}
 public static void send(ServerPlayer player,CustomPacketPayload p){net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,p);}
 public static boolean canSend(ServerPlayer player,CustomPacketPayload.Type<?> type){return player.connection.hasChannel(type.id());}
}
