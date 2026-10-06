package com.javafied.villagernews.platform;
public final class PlayerLookup {public static java.util.Collection<net.minecraft.server.level.ServerPlayer> tracking(net.minecraft.world.entity.Entity entity){return ((net.minecraft.server.level.ServerLevel)entity.level()).players().stream().filter(p->p.distanceToSqr(entity)<128*128).toList();} }
