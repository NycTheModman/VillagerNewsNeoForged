package com.javafied.villagernews.platform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.client.Minecraft;
public final class ClientPlayNetworking {
 public record Context(Minecraft client){}
 private static final java.util.Map<CustomPacketPayload.Type<?>,java.util.function.BiConsumer<CustomPacketPayload,Context>> handlers=new java.util.HashMap<>();
 public static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type,java.util.function.BiConsumer<T,Context> handler){handlers.put(type,(p,c)->handler.accept((T)p,c));}
 public static void receive(CustomPacketPayload p){var h=handlers.get(p.type());if(h!=null)h.accept(p,new Context(Minecraft.getInstance()));}
 public static void send(CustomPacketPayload p){net.neoforged.neoforge.network.PacketDistributor.sendToServer(p);}
 public static boolean canSend(CustomPacketPayload.Type<?> type){var c=Minecraft.getInstance().getConnection();return c!=null&&c.hasChannel(type.id());}
}
