package com.navis.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.telephony.SmsManager;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String SHELL_URL = "https://appassets.androidplatform.net/assets/index.html";
    private static final int REQ_CALL_PHONE = 201;
    private static final int REQ_SEND_SMS = 202;
    private static final int REQ_CAMERA_PERM = 203;
    private static final int REQ_TAKE_PHOTO = 204;

    private WebView webView;
    private String pendingCallNumber;
    private String pendingSmsNumber;
    private String pendingSmsBody;
    private File pendingPhotoFile;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemBars();

        webView = new WebView(this);
        setContentView(webView);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.addJavascriptInterface(new NavisPhoneBridge(), "NavisPhone");
        webView.addJavascriptInterface(new NavisCameraBridge(), "NavisCamera");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() != null ? uri.getHost() : "";
                if ("appassets.androidplatform.net".equals(host)) {
                    return false;
                }
                String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
                if ("tel".equals(scheme)) {
                    String raw = uri.getSchemeSpecificPart();
                    if (raw != null && !raw.trim().isEmpty()) {
                        startDirectCall(raw.trim());
                    }
                    return true;
                }
                if ("mailto".equals(scheme) || "http".equals(scheme) || "https".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });

        webView.loadUrl(SHELL_URL);
    }

    private void startDirectCall(String number) {
        String clean = number.trim();
        if (clean.isEmpty()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(clean)));
                startActivity(intent);
            } catch (Exception ignored) {
            }
        } else {
            pendingCallNumber = clean;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL_PHONE);
        }
    }

    private void dispatchSms(String number, String message) {
        String cleanNum = number.trim();
        String body = message != null ? message.trim() : "";
        if (cleanNum.isEmpty() || body.isEmpty()) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                SmsManager smsManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? getSystemService(SmsManager.class)
                        : SmsManager.getDefault();
                if (smsManager == null) {
                    smsManager = SmsManager.getDefault();
                }
                ArrayList<String> parts = smsManager.divideMessage(body);
                if (parts.size() > 1) {
                    smsManager.sendMultipartTextMessage(cleanNum, null, parts, null, null);
                } else {
                    smsManager.sendTextMessage(cleanNum, null, body, null, null);
                }
            } catch (Exception ignored) {
            }
        } else {
            pendingSmsNumber = cleanNum;
            pendingSmsBody = body;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, REQ_SEND_SMS);
        }
    }

    private void startCameraCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERM);
            return;
        }
        try {
            File dir = new File(getCacheDir(), "navis_photos");
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            pendingPhotoFile = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            Uri photoUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", pendingPhotoFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQ_TAKE_PHOTO);
        } catch (Exception ignored) {
        }
    }

    private String bitmapToJpegDataUrl(File file) {
        if (file == null || !file.exists()) return null;
        Bitmap raw = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (raw == null) return null;

        int maxDim = 1024;
        int w = raw.getWidth();
        int h = raw.getHeight();
        if (w > maxDim || h > maxDim) {
            float scale = Math.min((float) maxDim / w, (float) maxDim / h);
            w = Math.max(1, Math.round(w * scale));
            h = Math.max(1, Math.round(h * scale));
            raw = Bitmap.createScaledBitmap(raw, w, h, true);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        raw.compress(Bitmap.CompressFormat.JPEG, 78, out);
        String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        return "data:image/jpeg;base64," + b64;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TAKE_PHOTO && resultCode == RESULT_OK) {
            final String dataUrl = bitmapToJpegDataUrl(pendingPhotoFile);
            if (pendingPhotoFile != null && pendingPhotoFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pendingPhotoFile.delete();
            }
            if (dataUrl != null && webView != null) {
                String escaped = dataUrl.replace("\\", "\\\\").replace("'", "\\'");
                webView.post(() -> webView.evaluateJavascript(
                        "if(window.onNavisPhotoCaptured){window.onNavisPhotoCaptured('" + escaped + "');}",
                        null));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == REQ_CALL_PHONE) {
            String num = pendingCallNumber;
            pendingCallNumber = null;
            if (granted && num != null) {
                startDirectCall(num);
            }
        } else if (requestCode == REQ_SEND_SMS) {
            String num = pendingSmsNumber;
            String body = pendingSmsBody;
            pendingSmsNumber = null;
            pendingSmsBody = null;
            if (granted && num != null && body != null) {
                dispatchSms(num, body);
            }
        } else if (requestCode == REQ_CAMERA_PERM) {
            if (granted) {
                startCameraCapture();
            }
        }
    }

    public class NavisPhoneBridge {
        @JavascriptInterface
        public boolean placeCall(String number) {
            if (number == null || number.trim().isEmpty()) return false;
            final String target = number.trim();
            runOnUiThread(() -> startDirectCall(target));
            return true;
        }

        @JavascriptInterface
        public boolean sendSms(String number, String message) {
            if (number == null || number.trim().isEmpty()
                    || message == null || message.trim().isEmpty()) {
                return false;
            }
            final String target = number.trim();
            final String body = message.trim();
            runOnUiThread(() -> dispatchSms(target, body));
            return true;
        }
    }

    public class NavisCameraBridge {
        @JavascriptInterface
        public boolean capturePhoto() {
            runOnUiThread(() -> startCameraCapture());
            return true;
        }
    }

    private void hideSystemBars() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()
                && !SHELL_URL.equals(webView.getUrl())) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
