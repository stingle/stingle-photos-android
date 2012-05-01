package com.fenritz.safecamera;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageView;

import com.fenritz.safecamera.util.Helpers;

public class TestActivity  extends Activity{

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test);

		
		Intent intent = new Intent(getBaseContext(), FileDialog.class);
		intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());

		// can user select directories or not
		intent.putExtra(FileDialog.CAN_SELECT_FILE, true);
		intent.putExtra(FileDialog.CAN_SELECT_DIR, false);
		intent.putExtra(FileDialog.SELECTION_MODE, FileDialog.MODE_OPEN);
		intent.putExtra(FileDialog.FILE_SELECTION_MODE, FileDialog.MODE_SINGLE);

		// alternatively you can set file filter
		// intent.putExtra(FileDialog.FORMAT_FILTER, new String[] {
		// "png" });

		startActivityForResult(intent, GalleryActivity.REQUEST_DECRYPT);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			if (requestCode == GalleryActivity.REQUEST_DECRYPT) {
				String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				try {
					FileInputStream inputStream = new FileInputStream(filePath);
					byte[] dec = Helpers.getAESCrypt(TestActivity.this).decrypt(inputStream);
					
					((ImageView)findViewById(R.id.imageView)).setImageBitmap(BitmapFactory.decodeByteArray(dec, 0, dec.length));
				}
				catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
