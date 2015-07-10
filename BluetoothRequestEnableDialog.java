package com.freddykilo.smartdoor;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;

public class BluetoothRequestEnableDialog extends Activity{

	private static final int REQUEST_ENABLE_BT = 5001;
	
	/**
	 * Open a dialog to request user to enable Bluetooth.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		super.setTheme(android.R.style.Theme_DeviceDefault_Dialog);
		Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		super.onCreate(savedInstanceState);
		finish();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		finish();
	}
}
