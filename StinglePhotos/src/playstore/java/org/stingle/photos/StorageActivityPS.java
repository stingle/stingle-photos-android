package org.stingle.photos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Billing.BillingSecurity;
import org.stingle.photos.Sync.SyncAsyncTask;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageActivityPS extends AppCompatActivity implements PurchasesUpdatedListener {

	private BillingClient billingClient;

	private List<String> skus = new ArrayList<>();
	private List<HashMap<String, String>> paymentBoxDetails = new ArrayList<>();
	private List<Purchase> purchasedSkus = new ArrayList<>();
	private Map<String, SkuDetails> skuDetailsMap = new HashMap<>();
	private boolean isServiceConnected = false;
	private LinearLayout boxesParent;
	private boolean wentToPayment = true;

	private static final String BASE_64_ENCODED_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA00IIe/BqA9IUbrj2y1bgvgi6Tqu37ulDOndT3v4ElDCViaZI+JFF97lvYX0ijPIZ0ryj3Jo2mC+ncXXtQKeFZsvh9EauJyQXyQbsVERGjpTZ+sMdTkYu46zRjHezl3siLWWZMuc9UFQcvU1qkMOH6MI1gic1PAXi46wuMSL+kanDyQ2UfO3VlQsVkq9o/JwZGzaA4D8NkS1Ja2JcvdLxg2ES9YLaJBL/b2inHjiZW5tO59eAy6KqZy+N6kMfaoL421AhKovocejza7g4LFkkNvqdKfLZe4CEJVhYHN2OOBsqci7KwODsyEZEv3WztqnaylnuQpDVRZztdt9qnMqKnQIDAQAB";
	private int billingClientResponseCode;

	private LocalBroadcastManager lbm;
	private BroadcastReceiver onLogout = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			LoginManager.redirectToLogin(StorageActivityPS.this);
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

		billingClient = BillingClient.newBuilder(this).enablePendingPurchases().setListener(this).build();
		boxesParent = findViewById(R.id.payment_boxes);

		initSkus();

		final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
		pullToRefresh.setOnRefreshListener(() -> {
			syncAndUpdateQuota();
			pullToRefresh.setRefreshing(false);
		});

		lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));
	}

	@Override
	protected void onResume() {
		super.onResume();
		wentToPayment = false;
		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserAuthSuccess() {
				updateQuotaInfo();
				getSkuDetails();
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

	private void getSkuDetails() {
		purchasedSkus.clear();
		skuDetailsMap.clear();
		Runnable queryRequest = () -> {
			Purchase.PurchasesResult subscriptionResult = billingClient.queryPurchases(BillingClient.SkuType.SUBS);
			if (subscriptionResult.getResponseCode() == BillingResponseCode.OK) {
				purchasedSkus.addAll(subscriptionResult.getPurchasesList());
				Log.d("purchasedItems", purchasedSkus.toString());
			}
			else {
				Log.e("qaq", "Got an error response trying to query subscription purchases");
			}


			SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
			params.setSkusList(skus).setType(BillingClient.SkuType.SUBS);
			billingClient.querySkuDetailsAsync(params.build(),
					(billingResult, skuDetailsList) -> {
						if (billingResult.getResponseCode() == BillingResponseCode.OK && skuDetailsList != null) {
							Log.d("skuDetails", skuDetailsList.toString());
							if(skuDetailsList.size() == 0){
								//findViewById(R.id.paymentNotice).setVisibility(View.VISIBLE);
							}
							else {
								for (SkuDetails skuDetails : skuDetailsList) {
									skuDetailsMap.put(skuDetails.getSku(), skuDetails);
								}

								createPaymentBoxes();
							}
						}
						else{
							//findViewById(R.id.paymentNotice).setVisibility(View.VISIBLE);
						}

					});
		};
		executeServiceRequest(queryRequest);
	}

	private void createPaymentBoxes(){
		initPaymentBoxDetails();
		boxesParent.removeAllViews();

		if(purchasedSkus == null || purchasedSkus.size() == 0) {
			int spaceQuota = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_QUOTA, 0);
			createPaymentBox(Helpers.formatSpaceUnits(spaceQuota), null, null);
		}
		else{
			String purchasedSku = purchasedSkus.get(0).getSkus().get(0);
			for(int i = 0; i < paymentBoxDetails.size(); i++) {
				HashMap<String, String> boxDetails = paymentBoxDetails.get(i);
				if(boxDetails != null && boxDetails.get("monthly").equals(purchasedSku) || (boxDetails.get("yearly") != null && boxDetails.get("yearly").equals(purchasedSku))){
					paymentBoxDetails.remove(i);
					paymentBoxDetails.add(0, boxDetails);
					break;
				}
			}
		}

		for(HashMap<String, String> boxDetails : paymentBoxDetails) {
			createPaymentBox(boxDetails.get("title"), skuDetailsMap.get(boxDetails.get("monthly")), (boxDetails.get("yearly") != null ? skuDetailsMap.get(boxDetails.get("yearly")) : null ));
		}
	}

	private void createPaymentBox(String title, SkuDetails monthly, SkuDetails yearly){
		LinearLayout box = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.item_payment_box, boxesParent, false);
		((TextView) box.findViewById(R.id.storage_type)).setText(title);

		boolean isPurchasedMonthly = false;
		boolean isPurchasedYearly = false;
		if(purchasedSkus.size() > 0 && purchasedSkus.get(0) != null) {
			String purchasedSku = purchasedSkus.get(0).getSkus().get(0);
			if (monthly != null && monthly.getSku().equals(purchasedSku)) {
				isPurchasedMonthly = true;
			}
			if (yearly != null && yearly.getSku().equals(purchasedSku)) {
				isPurchasedYearly = true;
			}
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
			payButton.setTextColor(Helpers.getAttrColor(this, R.attr.colorControlNormal));

			String topPrice = "";
			if(isFreePlan){
				topPrice = getString(R.string.free);
				yearlyInfo.setVisibility(View.GONE);
				yearlyPrice.setVisibility(View.GONE);
			}
			else if(monthly != null && isPurchasedMonthly){
				topPrice = getString(R.string.monthly_plan, monthly.getPrice());

				if(yearly != null){
					double saved = (monthly.getPriceAmountMicros() / 1000000.0 * 12.0) - (yearly.getPriceAmountMicros() / 1000000.0);
					saved = Math.round(saved * 100.0) / 100.0;
					yearlyInfo.setText(getString(R.string.yearly_plan_save, yearly.getPriceCurrencyCode() + " " + saved));
					yearlyPrice.setText(getString(R.string.yearly_plan, yearly.getPrice()));
					final String yearlySku = yearly.getSku();
					yearlyPrice.setOnClickListener(view -> pay(yearlySku));
				}
				else{
					yearlyInfo.setVisibility(View.GONE);
					yearlyPrice.setVisibility(View.GONE);
				}
			}
			else if(yearly != null && isPurchasedYearly){
				topPrice = getString(R.string.yearly_plan, yearly.getPrice());

				if(monthly != null){
					yearlyInfo.setText(getString(R.string.change_to_monthly_plan));
					yearlyPrice.setText(getString(R.string.monthly_plan, monthly.getPrice()));
					final String monthlySku = monthly.getSku();
					yearlyPrice.setOnClickListener(view -> pay(monthlySku));
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
			payButton.setText(getString(R.string.monthly_plan, monthly.getPrice()));

			final String monthlySku = monthly.getSku();
			payButton.setOnClickListener(view -> pay(monthlySku));

			if(yearly != null) {
				double saved = (monthly.getPriceAmountMicros() / 1000000.0 * 12.0) - (yearly.getPriceAmountMicros() / 1000000.0);
				saved = Math.round(saved * 100.0) / 100.0;
				yearlyInfo.setText(getString(R.string.yearly_plan_save, yearly.getPriceCurrencyCode() + " " + saved));
				yearlyPrice.setText(getString(R.string.yearly_plan, yearly.getPrice()));

				final String yearlySku = yearly.getSku();
				yearlyPrice.setOnClickListener(view -> pay(yearlySku));
			}
			else{
				yearlyInfo.setVisibility(View.GONE);
				yearlyPrice.setVisibility(View.GONE);
			}
		}


		boxesParent.addView(box);
	}

	private void pay(final String sku) {
		Runnable queryRequest = new Runnable() {
			@Override
			public void run() {

				if (areSubscriptionsSupported()) {
					wentToPayment = true;
					SkuDetails onetb = skuDetailsMap.get(sku);
					BillingFlowParams.Builder flowParamsBuilder = BillingFlowParams.newBuilder();
					flowParamsBuilder.setSkuDetails(onetb);
					flowParamsBuilder.setObfuscatedAccountId(Helpers.getPreference(StorageActivityPS.this, StinglePhotosApplication.USER_ID, ""));

					if(purchasedSkus != null && purchasedSkus.size() > 0){
						//purchasedSkus.get(0).getSkus().get(0)
						flowParamsBuilder.setSubscriptionUpdateParams(
								BillingFlowParams.SubscriptionUpdateParams.newBuilder().setOldSkuPurchaseToken(purchasedSkus.get(0).getPurchaseToken()).build()
						);
					}

					BillingFlowParams flowParams = flowParamsBuilder.build();

					BillingResult responseCode = billingClient.launchBillingFlow(StorageActivityPS.this, flowParams);
					Log.e("responseCode", responseCode.toString());

				}
			}
		};
		executeServiceRequest(queryRequest);
	}

	public void startServiceConnection(final Runnable executeOnSuccess) {
		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
				if (billingResult.getResponseCode() == BillingResponseCode.OK) {
					isServiceConnected = true;
					if (executeOnSuccess != null) {
						executeOnSuccess.run();
					}
				}
				else{
					//findViewById(R.id.paymentNotice).setVisibility(View.VISIBLE);
				}
				billingClientResponseCode = billingResult.getResponseCode();
			}

			@Override
			public void onBillingServiceDisconnected() {
				isServiceConnected = false;
			}
		});
	}
	public void destroy() {
		if (billingClient != null && billingClient.isReady()) {
			billingClient.endConnection();
			billingClient = null;
		}
	}

	private void executeServiceRequest(Runnable runnable) {
		if (isServiceConnected) {
			runnable.run();
		} else {
			// If billing service was disconnected, we try to reconnect 1 time.
			// (feel free to introduce your retry policy here).
			startServiceConnection(runnable);
		}
	}

	public boolean areSubscriptionsSupported() {
		BillingResult response = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS);
		return response.getResponseCode() == BillingResponseCode.OK;
	}

	@Override
	public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
		if (billingResult.getResponseCode() == BillingResponseCode.OK) {
			for (Purchase purchase : purchases) {
				handlePurchase(purchase);
			}
		}
	}

	private void handlePurchase(final Purchase purchase) {
		if (!verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature())) {
			return;
		}

		if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
			if (!purchase.isAcknowledged()) {
				AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
						.setPurchaseToken(purchase.getPurchaseToken())
						.build();
				billingClient.acknowledgePurchase(params, billingResult -> {
					Log.d("purchase", purchase.getSkus().get(0) + " - " + purchase.getPurchaseToken() + " - " + billingResult.getResponseCode());

					if (billingResult.getResponseCode() == BillingResponseCode.OK) {
						Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.payment_success), Snackbar.LENGTH_LONG).show();
						syncAndUpdateQuota();
					}
					else{
						Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.payment_error), Snackbar.LENGTH_LONG).show();
						syncAndUpdateQuota();
					}
				});
			}
			else{
				Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.payment_success), Snackbar.LENGTH_LONG).show();
				syncAndUpdateQuota();
			}
		}
		else{
			Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.pending_purchase), Snackbar.LENGTH_LONG).show();
			syncAndUpdateQuota();
		}
	}

	private void syncAndUpdateQuota(){
		SyncManager.startSync(this, SyncAsyncTask.MODE_CLOUD_TO_LOCAL, new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				updateQuotaInfo();
				getSkuDetails();
			}
		});
	}

	private boolean verifyValidSignature(String signedData, String signature) {
		// Some sanity checks to see if the developer (that's you!) really followed the
		// instructions to run this sample (don't put these checks on your app!)
		if (BASE_64_ENCODED_PUBLIC_KEY.contains("CONSTRUCT_YOUR")) {
			throw new RuntimeException("Please update your app's public key at: "
					+ "BASE_64_ENCODED_PUBLIC_KEY");
		}

		try {
			return BillingSecurity.verifyPurchase(BASE_64_ENCODED_PUBLIC_KEY, signedData, signature);
		} catch (IOException e) {
			return false;
		}
	}
}
