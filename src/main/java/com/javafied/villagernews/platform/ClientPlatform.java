package com.javafied.villagernews.platform;
public final class ClientPlatform {
 static final java.util.List<java.util.function.Consumer<net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent>> reloaders=new java.util.ArrayList<>();
 public static void reload(net.minecraft.resources.ResourceLocation id,net.minecraft.server.packs.resources.PreparableReloadListener listener){reloaders.add(e->e.registerReloadListener(listener));}
 public static void init(net.neoforged.bus.api.IEventBus bus){
  bus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional e)->{ for(String name : new String[]{"microphone","handbook"}) e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagernewsjavafied","item/"+name+"_icon"))); });
  new com.javafied.villagernews.client.VillagerNewsJavafiedClient().onInitializeClient();
  bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e)->e.enqueueWork(com.javafied.villagernews.client.dev.DevShots::init));
  bus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e)->reloaders.forEach(c->c.accept(e)));
  bus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e)->EntityRendererRegistry.renderers.forEach(c->c.accept(e)));
  bus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e)->HudElementRegistry.layers.forEach(c->c.accept(e)));
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->ClientTickEvents.END_CLIENT_TICK.listeners.forEach(c->c.accept(net.minecraft.client.Minecraft.getInstance())));
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e)->ClientPlayConnectionEvents.DISCONNECT.listeners.forEach(c->c.accept(null,net.minecraft.client.Minecraft.getInstance())));
 }
}
