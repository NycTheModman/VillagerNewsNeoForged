package com.javafied.villagernews.content;
import java.util.*;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.javafied.villagernews.platform.Platform;
import com.javafied.villagernews.VillagerNewsJavafied;
public final class ModAttachments {
 private static <T> AttachmentType<T> reg(String id,AttachmentType.Builder<T> builder){AttachmentType<T> value=builder.build();Platform.register(NeoForgeRegistries.ATTACHMENT_TYPES,VillagerNewsJavafied.id(id),value);return value;}
 public static final AttachmentType<String> VILLAGER_VARIANT=reg("villager_variant",AttachmentType.builder(()->VillagerVariantKeys.DEFAULT).serialize(Codec.STRING).sync(ByteBufCodecs.STRING_UTF8));
 public static final AttachmentType<Map<String,String>> BEHAVIOR_PROPERTIES=reg("behavior_properties",AttachmentType.<Map<String,String>>builder(()->new HashMap<>()).serialize(Codec.unboundedMap(Codec.STRING,Codec.STRING)).sync(ByteBufCodecs.map(HashMap::new,ByteBufCodecs.STRING_UTF8,ByteBufCodecs.STRING_UTF8)));
 public static final AttachmentType<Boolean> AVOIDING=reg("avoiding",AttachmentType.builder(()->false).sync(ByteBufCodecs.BOOL));
 public static final AttachmentType<Map<String,Integer>> GUIDE_SETTINGS=reg("guide_settings",AttachmentType.<Map<String,Integer>>builder(()->new HashMap<>()).serialize(Codec.unboundedMap(Codec.STRING,Codec.INT)));
 public static final AttachmentType<Boolean> RECEIVED_HANDBOOK=reg("received_handbook",AttachmentType.builder(()->false).serialize(Codec.BOOL).copyOnDeath());
 public static final AttachmentType<Boolean> FROM_VILLAGE_GENERATION=reg("from_village_generation",AttachmentType.builder(()->false).serialize(Codec.BOOL));
 public static final AttachmentType<Map<String,String>> SPECIAL_CHARACTERS=reg("special_characters",AttachmentType.<Map<String,String>>builder(()->new HashMap<>()).serialize(Codec.unboundedMap(Codec.STRING,Codec.STRING)));
 public static void init(){}
}
