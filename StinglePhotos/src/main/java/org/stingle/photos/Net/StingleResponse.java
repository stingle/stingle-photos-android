package org.stingle.photos.Net;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;

public class StingleResponse{

	private Context context;
	private JSONObject result = null;
	private String status = "ok";
	private JSONObject parts = null;
	private JSONArray infos = null;
	private JSONArray errors = null;
	private boolean isLoggedOut = false;

	public StingleResponse(Context context, JSONObject result){
		this(context, result, true);
	}

	public StingleResponse(Context context, JSONObject result, boolean showErrorInfos){
		this.context = context;
		this.result = result;

		if(result == null){
			this.status = "nok";
			return;
		}

		try {
			if (result.has("status")) {
				this.status = result.getString("status");
			}

			if (result.has("parts")) {
				this.parts = result.optJSONObject("parts");
			}

			if (result.has("infos")) {
				this.infos = result.optJSONArray("infos");
			}

			if (result.has("errors")) {
				this.errors = result.optJSONArray("errors");
			}

			if(showErrorInfos) {
				this.showErrorsInfos();
			}

			String logout = get("logout");
			if(logout != null && logout.length() > 0){
				LoginManager.logoutLocally(context);
				this.status = "nok";
				isLoggedOut = true;
			}
		}
		catch (JSONException e) {
			this.status = "nok";
		}
	}

	public boolean isStatusOk(){
		return this.status.equals("ok");
	}

	public String get(String name){
		if(parts == null){
			return null;
		}
		return parts.optString(name);
	}

	public JSONArray getArray(String name){
		if(parts == null){
			return null;
		}
		return parts.optJSONArray(name);
	}

	public JSONObject getObject(String name){
		if(parts == null){
			return null;
		}
		return parts.optJSONObject(name);
	}

	public String getRawOutput(){
		if(this.result != null) {
			return this.result.toString();
		}
		return "";
	}

	public void showErrorsInfos(){
		showErrors();
		showInfos();
	}

	public void showErrors(){
		StringBuilder errorsBuilder = new StringBuilder();
		for (int i = 0; i < this.errors.length(); i++) {
			String error = this.errors.optString(i);
			if(error != null){
				errorsBuilder.append(error + "\n");
			}
		}
		if(errorsBuilder.length() > 0) {
			new Handler(Looper.getMainLooper()).post(() -> Helpers.showAlertDialog(context, context.getString(R.string.error), errorsBuilder.toString()));
		}
	}

	public void showInfos(){
		StringBuilder infoBuilder = new StringBuilder();
		for (int i = 0; i < this.infos.length(); i++) {
			String info = this.infos.optString(i);
			if(info != null){
				infoBuilder.append(info + "\n");
			}
		}
		if(infoBuilder.length() > 0) {
			new Handler(Looper.getMainLooper()).post(() -> Helpers.showInfoDialog(context, null, infoBuilder.toString()));
		}
	}

	public int getErrorCount(){
		if(this.errors != null) {
			return this.errors.length();
		}
		return 0;
	}
	public int getInfoCount(){
		if(this.infos != null) {
			return this.infos.length();
		}
		return 0;
	}

	public boolean areThereErrorInfos(){
		if(getErrorCount() + getInfoCount() > 0){
			return true;
		}
		return false;
	}

	public boolean isLoggedOut() {
		return isLoggedOut;
	}
}
