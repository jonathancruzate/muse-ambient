package com.jonathancruzate.museambient;
import android.content.Context; import android.graphics.*; import android.view.View;
import java.util.Random;
/**
 * The optical half of the matte finish, drawn over the artwork (never over
 * text): a very subtle generated paper grain (about 6 percent alpha, overlay
 * blend, tiled) plus a gentle vignette (edges darkened about 12 percent).
 * Both are precomputed once per size; per-frame cost is two bitmap/shader
 * draws. The artwork bitmap itself is NOT blurred by this view.
 */
public class MatteOverlay extends View {
    private Bitmap grain; private Paint grainPaint, vignettePaint;
    public MatteOverlay(Context c){ super(c); setWillNotDraw(false); buildGrain(); }
    private void buildGrain(){
        int n=256; grain=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888);
        Random r=new Random(20261121); int[] px=new int[n*n];
        for(int i=0;i<px.length;i++){ int v=96+r.nextInt(64); px[i]=Color.rgb(v,v,v); } // mid-grey noise for OVERLAY
        grain.setPixels(px,0,n,0,0,n,n);
        grainPaint=new Paint(); grainPaint.setAlpha(15); // ~6 percent
        grainPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh);
        float radius=Math.max(w,h)*0.78f;
        RadialGradient g=new RadialGradient(w/2f,h/2f,radius,
            new int[]{0x00000000,0x00000000,0x1F000000},
            new float[]{0f,0.62f,1f},Shader.TileMode.CLAMP);
        vignettePaint=new Paint(); vignettePaint.setShader(g);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(vignettePaint!=null)canvas.drawRect(0,0,getWidth(),getHeight(),vignettePaint);
        if(grain!=null){ // tile the grain across the screen
            for(int x=0;x<getWidth();x+=grain.getWidth())
                for(int y=0;y<getHeight();y+=grain.getHeight())
                    canvas.drawBitmap(grain,x,y,grainPaint);
        }
    }
}
