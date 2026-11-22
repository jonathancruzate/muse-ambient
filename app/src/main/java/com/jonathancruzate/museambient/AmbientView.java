package com.jonathancruzate.museambient;
import android.animation.*; import android.content.Context; import android.graphics.*; import android.os.Handler; import android.os.Looper; import android.text.SpannableString; import android.text.Spanned; import android.text.style.AbsoluteSizeSpan; import android.text.style.ForegroundColorSpan; import android.view.*; import android.view.animation.DecelerateInterpolator; import android.widget.*;
import java.io.InputStream; import java.text.SimpleDateFormat; import java.util.*;
/**
 * Shared slideshow engine for the DreamService and the MainActivity preview.
 * Rotation: art slides, with a Muse card every museEvery-th slide (default 4th).
 * Muse card variants alternate Home / Daily Brief when brief data exists.
 * A Muse message appears on the Muse card and once as a signed note on
 * the next art slide. The "Muse" wordmark appears on Muse cards and in
 * the note's sign-off only, never as a standing overlay on art.
 *
 * Rendering rules (matte art frame, not a TV picture):
 * - Artwork is decoded at up to 3840 wide and finished with ImageLoader.matte
 *   plus the MatteOverlay grain/vignette. The art is never blurred (only the
 *   fill behind it is) and never cropped beyond the few-percent CENTER_CROP
 *   remainder of the 16:9 curation.
 * - THE ART IS PERFECTLY STILL (v0.2.2, Jonathan's spec: "staying still
 *   like an art piece"). No zoom, pan, drift, or scale animation on the
 *   artwork, ever. There is no Ken Burns code path; the only motion in the
 *   frame is the gentle crossfade between slides, the overlays' few-pixel
 *   micro-shift between slides (burn-in hygiene on the text only; this is
 *   an LCD QLED and slides rotate every ~60 s), and opacity fades.
 * - OVERLAY ZONES (Jonathan's rules): one element per zone, nothing shares
 *   a band. Top-left: clock + date + weather, one compact block.
 *   Bottom-right: the wall label only, RIGHT-aligned to the corner.
 *   Bottom-left: the Muse signed note only, when the feed has a message,
 *   wrapped inside its corner (max width 36 percent of frame).
 *   Top-right: reserved for live feed ALERTS only (transient, one at a
 *   time); nothing stands there. Muse cards replace the art, so they
 *   cannot collide with any of this.
 * - VISUAL HARMONY (Jonathan, v0.2.2 final): ONE typeface for ALL overlay
 *   text, a cursive face (bundled Caveat, SIL OFL; stock "cursive" as
 *   fallback). ONE ink tone per slide for ALL text, reactively sampled
 *   from that slide's painting (see computeInk): hue from the whole
 *   painting's palette, polarity/lightness chosen by worst-corner
 *   contrast scoring, one shared opacity and one shared polarity-aware
 *   soft shadow. Per slide every element shares font and colour; across
 *   slides the colour adapts to the painting.
 * - Type scale, opacity, shadow, scrim and geometry parameters follow the
 *   Gemini design consultation of 2026-11-21 (see BUILD-REPORT v0.2.2
 *   for the adoption list, the density math, and what was approximated).
 *   Sizes below are sp on the Shield (density 320, UI grid 1920x1080
 *   upscaled x2 to the 2160p panel): sp = consult panel px / 4.
 * - Playlist is curated to the panel: only 16:9 (plus/minus 5 percent)
 *   pieces, enforced again in ImageLoader, and in-tolerance art fills the
 *   frame exactly (CENTER_CROP, at most a few percent cropped).
 * - DASHBOARD MODE (v0.3.0, the default dream face): the room already has
 *   a real painting, so the TV's calm face is a home dashboard laid over
 *   a STATIC generated backdrop in that painting's own palette (warm
 *   white/greige field, taupe/camel masses, slate blue-grey, muted teal;
 *   assets/dashboard-bg.jpg, see dashboard-bg/BG-NOTES.md). The backdrop
 *   never moves and never rotates; it gets the same matte finish and its
 *   own sampled ink (computeInk over the backdrop), and the content is
 *   the Muse feed rendered broadside: clock + date + weather, the house
 *   mode as a quiet hero line, a single ledger (home / dog / next /
 *   playing / note, empty rows omitted), the Daily Brief as a stanza,
 *   and feed alerts top-right. Content refreshes on the feed poll and a
 *   20 s clock tick; the pixels underneath never change. Art mode above
 *   remains as a settings choice (Prefs.displayMode: dashboard / art /
 *   rotate, rotate alternating the two faces per interval).
 */
public class AmbientView extends FrameLayout {
    // ---- type scale (sp; consult panel px in comments) ----
    private static final float CLOCK_SP=21f;        // 84px
    private static final float CLOCK_SUFFIX_SP=11f; // 44px, lowercase pm
    private static final float DATE_SP=10.5f;       // 42px
    private static final float WEATHER_SP=9.5f;     // 38px
    private static final float LABEL_TITLE_SP=11f;  // 44px
    private static final float LABEL_ARTIST_SP=9f;  // 36px
    private static final float LABEL_COLL_SP=8f;    // 32px = hard floor
    private static final float NOTE_SP=11.5f;       // 46px
    private static final float NOTE_SIGN_SP=9f;     // 36px
    private static final float ALERT_SP=11f;        // 44px
    private static final float HERO_SP=27f;         // 108px card header
    private static final float CARD_META_SP=10.5f;  // 42px
    private static final float LEDGER_LABEL_SP=9.5f;// 38px
    private static final float LEDGER_VALUE_SP=14.5f;// 58px
    private static final float BRIEF_TITLE_SP=26f;  // 104px
    private static final float BRIEF_LINE_SP=14f;   // 56px
    private static final float CARD_SIGN_SP=10f;    // 40px
    private static final float DASH_BRIEF_TITLE_SP=16f; // 64px, dashboard stanza heading
    // Dashboard-only calibration (v0.3.1): one linear RGB scale applied
    // only to the static plaster backdrop, never to dashboard ink. The
    // Gemini dashboard consult named this wall_luminance_scale (0.70-1.05)
    // but v0.3.0 did not implement it. The in-room Wyze A/B (2026-11-21)
    // kept 0.80: 0.70 was tried and rejected because the camera-frame
    // panel-to-wall ratio did not improve past 0.80 while the lower
    // backdrop cost ink contrast and showed stronger banding.
    private static final float WALL_LUMINANCE_SCALE=0.80f;

    private final ImageView bg, fg, cardBg, dashBg; private final MatteOverlay matteOverlay; private final ScrimView scrimView;
    private final LinearLayout dashRoot, dashContent, dashClockBlock;
    private final TextView dashClockTv, dashSuffixTv, dashDateTv, dashWeatherTv;
    private final DashboardView dashboardView;
    private boolean dashShowing=false, dashTurn=true; private Bitmap dashBitmap=null; private Ink dashInk=null;
    private final LinearLayout clockBlock, labelBlock, noteBlock, alertBlock, cardRoot;
    private final TextView clockTv, suffixTv, dateTv, weatherTv, labelTitleTv, labelArtistTv, labelCollTv, msgTv, signTv, alertTv;
    private final View dim;
    private final Handler h=new Handler(Looper.getMainLooper()); private final Random rnd=new Random();
    private List<ArtItem> items=new ArrayList<>(); private int index=0, slideCount=0; private boolean running=false, museBriefNext=false, cardShowing=false;
    private String weather=null, shownMessageKey=null; private MuseFeed muse=null; private Bitmap lastBitmap=null;
    private final ImageLoader loader; private long lastWeather=0, lastMuse=0;
    private ObjectAnimator fgAlphaAnim, msgAnim, alertAnim, cardAnim, cardBgAnim;
    // Current slide ink (set by computeInk on every art slide).
    private int inkRgb=0xF5F0E6, deepRgb=0x141210, highRgb=0xF2EDE2; private boolean inkDark=false;
    private int inkShadow=0x750F0D0B; private float inkShadowDy=1.5f, inkShadowR=6f;
    // Alert engine state.
    private final ArrayDeque<String> alertQueue=new ArrayDeque<>(); private final HashMap<String,Long> alertShownAt=new HashMap<>();
    private boolean alertBusy=false; private String currentAlert=null;

    // ---- the one typeface: bundled Caveat (SIL OFL 1.1, see assets/fonts/OFL.txt) ----
    private static Typeface faceReg=null, faceMed=null;
    static Typeface face(Context c, boolean med){
        if(faceReg==null){
            Typeface r=null, m=null;
            try{ r=Typeface.createFromAsset(c.getAssets(),"fonts/Caveat-Regular.ttf"); }catch(Exception ignored){}
            try{ m=Typeface.createFromAsset(c.getAssets(),"fonts/Caveat-Medium.ttf"); }catch(Exception ignored){}
            if(r==null){ r=Typeface.create("cursive",Typeface.NORMAL); } // stock fallback
            if(m==null){ m=r; }
            faceReg=r; faceMed=m;
        }
        return med?faceMed:faceReg;
    }

    public AmbientView(Context c){
        super(c); setBackgroundColor(Color.BLACK); loader=new ImageLoader(c);
        float den=c.getResources().getDisplayMetrics().density;
        int frameW=c.getResources().getDisplayMetrics().widthPixels;
        int mx=(int)(36*den), my=(int)(30*den); // gallery inner frame: 144px L/R, 120px T/B at 2160p
        bg=new ImageView(c); bg.setScaleType(ImageView.ScaleType.CENTER_CROP); addView(bg,full());
        fg=new ImageView(c); fg.setScaleType(ImageView.ScaleType.CENTER_CROP); addView(fg,full()); // curated 16:9 art fills the frame; bg blurred fill remains only as the crossfade backdrop
        dashBg=new ImageView(c); dashBg.setScaleType(ImageView.ScaleType.CENTER_CROP); dashBg.setVisibility(GONE); addView(dashBg,full()); // dashboard's static backdrop, above the art layers, below the matte overlay
        matteOverlay=new MatteOverlay(c); addView(matteOverlay,full());
        scrimView=new ScrimView(c); addView(scrimView,full());
        cardBg=new ImageView(c); cardBg.setScaleType(ImageView.ScaleType.CENTER_CROP); cardBg.setVisibility(GONE); cardBg.setAlpha(0f); addView(cardBg,full());
        cardRoot=new LinearLayout(c); cardRoot.setOrientation(LinearLayout.VERTICAL);
        cardRoot.setPadding((int)(90*den),(int)(55*den),(int)(90*den),(int)(55*den)); // 360px / 220px at 2160p
        cardRoot.setVisibility(GONE); cardRoot.setAlpha(0f); addView(cardRoot,full());
        // Dashboard root (v0.3.0): clock block on top, dynamic content below,
        // all inside the gallery inner frame. Hidden unless the dashboard
        // face is showing; the alert block and the night dim sit above it.
        dashRoot=new LinearLayout(c); dashRoot.setOrientation(LinearLayout.VERTICAL);
        dashRoot.setPadding(mx,my,mx,my);
        dashRoot.setVisibility(GONE); dashRoot.setAlpha(0f); addView(dashRoot,full());
        dashClockBlock=new LinearLayout(c); dashClockBlock.setOrientation(LinearLayout.VERTICAL);
        dashClockTv=tv(c,CLOCK_SP,true); dashSuffixTv=tv(c,CLOCK_SUFFIX_SP,false); dashDateTv=tv(c,DATE_SP,false); dashWeatherTv=tv(c,WEATHER_SP,false);
        LinearLayout dashClockRow=new LinearLayout(c); dashClockRow.setOrientation(LinearLayout.HORIZONTAL);
        dashClockRow.addView(dashClockTv);
        LinearLayout.LayoutParams dsf=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); dsf.leftMargin=(int)(2.5f*den); // 10px panel gap
        dashClockRow.addView(dashSuffixTv,dsf);
        dashClockBlock.addView(dashClockRow);
        LinearLayout.LayoutParams ddp1=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); ddp1.topMargin=(int)(3*den); // 12px panel gap
        dashClockBlock.addView(dashDateTv,ddp1);
        LinearLayout.LayoutParams ddp2=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); ddp2.topMargin=(int)(1.5f*den); // 6px panel gap
        dashClockBlock.addView(dashWeatherTv,ddp2);
        dashDateTv.setMaxWidth((int)(frameW*0.26f)); dashWeatherTv.setMaxWidth((int)(frameW*0.26f));
        dashRoot.addView(dashClockBlock);
        dashContent=new LinearLayout(c); dashContent.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams dcp=new LinearLayout.LayoutParams((int)(frameW*0.58f),LayoutParams.WRAP_CONTENT); dcp.topMargin=(int)(10*den); // content column starts below the clock block
        dashRoot.addView(dashContent,dcp);
        // Top-left: clock + date + weather, one compact block.
        clockBlock=new LinearLayout(c); clockBlock.setOrientation(LinearLayout.VERTICAL);
        clockTv=tv(c,CLOCK_SP,true); suffixTv=tv(c,CLOCK_SUFFIX_SP,false); dateTv=tv(c,DATE_SP,false); weatherTv=tv(c,WEATHER_SP,false);
        // Clock line = digits + a smaller lowercase suffix in a horizontal
        // row (two plain TextViews, baseline-aligned by the LinearLayout).
        // Deliberately NOT one TextView with an AbsoluteSizeSpan: a spanned
        // (non-"boring") layout built at setText time reuses the view's
        // stale narrow measured width and clips everything past the first
        // token (found on device in the v0.2.2 final pass: the " pm" suffix
        // vanished and the note below collapsed to its first word).
        LinearLayout clockRow=new LinearLayout(c); clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.addView(clockTv);
        LinearLayout.LayoutParams sfp=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); sfp.leftMargin=(int)(2.5f*den); // 10px panel gap
        clockRow.addView(suffixTv,sfp);
        clockBlock.addView(clockRow);
        LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); dp1.topMargin=(int)(3*den); // 12px panel gap
        clockBlock.addView(dateTv,dp1);
        LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); dp2.topMargin=(int)(1.5f*den); // 6px panel gap
        clockBlock.addView(weatherTv,dp2);
        dateTv.setMaxWidth((int)(frameW*0.26f)); weatherTv.setMaxWidth((int)(frameW*0.26f));
        LayoutParams cp=new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.LEFT); cp.setMargins(mx,my,0,0); clockBlock.setLayoutParams(cp); addView(clockBlock);
        // Bottom-right: wall label only, RIGHT-aligned to the corner (fixes the ragged edge).
        labelBlock=new LinearLayout(c); labelBlock.setOrientation(LinearLayout.VERTICAL); labelBlock.setGravity(Gravity.RIGHT);
        labelTitleTv=tv(c,LABEL_TITLE_SP,true); labelTitleTv.setGravity(Gravity.RIGHT); labelTitleTv.setMaxLines(2); labelTitleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labelArtistTv=tv(c,LABEL_ARTIST_SP,false); labelArtistTv.setGravity(Gravity.RIGHT);
        labelCollTv=tv(c,LABEL_COLL_SP,false); labelCollTv.setGravity(Gravity.RIGHT);
        int labelMax=(int)(frameW*0.34f); labelTitleTv.setMaxWidth(labelMax); labelArtistTv.setMaxWidth(labelMax); labelCollTv.setMaxWidth(labelMax);
        labelBlock.addView(labelTitleTv);
        LinearLayout.LayoutParams lp1=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); lp1.topMargin=(int)(2.5f*den); lp1.gravity=Gravity.RIGHT; // 10px panel gap
        labelBlock.addView(labelArtistTv,lp1);
        LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); lp2.topMargin=(int)(1.5f*den); lp2.gravity=Gravity.RIGHT; // 6px panel gap
        labelBlock.addView(labelCollTv,lp2);
        LayoutParams bp=new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,Gravity.BOTTOM|Gravity.RIGHT); bp.setMargins(0,0,mx,my); labelBlock.setLayoutParams(bp); addView(labelBlock);
        // Bottom-left: the Muse signed note only, when the feed has a message.
        noteBlock=new LinearLayout(c); noteBlock.setOrientation(LinearLayout.VERTICAL);
        msgTv=tv(c,NOTE_SP,false); msgTv.setMaxLines(2); msgTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        msgTv.setLineSpacing(0f,1.32f);
        // No BREAK_STRATEGY_BALANCED here: like the clock's size span, it
        // makes the layout non-"boring" and the setText-time layout then
        // sticks at the view's stale narrow width (on device, the note
        // rendered only its first word). Default breaking + an EXACT
        // layout width (36 percent of frame, below) keep it honest.
        signTv=tv(c,NOTE_SIGN_SP,true); signTv.setText("\u2014 Muse"); signTv.setPadding((int)(7*den),0,0,0); // 28px panel indent
        noteBlock.addView(msgTv,new LinearLayout.LayoutParams((int)(frameW*0.36f),LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams np1=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); np1.topMargin=(int)(4*den); // 16px panel gap
        noteBlock.addView(signTv,np1);
        noteBlock.setAlpha(0f); noteBlock.setVisibility(GONE);
        LayoutParams np=new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,Gravity.BOTTOM|Gravity.LEFT); np.setMargins(mx,0,0,my); noteBlock.setLayoutParams(np); addView(noteBlock);
        // Top-right: live feed alerts only. Nothing stands here.
        alertBlock=new LinearLayout(c); alertBlock.setOrientation(LinearLayout.VERTICAL); alertBlock.setGravity(Gravity.RIGHT);
        alertTv=tv(c,ALERT_SP,true); alertTv.setGravity(Gravity.RIGHT); alertTv.setMaxWidth((int)(frameW*0.28f)); alertTv.setMaxLines(2); alertTv.setEllipsize(android.text.TextUtils.TruncateAt.END); alertTv.setLineSpacing(0f,1.22f);
        alertBlock.addView(alertTv); alertBlock.setAlpha(0f); alertBlock.setVisibility(GONE);
        LayoutParams ap=new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.RIGHT); ap.setMargins(0,my,mx,0); alertBlock.setLayoutParams(ap); addView(alertBlock);
        // v0.3 editorial dashboard. It sits above the legacy dashboard
        // scaffolding and the art alert corner; its own notification engine
        // is used while this face is showing.
        dashboardView=new DashboardView(c); dashboardView.setVisibility(GONE); addView(dashboardView,full());
        dim=new View(c); dim.setBackgroundColor(Color.BLACK); dim.setAlpha(0f); addView(dim,full());
        applyFallbackInk();
        setFocusable(true);
    }
    private LayoutParams full(){return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT);}
    private TextView tv(Context c,float sp,boolean med){ TextView t=new TextView(c); t.setTypeface(face(c,med)); t.setTextSize(sp); t.setTextColor(0xCCF5F0E6); t.setShadowLayer(inkShadowR,0,inkShadowDy,inkShadow); return t; }
    private int spPx(float sp){ return Math.round(sp*getResources().getDisplayMetrics().scaledDensity); }
    private void applyFallbackInk(){ int ink=Color.argb(184,245,240,230); clockTv.setTextColor(ink); suffixTv.setTextColor(ink); dateTv.setTextColor(ink); weatherTv.setTextColor(ink); labelTitleTv.setTextColor(ink); labelArtistTv.setTextColor(ink); labelCollTv.setTextColor(ink); msgTv.setTextColor(ink); signTv.setTextColor(ink); alertTv.setTextColor(Color.argb(209,245,240,230)); }

    public void start(){ if(running)return; running=true; new Thread(new Runnable(){public void run(){ items=new FeedLoader(getContext()).load(); refreshMuse(true); refreshWeather(true); h.post(new Runnable(){public void run(){ beginFace(); }}); }}).start();
        h.postDelayed(weatherLoop,60000); h.postDelayed(museLoop,60000); h.postDelayed(dashTick,20000); }
    /** Pick the opening face from the display-mode setting. */
    private void beginFace(){
        String mode=Prefs.displayMode(getContext());
        if("art".equals(mode)){ h.post(next); }
        else if("rotate".equals(mode)){ dashTurn=true; h.post(next); }
        else { showDashboard(); }
    }
    /** Dashboard clock tick: the backdrop never changes, the clock does. */
    private final Runnable dashTick=new Runnable(){public void run(){ if(!running)return; if(dashShowing)updateClock(); h.postDelayed(this,20000); }};
    public void stop(){ running=false; h.removeCallbacksAndMessages(null); if(dashboardView!=null)dashboardView.stop(); cancelFgAnims(); if(msgAnim!=null)msgAnim.cancel(); if(alertAnim!=null)alertAnim.cancel(); alertBusy=false; }
    private final Runnable weatherLoop=new Runnable(){public void run(){ if(!running)return; refreshWeather(false); h.postDelayed(this,3600000); }};
    private final Runnable museLoop=new Runnable(){public void run(){ if(!running)return; refreshMuse(false); h.postDelayed(this,300000); }}; // poll Muse feed every 5 min
    private void refreshWeather(boolean force){ if(!Prefs.showWeather(getContext()))return; if(!force&&System.currentTimeMillis()-lastWeather<3600000)return; lastWeather=System.currentTimeMillis(); new Thread(new Runnable(){public void run(){ String w=WeatherClient.fetch(); if(w!=null){ weather=w; h.post(new Runnable(){public void run(){ if(dashShowing&&dashboardView!=null)dashboardView.setWeather(weather); updateClock(); }}); } }}).start(); }
    private void refreshMuse(boolean force){ if(!force&&System.currentTimeMillis()-lastMuse<300000)return; lastMuse=System.currentTimeMillis(); new Thread(new Runnable(){public void run(){ muse=new MuseFeedLoader(getContext()).load(); h.post(new Runnable(){public void run(){ maybeQueueAlerts(); if(dashShowing)rebuildDashboard(); }}); }}).start(); }

    public void skip(int dir){ if(dashShowing||items.isEmpty())return; index=(index+dir+items.size())%items.size(); h.removeCallbacks(next); showArt(); }

    private final Runnable next=new Runnable(){public void run(){ if(!running)return; advance(); }};
    private void schedule(){ h.removeCallbacks(next); h.postDelayed(next,Math.max(10,Prefs.intervalSec(getContext()))*1000L); }
    private void advance(){
        if("rotate".equals(Prefs.displayMode(getContext()))){
            // Alternate faces per interval: dashboard, then one art slide.
            if(dashTurn){ dashTurn=false; showDashboard(); }
            else { dashTurn=true; if(items.isEmpty()){ showDashboard(); } else { index=(index+1)%items.size(); showArt(); } }
            schedule(); return;
        }
        slideCount++;
        boolean museSlot = Prefs.museCards(getContext()) && muse!=null && Prefs.museEvery(getContext())>0 && (slideCount % Prefs.museEvery(getContext())==0);
        if(museSlot){ showMuseCard(); }
        else { if(items.isEmpty()){ schedule(); return; } index=(index+1)%items.size(); showArt(); }
        schedule();
    }
    private void showArt(){
        hideDashboard();
        fadeOutCard();
        final ArtItem item=items.get(index);
        new Thread(new Runnable(){public void run(){ Bitmap raw=loader.load(item.imageUrl); if(raw==null){ h.post(new Runnable(){public void run(){ if(running){ index=(index+1)%items.size(); showArt(); } }}); return; }
            final Bitmap matte=ImageLoader.matte(raw); if(raw!=matte)raw.recycle();
            final Bitmap blurred=ImageLoader.blur(matte);
            final Ink ink=computeInk(matte);
            h.post(new Runnable(){public void run(){ if(!running)return; lastBitmap=matte; crossfade(blurred,matte);
                applyInk(ink); // one reactive tone for ALL text on this slide
                shiftOverlays();
                if(Prefs.showLabel(getContext())){
                    labelBlock.setVisibility(VISIBLE);
                    labelTitleTv.setText(item.title);
                    labelArtistTv.setText(item.artist+(item.year.isEmpty()?"":" \u00B7 "+item.year));
                    if(item.collection.isEmpty())labelCollTv.setVisibility(GONE); else { labelCollTv.setVisibility(VISIBLE); labelCollTv.setText(item.collection); }
                } else labelBlock.setVisibility(GONE);
                updateClock(); applyDim();
                // Muse message: evaluated HERE, after the slide is on
                // screen, because the feed load races the first slide.
                // Once per message, as a signed note that fades in, holds
                // for the slide, and fades out at the next change. Silence
                // rule: no message in the feed = the corner stays empty.
                if(Prefs.museMessages(getContext())&&muse!=null&&muse.messageActive()){
                    String key=muse.messageText+"|"+muse.messageExpiresAt;
                    if(!key.equals(shownMessageKey)){ shownMessageKey=key; fadeInMessage(muse.messageText); }
                    else fadeOutMessage();
                } else fadeOutMessage();
                maybeQueueAlerts();
            }}); }}).start();
    }

    // --- fg animation: alpha crossfade ONLY. The art is perfectly still:
    // no scale, translation, zoom, pan, or drift animators exist (v0.2.2).
    private void cancelFgAnims(){ if(fgAlphaAnim!=null)fgAlphaAnim.cancel(); }
    private void crossfade(Bitmap blurred, Bitmap bmp){
        bg.setImageBitmap(blurred);
        cancelFgAnims();
        fg.setScaleX(1f); fg.setScaleY(1f); fg.setTranslationX(0); fg.setTranslationY(0);
        fg.setImageBitmap(bmp); fg.setAlpha(0f);
        fgAlphaAnim=ObjectAnimator.ofFloat(fg,"alpha",0f,1f); fgAlphaAnim.setDuration(1200); fgAlphaAnim.start();
        // Nothing else animates the artwork. It hangs still for the slide.
    }

    // ================= Dashboard face (v0.3.0) =================
    // A static backdrop in the room painting's palette, matte-finished and
    // ink-sampled exactly like an art slide, but decoded once and never
    // rotated, crossfaded, or shifted. Only the text on it is live.
    private void showDashboard(){
        fadeOutCard(); fadeOutMessage(); hideAlertNow();
        dashShowing=true;
        clockBlock.setVisibility(GONE); labelBlock.setVisibility(GONE); noteBlock.setVisibility(GONE);
        fg.setVisibility(GONE); bg.setVisibility(GONE);
        matteOverlay.setVisibility(GONE); scrimView.setVisibility(GONE); // the dashboard spec allows no scrim or vignette
        dashRoot.setVisibility(GONE);
        dim.setAlpha(0f); // DashboardView owns the dusk ramp and nocturnal inversion.
        if(dashBitmap!=null){ presentDashboard(); return; }
        new Thread(new Runnable(){public void run(){
            Bitmap raw=null;
            try{ InputStream in=getContext().getAssets().open("dashboard-bg.jpg"); raw=BitmapFactory.decodeStream(in); in.close(); }catch(Exception ignored){}
            final Bitmap decoded=raw;
            h.post(new Runnable(){public void run(){ dashBitmap=decoded; presentDashboard(); }});
        }}).start();
    }
    private void applyWallLuminanceScale(){
        ColorMatrix cm=new ColorMatrix(new float[]{
            WALL_LUMINANCE_SCALE,0,0,0,0,
            0,WALL_LUMINANCE_SCALE,0,0,0,
            0,0,WALL_LUMINANCE_SCALE,0,0,
            0,0,0,1,0
        });
        dashBg.setColorFilter(new ColorMatrixColorFilter(cm));
    }
    private void presentDashboard(){
        if(!dashShowing)return;
        applyWallLuminanceScale();
        if(dashBitmap!=null){ dashBg.setImageBitmap(dashBitmap); dashBg.setVisibility(VISIBLE); }
        else { dashBg.setImageBitmap(null); dashBg.setBackgroundColor(0xFFB1AAA1); dashBg.setVisibility(VISIBLE); } // #DDD5C9 at the same WALL_LUMINANCE_SCALE (0.80)
        dashRoot.setVisibility(GONE);
        dashboardView.setAlpha(0f); dashboardView.setVisibility(VISIBLE);
        dashboardView.onShow(muse,weather,!Prefs.museFeedUrl(getContext()).trim().isEmpty(),Prefs.museMessages(getContext()),Prefs.showClock(getContext()),Prefs.showWeather(getContext()));
        ObjectAnimator a=ObjectAnimator.ofFloat(dashboardView,"alpha",0f,1f); a.setDuration(900); a.start();
    }
    private void hideDashboard(){
        if(!dashShowing)return; dashShowing=false;
        dashboardView.onHide(); dashboardView.setVisibility(GONE);
        dashRoot.setVisibility(GONE); dashRoot.setAlpha(0f); dashBg.setVisibility(GONE);
        matteOverlay.setVisibility(VISIBLE); scrimView.setVisibility(VISIBLE);
        fg.setVisibility(VISIBLE); bg.setVisibility(VISIBLE);
    }
    /** The backdrop's one sampled tone, applied to every dashboard text
     *  element (and shared with the alert corner, as on art slides). */
    private void applyDashInk(Ink ink){
        inkRgb=ink.rgb; deepRgb=ink.deepRgb; highRgb=ink.highRgb; inkDark=ink.dark;
        inkShadow=ink.shadowColor; inkShadowDy=ink.shadowDy; inkShadowR=ink.shadowR;
        int prim=Color.argb(219,Color.red(ink.rgb),Color.green(ink.rgb),Color.blue(ink.rgb)); // 0.86, the card primary
        int sec=Color.argb(184,Color.red(ink.rgb),Color.green(ink.rgb),Color.blue(ink.rgb));  // 0.72 base
        TextView[] p={dashClockTv,dashSuffixTv};
        for(TextView t: p){ t.setTextColor(prim); t.setShadowLayer(ink.shadowR,0,ink.shadowDy,ink.shadowColor); }
        TextView[] s={dashDateTv,dashWeatherTv};
        for(TextView t: s){ t.setTextColor(sec); t.setShadowLayer(ink.shadowR,0,ink.shadowDy,ink.shadowColor); }
        alertTv.setTextColor(Color.argb(209,Color.red(ink.rgb),Color.green(ink.rgb),Color.blue(ink.rgb))); // alerts peak at 0.82
        alertTv.setShadowLayer(ink.shadowR,0,ink.shadowDy,ink.shadowColor);
        scrimView.setScrims(ink.scrim,ink.scrimRgb);
    }
    /** Delegate dashboard rendering to the v0.3 editorial DashboardView. */
    private void rebuildDashboard(){
        if(dashboardView==null||!dashShowing)return;
        dashboardView.setFeed(muse,weather,!Prefs.museFeedUrl(getContext()).trim().isEmpty(),Prefs.museMessages(getContext()),Prefs.showClock(getContext()),Prefs.showWeather(getContext()));
    }

    // ================= reactive ink (one tone per slide, for ALL text) =================
    /** Result of sampling one slide. All text on the slide shares this. */
    private static class Ink {
        int rgb; boolean dark; float baseAlpha; int shadowColor; float shadowDy, shadowR;
        float[] scrim=new float[4]; int scrimRgb; // corners: 0 TL, 1 TR, 2 BL, 3 BR
        int deepRgb, highRgb;
    }
    private static float linY(int r,int g,int b){ // WCAG relative luminance from sRGB 0-255
        double R=Math.pow(r/255.0,2.2), G=Math.pow(g/255.0,2.2), B=Math.pow(b/255.0,2.2);
        return (float)(0.2126*R+0.7152*G+0.0722*B);
    }
    private static float wcag(float y1,float y2){ float hi=Math.max(y1,y2), lo=Math.min(y1,y2); return (hi+0.05f)/(lo+0.05f); }
    private static int hslRgb(float hueDeg,float s,float l){
        float h=((hueDeg%360f)+360f)%360f/360f; float c=(1f-Math.abs(2f*l-1f))*s; float x=c*(1f-Math.abs((h*6f)%2f-1f)); float m=l-c/2f;
        float r,g,b; int seg=(int)(h*6f)%6;
        if(seg==0){r=c;g=x;b=0;} else if(seg==1){r=x;g=c;b=0;} else if(seg==2){r=0;g=c;b=x;} else if(seg==3){r=0;g=x;b=c;} else if(seg==4){r=x;g=0;b=c;} else {r=c;g=0;b=x;}
        return Color.rgb(Math.round((r+m)*255f),Math.round((g+m)*255f),Math.round((b+m)*255f));
    }
    private static float pct(float[] sorted,float p){ if(sorted.length==0)return 0f; int i=Math.min(sorted.length-1,Math.max(0,Math.round(p*(sorted.length-1)))); return sorted[i]; }

    /**
     * The one reactive tone for a slide, following the Gemini consultation's
     * OKLCH pipeline, implemented pragmatically in HSV/HSL terms (recorded
     * as an approximation in BUILD-REPORT; no OKLCH is claimed):
     * 1) Corner audit on a 192x108 downscale: median / p15 / p85 luminance
     *    for the three occupied corners (TL, BL, BR) plus TR for scrims.
     * 2) Polarity by worst-corner scoring with canonical ink luminances
     *    (light 0.78, dark 0.025): light ink is scored against each corner's
     *    bright tail (p85), dark ink against its dark tail (p15); dark wins
     *    if the corners' mean median luminance exceeds 0.44, or its
     *    worst-corner score beats light's by 0.6 or more (the margin also
     *    covers the consult's +0.4 light tie-break). ONE polarity for the
     *    whole slide: per-corner polarity switching was rejected by
     *    Jonathan's rule 4.
     * 3) Hue from the whole painting: saturation-weighted hue histogram,
     *    near-greys and clipped extremes ignored, chroma-window weighted so
     *    a tiny saturated accent cannot dye the UI; monochrome paintings
     *    fall back to the warm gallery hue 75 degrees.
     * 4) Lightness/saturation clamps (light ink HSL L 0.84-0.93, S 0.14-0.30,
     *    never pure white; dark ink L 0.12-0.22, S 0.18-0.36, never pure
     *    black), then a contrast resolution loop against every corner's
     *    median: push lightness to the outer clamp and drop saturation,
     *    then per failing corner a conditional radial scrim.
     */
    private Ink computeInk(Bitmap art){
        Ink ink=new Ink();
        ink.deepRgb=deepRgb; ink.highRgb=highRgb; ink.shadowDy=inkShadowDy; ink.shadowR=inkShadowR; ink.shadowColor=inkShadow;
        if(art==null){ ink.rgb=0xF5F0E6; ink.dark=false; ink.baseAlpha=0.72f; ink.scrimRgb=deepRgb; return ink; }
        Bitmap small=Bitmap.createScaledBitmap(art,192,108,true);
        int W=192,H=108; int[] px=new int[W*H]; small.getPixels(px,0,W,0,0,W,H); if(small!=art)small.recycle();
        float[] ys=new float[W*H]; float[] hsv=new float[3];
        for(int i=0;i<px.length;i++){ int r=Color.red(px[i]),g=Color.green(px[i]),b=Color.blue(px[i]); ys[i]=linY(r,g,b); }
        // Corner ROIs as fractions {x0,y0,x1,y1}: TL, TR, BL, BR.
        float[][] roi={{0f,0f,0.26f,0.14f},{0.72f,0f,1f,0.14f},{0f,0.80f,0.36f,1f},{0.66f,0.82f,1f,1f}};
        float[] med=new float[4], p15=new float[4], p85=new float[4];
        for(int ci=0;ci<4;ci++){
            int x0=Math.round(roi[ci][0]*W), y0=Math.round(roi[ci][1]*H), x1=Math.round(roi[ci][2]*W), y1=Math.round(roi[ci][3]*H);
            ArrayList<Float> v=new ArrayList<>();
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)v.add(ys[y*W+x]);
            float[] s=new float[v.size()]; for(int i=0;i<s.length;i++)s[i]=v.get(i); Arrays.sort(s);
            med[ci]=pct(s,0.5f); p15[ci]=pct(s,0.15f); p85[ci]=pct(s,0.85f);
        }
        // Deepest and highlight tones (mean RGB of darkest / lightest 5 percent).
        float[] allY=ys.clone(); Arrays.sort(allY); float yLo=pct(allY,0.05f), yHi=pct(allY,0.95f);
        long dr=0,dg=0,db=0,dn=0, hr=0,hg=0,hb=0,hn=0;
        for(int i=0;i<px.length;i++){ int r=Color.red(px[i]),g=Color.green(px[i]),b=Color.blue(px[i]);
            if(ys[i]<=yLo){dr+=r;dg+=g;db+=b;dn++;} if(ys[i]>=yHi){hr+=r;hg+=g;hb+=b;hn++;} }
        int deep = dn>0?Color.rgb((int)(dr/dn),(int)(dg/dn),(int)(db/dn)):0x141210;
        int high = hn>0?Color.rgb((int)(hr/hn),(int)(hg/hn),(int)(hb/hn)):0xF2EDE2;
        ink.deepRgb=deep; ink.highRgb=high;
        // Polarity (occupied corners only: TL=0, BL=2, BR=3).
        int[] occ={0,2,3};
        float meanMed=0f, lightWorst=Float.MAX_VALUE, darkWorst=Float.MAX_VALUE;
        for(int ci: occ){ meanMed+=med[ci]; lightWorst=Math.min(lightWorst,wcag(0.78f,p85[ci])); darkWorst=Math.min(darkWorst,wcag(p15[ci],0.025f)); }
        meanMed/=occ.length;
        boolean dark = meanMed>0.44f || darkWorst>lightWorst+0.6f;
        ink.dark=dark;
        // Hue: saturation-weighted histogram, chroma window, circular mean around the winning bin.
        double[] bins=new double[36]; double totalW=0;
        for(int i=0;i<px.length;i++){
            if(ys[i]<0.015f||ys[i]>0.87f)continue;
            Color.RGBToHSV(Color.red(px[i]),Color.green(px[i]),Color.blue(px[i]),hsv);
            float sat=hsv[1]; if(sat<0.10f)continue;
            double w=sat*(sat<=0.45f?1.0:Math.max(0.0,1.0-(sat-0.45)*1.5)); if(w<=0)continue;
            bins[Math.min(35,(int)(hsv[0]/10f))]+=w; totalW+=w;
        }
        float hue=75f; // warm gallery fallback for monochrome
        if(totalW>0.05*px.length){
            int best=0; for(int i=1;i<36;i++)if(bins[i]>bins[best])best=i;
            double sx=0, sy=0;
            for(int o=-1;o<=1;o++){ int bi=(best+o+36)%36; double wgt=bins[bi]; double ang=Math.toRadians(bi*10.0+5.0); sx+=wgt*Math.cos(ang); sy+=wgt*Math.sin(ang); }
            if(sx!=0||sy!=0)hue=(float)((Math.toDegrees(Math.atan2(sy,sx))+360.0)%360.0);
        }
        // Lightness / saturation within clamps, then the contrast loop.
        float L = dark?0.17f:0.88f, S = dark?0.27f:0.22f;
        float base = dark?0.76f:0.72f;
        int rgb=hslRgb(hue,S,L); float yInk=linY(Color.red(rgb),Color.green(rgb),Color.blue(rgb));
        boolean lowContrast=false;
        if(minCornerContrast(yInk,base,med,null,deep,high)<4.5f){
            lowContrast=true;
            L = dark?0.11f:0.93f; S = dark?0.16f:0.12f;
            rgb=hslRgb(hue,S,L); yInk=linY(Color.red(rgb),Color.green(rgb),Color.blue(rgb));
            float[] scrim=new float[4];
            if(minCornerContrast(yInk,base,med,scrim,deep,high)<4.5f){
                // scrims were filled per failing corner by the helper
            }
            ink.scrim=scrim;
        }
        if(lowContrast)base=0.78f; // collapse to the shared low-contrast opacity
        ink.rgb=rgb; ink.baseAlpha=base;
        ink.scrimRgb = dark?high:deep;
        // One shared polarity-aware soft shadow, no pure black/white, no outlines.
        if(dark){ ink.shadowColor=Color.argb(140,Color.red(high),Color.green(high),Color.blue(high)); ink.shadowDy=0.5f; ink.shadowR=7f; }
        else { ink.shadowColor=Color.argb(117,Color.red(deep),Color.green(deep),Color.blue(deep)); ink.shadowDy=1.5f; ink.shadowR=6f; }
        return ink;
    }
    /** Worst effective contrast of the ink over the four corners' medians.
     *  When scrimOut is non-null, fills per-corner scrim alphas for corners
     *  still failing (steps up to the caps: 0.26 behind light ink, 0.22
     *  behind dark ink) and returns the post-scrim worst case. */
    private float minCornerContrast(float yInk,float alpha,float[] med,float[] scrimOut,int deep,int high){
        float yDeep=linY(Color.red(deep),Color.green(deep),Color.blue(deep));
        float yHigh=linY(Color.red(high),Color.green(high),Color.blue(high));
        boolean lightInk = yInk>0.3f;
        float cap = lightInk?0.26f:0.22f;
        float worst=Float.MAX_VALUE;
        for(int ci=0;ci<4;ci++){
            float yBg=med[ci];
            float yEff=alpha*yInk+(1f-alpha)*yBg;
            float cr=wcag(yEff,yBg);
            if(scrimOut!=null&&cr<4.5f){
                float[] steps={0.08f,0.14f,0.20f,cap};
                float chosen=cap;
                for(float s: steps){
                    float yBg2 = lightInk ? (1f-s)*yBg+s*yDeep : (1f-s)*yBg+s*yHigh;
                    float yEff2=alpha*yInk+(1f-alpha)*yBg2;
                    if(wcag(yEff2,yBg2)>=4.5f){ chosen=s; break; }
                }
                scrimOut[ci]=chosen;
                float yBg2 = lightInk ? (1f-chosen)*yBg+chosen*yDeep : (1f-chosen)*yBg+chosen*yHigh;
                cr=wcag(alpha*yInk+(1f-alpha)*yBg2,yBg2);
            }
            worst=Math.min(worst,cr);
        }
        return worst;
    }
    /** Apply the slide's one tone to EVERY text element, uniformly. */
    private void applyInk(Ink ink){
        inkRgb=ink.rgb; deepRgb=ink.deepRgb; highRgb=ink.highRgb; inkDark=ink.dark;
        inkShadow=ink.shadowColor; inkShadowDy=ink.shadowDy; inkShadowR=ink.shadowR;
        int a=Math.round(ink.baseAlpha*255f);
        int col=Color.argb(a,Color.red(ink.rgb),Color.green(ink.rgb),Color.blue(ink.rgb));
        TextView[] all={clockTv,suffixTv,dateTv,weatherTv,labelTitleTv,labelArtistTv,labelCollTv,msgTv,signTv};
        for(TextView t: all){ t.setTextColor(col); t.setShadowLayer(ink.shadowR,0,ink.shadowDy,ink.shadowColor); }
        alertTv.setTextColor(Color.argb(209,Color.red(ink.rgb),Color.green(ink.rgb),Color.blue(ink.rgb))); // alerts peak at 0.82
        alertTv.setShadowLayer(ink.shadowR,0,ink.shadowDy,ink.shadowColor);
        scrimView.setScrims(ink.scrim,ink.scrimRgb);
    }
    // --- Muse message as a signed note, bottom-left ---
    private void fadeInMessage(String text){
        if(msgAnim!=null)msgAnim.cancel();
        msgTv.setText(text); // sign-off is its own line ("— Muse", signTv)
        noteBlock.setVisibility(VISIBLE); noteBlock.requestLayout();
        msgAnim=ObjectAnimator.ofFloat(noteBlock,"alpha",noteBlock.getAlpha(),1f); msgAnim.setDuration(1500); msgAnim.start();
    }
    private void fadeOutMessage(){
        if(noteBlock.getVisibility()!=VISIBLE)return;
        if(msgAnim!=null)msgAnim.cancel();
        msgAnim=ObjectAnimator.ofFloat(noteBlock,"alpha",noteBlock.getAlpha(),0f); msgAnim.setDuration(1200);
        msgAnim.addListener(new AnimatorListenerAdapter(){ @Override public void onAnimationEnd(Animator a){ noteBlock.setVisibility(GONE); msgTv.setText(""); } });
        msgAnim.start();
    }

    // --- Live feed alerts, top-right: the reserved corner. Same cursive,
    // same slide ink, same shadow; no pill, badge, or border. Pure opacity
    // motion: fade in 1400 ms, hold 14 s, fade out 2200 ms, one at a time,
    // never stacked; the same alert is not re-shown within 5 minutes.
    private final Runnable alertHoldEnd=new Runnable(){public void run(){ fadeOutAlert(); }};
    private final Runnable alertPump=new Runnable(){public void run(){ pumpAlerts(); }};
    private void maybeQueueAlerts(){
        if(dashShowing)return; // DashboardView owns notifications on the dashboard face.
        if(muse==null||!running)return;
        long now=System.currentTimeMillis(); boolean added=false;
        for(String a: muse.alerts){ if(a==null)continue; String t=a.trim(); if(t.isEmpty())continue;
            Long last=alertShownAt.get(t); if(last!=null&&now-last<300000)continue;
            if(!alertQueue.contains(t)&&!t.equals(currentAlert)){ alertQueue.add(t); added=true; } }
        if(added)h.post(alertPump);
    }
    private void pumpAlerts(){
        if(!running||alertBusy||cardShowing)return;
        final String t=alertQueue.poll(); if(t==null)return;
        alertBusy=true; currentAlert=t; alertTv.setText(t);
        alertBlock.setVisibility(VISIBLE);
        if(alertAnim!=null)alertAnim.cancel();
        alertAnim=ObjectAnimator.ofFloat(alertBlock,"alpha",0f,1f); alertAnim.setDuration(1400); alertAnim.setInterpolator(new DecelerateInterpolator()); alertAnim.start();
        h.removeCallbacks(alertHoldEnd); h.postDelayed(alertHoldEnd,1400+14000);
    }
    private void fadeOutAlert(){
        if(!alertBusy)return;
        if(alertAnim!=null)alertAnim.cancel();
        alertAnim=ObjectAnimator.ofFloat(alertBlock,"alpha",alertBlock.getAlpha(),0f); alertAnim.setDuration(2200);
        alertAnim.addListener(new AnimatorListenerAdapter(){ @Override public void onAnimationEnd(Animator a){
            alertBlock.setVisibility(GONE); alertBlock.animate().setListener(null);
            if(currentAlert!=null)alertShownAt.put(currentAlert,System.currentTimeMillis());
            currentAlert=null; alertBusy=false; h.postDelayed(alertPump,600); } });
        alertAnim.start();
    }
    private void hideAlertNow(){
        h.removeCallbacks(alertHoldEnd); h.removeCallbacks(alertPump);
        if(alertAnim!=null)alertAnim.cancel();
        alertBlock.setAlpha(0f); alertBlock.setVisibility(GONE);
        if(currentAlert!=null)alertShownAt.put(currentAlert,System.currentTimeMillis());
        currentAlert=null; alertBusy=false;
    }

    // --- Muse cards: a hand-lettered broadside on a wash of the preceding
    // painting. No boxes, panels, or borders: the wash fills the frame
    // (heavy blur, desaturated, veiled with the painting's own deep or
    // highlight tone), text sits directly on it in the slide's cursive ink
    // at reading opacity. Home is a single ledger, never a grid.
    private Bitmap washBitmap(Bitmap art){
        if(art==null)return null;
        try{
            Bitmap b=Bitmap.createScaledBitmap(art,480,270,true);
            Bitmap b2=Bitmap.createScaledBitmap(b,120,68,true); b=Bitmap.createScaledBitmap(b2,480,270,true); // two-step downscale = heavy blur
            Bitmap out=Bitmap.createBitmap(480,270,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(out);
            Paint p=new Paint(Paint.FILTER_BITMAP_FLAG); ColorMatrix cm=new ColorMatrix(); cm.setSaturation(0.65f); // desaturate 35 percent
            p.setColorFilter(new ColorMatrixColorFilter(cm)); cv.drawBitmap(b,0,0,p);
            Paint veil=new Paint(); int tone = inkDark?highRgb:deepRgb; veil.setColor(Color.argb(inkDark?194:158,Color.red(tone),Color.green(tone),Color.blue(tone))); // light paper veil 76% / dark veil 62%
            cv.drawRect(0,0,480,270,veil);
            return out;
        }catch(Exception e){ return null; }
    }
    private TextView cardText(float sp,boolean med,int alpha){
        TextView t=new TextView(getContext()); t.setTypeface(face(getContext(),med)); t.setTextSize(sp);
        t.setTextColor(Color.argb(alpha,Color.red(inkRgb),Color.green(inkRgb),Color.blue(inkRgb)));
        t.setShadowLayer(inkShadowR,0,inkShadowDy,inkShadow); return t;
    }
    private View spacer(int dpUnits){ View v=new View(getContext()); float den=getResources().getDisplayMetrics().density; v.setLayoutParams(new LinearLayout.LayoutParams(1,(int)(dpUnits*den))); return v; }
    private void addLedgerRow(String label,String value){ addLedgerRowTo(cardRoot,label,value); }
    private void addLedgerRowTo(LinearLayout parent,String label,String value){
        if(value==null||value.trim().isEmpty())return;
        TextView t=new TextView(getContext()); t.setTypeface(face(getContext(),false)); t.setTextSize(LEDGER_VALUE_SP);
        t.setShadowLayer(inkShadowR,0,inkShadowDy,inkShadow);
        String text=label+"    "+value;
        SpannableString ss=new SpannableString(text);
        ss.setSpan(new AbsoluteSizeSpan(spPx(LEDGER_LABEL_SP)),0,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(Color.argb(153,Color.red(inkRgb),Color.green(inkRgb),Color.blue(inkRgb))),0,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(Color.argb(219,Color.red(inkRgb),Color.green(inkRgb),Color.blue(inkRgb))),label.length(),text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setText(ss);
        // MATCH_PARENT width: spanned text is non-"boring", so an exact
        // measure spec is what guarantees its layout is built at full
        // width instead of a stale narrow one (same trap as the clock).
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT); lp.topMargin=spPx(4f);
        parent.addView(t,lp);
    }
    private void buildHomeCard(){
        cardRoot.setGravity(Gravity.TOP);
        LinearLayout header=new LinearLayout(getContext()); header.setOrientation(LinearLayout.HORIZONTAL);
        TextView hero=cardText(HERO_SP,true,224); hero.setText(muse.houseMode!=null?muse.houseMode:"Home");
        header.addView(hero,new LinearLayout.LayoutParams(0,LayoutParams.WRAP_CONTENT,1f));
        TextView meta=cardText(CARD_META_SP,false,163); meta.setGravity(Gravity.RIGHT);
        meta.setText(new SimpleDateFormat("EEE, MMM d",Locale.US).format(new Date())+" \u00B7 "+new SimpleDateFormat("h:mm a",Locale.US).format(new Date()).toLowerCase(Locale.US));
        header.addView(meta,new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        cardRoot.addView(header);
        cardRoot.addView(spacer(30)); // ledger starts ~120px (panel) below the header
        if(!muse.whoHome.isEmpty())addLedgerRow("home",join(muse.whoHome));
        if(muse.daisyStatus!=null||muse.daisyNote!=null)addLedgerRow("dog",nz(muse.daisyStatus)+(nz(muse.daisyNote).isEmpty()?"":" \u00B7 "+muse.daisyNote));
        if(muse.nextEventTitle!=null)addLedgerRow("next",nz(muse.nextEventTime).isEmpty()?muse.nextEventTitle:muse.nextEventTime+" \u00B7 "+muse.nextEventTitle);
        if(muse.nowPlaying!=null&&!muse.nowPlaying.trim().isEmpty()&&!"Nothing playing".equalsIgnoreCase(muse.nowPlaying.trim()))addLedgerRow("playing",muse.nowPlaying); // idle row omitted entirely
        if(!muse.alerts.isEmpty())addLedgerRow("alerts",join(muse.alerts,"; "));
        if(Prefs.museMessages(getContext())&&muse.messageActive())addLedgerRow("note",muse.messageText);
        TextView sign=cardText(CARD_SIGN_SP,true,143); sign.setText("\u2014 Muse");
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); slp.topMargin=spPx(9f);
        cardRoot.addView(sign,slp);
    }
    private void buildBriefCard(){
        cardRoot.setGravity(Gravity.CENTER_HORIZONTAL);
        float den=getResources().getDisplayMetrics().density;
        LinearLayout col=new LinearLayout(getContext()); col.setOrientation(LinearLayout.VERTICAL);
        int frameW=getResources().getDisplayMetrics().widthPixels;
        int colW=Math.min((int)(525*den),(int)(frameW*0.62f)); // 2100px panel max column
        cardRoot.addView(col,new LinearLayout.LayoutParams(colW,LayoutParams.WRAP_CONTENT));
        TextView title=cardText(BRIEF_TITLE_SP,true,224); title.setText(muse.briefTitle!=null?muse.briefTitle:"Daily Brief"); col.addView(title);
        col.addView(spacer(19)); // 76px panel gap
        for(String line: muse.briefLines){
            TextView t=cardText(BRIEF_LINE_SP,false,209); t.setLineSpacing(0f,1.35f);
            String text="\u2014  "+line; SpannableString ss=new SpannableString(text);
            ss.setSpan(new ForegroundColorSpan(Color.argb(115,Color.red(inkRgb),Color.green(inkRgb),Color.blue(inkRgb))),0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(Color.argb(209,Color.red(inkRgb),Color.green(inkRgb),Color.blue(inkRgb))),2,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.setText(ss);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(colW,LayoutParams.WRAP_CONTENT); lp.bottomMargin=(int)(11*den); // 44px panel between items; exact width, see addLedgerRow
            col.addView(t,lp);
        }
        TextView sign=cardText(CARD_SIGN_SP,true,143); sign.setText("\u2014 Muse");
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); slp.topMargin=(int)(18*den); // 72px panel below the last line
        col.addView(sign,slp);
    }
    private void showMuseCard(){
        cardShowing=true; fadeOutMessage(); hideAlertNow();
        labelBlock.setVisibility(GONE);
        boolean brief = muse.hasBrief() && museBriefNext; museBriefNext = muse.hasBrief() && !museBriefNext;
        cardRoot.removeAllViews();
        if(brief)buildBriefCard(); else buildHomeCard();
        Bitmap wash=washBitmap(lastBitmap);
        if(wash!=null){ cardBg.setImageBitmap(wash); cardBg.setBackgroundColor(Color.TRANSPARENT); } else cardBg.setBackgroundColor(inkDark?0xFFE8E2D4:0xFF101418);
        scrimView.setVisibility(GONE);
        cardBg.setVisibility(VISIBLE); cardRoot.setVisibility(VISIBLE);
        if(cardAnim!=null)cardAnim.cancel(); if(cardBgAnim!=null)cardBgAnim.cancel();
        cardBg.setAlpha(0f); cardRoot.setAlpha(0f);
        cardBgAnim=ObjectAnimator.ofFloat(cardBg,"alpha",0f,1f); cardBgAnim.setDuration(1800); cardBgAnim.start(); // the pigment dissolves into paper
        cardAnim=ObjectAnimator.ofFloat(cardRoot,"alpha",0f,1f); cardAnim.setDuration(1800); cardAnim.start();
        updateClock(); applyDim();
    }
    private void fadeOutCard(){
        if(!cardShowing)return; cardShowing=false;
        if(cardAnim!=null)cardAnim.cancel(); if(cardBgAnim!=null)cardBgAnim.cancel();
        ObjectAnimator a=ObjectAnimator.ofFloat(cardRoot,"alpha",cardRoot.getAlpha(),0f); a.setDuration(700);
        a.addListener(new AnimatorListenerAdapter(){ @Override public void onAnimationEnd(Animator an){ cardRoot.setVisibility(GONE); } }); a.start(); cardAnim=a;
        ObjectAnimator b=ObjectAnimator.ofFloat(cardBg,"alpha",cardBg.getAlpha(),0f); b.setDuration(700);
        b.addListener(new AnimatorListenerAdapter(){ @Override public void onAnimationEnd(Animator an){ cardBg.setVisibility(GONE); scrimView.setVisibility(VISIBLE); } }); b.start(); cardBgAnim=b;
        h.postDelayed(alertPump,900); // alerts may resume once the card is gone
    }
    private String nz(String s){return s==null?"":s;}
    private String join(List<String> l){ return join(l,", "); }
    private String join(List<String> l,String sep){ StringBuilder s=new StringBuilder(); for(int i=0;i<l.size();i++){if(i>0)s.append(sep); s.append(l.get(i));} return s.toString(); }
    // Overlays only: a few pixels of micro-shift between slides (burn-in
    // hygiene for the text). The artwork itself never moves (v0.2.2).
    // Zones are anchored a frame apart, so these few pixels can never make
    // two elements share a band.
    private void shiftOverlays(){ int dx=rnd.nextInt(7), dy=rnd.nextInt(5); clockBlock.setTranslationX(dx); clockBlock.setTranslationY(dy); labelBlock.setTranslationX(-dx); labelBlock.setTranslationY(-dy); noteBlock.setTranslationX(dx); noteBlock.setTranslationY(-dy); if(!alertBusy){ alertBlock.setTranslationX(-dx); alertBlock.setTranslationY(dy); } }
    private void updateClock(){
        if(dashShowing){
            clockBlock.setVisibility(GONE); dashClockBlock.setVisibility(GONE);
            if(dashboardView!=null)dashboardView.tick();
            return;
        }
        if(!Prefs.showClock(getContext())){ clockBlock.setVisibility(GONE); dashClockBlock.setVisibility(GONE); return; }
        // Exactly one face owns a clock block at a time.
        clockBlock.setVisibility(VISIBLE);
        dashClockBlock.setVisibility(GONE);
        String t=new SimpleDateFormat("h:mm a",Locale.US).format(new Date());
        int sp=t.lastIndexOf(' ');
        String digits=t.substring(0,sp), suffix=t.substring(sp+1).toLowerCase(Locale.US); // "pm", smaller, its own plain TextView (see constructor note)
        String date=new SimpleDateFormat("EEE, MMM d",Locale.US).format(new Date());
        String w=weather!=null?weather:"";
        clockTv.setText(digits); suffixTv.setText(suffix); dateTv.setText(date); weatherTv.setText(w);
        dashClockTv.setText(digits); dashSuffixTv.setText(suffix); dashDateTv.setText(date); dashWeatherTv.setText(w);
    }
    private void applyDim(){ if(dashShowing){ dim.setAlpha(0f); return; } float a=0f; if(Prefs.nightDim(getContext())){ int hr=Calendar.getInstance().get(Calendar.HOUR_OF_DAY); if(hr>=23||hr<7)a=0.6f; } dim.setAlpha(a); }
    @Override public boolean onKeyDown(int k, KeyEvent e){ if(k==KeyEvent.KEYCODE_DPAD_LEFT){skip(-1); return true;} if(k==KeyEvent.KEYCODE_DPAD_RIGHT){skip(1); return true;} return super.onKeyDown(k,e); }
}

/**
 * Conditional corner scrims (Gemini consult section D): invisible by
 * default, a radial vignette ONLY on a corner whose local contrast fails
 * even after the ink's lightness push, capped at 0.26 (dark, behind light
 * ink) / 0.22 (pale mist, behind dark ink), coloured from the painting
 * itself, never a linear bar. Elliptical falloff: horizontal radius 25
 * percent of frame width, vertical 18 percent of height, half strength by
 * 45 percent of the radius.
 */
class ScrimView extends View {
    private final float[] alphas=new float[4]; private int rgb=0x000000;
    public ScrimView(Context c){ super(c); setWillNotDraw(false); }
    public void setScrims(float[] a,int colorRgb){ for(int i=0;i<4;i++)alphas[i]=a!=null?a[i]:0f; rgb=colorRgb; invalidate(); }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int W=getWidth(), H=getHeight(); if(W==0||H==0)return;
        float rx=W*0.25f, ry=H*0.18f;
        float[][] corners={{0,0},{W,0},{0,H},{W,H}};
        for(int i=0;i<4;i++){
            float a=alphas[i]; if(a<=0.003f)continue;
            int a255=Math.round(a*255f);
            RadialGradient g=new RadialGradient(0,0,rx,
                new int[]{Color.argb(a255,Color.red(rgb),Color.green(rgb),Color.blue(rgb)),
                          Color.argb(Math.round(a255*0.5f),Color.red(rgb),Color.green(rgb),Color.blue(rgb)),
                          Color.argb(0,Color.red(rgb),Color.green(rgb),Color.blue(rgb))},
                new float[]{0f,0.45f,1f},Shader.TileMode.CLAMP);
            Paint p=new Paint(); p.setShader(g);
            canvas.save(); canvas.translate(corners[i][0],corners[i][1]); canvas.scale(1f,ry/rx);
            canvas.drawCircle(0,0,rx,p); canvas.restore();
        }
    }
}
