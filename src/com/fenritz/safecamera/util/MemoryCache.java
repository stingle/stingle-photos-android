package com.fenritz.safecamera.util;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Map;

import android.graphics.Bitmap;


public class MemoryCache {
    //private final HashMap<String, SoftReference<Bitmap>> cache=new HashMap<String, SoftReference<Bitmap>>();
    static int CACHE_SIZE = 2 * 1024 * 1024; 
    static Map cache = Collections.synchronizedMap(new LruCacheLinkedHashMap(CACHE_SIZE));
    
    public Bitmap get(String id){
        if(!cache.containsKey(id)){
            return null;
        }
        SoftReference softReferenceDrawable = (SoftReference)cache.get(id);
        //SoftReference<Bitmap> ref=cache.get(id);
        return (Bitmap)softReferenceDrawable.get();
    }
    
    public void put(String id, Bitmap bitmap){
    	SoftReference softReferenceDrawable = new SoftReference(bitmap);
    	cache.put(id, softReferenceDrawable); 
    }

    public void clear() {
        cache.clear();
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