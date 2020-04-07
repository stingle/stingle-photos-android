package org.stingle.photos.Db.Objects;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Db.StingleDbContract;

public class StingleDbFolder {
	public Integer id;
	public String folderId;
	public String data;
	public String folderPK;
	public Long dateCreated;
	public Long dateModified;

	public StingleDbFolder(Cursor cursor){
		this.id = cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns._ID));
		this.folderId = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID));
		this.data = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATA));
		this.folderPK = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FOLDER_PK));

		int index;

		if((index = cursor.getColumnIndex(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED)) != -1) {
			this.dateCreated = cursor.getLong(index);
		}
		if((index = cursor.getColumnIndex(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED)) != -1) {
			this.dateModified = cursor.getLong(index);
		}
	}

	public StingleDbFolder(JSONObject json) throws JSONException {
		this.folderId = json.getString("folderId");
		this.data = json.getString("data");
		this.folderPK = json.getString("folderPK");
		this.dateCreated = json.getLong("dateCreated");
		this.dateModified = json.getLong("dateModified");
	}
}
