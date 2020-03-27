package org.stingle.photos.Db.Objects;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Db.StingleDbContract;

public class StingleDbFile extends StingleFile {

	public StingleDbFile(Cursor cursor){
		this.filename = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FILENAME));
		this.isLocal = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL)) == 1);
		this.isRemote = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE)) == 1);
		this.version = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_VERSION));
		this.reupload = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD));
		this.headers = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_HEADERS));
		this.dateCreated = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED));
		this.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED));
	}

	public StingleDbFile(JSONObject json) throws JSONException {
		this.filename = json.getString("file");
		this.isLocal = null;
		this.isRemote = null;
		if(json.has("version")) {
			this.version = json.getInt("version");
		}
		this.dateCreated = json.getLong("dateCreated");
		this.dateModified = json.getLong("dateModified");
		this.headers = json.getString("headers");
	}
}
