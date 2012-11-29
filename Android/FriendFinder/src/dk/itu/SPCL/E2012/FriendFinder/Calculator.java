package dk.itu.SPCL.E2012.FriendFinder;

import android.location.Location;
import android.util.Log;

public class Calculator {

	public static void calculateBearing(String[] args) {
		Location l1 = new Location("l1");
		Location l2 = new Location("l2");
		
		l1.setLatitude(55.659698162244496);
		l1.setLongitude(12.59107232093811);
		
		l2.setLatitude(55.6703970095186);
		l2.setLongitude(12.578551769256592);
			
		Log.i("CALCULATIONS", Float.toString(l1.bearingTo(l2)));
	}
}
