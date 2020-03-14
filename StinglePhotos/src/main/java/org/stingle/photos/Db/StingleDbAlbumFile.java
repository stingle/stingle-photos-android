package org.stingle.photos.Db;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

public class StingleDbAlbumFile {
	public Integer id;
	public Integer albumId;
	public String filename;
	public String headers;
	public Long dateCreated;
	public Long dateModified;

	public StingleDbAlbumFile(Cursor cursor){
		this.id = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files._ID));
		this.albumId = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_ALBUM_ID));
		this.filename = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_FILENAME));
		this.headers = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_HEADERS));

		int index;

		if((index = cursor.getColumnIndex(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED)) != -1) {
			this.dateCreated = cursor.getLong(index);
		}
		if((index = cursor.getColumnIndex(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED)) != -1) {
			this.dateModified = cursor.getLong(index);
		}
	}

	public StingleDbAlbumFile(JSONObject json) throws JSONException {
		this.id = json.getInt("id");
		this.albumId = json.getInt("albumId");
		this.filename = json.getString("filename");
		this.headers = json.getString("headers");
		this.dateCreated = json.getLong("dateCreated");
		this.dateModified = json.getLong("dateModified");
	}
}
