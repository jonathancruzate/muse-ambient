package com.jonathancruzate.museambient;
import org.json.JSONObject;
/** Open-Meteo, no API key, Astoria NY. Fail-silent. */
public class WeatherClient {
    public static final String URL="https://api.open-meteo.com/v1/forecast?latitude=40.77&longitude=-73.91&current=temperature_2m,weather_code&temperature_unit=fahrenheit";
    public static String fetch(){
        try{ JSONObject j=new JSONObject(HttpUtil.getString(URL)); JSONObject c=j.getJSONObject("current");
            int t=(int)Math.round(c.getDouble("temperature_2m")); int code=c.optInt("weather_code",0);
            // Art-slide format per the v0.2.2 design pass: "66\u00B0 \u00B7 Partly cloudy".
            // The city name is dropped on art slides (it inflated the line's
            // footprint); the feed is fixed to Astoria, so no information is lost.
            return t+"\u00B0 \u00B7 "+label(code);
        }catch(Exception e){return null;}
    }
    static String label(int c){ if(c==0)return "Clear"; if(c<=3)return "Partly cloudy"; if(c==45||c==48)return "Fog"; if(c>=51&&c<=57)return "Drizzle"; if(c>=61&&c<=67)return "Rain"; if(c>=71&&c<=77)return "Snow"; if(c>=80&&c<=82)return "Showers"; if(c>=95)return "Thunderstorm"; return ""; }
}
