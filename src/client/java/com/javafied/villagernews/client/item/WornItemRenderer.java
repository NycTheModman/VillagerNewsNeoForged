package com.javafied.villagernews.client.item;
import com.javafied.villagernews.content.WearableItem;
import com.javafied.villagernews.client.bedrock.*;
import com.javafied.villagernews.names.AddonNames;
import com.javafied.villagernews.VillagerNewsJavafied;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
final class WornItemRenderer extends GeoArmorRenderer<WearableItem>{
 final String attachable;
 WornItemRenderer(WearableItem i){super(new Model(BuiltInRegistries.ITEM.getKey(i).getPath()));attachable=AddonNames.item(BuiltInRegistries.ITEM.getKey(i).getPath());}
 @Override public void preRender(PoseStack s,WearableItem i,BakedGeoModel m,MultiBufferSource b,VertexConsumer v,boolean repeat,float t,int light,int overlay,int c){super.preRender(s,i,m,b,v,repeat,t,light,overlay,c);if(currentEntity instanceof LivingEntity l){var p=BedrockRuntime.evaluateWorn(l,attachable,t);if(p!=null)BedrockEntityRenderer.applyPose(m,p,true);}}
 private static final class Model extends GeoModel<WearableItem>{final String path;Model(String p){path=p;}
 // BedrockRuntime supplies poses in preRender; do not reset them through an empty Gecko controller.
 public void handleAnimations(WearableItem a,long id,software.bernie.geckolib.animation.AnimationState<WearableItem> state,float tick){}
 public ResourceLocation getModelResource(WearableItem a){return VillagerNewsJavafied.id("geo/item/"+path+".geo.json");}
 public ResourceLocation getTextureResource(WearableItem a){return VillagerNewsJavafied.id("textures/attachable/"+path+".png");}
 public ResourceLocation getAnimationResource(WearableItem a){return VillagerNewsJavafied.id("animations/empty.animation.json");}}
}
