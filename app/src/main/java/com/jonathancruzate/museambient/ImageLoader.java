package com.jonathancruzate.museambient;
import android.content.Context; import android.graphics.*; import android.util.Log;
import java.io.*; import java.security.MessageDigest; import java.util.*;
public class ImageLoader {
    private static final String TAG="MuseAmbient";
    /** Cache dir is versioned: v0.1.0 cached small images under "images"; never reuse them. */
    private final File dir;
    /** Cap decoded width: the Q80R panel is 3840 wide; larger sources are sampled down. */
    static final int MAX_WIDTH=3840;
    public ImageLoader(Context c){dir=new File(c.getCacheDir(),"images-v2"); dir.mkdirs();}
    private File fileFor(String url) throws Exception { StringBuilder s=new StringBuilder(); for(byte b:MessageDigest.getInstance("MD5").digest(url.getBytes("UTF-8")))s.append(String.format("%02x",b)); return new File(dir,s.toString()+".img"); }

    /** Panel rule (Jonathan): only art that fits the TV. 16:9 = 1.778, tolerance plus/minus 5 percent. */
    static final float RATIO_MIN=1.69f, RATIO_MAX=1.87f;
    public Bitmap load(String url){
        // High-res first; if a width-parameterised Commons URL fails at 3840,
        // step down to 2560, then 1920, before giving up on the item.
        List<String> tries=new ArrayList<>(); tries.add(url);
        if(url.contains("width=3840")){ tries.add(url.replace("width=3840","width=2560")); tries.add(url.replace("width=3840","width=1920")); }
        for(String u: tries){ Bitmap b=loadOne(u); if(b!=null){
            float ratio=b.getWidth()/(float)b.getHeight();
            if(ratio<RATIO_MIN||ratio>RATIO_MAX){ Log.i(TAG,"skip (aspect "+String.format("%.3f",ratio)+" outside 1.69-1.87): "+u); b.recycle(); return null; }
            Log.i(TAG,"loaded "+b.getWidth()+"x"+b.getHeight()+" from "+u); return b; } }
        return null;
    }
    private Bitmap loadOne(String url){
        try{ File f=fileFor(url); byte[] data;
            if(f.exists()&&f.length()>0){ data=read(f); }
            else { data=HttpUtil.getBytes(url); FileOutputStream o=new FileOutputStream(f); o.write(data); o.close(); }
            BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(data,0,data.length,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
            BitmapFactory.Options o=new BitmapFactory.Options(); int s=1;
            while(bounds.outWidth/s>MAX_WIDTH)s*=2;
            o.inSampleSize=s;
            return BitmapFactory.decodeByteArray(data,0,data.length,o);
        }catch(Exception e){ Log.i(TAG,"load failed: "+url+" ("+e+")"); return null; }
    }
    private byte[] read(File f) throws Exception { FileInputStream in=new FileInputStream(f); ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[16384]; int n; while((n=in.read(b))>0)o.write(b,0,n); in.close(); return o.toByteArray(); }

    /** Cheap blur: downscale hard, then upscale. Used for the fill background and card washes. */
    public static Bitmap blur(Bitmap src){
        if(src==null)return null;
        Bitmap small=Bitmap.createScaledBitmap(src,Math.max(1,src.getWidth()/24),Math.max(1,src.getHeight()/24),true);
        Bitmap big=Bitmap.createScaledBitmap(small,src.getWidth(),src.getHeight(),true);
        return big;
    }

    /**
     * Matte painting finish for a bright glossy QLED panel, applied once per
     * slide: saturation x0.85, contrast x0.90 with the blacks lifted (+18.75
     * offset, so shadows sit slightly open instead of glossy-crisp). The
     * grain + vignette half of the matte look lives in MatteOverlay, drawn
     * over the art at render time. Never blurs the artwork.
     */
    public static Bitmap matte(Bitmap src){
        if(src==null)return null;
        ColorMatrix sat=new ColorMatrix(); sat.setSaturation(0.85f);
        float lift=(1f-0.90f)*127.5f+6f; // contrast pivot + black lift ~= 18.75
        ColorMatrix cc=new ColorMatrix(new float[]{
            0.90f,0,0,0,lift,
            0,0.90f,0,0,lift,
            0,0,0.90f,0,lift,
            0,0,0,1,0 });
        cc.postConcat(sat); // saturation first, then contrast/lift
        Bitmap out=Bitmap.createBitmap(src.getWidth(),src.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out); Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cc));
        canvas.drawBitmap(src,0,0,p);
        return out;
    }

    /** 1x1 average colour of a bitmap, for artwork-tinted UI surfaces. */
    public static int averageColor(Bitmap src){
        if(src==null)return 0;
        Bitmap one=Bitmap.createScaledBitmap(src,1,1,true);
        int px=one.getPixel(0,0); if(one!=src)one.recycle();
        return px;
    }
}
