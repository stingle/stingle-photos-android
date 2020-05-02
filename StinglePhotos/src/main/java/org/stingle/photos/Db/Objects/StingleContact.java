package org.stingle.photos.Db.Objects;

import android.database.Cursor;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Db.StingleDbContract;

import java.util.Objects;

public class StingleContact {
	public long userId;
	public String email;
	public String publicKey;
	public Long dateUsed = null;
	public Long dateModified = null;


	public StingleContact(){

	}

	public StingleContact(Cursor cursor){
		this.userId = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_USER_ID));
		this.email = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_EMAIL));
		this.publicKey = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_PUBLIC_KEY));
		this.dateUsed = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_USED));
		this.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED));
	}

	public StingleContact(JSONObject json) throws JSONException {
		this.userId = json.getInt("userId");
		this.email = json.getString("email");
		this.publicKey = json.getString("publicKey");
		if(json.has("dateUsed")) {
			this.dateUsed = json.getLong("dateUsed");
		}
		if(json.has("dateModified")) {
			this.dateModified = json.getLong("dateModified");
		}
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		// self check
		if (this == obj) return true;
		// null check
		if (obj == null) return false;
		// type check and cast
		if (getClass() != obj.getClass()) return false;
		// field comparison
		StingleContact contact = (StingleContact) obj;
		return Objects.equals(userId, contact.userId);
	}
}
