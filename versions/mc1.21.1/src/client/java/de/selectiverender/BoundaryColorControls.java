package de.selectiverender;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.function.Consumer;

/** Inline region palette; native texture rendering is shared with the global sliders. */
final class BoundaryColorControls implements AutoCloseable {
    private final BoundaryEditorTarget target;
    private final Consumer<String> reportError;
    private final net.minecraft.client.font.TextRenderer font;
    private BoundarySliderTexture image;
    private float hue, saturation, brightness;
    private int opacity;
    private boolean rainbow, changed;
    private HuePresetReset presetReset;
    private final java.util.List<Bar> bars=new java.util.ArrayList<>();
    boolean editing() { return bars.stream().anyMatch(bar->bar.isFocused()); }
    BoundaryColorControls(BoundaryEditorTarget target, net.minecraft.client.font.TextRenderer font, Consumer<String> reportError) {
        this.target=target; this.font=font; this.reportError=reportError;
        var hsv=BoundaryStyle.hsv(target.color()); hue=hsv[0]; saturation=hsv[1]; brightness=hsv[2];
        opacity=target.opacity(); rainbow=target.rainbow();
        presetReset=new HuePresetReset(target.color(),rainbow);
    }
    private void apply() {
        changed=false;
        if (!target.colorEnabled()) return;
        try { target.commit(BoundaryStyle.hsv(hue,saturation,brightness),target.texture(),opacity,rainbow,true,false); }
        catch(IllegalArgumentException error) { reportError.accept(error.getMessage()); }
    }
    void release() { if(changed) apply(); }
    void row(int kind,int x,int y,int width,Consumer<net.minecraft.client.gui.widget.ClickableWidget> add) {
        int presetWidth=Math.min(80,Math.max(44,width/4)), sliderWidth=Math.max(24,width-presetWidth-8);
        if(kind<4) { var bar=new Bar(kind,x,y,sliderWidth); bars.add(bar); add.accept(bar); }
        if(kind<3) {
            String label=new String[]{"Rainbow","White","Black"}[kind];
            add.accept(ButtonWidget.builder(Text.literal(label),b-> {
                if(kind==0) { rainbow=true; saturation=brightness=1; }
                else { rainbow=false; hue=0; saturation=0; brightness=kind==1?1:0; }
                presetReset.preset();
                for (Bar bar : bars) bar.sync();
                apply();
            }).dimensions(x+sliderWidth+8,y,presetWidth,20).build());
        }
    }
    void preview(DrawContext draw,int x,int y,int width) {
        int presetWidth=Math.min(80,Math.max(44,width/4));
        prepare();
        draw.getMatrices().push(); draw.getMatrices().translate(0,0,300);
        image.draw(draw,4,x,y,Math.max(24,width-presetWidth-8));
        draw.getMatrices().pop();
    }
    private void prepare() { if(image==null) image=new BoundarySliderTexture(); image.update(hue,saturation,brightness,opacity,rainbow); }
    @Override public void close() { if(image!=null) { image.close(); image=null; } }
    private final class Bar extends SliderWidget {
        private final int kind;
        Bar(int kind,int x,int y,int width) {
            super(x,y,width,20,Text.empty(),kind==0?hue:kind==1?saturation:kind==2?brightness:opacity/100d);
            this.kind=kind; updateMessage();
        }
        @Override public boolean keyPressed(int key,int scan,int modifiers) {
            boolean handled=super.keyPressed(key,scan,modifiers); if(handled && changed) apply(); return handled;
        }
        void sync() { value=kind==0?hue:kind==1?saturation:kind==2?brightness:opacity/100d; updateMessage(); }
        @Override protected void updateMessage() { setMessage(Text.literal(Math.round(value*(kind==0?360:100))+(kind==0?"°":"%"))); }
        @Override protected void applyValue() {
            if(kind==0) { hue=(float)value; rainbow=false; if(presetReset.hue()) { saturation=brightness=1; opacity=100; for(Bar bar:bars) if(bar!=this) bar.sync(); } } else { presetReset.manual(); if(kind==1) saturation=(float)value;
            else if(kind==2) brightness=(float)value; else opacity=(int)Math.round(value*100);
            }
            changed=true;
        }
        @Override public boolean mouseClicked(double x,double y,int button) {
            if(kind==0 && visible && button==0 && isMouseOver(x,y)) { rainbow=false; changed=true; }
            return super.mouseClicked(x,y,button);
        }
        @Override public void renderWidget(DrawContext draw,int mouseX,int mouseY,float delta) {
            prepare(); draw.getMatrices().push(); draw.getMatrices().translate(0,0,300);
            image.draw(draw,kind,getX(),getY(),getWidth());
            int handle=getX()+(int)Math.round(value*(getWidth()-4));
            draw.fill(handle,getY(),handle+4,getY()+20,0xFFEEEEEE);
            draw.drawCenteredTextWithShadow(font,getMessage(),getX()+getWidth()/2,getY()+6,0xFFFFFFFF);
            draw.getMatrices().pop();
        }
    }
}
