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


import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.preference.PreferenceManager;

/**
 * Helper for pref values storage.
 */
public class PrefsValues {

    private final Context mContext;
    private SharedPreferences mPrefs;

	public PrefsValues(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
	}

	public SharedPreferences getPrefs() {
        return mPrefs;
    }

    public boolean isIntroDismissed() {
        return mPrefs.getBoolean("dismiss_intro", false);
    }

    /**
     * Sets the dismiss_intro boolean value.
     * @return true if value was successfully changed if the prefs
     */
    public boolean setIntroDismissed(boolean dismiss) {
        return mPrefs.edit().putBoolean("dismiss_intro", dismiss).commit();
    }

    public Location getSavedLocation(Location location) {
        try {
            double lat = Double.parseDouble(mPrefs.getString("saved-loc-lat", null));
            double lng = Double.parseDouble(mPrefs.getString("saved-loc-long", null));

            if (location == null) location = new Location((String)null);
            location.setLatitude(lat);
            location.setLongitude(lng);
            return location;

        } catch (NullPointerException e) {
            return null;
        } catch (NumberFormatException e2) {
            return null;
        }
    }

    public void setSavedLocation(Location location) {
        Editor e = mPrefs.edit();
        try {
            e.putString("saved-loc-lat",
                    location == null ? null : Double.toString(location.getLatitude()));
            e.putString("saved-loc-long",
                    location == null ? null : Double.toString(location.getLongitude()));
        } finally {
            e.commit();
        }
    }

    public boolean useMetricUnits() {
        if (mPrefs.contains("metric_units")) {
            return mPrefs.getBoolean("metric_units", false);
        }

        // otherwise use the default based on local resources
        String s = mContext.getString(R.string.prefs_units_metric_default);
        return s != null && Boolean.parseBoolean(s);
    }
}
