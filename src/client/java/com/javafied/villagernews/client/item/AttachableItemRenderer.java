package com.javafied.villagernews.client.item;
import com.javafied.villagernews.content.AttachableItem;
import com.javafied.villagernews.client.bedrock.*;
import com.javafied.villagernews.names.AddonNames;
import com.javafied.villagernews.VillagerNewsJavafied;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.model.GeoModel;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
final class AttachableItemRenderer extends GeoItemRenderer<AttachableItem>{
 private final String attachable;private final String path;
 AttachableItemRenderer(AttachableItem item){super(new Model(AddonNames.item(BuiltInRegistries.ITEM.getKey(item).getPath())));path=BuiltInRegistries.ITEM.getKey(item).getPath();attachable=AddonNames.item(path);}
 @Override public void renderByItem(ItemStack stack,ItemDisplayContext perspective,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
 var mc=Minecraft.getInstance();float tick=mc.getTimer().getGameTimeDeltaPartialTick(true);
 boolean held=perspective.firstPerson()||perspective==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||perspective==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
 if(!held){var icon=mc.getModelManager().getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(VillagerNewsJavafied.id("item/"+path+"_icon")));pose.pushPose();pose.translate(.5,.5,.5);mc.getItemRenderer().render(stack,perspective,false,pose,buffers,light,overlay,icon);pose.popPose();return;}
 animatable=(AttachableItem)stack.getItem();currentItemStack=stack;renderPerspective=perspective;
 var plan=mc.player==null?null:BedrockRuntime.evaluateAttachable(mc.player,attachable,perspective.firstPerson(),perspective!=ItemDisplayContext.FIRST_PERSON_LEFT_HAND&&perspective!=ItemDisplayContext.THIRD_PERSON_LEFT_HAND,tick);
 ((Model)model).plan=plan;pose.pushPose();pose.translate(.5,.5,.5);
 if(!perspective.firstPerson()){pose.translate(0,-3/16f,3/16f);pose.mulPose(Axis.XP.rotationDegrees(90));}
 else{float side=perspective==ItemDisplayContext.FIRST_PERSON_LEFT_HAND?-1:1;pose.translate(-side*.56,.52,.72);pose.mulPose(Axis.YP.rotationDegrees(180));pose.translate(0,-1.62,0);pose.translate(side*-13.5/16,-10/16f,12/16f);pose.translate(side*5/16,22/16f,0);pose.mulPose(Axis.ZP.rotationDegrees(side*115));pose.mulPose(Axis.YP.rotationDegrees(side*45));pose.mulPose(Axis.XP.rotationDegrees(-95));pose.translate(-side*5/16,-22/16f,0);pose.translate(side*6/16,15/16f,0);}
 var baked=model.getBakedModel(model.getModelResource(animatable));if(plan!=null)BedrockEntityRenderer.applyPose(baked,plan,false);
 var roots=boundRoots(model.getModelResource(animatable));for(var bone:baked.topLevelBones())if(roots.contains(bone.getName()))bone.setPosY(bone.getPosY()-24);
 // The handbook's negative-size cubes form an inside-out outline shell. Its back faces
 // must be culled; otherwise the solid outline swatch covers the actual cover and pages.
 var texture=model.getTextureResource(animatable);
 var type=path.equals("handbook")?RenderType.entityCutout(texture):RenderType.entityCutoutNoCull(texture);var vertices=buffers.getBuffer(type);
 for(var bone:baked.topLevelBones())renderRecursively(pose,animatable,bone,type,buffers,vertices,true,tick,light,overlay,0xffffffff);
 pose.popPose();animatable=null;
 }
 private static final java.util.Map<ResourceLocation,java.util.Set<String>> BOUND=new java.util.HashMap<>();
 private static java.util.Set<String> boundRoots(ResourceLocation id){return BOUND.computeIfAbsent(id,key->{var names=new java.util.HashSet<String>();try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(key)){var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();for(var geometry:json.getAsJsonArray("minecraft:geometry"))for(var value:geometry.getAsJsonObject().getAsJsonArray("bones")){var bone=value.getAsJsonObject();if(!bone.has("parent")&&bone.has("binding"))names.add(bone.get("name").getAsString());}}catch(java.io.IOException ex){VillagerNewsJavafied.LOGGER.warn("Could not read attachable binding {}",key,ex);}return java.util.Set.copyOf(names);});}
 private static final class Model extends GeoModel<AttachableItem>{final String name;BedrockRuntime.RenderPlan plan;Model(String n){name=n;}
 private BedrockRuntime.Layer layer(){return plan!=null&&!plan.layers().isEmpty()?plan.layers().getFirst():BedrockRuntime.attachableBaseLayer(name);}
 public ResourceLocation getModelResource(AttachableItem a){return BedrockGeoModel.geometry(layer().model());}
 public ResourceLocation getTextureResource(AttachableItem a){return layer().texture();}
 public ResourceLocation getAnimationResource(AttachableItem a){return VillagerNewsJavafied.id("animations/empty.animation.json");}
 }
}
