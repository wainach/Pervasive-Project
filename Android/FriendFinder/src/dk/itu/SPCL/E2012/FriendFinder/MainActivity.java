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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity implements Observer {

	private final static String	TAG	= "MainActivity";
	private LocationManager locationManager;
	private LocationListener locationListener;
	private Location currentBestLocation;
	private float inducedBearing; // Bearing calculated on location changes
	
	private String UUID; // Phone-specific id
	private List<Friend> friends; // Friends represented by their phone's id
	private final String[] SOUNDS = {"submarine2", "owl", "sonar", "submarine", "lake"};
	private UIData uiData; // Object for storing data to be presented in UI
	
	private class UIData {
		public String uiText = "";
		public String position = "";
	}
	
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
		connectToFriendFinderWS("http://martinpoulsen.pythonanywhere.com/positions/json");
		getAndroidLocation();
		
		// sound
        try {
			/* First we obtain the instance of the sound environment. */
			this.env = SoundEnv.getInstance(this);
		} 
        catch (Exception e) {
			Log.e(TAG, "could not initialise OpenAL4Android", e);
		}
	}

	// this.env.setListenerOrientation(20);
	// this.lake1.setPosition(0,0,0);
	
	/*
	 * GET UNIQUE ID FOR THIS DEVICE
	 */
	private String getUUID() {
		final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);

	    final String tmDevice, tmSerial, androidId;
	    tmDevice = "" + tm.getDeviceId();
	    tmSerial = "" + tm.getSimSerialNumber();
	    androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

	    UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
	    return deviceUuid.toString();
	}
	///////////////////////////////////////////////////////////////////
	

	
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
				textView.setText("Friends: \n" + uiData.uiText + "\n\n" + "Me: \n" + uiData.position + "\n" + "InducedBearing: " + inducedBearing);
			}
		});
	}
	///////////////////////////////////////////////////////////////////
	
	
	
	/*
	 * COMMUNICATION WITH FRIENDFINDER WEB SERVICE
	 */
	private void connectToFriendFinderWS(String url) {
		AsyncConnection connection = new AsyncConnection(url, this);
		new Thread(connection).start();
	}
	
	// Called every time worker thread has polled web service for a location request
	@Override
	public void update(Observable observable, Object data) {

		for (String[] location : (ArrayList<String[]>) data) {
			String friendUUID = location[0];
			// continue to next id if we are looking at our own
			if (friendUUID.equals(UUID))
				continue;
			Friend f;
			if (newFriend(friendUUID)) {
				Log.i(TAG, "New friend: " + friendUUID);
				f = new Friend(friendUUID, SOUNDS[friends.size()]);
				friends.add(f);
				try {
					soundMap.put(f.getSound(), env.addSource(env.addBuffer(f.getSound())));
					soundMap.get(f.getSound()).play(true);
					Log.i(TAG, "Sound added: " + f.getSound());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else {
				Log.i(TAG, "Old friend: " + friendUUID);
				f = getFriend(friendUUID);
			}
			f.setLat(Float.parseFloat(location[1]));
			f.setLon(Float.parseFloat(location[2]));
			f.setAlt(Float.parseFloat(location[3]));
			
			soundMap.get(f.getSound()).setPosition(f.getLon(), f.getLat(), f.getAlt());
			
			StringBuilder sb = new StringBuilder();
			sb.append(location[0] + "\n----" + location[1] + ", " + location[2] + "\n");
			uiData.uiText = sb.toString();
		}		
		updateDisplay();
	}
	///////////////////////////////////////////////////////////////////

	
	
	/*
	 * HANDELING LOCATION
	 */
	private void getAndroidLocation() {

		// Acquire a reference to the system Location Manager
		locationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);

		// Define a listener that responds to location updates
		locationListener = new LocationListener() {
			
			// Called when a new location is found by the network location provider.
			public void onLocationChanged(Location location) {
				
				if (!isBetterLocation(location, currentBestLocation)) {
					Log.i(TAG, "new location is NOT better");
					return;
				} else {
					// induce direction by comparing the old and the new locations
					if (currentBestLocation != null) {
						inducedBearing = currentBestLocation.bearingTo(location);
						Log.i(TAG, "inducedBearing: " + inducedBearing);
					}
					
					currentBestLocation = location;
					Log.i(TAG, "new location is better");
				}

				double lat = currentBestLocation.getLatitude();
				double lon = currentBestLocation.getLongitude();
				double alt = currentBestLocation.getAltitude();
				Log.i(TAG, "A_lat: " + lat + " , A_lon: " + lon);
				
				// Update listener position
				env.setListenerPos((float) lon, (float) lat, (float) alt);
				
				// Post position to web service
				String[] postData = new String[4];
				postData[0] = UUID;
				postData[1] = Double.toString(lat);
				postData[2] = Double.toString(lon);
				postData[3] = Double.toString(alt);
				new RestClient().postData(postData, "http://martinpoulsen.pythonanywhere.com/positions/json/");
				Log.i(TAG, "Location posted to WS");
				
				// Present location on screen
				uiData.position = 
						"lat: "	+ currentBestLocation.getLatitude() + 
						", lon: " + currentBestLocation.getLongitude();
				updateDisplay();
			}

			public void onStatusChanged(String provider, int status, Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}

		};

		// Register the listener with the Location Manager to receive location updates
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
		int accuracyDelta = (int) (location.getAccuracy() - currentlyBestLocation
				.getAccuracy());
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
	///////////////////////////////////////////////////////////////////
	
	
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
