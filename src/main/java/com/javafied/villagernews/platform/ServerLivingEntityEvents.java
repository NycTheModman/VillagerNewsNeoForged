package com.javafied.villagernews.platform;
public final class ServerLivingEntityEvents {
 @FunctionalInterface public interface Damage {void accept(net.minecraft.world.entity.LivingEntity e,net.minecraft.world.damagesource.DamageSource s,float base,float actual,boolean blocked);}
 @FunctionalInterface public interface Death {boolean accept(net.minecraft.world.entity.LivingEntity e,net.minecraft.world.damagesource.DamageSource s,float amount);}
 public static final Hook<Damage> AFTER_DAMAGE=new Hook<>(); public static final Hook<Death> ALLOW_DEATH=new Hook<>();
 public static final Hook<java.util.function.BiConsumer<net.minecraft.world.entity.LivingEntity,net.minecraft.world.damagesource.DamageSource>> AFTER_DEATH=new Hook<>();
}
