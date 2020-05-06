package org.stingle.photos.Welcome;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;

import org.stingle.photos.R;

import java.util.ArrayList;

public class WelcomePagerAdapter extends PagerAdapter {
	private Context mContext;
	private ArrayList<WelcomeItem> welcomeItems=new ArrayList<>();


	public WelcomePagerAdapter(Context mContext, ArrayList<WelcomeItem> items) {
		this.mContext = mContext;
		this.welcomeItems = items;
	}

	@Override
	public int getCount() {
		return welcomeItems.size();
	}

	@Override
	public boolean isViewFromObject(View view, Object object) {
		return view == object;
	}

	@Override
	public Object instantiateItem(ViewGroup container, int position) {
		View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_welcome, container, false);

		WelcomeItem item = welcomeItems.get(position);

		((ImageView)itemView.findViewById(R.id.slide_image)).setImageResource(item.getImageID());
		((TextView)itemView.findViewById(R.id.slide_header)).setText(item.getTitle());
		((TextView)itemView.findViewById(R.id.slide_desc)).setText(item.getDescription());

		container.addView(itemView);

		return itemView;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		container.removeView((LinearLayout) object);
	}
}
