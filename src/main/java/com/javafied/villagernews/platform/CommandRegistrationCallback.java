package com.javafied.villagernews.platform;
@FunctionalInterface public interface CommandRegistrationCallback { Hook<CommandRegistrationCallback> EVENT=new Hook<>(); void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext context,net.minecraft.commands.Commands.CommandSelection selection); }
