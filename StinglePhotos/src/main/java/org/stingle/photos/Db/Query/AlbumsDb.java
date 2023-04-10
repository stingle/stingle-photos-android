package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.DatabaseManager;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;

import java.util.ArrayList;

public class AlbumsDb {

	private final DatabaseManager db;

	private final String tableName = StingleDbContract.Columns.TABLE_NAME_ALBUMS;

	public static final int ALBUM_ID_LEN = 32;
	public static final int SORT_BY_CREATION_DATE = 0;
	public static final int SORT_BY_MODIFIED_DATE = 1;

	public AlbumsDb(Context context) {
		db = DatabaseManager.getInstance(context);
	}

	private final String[] projection = {
			StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID,
			StingleDbContract.Columns.COLUMN_NAME_ALBUM_SK,
			StingleDbContract.Columns.COLUMN_NAME_ALBUM_PK,
			StingleDbContract.Columns.COLUMN_NAME_METADATA,
			StingleDbContract.Columns.COLUMN_NAME_IS_SHARED,
			StingleDbContract.Columns.COLUMN_NAME_IS_HIDDEN,
			StingleDbContract.Columns.COLUMN_NAME_IS_OWNER,
			StingleDbContract.Columns.COLUMN_NAME_PERMISSIONS,
			StingleDbContract.Columns.COLUMN_NAME_MEMBERS,
			StingleDbContract.Columns.COLUMN_NAME_SYNC_LOCAL,
			StingleDbContract.Columns.COLUMN_NAME_IS_LOCKED,
			StingleDbContract.Columns.COLUMN_NAME_COVER,
			StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED,
			StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED
	};


	public String insertAlbum(StingleDbAlbum album) {
		if (album.albumId == null) {
			album.albumId = CryptoHelpers.getRandomString(ALBUM_ID_LEN);
		}

		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID, album.albumId);
		values.put(StingleDbContract.Columns.COLUMN_NAME_ALBUM_SK, album.encPrivateKey);
		values.put(StingleDbContract.Columns.COLUMN_NAME_ALBUM_PK, album.publicKey);

		values.put(StingleDbContract.Columns.COLUMN_NAME_METADATA, album.metadata);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_SHARED, album.isShared);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_HIDDEN, album.isHidden);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_OWNER, album.isOwner);
		values.put(StingleDbContract.Columns.COLUMN_NAME_PERMISSIONS, album.permissions);
		values.put(StingleDbContract.Columns.COLUMN_NAME_MEMBERS, album.getMembersAsString());
		values.put(StingleDbContract.Columns.COLUMN_NAME_SYNC_LOCAL, album.syncLocal);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_LOCKED, album.isLocked);
		values.put(StingleDbContract.Columns.COLUMN_NAME_COVER, album.cover);

		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, album.dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, album.dateModified);

		db.getDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);

		return album.albumId;
	}

	public int updateAlbum(StingleDbAlbum album) {
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID, album.albumId);
		values.put(StingleDbContract.Columns.COLUMN_NAME_ALBUM_SK, album.encPrivateKey);
		values.put(StingleDbContract.Columns.COLUMN_NAME_ALBUM_PK, album.publicKey);

		values.put(StingleDbContract.Columns.COLUMN_NAME_METADATA, album.metadata);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_SHARED, album.isShared);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_HIDDEN, album.isHidden);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_OWNER, album.isOwner);
		values.put(StingleDbContract.Columns.COLUMN_NAME_PERMISSIONS, album.permissions);
		values.put(StingleDbContract.Columns.COLUMN_NAME_MEMBERS, album.getMembersAsString());
		values.put(StingleDbContract.Columns.COLUMN_NAME_SYNC_LOCAL, album.syncLocal);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_LOCKED, album.isLocked);
		values.put(StingleDbContract.Columns.COLUMN_NAME_COVER, album.cover);

		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, album.dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, album.dateModified);

		String selection = StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = {String.valueOf(album.albumId)};

		return db.getDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}


	public int deleteAlbum(String albumId) {
		String selection = StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = {String.valueOf(albumId)};

		return db.getDb().delete(tableName, selection, selectionArgs);
	}

	public int truncateTable() {
		return db.getDb().delete(tableName, null, null);
	}


	public AutoCloseableCursor getAlbumsList(int sort) {
		String sortOrder =
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		return new AutoCloseableCursor(db.getDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				null,              // The columns for the WHERE clause
				null,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				sortOrder               // The sort order
		));
	}


	public StingleDbAlbum getAlbumAtPosition(int pos, int sortBy, int sort, Boolean isHidden, Boolean isShared) {
		String selection = "";
		ArrayList<String> selectionArgs = new ArrayList<>();
		if(isHidden != null) {
			selection = StingleDbContract.Columns.COLUMN_NAME_IS_HIDDEN + " = ?";
			selectionArgs.add((isHidden ? "1" : "0"));
		}
		if(isShared != null) {
			if(selection.length() > 0){
				selection += " OR ";
			}
			selection += StingleDbContract.Columns.COLUMN_NAME_IS_SHARED + " = ?";
			selectionArgs.add((isShared ? "1" : "0"));
		}

		String sortColumn = (sortBy == SORT_BY_CREATION_DATE ? StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED : StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED);

		String sortOrder = sortColumn + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		String[] selArgs = new String[selectionArgs.size()];
		for (int i = 0;i <selectionArgs.size(); i++){
			selArgs[i] = selectionArgs.get(i);
		}
		Log.d("selection", selection);

		try(AutoCloseableCursor autoCloseableCursor = new AutoCloseableCursor(db.getDb().query(
				false,
				tableName,
				projection,
				selection,
				selArgs,
				null,
				null,
				sortOrder,
				pos + ", 1"
		))) {
			Cursor result = autoCloseableCursor.getCursor();
			if (result.getCount() > 0) {
				result.moveToNext();
				return new StingleDbAlbum(result);
			}
		}
		return null;
	}

	public StingleDbAlbum getAlbumById(String albumId) {
		String selection = StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = {albumId};

		try(AutoCloseableCursor autoCloseableCursor = new AutoCloseableCursor(db.getDb().query(
				false,
				tableName,
				projection,
				selection,
				selectionArgs,
				null,
				null,
				null,
				null
		))) {
			Cursor result = autoCloseableCursor.getCursor();
			if (result.getCount() > 0) {
				result.moveToNext();
				return new StingleDbAlbum(result);
			}
		}
		return null;
	}

	public long getTotalAlbumsCount(Boolean isHidden, Boolean isShared) {
		if(isHidden != null && isShared != null) {
			String selection = StingleDbContract.Columns.COLUMN_NAME_IS_HIDDEN + " = ? OR " + StingleDbContract.Columns.COLUMN_NAME_IS_SHARED + " = ?";
			String[] selectionArgs = {(isHidden ? "1" : "0"), (isShared ? "1" : "0")};
			return DatabaseUtils.queryNumEntries(db.getDb(), tableName, selection, selectionArgs);
		}
		else if(isHidden != null) {
			String selection = StingleDbContract.Columns.COLUMN_NAME_IS_HIDDEN + " = ?";
			String[] selectionArgs = {(isHidden ? "1" : "0")};
			return DatabaseUtils.queryNumEntries(db.getDb(), tableName, selection, selectionArgs);
		}
		else {
			return DatabaseUtils.queryNumEntries(db.getDb(), tableName);
		}
	}

	public void close() {
		db.closeDb();
	}
}

