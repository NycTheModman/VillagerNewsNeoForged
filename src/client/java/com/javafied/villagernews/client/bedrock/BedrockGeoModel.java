package com.javafied.villagernews.client.bedrock;
import software.bernie.geckolib.model.GeoModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import java.util.function.Function;
public final class BedrockGeoModel extends GeoModel<BedrockAnimatable>{
 final Function<Entity,String> clientEntity; BedrockRuntime.RenderPlan plan;
 public BedrockGeoModel(Function<Entity,String> f){clientEntity=f;}
 public static ResourceLocation geometry(ResourceLocation id){return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"geo/"+id.getPath()+".geo.json");}
 public ResourceLocation getModelResource(BedrockAnimatable a){return geometry(plan.layers().getFirst().model());}
 public ResourceLocation getTextureResource(BedrockAnimatable a){return plan.layers().getFirst().texture();}
 public ResourceLocation getAnimationResource(BedrockAnimatable a){return ResourceLocation.fromNamespaceAndPath("villagernewsjavafied","animations/empty.animation.json");}
}
