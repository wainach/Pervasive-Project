/*
 * Project: Bearing
 * Copyright (C) 2009 ralfoide gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alfray.bearing;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * Displays intro text.
 */
public class IntroActivity extends Activity {

    public static final String EXTRA_NO_CONTROLS = "no-controls";

    private class JSVersion {

        private String mVersion;

        public String longVersion() {
            if (mVersion == null) {
                PackageManager pm = getPackageManager();
                PackageInfo pi;
                try {
                    pi = pm.getPackageInfo(getPackageName(), 0);
                    mVersion = pi.versionName;
                } catch (NameNotFoundException e) {
                    mVersion = ""; // failed, ignored
                }
            }
            return mVersion;
        }

        public String shortVersion() {
            String v = longVersion();
            v = v.substring(0, v.lastIndexOf('.'));
            return v;
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.intro);

        JSVersion jsVersion = new JSVersion();

        String title = getString(R.string.intro_title, jsVersion.shortVersion());
        setTitle(title);

        WebView wv = (WebView) findViewById(R.id.web);
        if (wv != null) {

            wv.getSettings().setJavaScriptEnabled(true);
            wv.addJavascriptInterface(jsVersion, "JSAppVersion");

            wv.loadUrl("file:///android_asset/intro.html");
            wv.setFocusable(true);
            wv.setFocusableInTouchMode(true);
            wv.requestFocus();
        }

        boolean hideControls = false;
        Intent i = getIntent();
        if (i != null) {
            Bundle e = i.getExtras();
            if (e != null) hideControls = e.getBoolean(EXTRA_NO_CONTROLS);
        }

        CheckBox dismiss = (CheckBox) findViewById(R.id.dismiss);
        if (dismiss != null) {
            if (hideControls) {
                dismiss.setVisibility(View.GONE);
            } else {
                final PrefsValues pv = new PrefsValues(this);
                dismiss.setChecked(pv.isIntroDismissed());

                dismiss.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        pv.setIntroDismissed(isChecked);
                    }
                });
            }
        }

        Button cont = (Button) findViewById(R.id.cont);
        if (cont != null) {
            if (hideControls) {
                cont.setVisibility(View.GONE);
            } else {
                cont.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // close activity
                        finish();
                    }
                });
            }
        }
    }
}
