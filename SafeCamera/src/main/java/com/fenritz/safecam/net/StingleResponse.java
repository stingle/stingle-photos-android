package com.fenritz.safecam.Net;

import android.content.Context;

import com.fenritz.safecam.Util.Helpers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class StingleResponse{

	Context context;
	JSONObject result = null;
	String status = "ok";
	JSONObject parts = null;
	JSONArray infos = null;
	JSONArray errors = null;

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
		}
		catch (JSONException e) {
			this.status = "nok";
		}
	}

	public boolean isStatusOk(){
		return (this.status.equals("ok") ? true : false);
	}

	public String get(String name){
		if(parts == null){
			return null;
		}
		return parts.optString(name);
	}

	public String getRawOutput(){
		if(this.result != null) {
			return this.result.toString();
		}
		return "";
	}

	protected void showErrorsInfos(){
		for (int i = 0; i < this.errors.length(); i++) {
			String error = this.errors.optString(i);
			if(error != null){
				Helpers.showAlertDialog(this.context, error);
			}
		}

		for (int i = 0; i < this.infos.length(); i++) {
			String info = this.infos.optString(i);
			if(info != null){
				Helpers.showInfoDialog(this.context, info);
			}
		}
	}
}
