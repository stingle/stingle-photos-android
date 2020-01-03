package org.stingle.photos;

import android.app.Application;
import android.content.Context;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Util.MemoryCache;

import java.util.HashMap;

public class StinglePhotosApplication extends Application{

	public static final int REQUEST_SD_CARD_PERMISSION = 1;
	public static final int REQUEST_CAMERA_PERMISSION = 2;
	public static final int REQUEST_AUDIO_PERMISSION = 3;

	public final static String TAG = "StinglePhotos";
	public final static String FILE_EXTENSION = ".sp";
	public final static int FILENAME_LENGTH = 32;

	private static byte[] key = null;
	
	private static Context context;
	private static MemoryCache cache;
	private static Crypto crypto;
	private static HashMap<String, String> tempStore = new HashMap<>();

    public static final String DEFAULT_PREFS = "default_prefs";
    public static final String API_TOKEN = "api_token";
    public static final String PASSWORD_BIOMETRICS = "password_bio";
    public static final String PASSWORD_BIOMETRICS_IV = "password_bio_iv";
    public static final String BLOCK_SCREENSHOTS = "block_screenshots";

    public static final String USER_ID = "user_id";
    public static final String USER_EMAIL = "user_email";
    public static final String USER_HOME_FOLDER = "user_home_folder";

    @Override
	public void onCreate(){
        super.onCreate();
        StinglePhotosApplication.context = getApplicationContext();
        StinglePhotosApplication.cache = new MemoryCache();
        StinglePhotosApplication.crypto = new Crypto(this);
    }

    public static Crypto getCrypto() {
        return StinglePhotosApplication.crypto;
    }

    public static Context getAppContext() {
        return StinglePhotosApplication.context;
    }

    public static MemoryCache getCache() {
        return StinglePhotosApplication.cache;
    }
	
	public static byte[] getKey(){
        return key;
	}
	
	public static void setKey(byte[] pKey){
		key = pKey;
	}

	public static void setTempStore(String key, String value){
    	tempStore.put(key,value);
	}
	public static String getTempStore(String key){
    	return tempStore.get(key);
	}
	public static void deleteTempStore(String key){
		tempStore.remove(key);
	}

}
