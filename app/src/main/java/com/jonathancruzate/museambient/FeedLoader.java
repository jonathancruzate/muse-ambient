package com.jonathancruzate.museambient;
import android.content.Context; import org.json.*; import java.io.*; import java.util.*;
/** Art playlist: configurable JSON URL -> cache -> bundled BuiltInFeed. Fail-silent. */
public class FeedLoader {
    private final Context ctx; public FeedLoader(Context c){ctx=c.getApplicationContext();}
    public List<ArtItem> load(){
        String url=Prefs.artFeedUrl(ctx);
        if(url!=null&&!url.trim().isEmpty()){
            try{ String json=HttpUtil.getString(url.trim()); List<ArtItem> l=parse(json); if(!l.isEmpty()){cache(json); return l;} }catch(Exception ignored){}
            try{ String json=readCache(); if(json!=null){ List<ArtItem> l=parse(json); if(!l.isEmpty())return l; } }catch(Exception ignored){}
        }
        return BuiltInFeed.items();
    }
    static List<ArtItem> parse(String json) throws Exception {
        List<ArtItem> l=new ArrayList<>(); JSONArray a=new JSONArray(json);
        for(int i=0;i<a.length();i++){ JSONObject j=a.getJSONObject(i); String u=j.optString("imageUrl",""); if(!u.isEmpty()) l.add(new ArtItem(j.optString("title","Untitled"),j.optString("artist",""),j.optString("year",""),j.optString("collection",""),u)); }
        return l;
    }
    private void cache(String j){ try{ FileOutputStream o=new FileOutputStream(new File(ctx.getCacheDir(),"art-feed.json")); o.write(j.getBytes("UTF-8")); o.close(); }catch(Exception ignored){} }
    private String readCache() throws Exception { File f=new File(ctx.getCacheDir(),"art-feed.json"); if(!f.exists())return null; FileInputStream in=new FileInputStream(f); ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n; while((n=in.read(b))>0)o.write(b,0,n); in.close(); return o.toString("UTF-8"); }
}
