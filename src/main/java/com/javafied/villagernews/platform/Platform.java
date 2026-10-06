package com.javafied.villagernews.platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.minecraft.server.level.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
import net.neoforged.neoforge.registries.RegisterEvent;
public final class Platform {
 private static final java.util.List<java.util.function.Consumer<RegisterEvent>> pending=new java.util.ArrayList<>();
 public static <T> T register(Registry<T> registry,ResourceLocation id,T value){pending.add(e->e.register(registry.key(),id,()->value));return value;}
 public static <T> T register(Registry<T> registry,ResourceKey<T> key,T value){return register(registry,key.location(),value);}
 public static void init(IEventBus bus){
  bus.addListener((RegisterEvent e)->pending.forEach(c->c.accept(e)));
  bus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e)->PayloadTypeRegistry.registerAll(e));
  var events=NeoForge.EVENT_BUS;
  DevServerSmoke.init();
  events.addListener((ServerTickEvent.Post e)->ServerTickEvents.END_SERVER_TICK.listeners.forEach(c->c.accept(e.getServer())));
  events.addListener((ServerStartingEvent e)->ServerLifecycleEvents.SERVER_STARTING.listeners.forEach(c->c.accept(e.getServer())));
  events.addListener((ServerStartedEvent e)->ServerLifecycleEvents.SERVER_STARTED.listeners.forEach(c->c.accept(e.getServer())));
  events.addListener((ServerStoppedEvent e)->ServerLifecycleEvents.SERVER_STOPPED.listeners.forEach(c->c.accept(e.getServer())));
  events.addListener((EntityJoinLevelEvent e)->{if(e.getLevel() instanceof ServerLevel l)ServerEntityEvents.ENTITY_LOAD.listeners.forEach(c->c.accept(e.getEntity(),l));});
  events.addListener((EntityLeaveLevelEvent e)->{if(e.getLevel() instanceof ServerLevel l)ServerEntityEvents.ENTITY_UNLOAD.listeners.forEach(c->c.accept(e.getEntity(),l));});
  events.addListener((LivingDamageEvent.Post e)->{if(!e.getEntity().level().isClientSide())ServerLivingEntityEvents.AFTER_DAMAGE.listeners.forEach(c->c.accept(e.getEntity(),e.getSource(),e.getOriginalDamage(),e.getNewDamage(),e.getBlockedDamage()>0));});
  events.addListener((LivingDeathEvent e)->{if(!e.getEntity().level().isClientSide())for(var c:ServerLivingEntityEvents.ALLOW_DEATH.listeners)if(!c.accept(e.getEntity(),e.getSource(),Float.MAX_VALUE)){e.setCanceled(true);break;}});
  events.addListener(net.neoforged.bus.api.EventPriority.LOWEST,(LivingDeathEvent e)->{if(!e.getEntity().level().isClientSide())ServerLivingEntityEvents.AFTER_DEATH.listeners.forEach(c->c.accept(e.getEntity(),e.getSource()));});
  events.addListener((PlayerInteractEvent.EntityInteract e)->{for(var c:UseEntityCallback.EVENT.listeners){var r=c.interact(e.getEntity(),e.getLevel(),e.getHand(),e.getTarget(),null);if(r.consumesAction()){e.setCancellationResult(r);e.setCanceled(true);break;}}});
  events.addListener((PlayerInteractEvent.RightClickBlock e)->{for(var c:UseBlockCallback.EVENT.listeners){var r=c.interact(e.getEntity(),e.getLevel(),e.getHand(),e.getHitVec());if(r.consumesAction()){e.setCancellationResult(r);e.setCanceled(true);break;}}});
  events.addListener(net.neoforged.bus.api.EventPriority.LOWEST,(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e)->{if(e.getLevel() instanceof ServerLevel level)PlayerBlockBreakEvents.AFTER.listeners.forEach(c->c.accept(level,e.getPlayer(),e.getPos(),e.getState(),level.getBlockEntity(e.getPos())));});
  events.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer player)ServerPlayConnectionEvents.JOIN.listeners.forEach(c->c.accept(player.connection,null,player.level().getServer()));});
  events.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e)->CommandRegistrationCallback.EVENT.listeners.forEach(c->c.register(e.getDispatcher(),e.getBuildContext(),e.getCommandSelection())));
 }
}
