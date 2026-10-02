package com.example.wxbubble;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

public class MainActivity extends Activity {

    private static final String PREFS = "bubble_prefs";
    private static final String KEY_SELF = "bubble_self";
    private static final String KEY_OTHER = "bubble_other";

    private static final int REQ_SELF = 1001;
    private static final int REQ_OTHER = 1002;

    private static final int MAX_DIMEN = 720;

    private ImageView ivSelf;
    private ImageView ivOther;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivSelf = findViewById(R.id.iv_self);
        ivOther = findViewById(R.id.iv_other);

        Button btnSelf = findViewById(R.id.btn_self);
        Button btnOther = findViewById(R.id.btn_other);
        Button btnClear = findViewById(R.id.btn_clear);

        btnSelf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage(REQ_SELF);
            }
        });

        btnOther.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage(REQ_OTHER);
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().clear().commit();
                makePrefsReadable();
                refreshPreview();
                toast("已清除，重启微信生效");
            }
        });

        refreshPreview();
    }

    private void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择气泡图片"), requestCode);
        } catch (Throwable t) {
            toast("无法打开图片选择器：" + t.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode != REQ_SELF && requestCode != REQ_OTHER) return;

        Uri uri = data.getData();
        try {
            Bitmap bmp = decodeSampled(this, uri, MAX_DIMEN);
            if (bmp == null) {
                toast("图片解码失败");
                return;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            byte[] bytes = bos.toByteArray();

            if (bytes.length > 900 * 1024) {
                bos.reset();
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos);
                bytes = bos.toByteArray();
            }
            bmp.recycle();

            String b64 = Base64.encodeToString(bytes, Base64.DEFAULT);
            String key = (requestCode == REQ_SELF) ? KEY_SELF : KEY_OTHER;

            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().putString(key, b64).commit();

            makePrefsReadable();
            refreshPreview();
            toast("已保存，重启微信后生效");

        } catch (Throwable t) {
            toast("保存失败：" + t.getMessage());
        }
    }

    private void refreshPreview() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        showBitmap(ivSelf, sp.getString(KEY_SELF, null));
        showBitmap(ivOther, sp.getString(KEY_OTHER, null));
    }

    private void showBitmap(ImageView iv, String b64) {
        if (b64 == null || b64.length() == 0) {
            iv.setImageDrawable(null);
            return;
        }
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
            iv.setImageBitmap(bmp);
        } catch (Throwable t) {
            iv.setImageDrawable(null);
        }
    }

    private void makePrefsReadable() {
        try {
            File dataDir = new File(getApplicationInfo().dataDir);
            File prefsDir = new File(dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, PREFS + ".xml");

            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
            }
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
            dataDir.setExecutable(true, false);
            dataDir.setReadable(true, false);
        } catch (Throwable ignored) {
        }
    }

    private static Bitmap decodeSampled(Context ctx, Uri uri, int maxDimen)
            throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        InputStream is = ctx.getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(is, null, opts);
        if (is != null) is.close();

        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;

        int sample = 1;
        int maxSide = Math.max(opts.outWidth, opts.outHeight);
        while (maxSide / sample > maxDimen) {
            sample *= 2;
        }

        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        o2.inPreferredConfig = Bitmap.Config.ARGB_8888;

        InputStream is2 = ctx.getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(is2, null, o2);
        if (is2 != null) is2.close();

        return bmp;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
