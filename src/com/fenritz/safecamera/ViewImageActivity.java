package com.fenritz.safecamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.fenritz.safecamera.util.DecryptAndShowImage;

public class ViewImageActivity extends Activity {

	DecryptAndShowImage task;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		setContentView(R.layout.view_photo);
		getWindow().addFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN );

		Intent intent = getIntent();
		String imagePath = intent.getStringExtra("EXTRA_IMAGE_PATH");

		task = new DecryptAndShowImage(imagePath, ((LinearLayout)findViewById(R.id.parent_layout)), null, null, true);
		task.execute();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		task.cancel(true);
	}

}
