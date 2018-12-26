package com.fenritz.safecam.util;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import com.fenritz.safecam.SafeCameraApplication;


public class MemoryCache {
    private LruCache<String, Bitmap> cache;
    
    public MemoryCache(){
    	int memClass = ( ( ActivityManager )SafeCameraApplication.getAppContext().getSystemService( Context.ACTIVITY_SERVICE ) ).getMemoryClass();
    	int cacheSize = 1024 * 1024 * memClass / 8;
    	
    	cache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap value) {
				return value.getRowBytes() * value.getHeight();
		   }
		};
    }
    
    public Bitmap get(String id){
    	Bitmap bitmap;
		synchronized(cache) {
			bitmap = cache.get(id);
		}
    	
        return bitmap;
    }
    
    @SuppressLint("NewApi")
	public void put(String id, Bitmap bitmap){
        synchronized(cache) {
        	if(bitmap != null){
        		cache.put(id, bitmap);
        	}
		}
    }
    
    @SuppressLint("NewApi")
	public void remove(String id){
        synchronized(cache) {
        	cache.remove(id);
		}
    }

    public void clear() {
        cache.evictAll();
    }
}