package com.fenritz.safecam;

import android.app.Application;
import android.content.Context;

import com.fenritz.safecam.util.MemoryCache;

public class SafeCameraApplication extends Application{

	private String key;
	private long lockedTime = 0;
	
	private static Context context;
	private static MemoryCache cache;

    @Override
	public void onCreate(){
        super.onCreate();
        SafeCameraApplication.context = getApplicationContext();
        SafeCameraApplication.cache = new MemoryCache();
    }

    public static Context getAppContext() {
        return SafeCameraApplication.context;
    }
    
    public static MemoryCache getCache() {
        return SafeCameraApplication.cache;
    }
	
	public String getKey(){
		return key;
	}
	
	public void setKey(String pKey){
		key = pKey;
	}
	
	public long getLockedTime(){
		return lockedTime;
	}
	
	public void setLockedTime(long time){
		lockedTime = time;
	}
	
}
