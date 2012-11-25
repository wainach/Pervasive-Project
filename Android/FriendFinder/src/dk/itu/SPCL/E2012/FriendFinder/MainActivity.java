package dk.itu.SPCL.E2012.FriendFinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.UUID;

import org.pielot.openal.Buffer;
import org.pielot.openal.SoundEnv;
import org.pielot.openal.Source;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity implements Observer {

	private final static String TAG = "MainActivity";
	private LocationManager locationManager;
	private LocationListener locationListener;
	private Location currentBestLocation;
	private float inducedDirection; // Bearing calculated on location changes
	private float altToMetricFactor = 1000f; // ???
	private float latToMetricFactor = 1113.5f; // 1 : 100m
	private float lonToMetricFactor = 629.5f; // 1 : 100m
	final int distFactor = 1000; // Distance factor
	
	private SensorManager mSensorMan;
	private Sensor mOrientSensor;
	private Sensor mAccelSensor;
	private Sensor mMagnetSensor;
	private OrientListener mOrientListener;
	public String mOrientAccuracy;
	public long mLastOrientTS;
	private float mOrientation;
	public float mOrientation2;

	private String UUID; // Phone-specific id
	private List<Friend> friends; // Friends represented by their phone's id
	// private final String[] SOUND_STRINGS = {"submarine2", "owl", "sonar",
	// "submarine", "lake"};
	private final String[] SOUND_STRINGS = { "owl", "monster-growl",
			"submarine2", "lake", "galloping_horse", "sonar", "submarine",
			"duck" };
	private UIData uiData; // Object for storing data to be presented in UI

	private class UIData {
		public String uiText = "";
		public String position = "";
	}

	// latest friend locations
	ArrayList<String[]> latestFriendLocations = new ArrayList<String[]>();


	/**
	 * OpenAL
	 */
	private SoundEnv env;
	private HashMap<String, Source> soundMap;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		uiData = new UIData();
		UUID = getUUID();
		friends = new ArrayList<Friend>();
		soundMap = new HashMap<String, Source>();
		connectToFriendFinderWS("http://martinpoulsen.pythonanywhere.com/positions/json/get_locations", UUID);
		getAndroidLocation();

		// orientation
		mSensorMan = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		if (mSensorMan != null) {
			if (mOrientListener == null) {
				mOrientListener = new OrientListener();
			}

			if (mOrientSensor == null) {
				mOrientSensor = mSensorMan
						.getDefaultSensor(Sensor.TYPE_ORIENTATION);
				if (mOrientSensor != null) {
					mSensorMan.registerListener(mOrientListener, mOrientSensor,
							SensorManager.SENSOR_DELAY_NORMAL);
				}
			}
			if (mAccelSensor == null) {
				mAccelSensor = mSensorMan
						.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
				if (mAccelSensor != null) {
					mSensorMan.registerListener(mOrientListener, mAccelSensor,
							SensorManager.SENSOR_DELAY_NORMAL);
				}
			}
			if (mMagnetSensor == null) {
				mMagnetSensor = mSensorMan
						.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
				if (mMagnetSensor != null) {
					mSensorMan.registerListener(mOrientListener, mMagnetSensor,
							SensorManager.SENSOR_DELAY_NORMAL);
				}
			}
		}

		// sound
		try {
			/* First we obtain the instance of the sound environment. */
			this.env = SoundEnv.getInstance(this);
		} catch (Exception e) {
			Log.e(TAG, "could not initialise OpenAL4Android", e);
		}
	}

	// this.env.setListenerOrientation(20);
	// this.lake1.setPosition(0,0,0);

	/*
	 * GET UNIQUE ID FOR THIS DEVICE
	 */
	private String getUUID() {
		final TelephonyManager tm = (TelephonyManager) getBaseContext()
				.getSystemService(Context.TELEPHONY_SERVICE);

		final String tmDevice, tmSerial, androidId;
		tmDevice = "" + tm.getDeviceId();
		tmSerial = "" + tm.getSimSerialNumber();
		androidId = ""
				+ android.provider.Settings.Secure.getString(
						getContentResolver(),
						android.provider.Settings.Secure.ANDROID_ID);

		UUID deviceUuid = new UUID(androidId.hashCode(),
				((long) tmDevice.hashCode() << 32) | tmSerial.hashCode());
		return deviceUuid.toString();
	}

	// /////////////////////////////////////////////////////////////////

	/*
	 * HANDLING UI DATA
	 */
	public void clearDisplay(View v) {
		uiData.uiText = "";
		updateDisplay();
	}

	private void updateDisplay() {
		this.runOnUiThread(new Runnable() {
			public void run() {
				TextView textView = (TextView) findViewById(R.id.mainTextView);
				textView.setText("Friends: \n" + uiData.uiText + "\n\n"
						+ "Me: \n" + uiData.position + "\n" + "mOrientation: "
						+ mOrientation + "\n" + "InducedDirection: "
						+ inducedDirection);
			}
		});
	}

	// /////////////////////////////////////////////////////////////////

	/*
	 * COMMUNICATION WITH FRIENDFINDER WEB SERVICE
	 */
	private void connectToFriendFinderWS(String url, String UUID) {
		AsyncConnection connection = new AsyncConnection(url, UUID, this);
		new Thread(connection).start();
	}

	// Called every time worker thread has polled web service for a location
	// request
	@Override
	public void update(Observable observable, Object data) {
		latestFriendLocations = (ArrayList<String[]>) data;
		StringBuilder sb = new StringBuilder();
		if (((ArrayList<String[]>) data).size() - 1 > this.friends.size()) {

			// stop all sounds of friends and release their buffer
			for (String[] location : (ArrayList<String[]>) data) {
				String friendUUID = location[0];
				try {
					this.soundMap.get(getFriend(friendUUID).getSound()).stop();
					this.soundMap.get(getFriend(friendUUID).getSound())
							.release();
				} catch (Exception e) {
					Log.e(TAG,
							"Exception catched when trying to stop a sound in update() method");
				}
			}

			// remove all friends - active players will be re-created below
			friends.clear();
		}

		for (String[] location : (ArrayList<String[]>) data) {
			String friendUUID = location[0];
			// continue to next id if we are looking at our own UUID
			if (friendUUID.equals(UUID))
				continue;
			Friend f;
			if (newFriend(friendUUID)) {
				Log.i(TAG, "New friend: " + friendUUID);
				f = new Friend(friendUUID, SOUND_STRINGS[friends.size()]);
				friends.add(f);
				try {
					soundMap.put(f.getSound(),
							env.addSource(env.addBuffer(f.getSound())));
					soundMap.get(f.getSound()).play(true);
					Log.i(TAG, "Sound added: " + f.getSound());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				Log.i(TAG, "Old friend: " + friendUUID);
				f = getFriend(friendUUID);
			}
			f.setLat(Float.parseFloat(location[1]));
			f.setLon(Float.parseFloat(location[2]));
			f.setAlt(Float.parseFloat(location[3]));

			// soundMap.get(f.getSound()).setPosition(f.getLon(), f.getLat(),
			// f.getAlt());

			sb.append(location[0] + "\n----" + location[1] + ", " + location[2]
					+ "\n");

		}
		this.updateSoundOrientation();
		uiData.uiText = sb.toString();
		updateDisplay();

		// check distance
		checkDistance();
	}

	// /////////////////////////////////////////////////////////////////

	/*
	 * HANDLING ORIENTATION SENSOR
	 */

	private class OrientListener implements SensorEventListener {

		// private float[] mLastAccel = null;
		// private float[] mLastMagnet = null;
		// private float[] mTempRotMatrix = new float[9];
		// private float[] mTempOrient = new float[3];
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
				String accuracy = event.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH ? "High"
						: event.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ? "Medium"
								: event.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ? "Low"
										: event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ? "Unreliable"
												: "Unknown";

				mOrientAccuracy = accuracy;
				mLastOrientTS = System.currentTimeMillis();

				mOrientation = event.values[0];
				mOrientation2 = windowAvg(event.values[0]);

				updateOrientation();
				updateSoundOrientation();
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
					// T'= Sum/n' + v/n'
					// T'= T*n/n' + v/n'
					// T'= (T*n+v)/n'
					mWindowAvg = (j * mWindowAvg + v) / (j + 1);
				}
			} else {
				// n >= 0
				// Before: T = Sum/n
				// After : T'= (Sum - value[-10] + new_value) / n
				// T'= T - v[-10]/n + v/n
				// T'= T + (v - v[-10]) / n

				float old = mWindow[n];
				mWindow[n] = v;
				n++;
				if (n == WINDOW_SIZE)
					n = 0;

				mWindowAvg += (v - old) / WINDOW_SIZE;
			}

			mNbWindow = n;
			return mWindowAvg;
		}
	}

	/*
	 * HELPER METHODS
	 */
	private float normalizeDegrees(float degrees) {
		if (degrees > 360f)
			degrees = normalizeDegrees(degrees - 360f);
		else if (degrees < 0f)
			degrees = normalizeDegrees(degrees + 360f);
		return degrees;
	}

	private void updateOrientation() {
		// listener orientation is the compass orientation
		this.env.setListenerOrientation(mOrientation);
		// Log.i("ORIENTATION", Float.toString(mOrientation));

		this.updateSoundOrientation();

		this.updateDisplay();
	}

	/*
	 * HANDLING LOCATION
	 */
	private void getAndroidLocation() {

		// Acquire a reference to the system Location Manager
		locationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);

		// Define a listener that responds to location updates
		locationListener = new LocationListener() {

			// Called when a new location is found by the network location
			// provider.
			public void onLocationChanged(Location location) {

				if (!isBetterLocation(location, currentBestLocation)) {
					Log.i(TAG, "new location is NOT better");
					return;
				} else {
					// induce direction by comparing the old and the new
					// locations
					if (currentBestLocation != null) {
						inducedDirection = currentBestLocation.bearingTo(location);
						Log.i(TAG, "inducedBearing: " + inducedDirection);
					}

					currentBestLocation = location;
					Log.i(TAG, "new location is better");
				}

				double lat = currentBestLocation.getLatitude();
				double lon = currentBestLocation.getLongitude();
				double alt = currentBestLocation.getAltitude();
				Log.i(TAG, "A_lat: " + lat + " , A_lon: " + lon);

				// Update listener position
				/*env.setListenerPos((float) lon * lonToMetricFactor, (float) lat
						* latToMetricFactor, (float) alt * altToMetricFactor);*/
				env.setListenerPos((float) lon * lonToMetricFactor, (float) lat
				* latToMetricFactor, 0);
				// this.env.setListenerPos((float)this.currentBestLocation.getLongitude()*500,
				// (float)this.currentBestLocation.getLatitude()*500, 0);

				// Post position to web service
				String[] postData = new String[4];
				postData[0] = UUID;
				postData[1] = Double.toString(lat);
				postData[2] = Double.toString(lon);
				postData[3] = Double.toString(alt);
				RestClient.postData(postData, "http://martinpoulsen.pythonanywhere.com/positions/json/post_location/");
				Log.i(TAG, "Location posted to WS");

				// Present location on screen
				uiData.position = "lat: " + currentBestLocation.getLatitude()
						+ ", lon: " + currentBestLocation.getLongitude();
				updateDisplay();

				// distance check
				checkDistance();
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}

		};

		// Register the listener with the Location Manager to receive location
		// updates
		locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
				0, locationListener);
	}

	private static final int TEN_SECONDS = 1000 * 10;

	protected boolean isBetterLocation(Location location,
			Location currentlyBestLocation) {
		if (currentlyBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentlyBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > TEN_SECONDS;
		boolean isSignificantlyOlder = timeDelta < -TEN_SECONDS;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use
		// the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
			// If the new location is more than two minutes older, it must be
			// worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentlyBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(),
				currentlyBestLocation.getProvider());

		// Determine location quality using a combination of timeliness and
		// accuracy
		if (isMoreAccurate) {
			return true;
		} else if (isNewer && !isLessAccurate) {
			return true;
		} else if (isNewer && !isSignificantlyLessAccurate
				&& isFromSameProvider) {
			return true;
		}
		return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) {
			return provider2 == null;
		}
		return provider1.equals(provider2);
	}

	// /////////////////////////////////////////////////////////////////

	/*
	 * HANDLING ORIENTATION
	 */
	public void updateSoundOrientation() {

		for (Friend f : friends) {
			Source sound = this.soundMap.get(f.getSound());
			Location soundLocation = f.getLocation();

			// new bearing value
			float bearingAngle = 0;

			if (this.currentBestLocation != null && soundLocation != null
					&& sound != null) {

				sound.setPosition((float) soundLocation.getLongitude()
						* this.lonToMetricFactor,
						(float) soundLocation.getLatitude()
								* this.latToMetricFactor, 0);
				// Log.i("FRIEND POSITION", "long: " + (float)
				// soundLocation.getLongitude() * this.gpsToMetricFactor +
				// " | lat: " + (float) soundLocation.getLatitude() *
				// this.gpsToMetricFactor);

				// bearing angle from magnetic north (interval -180 to 180)
				bearingAngle = normalizeDegrees(this.currentBestLocation
						.bearingTo(soundLocation));
				Log.i("DIRECTION",
						"bearingAngle: " + Float.toString(bearingAngle));

				Log.i("DIRECTION",
						"mOrientation: " + Float.toString(mOrientation));

				// difference from compass value (interval out 0 to 360)
				float dir = normalizeDegrees(mOrientation - bearingAngle);
				Log.i("DIRECTION", "dir: " + Float.toString(dir));

				// check for back or front and adjust gain (vol)
				float max = 1;
				float min = 0.2f;
				float dirStart = 30;
				float dirEnd = 350;
				// example formula
				// factor R = (20 - 10) / (6 - 2)
				// gain y = (x - 2) * R + 10
				// right side
				if (dir > dirStart && dir < 180) {
					float R = (min - max) / (180 - dirStart);
					float gain = (dir - dirStart) * R + max;
					Log.i("GAIN", "Right: " + Float.toString(gain));
					sound.setGain(gain);
				}
				// left side
				else if (dir > 180 && dir < dirEnd) {
					float R = (min - max) / (180 - dirEnd);
					float gain = (dir - dirEnd) * R + max;
					Log.i("GAIN", "Left: " + Float.toString(gain));
					sound.setGain(gain);
				}
				// we are on our way
				else {
					sound.setGain(1);
					Log.i("GAIN", "Center: 1");
				}
			}
		}
	}

	/*
	 * HANDLING DISTANCE
	 */
	private void checkDistance() {

		if (currentBestLocation != null) {
        
			 // Iterate
			 for (Friend f : friends) {
				
				float distBetween = currentBestLocation.distanceTo(f.getLocation());
				 
				// Get sound information
				Source sound = this.soundMap.get(f.getSound());
				
				// continuous pitch
			
				float pitchMax = 2.0f;
				float pitchMin = 1.0f;
				float distMax = 3000;
				float distMin = 10;
				
				if (distBetween < distMin) {
						
					Log.i("PITCH", "Adjust to: " + Float.toString(pitchMax));
					sound.setPitch(pitchMax);
					
				} else if (distBetween > distMax) {
					
					Log.i("PITCH", "Adjust to: " + Float.toString(pitchMin));
					sound.setPitch(pitchMin);
					
				} else {
					
					// Need to test this calculation properly
					
					float OldRange = (distMax - distMin);
					float NewRange = (pitchMax - pitchMin);
					float pitch = (((distBetween - distMin) * NewRange) / OldRange) + pitchMin;
					
					Log.i("PITCH", "Adjust to: " + Float.toString(pitch));
					sound.setPitch(pitch);
					
				}
				
				/*
				pitch with levels
				
				// Check distance levels and set pitch appropriately
				if (distBetween < (5 / 10) * distFactor) {
					
					if (distBetween < (4 / 10) * distFactor) {
					
						if (distBetween < (3 / 10) * distFactor) {
							
							if (distBetween < (2 / 10) * distFactor) {
								
								if (distBetween < (1 / 10) * distFactor) {
									 
										sound.setPitch(2.0f);
										Log.i("PITCH CHANGE", "Pitch: 2.0");
								 
								 } else {
									sound.setPitch(1.8f);
									Log.i("PITCH CHANGE", "Pitch: 1.8");
								 }
							 
							 } else {
								 sound.setPitch(1.6f);
								 Log.i("PITCH CHANGE", "Pitch: 1.6");
							 }
							 
						 } else {
							 sound.setPitch(1.4f);
							 Log.i("PITCH CHANGE", "Pitch: 1.4");
						 }
					 
					 } else {
						sound.setPitch(1.2f);
						Log.i("PITCH CHANGE", "Pitch: 1.2");
					 }
				} 
				*/
				
				
				// Log distance
				Log.i("TESTING DISTANCE", "Distance to " + f.getId() + ": "	+ Double.toString(currentBestLocation.distanceTo(f.getLocation())));
			 
			 }
		}
	}

	// /////////////////////////////////////////////////////////////////

	/*
	 * UTILITY FUNCTIONS
	 */
	private boolean newFriend(String id) {
		for (Friend f : friends) {
			if (id.equals(f.getId()))
				return false;
		}
		return true;
	}

	private Friend getFriend(String id) {
		for (Friend f : friends) {
			if (id.equals(f.getId()))
				return f;
		}
		return null;
	}

}
