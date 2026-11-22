package com.jonathancruzate.museambient;
import android.service.dreams.DreamService;
public class AmbientDreamService extends DreamService {
    private AmbientView view;
    @Override public void onAttachedToWindow(){ super.onAttachedToWindow(); setInteractive(false); setFullscreen(true); view=new AmbientView(this); setContentView(view); }
    @Override public void onDreamingStarted(){ super.onDreamingStarted(); if(view!=null)view.start(); }
    @Override public void onDreamingStopped(){ if(view!=null)view.stop(); super.onDreamingStopped(); }
}
