package com.fenritz.safecam;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Auth.KeyManagement;

import java.util.HashMap;

public class SetUpActivity  extends Activity{

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		setContentView(R.layout.setup);

		/*File homeFolder = new File(Helpers.getHomeDir(this));
		
		if(homeFolder.exists()){
			File[] files = homeFolder.listFiles(new FileFilter() {
				
				public boolean accept(File file) {
					if(file.isFile() && file.getName().endsWith(getString(R.string.file_extension))) {
						return true;
					}
					return false;
				}
			});
			
			if(files != null && files.length > 0){
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.attention));
				builder.setMessage(getString(R.string.same_password_alert));
				builder.setNeutralButton(getString(R.string.understood), null);
				AlertDialog dialog = builder.create();
				dialog.show();
			}
		}*/
		
		((Button) findViewById(R.id.signup)).setOnClickListener(signup());



		/*LazySodiumAndroid ls = new LazySodiumAndroid(new SodiumAndroid());

		Box.Lazy box = (Box.Lazy) ls;*/
        /*
        try {
			// This is our keypair.
			KeyPair myKeyPair = box.cryptoBoxKeypair();
			KeyPair hisKeyPair = box.cryptoBoxKeypair();

			String myPrivate = myKeyPair.getSecretKey().getAsHexString();
			String myPublic = myKeyPair.getPublicKey().getAsHexString();

			String hisPrivate = hisKeyPair.getSecretKey().getAsHexString();
			String hisPublic = hisKeyPair.getPublicKey().getAsHexString();

			KeyPair encryptionKeyPair = new KeyPair(hisKeyPair.getPublicKey(), myKeyPair.getSecretKey());

			byte[] nonce = ls.randomBytesBuf(SecretBox.NONCEBYTES);

			String cipherText = box.cryptoBoxEasy(
					"Qaqem glxid",
					nonce,
					encryptionKeyPair
			);

            KeyPair decryptionKeyPair = new KeyPair(myKeyPair.getPublicKey(), hisKeyPair.getSecretKey());
            String plainText = box.cryptoBoxOpenEasy(
                    cipherText,
                    nonce,
                    decryptionKeyPair
            );
            Log.d("cipherText", cipherText);
            Log.d("plainText", plainText);
			Log.d("myPrivate", myPrivate);
			Log.d("myPublic", myPublic);
			Log.d("hisPrivate", hisPrivate);
			Log.d("hisPublic", hisPublic);
			Log.d("cypher", cipherText);

			} catch (SodiumException e) {
			e.printStackTrace();
		}
			*/
		/*AEAD.Lazy aead = (AEAD.Lazy) ls;

		//if(ls.cryptoAeadAES256GCMIsAvailable()) {
		byte[] nonce = ls.randomBytesBuf(SecretBox.NONCEBYTES);
		Key key = aead.keygen(AEAD.Method.CHACHA20_POLY1305_IETF);

		String cipher = aead.encrypt("Qaqem vorid cerin", null, nonce, key, AEAD.Method.CHACHA20_POLY1305_IETF);

		String plain = aead.decrypt(cipher, null, nonce, key, AEAD.Method.CHACHA20_POLY1305_IETF);

		Log.d("cipherText", cipher);
		Log.d("plainText", plain);*/

		//aead.encrypt()


		//new EncryptAndWriteFile("pic.jpg", "pic.jpg.enc", true, "65B41D81D9D5419B3502247A4BC26DEF7E23CD6C1855E6CA67AE9BF4E72F25C0").execute();
		//new EncryptAndWriteFile("pic.jpg.enc", "picDec.jpg", false, "65B41D81D9D5419B3502247A4BC26DEF7E23CD6C1855E6CA67AE9BF4E72F25C0").execute();

		//new EncryptAndWriteFile("test.txt", "test.txt.enc", true, "65B41D81D9D5419B3502247A4BC26DEF7E23CD6C1855E6CA67AE9BF4E72F25C0").execute();
		//new EncryptAndWriteFile("test.txt.enc", "testDec.txt", false, "65B41D81D9D5419B3502247A4BC26DEF7E23CD6C1855E6CA67AE9BF4E72F25C0").execute();

		//new EncryptAndWriteFile("qaq.txt", "qaqik.txt").execute();

		/*Log.e("qaqik", "Starting...");
		String path = Environment.getExternalStorageDirectory().getAbsolutePath();
		Log.e("qaqik", path);
		try {

			FileInputStream in = new FileInputStream(new File(path + "/" + "pic.jpg"));
			FileOutputStream out = new FileOutputStream(new File(path + "/" + "pic.jpg.enc"), false);

			LazySodiumAndroid ls = new LazySodiumAndroid(new SodiumAndroid());
			SecretStream.Lazy secretStream = (SecretStream.Lazy) ls;
			int bufSize = 1024*4;
			byte[] buf = new byte[bufSize];

			Key key = secretStream.cryptoSecretStreamKeygen();
			byte[] header = new byte[SecretStream.HEADERBYTES];

			SecretStream.State state = secretStream.cryptoSecretStreamInitPush(header, key);
			out.write(header);

			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {
				byte tag = (numRead < bufSize ? SecretStream.XCHACHA20POLY1305_TAG_FINAL : 0);
				String enc = secretStream.cryptoSecretStreamPush(state, String.valueOf(buf), tag);
				byte[] encBytes = enc.getBytes();
				out.write(encBytes);


			}
			out.flush();
			out.close();
			in.close();
		} catch (SodiumException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		Log.e("qaqik", "Finished...");*/


	}

	/*public class EncryptAndWriteFile extends AsyncTask<byte[], Void, Void> {

		private final String inFile;
		private final String outFile;
		private boolean encrypt;
		private String key = null;

		public EncryptAndWriteFile(String inFile, String outFile, boolean encrypt, String key) {
			super();

			this.inFile = inFile;
			this.outFile = outFile;
			this.encrypt = encrypt;
			this.key = key;
		}


		@Override
		protected Void doInBackground(byte[]... params) {
			if(isExternalStorageWritable()) {
				try {

					String path = Environment.getExternalStorageDirectory().getAbsolutePath();
					File inFileHandle = new File(path + "/" + inFile);
					FileInputStream in = new FileInputStream(inFileHandle);
					FileOutputStream out = new FileOutputStream(new File(path + "/" + outFile));
					if(encrypt) {
						SafeCameraApplication.getCrypto().encrypt(key, in, out);
					}
					else{
						SafeCameraApplication.getCrypto().decrypt(key, in, out);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
			else{
				Log.e("debug", "Not writable...");
			}
			return null;
		}

		public boolean isExternalStorageWritable() {
			String state = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(state)) {
				return true;
			}
			return false;
		}

	}*/

	@Override
	protected void onResume() {
		super.onResume();

		if(Helpers.requestSDCardPermission(this)){
			Helpers.createFolders(this);
		}
	}
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case SafeCameraApplication.REQUEST_SD_CARD_PERMISSION: {
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					Helpers.createFolders(this);

				} else {
					finish();
				}
				return;
			}
		}
	}

	private OnClickListener signup() {
		return new OnClickListener() {
			public void onClick(View v) {
				final String email = ((EditText)findViewById(R.id.email)).getText().toString();
				final String password1 = ((EditText)findViewById(R.id.password1)).getText().toString();
				String password2 = ((EditText)findViewById(R.id.password2)).getText().toString();

				if(!Helpers.isValidEmail(email)){
					Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.invalid_email));
					return;
				}

				if(password1.equals("")){
					Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.password_empty));
					return;
				}
				
				if(!password1.equals(password2)){
					Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.password_not_match));
					return;
				}
				
				if(password1.length() < Integer.valueOf(getString(R.string.min_pass_length))){
					Helpers.showAlertDialog(SetUpActivity.this, String.format(getString(R.string.password_short), getString(R.string.min_pass_length)));
					return;
				}


				final ProgressDialog progressDialog = new ProgressDialog(SetUpActivity.this);
				progressDialog.setMessage(getString(R.string.creating_account));
				progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				progressDialog.show();

				HashMap<String, String> loginHash = SafeCameraApplication.getCrypto().getPasswordHashForStorage(password1);

				HashMap<String, String> postParams = new HashMap<String, String>();

				postParams.put("email", email);
				postParams.put("password", loginHash.get("hash"));
				postParams.put("salt", loginHash.get("salt"));

				HttpsClient.post(SetUpActivity.this, getString(R.string.api_server_url) + getString(R.string.registration_path), postParams, new HttpsClient.OnNetworkFinish() {
					@Override
					public void onFinish(StingleResponse response) {

						boolean result = false;
						if(response.isStatusOk()) {
							String token = response.get("token");
							if(token != null) {
								KeyManagement.setApiToken(SetUpActivity.this, token);

								try {
									SafeCameraApplication.getCrypto().generateMainKeypair(password1);
									((SafeCameraApplication) SetUpActivity.this.getApplication()).setKey(SafeCameraApplication.getCrypto().getPrivateKey(password1));

									KeyManagement.uploadKeyBundle(SetUpActivity.this, password1, new HttpsClient.OnNetworkFinish() {
										@Override
										public void onFinish(StingleResponse response) {
											progressDialog.dismiss();

											if(response.isStatusOk()) {
												Helpers.storePreference(SetUpActivity.this, SafeCameraApplication.USER_EMAIL, email);

												Intent intent = new Intent();
												intent.setClass(SetUpActivity.this, GalleryActivity.class);
												intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
												intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
												SetUpActivity.this.startActivity(intent);
												SetUpActivity.this.finish();
											}
											else{
												Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.fail_reg));
											}

										}
									});

									result = true;
								}
								catch (CryptoException e) {
									e.printStackTrace();
								}
							}
						}

						if(!result) {
							Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.fail_reg));
							progressDialog.dismiss();
						}

					}
				});

				/*
				SharedPreferences preferences = getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, MODE_PRIVATE);
				try {

					preferences.edit().putString(SafeCameraApplication.PASSWORD, loginHash).commit();

					SafeCameraApplication.getCrypto().generateMainKeypair(password1);

					((SafeCameraApplication) SetUpActivity.this.getApplication()).setKey(SafeCameraApplication.getCrypto().getPrivateKey(password1));
				}
				catch (CryptoException e) {
					e.printStackTrace();
					Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.unexpected_error));
					return;
				}
				
				Intent intent = new Intent();
				intent.setClass(SetUpActivity.this, DashboardActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
				finish();*/
			}
		};
	}
}
