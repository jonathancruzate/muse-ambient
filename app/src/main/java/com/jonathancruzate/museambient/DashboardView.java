package com.jonathancruzate.museambient;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Muse Ambient v0.3 dashboard face.
 *
 * Design source: ~/workspace/shield/gemini-dashboard-redesign-consult.md.
 * A museum wall colophon on warm limewashed plaster: permanent left spine,
 * a 2 px hairline datum at panel y 1040, three pinned ledger slots, and
 * conditional right zones. No shadows, no scrims, no cards in the resting
 * layout. The only filled surface is the rare notification veil.
 *
 * Coordinates in this file are 2160p panel pixels unless a name says
 * otherwise. The Shield UI grid is 1920x1080 at density 320, so a panel
 * length converts to view pixels as panel * density / 4, and a panel font
 * size converts to sp as panel / 4.
 */
public class DashboardView extends FrameLayout {
    private static final int INK = 0x2B2724;
    private static final int ACCENT = 0x2F4F5B;
    private static final int UMBER = 0x7A5B40;
    private static final int PAPER = 0xEFEAE2;
    private static final int NIGHT_PARCHMENT = 0x9A9083;
    private static final int NIGHT_ACCENT = 0x7D9CA8;
    private static final int NIGHT_VEIL = 0x26221E;

    private static Typeface newsLight72;
    private static Typeface newsRegular36;
    private static Typeface newsRegular28;
    private static Typeface newsItalic28;
    private static Typeface sansRegular;
    private static Typeface sansMedium;
    private static Typeface sansSemiBold;

    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final View duskOverlay;
    private final View nightOverlay;
    private final View hairline;
    private final LinearLayout clockRow;
    private final TextView clockTv;
    private final TextView meridiemTv;
    private final TextView dateTv;
    private final TextView weatherTv;
    private final LinearLayout modeRow;
    private final ModeDot modeDot;
    private final TextView modeTv;

    private final FrameLayout homeSlot;
    private final FrameLayout daisySlot;
    private final FrameLayout nextSlot;
    private final TextView homeValue;
    private final TextView daisyValue;
    private final TextView daisyNote;
    private final TextView nextValue;
    private final TextView nextTime;

    private final LinearLayout briefBlock;
    private final TextView briefTitle;
    private final List<TextView> briefLines = new ArrayList<TextView>();

    private final View noteRule;
    private final LinearLayout noteBlock;
    private final TextView noteBody;
    private final TextView noteAttribution;

    private final LinearLayout playingBlock;
    private final TextView playingTrack;
    private final TextView playingArtist;

    private final FrameLayout notificationBox;
    private final View notificationBar;
    private final TextView notificationCategory;
    private final TextView notificationHeadline;
    private final TextView notificationTime;

    private MuseFeed feed;
    private String weather;
    private boolean liveFeed;
    private boolean messagesEnabled = true;
    private boolean clockEnabled = true;
    private boolean weatherEnabled = true;
    private boolean active;
    private boolean firstRender = true;
    private boolean night;
    private boolean nightInitialized;
    private String lastFeedSignature = "";
    private String lastClock = "";
    private String lastDaisySignature = "";
    private String lastNextSignature = "";
    private long showStartedAt;
    private long nextBriefAt;
    private long briefStartedAt;
    private long briefYieldUntil;
    private boolean briefShowing;
    private String playingText;
    private long playingLastSeenAt;
    private int sunsetMinuteCache = -2;
    private int sunsetDayCache = -1;

    private static class QueuedAlert {
        String text;
        long queuedAt;
        QueuedAlert(String text, long queuedAt) { this.text = text; this.queuedAt = queuedAt; }
    }

    private final ArrayDeque<QueuedAlert> alertQueue = new ArrayDeque<QueuedAlert>();
    private final HashMap<String, Long> alertShownAt = new HashMap<String, Long>();
    private final HashSet<String> alertsPresent = new HashSet<String>();
    private String activeAlert;
    private long activeAlertStartedAt;
    private long activeAlertDeadline;
    private boolean alertFading;

    private static Typeface loadFont(Context c, String path, Typeface fallback) {
        try {
            Typeface t = Typeface.createFromAsset(c.getAssets(), path);
            return t != null ? t : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void loadFonts(Context c) {
        if (sansRegular != null) return;
        newsLight72 = loadFont(c, "fonts/Newsreader-Light-72.ttf", Typeface.SERIF);
        newsRegular36 = loadFont(c, "fonts/Newsreader-Regular-36.ttf", Typeface.SERIF);
        newsRegular28 = loadFont(c, "fonts/Newsreader-Regular-28.ttf", Typeface.SERIF);
        newsItalic28 = loadFont(c, "fonts/Newsreader-Italic-28.ttf", Typeface.SERIF);
        sansRegular = loadFont(c, "fonts/SourceSans3-Regular.ttf", Typeface.SANS_SERIF);
        sansMedium = loadFont(c, "fonts/SourceSans3-Medium.ttf", Typeface.SANS_SERIF);
        sansSemiBold = loadFont(c, "fonts/SourceSans3-SemiBold.ttf", Typeface.SANS_SERIF);
    }

    public DashboardView(Context c) {
        super(c);
        loadFonts(c);
        density = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.TRANSPARENT);

        duskOverlay = new View(c);
        duskOverlay.setBackgroundColor(0x2A1F16);
        duskOverlay.setAlpha(0f);
        addView(duskOverlay, full());

        nightOverlay = new View(c);
        nightOverlay.setBackgroundColor(Color.rgb(27, 24, 22));
        nightOverlay.setAlpha(0f);
        addView(nightOverlay, full());

        // Permanent left spine, x 240, width 1365.
        clockRow = new LinearLayout(c);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setBaselineAligned(true);
        clockTv = text(c, newsLight72, 236, ink(0.86f));
        clockTv.setLineSpacing(0f, 220f / 236f);
        clockTv.setLetterSpacing(-0.025f);
        clockTv.setSingleLine(true);
        clockRow.addView(clockTv, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        meridiemTv = text(c, sansRegular, 52, ink(0.72f));
        meridiemTv.setLetterSpacing(0.10f);
        meridiemTv.setSingleLine(true);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        mp.leftMargin = P(18);
        mp.gravity = Gravity.BOTTOM;
        clockRow.addView(meridiemTv, mp);
        addView(clockRow, at(240, 220, 1365, 220));

        dateTv = text(c, sansRegular, 58, ink(0.92f));
        dateTv.setLetterSpacing(0.005f);
        dateTv.setSingleLine(true);
        dateTv.setEllipsize(TextUtils.TruncateAt.END);
        addView(dateTv, at(240, 470, 1365, 72));

        weatherTv = text(c, sansRegular, 58, ink(0.92f));
        weatherTv.setLetterSpacing(0.005f);
        weatherTv.setSingleLine(true);
        weatherTv.setEllipsize(TextUtils.TruncateAt.END);
        addView(weatherTv, at(240, 542, 1365, 72));

        modeRow = new LinearLayout(c);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        modeDot = new ModeDot(c);
        modeRow.addView(modeDot, new LinearLayout.LayoutParams(P(10), P(10)));
        modeTv = text(c, sansMedium, 44, ink(0.92f));
        modeTv.setLetterSpacing(0.16f);
        modeTv.setSingleLine(true);
        LinearLayout.LayoutParams modeTextParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        modeTextParams.leftMargin = P(18);
        modeRow.addView(modeTv, modeTextParams);
        addView(modeRow, at(240, 650, 1365, 56));

        hairline = new View(c);
        hairline.setBackgroundColor(ink(0.16f));
        addView(hairline, at(240, 1040, 1365, 2));

        // Three pinned ledger slots. Their origins never move.
        homeSlot = slot(1100, 180);
        homeValue = slotValue(homeSlot, 58);
        daisySlot = slot(1280, 268);
        daisyValue = slotValue(daisySlot, 58);
        daisyNote = new TextView(c);
        configure(daisyNote, sansRegular, 52, ink(0.72f), 66);
        daisyNote.setLetterSpacing(0.005f);
        daisyNote.setMaxLines(2);
        daisyNote.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams dn = new FrameLayout.LayoutParams(P(1365), LayoutParams.WRAP_CONTENT);
        dn.leftMargin = 0;
        dn.topMargin = P(130);
        daisySlot.addView(daisyNote, dn);

        nextSlot = slot(1600, 272);
        // Title and time flow inside the pinned NEXT slot: the slot origin
        // never moves, but a one-line title does not leave the time stranded
        // at the two-line position.
        LinearLayout nextBlock = new LinearLayout(c);
        nextBlock.setOrientation(LinearLayout.VERTICAL);
        nextValue = new TextView(c);
        configure(nextValue, sansRegular, 58, ink(0.92f), 72);
        nextValue.setMaxLines(2);
        nextValue.setEllipsize(TextUtils.TruncateAt.END);
        nextBlock.addView(nextValue, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        nextTime = new TextView(c);
        configure(nextTime, sansRegular, 52, ink(0.72f), 66);
        nextTime.setLetterSpacing(0.005f);
        nextTime.setSingleLine(true);
        nextTime.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nt = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        nt.topMargin = P(6);
        nextBlock.addView(nextTime, nt);
        FrameLayout.LayoutParams nb = new FrameLayout.LayoutParams(P(1365), LayoutParams.WRAP_CONTENT);
        nb.topMargin = P(58);
        nextSlot.addView(nextBlock, nb);

        // Daily Brief rotation moment, replacing ledger and note below datum.
        briefBlock = new LinearLayout(c);
        briefBlock.setOrientation(LinearLayout.VERTICAL);
        TextView briefEyebrow = text(c, sansMedium, 44, ink(0.72f));
        briefEyebrow.setText("DAILY BRIEF");
        briefEyebrow.setLetterSpacing(0.16f);
        briefBlock.addView(briefEyebrow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        briefTitle = text(c, newsRegular36, 76, ink(0.92f));
        briefTitle.setLineSpacing(0f, 92f / 76f);
        briefTitle.setLetterSpacing(-0.015f);
        briefTitle.setSingleLine(true);
        briefTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bt = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bt.topMargin = P(18);
        briefBlock.addView(briefTitle, bt);
        for (int i = 0; i < 4; i++) {
            TextView line = text(c, sansRegular, 56, ink(0.92f));
            line.setLineSpacing(0f, 78f / 56f);
            line.setMaxLines(2);
            line.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = P(24);
            briefBlock.addView(line, lp);
            briefLines.add(line);
        }
        briefBlock.setVisibility(GONE);
        briefBlock.setAlpha(0f);
        addView(briefBlock, at(240, 1100, 1780, 780));

        // Muse note as editorial marginalia, with a 3 px accent rule.
        noteRule = new View(c);
        noteRule.setBackgroundColor(accent(0.55f));
        noteRule.setVisibility(GONE);
        addView(noteRule, at(1922, 1100, 3, 356));
        noteBlock = new LinearLayout(c);
        noteBlock.setOrientation(LinearLayout.VERTICAL);
        noteBody = text(c, newsItalic28, 64, ink(0.92f));
        noteBody.setLineSpacing(0f, 90f / 64f);
        noteBody.setMaxLines(3);
        noteBody.setEllipsize(TextUtils.TruncateAt.END);
        noteBlock.addView(noteBody, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        noteAttribution = text(c, sansMedium, 44, accent(0.85f));
        noteAttribution.setText("MUSE");
        noteAttribution.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams na = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        na.topMargin = P(24);
        noteBlock.addView(noteAttribution, na);
        noteBlock.setVisibility(GONE);
        noteBlock.setAlpha(0f);
        addView(noteBlock, at(1950, 1100, 1365, 356));

        // Now playing, bottom right, text remains left aligned to its grid line.
        playingBlock = new LinearLayout(c);
        playingBlock.setOrientation(LinearLayout.VERTICAL);
        TextView playingEyebrow = text(c, sansMedium, 44, ink(0.72f));
        playingEyebrow.setText("NOW PLAYING");
        playingEyebrow.setLetterSpacing(0.16f);
        playingBlock.addView(playingEyebrow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        playingTrack = text(c, newsRegular28, 60, ink(0.92f));
        playingTrack.setLetterSpacing(-0.005f);
        playingTrack.setSingleLine(true);
        playingTrack.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams pt = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        pt.topMargin = P(8);
        playingBlock.addView(playingTrack, pt);
        playingArtist = text(c, sansRegular, 52, ink(0.72f));
        playingArtist.setLetterSpacing(0.005f);
        playingArtist.setSingleLine(true);
        playingArtist.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        pa.topMargin = P(4);
        playingBlock.addView(playingArtist, pa);
        playingBlock.setVisibility(GONE);
        playingBlock.setAlpha(0f);
        addView(playingBlock, at(2235, 1740, 1365, 220));

        // Rare notification: warm paper veil and one slate teal bar.
        notificationBox = new FrameLayout(c);
        notificationBox.setBackgroundColor(Color.argb(143, Color.red(PAPER), Color.green(PAPER), Color.blue(PAPER)));
        notificationBox.setVisibility(GONE);
        notificationBox.setAlpha(0f);
        notificationBar = new View(c);
        notificationBar.setBackgroundColor(accent(0.90f));
        // NOT MATCH_PARENT height: a match-parent child inside the
        // wrap-content notification box stretches the box to full height
        // during FrameLayout measurement (found on device). The bar gets
        // a provisional fixed height and is resized to the box's measured
        // height in showAlert once the text has laid out.
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(P(4), P(200));
        notificationBox.addView(notificationBar, barParams);
        LinearLayout notificationText = new LinearLayout(c);
        notificationText.setOrientation(LinearLayout.VERTICAL);
        notificationCategory = text(c, sansSemiBold, 44, accent(0.92f));
        notificationCategory.setLetterSpacing(0.16f);
        notificationCategory.setSingleLine(true);
        notificationText.addView(notificationCategory, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        notificationHeadline = text(c, sansMedium, 58, ink(0.94f));
        notificationHeadline.setLineSpacing(0f, 74f / 58f);
        notificationHeadline.setMaxLines(2);
        notificationHeadline.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nh = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        nh.topMargin = P(6);
        notificationText.addView(notificationHeadline, nh);
        notificationTime = text(c, sansRegular, 46, ink(0.72f));
        notificationTime.setText("Just now");
        notificationTime.setLetterSpacing(0.01f);
        LinearLayout.LayoutParams ntp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ntp.topMargin = P(4);
        notificationText.addView(notificationTime, ntp);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = P(40);
        textParams.rightMargin = P(40);
        textParams.topMargin = P(32);
        textParams.bottomMargin = P(32);
        notificationBox.addView(notificationText, textParams);
        FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(P(920), LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        boxParams.topMargin = P(220);
        boxParams.rightMargin = P(240);
        addView(notificationBox, boxParams);
    }

    private FrameLayout slot(int yPanel, int heightPanel) {
        FrameLayout slot = new FrameLayout(getContext());
        TextView label = new TextView(getContext());
        String labelText = yPanel == 1100 ? "HOME" : yPanel == 1280 ? "DAISY" : "NEXT";
        configure(label, sansMedium, 44, ink(0.72f), 52);
        label.setText(labelText);
        label.setLetterSpacing(0.16f);
        slot.addView(label, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(slot, at(240, yPanel, 1365, heightPanel));
        return slot;
    }

    private TextView slotValue(FrameLayout slot, int sizePanel) {
        TextView value = new TextView(getContext());
        configure(value, sansRegular, sizePanel, ink(0.92f), 72);
        value.setLetterSpacing(0f);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(P(1365), LayoutParams.WRAP_CONTENT);
        lp.topMargin = P(58);
        slot.addView(value, lp);
        return value;
    }

    private TextView text(Context c, Typeface typeface, int sizePanel, int color) {
        TextView t = new TextView(c);
        configure(t, typeface, sizePanel, color, Math.round(sizePanel * 1.24f));
        return t;
    }

    private void configure(TextView t, Typeface typeface, int sizePanel, int color, int linePanel) {
        t.setTypeface(typeface);
        t.setTextSize(sizePanel / 4f);
        t.setTextColor(color);
        t.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        t.setIncludeFontPadding(false);
        if (sizePanel > 0) t.setLineSpacing(0f, linePanel / (float) sizePanel);
    }

    private int P(double panel) {
        return (int) Math.round(panel * density / 4.0);
    }

    private LayoutParams full() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    private LayoutParams at(int xPanel, int yPanel, int wPanel, int hPanel) {
        LayoutParams lp = new LayoutParams(P(wPanel), P(hPanel), Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = P(xPanel);
        lp.topMargin = P(yPanel);
        return lp;
    }

    private static int ink(float opacity) {
        return Color.argb(Math.round(opacity * 255f), Color.red(INK), Color.green(INK), Color.blue(INK));
    }

    private static int accent(float opacity) {
        return Color.argb(Math.round(opacity * 255f), Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT));
    }

    private static int parchment(float opacity) {
        return Color.argb(Math.round(opacity * 255f), Color.red(NIGHT_PARCHMENT), Color.green(NIGHT_PARCHMENT), Color.blue(NIGHT_PARCHMENT));
    }

    public void onShow(MuseFeed newFeed, String newWeather, boolean isLiveFeed,
                       boolean showMessages, boolean showClock, boolean showWeather) {
        active = true;
        long now = System.currentTimeMillis();
        showStartedAt = now;
        nextBriefAt = now + 300000L;
        briefShowing = false;
        briefStartedAt = 0L;
        briefYieldUntil = 0L;
        firstRender = true;
        nightInitialized = false;
        setFeed(newFeed, newWeather, isLiveFeed, showMessages, showClock, showWeather);
        pumpAlerts();
    }

    public void onHide() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        hideNotificationNow();
    }

    public void stop() {
        onHide();
    }

    public void setWeather(String newWeather) {
        weather = newWeather;
        updateTimeAndEnvironment();
        renderPlaying(false);
    }

    public void setFeed(MuseFeed newFeed, String newWeather, boolean isLiveFeed,
                        boolean showMessages, boolean showClock, boolean showWeather) {
        feed = newFeed;
        weather = newWeather;
        liveFeed = isLiveFeed;
        messagesEnabled = showMessages;
        clockEnabled = showClock;
        weatherEnabled = showWeather;
        updateTimeAndEnvironment();

        String daisySig = daisySignature();
        String nextSig = nextSignature();
        if (!firstRender && briefShowing && (!daisySig.equals(lastDaisySignature) || !nextSig.equals(lastNextSignature))) {
            briefYieldUntil = System.currentTimeMillis() + 60000L;
        }
        lastDaisySignature = daisySig;
        lastNextSignature = nextSig;

        String signature = feedSignature();
        offerNewAlerts();
        if (active) pumpAlerts();
        if (signature.equals(lastFeedSignature) && !firstRender) {
            refreshBriefRotation();
            return;
        }
        lastFeedSignature = signature;
        if (firstRender) {
            populateDynamic();
            firstRender = false;
        } else {
            fadeDynamicOutAndPopulate();
        }
        refreshBriefRotation();
    }

    public void tick() {
        if (!active) return;
        updateTimeAndEnvironment();
        refreshBriefRotation();
        renderPlaying(false);
    }

    private String feedSignature() {
        if (feed == null) return "null";
        StringBuilder s = new StringBuilder();
        s.append(nz(feed.houseMode)).append('|').append(join(feed.whoHome)).append('|')
                .append(nz(feed.daisyStatus)).append('|').append(nz(feed.daisyNote)).append('|')
                .append(nz(feed.nextEventTitle)).append('|').append(nz(feed.nextEventTime)).append('|')
                .append(nz(feed.nowPlaying)).append('|').append(nz(feed.messageText)).append('|')
                .append(nz(feed.briefTitle)).append('|').append(join(feed.briefLines));
        return s.toString();
    }

    private String daisySignature() {
        return feed == null ? "" : nz(feed.daisyStatus) + "|" + nz(feed.daisyNote);
    }

    private String nextSignature() {
        return feed == null ? "" : nz(feed.nextEventTitle) + "|" + nz(feed.nextEventTime);
    }

    private void fadeDynamicOutAndPopulate() {
        final View[] views = new View[]{homeSlot, daisySlot, nextSlot, noteBlock, noteRule, playingBlock};
        for (View v : views) {
            if (v.getVisibility() == VISIBLE) {
                ObjectAnimator a = ObjectAnimator.ofFloat(v, "alpha", v.getAlpha(), 0f);
                a.setDuration(450);
                a.start();
            }
        }
        handler.postDelayed(new Runnable() {
            public void run() {
                if (!active) return;
                populateDynamic();
                View[] after = new View[]{homeSlot, daisySlot, nextSlot, noteBlock, noteRule, playingBlock};
                for (View v : after) {
                    if (v.getVisibility() == VISIBLE && !briefShowing) {
                        ObjectAnimator a = ObjectAnimator.ofFloat(v, "alpha", 0f, 1f);
                        a.setDuration(650);
                        a.start();
                    }
                }
            }
        }, 550L);
    }

    private void populateDynamic() {
        populateLedger();
        populateNote();
        populateBrief();
        renderPlaying(true);
        applyPalette();
    }

    private void populateLedger() {
        boolean stale = feedStale();
        if (feed == null) {
            homeSlot.setVisibility(GONE);
            daisySlot.setVisibility(GONE);
            nextSlot.setVisibility(GONE);
            hairline.setVisibility(GONE);
            return;
        }
        boolean any = false;
        if (!night && feed.whoHome != null && !feed.whoHome.isEmpty()) {
            homeValue.setText(join(feed.whoHome));
            homeSlot.setVisibility(VISIBLE);
            any = true;
        } else {
            homeSlot.setVisibility(GONE);
        }

        boolean daisyPresent = feed.daisyStatus != null || feed.daisyNote != null;
        boolean daisyAtNight = !daisyPresent || isDaisyAway();
        if (daisyPresent && (!night || daisyAtNight)) {
            daisyValue.setText(nz(feed.daisyStatus));
            daisyNote.setText(nz(feed.daisyNote));
            daisyNote.setVisibility(feed.daisyNote == null || feed.daisyNote.trim().isEmpty() ? GONE : VISIBLE);
            daisySlot.setVisibility(VISIBLE);
            any = true;
        } else {
            daisySlot.setVisibility(GONE);
        }

        if (!night && !stale && feed.nextEventTitle != null && !feed.nextEventTitle.trim().isEmpty()) {
            nextValue.setText(feed.nextEventTitle.trim());
            nextTime.setText(nz(feed.nextEventTime));
            nextTime.setVisibility(feed.nextEventTime == null || feed.nextEventTime.trim().isEmpty() ? GONE : VISIBLE);
            nextSlot.setVisibility(VISIBLE);
            any = true;
        } else {
            nextSlot.setVisibility(GONE);
        }
        hairline.setVisibility(any || briefShowing ? VISIBLE : GONE);
    }

    private boolean isDaisyAway() {
        String s = (nz(feed.daisyStatus) + " " + nz(feed.daisyNote)).toLowerCase(Locale.US);
        return s.contains("away") || s.contains(" out") || s.startsWith("out") || s.contains("walker")
                || s.contains("walking") || s.contains("daycare") || s.contains("boarding")
                || s.contains("groom") || s.contains("vet");
    }

    private void populateNote() {
        boolean show = !night && messagesEnabled && feed != null && feed.messageActive();
        if (!show) {
            noteBlock.setVisibility(GONE);
            noteRule.setVisibility(GONE);
            return;
        }
        String noteText = feed.messageText.trim();
        noteBody.setText(noteText);
        // The accent rule spans the note itself, not the whole zone budget:
        // body lines at 90 px plus the attribution row.
        int estimatedLines = Math.max(1, Math.min(3, (int) Math.ceil(noteText.length() / 52.0)));
        LayoutParams ruleParams = (LayoutParams) noteRule.getLayoutParams();
        ruleParams.height = P(estimatedLines == 1 ? 196 : estimatedLines == 2 ? 286 : 376);
        noteRule.setLayoutParams(ruleParams);
        noteBlock.setVisibility(VISIBLE);
        noteRule.setVisibility(VISIBLE);
        noteBlock.setAlpha(1f);
        noteRule.setAlpha(1f);
    }

    private void populateBrief() {
        if (feed == null || !feed.hasBrief()) {
            briefTitle.setText("");
            for (TextView line : briefLines) line.setText("");
            return;
        }
        briefTitle.setText(feed.briefTitle != null ? feed.briefTitle : "Daily Brief");
        for (int i = 0; i < briefLines.size(); i++) {
            briefLines.get(i).setText(i < feed.briefLines.size() ? feed.briefLines.get(i) : "");
        }
    }

    private void renderPlaying(boolean immediate) {
        String raw = feed == null ? null : feed.nowPlaying;
        boolean present = raw != null && !raw.trim().isEmpty()
                && !"Nothing playing".equalsIgnoreCase(raw.trim()) && !feedStale();
        long now = System.currentTimeMillis();
        if (present) {
            playingText = raw.trim();
            playingLastSeenAt = now;
        }
        boolean show = playingText != null && (present || now - playingLastSeenAt < 15000L);
        if (!show) {
            if (playingBlock.getVisibility() == VISIBLE && !immediate) fadeView(playingBlock, false, 1200);
            else {
                playingBlock.setVisibility(GONE);
                playingBlock.setAlpha(0f);
            }
            if (now - playingLastSeenAt >= 15000L) playingText = null;
            return;
        }
        String track = playingText;
        String artist = "";
        int split = playingText.indexOf(" - ");
        if (split > 0 && split < playingText.length() - 3) {
            track = playingText.substring(0, split).trim();
            artist = playingText.substring(split + 3).trim();
        } else {
            int colon = playingText.indexOf(": ");
            if (colon > 0 && colon <= 24) {
                track = playingText.substring(colon + 2).trim();
            }
        }
        playingTrack.setText(track);
        playingArtist.setText(artist);
        playingArtist.setVisibility(artist.isEmpty() ? GONE : VISIBLE);
        playingBlock.setAlpha(night ? 0.48f : 1f);
        if (playingBlock.getVisibility() != VISIBLE) {
            playingBlock.setVisibility(VISIBLE);
            if (!immediate) fadeView(playingBlock, true, 900);
            else playingBlock.setAlpha(night ? 0.48f : 1f);
        }
    }

    private void refreshBriefRotation() {
        if (!active) return;
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        boolean eligible = !night && hour >= 7 && hour < 21 && feed != null && feed.hasBrief()
                && feed.briefLines != null && !feed.briefLines.isEmpty() && now >= briefYieldUntil;
        if (briefShowing) {
            if (!eligible || now - briefStartedAt >= 45000L) {
                setBriefShowing(false);
                nextBriefAt = now + 300000L - 45000L;
            }
            return;
        }
        if (eligible && showStartedAt > 0 && now >= nextBriefAt) {
            setBriefShowing(true);
            briefStartedAt = now;
        }
    }

    private void setBriefShowing(boolean show) {
        if (briefShowing == show) return;
        briefShowing = show;
        if (show) {
            fadeView(homeSlot, false, 800);
            fadeView(daisySlot, false, 800);
            fadeView(nextSlot, false, 800);
            fadeView(noteBlock, false, 800);
            fadeView(noteRule, false, 800);
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (!active || !briefShowing) return;
                    homeSlot.setVisibility(GONE);
                    daisySlot.setVisibility(GONE);
                    nextSlot.setVisibility(GONE);
                    noteBlock.setVisibility(GONE);
                    noteRule.setVisibility(GONE);
                    briefBlock.setVisibility(VISIBLE);
                    fadeView(briefBlock, true, 1000);
                }
            }, 1050L);
        } else {
            fadeView(briefBlock, false, 900);
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (!active || briefShowing) return;
                    briefBlock.setVisibility(GONE);
                    populateLedger();
                    populateNote();
                    View[] views = new View[]{homeSlot, daisySlot, nextSlot, noteBlock, noteRule};
                    for (View v : views) {
                        if (v.getVisibility() == VISIBLE) fadeView(v, true, 900);
                    }
                }
            }, 1150L);
        }
    }

    private void fadeView(final View v, boolean in, int duration) {
        if (v == null) return;
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "alpha", v.getAlpha(), in ? 1f : 0f);
        a.setDuration(duration);
        a.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
        if (!in) {
            a.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (v.getAlpha() == 0f) v.setVisibility(GONE);
                }
            });
        }
        a.start();
    }

    private void updateTimeAndEnvironment() {
        Date nowDate = new Date();
        String formatted = new SimpleDateFormat("h:mm a", Locale.US).format(nowDate);
        int split = formatted.lastIndexOf(' ');
        String digits = split >= 0 ? formatted.substring(0, split) : formatted;
        String meridiem = split >= 0 ? formatted.substring(split + 1).toUpperCase(Locale.US) : "";
        if (!digits.equals(lastClock)) {
            lastClock = digits;
            clockTv.setText(digits);
            meridiemTv.setText(meridiem);
            if (active && !firstRender) {
                ObjectAnimator a = ObjectAnimator.ofFloat(clockRow, "alpha", 0.35f, 1f);
                a.setDuration(600);
                a.start();
            }
        } else {
            meridiemTv.setText(meridiem);
        }

        updateNightState();
        clockRow.setVisibility(clockEnabled ? VISIBLE : GONE);
        modeRow.setVisibility(clockEnabled ? VISIBLE : GONE);
        String fullDate = new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(nowDate);
        if (night) {
            String temp = temperatureOnly();
            dateTv.setText(temp.isEmpty() ? fullDate : fullDate + " \u00B7 " + temp);
            weatherTv.setVisibility(GONE);
        } else {
            dateTv.setText(fullDate);
            weatherTv.setText(weatherEnabled && weather != null ? displayWeather(weather) : "");
            weatherTv.setVisibility(clockEnabled && weatherEnabled ? VISIBLE : GONE);
        }
        dateTv.setVisibility(clockEnabled ? VISIBLE : GONE);

        String mode = feed != null && feed.houseMode != null && !feed.houseMode.trim().isEmpty()
                ? feed.houseMode.trim() : "Home";
        modeTv.setText(mode.toUpperCase(Locale.US));
        modeDot.setMode(mode, night);
        updateDuskOverlay();
    }

    /** Dashboard weather uses title case for the condition; the art face
     *  keeps the WeatherClient sentence-case form it was designed with. */
    private static String displayWeather(String raw) {
        String w = raw.trim();
        int split = w.indexOf(" \u00B7 ");
        if (split < 0 || split + 3 >= w.length()) return w;
        String condition = w.substring(split + 3);
        StringBuilder out = new StringBuilder(w.substring(0, split + 3));
        boolean capitalize = true;
        for (int i = 0; i < condition.length(); i++) {
            char ch = condition.charAt(i);
            if (capitalize && Character.isLetter(ch)) {
                out.append(Character.toUpperCase(ch));
                capitalize = false;
            } else {
                out.append(ch);
                if (ch == ' ') capitalize = true;
            }
        }
        return out.toString();
    }

    private String temperatureOnly() {
        if (weather == null) return "";
        String w = weather.trim();
        int space = w.indexOf(' ');
        return space > 0 ? w.substring(0, space) : w;
    }

    private void updateDuskOverlay() {
        if (night) {
            duskOverlay.setAlpha(0.35f);
            return;
        }
        Calendar cal = Calendar.getInstance();
        int minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int sunset = sunsetMinute(cal);
        float alpha = 0f;
        if (sunset >= 0) {
            if (minute >= sunset && minute < sunset + 30) alpha = 0.16f * (minute - sunset) / 30f;
            else if (minute >= sunset + 30) alpha = 0.16f;
        }
        duskOverlay.setAlpha(alpha);
    }

    /** NOAA solar approximation for Astoria, returned as local minute of day. */
    private int sunsetMinute(Calendar local) {
        int day = local.get(Calendar.DAY_OF_YEAR);
        if (sunsetMinuteCache != -2 && sunsetDayCache == day) return sunsetMinuteCache;
        double lat = 40.7644;
        double lng = -73.9235;
        double gamma = 2.0 * Math.PI / 365.0 * (day - 1 + 0.5);
        double eqtime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma));
        double decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);
        double latRad = Math.toRadians(lat);
        double cosHa = (Math.cos(Math.toRadians(90.833)) / (Math.cos(latRad) * Math.cos(decl))
                - Math.tan(latRad) * Math.tan(decl));
        cosHa = Math.max(-1.0, Math.min(1.0, cosHa));
        double ha = Math.toDegrees(Math.acos(cosHa));
        double utcMinutes = 720.0 - 4.0 * (lng + ha) - eqtime;
        int offsetMinutes = (int) Math.round(TimeZone.getDefault().getOffset(local.getTimeInMillis()) / 60000.0);
        int result = (int) Math.round(utcMinutes + offsetMinutes);
        sunsetMinuteCache = result;
        sunsetDayCache = day;
        return result;
    }

    private void updateNightState() {
        Calendar cal = Calendar.getInstance();
        int minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        boolean modeNight = feed != null && feed.houseMode != null && "Night".equalsIgnoreCase(feed.houseMode.trim());
        boolean timeNight = minute >= 23 * 60 || minute < 6 * 60 + 30;
        boolean newNight = modeNight || timeNight;
        if (!nightInitialized) {
            night = newNight;
            nightInitialized = true;
            nightOverlay.setAlpha(night ? 0.97f : 0f);
            applyPalette();
            populateLedger();
            populateNote();
            return;
        }
        if (newNight == night) return;
        night = newNight;
        final float target = night ? 0.97f : 0f;
        ObjectAnimator a = ObjectAnimator.ofFloat(nightOverlay, "alpha", nightOverlay.getAlpha(), target);
        a.setDuration(30000L);
        a.setInterpolator(new DecelerateInterpolator());
        a.start();
        applyPalette();
        populateLedger();
        populateNote();
        if (night) setBriefShowing(false);
    }

    private void applyPalette() {
        int primary = night ? parchment(0.65f) : ink(0.92f);
        int hero = night ? parchment(0.65f) : ink(0.86f);
        int secondary = night ? parchment(0.48f) : ink(0.72f);
        int label = secondary;
        clockTv.setTextColor(hero);
        meridiemTv.setTextColor(secondary);
        dateTv.setTextColor(primary);
        weatherTv.setTextColor(primary);
        modeTv.setTextColor(night ? parchment(0.42f) : primary);
        hairline.setBackgroundColor(night ? parchment(0.16f) : ink(0.16f));
        setSlotColors(homeSlot, label, primary);
        setSlotColors(daisySlot, label, primary);
        setSlotColors(nextSlot, label, primary);
        daisyNote.setTextColor(secondary);
        nextTime.setTextColor(secondary);
        for (int i = 0; i < briefBlock.getChildCount(); i++) {
            View child = briefBlock.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextColor(primary);
        }
        briefTitle.setTextColor(primary);
        for (TextView line : briefLines) line.setTextColor(primary);
        noteBody.setTextColor(primary);
        noteAttribution.setTextColor(night ? parchment(0.48f) : accent(0.85f));
        noteRule.setBackgroundColor(night ? parchment(0.30f) : accent(0.55f));
        playingTrack.setTextColor(primary);
        playingArtist.setTextColor(secondary);
        if (playingBlock.getVisibility() == VISIBLE) playingBlock.setAlpha(night ? 0.48f : 1f);
    }

    private void setSlotColors(FrameLayout slot, int labelColor, int valueColor) {
        colorizeSlot(slot, labelColor, valueColor, night ? parchment(0.48f) : ink(0.72f));
    }

    private void colorizeSlot(View view, int labelColor, int valueColor, int secondaryColor) {
        if (view instanceof TextView) {
            TextView t = (TextView) view;
            if (t == daisyNote || t == nextTime) {
                t.setTextColor(secondaryColor);
                return;
            }
            String s = t.getText() == null ? "" : t.getText().toString();
            boolean isLabel = "HOME".equals(s) || "DAISY".equals(s) || "NEXT".equals(s);
            t.setTextColor(isLabel ? labelColor : valueColor);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) colorizeSlot(group.getChildAt(i), labelColor, valueColor, secondaryColor);
        }
    }

    private boolean feedStale() {
        if (!liveFeed || feed == null || feed.updatedAt == null) return false;
        long updated = MuseFeed.parseIso(feed.updatedAt);
        return updated > 0 && System.currentTimeMillis() - updated > 45L * 60L * 1000L;
    }

    // Notifications. Feed alerts are single strings, so a category and
    // headline are split only when the producer encoded a short prefix
    // before a colon. No category or fact is invented otherwise.
    private void offerNewAlerts() {
        if (feed == null) return;
        HashSet<String> nowPresent = new HashSet<String>();
        if (feed.alerts != null) {
            for (String raw : feed.alerts) {
                if (raw == null) continue;
                String alert = raw.trim();
                if (alert.isEmpty()) continue;
                nowPresent.add(alert);
                if (!alertsPresent.contains(alert)) enqueueAlert(alert);
            }
        }
        alertsPresent.clear();
        alertsPresent.addAll(nowPresent);
    }

    private void enqueueAlert(String alert) {
        long now = System.currentTimeMillis();
        if (night && !isHighPriority(alert)) return;
        if (alert.equals(activeAlert)) {
            long extended = Math.min(activeAlertStartedAt + 24000L, activeAlertDeadline + 6000L);
            if (extended > activeAlertDeadline) {
                activeAlertDeadline = extended;
                scheduleActiveAlertFade();
            }
            return;
        }
        Long shown = alertShownAt.get(alert);
        if (shown != null && now - shown.longValue() < 30000L) return;
        for (QueuedAlert q : alertQueue) if (q.text.equals(alert)) return;
        if (alertQueue.size() >= 3) alertQueue.poll();
        alertQueue.add(new QueuedAlert(alert, now));
        if (isHighPriority(alert) && activeAlert != null && !isHighPriority(activeAlert) && !alertFading) {
            handler.removeCallbacks(alertFadeRunnable);
            handler.postDelayed(alertFadeRunnable, 1000L);
        } else {
            pumpAlerts();
        }
    }

    private final Runnable alertFadeRunnable = new Runnable() {
        public void run() { fadeOutActiveAlert(1500); }
    };

    private void scheduleActiveAlertFade() {
        handler.removeCallbacks(alertFadeRunnable);
        long delay = Math.max(0L, activeAlertDeadline - System.currentTimeMillis());
        handler.postDelayed(alertFadeRunnable, delay);
    }

    private void pumpAlerts() {
        if (!active || activeAlert != null || alertFading) return;
        QueuedAlert next = null;
        long now = System.currentTimeMillis();
        while (!alertQueue.isEmpty()) {
            QueuedAlert candidate = alertQueue.poll();
            if (now - candidate.queuedAt <= 60000L) {
                next = candidate;
                break;
            }
        }
        if (next == null) return;
        showAlert(next.text);
    }

    private void showAlert(String alert) {
        activeAlert = alert;
        activeAlertStartedAt = System.currentTimeMillis();
        String lower = alert.toLowerCase(Locale.US);
        long hold = lower.contains("doorbell") || lower.contains("front door") ? 18000L : 12000L;
        activeAlertDeadline = activeAlertStartedAt + 500L + hold;

        String category = null;
        String headline = alert;
        int colon = alert.indexOf(':');
        if (colon > 0 && colon <= 20 && colon + 1 < alert.length()) {
            category = alert.substring(0, colon).trim().toUpperCase(Locale.US);
            headline = alert.substring(colon + 1).trim();
        } else if (lower.contains("doorbell")) {
            category = "DOORBELL";
        }
        notificationCategory.setText(category == null ? "" : category);
        notificationCategory.setVisibility(category == null ? GONE : VISIBLE);
        notificationHeadline.setText(headline);
        notificationTime.setText("Just now");
        applyNotificationPalette();
        setNotificationWidth(category, headline);
        notificationBox.setVisibility(VISIBLE);
        notificationBox.post(new Runnable() {
            public void run() {
                FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) notificationBar.getLayoutParams();
                bp.height = notificationBox.getHeight();
                notificationBar.setLayoutParams(bp);
            }
        });
        ObjectAnimator a = ObjectAnimator.ofFloat(notificationBox, "alpha", 0f, 1f);
        a.setDuration(500);
        a.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
        a.start();
        scheduleActiveAlertFade();
    }

    private void applyNotificationPalette() {
        if (night) {
            notificationBox.setBackgroundColor(Color.argb(204, Color.red(NIGHT_VEIL), Color.green(NIGHT_VEIL), Color.blue(NIGHT_VEIL)));
            notificationBar.setBackgroundColor(Color.argb(230, Color.red(NIGHT_ACCENT), Color.green(NIGHT_ACCENT), Color.blue(NIGHT_ACCENT)));
            notificationCategory.setTextColor(Color.argb(235, Color.red(NIGHT_ACCENT), Color.green(NIGHT_ACCENT), Color.blue(NIGHT_ACCENT)));
            notificationHeadline.setTextColor(parchment(0.94f));
            notificationTime.setTextColor(parchment(0.62f));
        } else {
            notificationBox.setBackgroundColor(Color.argb(143, Color.red(PAPER), Color.green(PAPER), Color.blue(PAPER)));
            notificationBar.setBackgroundColor(accent(0.90f));
            notificationCategory.setTextColor(accent(0.92f));
            notificationHeadline.setTextColor(ink(0.94f));
            notificationTime.setTextColor(ink(0.72f));
        }
    }

    private void setNotificationWidth(String category, String headline) {
        Paint paint = new Paint();
        paint.setTypeface(sansMedium);
        paint.setTextSize(P(58));
        float width = paint.measureText(headline == null ? "" : headline);
        paint.setTypeface(sansSemiBold);
        paint.setTextSize(P(44));
        if (category != null) width = Math.max(width, paint.measureText(category) * 1.16f);
        paint.setTypeface(sansRegular);
        paint.setTextSize(P(46));
        width = Math.max(width, paint.measureText("Just now"));
        double widthPanel = width * 4.0 / density + 84.0;
        int clamped = (int) Math.max(680, Math.min(1120, Math.ceil(widthPanel)));
        LayoutParams lp = (LayoutParams) notificationBox.getLayoutParams();
        lp.width = P(clamped);
        notificationBox.setLayoutParams(lp);
    }

    private void fadeOutActiveAlert(int duration) {
        if (activeAlert == null || alertFading) return;
        alertFading = true;
        handler.removeCallbacks(alertFadeRunnable);
        ObjectAnimator a = ObjectAnimator.ofFloat(notificationBox, "alpha", notificationBox.getAlpha(), 0f);
        a.setDuration(duration);
        a.setInterpolator(new DecelerateInterpolator());
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                notificationBox.setVisibility(GONE);
                if (activeAlert != null) alertShownAt.put(activeAlert, System.currentTimeMillis());
                activeAlert = null;
                alertFading = false;
                if (!alertQueue.isEmpty()) {
                    handler.postDelayed(new Runnable() {
                        public void run() { pumpAlerts(); }
                    }, 2000L);
                }
            }
        });
        a.start();
    }

    private void hideNotificationNow() {
        handler.removeCallbacks(alertFadeRunnable);
        notificationBox.setAlpha(0f);
        notificationBox.setVisibility(GONE);
        if (activeAlert != null) alertShownAt.put(activeAlert, System.currentTimeMillis());
        activeAlert = null;
        alertFading = false;
        alertQueue.clear();
    }

    private boolean isHighPriority(String alert) {
        String lower = alert.toLowerCase(Locale.US);
        return lower.contains("doorbell") || lower.contains("front door") || lower.contains("arrival");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String join(List<String> values) {
        if (values == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static class ModeDot extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String mode = "Home";
        private boolean night;

        ModeDot(Context c) {
            super(c);
            setWillNotDraw(false);
        }

        void setMode(String newMode, boolean isNight) {
            mode = newMode == null ? "Home" : newMode;
            night = isNight;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) / 2f;
            if ("Away".equalsIgnoreCase(mode)) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, getWidth() * 0.2f));
                paint.setColor(Color.argb(140, Color.red(INK), Color.green(INK), Color.blue(INK)));
                canvas.drawCircle(cx, cy, r - paint.getStrokeWidth() / 2f, paint);
                return;
            }
            paint.setStyle(Paint.Style.FILL);
            if ("Night".equalsIgnoreCase(mode)) {
                paint.setColor(Color.argb(191, Color.red(UMBER), Color.green(UMBER), Color.blue(UMBER)));
            } else if (night) {
                paint.setColor(parchment(0.42f));
            } else {
                paint.setColor(accent(0.80f));
            }
            canvas.drawCircle(cx, cy, r, paint);
        }
    }
}
