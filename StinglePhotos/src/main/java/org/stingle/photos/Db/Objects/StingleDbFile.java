package org.stingle.photos.Db.Objects;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDbContract;

public class StingleDbFile {
	public long id;
	public String folderId = null;
	public String filename;
	public Boolean isLocal;
	public Boolean isRemote;
	public Integer version = GalleryTrashDb.INITIAL_VERSION;
	public Integer reupload = GalleryTrashDb.REUPLOAD_NO;
	public Long dateCreated;
	public Long dateModified;
	public String headers;

	public StingleDbFile(Cursor cursor){
		this.id = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns._ID));
		this.filename = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FILENAME));
		this.isLocal = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL)) == 1);
		this.isRemote = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE)) == 1);
		this.version = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_VERSION));
		this.reupload = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD));
		this.headers = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_HEADERS));
		this.dateCreated = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED));
		this.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED));

		try {
			this.folderId = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID));
		}
		catch (IllegalArgumentException ignored) {}
	}

	public StingleDbFile(JSONObject json) throws JSONException {
		this.folderId = json.optString("folderId");
		this.filename = json.getString("file");
		this.isLocal = null;
		this.isRemote = null;
		if(json.has("version")) {
			this.version = json.getInt("version");
		}
		this.headers = json.getString("headers");
		this.dateCreated = json.getLong("dateCreated");
		this.dateModified = json.getLong("dateModified");
	}
}
