package com.droid.botcam;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;

public class DroidFunctions {

    public static Drawable bitmapToDrawable(Context context, Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        } else {
            return new BitmapDrawable(context.getResources(), bitmap);
        }
    }

    public static Bitmap imageResourceToBitmap(Context context, int img) {
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), img);
        return bitmap;
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        } else if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();

        if (!(intrinsicWidth > 0 && intrinsicHeight > 0)) {
            return null;
        }

        try {
            Bitmap bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    public static float dpToPx(float dp) {
        return (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static float pxToDp(float px) {
        return (px / Resources.getSystem().getDisplayMetrics().density);
    }

    public static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    public static void requestCameraPermission(Context context, int requestCode) {
        ActivityCompat.requestPermissions((Activity) context,
                new String[]{Manifest.permission.CAMERA},
                requestCode);
    }

    public static void requestStoragePermission(Context context, int requestCode) {
        ActivityCompat.requestPermissions((Activity) context,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                requestCode);
    }

    public static File createFolderInExternalStorage(String folderName) {
        File file = new File(Environment.getExternalStorageDirectory(), folderName);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }
}
