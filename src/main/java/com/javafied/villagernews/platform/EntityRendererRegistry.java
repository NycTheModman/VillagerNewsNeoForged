package com.javafied.villagernews.platform;
public final class EntityRendererRegistry {
 static final java.util.List<java.util.function.Consumer<net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers>> renderers=new java.util.ArrayList<>();
 public static <T extends net.minecraft.world.entity.Entity> void register(net.minecraft.world.entity.EntityType<? extends T> type,net.minecraft.client.renderer.entity.EntityRendererProvider<T> provider){renderers.add(e->e.registerEntityRenderer(type,provider));}
}
