package com.fenritz.safecam.Util;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;

import com.fenritz.safecam.SafeCameraApplication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;


public class MemoryCache {
    private LruCache<String, Object> cache;
    
    public MemoryCache(){
    	int memClass = ( ( ActivityManager )SafeCameraApplication.getAppContext().getSystemService( Context.ACTIVITY_SERVICE ) ).getMemoryClass();
    	int cacheSize = 1024 * 1024 * memClass / 7;
    	
    	cache = new LruCache<String, Object>(cacheSize) {
			@Override
			protected int sizeOf(String key, Object value) {
				if(value instanceof Bitmap){
					return ((Bitmap)value).getRowBytes() * ((Bitmap)value).getHeight();
				}
				else if(value instanceof MemcacheSizeable){
					return ((MemcacheSizeable) value).getSize();
				}
				else{
					return 1;
				}
		   }
		};
    }
    
    public Object get(String id){
    	Object obj;
		synchronized(cache) {
			obj = cache.get(id);
		}
    	
        return obj;
    }
    
    @SuppressLint("NewApi")
	public void put(String id, Object obj){
        synchronized(cache) {
        	if(obj != null){
        		cache.put(id, obj);
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