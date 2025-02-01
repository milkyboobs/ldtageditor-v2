package com.ld.tageditor;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private IntentFilter[] mFilters;
    private PendingIntent mPendingIntent;
    private String[][] mTechLists;
    private NfcAdapter nfc;
    private Tag tag;
    private WebView webView;

    public Tag getTag() {
        return tag;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Updated PendingIntent with FLAG_MUTABLE for Android 14+
        this.mPendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE
        );

        try {
            IntentFilter ndefFilter = new IntentFilter("android.nfc.action.NDEF_DISCOVERED");
            ndefFilter.addDataType("*/*");
            this.mFilters = new IntentFilter[]{ndefFilter};

            this.mTechLists = new String[][]{{NfcA.class.getName()}};
            this.nfc = NfcAdapter.getDefaultAdapter(this);

            this.webView = findViewById(R.id.webView);
            this.webView.getSettings().setJavaScriptEnabled(true);
            this.webView.getSettings().setAllowFileAccess(true);
            this.webView.getSettings().setAllowFileAccessFromFileURLs(false); // More secure
            this.webView.getSettings().setAllowUniversalAccessFromFileURLs(false); // More secure

            this.webView.setWebChromeClient(new WebChromeClient() {
                public void onProgressChanged(WebView view, int progress) {
                    setProgress(progress * 100);
                }
            });

            this.webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    String host = Uri.parse(url).getHost();
                    ArrayList<String> allowedHosts = new ArrayList<>();
                    allowedHosts.add("android_asset");
                    allowedHosts.add("127.0.0.1");

                    ArrayList<String> allowedSubstring = new ArrayList<>();
                    allowedSubstring.add("192.168.");

                    if (allowedHosts.contains(host)) {
                        return false;
                    }
                    for (String prefix : allowedSubstring) {
                        if (host.startsWith(prefix)) {
                            return false;
                        }
                    }

                    view.evaluateJavascript("(function(){ var err = 'ACCESS TO URL " + url +
                            " DENIED'; console.error(err); if(window.appErrorHandler) " +
                            "window.appErrorHandler(err); })();", null);

                    return true;
                }
            });

            WebView.setWebContentsDebuggingEnabled(true);
            this.webView.addJavascriptInterface(new JSAPI(this, this.webView), "AndroidApp");
            this.webView.loadUrl("file:///android_asset/index.html");

        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("Failed to initialize NFC", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (this.nfc != null && this.nfc.isEnabled()) {
            this.nfc.enableForegroundDispatch(this, this.mPendingIntent, this.mFilters, this.mTechLists);
        } else {
            Log.e(TAG, "NFC is not enabled!");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.nfc != null) {
            this.nfc.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Log.i("NFC", "Tag detected: " + tag);

        if (tag != null) {
            Log.i("NFC", "Calling JavaScript function...");
            callJavaScript("AndroidApp.tagDetected", "NFC tag found");
        }
    }


    private void callJavaScript(String methodName, Object... params) {
        Log.i(TAG, "CallJS Building string...");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("javascript:try{(window.")
                .append(methodName)
                .append("||console.warn.bind(console,'UNHANDLED','")
                .append(methodName)
                .append("'))(");

        for (Object param : params) {
            String paramStr = (param instanceof String) ? "'" + param + "'" : param.toString();
            stringBuilder.append(paramStr).append(",");
        }
        stringBuilder.append("''").append(")}catch(error){console.error('ANDROID APP ERROR',error);}");

        this.webView.loadUrl(stringBuilder.toString());
        Log.i(TAG, "CallJS: " + stringBuilder);
    }
}