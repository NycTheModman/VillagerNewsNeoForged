package com.javafied.villagernews.client.dev;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockAnimatable;
import com.javafied.villagernews.client.bedrock.BedrockEntityRenderer;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Opt-in integration check: submitted vertex normals must agree with the rendered surface. */
final class LightingRegressionChecks {
    private static boolean done;
    static void run() {
        if(done)return;
        var mc=Minecraft.getInstance();
        for(var entity:mc.level.entitiesForRendering()) {
            if(!(mc.getEntityRenderDispatcher().getRenderer(entity) instanceof BedrockEntityRenderer<?> fixed))continue;
            done=true;
            GeoRenderer<BedrockAnimatable> legacy=new GeoRenderer<>() {
                public GeoModel<BedrockAnimatable> getGeoModel(){return null;}
                public BedrockAnimatable getAnimatable(){return null;}
                public void fireCompileRenderLayersEvent(){}
                public boolean firePreRenderEvent(PoseStack p,BakedGeoModel m,MultiBufferSource b,float t,int l){return true;}
                public void firePostRenderEvent(PoseStack p,BakedGeoModel m,MultiBufferSource b,float t,int l){}
                public void updateAnimatedTextureFrame(BedrockAnimatable a){}
            };
            int tested=0,failed=0,legacyFailures=0;
            for(int axis=0;axis<3;axis++)for(int pitch:new int[]{-35,0,40,65})for(int yaw:new int[]{0,45,90,180,270})for(boolean scaled:new boolean[]{false,true}) {
                var vertices=new GeoVertex[]{new GeoVertex(0,0,0),
                    new GeoVertex(axis==1?1:0,axis==2?1:0,axis==0?1:0),
                    new GeoVertex(axis==0?0:1,axis==1?0:1,axis==2?0:1),
                    new GeoVertex(axis==2?1:0,axis==0?1:0,axis==1?1:0)};
                var normal=new Vector3f(axis==0?-1:0,axis==1?-1:0,axis==2?-1:0);
                var quad=new GeoQuad(vertices,normal,axis==0?Direction.WEST:axis==1?Direction.DOWN:Direction.NORTH);
                var cube=new GeoCube(new GeoQuad[]{quad},Vec3.ZERO,Vec3.ZERO,new Vec3(axis==0?0:1,axis==1?0:1,axis==2?0:1),0,false);
                var stack=new PoseStack();stack.mulPose(Axis.YP.rotationDegrees(yaw));stack.mulPose(Axis.XP.rotationDegrees(-pitch));
                if(scaled)stack.scale(1.2f,.85f,1.1f);
                var actual=new Capture();var old=new Capture();
                stack.pushPose();fixed.renderCube(stack,cube,actual,0xf000f0,0,0xffffffff);stack.popPose();
                stack.pushPose();legacy.renderCube(stack,cube,old,0xf000f0,0,0xffffffff);stack.popPose();
                tested++;
                if(!actual.correct())failed++;
                if(!old.correct())legacyFailures++;
                if(!actual.positions.equals(old.positions))throw new AssertionError("Lighting fix changed geometry");
                if(!normal.equals(axis==0?-1:0,axis==1?-1:0,axis==2?-1:0))throw new AssertionError("Cached normal mutated");
            }
            String result="Lighting normals: "+(failed==0&&legacyFailures>0?"PASS":"FAIL")+"; cases="+tested+"; fixed failures="+failed+"; old renderer failures="+legacyFailures+"; geometry unchanged\n";
            VillagerNewsJavafied.LOGGER.info(result.trim());
            try{Files.writeString(mc.gameDirectory.toPath().resolve("lighting-regression.txt"),result);}catch(Exception e){throw new RuntimeException(e);}
            if(failed!=0||legacyFailures==0)throw new AssertionError(result);
            return;
        }
    }
    private static final class Capture implements VertexConsumer {
        final List<Vector3f> positions=new ArrayList<>(),normals=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z){positions.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setNormal(float x,float y,float z){normals.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        boolean correct(){
            if(positions.size()!=4||normals.size()!=4)return false;
            var expected=new Vector3f(positions.get(1)).sub(positions.get(0)).cross(new Vector3f(positions.get(2)).sub(positions.get(0))).normalize();
            return normals.stream().allMatch(n->n.isFinite()&&Math.abs(n.length()-1)<.0001f&&new Vector3f(n).normalize().dot(expected)>.9999f);
        }
    }
}
