package com.fenritz.safecamera;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import com.fenritz.safecamera.util.Helpers;

public class ViewImageActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.view_photo);

		Intent intent = getIntent();
		String imagePath = intent.getStringExtra("EXTRA_IMAGE_PATH");

		try {
			FileInputStream input = new FileInputStream(imagePath);
			byte[] decryptedData = SafeCameraActivity.crypto.decrypt(input);

			if (decryptedData != null) {
				BitmapFactory.Options opts=new BitmapFactory.Options();
				opts.inSampleSize = 4;
				Bitmap bMap = BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.length, opts);
				ImageView image = (ImageView) findViewById(R.id.image);
				image.setImageBitmap(bMap);
			}
			else {
				Toast.makeText(this, "Unable to decrypt image", Toast.LENGTH_LONG).show();
			}
		}
		catch (FileNotFoundException e) {
			Helpers.showAlertDialog(ViewImageActivity.this, getString(R.string.file_not_found));
			e.printStackTrace();
		}
	}

}
