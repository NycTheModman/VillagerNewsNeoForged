package com.javafied.villagernews.platform;
public final class LoaderEnvironment {
 private static final LoaderEnvironment INSTANCE=new LoaderEnvironment();
 public static LoaderEnvironment getInstance(){return INSTANCE;}
 public java.nio.file.Path getGameDir(){return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();}
 public java.nio.file.Path getConfigDir(){return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();}
 public EnvType getEnvironmentType(){return net.neoforged.fml.loading.FMLEnvironment.dist.isClient()?EnvType.CLIENT:EnvType.SERVER;}
 public boolean isModLoaded(String id){return net.neoforged.fml.ModList.get().isLoaded(id);}
}
