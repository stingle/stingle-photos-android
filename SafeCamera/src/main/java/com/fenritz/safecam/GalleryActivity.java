package com.fenritz.safecam;

import android.content.res.Configuration;
import android.os.Bundle;

import com.fenritz.safecam.Auth.LoginManager;
import com.fenritz.safecam.Gallery.AutoFitGridLayoutManager;
import com.fenritz.safecam.Gallery.GalleryAdapterPisasso;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Gallery.DragSelectRecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import android.util.Log;
import android.view.View;

import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;

import android.view.MenuItem;

import com.google.android.material.navigation.NavigationView;

import androidx.drawerlayout.widget.DrawerLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import android.view.Menu;

public class GalleryActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener, GalleryAdapterPisasso.Listener {

	private DragSelectRecyclerView recyclerView;
	private GalleryAdapterPisasso adapter;
	AutoFitGridLayoutManager layoutManager;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gallery);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		FloatingActionButton fab = findViewById(R.id.fab);
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
						.setAction("Action", null).show();
			}
		});
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		NavigationView navigationView = findViewById(R.id.nav_view);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();
		navigationView.setNavigationItemSelectedListener(this);

		recyclerView = (DragSelectRecyclerView) findViewById(R.id.gallery_recycler_view);
		layoutManager = new AutoFitGridLayoutManager(GalleryActivity.this, Helpers.getThumbSize(GalleryActivity.this));
		layoutManager.setSpanSizeLookup(new AutoFitGridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				switch(adapter.getItemViewType(position)){
					case GalleryAdapterPisasso.TYPE_DATE:
						return layoutManager.getCurrentCalcSpanCount();
					default:
						return 1;
				}
			}
		});

		// use this setting to improve performance if you know that changes
		// in content do not change the layout size of the RecyclerView
		//recyclerView.setHasFixedSize(true);

		// use a linear layout manager

		// specify an adapter (see also next example)


	}

	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserLoginSuccess() {
				adapter = new GalleryAdapterPisasso(GalleryActivity.this, GalleryActivity.this, layoutManager);
				recyclerView.setAdapter(adapter);
				recyclerView.setLayoutManager(layoutManager);
				recyclerView.setHasFixedSize(true);
			}

			@Override
			public void onUserLoginFail() {
				LoginManager.redirectToDashboard(GalleryActivity.this);
			}
		});
		LoginManager.disableLockTimer(this);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		layoutManager.updateAutoFit();
	}

	@Override
	public void onBackPressed() {
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		if (drawer.isDrawerOpen(GravityCompat.START)) {
			drawer.closeDrawer(GravityCompat.START);
		}
		else if (!adapter.getSelectedIndices().isEmpty()) {
				adapter.clearSelected();
		}
		else {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.gallery, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@SuppressWarnings("StatementWithEmptyBody")
	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_home) {
			// Handle the camera action
		} else if (id == R.id.nav_gallery) {

		} else if (id == R.id.nav_slideshow) {

		} else if (id == R.id.nav_tools) {

		} else if (id == R.id.nav_share) {

		} else if (id == R.id.nav_send) {

		}

		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);
		return true;
	}

	@Override
	public void onClick(int index) {
		Log.d("click", String.valueOf(index));
		if (adapter.isSelectionModeActive()){
			adapter.toggleSelected(index);
		}
	}

	@Override
	public void onLongClick(int index) {
		Log.d("longclick", "longclick");
		recyclerView.setDragSelectActive(true, index);
		adapter.setSelectionModeActive(true);
	}

	@Override
	public void onSelectionChanged(int count) {

	}

}
