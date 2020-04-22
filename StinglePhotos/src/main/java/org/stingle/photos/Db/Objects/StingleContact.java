package org.stingle.photos.Db.Objects;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Db.StingleDbContract;

public class StingleContact {
	public long userId;
	public String email;
	public String publicKey;
	public Long dateModified = null;


	public StingleContact(){

	}

	public StingleContact(Cursor cursor){
		this.userId = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_USER_ID));
		this.email = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_EMAIL));
		this.publicKey = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_PUBLIC_KEY));
		this.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED));
	}

	public StingleContact(JSONObject json) throws JSONException {
		this.userId = json.getInt("userId");
		this.email = json.getString("email");
		this.publicKey = json.getString("publicKey");
		if(json.has("dateModified")) {
			this.dateModified = json.getLong("dateModified");
		}
	}
}
