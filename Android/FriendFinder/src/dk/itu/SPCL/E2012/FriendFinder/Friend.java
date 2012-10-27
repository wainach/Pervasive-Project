package dk.itu.SPCL.E2012.FriendFinder;

public class Friend {

	private String id;
	private String sound;
	private float lat = 0;
	private float lon = 0;
	private float alt = 0;
	
	public Friend(String id, String sound) {
		this.id = id;
		this.sound = sound;
	}

	public float getLat() {
		return lat;
	}

	public void setLat(float lat) {
		this.lat = lat;
	}
	
	public float getLon() {
		return lon;
	}

	public void setLon(float lon) {
		this.lon = lon;
	}
	
	public float getAlt() {
		return alt;
	}

	public void setAlt(float alt) {
		this.alt = alt;
	}

	public String getId() {
		return id;
	}

	public String getSound() {
		return sound;
	}
	
}
