package com.ardaxi.authenticator;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class LegalActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.webview);
		WebView browser=(WebView)findViewById(R.id.webkit);
		browser.loadUrl("file:///android_asset/legal.html");
	}
}
