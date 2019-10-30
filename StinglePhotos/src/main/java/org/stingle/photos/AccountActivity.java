package org.stingle.photos;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ContentLoadingProgressBar;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Billing.BillingSecurity;
import org.stingle.photos.Billing.StingleBilling;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Sync.SyncService;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountActivity extends AppCompatActivity implements PurchasesUpdatedListener {

	private BillingClient billingClient;

	private List<String> skus = new ArrayList<>();
	private List<Purchase> purchasedSkus = new ArrayList<>();
	private Map<String, SkuDetails> skuDetailsMap = new HashMap<>();
	private boolean isServiceConnected = false;
	private int billingClientResponseCode;
	private LinearLayout boxesParent;

	private static final String BASE_64_ENCODED_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA00IIe/BqA9IUbrj2y1bgvgi6Tqu37ulDOndT3v4ElDCViaZI+JFF97lvYX0ijPIZ0ryj3Jo2mC+ncXXtQKeFZsvh9EauJyQXyQbsVERGjpTZ+sMdTkYu46zRjHezl3siLWWZMuc9UFQcvU1qkMOH6MI1gic1PAXi46wuMSL+kanDyQ2UfO3VlQsVkq9o/JwZGzaA4D8NkS1Ja2JcvdLxg2ES9YLaJBL/b2inHjiZW5tO59eAy6KqZy+N6kMfaoL421AhKovocejza7g4LFkkNvqdKfLZe4CEJVhYHN2OOBsqci7KwODsyEZEv3WztqnaylnuQpDVRZztdt9qnMqKnQIDAQAB";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//getActionBar().setDisplayHomeAsUpEnabled(true);
		//getActionBar().setHomeButtonEnabled(true);

		setContentView(R.layout.activity_account);

		Toolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(getString(R.string.title_account));
		setSupportActionBar(toolbar);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowHomeEnabled(true);

		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				finish();
			}
		});

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		billingClient = BillingClient.newBuilder(this).enablePendingPurchases().setListener(this).build();
		boxesParent = findViewById(R.id.payment_boxes);

		initSkus();
	}

	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserAuthSuccess() {
				updateQuotaInfo();
				getSkuDetails();
			}

			@Override
			public void onUserAuthFail() {

			}

			@Override
			public void onNotLoggedIn() {

			}

			@Override
			public void onLoggedIn() {

			}
		});
		LoginManager.disableLockTimer(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		LoginManager.setLockedTime(this);
	}

	private void initSkus() {
		skus.add("1tb_monthly");
		skus.add("1tb_yearly");
		skus.add("3tb_monthly");
		skus.add("3tb_yearly");
		skus.add("5tb_monthly");
		skus.add("10tb_monthly");
		skus.add("20tb_monthly");
	}

	private void updateQuotaInfo() {
		int spaceUsed = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_USED, 0);
		int spaceQuota = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_QUOTA, 1);
		int percent = Math.round(spaceUsed * 100 / spaceQuota);


		String quotaText = getString(R.string.quota_text, Helpers.formatSpaceUnits(spaceUsed), Helpers.formatSpaceUnits(spaceQuota), String.valueOf(percent) + "%");
		((TextView) findViewById(R.id.quotaInfo)).setText(quotaText);
		ContentLoadingProgressBar progressBar = findViewById(R.id.quotaBar);

		progressBar.setMax(100);
		progressBar.setProgress(percent);

	}

	private void getSkuDetails() {
		purchasedSkus.clear();
		skuDetailsMap.clear();
		Runnable queryRequest = new Runnable() {
			@Override
			public void run() {
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
						new SkuDetailsResponseListener() {
							@Override
							public void onSkuDetailsResponse(BillingResult billingResult,
															 List<SkuDetails> skuDetailsList) {
								if (billingResult.getResponseCode() == BillingResponseCode.OK && skuDetailsList != null) {
									Log.d("skuDetails", skuDetailsList.toString());
									for (SkuDetails skuDetails : skuDetailsList) {
										skuDetailsMap.put(skuDetails.getSku(), skuDetails);
									}

									createPaymentBoxes();
								}

							}
						});
			}
		};
		executeServiceRequest(queryRequest);
	}

	private void createPaymentBoxes(){

		boxesParent.removeAllViews();

		if(purchasedSkus == null || purchasedSkus.size() == 0) {
			int spaceQuota = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_QUOTA, 0);
			createPaymentBox(Helpers.formatSpaceUnits(spaceQuota), null, null);
		}
		createPaymentBox("1 TB", skuDetailsMap.get("1tb_monthly"), skuDetailsMap.get("1tb_yearly"));
		createPaymentBox("3 TB", skuDetailsMap.get("3tb_monthly"), skuDetailsMap.get("3tb_yearly"));
		createPaymentBox("5 TB", skuDetailsMap.get("5tb_monthly"), null);
		createPaymentBox("10 TB", skuDetailsMap.get("10tb_monthly"), null);
		createPaymentBox("20 TB", skuDetailsMap.get("20tb_monthly"), null);

		if(purchasedSkus != null && purchasedSkus.size() > 0){
			SkuDetails sku = skuDetailsMap.get(purchasedSkus.get(0).getSku());
			((TextView) findViewById(R.id.planInfo)).setText(sku.getDescription());
		}
		else {
			((TextView) findViewById(R.id.planInfo)).setText(getString(R.string.free));
		}
	}

	private void createPaymentBox(String title, SkuDetails monthly, SkuDetails yearly){
		LinearLayout box = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.payment_box, boxesParent, false);
		((TextView) box.findViewById(R.id.storage_type)).setText(title);

		boolean isPurchased = false;
		for(Purchase purchase : purchasedSkus){
			String purchasedSku = purchase.getSku();
			if(monthly != null && monthly.getSku().equals(purchasedSku)){
				isPurchased = true;
			}
			if(yearly != null && yearly.getSku().equals(purchasedSku)){
				isPurchased = true;
			}
		}

		if(isPurchased || (purchasedSkus.size() == 0 && monthly == null && yearly == null)){
			box.setBackground(getDrawable(R.drawable.rectangle_selected));
		}

		if(monthly != null) {
			if(isPurchased){
				box.findViewById(R.id.price_monthly).setVisibility(View.GONE);
				((Button)box.findViewById(R.id.pay)).setText(getString(R.string.current_plan));
				((Button)box.findViewById(R.id.pay)).setEnabled(false);
				((Button)box.findViewById(R.id.pay)).setTextColor(getColor(R.color.primaryDarkColor));
			}
			else{
				((TextView) box.findViewById(R.id.price_monthly)).setText(getString(R.string.monthly_plan, monthly.getPrice()));
				final String monthlySku = monthly.getSku();
				box.findViewById(R.id.pay).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						pay(monthlySku);
					}
				});
			}


		}
		else{
			((TextView) box.findViewById(R.id.price_monthly)).setText(getString(R.string.free));
			box.findViewById(R.id.pay).setVisibility(View.GONE);
		}

		if(yearly != null && !isPurchased) {
			double saved = (monthly.getPriceAmountMicros() / 1000000.0 * 12.0) - (yearly.getPriceAmountMicros() / 1000000.0);
			saved = Math.round(saved *100.0)/100.0;
			String yearlyText = getString(R.string.yearly_plan, yearly.getPrice(), yearly.getPriceCurrencyCode() + " " + String.valueOf(saved));
			((TextView) box.findViewById(R.id.yearly)).setText(yearlyText);
			final String yearlySku = yearly.getSku();
			box.findViewById(R.id.yearly).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					pay(yearlySku);
				}
			});
		}
		else{
			box.findViewById(R.id.yearly).setVisibility(View.GONE);
		}
		boxesParent.addView(box);
	}

	private void pay(final String sku) {
		Runnable queryRequest = new Runnable() {
			@Override
			public void run() {

				if (areSubscriptionsSupported()) {
					SkuDetails onetb = skuDetailsMap.get(sku);
					BillingFlowParams.Builder flowParamsBuilder = BillingFlowParams.newBuilder();
					flowParamsBuilder.setSkuDetails(onetb);

					if(purchasedSkus != null && purchasedSkus.size() > 0){
						flowParamsBuilder.setOldSku(purchasedSkus.get(0).getSku());
					}

					BillingFlowParams flowParams = flowParamsBuilder.build();

					BillingResult responseCode = billingClient.launchBillingFlow(AccountActivity.this, flowParams);
					Log.e("responseCode", responseCode.toString());
				}
			}
		};
		executeServiceRequest(queryRequest);






		/*Purchase.PurchasesResult subscriptionResult
				= billingClient.queryPurchases(BillingClient.SkuType.SUBS);
		Log.i("qaq", "Querying subscriptions result code: "
				+ subscriptionResult.getResponseCode()
				+ " res: " + subscriptionResult.getPurchasesList().size());

		if (subscriptionResult.getResponseCode() == BillingResponseCode.OK) {
			Log.e("subres", subscriptionResult.getPurchasesList().toString());
		} else {
			Log.e("qaq", "Got an error response trying to query subscription purchases");
		}

		List<String> skuList = new ArrayList<>();
		skuList.add("1tb_monthly");


		SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
		params.setSkusList(skuList).setType(BillingClient.SkuType.SUBS);
		billingClient.querySkuDetailsAsync(params.build(),
				new SkuDetailsResponseListener() {
					@Override
					public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList) {
						Log.e("skudetails", skuDetailsList.toString());
						if (skuDetailsList != null && skuDetailsList.size() == 1) {

						}
					}


				});*/
	}

	public void startServiceConnection(final Runnable executeOnSuccess) {
		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(BillingResult billingResult) {
				if (billingResult.getResponseCode() == BillingResponseCode.OK) {
					isServiceConnected = true;
					if (executeOnSuccess != null) {
						executeOnSuccess.run();
					}
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
	public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
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
						.setDeveloperPayload(Helpers.getPreference(this, StinglePhotosApplication.USER_ID, ""))
						.build();
				billingClient.acknowledgePurchase(params, new AcknowledgePurchaseResponseListener() {
					@Override
					public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
						Log.d("purchase", purchase.getSku() + " - " + purchase.getPurchaseToken() + " - " + billingResult.getResponseCode());

						if (billingResult.getResponseCode() == BillingResponseCode.OK) {
							notifyServerAboutPurchase(purchase);
						}
					}
				});
			}
			else{
				notifyServerAboutPurchase(purchase);
			}
		}
		else{
			Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.pending_purchase), Snackbar.LENGTH_LONG).show();
		}
	}

	private void notifyServerAboutPurchase(Purchase purchase){
		final ProgressDialog spinner = Helpers.showProgressDialog(this, getString(R.string.confirming_purchase), null);
		(new StingleBilling.NotifyServerAboutPurchase(AccountActivity.this, purchase.getSku(), purchase.getPurchaseToken(), new StingleBilling.OnFinish() {
			@Override
			public void onFinish(boolean isSuccess) {
				if(isSuccess) {
					Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.payment_success), Snackbar.LENGTH_LONG).show();
					getSkuDetails();
					updateQuotaInfo();
				}
				else{
					Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.payment_error), Snackbar.LENGTH_LONG).show();
				}
				spinner.dismiss();
			}
		})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
