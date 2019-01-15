package com.fenritz.safecam;

import android.app.Application;
import android.content.Context;

import com.fenritz.safecam.util.Crypto;
import com.fenritz.safecam.util.MemoryCache;

public class SafeCameraApplication extends Application{

	public final static String TAG = "SCCam";

	private static byte[] key = null;
	
	private static Context context;
	private static MemoryCache cache;
	private static Crypto crypto;

    @Override
	public void onCreate(){
        super.onCreate();
        SafeCameraApplication.context = getApplicationContext();
        SafeCameraApplication.cache = new MemoryCache();
        SafeCameraApplication.crypto = new Crypto(this);
    }

    public static Crypto getCrypto() {
        return SafeCameraApplication.crypto;
    }

    public static Context getAppContext() {
        return SafeCameraApplication.context;
    }

    public static MemoryCache getCache() {
        return SafeCameraApplication.cache;
    }
	
	public static byte[] getKey(){
        return key;
	}
	
	public static void setKey(byte[] pKey){
		key = pKey;
	}
}
