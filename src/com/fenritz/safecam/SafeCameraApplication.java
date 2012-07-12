package com.fenritz.safecam;

import android.app.Application;

public class SafeCameraApplication extends Application{

	private String key;
	private long lockedTime = 0;
	
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
