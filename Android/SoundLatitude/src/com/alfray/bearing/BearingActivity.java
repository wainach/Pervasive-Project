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

import org.pielot.openal.Buffer;
import org.pielot.openal.SoundEnv;
import org.pielot.openal.Source;

import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main bearing activity.
 */
public class BearingActivity extends Activity {

	private final static String	TAG	= "BearingSoundCombi";
	
    // For debugging and taking screenshots
    private static boolean DEBUG_LOC = true;

    private static final int PREFS_DONE = 100;

    private TextView mTextGpsStatus;
    private TextView mTextMarkedLoc;
    private TextView mTextCurrentLoc;
    private TextView mTextCompass;

    private PrefsValues mPrefsValues;

    private LocationManager mLocMan;
    private LocationProvider mGpsProv;
    private GpsLocationListener mGpsLocListener;
    private GpsStatus mLastGpsStatus;
    public Location mMarkedLocation;
    public Location mCurrentLocation;
    private GpsStatusListener mGpsStatusListener;
    public boolean mGpsStarted;
    public int mGpsFixAt;
    public int mNbSat;
    public int mNbSatFix;
    public long mPreviousLocTime;
    public long mLastLocTS;

    private Handler mHandler;
    private PeriodicDisplayUpdate mPeriodicUpdate;

    private SensorManager mSensorMan;
    private Sensor mOrientSensor;
    private OrientListener mOrientListener;
    public String mOrientAccuracy;
    public float mOrientation;
    public long mLastOrientTS;
    private Sensor mAccelSensor;
    private Sensor mMagnetSensor;
    public float mOrientation2;

    private boolean mUseMetrics;
    
    /**
     * OpenAL
     */
	private SoundEnv env;

	private Source lake1;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        mHandler = new Handler();
        mPrefsValues = new PrefsValues(this);
        updatePrefs();
        showIntro(false);

        mTextGpsStatus = (TextView) findViewById(R.id.gps_status);
        mTextMarkedLoc = (TextView) findViewById(R.id.mark_loc);
        mTextCompass = (TextView) findViewById(R.id.curr_compass);
        mTextCurrentLoc = (TextView) findViewById(R.id.current_loc);

        mMarkedLocation = mPrefsValues.getSavedLocation(mMarkedLocation);

        View b = findViewById(R.id.button_mark_loc);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                doMarkLocation();
            }
        });

        b = findViewById(R.id.button_compass);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                doOpenCompass();
            }
        });
        
        // sound
        try {
			/* First we obtain the instance of the sound environment. */
			this.env = SoundEnv.getInstance(this);

			/*
			 * Now we load the sounds into the memory that we want to play
			 * later. Each sound has to be buffered once only. To add new sound
			 * copy them into the assets folder of the Android project.
			 * Currently only mono .wav files are supported.
			 */
			Buffer lake = env.addBuffer("lake");

			/*
			 * To actually play a sound and place it somewhere in the sound
			 * environment, we have to create sources. Each source has its own
			 * parameters, such as 3D position or pitch. Several sources can
			 * share a single buffer.
			 */
			this.lake1 = env.addSource(lake);

			// Now we spread the sounds throughout the sound room.
			this.lake1.setPosition(0,0,0);

			// and change the pitch of the second lake.
			//this.lake1.setPitch(1.1f);

			/*
			 * These sounds are perceived from the perspective of a virtual
			 * listener. Initially the position of this listener is 0,0,0. The
			 * position and the orientation of the virtual listener can be
			 * adjusted via the SoundEnv class.
			 */
			this.env.setListenerOrientation(20);
		} catch (Exception e) {
			Log.e(TAG, "could not initialise OpenAL4Android", e);
		}
    }

    private void showIntro(boolean force) {
        boolean hideControls = force;
        if (!force) {
            BearingApp tapp = getApp();
            if (tapp != null &&
                    !tapp.isIntroDisplayed() &&
                    !mPrefsValues.isIntroDismissed()) {
                tapp.setIntroDisplayed(true);
                force = true;
            }
        }

        if (force) {
            Intent i = new Intent(this, IntroActivity.class);
            if (hideControls) i.putExtra(IntroActivity.EXTRA_NO_CONTROLS, true);
            startActivity(i);
        }
    }

    private BearingApp getApp() {
        Application app = getApplication();
        if (app instanceof BearingApp) return (BearingApp) app;
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        startGps();
        /*
		 * Start playing all sources. 'true' as parameter specifies that the
		 * sounds shall be played as a loop.
		 */
		this.lake1.play(true);
    }

    @Override
    protected void onPause() {
        mPrefsValues.setSavedLocation(mMarkedLocation);
        stopGps();
        super.onPause();
        this.lake1.stop();
    }
    
    @Override
    protected void onDestroy() {
    	// Be nice with the system and release all resources
		this.env.stopAllSources();
		this.env.release();
    }
    
    @Override
	public void onLowMemory() {
		this.env.onLowMemory();
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PREFS_DONE) {
            updatePrefs();
            updateLocationAndBearing();
        }
    }

    // ----- Menu handling ----

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(0, R.string.menu_mark_loc, 0, R.string.menu_mark_loc).setIcon(R.drawable.ic_menu_mylocation);
        menu.add(0, R.string.menu_compass,  0, R.string.menu_compass ).setIcon(R.drawable.ic_menu_compass);
        menu.add(0, R.string.menu_settings, 0, R.string.menu_settings).setIcon(R.drawable.ic_menu_preferences);
        menu.add(0, R.string.menu_about,    0, R.string.menu_about   ).setIcon(R.drawable.ic_menu_help);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.string.menu_mark_loc:
                doMarkLocation();
                break;
            case R.string.menu_compass:
                doOpenCompass();
                break;
            case R.string.menu_about:
                showIntro(true);
                break;
            case R.string.menu_settings:
                startActivityForResult(new Intent(this, PrefsActivity.class), PREFS_DONE);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doMarkLocation() {
        mMarkedLocation = mCurrentLocation;

        if (DEBUG_LOC) {
            // N 48° 52' 25.67'', E 002° 17' 42''
        	// Flintholm st: +55¡ 41' 8.09", +12¡ 29' 55.10"
        	// Vanl¿se st: +55¡ 41' 15.19", +12¡ 29' 29.92"
        	// Islands Brygge: +55¡ 39' 48.52", +12¡ 35' 6.77"
            if (mMarkedLocation == null) mMarkedLocation = new Location("mock");
            mMarkedLocation.setLatitude( Location.convert("55:39:48.52"));
            mMarkedLocation.setLongitude(Location.convert( "12:35:6.77"));

            // N 48° 51' 30'', E 002° 17' 40''
            // Vanl¿se Alle 15:  +55¡ 41' 31.60", +12¡ 30' 13.57"
            // ITU: +55¡ 39' 35.87", +12¡ 35' 28.47"
            if (mCurrentLocation == null) mCurrentLocation = new Location("mock");
            mCurrentLocation.setLatitude( Location.convert("55:39:35.87"));
            mCurrentLocation.setLongitude(Location.convert( "12:35:28.47"));
        }

        updateLocationAndBearing();
    }


    private void doOpenCompass() {
        Intent i = new Intent("com.eclipsim.gpsstatus.VIEW");

        try {
            startActivity(i);
        } catch(ActivityNotFoundException e) {

            Toast.makeText(BearingActivity.this,
                            "GPS Status not found. Launching Market for you.",
                            Toast.LENGTH_SHORT)
                 .show();

            // get http://www.cyrket.com/package/com.eclipsim.gpsstatus2 from Market
            String pkg = "com.eclipsim.gpsstatus2";

            i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse("market://search?q=pname:" + pkg));

            try {
                startActivity(i);
            } catch(ActivityNotFoundException e2) {
                Toast.makeText(BearingActivity.this,
                                "GPS Status not found. Please install it manually.",
                                Toast.LENGTH_LONG)
                     .show();
            }
        }
    }

    // ----- Start and Stop -----

    private void startGps() {
        try {
            if (mLocMan == null) {
                mLocMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            }

            if (mGpsProv == null && mLocMan != null) {
                mGpsProv = mLocMan.getProvider(LocationManager.GPS_PROVIDER);
            }

            if (mLocMan != null) {
                mGpsStatusListener = new GpsStatusListener();
                mLocMan.addGpsStatusListener(mGpsStatusListener);

                mGpsLocListener = new GpsLocationListener();
                mLocMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                1000 /* minTime ms */,
                                1 /* minDistance in meters */,
                                mGpsLocListener);
            }

            if (mSensorMan == null) {
                mSensorMan = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            }

            if (mSensorMan != null) {
                if (mOrientListener == null) {
                    mOrientListener = new OrientListener();
                }

                if (mOrientSensor == null) {
                    mOrientSensor = mSensorMan.getDefaultSensor(Sensor.TYPE_ORIENTATION);
                    if (mOrientSensor != null) {
                        mSensorMan.registerListener(mOrientListener, mOrientSensor, SensorManager.SENSOR_DELAY_NORMAL);
                    }
                }
                if (mAccelSensor == null) {
                    mAccelSensor = mSensorMan.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                    if (mAccelSensor != null) {
                        mSensorMan.registerListener(mOrientListener, mAccelSensor, SensorManager.SENSOR_DELAY_NORMAL);
                    }
                }
                if (mMagnetSensor == null) {
                    mMagnetSensor = mSensorMan.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                    if (mMagnetSensor != null) {
                        mSensorMan.registerListener(mOrientListener, mMagnetSensor, SensorManager.SENSOR_DELAY_NORMAL);
                    }
                }
            }

            mPeriodicUpdate = new PeriodicDisplayUpdate();
            mPeriodicUpdate.run();

        } finally {
            updateGpsStatus();
        }
    }

    private void stopGps() {
        mPeriodicUpdate = null;

        if (mSensorMan != null) {
            if (mOrientListener != null) {
                mSensorMan.unregisterListener(mOrientListener);
                mOrientListener = null;
            }
            mOrientSensor = null;
            mAccelSensor = null;
            mMagnetSensor = null;
            mSensorMan = null;
        }

        if (mLocMan != null) {

            if (mGpsLocListener != null) {
                mLocMan.removeUpdates(mGpsLocListener);
                mGpsLocListener = null;
            }
            if (mGpsStatusListener != null) {
                mLocMan.removeGpsStatusListener(mGpsStatusListener);
                mGpsStatusListener = null;
            }
            mGpsProv = null;
            mLocMan = null;
        }

        updateGpsStatus();
    }

    // ---- Display Updates ----

    private class PeriodicDisplayUpdate implements Runnable {
        @Override
        public void run() {
            if (mPeriodicUpdate == null) return;

            updateLocationAndBearing();
            updateCompass();
            mHandler.postDelayed(mPeriodicUpdate, 2000 /* ms */);
        }
    }

    private void updateGpsStatus() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("----- GPS Status: ");

            if (mLocMan == null) {
                sb.append("No location manager.\n");
                return;
            }

            if (!mLocMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                sb.append("GPS is disabled in settings.\n");
                return;
            }

            if (mGpsProv == null) {
                sb.append("No GPS provider.\n");
                return;
            }

            sb.append(mGpsStarted ? "Started\n" : "Stopped\n");

            sb.append(String.format("Fix: %.1f s\n", mGpsFixAt / 1000.f));

            sb.append(String.format("Satellites: %d (%d in fix)",
                            mNbSat, mNbSatFix));

        } finally {
            mTextGpsStatus.setText(sb.toString());
        }
    }

    public void updateLocationAndBearing() {
        StringBuilder sb = new StringBuilder();

        Location location = mCurrentLocation;

        if (mCurrentLocation != null) {
            sb.append("------ Current Location -----\n");
            sb.append(String.format("%s, %s\n\n",
                            Location.convert(location.getLatitude(), Location.FORMAT_SECONDS),
                            Location.convert(location.getLongitude(), Location.FORMAT_SECONDS)));
            if (mUseMetrics) {
                sb.append(String.format("Accuracy %.0f m", location.getAccuracy()));
            } else {
                sb.append(String.format("Accuracy %.1f ft", m_to_ft(location.getAccuracy())));
            }
            sb.append("\n");

            sb.append(String.format("Bearing %.1f° -- ", location.getBearing()));

            if (mUseMetrics) {
                sb.append(String.format("Speed %.1f km/h", ms_to_kmh(location.getSpeed())));
            } else {
                sb.append(String.format("Speed %.1f mph", ms_to_mph(location.getSpeed())));
            }
            sb.append("\n");

            if (mLastLocTS > 0) {
                long ts = (System.currentTimeMillis() - mLastLocTS) / 1000;
                sb.append(String.format("Updated: %d s ago, Previous: %d s", ts, mPreviousLocTime / 1000));
            }

            mTextCurrentLoc.setText(sb.toString());
        }

        if (mMarkedLocation != null) {
            sb.setLength(0);

            sb.append("----- Marked Location -----\n");
            sb.append(String.format("%s, %s\n\n",
                            Location.convert(mMarkedLocation.getLatitude(), Location.FORMAT_SECONDS),
                            Location.convert(mMarkedLocation.getLongitude(), Location.FORMAT_SECONDS)));

            if (location != null) {
                float d = mMarkedLocation.distanceTo(location);
                sb.append(String.format("Bearing %.1f° -- ", location.bearingTo(mMarkedLocation)));

                if (mUseMetrics) {
                    sb.append(String.format("Dist %.1f m", d));
                } else {
                    sb.append(String.format("Dist %.1f ft", m_to_ft(d)));
                }
            }

            mTextMarkedLoc.setText(sb.toString());
        }

    }

    public void updateCompass() {
        StringBuilder sb = new StringBuilder();

        // VI HAR IKKE BRUG FOR BEARING?
        
        // Recalc compass values
        //if(mOrientation != 0 && mOrientation > 180) mOrientation = mOrientation - 360;
        // Calc degrees from orientation in relation to bearing
        float dir = 0;
        if(mCurrentLocation != null) {
        	dir = mOrientation - mCurrentLocation.bearingTo(mMarkedLocation);
        	
        	// update sound position
        	// longitude (x position in 2d)
        	// latitude (y position in 2d)
        	
        	Log.i("Current location (lat,long): ", Double.toString(mCurrentLocation.getLatitude())+", "+Double.toString(mCurrentLocation.getLongitude()));
        	Log.i("Marked location (lat,long): ", Double.toString(mMarkedLocation.getLatitude())+", "+Double.toString(mMarkedLocation.getLongitude()));
        	
        	this.lake1.setPosition((float)mMarkedLocation.getLongitude()*500,(float)mMarkedLocation.getLatitude()*500, 0);
        	this.env.setListenerPos((float)mCurrentLocation.getLongitude()*500, (float)mCurrentLocation.getLatitude()*500, 0);
        	
        	//this.lake1.setPosition(30,-17,0);
        	//this.env.setListenerPos(30,-17,0);
        	
        	this.env.setListenerOrientation(mOrientation);
        }
        
        sb.append("------ Compass -----\n");
        sb.append(String.format("Heading: %.1f° (avg %.1f°, %s), direction(new) %.1f",
                        mOrientation, mOrientation2, mOrientAccuracy, dir));

        long ts = (System.currentTimeMillis() - mLastOrientTS) / 1000;
        if (mLastOrientTS > 0 && ts > 2) {
            sb.append(String.format(" Delay: %d s ago", ts));
        }

        mTextCompass.setText(sb.toString());
    }

    private float ms_to_mph(float speed_mps) {
        return speed_mps * 2.23693629f;
    }

    private float ms_to_kmh(float speed_mps) {
        return speed_mps * 3.6f;
    }

    private float m_to_ft(float m) {
        return m * 3.2808399f;
    }

    // ---- GPS and Compass listeners ----

    private class GpsLocationListener implements LocationListener {

        @Override
        public void onProviderDisabled(String provider) {
            // pass
        }

        @Override
        public void onProviderEnabled(String provider) {
            // pass
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            updateGpsStatus();
        }

        @Override
        public void onLocationChanged(Location location) {
            if (!DEBUG_LOC) {
                mCurrentLocation = new Location(location);
            }

            updateLocationAndBearing();
            long ts = System.currentTimeMillis();
            if (mLastLocTS > 0) mPreviousLocTime = ts - mLastLocTS;
            mLastLocTS = ts;
        }

    }

    private class OrientListener implements SensorEventListener {

//        private float[] mLastAccel = null;
//        private float[] mLastMagnet = null;
//        private float[] mTempRotMatrix = new float[9];
//        private float[] mTempOrient = new float[3];
        private static final int WINDOW_SIZE = 20;
        private float[] mWindow = new float[WINDOW_SIZE];
        private int mNbWindow = -1 * WINDOW_SIZE;
        private float mWindowAvg = 0;

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // pass
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor == mOrientSensor) {
                String accuracy =
                    event.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH ? "High" :
                        event.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ? "Medium" :
                            event.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ? "Low" :
                                event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ? "Unreliable" :
                                    "Unknown";

                mOrientAccuracy = accuracy;
                mLastOrientTS = System.currentTimeMillis();

                mOrientation  = event.values[0];
                mOrientation2 = windowAvg(event.values[0]);
                updateCompass();

//                if (mLastAccel != null && mLastMagnet != null) {
//
//
//                    if (SensorManager.getRotationMatrix(
//                                    mTempRotMatrix, //R
//                                    null, // I
//                                    mLastAccel, //gravity
//                                    mLastMagnet //geomagnetic
//                                    )) {
//                        SensorManager.getOrientation(mTempRotMatrix, mTempOrient);
//
//                        float orient = (float) (mTempOrient[0] * 180 / Math.PI);
//
//                    }
//                }
//            } else if (event.sensor == mAccelSensor) {
//                if (mLastAccel == null) {
//                    mLastAccel = new float[3];
//                }
//                System.arraycopy(event.values, 0, mLastAccel, 0, 3);
//
//            } else if (event.sensor == mMagnetSensor) {
//                if (mLastMagnet == null) {
//                    mLastMagnet = new float[3];
//                }
//                System.arraycopy(event.values, 0, mLastMagnet, 0, 3);
            }
        }

        private float windowAvg(float v) {

            int n = mNbWindow;

            if (n < 0) {
                // fill initial values: n=-10 ... n=-1
                n++;
                // -9 => 10-9-1 = 0 ... 0 => 10+0-1 = -9
                int j = WINDOW_SIZE + n - 1;
                mWindow[j] = v;

                if (n == 1 - WINDOW_SIZE) {
                    mWindowAvg = v;
                } else {
                    // Before: T = Sum/n
                    // After : n'= n+1
                    //         T'= Sum/n' + v/n'
                    //         T'= T*n/n' + v/n'
                    //         T'= (T*n+v)/n'
                    mWindowAvg = (j * mWindowAvg + v) / (j+1);
                }
            } else {
                // n >= 0
                // Before: T = Sum/n
                // After : T'= (Sum - value[-10] + new_value) / n
                //         T'= T - v[-10]/n + v/n
                //         T'= T + (v - v[-10]) / n

                float old = mWindow[n];
                mWindow[n] = v;
                n++;
                if (n == WINDOW_SIZE) n = 0;

                mWindowAvg += (v - old) / WINDOW_SIZE;
            }

            mNbWindow = n;
            return mWindowAvg;
        }
    }

    private class GpsStatusListener implements GpsStatus.Listener {

        @Override
        public void onGpsStatusChanged(int event) {
            if (mLocMan == null) return;

            mLastGpsStatus = mLocMan.getGpsStatus(mLastGpsStatus);

            switch(event) {
                case GpsStatus.GPS_EVENT_STARTED:
                    mGpsStarted = true;
                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    mGpsStarted = false;
                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    mGpsFixAt = mLastGpsStatus.getTimeToFirstFix();
                    break;

                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:

                    mNbSat = 0;
                    mNbSatFix = 0;

                    for (GpsSatellite sat : mLastGpsStatus.getSatellites()) {
                        mNbSat++;
                        if (sat.usedInFix()) mNbSatFix++;
                    }
                    break;
            }

            updateGpsStatus();
        }
    }

    // ----- prefs ----

    private void updatePrefs() {
        mUseMetrics = mPrefsValues.useMetricUnits();
    }


}