package com.example.wxbubble;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.View;

import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "WxBubble";
    private static final String WECHAT_PKG = "com.tencent.mm";
    private static final String MODULE_PKG = "com.example.wxbubble";
    private static final String PREF_NAME = "bubble_prefs";

    private static final String[] BUBBLE_KEYWORDS = {
            "chatting_item_bg",
            "chat_bubble",
            "chat_bg",
            "bubble_bg",
            "bubble_left",
            "bubble_right"
    };

    private static final String[] SELF_KEYWORDS = {
            "self", "_me", "mine", "right", "out"
    };

    private static final String[] OTHER_KEYWORDS = {
            "other", "left", "in"
    };

    private static XSharedPreferences sPrefs;

    private static final ConcurrentHashMap<Integer, StretchBitmapDrawable> sDrawableCache =
            new ConcurrentHashMap<>();

    private static Bitmap sSelfBitmap;
    private static Bitmap sOtherBitmap;
    private static boolean sInstalled = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!WECHAT_PKG.equals(lpparam.packageName)) return;

        if (lpparam.processName != null && !WECHAT_PKG.equals(lpparam.processName)) {
            return;
        }

        if (sInstalled) return;
        sInstalled = true;

        try {
            sPrefs = new XSharedPreferences(MODULE_PKG, PREF_NAME);
            sPrefs.reload();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 无法读取配置 -> " + t);
        }

        sSelfBitmap = loadBitmap("bubble_self");
        sOtherBitmap = loadBitmap("bubble_other");

        XposedBridge.log(TAG + ": self=" + (sSelfBitmap != null)
                + " other=" + (sOtherBitmap != null));

        if (sSelfBitmap == null && sOtherBitmap == null) {
            XposedBridge.log(TAG + ": 未配置气泡图片，不安装 Hook");
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    View.class,
                    "setBackgroundResource",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                applyBubble(param);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": applyBubble error " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": Hook 安装成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook 安装失败 " + t);
        }
    }

    private void applyBubble(XC_MethodHook.MethodHookParam param) {
        int resId = (int) param.args[0];
        if (resId == 0) return;

        View view = (View) param.thisObject;
        if (view == null) return;

        Resources res = view.getResources();
        if (res == null) return;

        String fullName;
        try {
            fullName = res.getResourceName(resId);
        } catch (Throwable t) {
            return;
        }
        if (fullName == null) return;

        int slash = fullName.lastIndexOf('/');
        if (slash < 0) return;
        String resName = fullName.substring(slash + 1).toLowerCase();

        if (!isBubbleResource(resName)) return;

        Bitmap target = pickBitmap(resName);
        if (target == null) return;

        StretchBitmapDrawable drawable = sDrawableCache.get(resId);
        if (drawable == null || drawable.getBitmap() != target) {
            drawable = new StretchBitmapDrawable(target);
            sDrawableCache.put(resId, drawable);
        }

        view.setBackground(drawable);
        param.setResult(null);

        XposedBridge.log(TAG + ": 替换气泡 " + resName);
    }

    private static boolean isBubbleResource(String name) {
        for (String k : BUBBLE_KEYWORDS) {
            if (name.contains(k)) return true;
        }
        return false;
    }

    private static Bitmap pickBitmap(String resName) {
        boolean isSelf = containsAny(resName, SELF_KEYWORDS);
        boolean isOther = containsAny(resName, OTHER_KEYWORDS);

        if (isSelf && sSelfBitmap != null) return sSelfBitmap;
        if (isOther && sOtherBitmap != null) return sOtherBitmap;

        if (sOtherBitmap != null) return sOtherBitmap;
        return sSelfBitmap;
    }

    private static boolean containsAny(String src, String[] keys) {
        for (String k : keys) {
            if (src.contains(k)) return true;
        }
        return false;
    }

    private static Bitmap loadBitmap(String key) {
        try {
            if (sPrefs == null) return null;
            String b64 = sPrefs.getString(key, null);
            if (b64 == null || b64.length() == 0) return null;
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": loadBitmap(" + key + ") 失败 -> " + t);
            return null;
        }
    }
}
