package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;

public class ImportedIdsDb {

	private StingleDb db;

	private String tableName = StingleDbContract.Columns.TABLE_NAME_IMPORTED_IDS;

	private final static int cleanupLimit = 500;

	public ImportedIdsDb(Context context) {
		db = new StingleDb(context);
	}

	private String[] projection = {
			StingleDbContract.Columns._ID,
			StingleDbContract.Columns.COLUMN_NAME_MEDIA_ID
	};


	public Long insertImportedId(long id) {
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_MEDIA_ID, id);

		return db.openWriteDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);
	}

	public boolean isIdExists(long id){
		String selection = StingleDbContract.Columns.COLUMN_NAME_MEDIA_ID + " = ?";
		String[] selectionArgs = {String.valueOf(id)};
		return DatabaseUtils.queryNumEntries(db.openReadDb(), tableName, selection, selectionArgs) != 0;
	}

	public void cleanUpIds() {
		if(DatabaseUtils.queryNumEntries(db.openReadDb(), tableName) > cleanupLimit) {
			db.openReadDb().rawQuery("DELETE FROM " + tableName + " WHERE _id in (SELECT _id FROM " + tableName + " ORDER BY _id ASC LIMIT " + cleanupLimit + ")", null);
		}
	}

	public int truncateTable() {
		return db.openWriteDb().delete(tableName, null, null);
	}

	public void close() {
		cleanUpIds();
		db.close();
	}
}

