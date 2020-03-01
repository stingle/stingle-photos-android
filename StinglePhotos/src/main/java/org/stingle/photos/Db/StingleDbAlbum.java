package org.stingle.photos.Db;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

public class StingleDbAlbum {
	public Integer id;
	public String data;
	public String albumPK;
	public Long dateCreated;

	public StingleDbAlbum(Cursor cursor){
		this.id = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Files._ID));
		this.data = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_DATA));
		this.albumPK = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_ALBUM_PK));

		int index;

		if((index = cursor.getColumnIndex(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED)) != -1) {
			this.dateCreated = cursor.getLong(index);
		}
	}

	public StingleDbAlbum(JSONObject json) throws JSONException {
		this.id = json.getInt("id");
		this.data = json.getString("data");
		this.albumPK = json.getString("albumPK");
		this.dateCreated = json.getLong("dateCreated");
	}
}
