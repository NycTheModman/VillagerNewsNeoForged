package com.javafied.villagernews.client.guide;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.AbstractWidget;import net.minecraft.client.gui.layouts.LinearLayout;import net.minecraft.client.gui.narration.NarrationElementOutput;import net.minecraft.network.chat.Component;import java.util.*;
final class ScrollableLayout extends AbstractWidget{
 private final LinearLayout column;private final List<AbstractWidget> widgets=new ArrayList<>();private double offset;private int maxHeight;private AbstractWidget focused;
 ScrollableLayout(Minecraft mc,LinearLayout c,int h){super(0,0,1,h,Component.empty());column=c;maxHeight=h;c.visitWidgets(widgets::add);arrangeElements();}
 void setMaxHeight(int h){maxHeight=h;arrangeElements();}void arrangeElements(){column.arrangeElements();width=column.getWidth();height=Math.min(maxHeight,column.getHeight());offset=Math.max(0,Math.min(offset,column.getHeight()-height));column.setX(getX());column.setY(getY()-(int)offset);}
 @Override public void setX(int x){super.setX(x);if(column!=null)column.setX(x);}@Override public void setY(int y){super.setY(y);if(column!=null)column.setY(y-(int)offset);}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float t){g.enableScissor(getX(),getY(),getX()+width,getY()+height);for(var w:widgets)w.render(g,x,y,t);g.disableScissor();}
 @Override public boolean mouseScrolled(double x,double y,double h,double v){if(!isMouseOver(x,y))return false;offset-=v*20;arrangeElements();return true;}
 @Override public boolean mouseClicked(double x,double y,int b){if(!isMouseOver(x,y))return false;for(var w:widgets)if(w.mouseClicked(x,y,b)){if(focused!=null)focused.setFocused(false);focused=w;w.setFocused(true);return true;}return false;}
 @Override public boolean mouseReleased(double x,double y,int b){return focused!=null&&focused.mouseReleased(x,y,b);}
 @Override public boolean keyPressed(int k,int s,int m){return focused!=null&&focused.keyPressed(k,s,m);}@Override public boolean charTyped(char c,int m){return focused!=null&&focused.charTyped(c,m);}
 @Override protected void updateWidgetNarration(NarrationElementOutput o){}
}
