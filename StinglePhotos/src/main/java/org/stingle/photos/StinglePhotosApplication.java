package org.stingle.photos;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import org.stingle.photos.AsyncTasks.Sync.DownloadThumbsAsyncTask;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Sync.SyncAsyncTask;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Util.MemoryCache;

import java.util.HashMap;

public class StinglePhotosApplication extends Application{

	public static final int API_VERSION = 2;

	public static final int REQUEST_SD_CARD_PERMISSION = 1;
	public static final int REQUEST_CAMERA_PERMISSION = 2;
	public static final int REQUEST_AUDIO_PERMISSION = 3;
	public static final int REQUEST_NOTIF_PERMISSION = 4;
	public static final int REQUEST_MEDIA_PERMISSION = 5;

	public final static String TAG = "StinglePhotos";
	public final static String FILE_EXTENSION = ".sp";
	public final static int FILENAME_LENGTH = 32;

	private static byte[] key = null;
	
	private static Context context;
	private static MemoryCache cache;
	private static Crypto crypto;
	private static HashMap<String, String> tempStore = new HashMap<>();

    public static final String DEFAULT_PREFS = "default_prefs";
    public static final String STICKY_PREFS = "sticky_prefs";
    public static final String API_TOKEN = "api_token";
    public static final String SERVER_URL = "server_url";
    public static final String PASSWORD_BIOMETRICS = "password_bio";
    public static final String PASSWORD_BIOMETRICS_IV = "password_bio_iv";
    public static final String BLOCK_SCREENSHOTS = "block_screenshots";

    public static final String USER_ID = "user_id";
    public static final String USER_EMAIL = "user_email";
    public static final String USER_HOME_FOLDER = "user_home_folder";
    public static final String SERVER_ADDONS = "server_addons";
    public static final String IS_KEY_BACKED_UP = "is_key_backed_up";
    public static final String PREF_APP_START_COUNT = "app_start_count";

    public static int syncStatus = SyncManager.STATUS_IDLE;
    public static Bundle syncStatusParams = null;
    public static boolean syncRestartAfterFinish = false;
    public static int syncRestartAfterFinishMode = SyncAsyncTask.MODE_FULL;
	public DownloadThumbsAsyncTask downloadThumbsAsyncTask;

    @Override
	public void onCreate(){
		super.onCreate();

		/*if (BuildConfig.DEBUG) {
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
					.detectAll()
					.penaltyLog()
					.build());
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
					.detectAll()
					.penaltyLog()
					.build());
		}*/

        StinglePhotosApplication.context = getApplicationContext();
        StinglePhotosApplication.cache = new MemoryCache();
        StinglePhotosApplication.crypto = new Crypto(getApplicationContext());

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		Helpers.applyTheme(prefs.getString("theme", "auto"));
		Helpers.setLocale(getApplicationContext());

		Helpers.storePreference(this, PREF_APP_START_COUNT, Helpers.getPreference(this, PREF_APP_START_COUNT, 0)+1);
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
    	if(key == null){
    		byte[] decryptedKey = getCrypto().getDecryptedKey();
    		if(decryptedKey != null && decryptedKey.length > 0){
    			key = decryptedKey;
			}
		}
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

	public static String getApiUrl(){
		String currentServerURL = context.getSharedPreferences(StinglePhotosApplication.STICKY_PREFS, Context.MODE_PRIVATE).getString(StinglePhotosApplication.SERVER_URL, context.getString(R.string.api_server_url));;
		return currentServerURL + "v" + StinglePhotosApplication.API_VERSION + "/";
	}
}
