package com.javafied.villagernews.platform;
@FunctionalInterface public interface UseEntityCallback { Hook<UseEntityCallback> EVENT=new Hook<>(); net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player p,net.minecraft.world.level.Level l,net.minecraft.world.InteractionHand h,net.minecraft.world.entity.Entity e,net.minecraft.world.phys.EntityHitResult hit); }
