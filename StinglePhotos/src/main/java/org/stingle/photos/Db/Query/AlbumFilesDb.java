package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import org.stingle.photos.Db.Objects.StingleDbAlbumFile;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;

public class AlbumFilesDb implements FilesDb {

	private StingleDb db;

	private String tableName = StingleDbContract.Columns.TABLE_NAME_ALBUM_FILES;

	public AlbumFilesDb(Context context) {
		db = new StingleDb(context);
	}


	public long insertAlbumFile(String albumId, String filename, boolean isLocal, boolean isRemote, int version, String headers, long dateCreated, long dateModified){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID, albumId);
		values.put(StingleDbContract.Columns.COLUMN_NAME_FILENAME, filename);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL, (isLocal ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, (isRemote ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_VERSION, version);
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, FilesTrashDb.REUPLOAD_NO);
		values.put(StingleDbContract.Columns.COLUMN_NAME_HEADERS, headers);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, dateModified);

		return db.openWriteDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);

	}

	public int updateAlbumFile(StingleDbAlbumFile file){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID, file.albumId);
		values.put(StingleDbContract.Columns.COLUMN_NAME_FILENAME, file.filename);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL, (file.isLocal ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, (file.isRemote ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_VERSION, file.version);
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, file.reupload);
		values.put(StingleDbContract.Columns.COLUMN_NAME_HEADERS, file.headers);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, file.dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, file.dateModified);

		String selection = StingleDbContract.Columns._ID + " = ?";
		String[] selectionArgs = { String.valueOf(file.id) };

		return db.openWriteDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}



	public int deleteAlbumFile(Long id){
		String selection = StingleDbContract.Columns._ID + " = ?";
		String[] selectionArgs = { String.valueOf(id) };

		return db.openWriteDb().delete(tableName, selection, selectionArgs);
	}

	public int truncateTable(){
		return db.openWriteDb().delete(tableName, null, null);
	}



	public Cursor getAlbumFilesList(String albumId, int sort, String limit){

		String[] projection = {
				StingleDbContract.Columns._ID,
				StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID,
				StingleDbContract.Columns.COLUMN_NAME_FILENAME,
				StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL,
				StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE,
				StingleDbContract.Columns.COLUMN_NAME_VERSION,
				StingleDbContract.Columns.COLUMN_NAME_REUPLOAD,
				StingleDbContract.Columns.COLUMN_NAME_HEADERS,
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED
		};

		String selection = StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = { albumId };

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

	public StingleDbAlbumFile getFileAtPosition(int pos, String albumId, int sort){
		String[] projection = {
				StingleDbContract.Columns._ID,
				StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID,
				StingleDbContract.Columns.COLUMN_NAME_FILENAME,
				StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL,
				StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE,
				StingleDbContract.Columns.COLUMN_NAME_VERSION,
				StingleDbContract.Columns.COLUMN_NAME_REUPLOAD,
				StingleDbContract.Columns.COLUMN_NAME_HEADERS,
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED
		};

		String selection = StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = { albumId };

		String sortOrder =
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		Cursor result = db.openReadDb().query(
				false,
				tableName,
				projection,
				selection,
				selectionArgs,
				null,
				null,
				sortOrder,
				String.valueOf(pos) + ", 1"
		);

		if(result.getCount() > 0){
			result.moveToNext();
			return new StingleDbAlbumFile(result);
		}
		return null;
	}

	public Cursor getAvailableDates(String albumId, int sort){
		return db.openReadDb().rawQuery("SELECT date(round(" + StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + "/1000), 'unixepoch') as `cdate`, COUNT(" + StingleDbContract.Columns.COLUMN_NAME_FILENAME + ") " +
						"FROM " + tableName + " " +
						"WHERE album_id='" + albumId + "' " +
						"GROUP BY cdate " +
						"ORDER BY cdate " + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC")
				, null);

	}

	public long getTotalFilesCount(String albumId){
		String selection = StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = { albumId };
		return DatabaseUtils.queryNumEntries(db.openReadDb(), tableName, selection, selectionArgs);
	}

	public void close(){
		db.close();
	}
}

