package com.jonathancruzate.museambient;
import java.io.*; import java.net.*;
public class HttpUtil {
    public static String getString(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setRequestProperty("User-Agent","MuseAmbient/0.2.1");
        try{ InputStream in=c.getInputStream(); ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=in.read(b))>0)o.write(b,0,n); return o.toString("UTF-8"); }
        finally{c.disconnect();}
    }
    public static byte[] getBytes(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(30000); c.setRequestProperty("User-Agent","MuseAmbient/0.2.1");
        try{ InputStream in=c.getInputStream(); ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[16384]; int n; while((n=in.read(b))>0)o.write(b,0,n); return o.toByteArray(); }
        finally{c.disconnect();}
    }
}
