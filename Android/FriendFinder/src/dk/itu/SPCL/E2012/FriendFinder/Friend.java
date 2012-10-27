package dk.itu.SPCL.E2012.FriendFinder;

import android.location.Location;

public class Friend {

	private String id;
	private String sound;
	private Location location;
	
	public Friend(String id, String sound) {
		this.id = id;
		this.sound = sound;
		location = new Location(id + "_location");
		location.setLatitude(0);
		location.setLongitude(0);
		location.setAltitude(0);
	}

	public float getLat() {
		return (float) location.getLatitude();
	}

	public void setLat(float lat) {
		this.location.setLatitude((float) lat);
	}
	
	public float getLon() {
		return (float) location.getLongitude();
	}

	public void setLon(float lon) {
		this.location.setLongitude((float) lon);
	}
	
	public float getAlt() {
		return (float) location.getAltitude();
	}

	public void setAlt(float alt) {
		this.location.setAltitude((float) alt);
	}

	public String getId() {
		return this.id;
	}
	
	public Location getLocation() {
		return this.location;
	}

	public String getSound() {
		return this.sound;
	}
	
}
