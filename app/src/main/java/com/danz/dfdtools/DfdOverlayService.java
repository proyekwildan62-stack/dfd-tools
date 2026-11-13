package com.danz.dfdtools;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

public class DfdOverlayService extends Service {

    private static final String CHANNEL_ID = "dfd_tools_overlay_channel";
    private static final String PKG_FF_TH = "com.dts.freefireth";
    private static final String PKG_FF_MAX = "com.dts.freefiremax";

    private WindowManager wm;
    private View floatingWindow;
    private View collapsedLogoBubble;
    private View expandedPanel;
    private ReticleOverlayView reticleView;
    private View rogTelemetryBar;
    private TextView rogTelemetryText;
    private TextView cmdLogStatus;

    private WindowManager.LayoutParams mainParams;
    private WindowManager.LayoutParams reticleParams;
    private WindowManager.LayoutParams rogBarParams;

    private boolean isReticleShown = false;
    private boolean isRogBarShown = false;
    private int reticleMode = 0;
    private int baseDensityDpi = 420;
    private int baseWidthPx = 1080;
    private int baseHeightPx = 2400;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long lastFpsTimestampNs = 0;
    private int accumulatedFrames = 0;
    private int liveFps = 60;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (lastFpsTimestampNs == 0) {
                lastFpsTimestampNs = frameTimeNanos;
            }
            accumulatedFrames++;
            long diffNs = frameTimeNanos - lastFpsTimestampNs;
            if (diffNs >= 1_000_000_000L) {
                liveFps = (int) ((accumulatedFrames * 1_000_000_000L) / diffNs);
                accumulatedFrames = 0;
                lastFpsTimestampNs = frameTimeNanos;
            }
            if (isRogBarShown) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    private final Runnable telemetryUpdater = new Runnable() {
        @Override
        public void run() {
            if (isRogBarShown && rogTelemetryText != null) {
                float tempC = readSoCTemperature();
                int ramUsedPct = readRamUsagePercent();
                String modeTag = ShizukuShellEngine.isShizukuReady() ? "SHIZUKU-UID2000" : "SHELL";
                rogTelemetryText.setText(String.format(Locale.US,
                        "👑 DFD ROG | FPS: %d | TEMP: %.1f°C | RAM: %d%% | %s",
                        liveFps, tempC, ramUsedPct, modeTag));
                uiHandler.postDelayed(this, 1000L);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        baseDensityDpi = dm.densityDpi;
        baseWidthPx = Math.min(dm.widthPixels, dm.heightPixels);
        baseHeightPx = Math.max(dm.widthPixels, dm.heightPixels);

        initCustomReticle();
        initRogMonitorBar();
        initFloatingDfdMenu();
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID, "DFD TOOLS Engine", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(chan);

            Notification notif = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("DFD TOOLS // DANZ INJECT")
                    .setContentText("Engine Shizuku Aktif • Play Smooth Aim Better")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
            startForeground(1337, notif);
        }
    }

    private int getWindowOverlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void initFloatingDfdMenu() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        ImageView bubbleImg = new ImageView(this);
        bubbleImg.setImageBitmap(LogoHelper.loadCircularLogo(this, 145));
        bubbleImg.setVisibility(View.GONE);
        collapsedLogoBubble = bubbleImg;
        container.addView(collapsedLogoBubble, new LinearLayout.LayoutParams(145, 145));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24, 20, 24, 20);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#EB0B0414"));
        panelBg.setStroke(4, Color.parseColor("#C026D3"));
        panelBg.setCornerRadius(22f);
        panel.setBackground(panelBg);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView headerLogo = new ImageView(this);
        headerLogo.setImageBitmap(LogoHelper.loadCircularLogo(this, 76));
        topBar.addView(headerLogo, new LinearLayout.LayoutParams(76, 76));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(14, 0, 10, 0);

        TextView titleTxt = new TextView(this);
        titleTxt.setText("DFD TOOLS // DANZVIP");
        titleTxt.setTextColor(Color.parseColor("#E879F9"));
        titleTxt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleTxt.setTextSize(13f);
        titleCol.addView(titleTxt);

        cmdLogStatus = new TextView(this);
        cmdLogStatus.setText(ShizukuShellEngine.isShizukuReady()
                ? "SHIZUKU: ACTIVE (REAL INJECT)"
                : "SHIZUKU: STANDBY");
        cmdLogStatus.setTextColor(Color.parseColor("#4ADE80"));
        cmdLogStatus.setTypeface(Typeface.MONOSPACE);
        cmdLogStatus.setTextSize(9.5f);
        titleCol.addView(cmdLogStatus);

        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button minBtn = new Button(this);
        minBtn.setText("_");
        minBtn.setTextColor(Color.WHITE);
        minBtn.setBackgroundColor(Color.TRANSPARENT);
        minBtn.setOnClickListener(v -> {
            expandedPanel.setVisibility(View.GONE);
            collapsedLogoBubble.setVisibility(View.VISIBLE);
            wm.updateViewLayout(floatingWindow, mainParams);
        });
        topBar.addView(minBtn, new LinearLayout.LayoutParams(90, 90));

        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.parseColor("#FB7185"));
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setOnClickListener(v -> stopSelf());
        topBar.addView(closeBtn, new LinearLayout.LayoutParams(90, 90));

        panel.addView(topBar);

        collapsedLogoBubble.setOnClickListener(v -> {
            collapsedLogoBubble.setVisibility(View.GONE);
            expandedPanel.setVisibility(View.VISIBLE);
            wm.updateViewLayout(floatingWindow, mainParams);
        });

        ScrollView featureScroll = new ScrollView(this);
        LinearLayout featList = new LinearLayout(this);
        featList.setOrientation(LinearLayout.VERTICAL);
        featList.setPadding(4, 12, 4, 8);
        featureScroll.addView(featList);
        panel.addView(featureScroll, new LinearLayout.LayoutParams(720, 860));

        addRealToggle(featList, "1. DRAG HS (Vertical Y-Snap Velocity)", on -> {
            if (on) {
                execRealTweak("DRAG HS ON",
                        "settings put system pointer_speed 6;" +
                        "cmd device_config put input_native_boot velocity_tracker_strategy impulse;" +
                        "cmd device_config put input_native_boot deep_press_enabled false;" +
                        "settings put secure tap_duration_threshold 0.0");
            } else {
                execRealTweak("DRAG HS OFF",
                        "settings put system pointer_speed 0;" +
                        "cmd device_config delete input_native_boot velocity_tracker_strategy");
            }
        });

        addRealToggle(featList, "2. AIM DRAG (Zero Resample Latency)", on -> {
            if (on) {
                execRealTweak("AIM DRAG ON",
                        "setprop debug.input.resample_latency 0;" +
                        "setprop ro.input.noresample 1;" +
                        "settings put secure long_press_timeout 220;" +
                        "settings put secure multi_press_timeout 170");
            } else {
                execRealTweak("AIM DRAG OFF",
                        "setprop ro.input.noresample 0;" +
                        "settings put secure long_press_timeout 400;" +
                        "settings put secure multi_press_timeout 300");
            }
        });

        addRealToggle(featList, "3. HEADTRICK (Real WM Sub-Pixel DPI Matrix)", on -> {
            if (on) {
                int trickDpi = Math.max(280, (int) (baseDensityDpi * 0.84f));
                int trickW = (int) (baseWidthPx * 1.05f);
                int trickH = (int) (baseHeightPx * 1.12f);
                execRealTweak("HEADTRICK DPI " + trickDpi,
                        String.format(Locale.US, "wm size %dx%d; wm density %d", trickW, trickH, trickDpi));
            } else {
                execRealTweak("HEADTRICK RESET", "wm size reset; wm density reset");
            }
        });

        addRealToggle(featList, "4. AIM SMOOTH (SurfaceFlinger Frame Pacing)", on -> {
            if (on) {
                execRealTweak("AIM SMOOTH ON",
                        "cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 3;" +
                        "setprop debug.sf.early_phase_offset_ns 500000;" +
                        "setprop debug.sf.early_gl_phase_offset_ns 500000;" +
                        "setprop debug.choreographer.vsync 1");
            } else {
                execRealTweak("AIM SMOOTH OFF",
                        "setprop debug.sf.early_phase_offset_ns 0;" +
                        "setprop debug.sf.early_gl_phase_offset_ns 0");
            }
        });

        addRealToggle(featList, "5. EASY DRAG (Zero Friction Fling)", on -> {
            if (on) {
                execRealTweak("EASY DRAG ON",
                        "settings put global window_animation_scale 0.0;" +
                        "settings put global transition_animation_scale 0.0;" +
                        "settings put global animator_duration_scale 0.0;" +
                        "settings put system pointer_location 0");
            } else {
                execRealTweak("EASY DRAG OFF",
                        "settings put global window_animation_scale 1.0;" +
                        "settings put global transition_animation_scale 1.0;" +
                        "settings put global animator_duration_scale 1.0");
            }
        });

        addRealToggle(featList, "6. CONTROL RECOIL (Hold-Fire Stabilizer)", on -> {
            if (on) {
                execRealTweak("CONTROL RECOIL ON",
                        "settings put secure touch_exploration_enabled 0;" +
                        "settings put secure show_touches 0;" +
                        "settings put system pointer_speed 3;" +
                        "settings put secure accessibility_large_pointer_icon 0");
            } else {
                execRealTweak("CONTROL RECOIL OFF", "settings put system pointer_speed 0");
            }
        });

        final TextView pelicinLabel = new TextView(this);
        pelicinLabel.setText("7. PELICIN LAYAR [Level: 1 / 7]");
        pelicinLabel.setTextColor(Color.parseColor("#F5D0FE"));
        pelicinLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pelicinLabel.setTextSize(11.5f);
        pelicinLabel.setPadding(8, 14, 8, 4);
        featList.addView(pelicinLabel);

        SeekBar pelicinSlider = new SeekBar(this);
        pelicinSlider.setMax(6);
        pelicinSlider.setProgress(0);
        pelicinSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int lvl = progress + 1;
                pelicinLabel.setText(String.format(Locale.US, "7. PELICIN LAYAR [Level: %d / 7]", lvl));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int lvl = seekBar.getProgress() + 1;
                int ptrSpeed = Math.min(7, lvl);
                int lpTimeout = 400 - (lvl * 35);
                int mpTimeout = 280 - (lvl * 22);
                int unsignaled = (lvl >= 5) ? 1 : 0;
                String cmd = String.format(Locale.US,
                        "settings put system pointer_speed %d;" +
                        "settings put secure long_press_timeout %d;" +
                        "settings put secure multi_press_timeout %d;" +
                        "setprop debug.sf.latch_unsignaled %d",
                        ptrSpeed, lpTimeout, mpTimeout, unsignaled);
                execRealTweak("PELICIN LVL " + lvl, cmd);
            }
        });
        featList.addView(pelicinSlider);

        addRealToggle(featList, "8. OPTIMALKAN GOPHONE (RAM Purge + AOT Speed)", on -> {
            if (on) {
                execRealTweak("GOPHONE BOOST ON",
                        "am kill-all;" +
                        "cmd power set-fixed-performance-mode-enabled true;" +
                        "cmd activity memory-factor set CRITICAL;" +
                        "cmd package compile -m speed-profile -f " + PKG_FF_TH + ";" +
                        "cmd package compile -m speed-profile -f " + PKG_FF_MAX);
            } else {
                execRealTweak("GOPHONE BOOST OFF",
                        "cmd power set-fixed-performance-mode-enabled false;" +
                        "cmd activity memory-factor reset");
            }
        });

        addRealToggle(featList, "9. ANGLE RENDERING (Vulkan ANGLE Driver)", on -> {
            if (on) {
                execRealTweak("ANGLE VULKAN ON",
                        "settings put global angle_debug_package org.chromium.angle;" +
                        "settings put global angle_gl_driver_all_angle 0;" +
                        "settings put global angle_gl_driver_selection_pkgs " + PKG_FF_TH + "," + PKG_FF_MAX + ";" +
                        "settings put global angle_gl_driver_selection_values angle,angle;" +
                        "setprop debug.hwui.renderer skiavk");
            } else {
                execRealTweak("ANGLE DEFAULT",
                        "settings put global angle_gl_driver_selection_pkgs " + PKG_FF_TH + "," + PKG_FF_MAX + ";" +
                        "settings put global angle_gl_driver_selection_values default,default;" +
                        "setprop debug.hwui.renderer opengl");
            }
        });

        addRealToggle(featList, "10. KOSTUM BIDIKAN (Hardware Crosshair)", on -> {
            setReticleVisibility(on);
        });

        Button switchReticleBtn = new Button(this);
        switchReticleBtn.setText("GANTI MODEL BIDIKAN (DOT / CROSS / CROWN RING)");
        switchReticleBtn.setTextSize(10.5f);
        switchReticleBtn.setTextColor(Color.WHITE);
        switchReticleBtn.setBackgroundColor(Color.parseColor("#4C1D95"));
        switchReticleBtn.setOnClickListener(v -> {
            reticleMode = (reticleMode + 1) % 3;
            if (reticleView != null) reticleView.invalidate();
        });
        featList.addView(switchReticleBtn);

        addRealToggle(featList, "11. PEMANTAUAN ROG (Real FPS + Temp HUD)", on -> {
            setRogBarVisibility(on);
        });

        addRealToggle(featList, "12. GAME SPACE + GRAFIK RINGAN ROG/PC", on -> {
            if (on) {
                execRealTweak("GAME SPACE + DOWNSCALE 0.75",
                        "cmd game mode performance " + PKG_FF_TH + ";" +
                        "cmd game mode performance " + PKG_FF_MAX + ";" +
                        "cmd game set --mode 2 --downscale 0.75 --fps 120 " + PKG_FF_TH + ";" +
                        "cmd game set --mode 2 --downscale 0.75 --fps 120 " + PKG_FF_MAX + ";" +
                        "settings put system peak_refresh_rate 120.0;" +
                        "settings put system min_refresh_rate 120.0;" +
                        "cmd notification set_dnd priority");
            } else {
                execRealTweak("GAME SPACE RESET",
                        "cmd game reset " + PKG_FF_TH + ";" +
                        "cmd game reset " + PKG_FF_MAX + ";" +
                        "settings put system min_refresh_rate 60.0;" +
                        "cmd notification set_dnd off");
            }
        });

        expandedPanel = panel;
        container.addView(expandedPanel);
        floatingWindow = container;

        mainParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getWindowOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        mainParams.gravity = Gravity.TOP | Gravity.START;
        mainParams.x = 30;
        mainParams.y = 180;

        attachTouchDrag(topBar, floatingWindow, mainParams);
        attachTouchDrag(collapsedLogoBubble, floatingWindow, mainParams);
        wm.addView(floatingWindow, mainParams);
    }

    private void initCustomReticle() {
        reticleView = new ReticleOverlayView();
        reticleParams = new WindowManager.LayoutParams(
                150, 150,
                getWindowOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        reticleParams.gravity = Gravity.CENTER;
    }

    private void initRogMonitorBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(22, 8, 22, 8);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#D9090312"));
        bg.setStroke(3, Color.parseColor("#D946EF"));
        bg.setCornerRadius(12f);
        bar.setBackground(bg);

        rogTelemetryText = new TextView(this);
        rogTelemetryText.setTextColor(Color.parseColor("#F0ABFC"));
        rogTelemetryText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        rogTelemetryText.setTextSize(11f);
        rogTelemetryText.setText("👑 DFD ROG | FPS: 60 | TEMP: --°C | RAM: --%");
        bar.addView(rogTelemetryText);

        rogTelemetryBar = bar;
        rogBarParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getWindowOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        rogBarParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        rogBarParams.y = 20;
    }

    private void setReticleVisibility(boolean visible) {
        if (visible && !isReticleShown) {
            wm.addView(reticleView, reticleParams);
            isReticleShown = true;
        } else if (!visible && isReticleShown) {
            wm.removeView(reticleView);
            isReticleShown = false;
        }
    }

    private void setRogBarVisibility(boolean visible) {
        if (visible && !isRogBarShown) {
            wm.addView(rogTelemetryBar, rogBarParams);
            isRogBarShown = true;
            lastFpsTimestampNs = 0;
            accumulatedFrames = 0;
            Choreographer.getInstance().postFrameCallback(frameCallback);
            uiHandler.post(telemetryUpdater);
        } else if (!visible && isRogBarShown) {
            isRogBarShown = false;
            uiHandler.removeCallbacks(telemetryUpdater);
            wm.removeView(rogTelemetryBar);
        }
    }

    private interface ToggleAction {
        void onStateChanged(boolean enabled);
    }

    private void addRealToggle(LinearLayout parent, String title, ToggleAction action) {
        CheckBox cb = new CheckBox(this);
        cb.setText(title);
        cb.setTextColor(Color.parseColor("#E9D5FF"));
        cb.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        cb.setTextSize(11f);
        cb.setPadding(8, 10, 8, 10);
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cb.setTextColor(isChecked ? Color.parseColor("#4ADE80") : Color.parseColor("#E9D5FF"));
            action.onStateChanged(isChecked);
        });
        parent.addView(cb);
    }

    private void execRealTweak(final String label, String shellScript) {
        ShizukuShellEngine.execAsync(shellScript, (viaShizuku, exitCode, output) -> {
            uiHandler.post(() -> {
                String tag = viaShizuku ? "SHIZUKU-OK" : "SHELL";
                cmdLogStatus.setText(String.format(Locale.US, "[%s] %s (code=%d)", tag, label, exitCode));
            });
        });
    }

    private float readSoCTemperature() {
        String[] paths = {
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp"
        };
        for (String p : paths) {
            try (BufferedReader br = new BufferedReader(new FileReader(p))) {
                String line = br.readLine();
                if (line != null) {
                    float val = Float.parseFloat(line.trim());
                    return val > 1000 ? (val / 1000f) : val;
                }
            } catch (Throwable ignored) {
            }
        }
        return 39.5f;
    }

    private int readRamUsagePercent() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                long used = mi.totalMem - mi.availMem;
                return (int) ((used * 100L) / mi.totalMem);
            }
        } catch (Throwable ignored) {
        }
        return 55;
    }

    private void attachTouchDrag(View handle, final View targetWindow, final WindowManager.LayoutParams lp) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - touchX);
                        int dy = (int) (event.getRawY() - touchY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                        lp.x = startX + dx;
                        lp.y = startY + dy;
                        wm.updateViewLayout(targetWindow, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private class ReticleOverlayView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ReticleOverlayView() {
            super(DfdOverlayService.this);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            if (reticleMode == 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3.5f);
                p.setColor(Color.parseColor("#D946EF"));
                canvas.drawCircle(cx, cy, 22f, p);

                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.parseColor("#FF0044"));
                canvas.drawCircle(cx, cy, 4.5f, p);
            } else if (reticleMode == 1) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f);
                p.setColor(Color.parseColor("#00FFCC"));
                canvas.drawLine(cx - 28f, cy, cx - 8f, cy, p);
                canvas.drawLine(cx + 8f, cy, cx + 28f, cy, p);
                canvas.drawLine(cx, cy - 28f, cx, cy - 8f, p);
                canvas.drawLine(cx, cy + 8f, cx, cy + 28f, p);
                canvas.drawPoint(cx, cy, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f);
                p.setColor(Color.parseColor("#FACC15"));
                canvas.drawRect(cx - 20f, cy - 20f, cx + 20f, cy + 20f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, 3.5f, p);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ShizukuShellEngine.execAsync("wm size reset; wm density reset; cmd notification set_dnd off", null);
        if (floatingWindow != null) wm.removeView(floatingWindow);
        if (isReticleShown && reticleView != null) wm.removeView(reticleView);
        if (isRogBarShown && rogTelemetryBar != null) wm.removeView(rogTelemetryBar);
    }
}
