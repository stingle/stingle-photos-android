package org.stingle.photos;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
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

import org.stingle.photos.Billing.BillingSecurity;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.security.Security;
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

		updateQuotaInfo();

		findViewById(R.id.pay).setOnClickListener(getPayOnClickListener());

		billingClient = BillingClient.newBuilder(this).enablePendingPurchases().setListener(this).build();

		addDefaultPrices();
		getSkuDetails();
	}

	private void addDefaultPrices() {
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

	private View.OnClickListener getPayOnClickListener() {
		return new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				pay();
			}
		};
	}

	private void getSkuDetails() {
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
								}

							}
						});
			}
		};
		executeServiceRequest(queryRequest);
	}

	private void pay() {
		Runnable queryRequest = new Runnable() {
			@Override
			public void run() {

				if (areSubscriptionsSupported()) {
					SkuDetails onetb = skuDetailsMap.get("1tb_monthly");
					BillingFlowParams flowParams = BillingFlowParams.newBuilder()
							.setSkuDetails(onetb)
							.build();
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

		if(!purchase.isAcknowledged()){
			AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
			billingClient.acknowledgePurchase(params, new AcknowledgePurchaseResponseListener() {
				@Override
				public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
					Log.d("purchase", purchase.getPurchaseToken() + " - " + billingResult.getResponseCode());
				}
			});
		}
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
