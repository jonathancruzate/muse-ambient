package com.jonathancruzate.museambient;
import android.app.Activity; import android.content.*; import android.graphics.Color; import android.os.Bundle; import android.view.*; import android.widget.*;
import java.lang.reflect.Method;
/** Leanback launcher activity: preview (top 60%) + simple settings (bottom, scrollable). */
public class MainActivity extends Activity {
    private AmbientView preview; private EditText artUrl, museUrl, interval, museEvery;
    private CheckBox cbClock, cbWeather, cbLabel, cbDim, cbMuseCards, cbMuseMsg;
    private RadioButton rbDash, rbArt, rbRotate;
    @Override protected void onCreate(Bundle s){ super.onCreate(s);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        preview=new AmbientView(this); root.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,3f));
        ScrollView sv=new ScrollView(this); LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(32,16,32,16);
        artUrl=field("Art feed JSON URL (empty = bundled artworks)"); artUrl.setText(Prefs.artFeedUrl(this));
        museUrl=field("Muse feed JSON URL (empty = sample/demo mode)"); museUrl.setText(Prefs.museFeedUrl(this));
        interval=field("Seconds per slide"); interval.setText(String.valueOf(Prefs.intervalSec(this))); interval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        museEvery=field("Show Muse card every N slides"); museEvery.setText(String.valueOf(Prefs.museEvery(this))); museEvery.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(artUrl); form.addView(museUrl); form.addView(interval); form.addView(museEvery);
        TextView modeLabel=new TextView(this); modeLabel.setText("Display mode"); modeLabel.setTextColor(Color.WHITE); form.addView(modeLabel);
        RadioGroup modeGroup=new RadioGroup(this); modeGroup.setOrientation(LinearLayout.HORIZONTAL);
        rbDash=radio("Dashboard"); rbArt=radio("Art slideshow"); rbRotate=radio("Rotate both");
        modeGroup.addView(rbDash); modeGroup.addView(rbArt); modeGroup.addView(rbRotate);
        String dm=Prefs.displayMode(this); if("art".equals(dm))rbArt.setChecked(true); else if("rotate".equals(dm))rbRotate.setChecked(true); else rbDash.setChecked(true);
        form.addView(modeGroup);
        cbClock=check("Clock + date overlay",Prefs.showClock(this)); cbWeather=check("Weather (Astoria, Open-Meteo)",Prefs.showWeather(this)); cbLabel=check("Museum wall label",Prefs.showLabel(this)); cbDim=check("Night dimming 23:00-07:00",Prefs.nightDim(this)); cbMuseCards=check("Muse Home / Daily Brief cards",Prefs.museCards(this)); cbMuseMsg=check("Muse message overlay",Prefs.museMessages(this));
        form.addView(cbClock); form.addView(cbWeather); form.addView(cbLabel); form.addView(cbDim); form.addView(cbMuseCards); form.addView(cbMuseMsg);
        Button save=new Button(this); save.setText("Save settings"); save.setOnClickListener(new View.OnClickListener(){public void onClick(View v){save();}}); form.addView(save);
        Button start=new Button(this); start.setText(getString(R.string.start_dream)); start.setOnClickListener(new View.OnClickListener(){public void onClick(View v){startDream();}}); form.addView(start);
        TextView hint=new TextView(this); hint.setText(getString(R.string.settings_hint)); hint.setTextColor(Color.LTGRAY); form.addView(hint);
        sv.addView(form); root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,2f));
        setContentView(root);
    }
    private EditText field(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setSingleLine(true); return e; }
    private CheckBox check(String t,boolean v){ CheckBox c=new CheckBox(this); c.setText(t); c.setTextColor(Color.WHITE); c.setChecked(v); return c; }
    private RadioButton radio(String t){ RadioButton r=new RadioButton(this); r.setText(t); r.setTextColor(Color.WHITE); return r; }
    private void save(){ SharedPreferences.Editor e=Prefs.get(this).edit(); e.putString("art_feed_url",artUrl.getText().toString().trim()); e.putString("muse_feed_url",museUrl.getText().toString().trim());
        try{e.putInt("interval_sec",Integer.parseInt(interval.getText().toString()));}catch(Exception ignored){}
        try{e.putInt("muse_every",Math.max(2,Integer.parseInt(museEvery.getText().toString())));}catch(Exception ignored){}
        e.putBoolean("show_clock",cbClock.isChecked()); e.putBoolean("show_weather",cbWeather.isChecked()); e.putBoolean("show_label",cbLabel.isChecked()); e.putBoolean("night_dim",cbDim.isChecked()); e.putBoolean("muse_cards",cbMuseCards.isChecked()); e.putBoolean("muse_messages",cbMuseMsg.isChecked());
        e.putString("display_mode",rbArt.isChecked()?"art":rbRotate.isChecked()?"rotate":"dashboard"); e.apply();
        Toast.makeText(this,"Saved. Restart preview to apply.",Toast.LENGTH_SHORT).show(); preview.stop(); preview.start(); }
    private void startDream(){
        // DreamManager is not a public SDK API; try it reflectively, fall back to the Settings path in the hint text.
        try{ Object dm=getSystemService("dream"); Method m=dm.getClass().getMethod("startDreaming",boolean.class); m.invoke(dm,false); return; }catch(Exception ignored){}
        try{ startActivity(new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)); }catch(Exception e){ Toast.makeText(this,getString(R.string.settings_hint),Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onResume(){ super.onResume(); preview.start(); preview.requestFocus(); }
    @Override protected void onPause(){ preview.stop(); super.onPause(); }
    @Override public boolean onKeyDown(int k, KeyEvent e){ if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){ preview.onKeyDown(k,e); return true; } return super.onKeyDown(k,e); }
}
