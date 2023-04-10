package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.stingle.photos.Db.DatabaseManager;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Sync.SyncManager;

public class GalleryTrashDb implements FilesDb {

	private final String tableName;
	private final DatabaseManager db;

	private final String[] projection = {
			StingleDbContract.Columns._ID,
			StingleDbContract.Columns.COLUMN_NAME_FILENAME,
			StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL,
			StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE,
			StingleDbContract.Columns.COLUMN_NAME_VERSION,
			StingleDbContract.Columns.COLUMN_NAME_REUPLOAD,
			StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED,
			StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED,
			StingleDbContract.Columns.COLUMN_NAME_HEADERS
	};

	public GalleryTrashDb(Context context, int set) {
		if (set == SyncManager.GALLERY) {
			this.tableName = StingleDbContract.Columns.TABLE_NAME_GALLERY;
		} else if (set == SyncManager.TRASH) {
			this.tableName = StingleDbContract.Columns.TABLE_NAME_TRASH;
		} else {
			throw new RuntimeException("Invalid set");
		}
		db = DatabaseManager.getInstance(context);
	}


	public long insertFile(StingleDbFile file) {
		return insertFile(file.filename, file.isLocal, file.isRemote, file.version, file.dateCreated, file.dateModified, file.headers);
	}

	public long insertFile(String filename, boolean isLocal, boolean isRemote, int version, long dateCreated, long dateModified, String headers) {
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_FILENAME, filename);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL, (isLocal ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, (isRemote ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_VERSION, version);
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, REUPLOAD_NO);

		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, dateModified);
		values.put(StingleDbContract.Columns.COLUMN_NAME_HEADERS, headers);

		return db.getDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);

	}

	public int updateFile(StingleDbFile file) {
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL, (file.isLocal ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, (file.isRemote ? 1 : 0));

		values.put(StingleDbContract.Columns.COLUMN_NAME_VERSION, file.version);
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, file.reupload);

		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, file.dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, file.dateModified);
		values.put(StingleDbContract.Columns.COLUMN_NAME_HEADERS, file.headers);

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = {file.filename};

		return db.getDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public int markFileAsRemote(String filename) {
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, 1);

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = {filename};

		return db.getDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public int incrementVersion(String filename) {

		StingleDbFile file = getFileIfExists(filename);

		if (file == null || !file.isLocal) {
			return 0;
		}

		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_VERSION, file.version + 1);
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, REUPLOAD_YES);

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = {filename};

		return db.getDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public int markFileAsReuploaded(String filename) {

		StingleDbFile file = getFileIfExists(filename);

		if (file == null || !file.isLocal) {
			return 0;
		}

		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, REUPLOAD_NO);

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = {filename};

		return db.getDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public void deleteFile(String filename) {
		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = {filename};

		db.getDb().delete(tableName, selection, selectionArgs);
	}

	public void truncateTable() {
		db.getDb().delete(tableName, null, null);
	}

	public StingleDbFile getFileIfExists(String filename) {
		return getFileIfExists(filename, null);
	}

	public StingleDbFile getFileIfExists(String filename, String albumId) {
		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = {filename};

		try (AutoCloseableCursor autoCursor = new AutoCloseableCursor(
				db.getDb().query(
						tableName,   // The table to query
						projection,             // The array of columns to return (pass null to get all)
						selection,              // The columns for the WHERE clause
						selectionArgs,          // The values for the WHERE clause
						null,                   // don't group the rows
						null,                   // don't filter by row groups
						null               // The sort order
				)
		)) {
			Cursor result = autoCursor.getCursor();
			if (result.getCount() > 0) {
				result.moveToNext();
				return new StingleDbFile(result);
			}
		}

		return null;
	}

	public AutoCloseableCursor getFilesList(int mode, int sort, String limit, String albumId) {

		String selection = null;

		String[] selectionArgs = null;
		switch (mode) {
			case GET_MODE_ALL:

				break;
			case GET_MODE_ONLY_LOCAL:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[]{"1", "0"};
				break;
			case GET_MODE_ONLY_REMOTE:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[]{"0", "1"};
				break;
			case GET_MODE_LOCAL_AND_REMOTE:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[]{"1", "1"};
				break;
			case GET_MODE_LOCAL:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ?";
				selectionArgs = new String[]{"1"};
				break;
			case GET_MODE_REMOTE:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[]{"1"};
				break;
		}

		String sortOrder = StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		Cursor result = db.getDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				sortOrder,               // The sort order
				limit
		);

		return new AutoCloseableCursor(result);
	}

	public AutoCloseableCursor getReuploadFilesList() {

		String selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_REUPLOAD + " = ?";

		String[] selectionArgs = {"1", "1"};

		return new AutoCloseableCursor(db.getDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				null               // The sort order
		));

	}

	public StingleDbFile getFileAtPosition(int pos, String albumId, int sort) {

		String sortOrder =
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		try(AutoCloseableCursor autoCursor = new AutoCloseableCursor(db.getDb().query(
				false,
				tableName,
				projection,
				null,
				null,
				null,
				null,
				sortOrder,
				pos + ", 1"
		))) {
			Cursor result = autoCursor.getCursor();
			if (result.getCount() > 0) {
				result.moveToNext();
				return new StingleDbFile(result);
			}
		}
		return null;
	}

	public int getFilePositionByFilename(String filename, String albumId, int sort) {
		String sign = (sort == StingleDb.SORT_DESC ? "<=" : ">=");
		String query = "SELECT (SELECT COUNT(*) FROM `" + tableName + "` b WHERE a.date_created " + sign + " b.date_created) AS `position` FROM `" + tableName + "` a WHERE filename='" + filename + "'";
		Log.d("query-gallery", query);
		try(AutoCloseableCursor autoCursor = new AutoCloseableCursor(db.getDb().rawQuery(query, null))) {
			Cursor cursor = autoCursor.getCursor();
			if (cursor.getCount() == 1) {
				cursor.moveToNext();
				return cursor.getInt(cursor.getColumnIndexOrThrow("position")) - 1;
			}
		}
		return 0;
	}

	public AutoCloseableCursor getAvailableDates(String albumId, int sort) {
		return new AutoCloseableCursor(db.getDb().rawQuery("SELECT date(round(" + StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + "/1000), 'unixepoch', 'localtime') as `cdate`, COUNT(" + StingleDbContract.Columns.COLUMN_NAME_FILENAME + ") " +
						"FROM " + tableName + " " +
						"GROUP BY cdate " +
						"ORDER BY cdate " + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC")
				, null));

	}

	public long getTotalFilesCount(String albumId) {
		return DatabaseUtils.queryNumEntries(db.getDb(), tableName);
	}

	public void close() {
		db.closeDb();
	}
}

