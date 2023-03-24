package org.stingle.photos;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Welcome.WelcomeItem;
import org.stingle.photos.Welcome.WelcomePagerAdapter;

import java.util.ArrayList;

public class WelcomeActivity extends AppCompatActivity {

	private LinearLayout dotsContainer;
	private int dotsCount;
	private ViewPager viewPager;
	private WelcomePagerAdapter mAdapter;
	private ImageView[] dots;

	ArrayList<WelcomeItem> welcomeItems=new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Helpers.setLocale(this);
		setContentView(R.layout.activity_welcome);

		viewPager = findViewById(R.id.viewPager);
		dotsContainer = findViewById(R.id.dotsContainer);

		findViewById(R.id.sign_in).setOnClickListener(goToLogin());
		findViewById(R.id.sign_up).setOnClickListener(goToSignUp());
		findViewById(R.id.settings_login).setOnClickListener(showSettings());
		loadData();

		mAdapter = new WelcomePagerAdapter(this,welcomeItems);
		viewPager.setAdapter(mAdapter);
		viewPager.setCurrentItem(0);
		viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

			}

			@Override
			public void onPageSelected(int position) {

				// Change the current position intimation

				for (int i = 0; i < dotsCount; i++) {
					dots[i].setImageDrawable(ContextCompat.getDrawable(WelcomeActivity.this, R.drawable.welcome_dot_non_selected));
				}

				dots[position].setImageDrawable(ContextCompat.getDrawable(WelcomeActivity.this, R.drawable.welcome_dot_selected));
			}

			@Override
			public void onPageScrollStateChanged(int state) {

			}
		});

		setUpDots();
	}

	private View.OnClickListener goToLogin(){
		return v -> {
			Intent intent = new Intent();
			intent.setClass(WelcomeActivity.this, LoginActivity.class);
			startActivity(intent);
		};
	}
	private View.OnClickListener goToSignUp(){
		return v -> {
			Intent intent = new Intent();
			intent.setClass(WelcomeActivity.this, SignUpActivity.class);
			startActivity(intent);
		};
	}
	private View.OnClickListener showSettings() {
		return v -> {
			MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
			builder.setView(R.layout.dialog_login_settings);
			builder.setCancelable(true);
			AlertDialog settingsDialog = builder.create();
			settingsDialog.show();

			Button okButton = settingsDialog.findViewById(R.id.okButton);
			Button cancelButton = settingsDialog.findViewById(R.id.cancelButton);
			Button revertDefaultButton = settingsDialog.findViewById(R.id.revertDefaultButton);
			final EditText serverUrlText = settingsDialog.findViewById(R.id.server_url);

			String currentServerURL = getSharedPreferences(StinglePhotosApplication.STICKY_PREFS, Context.MODE_PRIVATE).getString(StinglePhotosApplication.SERVER_URL, getString(R.string.api_server_url));;
			serverUrlText.setText(currentServerURL);

			revertDefaultButton.setOnClickListener(v1 -> {
				serverUrlText.setText(getString(R.string.api_server_url));
			});

			okButton.setOnClickListener(v2 -> {
				String serverURL = FileManager.ensureLastSlash(serverUrlText.getText().toString());
				if(!Helpers.isValidURL(serverURL)){
					Helpers.showAlertDialog(this, getString(R.string.error), getString(R.string.invalid_url));
					return;
				}
				if(serverURL.startsWith("http://")){
					Helpers.showAlertDialog(this, getString(R.string.error), getString(R.string.invalid_url_http));
					return;
				}
				getSharedPreferences(StinglePhotosApplication.STICKY_PREFS, Context.MODE_PRIVATE).edit().putString(StinglePhotosApplication.SERVER_URL, serverURL).apply();
				settingsDialog.dismiss();
				Toast.makeText(this, R.string.save_success, Toast.LENGTH_LONG).show();
			});

			cancelButton.setOnClickListener(v3 -> {
				settingsDialog.dismiss();
			});
		};
	}

	public void loadData(){

		int[] header = {R.string.welcome_header1, R.string.welcome_header2, R.string.welcome_header3, R.string.welcome_header4};
		int[] desc = {R.string.welcome_desc1, R.string.welcome_desc2, R.string.welcome_desc3, R.string.welcome_desc4};
		int[] imageId = {R.drawable.welcome_image1, R.drawable.welcome_image2, R.drawable.welcome_image3, R.drawable.welcome_image4};

		for(int i=0;i<imageId.length;i++)
		{
			WelcomeItem item = new WelcomeItem();
			item.setImageID(imageId[i]);
			item.setTitle(getResources().getString(header[i]));
			item.setDescription(getResources().getString(desc[i]));

			welcomeItems.add(item);
		}
	}

	private void setUpDots() {

		dotsCount = mAdapter.getCount();
		dots = new ImageView[dotsCount];

		for (int i = 0; i < dotsCount; i++) {
			dots[i] = new ImageView(this);
			dots[i].setImageDrawable(ContextCompat.getDrawable(WelcomeActivity.this, R.drawable.welcome_dot_non_selected));

			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
			);

			params.setMargins(6, 0, 6, 0);

			dotsContainer.addView(dots[i], params);
		}

		dots[0].setImageDrawable(ContextCompat.getDrawable(WelcomeActivity.this, R.drawable.welcome_dot_selected));
	}
}
