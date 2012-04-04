package com.fenritz.safecamera.util;

import android.graphics.Bitmap;
import android.support.v4.util.LruCache;


public class MemoryCache {
	private static int CACHE_SIZE = 4*1024*1024;
    private LruCache<String, Bitmap> cache;
    
    public MemoryCache(){
    	cache = new LruCache<String, Bitmap>(CACHE_SIZE) {
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
    
    public void put(String id, Bitmap bitmap){
        synchronized(cache) {
		    cache.put(id, bitmap);
		}
    }

    public void clear() {
        cache.evictAll();
    }
}

/*public class MemoryCache {
	private final HashMap<String, SoftReference<Bitmap>> cache=new HashMap<String, SoftReference<Bitmap>>();
	
	public Bitmap get(String id){
		if(!cache.containsKey(id)){
			return null;
		}
		SoftReference<Bitmap> ref=cache.get(id);
		return ref.get();
	}
	
	public void put(String id, Bitmap bitmap){
		cache.put(id, new SoftReference<Bitmap>(bitmap));
	}
	
	public void clear() {
		cache.clear();
	}
}*/