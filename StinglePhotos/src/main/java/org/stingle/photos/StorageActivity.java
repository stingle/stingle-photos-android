package org.stingle.photos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.stingle.photos.AsyncTasks.DowngradeToFreeAsyncTask;
import org.stingle.photos.AsyncTasks.GetBillingInfoAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Billing.BillingEventsListener;
import org.stingle.photos.Billing.PlayBillingProxy;
import org.stingle.photos.Billing.WebBillingDialogFragment;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Util.Helpers;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class StorageActivity extends AppCompatActivity {

	private List<HashMap<String, String>> paymentBoxDetails = new ArrayList<>();
	private LinearLayout boxesParent;
	private boolean wentToPayment = false;
	private String currentPlan = "free";
	int spaceUsed = 0;
	int spaceQuota = 0;
	boolean isManual = false;
	private String currency = "USD";
	private boolean isPlayBillingAvailable = true;
	private HashMap<String,Double> prices = new HashMap<String,Double>() {{
		put("free", (double) 0);
		put("100gb_monthly", 2.99);
		put("100gb_yearly", 29.99);
		put("300gb_monthly", 4.99);
		put("300gb_yearly", 49.99);
		put("1tb_monthly", 11.99);
		put("1tb_yearly", 111.99);
		put("3tb_monthly", 35.99);
		put("3tb_yearly", 359.99);
		put("5tb_monthly", 59.99);
		put("5tb_yearly", 599.99);
		put("10tb_monthly", 110.99);
		put("10tb_yearly", 1109.99);
		put("20tb_monthly", 239.99);
		put("20tb_yearly", 2399.99);
	}};

	private ArrayList<HashMap<String, String>> paymentOptions = new ArrayList<HashMap<String, String>>(){{
		add(new HashMap<String, String>(){{
			put("name", "google");
			put("title", String.valueOf(R.string.google_play_billing));
			put("icon", String.valueOf(R.mipmap.ic_google_pay));
		}});
		add(new HashMap<String, String>(){{
			put("name", "stripe");
			put("title", String.valueOf(R.string.stripe_billing));
			put("icon", String.valueOf(R.mipmap.ic_stripe));
		}});
	}};

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

		final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
		pullToRefresh.setOnRefreshListener(() -> {
			getPlanInfo();
			pullToRefresh.setRefreshing(false);
		});

		lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));



		PlayBillingProxy.playBillingListener(this, new BillingEventsListener() {
			@Override
			public void playBillingNotAvailable() {
				isPlayBillingAvailable = false;
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		wentToPayment = false;
		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserAuthSuccess() {
				getPlanInfo();
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


	public void getPlanInfo(){
		((TextView) findViewById(R.id.planInfo)).setVisibility(View.GONE);
		(new GetBillingInfoAsyncTask(this, new OnAsyncTaskFinish() {
			@Override
			public void onFinish(Object object) {
				super.onFinish(object);
				if(object instanceof GetBillingInfoAsyncTask.BillingInfo){
					GetBillingInfoAsyncTask.BillingInfo info = (GetBillingInfoAsyncTask.BillingInfo) object;
					String planInfoText = "";
					if(info.plan == null || info.paymentGw == null || info.expiration == null || info.spaceQuota == null || info.spaceUsed == null){
						return;
					}
					if(!info.plan.equals("free") && !info.paymentGw.equals("null")) {
						planInfoText += getString(R.string.plan_info_gw_text, Helpers.capitalize(info.paymentGw));
					}
					if(info.isManual && info.expiration != null){
						Date date = new Date(Long.parseLong(info.expiration));

						DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
						planInfoText += "\n" + getString(R.string.plan_info_expires_text, dateFormat.format(date));
					}
					if(planInfoText.length() > 0) {
						((TextView) findViewById(R.id.planInfo)).setText(planInfoText);
						((TextView) findViewById(R.id.planInfo)).setVisibility(View.VISIBLE);
					}
					currentPlan = info.plan;
					spaceQuota = Integer.parseInt(info.spaceQuota);
					spaceUsed = Integer.parseInt(info.spaceUsed);
					isManual = info.isManual;

					updateQuotaInfo();
					createPaymentBoxes();
				}
			}
		})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private void initPaymentBoxDetails(){
		paymentBoxDetails.clear();
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "100 GB"); put("monthly", "100gb_monthly"); put("yearly", "100gb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "300 GB"); put("monthly", "300gb_monthly"); put("yearly", "300gb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "1 TB"); put("monthly", "1tb_monthly"); put("yearly", "1tb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "3 TB"); put("monthly", "3tb_monthly"); put("yearly", "3tb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "5 TB"); put("monthly", "5tb_monthly"); put("yearly", "5tb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "10 TB"); put("monthly", "10tb_monthly"); put("yearly", "10tb_yearly");}});
		paymentBoxDetails.add(new HashMap<String, String>() {{put("title", "20 TB"); put("monthly", "20tb_monthly"); put("yearly", "20tb_yearly");}});
	}

	private void updateQuotaInfo() {
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

		if(currentPlan.equals("free")) {
			createPaymentBox(Helpers.formatSpaceUnits(spaceQuota), null, null);
		}
		else{
			for(int i = 0; i < paymentBoxDetails.size(); i++) {
				HashMap<String, String> boxDetails = paymentBoxDetails.get(i);
				if(boxDetails != null && boxDetails.get("monthly").equals(currentPlan) || (boxDetails.get("yearly") != null && boxDetails.get("yearly").equals(currentPlan))){
					paymentBoxDetails.remove(i);
					paymentBoxDetails.add(0, boxDetails);
					break;
				}
			}
		}

		for(HashMap<String, String> boxDetails : paymentBoxDetails) {
			createPaymentBox(boxDetails.get("title"), boxDetails.get("monthly"), boxDetails.get("yearly"));
		}

		if(!isManual && !currentPlan.equals("free")) {
			createPaymentBox(Helpers.formatSpaceUnits(1024), null, null);
		}
	}

	private void createPaymentBox(String title, String monthly, String yearly){
		LinearLayout box = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.item_payment_box, boxesParent, false);
		((TextView) box.findViewById(R.id.storage_type)).setText(title);

		boolean isPurchasedMonthly = false;
		boolean isPurchasedYearly = false;

		if (monthly != null && monthly.equals(currentPlan)) {
			isPurchasedMonthly = true;
		}
		if (yearly != null && yearly.equals(currentPlan)) {
			isPurchasedYearly = true;
		}

		boolean isPurchased = isPurchasedMonthly || isPurchasedYearly;
		boolean isFreePlan = !isPurchased && monthly == null && yearly == null;
		TextView storageType = box.findViewById(R.id.storage_type);
		TextView priceTop = box.findViewById(R.id.price_top);
		Button payButton = box.findViewById(R.id.pay);
		TextView yearlyInfo = box.findViewById(R.id.yearlyInfo);
		TextView yearlyPrice = box.findViewById(R.id.yearly);

		if(isPurchased || isFreePlan){
			box.setBackground(getDrawable(R.drawable.rectangle_selected));
			payButton.setText(getString(R.string.current_plan));
			payButton.setEnabled(false);
			payButton.setTextColor(Helpers.getAttrColor(this, androidx.appcompat.R.attr.colorControlNormal));

			String topPrice = "";
			if(isFreePlan){
				topPrice = getString(R.string.free);
				if(!currentPlan.equals("free")){
					payButton.setText(getString(R.string.downgrade_to_free_plan));
					payButton.setEnabled(true);
					payButton.setOnClickListener(view -> downgradeToFree());
				}
				yearlyInfo.setVisibility(View.GONE);
				yearlyPrice.setVisibility(View.GONE);
			}
			else if(monthly != null && isPurchasedMonthly){
				topPrice = getString(R.string.monthly_plan, String.valueOf(prices.get(monthly)));

				if(yearly != null){
					double saved = (prices.get(monthly) * 12.0) - prices.get(yearly);
					saved = Math.round(saved * 100.0) / 100.0;
					yearlyInfo.setText(getString(R.string.yearly_plan_save, currency + " " + saved));
					yearlyPrice.setText(getString(R.string.yearly_plan, String.valueOf(prices.get(yearly))));
					yearlyPrice.setOnClickListener(view -> pay(yearly));
				}
				else{
					yearlyInfo.setVisibility(View.GONE);
					yearlyPrice.setVisibility(View.GONE);
				}
			}
			else if(yearly != null && isPurchasedYearly){
				topPrice = getString(R.string.yearly_plan, String.valueOf(prices.get(yearly)));

				if(monthly != null){
					yearlyInfo.setText(getString(R.string.change_to_monthly_plan));
					yearlyPrice.setText(getString(R.string.monthly_plan, String.valueOf(prices.get(monthly))));
					yearlyPrice.setOnClickListener(view -> pay(monthly));
				}
				else{
					yearlyInfo.setVisibility(View.GONE);
					yearlyPrice.setVisibility(View.GONE);
				}
			}
			priceTop.setText(topPrice);
		}
		else{
			priceTop.setVisibility(View.GONE);
			payButton.setText(getString(R.string.monthly_plan, String.valueOf(prices.get(monthly))));

			payButton.setOnClickListener(view -> pay(monthly));

			if(yearly != null) {
				double saved = (prices.get(monthly) * 12.0) - prices.get(yearly);
				saved = Math.round(saved * 100.0) / 100.0;
				yearlyInfo.setText(getString(R.string.yearly_plan_save, currency + " " + saved));
				yearlyPrice.setText(getString(R.string.yearly_plan, String.valueOf(prices.get(yearly))));

				yearlyPrice.setOnClickListener(view -> pay(yearly));
			}
			else{
				yearlyInfo.setVisibility(View.GONE);
				yearlyPrice.setVisibility(View.GONE);
			}
		}


		boxesParent.addView(box);
	}



	private void pay(String plan){
		BottomSheetDialog sheet = new BottomSheetDialog(this);
		sheet.setContentView(R.layout.dialog_payment_options);


		sheet.findViewById(R.id.button_google_pay).setOnClickListener(v -> {
			PlayBillingProxy.initiatePayment(this, plan);
			sheet.dismiss();
		});

		boolean disableGoogleBecauseOfPlan = false;
		if(plan.equals("5tb_yearly") || plan.equals("10tb_yearly") || plan.equals("20tb_yearly")){
			disableGoogleBecauseOfPlan = true;
		}
		if(!isPlayBillingAvailable || disableGoogleBecauseOfPlan){
			sheet.findViewById(R.id.button_google_pay).setEnabled(false);
		}

		sheet.findViewById(R.id.button_stripe).setOnClickListener(v -> {
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
			WebBillingDialogFragment newFragment = new WebBillingDialogFragment(this);


			String url = getUrlForStripe(plan);
			Log.i("url", url);
			newFragment.setUrl(url);

			newFragment.show(ft, "stripe_dialog");
			sheet.dismiss();
		});

		sheet.show();
	}

	private void downgradeToFree(){
		Helpers.showConfirmDialog(
				this,
				getString(R.string.downgrade_question),
				getString(R.string.downgrade_question_desc),
				R.drawable.ic_action_delete,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						(new DowngradeToFreeAsyncTask(StorageActivity.this, new OnAsyncTaskFinish() {
							@Override
							public void onFinish(Boolean result) {
								super.onFinish(result);
								if(result != null && result) {
									Helpers.showInfoDialog(StorageActivity.this, getString(R.string.success), getString(R.string.success_downgrade));
								}
								else{
									Helpers.showAlertDialog(StorageActivity.this, getString(R.string.error), getString(R.string.something_went_wrong));
								}
								getPlanInfo();
							}
						})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
				},
				null
		);
	}

	private String getUrlForStripe(String plan){
		HashMap<String, String> params = new HashMap<>();
		params.put("plan", plan);

		try {
			String token = URLEncoder.encode(KeyManagement.getApiToken(this), "utf-8");
			String encParams = Crypto.byteArrayToBase64UrlSafe(CryptoHelpers.encryptParamsForServer(params).getBytes());

			return StinglePhotosApplication.getApiUrl() + getString(R.string.stripe_url) + "/?token=" + token + "&params=" + encParams;
		}
		catch (CryptoException | UnsupportedEncodingException e){
			return null;
		}
	}


}
