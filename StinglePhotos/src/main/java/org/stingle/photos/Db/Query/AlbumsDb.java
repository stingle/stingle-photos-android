package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;

public class AlbumsDb {

	private StingleDb db;

	private String tableName = StingleDbContract.Files.TABLE_NAME_ALBUMS;

	public AlbumsDb(Context context) {
		db = new StingleDb(context);
	}


	public long insertAlbum(String data, String albumPK, long dateCreated, long dateModified){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Files.COLUMN_NAME_DATA, data);
		values.put(StingleDbContract.Files.COLUMN_NAME_ALBUM_PK, albumPK);
		values.put(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED, dateCreated);
		values.put(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED, dateModified);

		return db.openWriteDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);

	}

	public int updateAlbum(StingleDbAlbum album){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Files.COLUMN_NAME_DATA, album.data);
		values.put(StingleDbContract.Files.COLUMN_NAME_ALBUM_PK, album.albumPK);
		values.put(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED, album.dateCreated);
		values.put(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED, album.dateModified);

		String selection = StingleDbContract.Files._ID + " = ?";
		String[] selectionArgs = { String.valueOf(album.id) };

		return db.openWriteDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}



	public int deleteAlbum(Integer id){
		String selection = StingleDbContract.Files._ID + " = ?";
		String[] selectionArgs = { String.valueOf(id) };

		return db.openWriteDb().delete(tableName, selection, selectionArgs);
	}

	public int truncateTable(){
		return db.openWriteDb().delete(tableName, null, null);
	}



	public Cursor getAlbumsList(int sort){

		String[] projection = {
				BaseColumns._ID,
				StingleDbContract.Files.COLUMN_NAME_DATA,
				StingleDbContract.Files.COLUMN_NAME_ALBUM_PK,
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED
		};

		String selection = null;

		String sortOrder =
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

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

	public StingleDbAlbum getAlbumAtPosition(int pos, int sort){
		String[] projection = {
				BaseColumns._ID,
				StingleDbContract.Files.COLUMN_NAME_DATA,
				StingleDbContract.Files.COLUMN_NAME_ALBUM_PK,
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED
		};

		String sortOrder =
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

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
			return new StingleDbAlbum(result);
		}
		return null;
	}

	public StingleDbAlbum getAlbumById(long albumId){
		String[] projection = {
				BaseColumns._ID,
				StingleDbContract.Files.COLUMN_NAME_DATA,
				StingleDbContract.Files.COLUMN_NAME_ALBUM_PK,
				StingleDbContract.Files.COLUMN_NAME_DATE_CREATED,
				StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED
		};

		String selection = StingleDbContract.Files._ID + " = ?";
		String[] selectionArgs = { String.valueOf(albumId) };

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
			return new StingleDbAlbum(result);
		}
		return null;
	}

	public long getTotalAlbumsCount(){
		return DatabaseUtils.queryNumEntries(db.openReadDb(), tableName);
	}

	public void close(){
		db.close();
	}
}

