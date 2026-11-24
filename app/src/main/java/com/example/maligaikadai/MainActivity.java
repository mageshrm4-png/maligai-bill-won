package com.example.maligaikadai;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.telephony.SmsManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private WebView web;
    private SharedPreferences prefs;
    private String pendPhone;
    private String pendText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#14623c"));
        prefs = getSharedPreferences("maligai", Context.MODE_PRIVATE);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.handleBack ? window.handleBack() : false", value -> {
            if (!"true".equals(value)) {
                finish();
            }
        });
    }

    private void trySms(String phone, String text) {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            pendPhone = phone;
            pendText = text;
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 11);
        } else {
            doSms(phone, text);
        }
    }

    private void doSms(String phone, String text) {
        try {
            SmsManager sm;
            if (Build.VERSION.SDK_INT >= 31) {
                sm = getSystemService(SmsManager.class);
            } else {
                sm = SmsManager.getDefault();
            }
            ArrayList<String> parts = sm.divideMessage(text);
            sm.sendMultipartTextMessage(phone, null, parts, null, null);
            Toast.makeText(this, "SMS அனுப்பப்பட்டது", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            openSmsApp(phone, text);
        }
    }

    private void openSmsApp(String phone, String text) {
        try {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)));
            i.putExtra("sms_body", text);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "SMS அனுப்ப முடியவில்லை", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 11 && pendPhone != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doSms(pendPhone, pendText);
            } else {
                openSmsApp(pendPhone, pendText);
            }
            pendPhone = null;
            pendText = null;
        }
    }

    private class Bridge {
        @JavascriptInterface
        public String load(String key) {
            return prefs.getString(key, "");
        }

        @JavascriptInterface
        public void save(String key, String value) {
            prefs.edit().putString(key, value).commit();
        }

        @JavascriptInterface
        public void share(final String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Share"));
            });
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                pm.print("Bill", web.createPrintDocumentAdapter("Bill"),
                        new PrintAttributes.Builder().build());
            });
        }

        @JavascriptInterface
        public void sendSms(final String phone, final String text) {
            runOnUiThread(() -> trySms(phone, text));
        }

        @JavascriptInterface
        public void whatsapp(final String phone, final String text) {
            runOnUiThread(() -> {
                try {
                    String p = phone.replaceAll("[^0-9]", "");
                    if (p.length() == 10) {
                        p = "91" + p;
                    }
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://wa.me/" + p + "?text=" + Uri.encode(text)));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "WhatsApp திறக்க முடியவில்லை", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void toast(final String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    }
}
