package com.freddykilo.smartdoor;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

public class BluetoothHelper {
	
	public static final int SETUP_SUCCESSFULL = 1;
	public static final int SOCKET_SUCCESSFUL = 2;
	public static final int PAIR_DEVICE_REQUEST = 10;
	public static final int CONNECTION_FAILURE = 11;
	private static final String DEVICE_NAME = "Smart Door v1.0";
	public static BluetoothAdapter mBlueToothAdapter;
	public static BluetoothSocket mBluetoothSocket = null;
	public static BluetoothDevice mBluetoothDevice;
	public static OutputStream mOutputStream = null;
	
	/**
	 * Makes a call to createSocket() upon success of creating the BluetoothDevice.
	 * If getDeviceByName returns null, service will notify user to pair device.
	 */
	public static int setup() {
		mBlueToothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothDevice == null && mBlueToothAdapter.isEnabled()) {
			try {
				mBluetoothDevice = getDeviceByName(mBlueToothAdapter, DEVICE_NAME);
				if (mBluetoothDevice != null) {
					if (createSocketOK());
					return SETUP_SUCCESSFULL;
				} else {
					return PAIR_DEVICE_REQUEST;
					//Toast.makeText(this, "Please pair your device to\nSmart Door and try again", Toast.LENGTH_SHORT).show();
				}
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				//Toast.makeText(this, "Unable to make connection", Toast.LENGTH_LONG).show();
				return CONNECTION_FAILURE;
			}
		}
		return 0;
	}
	
	/**
	 * Gets hidden method createRfcommSocket() from BluetoothDevice then calls it to return
	 * the BluetoothSocket needed to connect to.
	 */
	public static boolean createSocketOK() {
		try {
			if (mBluetoothDevice != null) {
				Method createRfcommSocket = mBluetoothDevice.getClass().getMethod("createRfcommSocket", int.class);
				mBluetoothSocket = (BluetoothSocket) createRfcommSocket.invoke(mBluetoothDevice, 1);
				return true;
			} else {
				//Toast.makeText(mContext, "Please pair your device to\nSmart Door and try again", Toast.LENGTH_SHORT).show();
				teardown();
			}
		} catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * Return the BluetoothDevice object by friendly name
	 * @param adapter BluetoothAdapter.getDefaultAdapter()
	 * @param name Name of the remote device
	 * @return BluetoothDevice
	 */
	private static BluetoothDevice getDeviceByName(BluetoothAdapter adapter, String name) {
        for (BluetoothDevice device : getBondedDevices(adapter)) {
        	String thisDevice = device.getName();
            if (name.matches(thisDevice)) {
                return device;
            }
        }
        return null;
    }
	
	/**
	 * Return a Set of BluetoothDevice objects that are currently bonded (paired)
	 * @param adapter BluetoothAdapter.getDefaultAdapter()
	 * @return A Set of bonded BluetoothDevice objects
	 */
	private static Set<BluetoothDevice> getBondedDevices(BluetoothAdapter adapter) {
        Set<BluetoothDevice> results = adapter.getBondedDevices();
        if (results == null) {
            results = new HashSet<BluetoothDevice>();
        }
        return results;
    }
	
	/**
	 * Handles closing the socket and flushes/closes output stream if
	 * there is any data left in the buffer.
	 */
	public static void teardown() {
		try {
			if (mBluetoothSocket != null) {
				mBluetoothSocket.close();
			}
			if (mOutputStream != null) {
				mOutputStream.flush();
				mOutputStream.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
