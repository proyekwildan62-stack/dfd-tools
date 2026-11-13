package com.danz.dfdtools;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class LicenseEngine {

    public static final String MASTER_DEV_KEY = "DANZVIP";
    private static final String HMAC_SECRET = "DFD_TOOLS_DANZ_INJECT_2026_PURPLE_CROWN_SECRET_KEY";
    private static final String PREF_NAME = "dfd_license_vault";
    private static final String KEY_SAVED_TOKEN = "bound_token";
    private static final String KEY_SAVED_HWID = "bound_hwid";
    private static final String KEY_FIRST_BIND_MS = "first_bind_ms";

    public enum Tier {
        DAY_1("1D", 1L * 86400_000L, "1 HARI"),
        DAY_3("3D", 3L * 86400_000L, "3 HARI"),
        DAY_7("7D", 7L * 86400_000L, "7 HARI"),
        DAY_15("15D", 15L * 86400_000L, "15 HARI"),
        DAY_30("30D", 30L * 86400_000L, "30 HARI"),
        PERM("PERM", 36500L * 86400_000L, "PERMANENT"),
        RESELLER("RSL", 36500L * 86400_000L, "RESELLER PANEL");

        public final String code;
        public final long durationMs;
        public final String label;

        Tier(String code, long durationMs, String label) {
            this.code = code;
            this.durationMs = durationMs;
            this.label = label;
        }

        public static Tier fromCode(String code) {
            for (Tier t : values()) {
                if (t.code.equalsIgnoreCase(code)) return t;
            }
            return null;
        }
    }

    public static class AuthResult {
        public final boolean valid;
        public final boolean isDeveloper;
        public final boolean isReseller;
        public final String message;
        public final long remainingMs;

        public AuthResult(boolean valid, boolean isDeveloper, boolean isReseller, String message, long remainingMs) {
            this.valid = valid;
            this.isDeveloper = isDeveloper;
            this.isReseller = isReseller;
            this.message = message;
            this.remainingMs = remainingMs;
        }
    }

    public static String getDeviceHwid(Context ctx) {
        String androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) androidId = "UNKNOWN_AID";
        String raw = androidId + "|" + Build.BOARD + "|" + Build.BRAND + "|" + Build.HARDWARE;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format(Locale.US, "%02X", digest[i]));
            }
            return "DFD-" + sb.toString();
        } catch (Exception e) {
            return "DFD-00000000";
        }
    }

    public static String generateKey(String targetHwid, Tier tier) {
        String cleanHwid = (targetHwid == null || targetHwid.trim().isEmpty())
                ? "BIND1"
                : targetHwid.trim().toUpperCase(Locale.US).replace("DFD-", "");
        long issueEpochHours = System.currentTimeMillis() / 3600_000L;
        String payload = tier.code + "-" + cleanHwid + "-" + Long.toHexString(issueEpochHours).toUpperCase(Locale.US);
        String sig = computeHmacHex(payload).substring(0, 8);
        return "DFD-" + payload + "-" + sig;
    }

    public static AuthResult verifyAndBindKey(Context ctx, String inputKey) {
        if (inputKey == null) {
            return new AuthResult(false, false, false, "Key kosong.", 0);
        }
        String key = inputKey.trim();

        if (MASTER_DEV_KEY.equals(key)) {
            saveBoundSession(ctx, key, getDeviceHwid(ctx));
            return new AuthResult(true, true, true, "DEVELOPER MASTER UNLOCKED (DANZVIP)", Long.MAX_VALUE);
        }

        String[] parts = key.split("-");
        if (parts.length != 5 || !"DFD".equalsIgnoreCase(parts[0])) {
            return new AuthResult(false, false, false, "Format key tidak valid.", 0);
        }

        String tierCode = parts[1].toUpperCase(Locale.US);
        String keyHwidPart = parts[2].toUpperCase(Locale.US);
        String issueHex = parts[3].toUpperCase(Locale.US);
        String providedSig = parts[4].toUpperCase(Locale.US);

        Tier tier = Tier.fromCode(tierCode);
        if (tier == null) {
            return new AuthResult(false, false, false, "Tier durasi key tidak dikenali.", 0);
        }

        String payload = tierCode + "-" + keyHwidPart + "-" + issueHex;
        String expectedSig = computeHmacHex(payload).substring(0, 8);
        if (!expectedSig.equals(providedSig)) {
            return new AuthResult(false, false, false, "Signature key palsu / rusak.", 0);
        }

        String currentDeviceHwid = getDeviceHwid(ctx);
        String currentShortHwid = currentDeviceHwid.replace("DFD-", "");

        if (!"BIND1".equals(keyHwidPart) && !currentShortHwid.equals(keyHwidPart)) {
            return new AuthResult(false, false, false,
                    "KEY DITOLAK: Key ini terkunci pada HWID DFD-" + keyHwidPart + " (1 Key 1 Device).", 0);
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedToken = prefs.getString(KEY_SAVED_TOKEN, null);
        String savedHwid = prefs.getString(KEY_SAVED_HWID, null);
        long firstBindMs = prefs.getLong(KEY_FIRST_BIND_MS, 0L);

        long issueMs;
        try {
            issueMs = Long.parseLong(issueHex, 16) * 3600_000L;
        } catch (NumberFormatException e) {
            return new AuthResult(false, false, false, "Timestamp key korup.", 0);
        }

        long now = System.currentTimeMillis();
        long startAnchor = (key.equals(savedToken) && firstBindMs > 0) ? firstBindMs : Math.min(now, issueMs);
        if (!key.equals(savedToken)) {
            prefs.edit()
                    .putString(KEY_SAVED_TOKEN, key)
                    .putString(KEY_SAVED_HWID, currentDeviceHwid)
                    .putLong(KEY_FIRST_BIND_MS, now)
                    .apply();
            startAnchor = now;
        } else if (savedHwid != null && !savedHwid.equals(currentDeviceHwid)) {
            return new AuthResult(false, false, false, "HWID Mismatch! 1 Key 1 Device terdeteksi.", 0);
        }

        long elapsedSinceIssue = Math.max(0L, now - issueMs);
        long elapsedSinceBind = Math.max(0L, now - startAnchor);
        long effectiveElapsed = Math.max(elapsedSinceIssue, elapsedSinceBind);

        if (effectiveElapsed > tier.durationMs) {
            return new AuthResult(false, false, false, "Key sudah EXPIRED (" + tier.label + ").", 0);
        }

        long remaining = tier.durationMs - effectiveElapsed;
        boolean isReseller = (tier == Tier.RESELLER);
        return new AuthResult(true, false, isReseller,
                "AKTIF [" + tier.label + "] • HWID: " + currentDeviceHwid, remaining);
    }

    private static void saveBoundSession(Context ctx, String token, String hwid) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SAVED_TOKEN, token)
                .putString(KEY_SAVED_HWID, hwid)
                .putLong(KEY_FIRST_BIND_MS, System.currentTimeMillis())
                .apply();
    }

    public static String getSavedKey(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_SAVED_TOKEN, "");
    }

    private static String computeHmacHex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec spec = new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(spec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format(Locale.US, "%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "0000000000000000";
        }
    }
}
