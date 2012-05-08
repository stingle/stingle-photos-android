package com.fenritz.safecamera;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.fenritz.safecamera.util.Helpers;

public class TestActivity  extends Activity{

	private String filePath;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test);

		
		/*Intent intent = new Intent(getBaseContext(), FileDialog.class);
		intent.putExtra(FileDialog.START_PATH, Helpers.getHomeDir(TestActivity.this));

		// can user select directories or not
		intent.putExtra(FileDialog.CAN_SELECT_FILE, true);
		intent.putExtra(FileDialog.CAN_SELECT_DIR, false);
		intent.putExtra(FileDialog.SELECTION_MODE, FileDialog.MODE_OPEN);
		intent.putExtra(FileDialog.FILE_SELECTION_MODE, FileDialog.MODE_SINGLE);

		// alternatively you can set file filter
		// intent.putExtra(FileDialog.FORMAT_FILTER, new String[] {
		// "png" });

		((Button)findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				decrypt();
			}
		});
		
		startActivityForResult(intent, GalleryActivity.REQUEST_DECRYPT);*/
		//IMG_20120307_020351.jpg.sc
		
		new DecryptImage(R.id.imageView1).execute("/sdcard/SafeCamera/IMG_20120307_024546.jpg.sc");
		//new DecryptImage(R.id.imageView2).execute("/sdcard/SafeCamera/IMG_20120307_020351.jpg.sc");
	}
	
	/*private void decrypt(){
		//FileInputStream inputStream = new FileInputStream(filePath);
		//byte[] dec = Helpers.getAESCrypt(TestActivity.this).decrypt(inputStream);
		
		new DecryptAndShowImage(filePath, ((LinearLayout)findViewById(R.id.layout1))).execute();
		//((ImageView)findViewById(R.id.imageView)).setImageBitmap(BitmapFactory.decodeByteArray(dec, 0, dec.length));
	}*/
	
	/*@SuppressWarnings("unchecked")
	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			if (requestCode == GalleryActivity.REQUEST_DECRYPT) {
				filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				decrypt();
			}
		}
	}*/
	
	public class DecryptImage extends AsyncTask<String, Void, Bitmap> {

		int imageId;
		
		public DecryptImage(int pImageId){
			imageId = pImageId;
		}
		
		@Override
		protected Bitmap doInBackground(String... params) {
			
			try {
				FileInputStream input = new FileInputStream(params[0]);

				byte[] decryptedData = Helpers.getAESCrypt(getApplicationContext()).decrypt(input);
	
				if (decryptedData != null) {
					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, 300);
					decryptedData = null;
					return bitmap;
				}
				else {
					Log.d("sc", "Unable to decrypt: " + filePath);
				}
			}
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			super.onPostExecute(bitmap);

			((ImageView)findViewById(imageId)).setImageBitmap(bitmap);
		}

	}
}
