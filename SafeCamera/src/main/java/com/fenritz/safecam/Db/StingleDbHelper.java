package com.fenritz.safecam.Db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

public class StingleDbHelper extends SQLiteOpenHelper {
	// If you change the database schema, you must increment the database version.
	public static final int DATABASE_VERSION = 1;
	public static final String DATABASE_NAME = "stingleFiles.db";

	public static final int GET_MODE_ALL = 0;
	public static final int GET_MODE_ONLY_LOCAL = 1;
	public static final int GET_MODE_ONLY_REMOTE = 2;

	protected SQLiteDatabase dbWrite;
	protected SQLiteDatabase dbRead;

	public StingleDbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(StingleDbContract.SQL_CREATE_ENTRIES);
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL(StingleDbContract.SQL_DELETE_ENTRIES);
		onCreate(db);
	}
	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}

	protected SQLiteDatabase openWriteDb(){
		if(this.dbWrite == null) {
			this.dbWrite = getWritableDatabase();
		}
		return this.dbWrite;
	}
	protected SQLiteDatabase openReadDb(){
		if(this.dbRead == null) {
			this.dbRead = getReadableDatabase();
		}
		return this.dbRead;
	}

	public long insertFile(String filename, boolean isLocal, boolean isRemote, String date){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Files.COLUMN_NAME_FILENAME, filename);
		values.put(StingleDbContract.Files.COLUMN_NAME_IS_LOCAL, (isLocal ? 1 : 0));
		values.put(StingleDbContract.Files.COLUMN_NAME_IS_REMOTE, (isRemote ? 1 : 0));

		if(date != null){
			values.put(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED, date);
			values.put(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED, date);
		}

		return openWriteDb().insertWithOnConflict(StingleDbContract.Files.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

	}

	public int updateFile(String filename, boolean isLocal, boolean isRemote, String dateCreated, String dateModified){

		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Files.COLUMN_NAME_IS_LOCAL, (isLocal ? 1 : 0));
		values.put(StingleDbContract.Files.COLUMN_NAME_IS_REMOTE, (isRemote ? 1 : 0));

		if(dateCreated != null){
			values.put(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED, dateCreated);
		}
		if(dateModified != null){
			values.put(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED, dateModified);
		}

		String selection = StingleDbContract.Files.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return openWriteDb().update(
				StingleDbContract.Files.TABLE_NAME,
				values,
				selection,
				selectionArgs);
	}

	public int deleteFile(String filename){
		String selection = StingleDbContract.Files.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return openWriteDb().delete(StingleDbContract.Files.TABLE_NAME, selection, selectionArgs);
	}

	public Cursor getFilesList(int mode){

		String[] projection = {
				BaseColumns._ID,
				StingleDbContract.Files.COLUMN_NAME_FILENAME,
				StingleDbContract.Files.COLUMN_NAME_IS_LOCAL,
				StingleDbContract.Files.COLUMN_NAME_IS_REMOTE
		};

		String selection = null;

		String[] selectionArgs = new String[2];
		switch(mode){
			case GET_MODE_ALL:
				selectionArgs = null;
				break;
			case GET_MODE_ONLY_LOCAL:
				selection = StingleDbContract.Files.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Files.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs[0] = "1";
				selectionArgs[1] = "0";
				break;
			case GET_MODE_ONLY_REMOTE:
				selection = StingleDbContract.Files.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Files.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs[0] = "0";
				selectionArgs[1] = "1";
				break;
		}


		String sortOrder =
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED + " DESC";

		return openReadDb().query(
				StingleDbContract.Files.TABLE_NAME,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				sortOrder               // The sort order
		);

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

