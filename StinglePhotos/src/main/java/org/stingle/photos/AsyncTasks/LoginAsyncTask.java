package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;
import org.stingle.photos.Auth.BiometricsManagerWrapper;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.util.HashMap;

public class LoginAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private AppCompatActivity activity;
	private String email;
	private String password;
	private ProgressDialog progressDialog;
	private StingleResponse response;
	private Boolean isKeyBackedUp = true;
	private String userId;
	private String homeFolder;
	private String token;
	private boolean privateKeyIsAlreadySaved = false;


	public LoginAsyncTask(AppCompatActivity context, String email, String password){
		this.activity = context;
		this.email = email;
		this.password = password;
	}

	public void setPrivateKeyIsAlreadySaved(boolean privateKeyIsAlreadySaved) {
		this.privateKeyIsAlreadySaved = privateKeyIsAlreadySaved;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(activity);
		progressDialog.setMessage(activity.getString(R.string.signing_in));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {

		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("email", email);

		JSONObject resultJson = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + activity.getString(R.string.pre_login_path), postParams);
		response = new StingleResponse(this.activity, resultJson, false);


		if (response.isStatusOk()) {
			String salt = response.get("salt");
			if (salt != null) {

				final String loginHash = StinglePhotosApplication.getCrypto().getPasswordHashForStorage(password, salt);
				Log.d("loginhash", loginHash);
				HashMap<String, String> postParams2 = new HashMap<String, String>();

				postParams2.put("email", email);
				postParams2.put("password", loginHash);

				JSONObject resultJson2 = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + activity.getString(R.string.login_path), postParams2);
				response = new StingleResponse(this.activity, resultJson2, false);


				if (response.isStatusOk()) {
					String keyBundle = response.get("keyBundle");
					String serverPublicKey = response.get("serverPublicKey");
					token = response.get("token");
					userId = response.get("userId");
					isKeyBackedUp = response.get("isKeyBackedUp").equals("1");
					homeFolder = response.get("homeFolder");
					if (token != null && keyBundle != null && homeFolder != null && userId != null && token.length() > 0 && keyBundle.length() > 0 && homeFolder.length() > 0 && userId.length() > 0) {
						try {
							if(privateKeyIsAlreadySaved) {
								StinglePhotosApplication.setKey(StinglePhotosApplication.getCrypto().getPrivateKey(password));
							}
							else {
								boolean importResult = KeyManagement.importKeyBundle(keyBundle, password);
								KeyManagement.importServerPublicKey(serverPublicKey);

								if (!importResult) {
									return false;
								}

								if(isKeyBackedUp) {
									StinglePhotosApplication.setKey(StinglePhotosApplication.getCrypto().getPrivateKey(password));
								}
							}


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

	private void gotoGallery(){
		Intent intent = new Intent();
		intent.setClass(activity, GalleryActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		activity.startActivity(intent);
		activity.finish();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(result) {
			LoginManager.disableLockTimer(activity);

			if(!isKeyBackedUp && !privateKeyIsAlreadySaved){
				MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
				builder.setView(R.layout.dialog_enter_phrase);
				builder.setCancelable(false);
				AlertDialog dialog = builder.create();
				dialog.show();

				Button okButton = dialog.findViewById(R.id.okButton);
				Button cancelButton = dialog.findViewById(R.id.cancelButton);

				okButton.setOnClickListener(v -> {
					String backupPhrase = ((EditText)dialog.findViewById(R.id.backup_phrase_dialog)).getText().toString();
					(new CheckRecoveryPhraseAsyncTask(activity, email, backupPhrase, new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							(new SetNewPasswordAsyncTask(activity, email, backupPhrase, password, true, new OnAsyncTaskFinish() {
								@Override
								public void onFinish() {
									super.onFinish();
									dialog.dismiss();
									loginSuccess();
								}
							})).setShowProgress(false).execute();
						}
					})).setShowProgress(false).execute();

				});

				cancelButton.setOnClickListener(v -> {
					if(dialog != null){
						dialog.dismiss();
						progressDialog.dismiss();
					}
				});
			}
			else{
				loginSuccess();
			}

		}
		else{
			progressDialog.dismiss();
			if(response.areThereErrorInfos()) {
				response.showErrorsInfos();
			}
			else {
				Helpers.showAlertDialog(activity, activity.getString(R.string.error), activity.getString(R.string.fail_login));
			}
		}
	}

	private void loginSuccess(){
		KeyManagement.setApiToken(activity, token);
		Helpers.storePreference(activity, StinglePhotosApplication.USER_ID, userId);
		Helpers.storePreference(activity, StinglePhotosApplication.USER_EMAIL, email);
		Helpers.storePreference(activity, StinglePhotosApplication.USER_HOME_FOLDER, homeFolder);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
		settings.edit().putBoolean(StinglePhotosApplication.IS_KEY_BACKED_UP, isKeyBackedUp).apply();

		progressDialog.dismiss();
		BiometricsManagerWrapper biometricsManagerWrapper = new BiometricsManagerWrapper(activity);
		if(biometricsManagerWrapper.isBiometricsAvailable()) {
			Helpers.showConfirmDialog(activity, activity.getString(R.string.biometric_auth), activity.getString(R.string.enable_biometrics), R.drawable.ic_fingerprint,
					(dialog, which) -> {
						biometricsManagerWrapper.setupBiometrics(null, password, new BiometricsManagerWrapper.BiometricsSetupCallback() {
							@Override
							public void onSuccess() {
								gotoGallery();
							}

							@Override
							public void onFailed() {
								gotoGallery();
							}
						});

					},
					(dialog, which) -> {
						gotoGallery();
					});
		}
		else{
			gotoGallery();
		}
	}
}
