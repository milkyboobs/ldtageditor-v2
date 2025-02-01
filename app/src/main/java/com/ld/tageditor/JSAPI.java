package com.ld.tageditor;

import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.io.IOException;

public class JSAPI {
    private static final String TAG = "JSAPI";
    private final MainActivity activity;
    private final WebView webView;

    public JSAPI(MainActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public String readToken(byte page) {
        Tag tag = activity.getTag();
        if (tag == null) {
            Log.e(TAG, "No NFC tag detected!");
            return null;
        }

        NfcA nfcA = NfcA.get(tag);
        if (nfcA == null) {
            Log.e(TAG, "NfcA tech not supported on this tag");
            return null;
        }

        try {
            Log.i(TAG, "Connecting to NFC tag...");
            nfcA.connect();

            byte[] command = new byte[]{0x30, (byte) (page & 0xFF)};
            byte[] payload = nfcA.transceive(command);

            if (payload == null || payload.length < 4) {
                Log.e(TAG, "Invalid NFC read response");
                return null;
            }

            String encodedData = Base64.encodeToString(payload, Base64.NO_WRAP);
            Log.i(TAG, "Read success: " + encodedData);
            return encodedData;
        } catch (IOException e) {
            Log.e(TAG, "IOException while reading NFC tag", e);
            return null;
        } finally {
            try {
                nfcA.close();
                Log.i(TAG, "NFC connection closed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing NFC connection", e);
            }
        }
    }

    @JavascriptInterface
    public boolean writeToken(byte page, String payload) {
        Tag tag = activity.getTag();
        if (tag == null) {
            Log.e(TAG, "No NFC tag detected!");
            return false;
        }

        NfcA nfcA = NfcA.get(tag);
        if (nfcA == null) {
            Log.e(TAG, "NfcA tech not supported on this tag");
            return false;
        }

        byte[] data = Base64.decode(payload, Base64.NO_WRAP);
        if (data.length < 4) {
            Log.e(TAG, "Invalid data length for NFC write");
            return false;
        }

        try {
            Log.i(TAG, "Connecting to NFC tag...");
            nfcA.connect();

            byte[] command = new byte[]{(byte) 0xA2, (byte) (page & 0xFF), data[0], data[1], data[2], data[3]};
            nfcA.transceive(command);

            Log.i(TAG, "Write successful");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing to NFC tag", e);
            return false;
        } finally {
            try {
                nfcA.close();
                Log.i(TAG, "NFC connection closed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing NFC connection", e);
            }
        }
    }

    @JavascriptInterface
    public void callJavaScript(String methodName, String... params) {
        StringBuilder jsCode = new StringBuilder("javascript:try{(window.")
                .append(methodName)
                .append("||console.warn.bind(console,'UNHANDLED','")
                .append(methodName)
                .append("'))(");

        for (int i = 0; i < params.length; i++) {
            jsCode.append("'").append(params[i]).append("'");
            if (i < params.length - 1) {
                jsCode.append(",");
            }
        }

        jsCode.append(");}catch(error){console.error('ANDROID APP ERROR',error);}");

        final String jsCommand = jsCode.toString();
        webView.post(() -> webView.evaluateJavascript(jsCommand, null));

        Log.i(TAG, "CallJS: " + jsCommand);
    }
}
