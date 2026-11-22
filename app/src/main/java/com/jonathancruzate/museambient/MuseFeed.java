package com.jonathancruzate.museambient;
import org.json.*; import java.util.*;
/** Model for the Muse integration feed. See MUSE-FEED.md. All fields optional. */
public class MuseFeed {
    public String houseMode, daisyStatus, daisyNote, nextEventTitle, nextEventTime, nowPlaying, messageText, messageExpiresAt, briefTitle, updatedAt;
    public List<String> whoHome=new ArrayList<>(), alerts=new ArrayList<>(), briefLines=new ArrayList<>();
    public boolean hasBrief(){return briefTitle!=null||!briefLines.isEmpty();}
    public boolean messageActive(){
        if(messageText==null||messageText.trim().isEmpty())return false;
        if(messageExpiresAt==null)return true;
        long exp=parseIso(messageExpiresAt); return exp==0||System.currentTimeMillis()<exp;
    }
    static long parseIso(String s){
        try{ java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US); return f.parse(s).getTime(); }catch(Exception e){
        try{ java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US); f.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); return f.parse(s).getTime(); }catch(Exception e2){return 0;}}
    }
    public static MuseFeed parse(String json) throws Exception {
        MuseFeed m=new MuseFeed(); JSONObject j=new JSONObject(json);
        m.houseMode=j.optString("houseMode",null); m.nowPlaying=j.optString("nowPlaying",null); m.updatedAt=j.optString("updatedAt",null);
        JSONArray w=j.optJSONArray("whoHome"); if(w!=null)for(int i=0;i<w.length();i++)m.whoHome.add(w.optString(i));
        JSONObject d=j.optJSONObject("daisy"); if(d!=null){m.daisyStatus=d.optString("status",null); m.daisyNote=d.optString("note",null);}
        JSONObject e=j.optJSONObject("nextEvent"); if(e!=null){m.nextEventTitle=e.optString("title",null); m.nextEventTime=e.optString("time",null);}
        JSONArray a=j.optJSONArray("alerts"); if(a!=null)for(int i=0;i<a.length();i++)m.alerts.add(a.optString(i));
        JSONObject msg=j.optJSONObject("message"); if(msg!=null){m.messageText=msg.optString("text",null); m.messageExpiresAt=msg.optString("expiresAt",null);}
        JSONObject b=j.optJSONObject("brief"); if(b!=null){m.briefTitle=b.optString("title",null); JSONArray l=b.optJSONArray("lines"); if(l!=null)for(int i=0;i<Math.min(4,l.length());i++)m.briefLines.add(l.optString(i));}
        return m;
    }
}
