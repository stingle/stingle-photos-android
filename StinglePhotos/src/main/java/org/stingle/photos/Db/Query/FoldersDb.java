package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleDbFolder;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;

public class FoldersDb {

	private StingleDb db;

	private String tableName = StingleDbContract.Columns.TABLE_NAME_FOLDERS;

	public static final int FOLDER_ID_LEN = 32;

	public FoldersDb(Context context) {
		db = new StingleDb(context);
	}


	public String insertFolder(StingleDbFolder folder){
		return insertFolder(folder.folderId, folder.data, folder.folderPK, folder.dateCreated, folder.dateModified);
	}

	public String insertFolder(String folderId, String data, String folderPK, long dateCreated, long dateModified){
		if(folderId == null){
			folderId = CryptoHelpers.getRandomString(FOLDER_ID_LEN);
		}

		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATA, data);
		values.put(StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID, folderId);
		values.put(StingleDbContract.Columns.COLUMN_NAME_FOLDER_PK, folderPK);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, dateModified);

		db.openWriteDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);

		return folderId;
	}

	public int updateFolder(StingleDbFolder folder){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID, folder.folderId);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATA, folder.data);
		values.put(StingleDbContract.Columns.COLUMN_NAME_FOLDER_PK, folder.folderPK);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, folder.dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, folder.dateModified);

		String selection = StingleDbContract.Columns._ID + " = ?";
		String[] selectionArgs = { String.valueOf(folder.id) };

		return db.openWriteDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}



	public int deleteFolder(String folderId){
		String selection = StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID + " = ?";
		String[] selectionArgs = { String.valueOf(folderId) };

		return db.openWriteDb().delete(tableName, selection, selectionArgs);
	}

	public int truncateTable(){
		return db.openWriteDb().delete(tableName, null, null);
	}



	public Cursor getFoldersList(int sort){

		String[] projection = {
				BaseColumns._ID,
				StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID,
				StingleDbContract.Columns.COLUMN_NAME_DATA,
				StingleDbContract.Columns.COLUMN_NAME_FOLDER_PK,
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED
		};

		String selection = null;

		String sortOrder =
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		return db.openReadDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				null,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				sortOrder               // The sort order
		);

	}

	public StingleDbFolder getFolderAtPosition(int pos, int sort){
		String[] projection = {
				BaseColumns._ID,
				StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID,
				StingleDbContract.Columns.COLUMN_NAME_DATA,
				StingleDbContract.Columns.COLUMN_NAME_FOLDER_PK,
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED
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
			return new StingleDbFolder(result);
		}
		return null;
	}

	public StingleDbFolder getFolderById(String folderId){
		String[] projection = {
				BaseColumns._ID,
				StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID,
				StingleDbContract.Columns.COLUMN_NAME_DATA,
				StingleDbContract.Columns.COLUMN_NAME_FOLDER_PK,
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED
		};

		String selection = StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID + " = ?";
		String[] selectionArgs = { folderId };

		Cursor result = db.openReadDb().query(
				false,
				tableName,
				projection,
				selection,
				selectionArgs,
				null,
				null,
				null,
				null
		);

		if(result.getCount() > 0){
			result.moveToNext();
			return new StingleDbFolder(result);
		}
		return null;
	}

	public long getTotalFoldersCount(){
		return DatabaseUtils.queryNumEntries(db.openReadDb(), tableName);
	}

	public void close(){
		db.close();
	}
}

