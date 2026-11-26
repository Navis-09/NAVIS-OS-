package com.navis.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_FILE_SAVED = "com.navis.mobile.ACTION_FILE_SAVED";
    private static final String SHELL_URL = "https://appassets.androidplatform.net/assets/index.html";
    private static final String DEFAULT_BROWSER_URL = "https://www.google.com";
    private static final int REQ_CALL_PHONE = 201;
    private static final int REQ_SEND_SMS = 202;
    private static final int REQ_CAMERA_PERM = 203;
    private static final int REQ_TAKE_PHOTO = 204;
    private static final int REQ_BT_CONNECT = 205;
    private static final int REQ_AUDIO_PERM = 206;
    private static final int REQ_VOICE_INPUT = 207;

    private WebView webView;
    private String pendingCallNumber;
    private String pendingSmsNumber;
    private String pendingSmsBody;
    private File pendingPhotoFile;
    private boolean pendingOpenBtAfterPerm;
    private boolean pendingVoiceAfterPerm;
    private boolean receiverRegistered;

    private final BroadcastReceiver fileSavedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_FILE_SAVED.equals(intent.getAction())) return;
            String folder = intent.getStringExtra("folder");
            String name = intent.getStringExtra("name");
            String mime = intent.getStringExtra("mime");
            String path = intent.getStringExtra("path");
            if (folder == null || folder.trim().isEmpty()) folder = "Downloads";
            if (name == null || name.trim().isEmpty()) name = "download_" + System.currentTimeMillis();
            if (mime == null || mime.trim().isEmpty()) mime = "application/octet-stream";

            String payload = "";
            boolean isDataUrl = false;
            if (path != null && !path.isEmpty()) {
                File f = new File(path);
                if (f.exists() && f.length() <= 2_000_000L) {
                    try (FileInputStream in = new FileInputStream(f);
                         ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            bos.write(buf, 0, n);
                        }
                        byte[] raw = bos.toByteArray();
                        if (mime.startsWith("text/") || name.matches(".*\\.(txt|md|json|csv|html|js|xml|log)$")) {
                            payload = new String(raw, StandardCharsets.UTF_8);
                            isDataUrl = false;
                        } else {
                            payload = "data:" + mime + ";base64," + Base64.encodeToString(raw, Base64.NO_WRAP);
                            isDataUrl = true;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            final String fEsc = escapeJs(folder);
            final String nEsc = escapeJs(name);
            final String mEsc = escapeJs(mime);
            final String pEsc = escapeJs(payload);
            final boolean dFlag = isDataUrl;

            if (webView != null) {
                webView.post(() -> webView.evaluateJavascript(
                        "if(window.navisStoreFile){window.navisStoreFile('" + fEsc + "','" + nEsc + "','" + mEsc + "','" + pEsc + "'," + dFlag + ");}" +
                        "if(typeof toast==='function'){toast('Downloaded to NAVIS Files: " + nEsc + "');}",
                        null));
            }
        }
    };

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

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
        webView.addJavascriptInterface(new NavisBrowserBridge(), "NavisBrowser");
        webView.addJavascriptInterface(new NavisFilesBridge(), "NavisFiles");
        webView.addJavascriptInterface(new NavisConnectivityBridge(), "NavisConnectivity");
        webView.addJavascriptInterface(new NavisVoiceBridge(), "NavisVoice");
        webView.addJavascriptInterface(new NavisSystemBridge(), "NavisSystem");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return true;

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
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    openInNavisBrowser(uri.toString());
                    return true;
                }
                if ("mailto".equals(scheme)) {
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

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_FILE_SAVED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(fileSavedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(fileSavedReceiver, filter);
            }
            receiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (receiverRegistered) {
            try {
                unregisterReceiver(fileSavedReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
    }

    private void openInNavisBrowser(String url) {
        String target = (url == null || url.trim().isEmpty()) ? DEFAULT_BROWSER_URL : url.trim();
        try {
            Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
            intent.putExtra(BrowserActivity.EXTRA_URL, target);
            startActivity(intent);
        } catch (Exception ignored) {
        }
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

    private BluetoothAdapter getBluetoothAdapter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            return bm != null ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
        }
        return BluetoothAdapter.getDefaultAdapter();
    }

    private boolean hasBluetoothConnectPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void openWifiPanel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(new Intent(Settings.Panel.ACTION_WIFI));
            } else {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        } catch (Exception ignored) {
        }
    }

    private void openBluetoothPanel() {
        if (!hasBluetoothConnectPerm() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingOpenBtAfterPerm = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_CONNECT);
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    private void startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingVoiceAfterPerm = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_PERM);
            return;
        }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to NAVIS AI...");
            startActivityForResult(intent, REQ_VOICE_INPUT);
        } catch (Exception ignored) {
        }
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
                String escaped = escapeJs(dataUrl);
                webView.post(() -> webView.evaluateJavascript(
                        "if(window.onNavisPhotoCaptured){window.onNavisPhotoCaptured('" + escaped + "');}",
                        null));
            }
        } else if (requestCode == REQ_VOICE_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty() && webView != null) {
                String transcript = escapeJs(matches.get(0).replace("\n", " "));
                webView.post(() -> webView.evaluateJavascript(
                        "if(window.onNavisVoiceResult){window.onNavisVoiceResult('" + transcript + "');}",
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
        } else if (requestCode == REQ_BT_CONNECT) {
            boolean openBt = pendingOpenBtAfterPerm;
            pendingOpenBtAfterPerm = false;
            if (granted && openBt) {
                openBluetoothPanel();
            }
            if (webView != null) {
                webView.post(() -> webView.evaluateJavascript(
                        "if(window.onNavisConnectivityUpdated){window.onNavisConnectivityUpdated();}",
                        null));
            }
        } else if (requestCode == REQ_AUDIO_PERM) {
            boolean openVoice = pendingVoiceAfterPerm;
            pendingVoiceAfterPerm = false;
            if (granted && openVoice) {
                startVoiceRecognition();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                    "if(window.onNavisConnectivityUpdated){window.onNavisConnectivityUpdated();}",
                    null));
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

    public class NavisBrowserBridge {
        @JavascriptInterface
        public boolean openUrl(String url) {
            final String target = (url == null || url.trim().isEmpty())
                    ? DEFAULT_BROWSER_URL : url.trim();
            runOnUiThread(() -> openInNavisBrowser(target));
            return true;
        }
    }

    public class NavisFilesBridge {
        private File resolveSafeFile(String folder, String name) {
            String safeFolder = (folder == null || folder.trim().isEmpty())
                    ? "Documents" : folder.replaceAll("[^a-zA-Z0-9_-]", "_");
            String safeName = (name == null || name.trim().isEmpty())
                    ? ("file_" + System.currentTimeMillis()) : name.replaceAll("[^a-zA-Z0-9._-]", "_");
            File baseDir = new File(getFilesDir(), "navis_storage/" + safeFolder);
            if (!baseDir.exists() && !baseDir.mkdirs()) return null;
            return new File(baseDir, safeName);
        }

        @JavascriptInterface
        public String saveTextFile(String folder, String name, String content) {
            File f = resolveSafeFile(folder, name);
            if (f == null) return "";
            try (FileOutputStream out = new FileOutputStream(f)) {
                byte[] bytes = (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
                out.write(bytes);
                out.flush();
                return f.getAbsolutePath();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String readTextFile(String folder, String name) {
            File f = resolveSafeFile(folder, name);
            if (f == null || !f.exists()) return "";
            try (FileInputStream in = new FileInputStream(f);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                return bos.toString(StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean deleteFile(String folder, String name) {
            File f = resolveSafeFile(folder, name);
            return f != null && f.exists() && f.delete();
        }
    }

    public class NavisConnectivityBridge {
        @JavascriptInterface
        public String getStatusJson() {
            JSONObject obj = new JSONObject();
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                boolean wifiEnabled = wm != null && wm.isWifiEnabled();
                boolean wifiConnected = false;
                int linkSpeed = 0;

                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network net = cm.getActiveNetwork();
                    NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
                    wifiConnected = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                }

                if (wm != null && wifiEnabled) {
                    WifiInfo info = wm.getConnectionInfo();
                    if (info != null) {
                        linkSpeed = info.getLinkSpeed();
                    }
                }

                obj.put("wifiEnabled", wifiEnabled);
                obj.put("wifiConnected", wifiConnected);
                obj.put("wifiSpeedMbps", linkSpeed);

                BluetoothAdapter bt = getBluetoothAdapter();
                boolean btSupported = bt != null;
                boolean btPermGranted = hasBluetoothConnectPerm();
                boolean btEnabled = false;
                JSONArray pairedArr = new JSONArray();

                if (btSupported && btPermGranted) {
                    try {
                        btEnabled = bt.isEnabled();
                        if (btEnabled) {
                            Set<BluetoothDevice> bonded = bt.getBondedDevices();
                            if (bonded != null) {
                                for (BluetoothDevice dev : bonded) {
                                    JSONObject d = new JSONObject();
                                    d.put("name", dev.getName() != null ? dev.getName() : "Unknown Device");
                                    d.put("address", dev.getAddress() != null ? dev.getAddress() : "");
                                    pairedArr.put(d);
                                }
                            }
                        }
                    } catch (SecurityException ignored) {
                    }
                }

                obj.put("btSupported", btSupported);
                obj.put("btPermGranted", btPermGranted);
                obj.put("btEnabled", btEnabled);
                obj.put("pairedDevices", pairedArr);
            } catch (Exception ignored) {
            }
            return obj.toString();
        }

        @JavascriptInterface
        public void requestBluetoothPermission() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPerm()) {
                    pendingOpenBtAfterPerm = false;
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_CONNECT);
                }
            });
        }

        @JavascriptInterface
        public void openWifiSettings() {
            runOnUiThread(() -> openWifiPanel());
        }

        @JavascriptInterface
        public void openBluetoothSettings() {
            runOnUiThread(() -> openBluetoothPanel());
        }
    }

    public class NavisVoiceBridge {
        @JavascriptInterface
        public boolean startListening() {
            runOnUiThread(() -> startVoiceRecognition());
            return true;
        }
    }

    public class NavisSystemBridge {
        @JavascriptInterface
        public String getBatteryJson() {
            JSONObject obj = new JSONObject();
            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                int level = -1;
                int scale = 100;
                boolean charging = false;
                if (batteryStatus != null) {
                    level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;
                }
                int pct = (level >= 0 && scale > 0) ? Math.round((level * 100f) / scale) : -1;
                obj.put("percent", pct);
                obj.put("charging", charging);
            } catch (Exception ignored) {
            }
            return obj.toString();
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
