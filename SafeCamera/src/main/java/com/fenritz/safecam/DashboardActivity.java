package com.fenritz.safecam;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.core.content.FileProvider;

import com.fenritz.safecam.net.HttpsClient;
import com.fenritz.safecam.util.CryptoException;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.LoginManager;

import org.apache.sanselan.util.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class DashboardActivity extends Activity {

	private BroadcastReceiver receiver;
	public static final int REQUEST_SD_CARD_PERMISSION = 1;
	public static final int REQUEST_CAMERA_PERMISSION = 2;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//requestWindowFeature(Window.FEATURE_NO_TITLE);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		setContentView(R.layout.dashboard);

		initViews();

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);

		boolean showPopup = getIntent().getBooleanExtra("showPopup", false);
		if (showPopup) {
			showPopup();
		}

		if (Helpers.requestSDCardPermission(this)) {
			filesystemInit();
		}

		/*HashMap<String, String> params = new HashMap<String, String>();
		params.put("qaq", "cer");
		HttpsClient.post("https://api.stingle.org/", params, new HttpsClient.OnNetworkFinish() {
			@Override
			public void onFinish(JSONObject result) {
				try {
					Log.e("res", result.getString("result"));
					Log.e("qaq", result.toString());
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		});*/

	}

	public void filesystemInit() {
		Helpers.createFolders(this);

		Helpers.deleteTmpDir(this);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case REQUEST_SD_CARD_PERMISSION: {
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					filesystemInit();

				} else {
					finish();
				}
				return;
			}
		}
	}

	private void initViews() {
		findViewById(R.id.gotoCamera).setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(DashboardActivity.this, Camera2Activity.class);
				startActivity(intent);
			}
		});

		findViewById(R.id.gotoGallery).setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(DashboardActivity.this, GalleryActivity.class);
				startActivity(intent);
			}
		});
	}


	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
			setContentView(R.layout.dashboard);
		} else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			setContentView(R.layout.dashboard_land);
		}
		initViews();
	}

	private void showPopup() {
		final SharedPreferences preferences = getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, MODE_PRIVATE);

		AlertDialog.Builder builder = new AlertDialog.Builder(DashboardActivity.this);
		builder.setTitle(getString(R.string.popup_title));
		builder.setMessage(getString(R.string.popup_text));
		builder.setPositiveButton(getString(R.string.review), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse("market://details?id=" + getString(R.string.main_package_name)));
				startActivity(intent);
				preferences.edit().putBoolean(LoginManager.DONT_SHOW_POPUP, true).commit();
			}
		});
		builder.setNegativeButton(getString(R.string.later), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				int latersCount = preferences.getInt(LoginManager.POPUP_LATERS_COUNT, 0);
				latersCount++;

				if (latersCount >= Integer.valueOf(getString(R.string.popup_laters_limit))) {
					preferences.edit().putBoolean(LoginManager.DONT_SHOW_POPUP, true).commit();
				}
				preferences.edit().putInt(LoginManager.POPUP_LATERS_COUNT, latersCount).commit();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.disableLockTimer(this);

		Helpers.checkIsMainFolderWritable(this);
	}

	@Override
	protected void onPause() {
		super.onPause();

		LoginManager.setLockedTime(this);
	}

	@Override
	protected void onDestroy() {
		if (receiver != null) {
			unregisterReceiver(receiver);
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.dashborad_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != Activity.RESULT_OK || data == null) {
			return;
		}
		if(requestCode == 10){
			Uri outputUri = data.getData();
			Log.e("uri", outputUri.getPath());
			ContentResolver resolver = getContentResolver();
			try {

				OutputStream os = getContentResolver().openOutputStream(outputUri);
				if( os != null ) {
					os.write(SafeCameraApplication.getCrypto().exportKeyBundle());
					os.close();
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else if(requestCode == 20){
			Uri inputUri = data.getData();
			Log.e("uri", inputUri.getPath());
			ContentResolver resolver = getContentResolver();
			try {

				InputStream is = getContentResolver().openInputStream(inputUri);
				if( is != null ) {
					SafeCameraApplication.getCrypto().importKeyBundle(IOUtils.getInputStreamBytes(is));
					is.close();
					SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
					defaultSharedPrefs.edit().putBoolean("fingerprint", false).commit();
					LoginManager.logout(this);
					Helpers.showInfoDialog(this, "Import successful");
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (CryptoException e) {
				Helpers.showAlertDialog(this, e.getMessage());
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent;
		switch (item.getItemId()) {
			case R.id.settings:
				intent = new Intent();
				intent.setClass(DashboardActivity.this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case R.id.change_password:
				intent = new Intent();
				intent.setClass(DashboardActivity.this, ChangePasswordActivity.class);
				startActivity(intent);
				return true;
			case R.id.setup:
				intent = new Intent();
				intent.setClass(DashboardActivity.this, SetUpActivity.class);
				startActivity(intent);
				return true;
			case R.id.test:
				intent = new Intent();
				intent.setClass(DashboardActivity.this, TestActivity.class);
				startActivity(intent);
				return true;
			case R.id.export_keys:
				Intent chooserIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
				chooserIntent.addCategory(Intent.CATEGORY_OPENABLE);
				chooserIntent.setType("*/*");
				// Note: This is not documented, but works: Show the Internal Storage menu item in the drawer!
				chooserIntent.putExtra("android.content.extra.SHOW_ADVANCED", true);
				chooserIntent.putExtra(Intent.EXTRA_TITLE, "stingle_keys.skey");
				startActivityForResult(chooserIntent, 10);
				return true;
			case R.id.share_keys:
				try {
					String filePath = Helpers.getHomeDir(this) + "/.tmp/stingle_keys.skey";
					File destinationFile = new File(filePath);

					FileOutputStream outputStream = new FileOutputStream(destinationFile);
					outputStream.write(SafeCameraApplication.getCrypto().exportKeyBundle());
					outputStream.close();

					Intent share = new Intent(Intent.ACTION_SEND);
					share.setType("*/*");
					share.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getApplicationContext(), "com.fenritz.safecam.fileprovider", destinationFile));
					startActivity(Intent.createChooser(share, getString(R.string.export_keys)));

				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;
			case R.id.import_keys:
				Intent openerIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
				openerIntent.addCategory(Intent.CATEGORY_OPENABLE);
				openerIntent.setType("*/*");
				// Note: This is not documented, but works: Show the Internal Storage menu item in the drawer!
				openerIntent.putExtra("android.content.extra.SHOW_ADVANCED", true);
				startActivityForResult(openerIntent, 20);
				return true;
			case R.id.read_security:
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.security_page_link)));
				startActivity(browserIntent);
				return true;
			case R.id.logout:
				LoginManager.logout(DashboardActivity.this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}



}
