package com.javafied.villagernews.client.bedrock;
import com.javafied.villagernews.names.AddonNames;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.cache.object.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.util.Mth;
import java.util.Locale;
public class BedrockEntityRenderer<E extends Entity> extends GeoReplacedEntityRenderer<E,BedrockAnimatable>{
 public BedrockEntityRenderer(EntityRendererProvider.Context c,BedrockGeoModel m,BedrockAnimatable a){super(c,m,a);shadowRadius=.5f;}
 public static RenderType renderType(ResourceLocation t,BedrockMaterials.Kind k){return switch(k){case TRANSLUCENT->RenderType.entityTranslucent(t);case EMISSIVE->RenderType.entityTranslucentEmissive(t);default->RenderType.entityCutoutNoCull(t);};}
 /** Bedrock facial panels have explicit face normals, including zero-depth cubes.
  * GeckoLib's flat-cube correction takes absolute components AFTER rotation, which
  * turns downward-facing cheeks/eyelids toward the light. Keep their actual normal.
  * Scope this to this mod's flat geometry; solid cubes and world rendering stay vanilla.
  */
 @Override public void renderCube(PoseStack stack,GeoCube cube,VertexConsumer vertices,int light,int overlay,int color){
  var size=cube.size();
  if(size.x()!=0&&size.y()!=0&&size.z()!=0){super.renderCube(stack,cube,vertices,light,overlay,color);return;}
  software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(stack,cube);
  software.bernie.geckolib.util.RenderUtil.rotateMatrixAroundCube(stack,cube);
  software.bernie.geckolib.util.RenderUtil.translateAwayFromPivotPoint(stack,cube);
  var pose=stack.last();
  for(var quad:cube.quads()){
   if(quad==null)continue;
   var normal=pose.transformNormal(quad.normal(),new org.joml.Vector3f());
   createVerticesOfQuad(quad,pose.pose(),normal,vertices,light,overlay,color);
  }
 }
 @Override public void render(E entity,float yaw,float tick,PoseStack stack,MultiBufferSource buffers,int light){
  var m=(BedrockGeoModel)model;var plan=BedrockRuntime.evaluate(entity,AddonNames.character(m.clientEntity.apply(entity)),tick);
  if(plan==null||plan.layers().isEmpty()||entity.isInvisible())return;
  currentEntity=entity;m.plan=plan;stack.pushPose();float body=entity instanceof LivingEntity l?Mth.rotLerp(tick,l.yBodyRotO,l.yBodyRot):yaw;
  if(entity instanceof LivingEntity l&&l.isSleeping()&&l.getBedOrientation()!=null){var dir=l.getBedOrientation().getOpposite();stack.translate(dir.getStepX()*.5,0,dir.getStepZ()*.5);body=dir.toYRot();}
  if(entity instanceof LivingEntity l&&l.isSleeping())stack.mulPose(Axis.YP.rotationDegrees(180-body));
  else applyRotations(animatable,stack,entity.tickCount+tick,body,tick,1);
  stack.translate(0,plan.lift(),0);stack.scale(plan.scale(),plan.scale(),plan.scale());
  int overlay=entity instanceof LivingEntity l?OverlayTexture.pack(0,l.hurtTime>0||l.deathTime>0):OverlayTexture.NO_OVERLAY;
  for(var layer:plan.layers()){
   SignTextures.ensureLoaded(layer.texture());var baked=model.getBakedModel(BedrockGeoModel.geometry(layer.model()));applyPose(baked,plan,false);
   int color=layer.dyed()&&entity instanceof Sheep sheep?(sheep.getColor().getTextureDiffuseColor()|0xff000000):0xffffffff;
   var type=renderType(layer.texture(),layer.kind());VertexConsumer v=buffers.getBuffer(type);
   if(layer.uOffset()!=0||layer.vOffset()!=0)v=new BedrockLayersRenderLayer.ScrolledVertices(v,layer.uOffset(),layer.vOffset());
   for(var bone:baked.topLevelBones())renderRecursively(stack,animatable,bone,type,buffers,v,true,tick,light,overlay,color);
  }stack.popPose();
  renderFinal(stack,animatable,null,buffers,null,tick,light,overlay,0xffffffff);
  doPostRenderCleanup();
 }
 public static void applyPose(BakedGeoModel m,BedrockRuntime.RenderPlan p,boolean armor){for(var b:m.topLevelBones())applyBone(b,p,armor);}
 private static void applyBone(GeoBone b,BedrockRuntime.RenderPlan plan,boolean armor){var i=b.getInitialSnapshot();var p=plan.poses().get(b.getName().toLowerCase(Locale.ROOT));
 if(!armor||!b.getName().startsWith("armor")){b.updateRotation(i.getRotX()+(p==null?0:(float)-Math.toRadians(p.rx)),i.getRotY()+(p==null?0:(float)-Math.toRadians(p.ry)),i.getRotZ()+(p==null?0:(float)Math.toRadians(p.rz)));b.updatePosition(p==null?0:(float)p.px,p==null?0:(float)p.py,p==null?0:(float)p.pz);b.updateScale(p==null?1:(float)p.sx,p==null?1:(float)p.sy,p==null?1:(float)p.sz);b.setHidden(!plan.isBoneVisible(b.getName()));}
 for(var c:b.getChildBones())applyBone(c,plan,armor);}
}
