package com.freddykilo.smartdoor;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class ManualButton extends Service {
	
	private static final String TAG = "test";
	public static final String ACTIVATE = "open";
	private Context thisContext = this;
	private Handler toastThreadHandler;
	
	/**
	 * Called first time pressing manual button after adding widget to home screen
	 * or lock screen. This sets up the bonded (paired) device and creates a socket
	 * for communication. This will only be called once while service is active.
	 */
	@Override
	public void onCreate() {
		BluetoothHelper.setup();
		super.onCreate();
	}
	
	/**
	 * Called with every manual button press, as well as after calling onCreate().
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		processLogic();
		return START_NOT_STICKY;
	}
	
	/**
	 * Handles closing the socket and flushes/closes output stream if
	 * there is any data left in the buffer. This gets called after
	 * removing widget from the home screen.
	 */
	@Override
	public void onDestroy() {
		BluetoothHelper.teardown();
		super.onDestroy();
	}

	/**
	 * Handles button animation type. Makes a call to connectToBTModule() and/or activateGarageDoor()
	 * on a needed basis. If BlueToothSocket is null, createSocket() is called.
	 */
	public void processLogic() {
		if (!BluetoothHelper.mBlueToothAdapter.isEnabled() || BluetoothHelper.mBlueToothAdapter == null) {
			noAdapterAmination(6);
			Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
		} else if (BluetoothHelper.mBluetoothSocket != null) {
			if (!BluetoothHelper.mBluetoothSocket.isConnected()) {
				Toast.makeText(this, "Attempting connection...", Toast.LENGTH_SHORT).show();
				activationAmination(10);
				handleToastMsgInRogueThread();
				BluetoothHelper.mBlueToothAdapter.cancelDiscovery();
				connectToBTModule();
			} else {
				activationAmination(4);
				activateGarageDoor(ACTIVATE);
			}
		} else {
			if (BluetoothHelper.createSocketOK()) {
				processLogic();
			}
		}
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
						activateGarageDoor(ACTIVATE);
						checking = false;
					}
				}
			}
		}).start();
	}

	/**
	 * Creates OutputStream and writes each byte of message to the message buffer.
	 * Will create a new socket then make a call to onStartCommandLogic()
	 * if OutputStream fails to write due to an obsolete socket.
	 * @param message The String passed to the device to activate relay
	 */
	private void activateGarageDoor(String message){
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
			if (BluetoothHelper.createSocketOK()) {
				processLogic();
			}
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
	 * Sets a UI image to a drawable object on the fly. i.e. R.drawable.button_pressed
	 * @param drawable R.drawable.<name of image>
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
	 * Return a RemoteViews object based on the layout that is passed
	 * @param drawable R.drawable.foo_image
	 * @param layout R.layout.foo_layout
	 * @return RemoteViews object
	 */
	private RemoteViews getRemoteView(int drawable, int layout) {
		RemoteViews remoteViews = new RemoteViews(this.getPackageName(), layout);
		remoteViews.setImageViewResource(R.id.button_image, drawable);
		return remoteViews;
	}

	/**
	 * Unused implemented method.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
