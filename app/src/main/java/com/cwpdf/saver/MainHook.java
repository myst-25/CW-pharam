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
    private static boolean hasShownPopup = false;

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
            
            // Hook PDF Viewer Activities
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

            // Hook Activity onResume to show welcome popup once
            XposedInterface.Hooker activityOnResumeHooker = new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    Activity activity = (Activity) chain.getThisObject();
                    
                    // Skip splash screens so the popup doesn't get destroyed when the logo disappears
                    String actName = activity.getClass().getSimpleName().toLowerCase();
                    if (actName.contains("splash") || actName.contains("launch") || actName.contains("start")) {
                        return result;
                    }
                    
                    if (!hasShownPopup) {
                        hasShownPopup = true;
                        showWelcomePopup(activity);
                    }
                    return result;
                }
            };
            try {
                Method onResumeMethod = Activity.class.getDeclaredMethod("onResume");
                hook(onResumeMethod).intercept(activityOnResumeHooker);
            } catch (Throwable t) { Log.e(TAG, "Error hooking Activity.onResume", t); }



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
        btnView.setText("⬇️");
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
                File outFile = new File(downDir, safeTitle + "_myst25_" + System.currentTimeMillis() + ".pdf");
                
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

    private void showWelcomePopup(Activity activity) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                // Create a custom MD3 style dialog from scratch to avoid host app theme dependencies
                android.app.Dialog dialog = new android.app.Dialog(activity);
                dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                }

                android.widget.LinearLayout container = new android.widget.LinearLayout(activity);
                container.setOrientation(android.widget.LinearLayout.VERTICAL);
                container.setPadding(dpToPx(activity, 24), dpToPx(activity, 24), dpToPx(activity, 24), dpToPx(activity, 24));
                
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                int nightModeFlags = activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                boolean isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                bg.setColor(isDarkMode ? 0xFF2D2F31 : 0xFFF3F4F9); // MD3 surface color
                bg.setCornerRadius(dpToPx(activity, 28)); // MD3 Dialog corner radius
                container.setBackground(bg);

                // Title
                android.widget.TextView title = new android.widget.TextView(activity);
                title.setText("CW Pharmacy PDF Saver");
                title.setTextSize(24);
                title.setTextColor(isDarkMode ? 0xFFE3E2E6 : 0xFF1A1C1E);
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                container.addView(title);

                // Message
                android.widget.TextView message = new android.widget.TextView(activity);
                message.setText("Module is active! 🚀\n\nMade by myst-25.\nKnowledge must be free, accessible, and shareable for all.");
                message.setTextSize(14);
                message.setTextColor(isDarkMode ? 0xFFC4C6D0 : 0xFF44474E);
                message.setPadding(0, dpToPx(activity, 16), 0, dpToPx(activity, 24));
                container.addView(message);

                // Buttons container
                android.widget.LinearLayout buttonContainer = new android.widget.LinearLayout(activity);
                buttonContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
                buttonContainer.setGravity(Gravity.CENTER_HORIZONTAL);

                android.widget.LinearLayout.LayoutParams btnParams = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 48)
                );
                btnParams.bottomMargin = dpToPx(activity, 8);

                // Telegram Button (Primary Filled)
                android.widget.Button btnTelegram = new android.widget.Button(activity);
                btnTelegram.setText("Join Telegram");
                btnTelegram.setTextColor(isDarkMode ? 0xFF00325B : 0xFFFFFFFF);
                btnTelegram.setAllCaps(false);
                android.graphics.drawable.GradientDrawable btnTelBg = new android.graphics.drawable.GradientDrawable();
                btnTelBg.setColor(isDarkMode ? 0xFFD1E4FF : 0xFF0061A4);
                btnTelBg.setCornerRadius(dpToPx(activity, 24)); // Pill shape
                btnTelegram.setBackground(btnTelBg);
                btnTelegram.setOnClickListener(v -> {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/myst2123")));
                });
                buttonContainer.addView(btnTelegram, btnParams);

                // GitHub Button (Secondary Tonal)
                android.widget.Button btnGithub = new android.widget.Button(activity);
                btnGithub.setText("Star on GitHub");
                btnGithub.setTextColor(isDarkMode ? 0xFFE3E2E6 : 0xFF1A1C1E);
                btnGithub.setAllCaps(false);
                android.graphics.drawable.GradientDrawable btnGitBg = new android.graphics.drawable.GradientDrawable();
                btnGitBg.setColor(isDarkMode ? 0xFF44474E : 0xFFE0E2EC);
                btnGitBg.setCornerRadius(dpToPx(activity, 24));
                btnGithub.setBackground(btnGitBg);
                btnGithub.setOnClickListener(v -> {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/myst-25/CW-pharma")));
                });
                buttonContainer.addView(btnGithub, btnParams);

                // Close Button (Text style)
                android.widget.Button btnClose = new android.widget.Button(activity);
                btnClose.setText("Close");
                btnClose.setTextColor(isDarkMode ? 0xFFAEC6FF : 0xFF0061A4);
                btnClose.setAllCaps(false);
                btnClose.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                btnClose.setOnClickListener(v -> dialog.dismiss());
                buttonContainer.addView(btnClose, btnParams);

                container.addView(buttonContainer);

                dialog.setContentView(container);
                dialog.setCancelable(true);
                
                int width = (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.85);
                dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                
                dialog.show();

            } catch (Exception e) {
                Log.e(TAG, "Failed to show popup", e);
            }
        }, 500); // 500ms delay to ensure activity window is fully attached
    }
}
