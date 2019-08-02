package com.fenritz.safecam.Db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

public class StingleDbHelper extends SQLiteOpenHelper {
	// If you change the database schema, you must increment the database version.
	public static final int DATABASE_VERSION = 1;
	public static final String DATABASE_NAME = "stingleFiles.db";

	public static final int GET_MODE_ALL = 0;
	public static final int GET_MODE_ONLY_LOCAL = 1;
	public static final int GET_MODE_ONLY_REMOTE = 2;
	public static final int GET_MODE_LOCAL = 3;
	public static final int GET_MODE_REMOTE = 4;

	public static final int SORT_ASC = 0;
	public static final int SORT_DESC = 1;

	public static final int INITIAL_VERSION = 1;

	public static final int REUPLOAD_NO = 0;
	public static final int REUPLOAD_YES = 1;

	protected SQLiteDatabase dbWrite;
	protected SQLiteDatabase dbRead;

	protected String tableName;

	public StingleDbHelper(Context context, String tableName) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);

		this.tableName = tableName;
	}
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(StingleDbContract.SQL_CREATE_FILES);
		db.execSQL(StingleDbContract.SQL_CREATE_TRASH);
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL(StingleDbContract.SQL_DELETE_FILES);
		db.execSQL(StingleDbContract.SQL_DELETE_TRASH);
		onCreate(db);
	}
	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}

	protected SQLiteDatabase openWriteDb(){
		if(this.dbWrite == null || !this.dbWrite.isOpen()) {
			this.dbWrite = getWritableDatabase();
		}
		return this.dbWrite;
	}
	protected SQLiteDatabase openReadDb(){
		if(this.dbRead == null || !this.dbRead.isOpen()) {
			this.dbRead = getReadableDatabase();
		}
		return this.dbRead;
	}

	public long insertFile(StingleDbFile file){
		return insertFile(file.filename, file.isLocal, file.isRemote, file.version, file.dateCreated, file.dateModified);
	}

	public long insertFile(String filename, boolean isLocal, boolean isRemote, int version, long dateCreated, long dateModified){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Files.COLUMN_NAME_FILENAME, filename);
		values.put(StingleDbContract.Files.COLUMN_NAME_IS_LOCAL, (isLocal ? 1 : 0));
		values.put(StingleDbContract.Files.COLUMN_NAME_IS_REMOTE, (isRemote ? 1 : 0));
		values.put(StingleDbContract.Files.COLUMN_NAME_VERSION, version);
		values.put(StingleDbContract.Files.COLUMN_NAME_REUPLOAD, REUPLOAD_NO);

		values.put(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED, dateCreated);
		values.put(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED, dateModified);

		return openWriteDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);

	}

	public int updateFile(StingleDbFile file){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Files.COLUMN_NAME_IS_LOCAL, (file.isLocal ? 1 : 0));
		values.put(StingleDbContract.Files.COLUMN_NAME_IS_REMOTE, (file.isRemote ? 1 : 0));

		values.put(StingleDbContract.Files.COLUMN_NAME_VERSION, file.version);
		values.put(StingleDbContract.Files.COLUMN_NAME_REUPLOAD, file.reupload);

		values.put(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED, file.dateCreated);
		values.put(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED, file.dateModified);

		String selection = StingleDbContract.Files.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { file.filename };

		return openWriteDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public int markFileAsRemote(String filename){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Files.COLUMN_NAME_IS_REMOTE, 1);

		String selection = StingleDbContract.Files.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return openWriteDb().update(
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
		values.put(StingleDbContract.Files.COLUMN_NAME_VERSION, file.version +1);
		values.put(StingleDbContract.Files.COLUMN_NAME_REUPLOAD, REUPLOAD_YES);

		String selection = StingleDbContract.Files.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return openWriteDb().update(
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
		values.put(StingleDbContract.Files.COLUMN_NAME_REUPLOAD, REUPLOAD_NO);

		String selection = StingleDbContract.Files.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return openWriteDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public int deleteFile(String filename){
		String selection = StingleDbContract.Files.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return openWriteDb().delete(tableName, selection, selectionArgs);
	}

	public StingleDbFile getFileIfExists(String filename){
		String[] projection = {
				StingleDbContract.Files.COLUMN_NAME_FILENAME,
				StingleDbContract.Files.COLUMN_NAME_IS_LOCAL,
				StingleDbContract.Files.COLUMN_NAME_IS_REMOTE,
				StingleDbContract.Files.COLUMN_NAME_VERSION,
				StingleDbContract.Files.COLUMN_NAME_REUPLOAD,
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED
		};

		String selection = StingleDbContract.Files.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = {filename};

		Cursor result = openReadDb().query(
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
			return new StingleDbFile(result);
		}
		return null;
	}

	public Cursor getFilesList(int mode, int sort){

		String[] projection = {
				BaseColumns._ID,
				StingleDbContract.Files.COLUMN_NAME_FILENAME,
				StingleDbContract.Files.COLUMN_NAME_IS_LOCAL,
				StingleDbContract.Files.COLUMN_NAME_IS_REMOTE,
				StingleDbContract.Files.COLUMN_NAME_VERSION,
				StingleDbContract.Files.COLUMN_NAME_REUPLOAD,
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED
		};

		String selection = null;

		String[] selectionArgs = null;
		switch(mode){
			case GET_MODE_ALL:

				break;
			case GET_MODE_ONLY_LOCAL:
				selection = StingleDbContract.Files.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Files.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[2];
				selectionArgs[0] = "1";
				selectionArgs[1] = "0";
				break;
			case GET_MODE_ONLY_REMOTE:
				selection = StingleDbContract.Files.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Files.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[2];
				selectionArgs[0] = "0";
				selectionArgs[1] = "1";
				break;
			case GET_MODE_LOCAL:
				selection = StingleDbContract.Files.COLUMN_NAME_IS_LOCAL + " = ?";
				selectionArgs = new String[1];
				selectionArgs[0] = "1";
				break;
			case GET_MODE_REMOTE:
				selection = StingleDbContract.Files.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs = new String[1];
				selectionArgs[0] = "1";
				break;
		}


		String sortOrder =
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED + (sort == SORT_DESC ? " DESC" : " ASC");

		return openReadDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				sortOrder               // The sort order
		);

	}

	public Cursor getReuploadFilesList(){

		String[] projection = {
				BaseColumns._ID,
				StingleDbContract.Files.COLUMN_NAME_FILENAME,
				StingleDbContract.Files.COLUMN_NAME_IS_LOCAL,
				StingleDbContract.Files.COLUMN_NAME_IS_REMOTE,
				StingleDbContract.Files.COLUMN_NAME_VERSION,
				StingleDbContract.Files.COLUMN_NAME_REUPLOAD,
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED
		};

		String selection = StingleDbContract.Files.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Files.COLUMN_NAME_REUPLOAD + " = ?";

		String[] selectionArgs = {"1", "1"};

		return openReadDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				null               // The sort order
		);

	}

	public StingleDbFile getFileAtPosition(int pos){
		String[] projection = {
				StingleDbContract.Files.COLUMN_NAME_FILENAME,
				StingleDbContract.Files.COLUMN_NAME_IS_LOCAL,
				StingleDbContract.Files.COLUMN_NAME_IS_REMOTE,
				StingleDbContract.Files.COLUMN_NAME_VERSION,
				StingleDbContract.Files.COLUMN_NAME_REUPLOAD
		};

		String sortOrder =
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED + " DESC";

		Cursor result = openReadDb().query(
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

	public Cursor getAvailableDates(){
		return openReadDb().rawQuery("SELECT date(round(" + StingleDbContract.Files.COLUMN_NAME_DATE_CREATED + "/1000), 'unixepoch') as `cdate`, COUNT(" + StingleDbContract.Files.COLUMN_NAME_FILENAME + ") " +
						"FROM " + tableName + " " +
						"GROUP BY cdate " +
						"ORDER BY cdate DESC"
				, null);

	}

	public long getTotalFilesCount(){
		return DatabaseUtils.queryNumEntries(openReadDb(), tableName);
	}

	public void close(){
		if(this.dbWrite != null) {
			this.dbWrite.close();
		}
		if(this.dbRead != null) {
			this.dbRead.close();
		}
	}
}

