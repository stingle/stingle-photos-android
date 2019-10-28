package org.stingle.photos.Db;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

public class StingleDbFile {
	public String filename;
	public Boolean isLocal;
	public Boolean isRemote;
	public Integer version = StingleDbHelper.INITIAL_VERSION;
	public Integer reupload = StingleDbHelper.REUPLOAD_NO;
	public Long dateCreated;
	public Long dateModified;
	public String headers;

	public StingleDbFile(Cursor cursor){
		this.filename = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_FILENAME));
		this.isLocal = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_IS_LOCAL)) == 1  ? true : false );
		this.isRemote = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_IS_REMOTE)) == 1  ? true : false );

		int index;

		if((index = cursor.getColumnIndex(StingleDbContract.Files.COLUMN_NAME_VERSION)) != -1) {
			this.version = cursor.getInt(index);
		}

		if((index = cursor.getColumnIndex(StingleDbContract.Files.COLUMN_NAME_REUPLOAD)) != -1) {
			this.reupload = cursor.getInt(index);
		}

		if((index = cursor.getColumnIndex(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED)) != -1) {
			this.dateCreated = cursor.getLong(index);
		}
		if((index = cursor.getColumnIndex(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED)) != -1) {
			this.dateModified =  cursor.getLong(index);
		}
		if((index = cursor.getColumnIndex(StingleDbContract.Files.COLUMN_NAME_HEADERS)) != -1) {
			this.headers =  cursor.getString(index);
		}
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
