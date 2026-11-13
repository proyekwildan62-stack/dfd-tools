package com.danz.dfdtools;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 5001;
    private static final int REQ_SHIZUKU = 5002;

    private TextView shizukuStatusLabel;
    private TextView licenseStatusLabel;
    private LinearLayout keygenCard;
    private Button launchOverlayBtn;

    private final Shizuku.OnRequestPermissionResultListener shizukuPermListener =
            (requestCode, grantResult) -> runOnUiThread(this::refreshShizukuStatus);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermListener);
        } catch (Throwable ignored) {
        }
        buildDashboardUi();
        refreshShizukuStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermListener);
        } catch (Throwable ignored) {
        }
    }

    private void buildDashboardUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#07030D"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 48, 40, 64);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        ImageView logoView = new ImageView(this);
        logoView.setImageBitmap(LogoHelper.loadCircularLogo(this, 260));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(260, 260);
        logoLp.bottomMargin = 16;
        root.addView(logoView, logoLp);

        TextView appTitle = new TextView(this);
        appTitle.setText("DFD TOOLS // DANZ INJECT");
        appTitle.setTextColor(Color.parseColor("#D946EF"));
        appTitle.setTextSize(20f);
        appTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        appTitle.setGravity(Gravity.CENTER);
        root.addView(appTitle);

        TextView motto = new TextView(this);
        motto.setText("PLAY SMOOTH  •  AIM BETTER  •  BOOYAH MORE");
        motto.setTextColor(Color.parseColor("#A855F7"));
        motto.setTextSize(11f);
        motto.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        motto.setGravity(Gravity.CENTER);
        motto.setPadding(0, 4, 0, 28);
        root.addView(motto);

        final String myHwid = LicenseEngine.getDeviceHwid(this);
        LinearLayout hwidCard = createStyledCard("#130921", "#9333EA");
        TextView hwidText = new TextView(this);
        hwidText.setText("DEVICE HWID (1-KEY 1-DEVICE):\n" + myHwid);
        hwidText.setTextColor(Color.parseColor("#F3E8FF"));
        hwidText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hwidText.setTextSize(12.5f);
        hwidCard.addView(hwidText);

        Button copyHwidBtn = createStyledButton("SALIN HWID SAYA", "#581C87");
        copyHwidBtn.setOnClickListener(v -> {
            copyToClipboard("HWID", myHwid);
            Toast.makeText(this, "HWID disalin: " + myHwid, Toast.LENGTH_SHORT).show();
        });
        hwidCard.addView(copyHwidBtn);
        root.addView(hwidCard);

        LinearLayout shizukuCard = createStyledCard("#130921", "#7E22CE");
        shizukuStatusLabel = new TextView(this);
        shizukuStatusLabel.setTextColor(Color.parseColor("#E9D5FF"));
        shizukuStatusLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        shizukuStatusLabel.setTextSize(12f);
        shizukuCard.addView(shizukuStatusLabel);

        Button bindShizukuBtn = createStyledButton("HUBUNGKAN ADB SHIZUKU (NO ROOT)", "#7E22CE");
        bindShizukuBtn.setOnClickListener(v -> {
            ShizukuShellEngine.requestShizukuPermission(REQ_SHIZUKU);
            refreshShizukuStatus();
        });
        shizukuCard.addView(bindShizukuBtn);
        root.addView(shizukuCard);

        LinearLayout authCard = createStyledCard("#130921", "#C026D3");
        TextView authHeader = new TextView(this);
        authHeader.setText("VERIFIKASI LISENSI (1 KEY 1 DEVICE)");
        authHeader.setTextColor(Color.parseColor("#F0ABFC"));
        authHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        authHeader.setTextSize(13f);
        authCard.addView(authHeader);

        final EditText keyInput = new EditText(this);
        keyInput.setHint("Masukkan License Key / Developer Key...");
        keyInput.setHintTextColor(Color.parseColor("#70528C"));
        keyInput.setTextColor(Color.WHITE);
        keyInput.setSingleLine(true);
        keyInput.setText(LicenseEngine.getSavedKey(this));
        authCard.addView(keyInput);

        licenseStatusLabel = new TextView(this);
        licenseStatusLabel.setText("Status: Belum terautentikasi");
        licenseStatusLabel.setTextColor(Color.parseColor("#FCA5A5"));
        licenseStatusLabel.setTypeface(Typeface.MONOSPACE);
        licenseStatusLabel.setTextSize(11.5f);
        licenseStatusLabel.setPadding(0, 8, 0, 12);
        authCard.addView(licenseStatusLabel);

        Button verifyBtn = createStyledButton("AKTIFKAN LISENSI & BUKA FITUR", "#A21CAF");
        authCard.addView(verifyBtn);

        launchOverlayBtn = createStyledButton("JALANKAN FLOATING DFD TOOLS IN-GAME", "#16A34A");
        launchOverlayBtn.setEnabled(false);
        launchOverlayBtn.setAlpha(0.4f);
        launchOverlayBtn.setOnClickListener(v -> startDfdFloatingOverlay());
        authCard.addView(launchOverlayBtn);
        root.addView(authCard);

        keygenCard = createStyledCard("#1E0B36", "#E879F9");
        keygenCard.setVisibility(View.GONE);

        TextView kgTitle = new TextView(this);
        kgTitle.setText("👑 DANZVIP DEVELOPER / RESELLER KEYGEN\n(1 Key 1 Device Cryptographic Generator)");
        kgTitle.setTextColor(Color.parseColor("#F5D0FE"));
        kgTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        kgTitle.setTextSize(13f);
        keygenCard.addView(kgTitle);

        final EditText targetHwidInput = new EditText(this);
        targetHwidInput.setHint("Target HWID Pembeli (Contoh: DFD-8F1A2C9B atau kosongkan=AutoBind)");
        targetHwidInput.setHintTextColor(Color.parseColor("#8B6FA8"));
        targetHwidInput.setTextColor(Color.WHITE);
        targetHwidInput.setSingleLine(true);
        targetHwidInput.setText(myHwid);
        keygenCard.addView(targetHwidInput);

        final Spinner tierSpinner = new Spinner(this);
        String[] tierItems = new String[]{
                "1 DAY (1 Hari - 1 Device)",
                "3 DAYS (3 Hari - 1 Device)",
                "7 DAYS (7 Hari - 1 Device)",
                "15 DAYS (15 Hari - 1 Device)",
                "30 DAYS (30 Hari - 1 Device)",
                "PERMANENT (Selamanya - 1 Device)",
                "RESELLER (Akses Buat Key)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, tierItems);
        tierSpinner.setAdapter(adapter);
        keygenCard.addView(tierSpinner);

        final TextView generatedKeyOutput = new TextView(this);
        generatedKeyOutput.setText("Generated Key: -");
        generatedKeyOutput.setTextColor(Color.parseColor("#4ADE80"));
        generatedKeyOutput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        generatedKeyOutput.setTextSize(12.5f);
        generatedKeyOutput.setPadding(0, 14, 0, 14);
        keygenCard.addView(generatedKeyOutput);

        Button generateBtn = createStyledButton("CREATE KEY & SALIN KE CLIPBOARD", "#C026D3");
        generateBtn.setOnClickListener(v -> {
            LicenseEngine.Tier[] tiers = LicenseEngine.Tier.values();
            LicenseEngine.Tier selected = tiers[tierSpinner.getSelectedItemPosition()];
            String createdKey = LicenseEngine.generateKey(targetHwidInput.getText().toString(), selected);
            generatedKeyOutput.setText("KEY: " + createdKey);
            copyToClipboard("DFD_KEY", createdKey);
            Toast.makeText(this, "Key " + selected.label + " berhasil dibuat & disalin!", Toast.LENGTH_SHORT).show();
        });
        keygenCard.addView(generateBtn);
        root.addView(keygenCard);

        verifyBtn.setOnClickListener(v -> {
            String entered = keyInput.getText().toString();
            LicenseEngine.AuthResult res = LicenseEngine.verifyAndBindKey(this, entered);
            if (res.valid) {
                licenseStatusLabel.setText("✔ " + res.message);
                licenseStatusLabel.setTextColor(Color.parseColor("#4ADE80"));
                launchOverlayBtn.setEnabled(true);
                launchOverlayBtn.setAlpha(1.0f);

                if (res.isDeveloper || res.isReseller) {
                    keygenCard.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Panel Create Key Developer/Reseller Terbuka!", Toast.LENGTH_SHORT).show();
                } else {
                    keygenCard.setVisibility(View.GONE);
                }
            } else {
                licenseStatusLabel.setText("✖ " + res.message);
                licenseStatusLabel.setTextColor(Color.parseColor("#F87171"));
                launchOverlayBtn.setEnabled(false);
                launchOverlayBtn.setAlpha(0.4f);
                keygenCard.setVisibility(View.GONE);
            }
        });

        setContentView(scroll);

        if (!keyInput.getText().toString().isEmpty()) {
            verifyBtn.performClick();
        }
    }

    private void refreshShizukuStatus() {
        boolean ready = ShizukuShellEngine.isShizukuReady();
        if (ready) {
            shizukuStatusLabel.setText("⚡ ENGINE: ADB SHIZUKU TERHUBUNG (UID 2000 ACTIVE)\nSemua 12 Fitur Siap Injeksi ke Sistem In-Game.");
            shizukuStatusLabel.setTextColor(Color.parseColor("#4ADE80"));
        } else {
            shizukuStatusLabel.setText("⚠ ENGINE: SHIZUKU BELUM DIIZINKAN\nBuka aplikasi Shizuku (Wireless Debugging) lalu klik tombol di bawah.");
            shizukuStatusLabel.setTextColor(Color.parseColor("#FBBF24"));
        }
    }

    private void startDfdFloatingOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
            Toast.makeText(this, "Izinkan 'Tampilkan di atas aplikasi lain' untuk DFD TOOLS", Toast.LENGTH_LONG).show();
            return;
        }
        Intent svc = new Intent(this, DfdOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        Toast.makeText(this, "DFD TOOLS Floating Panel Aktif! Buka Free Fire sekarang.", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout createStyledCard(String bgHex, String borderHex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 24, 28, 24);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setStroke(3, Color.parseColor(borderHex));
        gd.setCornerRadius(20f);
        card.setBackground(gd);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 22;
        card.setLayoutParams(lp);
        return card;
    }

    private Button createStyledButton(String text, String colorHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setTextSize(12f);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(colorHex));
        gd.setCornerRadius(14f);
        gd.setStroke(2, Color.parseColor("#F0ABFC"));
        btn.setBackground(gd);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 14;
        btn.setLayoutParams(lp);
        return btn;
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
        }
    }
}
