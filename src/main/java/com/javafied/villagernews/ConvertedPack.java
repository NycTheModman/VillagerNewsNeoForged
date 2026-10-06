package com.javafied.villagernews;
import java.nio.file.Path;
public final class ConvertedPack{
 public static final String PACK_ID="villagernewsjavafied-converted";
 public static Path dir(){return net.neoforged.fml.ModList.get().getModFileById(VillagerNewsJavafied.MOD_ID).getFile().findResource("bundled");}
 public static Path serverData(String file){return dir().resolve("server").resolve(file);}
 public static String addonVersion(){return "1.0.4";}
}
