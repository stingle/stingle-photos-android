package com.fenritz.safecam;

import android.app.Application;
import android.content.Context;

public class SafeCameraApplication extends Application{

	private String key;
	private long lockedTime = 0;
	
	
	private static Context context;

    @Override
	public void onCreate(){
        super.onCreate();
        SafeCameraApplication.context = getApplicationContext();
    }

    public static Context getAppContext() {
        return SafeCameraApplication.context;
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
