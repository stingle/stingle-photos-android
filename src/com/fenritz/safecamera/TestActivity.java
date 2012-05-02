package com.fenritz.safecamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.fenritz.safecamera.util.DecryptAndShowImage;
import com.fenritz.safecamera.util.Helpers;

public class TestActivity  extends Activity{

	private String filePath;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test);

		
		Intent intent = new Intent(getBaseContext(), FileDialog.class);
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
		
		startActivityForResult(intent, GalleryActivity.REQUEST_DECRYPT);
		
	}
	
	private void decrypt(){
		//FileInputStream inputStream = new FileInputStream(filePath);
		//byte[] dec = Helpers.getAESCrypt(TestActivity.this).decrypt(inputStream);
		
		new DecryptAndShowImage(filePath, ((LinearLayout)findViewById(R.id.layout1))).execute();
		//((ImageView)findViewById(R.id.imageView)).setImageBitmap(BitmapFactory.decodeByteArray(dec, 0, dec.length));
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			if (requestCode == GalleryActivity.REQUEST_DECRYPT) {
				filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				decrypt();
			}
		}
	}
}
