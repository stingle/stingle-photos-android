package org.stingle.photos;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

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
		setContentView(R.layout.activity_welcome);

		viewPager = findViewById(R.id.viewPager);
		dotsContainer = findViewById(R.id.dotsContainer);

		findViewById(R.id.sign_in).setOnClickListener(goToLogin());
		findViewById(R.id.sign_up).setOnClickListener(goToSignUp());

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
