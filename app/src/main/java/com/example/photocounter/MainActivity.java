package com.example.photocounter;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        try {
            String html = new String(readAll(getAssets().open("index.html")), "UTF-8");
            web.loadDataWithBaseURL("https://photo.local/", html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            web.loadData("Ошибка загрузки", "text/plain", "UTF-8");
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        return o.toByteArray();
    }

    class Bridge {
        @JavascriptInterface
        public String apiUrl() {
            return getString(R.string.app_url);
        }

        @JavascriptInterface
        public void request(final int id, final String method, final String url, final String body) {
            new Thread(new Runnable() {
                public void run() {
                    boolean ok = false;
                    String text = "";
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(30000);
                        c.setInstanceFollowRedirects(true);
                        c.setRequestMethod(method);
                        if ("POST".equals(method)) {
                            c.setDoOutput(true);
                            c.setRequestProperty("Content-Type", "text/plain;charset=utf-8");
                            OutputStream os = c.getOutputStream();
                            os.write(body.getBytes("UTF-8"));
                            os.close();
                        }
                        int code = c.getResponseCode();
                        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                        if (in != null) text = new String(readAll(in), "UTF-8");
                        ok = code == 200;
                    } catch (Exception e) {
                        ok = false;
                    }
                    final String js = "window.__cb(" + id + "," + ok + "," + JSONObject.quote(text) + ")";
                    web.post(new Runnable() {
                        public void run() {
                            web.evaluateJavascript(js, null);
                        }
                    });
                }
            }).start();
        }
    }
}
