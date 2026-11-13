package com.danz.dfdtools;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public final class ShizukuShellEngine {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface ExecutionCallback {
        void onComplete(boolean viaShizuku, int exitCode, String output);
    }

    public static boolean isShizukuReady() {
        try {
            if (!Shizuku.pingBinder()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestShizukuPermission(int requestCode) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void execAsync(final String commandBlock, final ExecutionCallback callback) {
        EXECUTOR.execute(() -> {
            boolean usedShizuku = false;
            int code = -1;
            StringBuilder out = new StringBuilder();
            Process proc = null;

            try {
                if (isShizukuReady()) {
                    usedShizuku = true;
                    Method newProcMethod = Shizuku.class.getDeclaredMethod(
                            "newProcess", String[].class, String[].class, String.class);
                    newProcMethod.setAccessible(true);
                    proc = (ShizukuRemoteProcess) newProcMethod.invoke(
                            null, new String[]{"sh"}, null, null);
                } else {
                    proc = Runtime.getRuntime().exec(new String[]{"sh"});
                }

                if (proc != null) {
                    OutputStream os = proc.getOutputStream();
                    os.write((commandBlock + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();

                    BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        out.append(line).append('\n');
                    }
                    br.close();
                    code = proc.waitFor();
                }
            } catch (Throwable e) {
                out.append("ERR: ").append(e.getMessage());
            } finally {
                if (proc != null) proc.destroy();
            }

            if (callback != null) {
                callback.onComplete(usedShizuku, code, out.toString().trim());
            }
        });
    }
}
