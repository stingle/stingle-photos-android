package org.stingle.photos.Billing;

class PlayBilling /*implements PurchasesUpdatedListener*/ {

	/*private Activity activity;
	private BillingClient billingClient;

	private List<Purchase> purchasedSkus = new ArrayList<>();
	private Map<String, SkuDetails> skuDetailsMap = new HashMap<>();
	private boolean isServiceConnected = false;
	private boolean wentToPayment = false;
	private List<String> skus = new ArrayList<>();

	private static final String BASE_64_ENCODED_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA00IIe/BqA9IUbrj2y1bgvgi6Tqu37ulDOndT3v4ElDCViaZI+JFF97lvYX0ijPIZ0ryj3Jo2mC+ncXXtQKeFZsvh9EauJyQXyQbsVERGjpTZ+sMdTkYu46zRjHezl3siLWWZMuc9UFQcvU1qkMOH6MI1gic1PAXi46wuMSL+kanDyQ2UfO3VlQsVkq9o/JwZGzaA4D8NkS1Ja2JcvdLxg2ES9YLaJBL/b2inHjiZW5tO59eAy6KqZy+N6kMfaoL421AhKovocejza7g4LFkkNvqdKfLZe4CEJVhYHN2OOBsqci7KwODsyEZEv3WztqnaylnuQpDVRZztdt9qnMqKnQIDAQAB";
	private int billingClientResponseCode;

	public PlayBilling(Context context){
		this.activity = context;
		billingClient = BillingClient.newBuilder(context).enablePendingPurchases().setListener(this).build();

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

	@Override
	public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
		if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
			for (Purchase purchase : purchases) {
				handlePurchase(purchase);
			}
		}
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
					flowParamsBuilder.setObfuscatedAccountId(Helpers.getPreference(activity, StinglePhotosApplication.USER_ID, ""));

					if(purchasedSkus != null && purchasedSkus.size() > 0){
						flowParamsBuilder.setOldSku(purchasedSkus.get(0).getSku(), purchasedSkus.get(0).getPurchaseToken());
					}

					BillingFlowParams flowParams = flowParamsBuilder.build();

					BillingResult responseCode = billingClient.launchBillingFlow(activity, flowParams);
					Log.e("responseCode", responseCode.toString());

				}
			}
		};
		executeServiceRequest(queryRequest);
	}

	private void getSkuDetails() {
		purchasedSkus.clear();
		skuDetailsMap.clear();
		Runnable queryRequest = () -> {
			Purchase.PurchasesResult subscriptionResult = billingClient.queryPurchases(BillingClient.SkuType.SUBS);
			if (subscriptionResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
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
						if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
							Log.d("skuDetails", skuDetailsList.toString());
							if(skuDetailsList.size() == 0){
								findViewById(R.id.paymentNotice).setVisibility(View.VISIBLE);
							}
							else {
								for (SkuDetails skuDetails : skuDetailsList) {
									skuDetailsMap.put(skuDetails.getSku(), skuDetails);
								}

								createPaymentBoxes();
							}
						}
						else{
							findViewById(R.id.paymentNotice).setVisibility(View.VISIBLE);
						}

					});
		};
		executeServiceRequest(queryRequest);
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
					Log.d("purchase", purchase.getSku() + " - " + purchase.getPurchaseToken() + " - " + billingResult.getResponseCode());

					if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
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

	public void startServiceConnection(final Runnable executeOnSuccess) {
		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
				if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
					isServiceConnected = true;
					if (executeOnSuccess != null) {
						executeOnSuccess.run();
					}
				}
				else{
					findViewById(R.id.paymentNotice).setVisibility(View.VISIBLE);
				}
				billingClientResponseCode = billingResult.getResponseCode();
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
	}*/
}
