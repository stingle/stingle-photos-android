package org.stingle.photos.Db.Objects;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Db.StingleDbContract;

public class StingleDbAlbumFile extends StingleFile {
	public Integer id;
	public Integer albumId;

	public StingleDbAlbumFile(Cursor cursor){
		this.id = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files._ID));
		this.albumId = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_ALBUM_ID));
		this.filename = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_FILENAME));
		this.isLocal = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_IS_LOCAL)) == 1);
		this.isRemote = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_IS_REMOTE)) == 1);
		this.version = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_VERSION));
		this.reupload = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_REUPLOAD));
		this.headers = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_HEADERS));
		this.dateCreated = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED));
		this.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED));
	}

	public StingleDbAlbumFile(JSONObject json) throws JSONException {
		this.id = json.getInt("id");
		this.albumId = json.getInt("albumId");
		this.filename = json.getString("filename");
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
