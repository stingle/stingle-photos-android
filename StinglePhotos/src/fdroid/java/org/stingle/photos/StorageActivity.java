package org.stingle.photos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Sync.SyncAsyncTask;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class StorageActivity extends AppCompatActivity {


	private List<String> skus = new ArrayList<>();
	private List<HashMap<String, String>> paymentBoxDetails = new ArrayList<>();
	private boolean isServiceConnected = false;
	private LinearLayout boxesParent;
	private boolean wentToPayment = true;

	private LocalBroadcastManager lbm;
	private BroadcastReceiver onLogout = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			LoginManager.redirectToLogin(StorageActivity.this);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Helpers.setLocale(this);
		setContentView(R.layout.activity_storage);

		Toolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(getString(R.string.title_storage));
		setSupportActionBar(toolbar);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowHomeEnabled(true);

		toolbar.setNavigationOnClickListener(view -> finish());

		Helpers.blockScreenshotsIfEnabled(this);

		boxesParent = findViewById(R.id.payment_boxes);

		initSkus();

		final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
		pullToRefresh.setOnRefreshListener(() -> {
			syncAndUpdateQuota();
			pullToRefresh.setRefreshing(false);
		});

		lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));

		findViewById(R.id.paymentNotice).setVisibility(View.VISIBLE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		wentToPayment = false;
		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserAuthSuccess() {
				updateQuotaInfo();
			}
		});
		LoginManager.disableLockTimer(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if(!wentToPayment) {
			LoginManager.setLockedTime(this);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		lbm.unregisterReceiver(onLogout);
	}

	private void initSkus() {
		skus.add("100gb_monthly");
		skus.add("100gb_yearly");
		skus.add("300gb_monthly");
		skus.add("300gb_yearly");
		skus.add("1tb_monthly");
		skus.add("1tb_yearly");
		skus.add("3tb_monthly");
		skus.add("3tb_yearly");
		skus.add("5tb_monthly");
		skus.add("10tb_monthly");
		skus.add("20tb_monthly");
	}

	private void initPaymentBoxDetails(){
		paymentBoxDetails.clear();
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "100 GB"); put("monthly", "100gb_monthly"); put("yearly", "100gb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "300 GB"); put("monthly", "300gb_monthly"); put("yearly", "300gb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "1 TB"); put("monthly", "1tb_monthly"); put("yearly", "1tb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "3 TB"); put("monthly", "3tb_monthly"); put("yearly", "3tb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "5 TB"); put("monthly", "5tb_monthly"); put("yearly", null);}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "10 TB"); put("monthly", "10tb_monthly"); put("yearly", null);}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "20 TB"); put("monthly", "20tb_monthly"); put("yearly", null);}});
	}

	private void updateQuotaInfo() {
		int spaceUsed = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_USED, 0);
		int spaceQuota = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_QUOTA, 1);
		int percent = Math.round(spaceUsed * 100 / spaceQuota);


		String quotaText = getString(R.string.quota_text, Helpers.formatSpaceUnits(spaceUsed), Helpers.formatSpaceUnits(spaceQuota), percent + "%");
		((TextView) findViewById(R.id.quotaInfo)).setText(quotaText);
		ContentLoadingProgressBar progressBar = findViewById(R.id.quotaBar);

		progressBar.setMax(100);
		progressBar.setProgress(percent);

	}


	private void createPaymentBoxes(){
		initPaymentBoxDetails();
		boxesParent.removeAllViews();

	}


	private void syncAndUpdateQuota(){
		SyncManager.startSync(this, SyncAsyncTask.MODE_CLOUD_TO_LOCAL, new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				updateQuotaInfo();
			}
		});
	}

}
