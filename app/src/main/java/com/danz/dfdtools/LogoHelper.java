package com.danz.dfdtools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.io.InputStream;

public final class LogoHelper {

    public static Bitmap loadCircularLogo(Context ctx, int sizePx) {
        Bitmap raw = null;
        try {
            int resId = ctx.getResources().getIdentifier("dfd_logo", "drawable", ctx.getPackageName());
            if (resId != 0) {
                raw = BitmapFactory.decodeResource(ctx.getResources(), resId);
            }
            if (raw == null) {
                InputStream is = ctx.getAssets().open("dfd_logo.jpg");
                raw = BitmapFactory.decodeStream(is);
                is.close();
            }
        } catch (Throwable ignored) {
        }

        if (raw == null) {
            return renderFallbackDanzLogo(sizePx);
        }

        Bitmap scaled = Bitmap.createScaledBitmap(raw, sizePx, sizePx, true);
        Bitmap output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect rect = new Rect(0, 0, sizePx, sizePx);

        canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - 4f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaled, rect, rect, paint);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(5f);
        border.setColor(Color.parseColor("#B829FF"));
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - 3f, border);

        return output;
    }

    private static Bitmap renderFallbackDanzLogo(int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float cx = size / 2f;
        float cy = size / 2f;

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.parseColor("#0B0414"));
        c.drawCircle(cx, cy, cx - 2, bg);

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(size * 0.045f);
        ring.setShader(new LinearGradient(0, 0, size, size,
                Color.parseColor("#D946EF"), Color.parseColor("#7E22CE"), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, cx - (size * 0.05f), ring);

        Paint crown = new Paint(Paint.ANTI_ALIAS_FLAG);
        crown.setColor(Color.parseColor("#C084FC"));
        Path p = new Path();
        p.moveTo(size * 0.28f, size * 0.36f);
        p.lineTo(size * 0.24f, size * 0.18f);
        p.lineTo(size * 0.39f, size * 0.27f);
        p.lineTo(size * 0.50f, size * 0.13f);
        p.lineTo(size * 0.61f, size * 0.27f);
        p.lineTo(size * 0.76f, size * 0.18f);
        p.lineTo(size * 0.72f, size * 0.36f);
        p.close();
        c.drawPath(p, crown);

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(Color.WHITE);
        txt.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTextSize(size * 0.24f);
        txt.setShadowLayer(12f, 0f, 0f, Color.parseColor("#A855F7"));
        c.drawText("DANZ", cx, cy + (size * 0.10f), txt);

        Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
        sub.setColor(Color.parseColor("#E9D5FF"));
        sub.setTypeface(Typeface.MONOSPACE);
        sub.setTextAlign(Paint.Align.CENTER);
        sub.setTextSize(size * 0.10f);
        c.drawText("INJECT", cx, cy + (size * 0.26f), sub);

        return bmp;
    }
}
