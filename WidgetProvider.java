package com.freddykilo.smartdoor;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

public class WidgetProvider extends AppWidgetProvider {
	
	private static final String AUTO = "com.freddykilo.smartdoor.AUTO_FUNCTION)";
	private static final String MANUAL = "com.freddykilo.smartdoor.MANUAL_FUNCTION";
	private static final String TAG = "test";
	public static boolean manualButtonPressed = false;
	BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	
	@Override
	public void onEnabled(Context context) {
		Log.d(TAG, "WidgetProvider.onEnabled()");
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBluetoothDialog = new Intent(context, BluetoothRequestEnableDialog.class);
			enableBluetoothDialog.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(enableBluetoothDialog);
		}
		prepareWidget(context);
		super.onEnabled(context);
	}
	
	@Override
	public void onDisabled(Context context) {
		Log.d(TAG, "WidgetProvider.onDisabled()");
		Intent opener = new Intent(context, AutoButton.class);
		context.stopService(opener);
		Intent opener2 = new Intent(context, ManualButton.class);
		context.stopService(opener2);
	}
	
	@Override
	public void onReceive(final Context context, Intent intent) {
		Log.d(TAG, "WidgetProvider.onReceive()");
		super.onReceive(context, intent);
		if (intent.getAction().equals(AUTO)) {
			Intent opener = new Intent(context, AutoButton.class);
			context.startService(opener);
		} else if (intent.getAction().equals(MANUAL)) {
			manualButtonPressed = true;
			Intent opener = new Intent(context, ManualButton.class);
			context.startService(opener);
		}
	}
	
	/**
	 * This gets called for each widget added to either the home screen or lock screen and
	 * sets the layout accordingly.
	 * @param context
	 */
	private void prepareWidget(Context context) {
		Log.d(TAG, "WidgetProvider.prepareWidget()");
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		ComponentName thisWidget = new ComponentName(context, WidgetProvider.class);
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
			appWidgetManager.updateAppWidget(widgetId, setIntentToRemoteView(context, baseLayout));
		}
	}
	
	/**
	 * Set the Intent (functionality) for each button, auto and manual. Defines which service
	 * to run.
	 * @param context
	 * @param layout
	 * @return RemoteView with attached OnClickListener (OnClickPendingIntent) used to pass into
	 * AppWidgetManager.updateAppWidget(int appWidgetId, RemoteViews views)
	 */
	public RemoteViews setIntentToRemoteView(Context context, int layout) {
		Log.d(TAG, "WidgetProvider.setIntentToRemoteView()");
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), layout);
		Intent intent1 = new Intent(context, WidgetProvider.class);
		intent1.setAction(AUTO);
		PendingIntent pendingIntent1 = PendingIntent.getBroadcast(context, 0, intent1, 0);
		remoteViews.setOnClickPendingIntent(R.id.button, pendingIntent1);
		Intent intent2 = new Intent(context, WidgetProvider.class); 
		intent2.setAction(MANUAL);
		PendingIntent pendingIntent2 = PendingIntent.getBroadcast(context, 0, intent2, 0);
		remoteViews.setOnClickPendingIntent(R.id.button_2, pendingIntent2);
		return remoteViews;
	}
}
