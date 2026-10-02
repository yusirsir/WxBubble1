package com.example.wxbubble;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class StretchBitmapDrawable extends Drawable {

    private final Bitmap bitmap;
    private final Paint paint;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    public StretchBitmapDrawable(Bitmap bitmap) {
        this.bitmap = bitmap;
        this.paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || bitmap.isRecycled()) return;

        srcRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        dstRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return bitmap.getWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return bitmap.getHeight();
    }
}
