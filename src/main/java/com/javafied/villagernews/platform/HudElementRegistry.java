package com.javafied.villagernews.platform;
public final class HudElementRegistry {
 static final java.util.List<java.util.function.Consumer<net.neoforged.neoforge.client.event.RegisterGuiLayersEvent>> layers=new java.util.ArrayList<>();
 public static void addLast(net.minecraft.resources.ResourceLocation id,net.minecraft.client.gui.LayeredDraw.Layer layer){layers.add(e->e.registerAboveAll(id,layer));}
}
