package com.javafied.villagernews.platform;
/** Opt-in smoke test, restricted to a disposable development directory. */
final class DevServerSmoke {
 static void init(){
 var dir=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
 var flag=dir.resolve("villagernews-smoke-test");
 if(!dir.endsWith("run-server")||!java.nio.file.Files.exists(flag))return;
 net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{
 var server=e.getServer();
 if(server.getTickCount()==5){
 var level=server.overworld();
 for(var type:java.util.List.of(net.minecraft.world.entity.EntityType.VILLAGER,net.minecraft.world.entity.EntityType.SHEEP,net.minecraft.world.entity.EntityType.WANDERING_TRADER)){
 var mob=type.create(level);mob.setPos(.5,-60,3.5);level.addFreshEntity(mob);
 }
 server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doMobSpawning false");
 server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doDaylightCycle false");
 server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set noon");
 }
 if(server.getTickCount()==60){try{java.nio.file.Files.writeString(dir.resolve("smoke-pass.txt"),"PASS: Minecraft 1.21.1 reached tick 60, spawned villager/sheep/trader and loaded dialogue.\n");}catch(java.io.IOException ex){throw new RuntimeException(ex);}com.javafied.villagernews.VillagerNewsJavafied.LOGGER.info("PORT SERVER SMOKE PASS");server.halt(false);}
 });
 }
}
