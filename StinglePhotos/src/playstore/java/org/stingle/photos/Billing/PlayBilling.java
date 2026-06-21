package org.stingle.photos.Billing;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PlayBilling implements PurchasesUpdatedListener {

	private Activity activity;
	private BillingClient billingClient;

	private List<Purchase> purchasedSkus = new ArrayList<>();
	private Map<String, ProductDetails> productDetailsMap = new HashMap<>();
	private boolean isServiceConnected = false;
	private boolean wentToPayment = false;
	private List<String> skus = new ArrayList<>();
	private BillingEventsListener billingEventsListener;

	private static final String BASE_64_ENCODED_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA00IIe/BqA9IUbrj2y1bgvgi6Tqu37ulDOndT3v4ElDCViaZI+JFF97lvYX0ijPIZ0ryj3Jo2mC+ncXXtQKeFZsvh9EauJyQXyQbsVERGjpTZ+sMdTkYu46zRjHezl3siLWWZMuc9UFQcvU1qkMOH6MI1gic1PAXi46wuMSL+kanDyQ2UfO3VlQsVkq9o/JwZGzaA4D8NkS1Ja2JcvdLxg2ES9YLaJBL/b2inHjiZW5tO59eAy6KqZy+N6kMfaoL421AhKovocejza7g4LFkkNvqdKfLZe4CEJVhYHN2OOBsqci7KwODsyEZEv3WztqnaylnuQpDVRZztdt9qnMqKnQIDAQAB";

	public PlayBilling(Activity activity, BillingEventsListener billingEventsListener) {
		this.activity = activity;
		this.billingEventsListener = billingEventsListener;
		billingClient = BillingClient.newBuilder(activity)
				.enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
				.setListener(this)
				.build();

		initSkus();
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

	private QueryProductDetailsParams buildProductDetailsParams() {
		List<QueryProductDetailsParams.Product> products = new ArrayList<>();
		for (String sku : skus) {
			products.add(QueryProductDetailsParams.Product.newBuilder()
					.setProductId(sku)
					.setProductType(BillingClient.ProductType.SUBS)
					.build());
		}
		return QueryProductDetailsParams.newBuilder().setProductList(products).build();
	}

	@Override
	public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
		if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
			for (Purchase purchase : purchases) {
				handlePurchase(purchase);
			}
		}
	}

	public void pay(String plan) {
		Runnable queryRequest = () -> {
			getSkuDetailsAndPay(plan);
		};
		executeServiceRequest(queryRequest);
	}

	private void goToPayment(String plan) {
		if (areSubscriptionsSupported()) {
			ProductDetails productDetails = productDetailsMap.get(plan);
			if (productDetails == null) {
				if (billingEventsListener != null) {
					billingEventsListener.playBillingNotAvailable();
				}
				return;
			}

			String offerToken = getOfferToken(productDetails);
			if (offerToken == null) {
				if (billingEventsListener != null) {
					billingEventsListener.playBillingNotAvailable();
				}
				return;
			}

			wentToPayment = true;

			List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();
			productDetailsParamsList.add(BillingFlowParams.ProductDetailsParams.newBuilder()
					.setProductDetails(productDetails)
					.setOfferToken(offerToken)
					.build());

			BillingFlowParams.Builder flowParamsBuilder = BillingFlowParams.newBuilder();
			flowParamsBuilder.setProductDetailsParamsList(productDetailsParamsList);
			flowParamsBuilder.setObfuscatedAccountId(Helpers.getPreference(activity, StinglePhotosApplication.USER_ID, ""));

			if (purchasedSkus != null && purchasedSkus.size() > 0) {
				flowParamsBuilder.setSubscriptionUpdateParams(
						BillingFlowParams.SubscriptionUpdateParams.newBuilder()
								.setOldPurchaseToken(purchasedSkus.get(0).getPurchaseToken())
								.setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION)
								.build()
				);
			}

			BillingFlowParams flowParams = flowParamsBuilder.build();

			BillingResult responseCode = billingClient.launchBillingFlow(activity, flowParams);
			Log.i("responseCode", responseCode.toString());
		}
	}

	// Picks the subscription offer to purchase: prefer the base plan (no offer id),
	// otherwise fall back to the first available offer.
	private String getOfferToken(ProductDetails productDetails) {
		List<ProductDetails.SubscriptionOfferDetails> offers = productDetails.getSubscriptionOfferDetails();
		if (offers == null || offers.isEmpty()) {
			return null;
		}
		for (ProductDetails.SubscriptionOfferDetails offer : offers) {
			if (offer.getOfferId() == null) {
				return offer.getOfferToken();
			}
		}
		return offers.get(0).getOfferToken();
	}

	public void checkPlayStoreAvailability() {
		Runnable queryRequest = () -> {
			billingClient.queryProductDetailsAsync(buildProductDetailsParams(),
					(billingResult1, productDetailsList) -> {
						if (billingResult1.getResponseCode() != BillingClient.BillingResponseCode.OK || productDetailsList == null || productDetailsList.isEmpty()) {
							if (billingEventsListener != null) { billingEventsListener.playBillingNotAvailable(); }
						}
					});
		};
		executeServiceRequest(queryRequest);
	}

	private void getSkuDetailsAndPay(String plan) {
		purchasedSkus.clear();
		productDetailsMap.clear();
		billingClient.queryPurchasesAsync(
				QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
				(billingResult, list) -> {
					if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
						purchasedSkus.addAll(list);
						Log.d("purchasedItems", purchasedSkus.toString());

						billingClient.queryProductDetailsAsync(buildProductDetailsParams(),
								(billingResult1, productDetailsList) -> {
									if (billingResult1.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
										Log.d("productDetails", productDetailsList.toString());
										if (productDetailsList.isEmpty()) {
											if (billingEventsListener != null) {
												billingEventsListener.playBillingNotAvailable();
											}
										} else {
											for (ProductDetails productDetails : productDetailsList) {
												productDetailsMap.put(productDetails.getProductId(), productDetails);
											}
										}
										goToPayment(plan);
									} else {
										if (billingEventsListener != null) {
											billingEventsListener.playBillingNotAvailable();
										}
									}

								});
					} else {
						Log.e("playBilling", "Got an error response trying to query subscription purchases");
					}
				});


	}

	private void handlePurchase(final Purchase purchase) {
		if (!verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature())) {
			billingEventsListener.makeSnackBar(activity.getString(R.string.payment_error));
			billingEventsListener.refresh();
			return;
		}

		if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
			if (!purchase.isAcknowledged()) {
				AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
						.setPurchaseToken(purchase.getPurchaseToken())
						.build();
				billingClient.acknowledgePurchase(params, billingResult -> {
					Log.d("purchase", purchase.getProducts().get(0) + " - " + purchase.getPurchaseToken() + " - " + billingResult.getResponseCode());

					if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
						if (billingEventsListener != null) {
							billingEventsListener.makeSnackBar(activity.getString(R.string.payment_success));
							billingEventsListener.refresh();
						}
					} else {
						if (billingEventsListener != null) {
							billingEventsListener.refresh();
						}
					}
				});
			} else {
				if (billingEventsListener != null) {
					billingEventsListener.makeSnackBar(activity.getString(R.string.payment_success));
					billingEventsListener.refresh();
				}
			}
		} else {
			if (billingEventsListener != null) {
				billingEventsListener.makeSnackBar(activity.getString(R.string.pending_purchase));
				billingEventsListener.refresh();
			}
		}
	}

	public void startServiceConnection(final Runnable executeOnSuccess) {
		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
				if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
					isServiceConnected = true;
					if (executeOnSuccess != null) {
						executeOnSuccess.run();
					}
				} else {
					if (billingEventsListener != null) {
						billingEventsListener.playBillingNotAvailable();
					}
				}
			}

			@Override
			public void onBillingServiceDisconnected() {
				isServiceConnected = false;
			}
		});
	}

	public boolean areSubscriptionsSupported() {
		BillingResult response = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS);
		return response.getResponseCode() == BillingClient.BillingResponseCode.OK;
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

	public void destroy() {
		if (billingClient != null && billingClient.isReady()) {
			billingClient.endConnection();
			billingClient = null;
		}
	}
}
