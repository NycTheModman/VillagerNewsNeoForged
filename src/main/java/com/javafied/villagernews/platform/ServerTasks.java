package com.javafied.villagernews.platform;
import net.minecraft.server.MinecraftServer;import net.minecraft.server.TickTask;import java.util.*;
public final class ServerTasks {
 private static final Map<MinecraftServer,List<TickTask>> pending=new WeakHashMap<>();
 static {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->tick(e.getServer()));}
 public static synchronized void schedule(MinecraftServer s,TickTask t){pending.computeIfAbsent(s,k->new ArrayList<>()).add(t);}
 private static void tick(MinecraftServer s){List<TickTask> ready=new ArrayList<>();synchronized(ServerTasks.class){var list=pending.get(s);if(list==null)return;var it=list.iterator();while(it.hasNext()){var t=it.next();if(t.getTick()<=s.getTickCount()){ready.add(t);it.remove();}}}ready.forEach(Runnable::run);}
}
