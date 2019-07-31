package com.fenritz.safecam;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Auth.LoginManager;

import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;

public class LoginActivity extends Activity {



	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		setContentView(R.layout.login);

		((Button) findViewById(R.id.login)).setOnClickListener(login());
		((TextView) findViewById(R.id.forgot_password)).setOnClickListener(forgotPassword());
		((TextView) findViewById(R.id.register)).setOnClickListener(gotoSignUp());
		((EditText) findViewById(R.id.password)).setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_GO) {
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
					doLogin();
					return true;
				}
				return false;
			}
		});

		displayVersionName();
	}

	private OnClickListener login() {
		return new OnClickListener() {
			public void onClick(View v) {
				doLogin();
			}
		};
	}

	private OnClickListener gotoSignUp() {
		return new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(LoginActivity.this, SetUpActivity.class);
				startActivity(intent);
				finish();
			}
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		LoginManager.disableLockTimer(this);
	}
	
	private void doLogin() {
		String email = ((EditText) findViewById(R.id.email)).getText().toString();
		String password = ((EditText) findViewById(R.id.password)).getText().toString();

		if(!Helpers.isValidEmail(email)){
			Helpers.showAlertDialog(this, getString(R.string.invalid_email));
			return;
		}

		if(password.length() < Integer.valueOf(getString(R.string.min_pass_length))){
			Helpers.showAlertDialog(this, String.format(getString(R.string.password_short), getString(R.string.min_pass_length)));
			return;
		}

		(new LoginAsyncTask(this, email, password)).execute();

	}

	public static class LoginAsyncTask extends AsyncTask<Void, Void, Boolean> {

		protected Activity context;
		protected String email;
		protected String password;
		protected ProgressDialog progressDialog;
		protected StingleResponse response;


		public LoginAsyncTask(Activity context, String email, String password){
			this.context = context;
			this.email = email;
			this.password = password;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(context);
			progressDialog.setMessage(context.getString(R.string.signing_in));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.show();
		}

		@Override
		protected Boolean doInBackground(Void... params) {

			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("email", email);

			JSONObject resultJson = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.pre_login_path), postParams);
			response = new StingleResponse(this.context, resultJson, false);


			if (response.isStatusOk()) {
				String salt = response.get("salt");
				if (salt != null) {

					final String loginHash = SafeCameraApplication.getCrypto().getPasswordHashForStorage(password, salt);
					Log.d("loginhash", loginHash);
					HashMap<String, String> postParams2 = new HashMap<String, String>();

					postParams2.put("email", email);
					postParams2.put("password", loginHash);

					JSONObject resultJson2 = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.login_path), postParams2);
					response = new StingleResponse(this.context, resultJson2, false);


					if (response.isStatusOk()) {
						String token = response.get("token");
						String keyBundle = response.get("keyBundle");
						if (token != null && keyBundle != null) {
							try {
								boolean importResult = KeyManagement.importKeyBundle(context, keyBundle, password);

								if (!importResult) {
									return false;
								}

								KeyManagement.setApiToken(context, token);
								Helpers.storePreference(context, SafeCameraApplication.USER_EMAIL, email);

								((SafeCameraApplication) context.getApplication()).setKey(SafeCameraApplication.getCrypto().getPrivateKey(password));

								return true;
							} catch (CryptoException e) {
								e.printStackTrace();
								return false;
							}
						}
					}
				}
			}


			return false;
		}



		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			if(result) {
				Intent intent = new Intent();
				intent.setClass(context, GalleryActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(intent);
				context.finish();
			}
			else{
				if(response.areThereErrorInfos()) {
					response.showErrorsInfos();
				}
				else {
					Helpers.showAlertDialog(context, context.getString(R.string.fail_login));
				}
			}

		}
	}

    protected OnClickListener forgotPassword(){
        return new OnClickListener() {
            public void onClick(View v) {
                /*AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
                builder.setTitle(getString(R.string.forgot_password));
                builder.setMessage(getString(R.string.reset_password));
                builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences preferences = getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, MODE_PRIVATE);
                        preferences.edit().remove(SafeCameraApplication.PASSWORD).commit();
						SafeCameraApplication.setKey(null);

                        Intent intent = new Intent();
                        intent.setClass(LoginActivity.this, SetUpActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
                builder.setNegativeButton(getString(R.string.no), null);
                AlertDialog dialog = builder.create();
                dialog.show();*/
            }
        };
    }


	private void displayVersionName() {
		String versionName = "";
		PackageInfo packageInfo;
		try {
			packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			versionName = "v " + packageInfo.versionName;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		((TextView) findViewById(R.id.versionText)).setText(versionName);
	}
}