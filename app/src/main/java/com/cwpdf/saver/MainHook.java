package com.cwpdf.saver;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainHook extends XposedModule {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String TAG = "CWPDFSaver";

    public MainHook() {
        super();
        Log.d(TAG, "MainHook instantiated (libxposed API 101)");
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!param.getPackageName().equals("com.gvxhgw.qwporr")) return;

        Log.d(TAG, "CW Pharma package loaded");

        try {
            ClassLoader classLoader = param.getDefaultClassLoader();
            
            // 1. Hook Paywall (CourseModel)
            try {
                Class<?> courseModelClass = classLoader.loadClass("com.appx.core.model.CourseModel");
                Method getIsPaid = courseModelClass.getDeclaredMethod("getIsPaid");
                hook(getIsPaid).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        return "1"; // Return 1 indicating it is paid
                    }
                });
                Log.d(TAG, "Hooked CourseModel.getIsPaid");
            } catch (Throwable t) { Log.e(TAG, "Error hooking CourseModel", t); }

            // 2. Hook Paywall (TestSeriesModel)
            try {
                Class<?> testSeriesModelClass = classLoader.loadClass("com.appx.core.model.TestSeriesModel");
                Method getIsPaid = testSeriesModelClass.getDeclaredMethod("getIsPaid");
                Method isPaid = testSeriesModelClass.getDeclaredMethod("isPaid");
                
                XposedInterface.Hooker stringOneHooker = new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        return "1";
                    }
                };
                
                hook(getIsPaid).intercept(stringOneHooker);
                hook(isPaid).intercept(stringOneHooker);
                Log.d(TAG, "Hooked TestSeriesModel");
            } catch (Throwable t) { Log.e(TAG, "Error hooking TestSeriesModel", t); }

            // 3. Hook Paywall (FolderCourseModel)
            try {
                Class<?> folderCourseModelClass = classLoader.loadClass("com.appx.core.model.FolderCourseModel");
                Method isPaid = folderCourseModelClass.getDeclaredMethod("isPaid");
                hook(isPaid).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        return 1; // Integer 1
                    }
                });
                Log.d(TAG, "Hooked FolderCourseModel");
            } catch (Throwable t) { Log.e(TAG, "Error hooking FolderCourseModel", t); }

            // 4. Hook PDF Viewer Activities
            XposedInterface.Hooker activityOnCreateHooker = new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed(); // execute original onCreate
                    Activity activity = (Activity) chain.getThisObject();
                    
                    Intent intent = activity.getIntent();
                    String pdfUrl = intent.getStringExtra("url");
                    Uri localUri = intent.getParcelableExtra("uri");
                    String key = intent.getStringExtra("key");
                    String title = intent.getStringExtra("title");
                    boolean isEncrypted = intent.getBooleanExtra("encrypted", false);

                    Log.d(TAG, "PDF opened — url=" + pdfUrl + " uri=" + localUri + " key=" + key + " encrypted=" + isEncrypted);

                    if ((pdfUrl == null || pdfUrl.isEmpty()) && localUri == null) {
                        return result;
                    }

                    injectDownloadButton(activity, pdfUrl, localUri, key, title, isEncrypted);
                    return result;
                }
            };

            try {
                Class<?> pdfViewerClassOld = classLoader.loadClass("com.appx.core.activity.PdfViewerActivity");
                Method onCreateOld = pdfViewerClassOld.getDeclaredMethod("onCreate", Bundle.class);
                hook(onCreateOld).intercept(activityOnCreateHooker);
            } catch (Throwable t) { Log.e(TAG, "Error hooking old PDF viewer", t); }

            try {
                Class<?> pdfViewerClassNew = classLoader.loadClass("com.appx.core.activity.NewPDFViewerActivity");
                Method onCreateNew = pdfViewerClassNew.getDeclaredMethod("onCreate", Bundle.class);
                hook(onCreateNew).intercept(activityOnCreateHooker);
            } catch (Throwable t) { Log.e(TAG, "Error hooking new PDF viewer", t); }

        } catch (Throwable t) {
            Log.e(TAG, "onPackageLoaded Error", t);
        }
    }

    private int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    private void injectDownloadButton(Activity activity, String pdfUrl, Uri localUri, String key, String title, boolean isEncrypted) {
        try {
            View existingBtn = activity.getWindow().getDecorView().findViewWithTag("cw_pdf_download_btn");
            if (existingBtn != null) return;
        } catch (Exception ignored) {}

        android.widget.TextView btnView = new android.widget.TextView(activity);
        btnView.setTag("cw_pdf_download_btn");
        btnView.setText("DL");
        btnView.setTextColor(android.graphics.Color.WHITE);
        btnView.setGravity(Gravity.CENTER);
        btnView.setTextSize(16);
        btnView.setTypeface(null, android.graphics.Typeface.BOLD);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(0xFF2196F3); // Material Blue
        gd.setCornerRadius(dpToPx(activity, 28)); // Makes it a perfect circle for a 56dp button
        btnView.setBackground(gd);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dpToPx(activity, 56), dpToPx(activity, 56)
        );
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.bottomMargin = dpToPx(activity, 120);
        params.rightMargin = dpToPx(activity, 32);
        btnView.setLayoutParams(params);

        btnView.setOnClickListener(v -> {
            Toast.makeText(activity, "Downloading PDF...", Toast.LENGTH_SHORT).show();
            startDownload(activity, pdfUrl, localUri, key, title, isEncrypted);
        });

        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        decorView.addView(btnView, params);
        Log.d(TAG, "Download button injected successfully.");
    }

    private void startDownload(Activity activity, String pdfUrl, Uri localUri, String key, String title, boolean isEncrypted) {
        executor.execute(() -> {
            try {
                byte[] rawBytes = null;

                if (localUri != null) {
                    rawBytes = readUriBytes(activity, localUri);
                } else if (pdfUrl != null && !pdfUrl.isEmpty()) {
                    rawBytes = downloadBytes(pdfUrl);
                }

                if (rawBytes == null) {
                    showToast(activity, "Download failed: Empty response or inaccessible file.");
                    return;
                }

                byte[] pdfBytes;
                if (isEncrypted && key != null && !key.isEmpty()) {
                    pdfBytes = decryptAesCbc(rawBytes, key);
                } else {
                    if (rawBytes.length >= 4) {
                        if (rawBytes[0] == 0x2C && rawBytes[1] == 0x59 && rawBytes[2] == 0x4D && rawBytes[3] == 0x4F) {
                            pdfBytes = decryptXorHeader(rawBytes, key);
                        } else if (rawBytes[0] == 0x25 && rawBytes[1] == 0x50 && rawBytes[2] == 0x44 && rawBytes[3] == 0x46) {
                            pdfBytes = rawBytes;
                        } else {
                            pdfBytes = decryptXorHeader(rawBytes, key);
                        }
                    } else {
                        pdfBytes = decryptXorHeader(rawBytes, key);
                    }
                }

                if (pdfBytes == null) {
                    showToast(activity, "Decryption failed");
                    return;
                }

                File downDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                String safeTitle = (title != null && !title.isEmpty()) ? title.replaceAll("[^a-zA-Z0-9._-]", "_") : "document";
                File outFile = new File(downDir, safeTitle + "_" + System.currentTimeMillis() + ".pdf");
                
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(pdfBytes);
                }
                
                showToast(activity, "Saved to Downloads: " + outFile.getName());

            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                showToast(activity, "Error: " + e.getMessage());
            }
        });
    }

    private byte[] readUriBytes(Context context, Uri uri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    private byte[] downloadBytes(String url) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("Referer", "https://player.akamai.net.in/")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "HTTP error: " + response.code());
                return null;
            }
            ResponseBody body = response.body();
            return body != null ? body.bytes() : null;
        }
    }

    private byte[] decryptAesCbc(byte[] encryptedContent, String password) throws Exception {
        byte[] ivBytes = Arrays.copyOfRange(encryptedContent, 0, 16);
        byte[] cipherText = Arrays.copyOfRange(encryptedContent, 16, encryptedContent.length);

        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(password.getBytes("UTF-8"));
        byte[] keyBytes = md.digest();

        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

        return cipher.doFinal(cipherText);
    }

    private byte[] decryptXorHeader(byte[] rawBytes, String key) {
        byte[] original = rawBytes.clone();
        for (int i = 0; i < 28 && i < original.length; i++) {
            original[i] = (byte) (original[i] ^ 9);
        }
        return original;
    }

    private void showToast(Activity activity, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
        });
    }
}
