package org.stingle.photos.Billing;


import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.R;


public class WebBillingDialogFragment extends AppCompatDialogFragment {


	private Toolbar toolbar;
	private String url;
	private WebView webView;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(AppCompatDialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		Context contextThemeWrapper = new ContextThemeWrapper(getActivity(), R.style.AppTheme);
		LayoutInflater localInflater = inflater.cloneInContext(contextThemeWrapper);
		View view = localInflater.inflate(R.layout.fragment_web_billing, container, false);

		toolbar = view.findViewById(R.id.toolbar);
		toolbar.setNavigationIcon(R.drawable.ic_close);
		toolbar.setNavigationOnClickListener(view1 -> dismiss());
		toolbar.setTitle(getString(R.string.purchase_subscription));

		webView = view.findViewById(R.id.webview);
		/*webView.setWebViewClient(new WebViewClient(){
			@Override
			public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
				handler.proceed();

			}
		});*/

		if(url != null){
			webView.loadUrl(url);
		}

		return view;
	}


	public void setUrl(String url){
		this.url = url;
	}

	public void showSnack(String message){
		Snackbar.make(requireDialog().findViewById(R.id.drawer_layout), message, Snackbar.LENGTH_LONG).show();
	}

}
