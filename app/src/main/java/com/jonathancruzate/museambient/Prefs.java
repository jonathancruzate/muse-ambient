package com.jonathancruzate.museambient;
import android.content.*;
public class Prefs {
    public static final String NAME="muse_ambient";
    // TODO: replace with a real hosted playlist URL when one exists.
    public static final String DEFAULT_ART_FEED_URL="";
    public static SharedPreferences get(Context c){return c.getSharedPreferences(NAME,Context.MODE_PRIVATE);}
    public static String artFeedUrl(Context c){return get(c).getString("art_feed_url",DEFAULT_ART_FEED_URL);}
    public static String museFeedUrl(Context c){return get(c).getString("muse_feed_url","");}
    public static int intervalSec(Context c){return get(c).getInt("interval_sec",60);}
    public static int museEvery(Context c){return get(c).getInt("muse_every",4);}
    public static boolean showClock(Context c){return get(c).getBoolean("show_clock",true);}
    public static boolean showWeather(Context c){return get(c).getBoolean("show_weather",true);}
    public static boolean showLabel(Context c){return get(c).getBoolean("show_label",true);}
    public static boolean nightDim(Context c){return get(c).getBoolean("night_dim",true);}
    public static boolean museCards(Context c){return get(c).getBoolean("muse_cards",true);}
    public static boolean museMessages(Context c){return get(c).getBoolean("muse_messages",true);}
    /** Dream face (v0.3.0): "dashboard" (default), "art" (the v0.2.2 slideshow), or "rotate" (alternate the two). */
    public static String displayMode(Context c){return get(c).getString("display_mode","dashboard");}
}
