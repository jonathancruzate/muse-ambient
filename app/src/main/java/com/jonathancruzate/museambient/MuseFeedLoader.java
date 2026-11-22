package com.jonathancruzate.museambient;
import android.content.Context; import java.io.*;
/** Polls Muse feed, caches last good payload, falls back to bundled assets/home.json (sample/demo mode). */
public class MuseFeedLoader {
    private final Context ctx;
    public MuseFeedLoader(Context c){ctx=c.getApplicationContext();}
    public MuseFeed load(){
        String url=Prefs.museFeedUrl(ctx);
        if(url!=null&&!url.trim().isEmpty()){
            try{ String json=HttpUtil.getString(url.trim()); MuseFeed m=MuseFeed.parse(json); writeCache(json); return m; }
            catch(Exception e){ MuseFeed cached=fromCache(); if(cached!=null)return cached; }
        }
        return fromAssets();
    }
    private void writeCache(String json){ try{ FileOutputStream o=new FileOutputStream(new File(ctx.getCacheDir(),"muse-feed.json")); o.write(json.getBytes("UTF-8")); o.close(); }catch(Exception ignored){} }
    private MuseFeed fromCache(){ try{ File f=new File(ctx.getCacheDir(),"muse-feed.json"); if(!f.exists())return null; return MuseFeed.parse(readFile(f)); }catch(Exception e){return null;} }
    private MuseFeed fromAssets(){ try{ InputStream in=ctx.getAssets().open("home.json"); ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n; while((n=in.read(b))>0)o.write(b,0,n); return MuseFeed.parse(o.toString("UTF-8")); }catch(Exception e){return new MuseFeed();} }
    private String readFile(File f) throws Exception { FileInputStream in=new FileInputStream(f); ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n; while((n=in.read(b))>0)o.write(b,0,n); in.close(); return o.toString("UTF-8"); }
}
