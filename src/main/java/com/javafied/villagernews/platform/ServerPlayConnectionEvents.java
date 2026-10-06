package com.javafied.villagernews.platform;
public final class ServerPlayConnectionEvents { @FunctionalInterface public interface Join {void accept(net.minecraft.server.network.ServerGamePacketListenerImpl handler,Object sender,net.minecraft.server.MinecraftServer server);} public static final Hook<Join> JOIN=new Hook<>(); }
