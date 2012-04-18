package com.fenritz.safecamera;

import javax.crypto.SecretKey;

import android.app.Application;

public class SafeCameraApplication extends Application{

	private SecretKey key;
	private long lockedTime = 0;
	
	public SecretKey getKey(){
		return key;
	}
	
	public void setKey(SecretKey pKey){
		key = pKey;
	}
	
	public long getLockedTime(){
		return lockedTime;
	}
	
	public void setLockedTime(long time){
		lockedTime = time;
	}
	
}
