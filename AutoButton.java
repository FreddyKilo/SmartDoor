package com.freddykilo.smartdoor;

import java.io.IOException;

import android.app.Activity;
import android.app.Dialog;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;

public class AutoButton extends Service implements
ConnectionCallbacks, OnConnectionFailedListener, LocationListener{
	
	private static final double HOME_LATITUDE = 33.368007;
	private static final double HOME_LONGITUDE = -111.719131;
	private static final int GPS_ERRORDIALOG_REQUEST = 9001;
	private static final int ONE_SECOND = 1000;
	private static final int ONE_MINUTE = 60000;
	private static final int SMALLEST_DISPLACEMENT = 0; // Meters to move before request
	private static final int DEFAULT_REQUEST_INTERVAL = ONE_SECOND * 5;
	private static final int DEFAULT_FASTEST_INTERVAL = ONE_SECOND * 2;
	private static final String TAG = "test";
	private float furthestMarker = (float) .05; // Default value after calling onCreate()
	private float closestMarker = 0;            // Default value after calling onCreate()
	private float milesToHome;                  // number of miles
	private double feetToHome;
	private Handler toastThreadHandler;
	public static boolean isLocationServicesUpdating = false;
	public static boolean autoEnabled = false;
	boolean atHome = false;
	boolean justStarted = true;
	int variableRequestInterval = DEFAULT_REQUEST_INTERVAL;
	
	Context thisContext = this;
	GoogleMap mMap;
	GoogleApiClient mGoogleApiClient;
	LocationRequest request = LocationRequest.create();
	
	/**
	 * Called the first time Auto button is pressed. Sets up all necessary components.
	 * i.e. LocationServices, Bluetooth
	 */
	@Override
	public void onCreate() {
		Log.d(TAG, "AutoButton.onCreate()");
		if (servicesOK()) {
			createGoogleApiClient().connect();
			BluetoothHelper.setup();
		}
		else{
			stopSelf();
		}
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "AutoButton.onDestroy()");
		cleanup();
		super.onDestroy();
	}
	
	/**
	 * Stops location requests, kills Bluetooth connection.
	 */
	private void cleanup() {
		Log.d(TAG, "AutoButton.cleanup()");
		stopLocationUpdate();
		mGoogleApiClient.disconnect();
		BluetoothHelper.teardown();
	}
	
	/**
	 * Handles the UI feedback for button click, toggles location updates on or off.
	 */
	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "AutoButton.onStartCommand()");
		if (justStarted) {
			justStarted = false;
			autoEnabled = true;
			setAutoButtonImage(R.drawable.smart_door_widget_auto_activated);
			showToast("AUTO ON", Toast.LENGTH_SHORT, R.dimen.toast_size);
		} else if (autoEnabled) {
			stopLocationUpdate();
			autoEnabled = false;
			setAutoButtonImage(R.drawable.smart_door_widget);
			showToast("AUTO OFF", Toast.LENGTH_SHORT, R.dimen.toast_size);
		} else {
			setRequestInterval(variableRequestInterval);
			autoEnabled = true;
			setAutoButtonImage(R.drawable.smart_door_widget_auto_activated);
			showToast("AUTO ON", Toast.LENGTH_SHORT, R.dimen.toast_size);
		}	
        return START_STICKY;
    }

	/**
	 * Checks if device is able to connect to Google Play Services, connection is required
	 * to be able to use LocationServices.
	 * @return true if able to connect, false if not able to connect.
	 */
	public boolean servicesOK() {
		int isAvailable = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if (isAvailable == ConnectionResult.SUCCESS) {
			return true;
		} else if (GooglePlayServicesUtil.isUserRecoverableError(isAvailable)) {
			Activity activity = new Activity();
			Dialog dialog = GooglePlayServicesUtil.getErrorDialog(isAvailable, activity, GPS_ERRORDIALOG_REQUEST);
			dialog.show();
		} else {
			Toast.makeText(this, "Can't connect to Google Play services", Toast.LENGTH_SHORT).show();
		}
		return false;
	}
	
	/**
	 * Builds the necessary GoogleApiClient.
	 * @return the necessary GoogleApiClient
	 */
	protected synchronized GoogleApiClient createGoogleApiClient() {
		mGoogleApiClient = new GoogleApiClient.Builder(this)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi(LocationServices.API)
        .build();
		return mGoogleApiClient;
	}
	
	/**
	 * Handles the logic for location updates and request intervals based on current location.
	 */
	@Override
	public void onLocationChanged(Location location) {
		String msg2 = "onLocationChanged() Interval: " + request.getInterval() + ", Fastest: " + request.getFastestInterval();
		Log.d(TAG, msg2);
		calculateDistanceToHome(location);
		if (milesToHome > .05) {
			atHome = false;
			if (milesToHome > furthestMarker || milesToHome < closestMarker) {
				furthestMarker = (float) (milesToHome * 1.2);
				closestMarker = (float) (milesToHome * .75);
				setRequestInterval(variableRequestInterval);
			}
		} else if (feetToHome < 250 && !atHome && variableRequestInterval != ONE_SECOND){
			if (!BluetoothHelper.mBlueToothAdapter.isEnabled()) {
				BluetoothHelper.mBlueToothAdapter.enable();
				Log.d(TAG, "feetToHome < 250 FEET: Enable Bluetooth");
			}
			variableRequestInterval = ONE_SECOND;
			setRequestInterval(variableRequestInterval);
		}
		if (feetToHome < 50 && !atHome) {
			atHome = true;
			autoEnabled = false;
			stopLocationUpdate();
			processLogic();
			showToast("AUTO OFF", Toast.LENGTH_SHORT, R.dimen.toast_size);
//			stopSelf();
			Log.d(TAG, "feetToHome < 50 FEET: Search for Garage Door Opener");
		}
	}
	
	/**
	 * Math helper to calculate distance to destination using latitude and longitude.
	 * @param location
	 */
	private void calculateDistanceToHome(Location location) {
		double currentLat = location.getLatitude();
		double currentLng = location.getLongitude();
		double latToGo = Math.abs(currentLat - HOME_LATITUDE);
		double lngToGo = Math.abs(currentLng - HOME_LONGITUDE);
		milesToHome = (float) (Math.sqrt((latToGo * latToGo) + (lngToGo * lngToGo)) / .0144927);
		feetToHome = milesToHome * 5280;
		if (variableRequestInterval != ONE_SECOND) {
			variableRequestInterval = (int) (milesToHome * ONE_MINUTE);
		}
		String msg = "Miles to go: " + String.format("%.2f", milesToHome) +
		"\nFeet to go: " + String.format("%.1f", feetToHome); 
		Log.d(TAG, msg);
	}
	
	/**
	 * Set the location request interval.
	 * @param time Time in milliseconds
	 */
	private void setRequestInterval(long time) {
		Log.d(TAG, "setRequestInterval(" + time + ")");
		stopLocationUpdate();
		request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
		.setInterval(time)
		.setFastestInterval(time)
		.setSmallestDisplacement(SMALLEST_DISPLACEMENT);
		LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, this);
		isLocationServicesUpdating = true;
	}
	
	/**
	 * Stop location updates.
	 */
	public void stopLocationUpdate() {
		Log.d(TAG, "AutoButton.stopLocationUpdate()");
		if (isLocationServicesUpdating) {
			LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
			isLocationServicesUpdating = false;
		}
	}

	/**
	 * This is called after a successful connection to GoogleApiClient.
	 */
	@Override
	public void onConnected(Bundle arg0) {
		request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
		.setInterval(DEFAULT_REQUEST_INTERVAL)
		.setFastestInterval(DEFAULT_FASTEST_INTERVAL)
		.setSmallestDisplacement(SMALLEST_DISPLACEMENT);
		LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, this);
		isLocationServicesUpdating = true;
		
		String msg = "onConnected() Interval: " + request.getInterval() + " Fastest: " + request.getFastestInterval();
		Log.d(TAG, msg);
	}
	
	/**
	 * Handles button animation type. Makes a call to connectToBTModule() and/or activateGarageDoor()
	 * on a needed basis. If BlueToothSocket is null, createSocketOK() is called.
	 */
	public void processLogic() {
		Log.d(TAG, "AutoButton.processLogic()");
		if (!BluetoothHelper.mBlueToothAdapter.isEnabled() || BluetoothHelper.mBlueToothAdapter == null) {
			noAdapterAmination(6);
			Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
		} else if (BluetoothHelper.mBluetoothSocket != null) {
			if (!BluetoothHelper.mBluetoothSocket.isConnected()) {
				Toast.makeText(this, "Attempting connection...", Toast.LENGTH_SHORT).show();
				activationAmination(8);
				handleToastMsgInRogueThread();
				BluetoothHelper.mBlueToothAdapter.cancelDiscovery();
				connectToBTModule();
			} else {
				activationAmination(4);
				activateGarageDoor(ManualButton.ACTIVATE);
			}
		} else {
			BluetoothHelper.createSocketOK();
			processLogic();
		}
	}
	
	/**
	 * Draws the widget to the drawable that is passed. This is used to animate the button
	 * presses to give user feedback.
	 * @param drawable i.e. R.drawable.button_activated
	 */
	private void setAutoButtonImage(int drawable) {
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
		ComponentName thisWidget = new ComponentName(this, WidgetProvider.class);
		int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
		for (int widgetId : allWidgetIds) {
			Bundle myOptions = appWidgetManager.getAppWidgetOptions(widgetId);
			int category = myOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1);
			boolean isKeyguard = category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;
			int baseLayout;
			if (isKeyguard) {
				baseLayout = R.layout.keyguard_widget_layout;
			} else {
				baseLayout = R.layout.widget_layout;
			}
			appWidgetManager.updateAppWidget(widgetId, getRemoteView(drawable, baseLayout));
		}
	}
	
	/**
	 * Does exactly what is says.
	 * @param drawable
	 * @param layout
	 * @return RemoteViews
	 */
	private RemoteViews getRemoteView(int drawable, int layout) {
		RemoteViews remoteViews = new RemoteViews(this.getPackageName(), layout);
		remoteViews.setImageViewResource(R.id.button_image, drawable);
		return remoteViews;
	}

	/**
	 * Creates Handler to execute Toast message when called from a background thread
	 */
	private void handleToastMsgInRogueThread() {
		toastThreadHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				String mString = (String) msg.obj;
				Toast.makeText(thisContext, mString, Toast.LENGTH_SHORT).show();
			}
		};
	}
	
	/**
	 * Connect to BluetoothSocket in separate thread to allow for simultaneous
	 * UI animation of button. Informs user of successful connection then makes
	 * a call to activateGarageDoor(). Returns from method if connection fails.
	 */
	public void connectToBTModule() {
		Log.d(TAG, "AutoButton.connectToBTModule()");
		new Thread(new Runnable() {
			@Override
			public void run() {
				Message msg = new Message();
				try {
					BluetoothHelper.mBluetoothSocket.connect();
					msg.obj = "Connected";
					toastThreadHandler.sendMessage(msg);
				} catch (IOException e) {
					msg.obj = "Could not connect to Smart Door";
					toastThreadHandler.sendMessage(msg);
					e.printStackTrace();
					return;
				}
				boolean checking = true;
				while (checking){
					if (BluetoothHelper.mBluetoothSocket.isConnected()) {
						activateGarageDoor(ManualButton.ACTIVATE);
						checking = false;
					}
				}
			}
		}).start();
	}

	/**
	 * Creates OutputStream and writes each byte to the message buffer.
	 * Will create a new socket then make a call to processLogic()
	 * if OutputStream fails to write due to an obsolete socket.
	 * @param message The String passed to the remote device to activate relay
	 */
	private void activateGarageDoor(String message){
		Log.d(TAG, "AutoButton.activateGarageDoor()");
		byte[] msgBuffer = message.getBytes();
		try {
			BluetoothHelper.mOutputStream = BluetoothHelper.mBluetoothSocket.getOutputStream();
			for (int i = 0; i < msgBuffer.length; i++) {
				BluetoothHelper.mOutputStream.write(msgBuffer[i]);
			}
			Log.d(TAG, "Sent data: '" + message + "' to receiver...");
		} catch (IOException e) {
			BluetoothHelper.mBluetoothSocket = null;
			try {
				BluetoothHelper.mOutputStream.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			BluetoothHelper.createSocketOK();
			processLogic();
			e.printStackTrace();
		}
	}
	
	/**
	 * Runs the button animation for connection error with 85 milliseconds between
	 * on and off.
	 * @param blinks Number of blinking animations
	 */
	private void noAdapterAmination(int blinks) {
		Handler handler = new Handler();
		int milliseconds = 40;
		for (int i = 0; i < blinks; i++) {
			handler.postDelayed(new Runnable() {
				public void run() {
					setAutoButtonImage(R.drawable.smart_door_widget_manual_activated);
				}
			}, milliseconds);
			milliseconds += 85;
			handler.postDelayed(new Runnable() {
				public void run() {
					setAutoButtonImage(R.drawable.smart_door_widget);
				}
			}, milliseconds);
			milliseconds += 85;
		}
		handler.postDelayed(new Runnable() {
			public void run() {
				if (AutoButton.autoEnabled) {
					setAutoButtonImage(R.drawable.smart_door_widget_auto_activated);
				} else {
					setAutoButtonImage(R.drawable.smart_door_widget);
				}
			}
		}, milliseconds);
	}
	
	/**
	 * Runs the button animation for a successful connection with 250 milliseconds
	 * between on and off.
	 * @param blinks Number of blinking animations
	 */
	private void activationAmination(int blinks) {
		Handler handler = new Handler();
		int milliseconds = 40;
		for (int i = 0; i < blinks; i++) {
			handler.postDelayed(new Runnable() {
				public void run() {
					setAutoButtonImage(R.drawable.smart_door_widget_manual_activated);
				}
			}, milliseconds);
			milliseconds += 250;
			handler.postDelayed(new Runnable() {
				public void run() {
					setAutoButtonImage(R.drawable.smart_door_widget);
				}
			}, milliseconds);
			milliseconds += 250;
		}
	}
	
	/**
	 * Show user a custom Toast message.
	 * @param message The message intended to show
	 * @param time Either Toast.LENTH_SHORT or Toast.LENTH_LONG
	 * @param size The size of the text i.e. R.dimen.custom_toast_size
	 */
	public void showToast(String message, int time, int size){
    	Toast toastMsg;
		toastMsg = Toast.makeText(this, message, time);
		LinearLayout toastLayout = (LinearLayout) toastMsg.getView();
		TextView toastText = (TextView) toastLayout.getChildAt(0);
		toastText.setTextSize(this.getResources().getDimension(size));
		toastMsg.show();
    }
	
	/**
	 * Unused implemented method.
	 */
	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
	}
	
	/**
	 * Unused implemented method.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * Unused implemented method.
	 */
	@Override
	public void onConnectionSuspended(int arg0) {
	}
}