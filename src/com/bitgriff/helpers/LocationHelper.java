package com.bitgriff.helpers;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class LocationHelper implements LocationListener {
	final private static long INTERVAL = 30 * 100;
	final private static float MIN_DISTANCE = 1;
	
	private static LocationHelper instance;

	private Location location;

	public void startGPS(Context context) {
		LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL, MIN_DISTANCE, this);
	}


	public void stopGPS(Context context) {
		LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		locationManager.removeUpdates(this);
	}

	public Location getLocation() {
		return location;
	}
	
	@Override
	public void onLocationChanged(Location location) {
		this.location = location;
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}
	
	public static LocationHelper getInstance() {
		if (instance == null)
			instance = new LocationHelper();
		return instance;
	}
}
