package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Sync.SyncManager;

public class GalleryTrashDb implements FilesDb{

	private String tableName;
	private StingleDb db;

	public GalleryTrashDb(Context context, int set) {
		if(set == SyncManager.GALLERY) {
			this.tableName = StingleDbContract.Columns.TABLE_NAME_GALLERY;
		}
		else if(set == SyncManager.TRASH){
			this.tableName = StingleDbContract.Columns.TABLE_NAME_TRASH;
		}
		else{
			throw new RuntimeException("Invalid set");
		}
		db = new StingleDb(context);
	}


	public long insertFile(StingleDbFile file){
		return insertFile(file.filename, file.isLocal, file.isRemote, file.version, file.dateCreated, file.dateModified, file.headers);
	}

	public long insertFile(String filename, boolean isLocal, boolean isRemote, int version, long dateCreated, long dateModified, String headers){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_FILENAME, filename);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL, (isLocal ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, (isRemote ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_VERSION, version);
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, REUPLOAD_NO);

		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, dateModified);
		values.put(StingleDbContract.Columns.COLUMN_NAME_HEADERS, headers);

		return db.openWriteDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);

	}

	public int updateFile(StingleDbFile file){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL, (file.isLocal ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, (file.isRemote ? 1 : 0));

		values.put(StingleDbContract.Columns.COLUMN_NAME_VERSION, file.version);
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, file.reupload);

		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, file.dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, file.dateModified);
		values.put(StingleDbContract.Columns.COLUMN_NAME_HEADERS, file.headers);

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { file.filename };

		return db.openWriteDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public int markFileAsRemote(String filename){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, 1);

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return db.openWriteDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public int incrementVersion(String filename){

		StingleDbFile file = getFileIfExists(filename);

		if(file == null || !file.isLocal){
			return 0;
		}

		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_VERSION, file.version +1);
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, REUPLOAD_YES);

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return db.openWriteDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public int markFileAsReuploaded(String filename){

		StingleDbFile file = getFileIfExists(filename);

		if(file == null || !file.isLocal){
			return 0;
		}

		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, REUPLOAD_NO);

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return db.openWriteDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public int deleteFile(String filename){
		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return db.openWriteDb().delete(tableName, selection, selectionArgs);
	}

	public int truncateTable(){
		return db.openWriteDb().delete(tableName, null, null);
	}

	public StingleDbFile getFileIfExists(String filename){
		return getFileIfExists(filename, null);
	}

	public StingleDbFile getFileIfExists(String filename, String albumId){
		String[] projection = {
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

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = {filename};

		Cursor result = db.openReadDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				null               // The sort order
		);

		if(result.getCount() > 0){
			result.moveToNext();
			StingleDbFile dbFile = new StingleDbFile(result);
			result.close();
			return dbFile;
		}
		result.close();
		return null;
	}

	public Cursor getFilesList(int mode, int sort, String limit, String albumId){

		String[] projection = {
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

		String selection = null;

		String[] selectionArgs = null;
		switch(mode){
			case GET_MODE_ALL:

				break;
			case GET_MODE_ONLY_LOCAL:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[2];
				selectionArgs[0] = "1";
				selectionArgs[1] = "0";
				break;
			case GET_MODE_ONLY_REMOTE:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[2];
				selectionArgs[0] = "0";
				selectionArgs[1] = "1";
				break;
			case GET_MODE_LOCAL:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ?";
				selectionArgs = new String[1];
				selectionArgs[0] = "1";
				break;
			case GET_MODE_REMOTE:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[1];
				selectionArgs[0] = "1";
				break;
		}


		String sortOrder =
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		return db.openReadDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				sortOrder,               // The sort order
				limit
		);

	}

	public Cursor getReuploadFilesList(){

		String[] projection = {
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

		String selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_REUPLOAD + " = ?";

		String[] selectionArgs = {"1", "1"};

		return db.openReadDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				null               // The sort order
		);

	}

	public StingleDbFile getFileAtPosition(int pos, String albumId, int sort){
		String[] projection = {
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

		String sortOrder =
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		Cursor result = db.openReadDb().query(
				false,
				tableName,
				projection,
				null,
				null,
				null,
				null,
				sortOrder,
				String.valueOf(pos) + ", 1"
		);

		if(result.getCount() > 0){
			result.moveToNext();
			return new StingleDbFile(result);
		}
		return null;
	}

	public Integer getFilePositionByFilename(String filename){
		String query = "SELECT (SELECT COUNT(*) FROM `"+tableName+"` b WHERE a.date_created <= b.date_created) AS `position` FROM `"+tableName+"` a WHERE filename='"+filename+"'";
		Cursor cursor = db.openReadDb().rawQuery(query, null);
		if(cursor.getCount() == 1){
			cursor.moveToNext();
			return cursor.getInt(cursor.getColumnIndexOrThrow("position")) - 1;
		}
		return null;
	}

	public Cursor getAvailableDates(String albumId, int sort){
		return db.openReadDb().rawQuery("SELECT date(round(" + StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + "/1000), 'unixepoch') as `cdate`, COUNT(" + StingleDbContract.Columns.COLUMN_NAME_FILENAME + ") " +
						"FROM " + tableName + " " +
						"GROUP BY cdate " +
						"ORDER BY cdate " + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC")
				, null);

	}

	public long getTotalFilesCount(String albumId){
		return DatabaseUtils.queryNumEntries(db.openReadDb(), tableName);
	}

	public void close(){
		db.close();
	}
}

