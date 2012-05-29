package com.fenritz.safecamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

public class DashboardActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dashboard);
		
		findViewById(R.id.gotoCamera).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(DashboardActivity.this, CameraActivity.class);
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
}
