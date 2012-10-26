package dk.itu.SPCL.E2012.FriendFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.UUID;

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

	private LocationManager locationManager;
	private LocationListener locationListener;
	private Location currentBestLocation;
	private float inducedBearing; // Bearing calculated on location changes
	
	private String UUID; // Phone-specific id
	private List<String> friends; // Friends represented by their phone's id
	private UIData uiData; // Object for storing data to be presented in UI
	private class UIData {
		public String uiText = "";
		public String position = "";
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		uiData = new UIData();
		UUID = getUUID();

		connectToFriendFinderWS("http://martinpoulsen.pythonanywhere.com/positions/json");
		getAndroidLocation();
	}

	private String getUUID() {
		final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);

	    final String tmDevice, tmSerial, androidId;
	    tmDevice = "" + tm.getDeviceId();
	    tmSerial = "" + tm.getSimSerialNumber();
	    androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

	    UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
	    return deviceUuid.toString();
	}
	

	/*
	 * HANDELING UI DATA
	 */
	public void clearDisplay(View v) {
		uiData.uiText = "";
		updateDisplay();
	}

	private void updateDisplay() {
		this.runOnUiThread(new Runnable() {
			public void run() {
				TextView textView = (TextView) findViewById(R.id.mainTextView);
				textView.setText(uiData.uiText + "\n\n" + uiData.position);
			}
		});
	}
	///////////////////////////////////////////////////////////////////
	
	
	
	/*
	 * COMMUNICATION WITH FRIENDFINDER WEBSERVICE
	 */
	private void connectToFriendFinderWS(String url) {
		AsyncConnection connection = new AsyncConnection(url, this);
		new Thread(connection).start();
	}
	
	// Called every time worker thread has polled web service for a location request
	@Override
	public void update(Observable observable, Object data) {
		// TODO Auto-generated method stub
		Log.i("mapou", "update() " + data);
		// uiData.uiText = (String) data;
		ArrayList<String[]> locations = (ArrayList<String[]>) data;
		StringBuilder sb = new StringBuilder();

		for (String[] location : locations) {
			String UUID = location[0];
			float lat = Float.parseFloat(location[1]);
			float lon = Float.parseFloat(location[2]);
			sb.append(location[0] + "\n----" + location[1] + ", " + location[2] + "\n");

			// TODO: Update position of sound associated with UUID
		}
		uiData.uiText = sb.toString();
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
					Log.i("mapou", "new location is NOT better");
					return;
				} else {
					// induce direction by comparing the old and the new locations
					if (currentBestLocation != null) {
						inducedBearing = currentBestLocation.bearingTo(location);
						Log.i("mapou", "inducedBearing: " + inducedBearing);
					}
					
					currentBestLocation = location;
					Log.i("mapou", "new location is better");
				}

				double lat = currentBestLocation.getLatitude();
				double lon = currentBestLocation.getLongitude();
				double alt = currentBestLocation.getAltitude();
				Log.i("mapou", "A_lat: " + lat + " , A_lon: " + lon);
				
				// Post position to web service
				String[] postData = new String[4];
				postData[0] = UUID;
				postData[1] = Double.toString(lat);
				postData[2] = Double.toString(lon);
				postData[3] = Double.toString(alt);
				new RestClient().postData(postData, "http://martinpoulsen.pythonanywhere.com/positions/json/");
				Log.i("mapou", "Location posted to WS");
				
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

}
