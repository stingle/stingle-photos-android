package com.fenritz.safecamera.util;

import java.io.File;

import android.os.Environment;

public class Helpers {
	public static String getMainDir(){
		File sdcard = Environment.getExternalStorageDirectory();
		return sdcard.toString() + "/SafeCamera/";
	}
}
