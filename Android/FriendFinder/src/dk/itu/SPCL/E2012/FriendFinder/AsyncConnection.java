package dk.itu.SPCL.E2012.FriendFinder;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;

public class AsyncConnection extends Observable implements Runnable {

	private String url;
	private String UUID;
	private final static String	TAG	= "AsyncConnection";

	public AsyncConnection(String url, String UUID, Observer obs) {
		this.url = url;
		this.UUID = UUID;
		this.addObserver(obs);
	}

	@Override
	public void run() {

		while (true) {

			ArrayList<String[]> reply = RestClient.connect(url); //url + "?uuid=" + UUID);
			if (reply != null) {
				setChanged();
				notifyObservers(reply);
			}
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
