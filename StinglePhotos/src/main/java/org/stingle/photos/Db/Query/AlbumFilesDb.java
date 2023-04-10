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
import org.stingle.photos.Files.FileManager;

import java.util.ArrayList;

public class AlbumFilesDb implements FilesDb {

	private final DatabaseManager db;

	private final String tableName = StingleDbContract.Columns.TABLE_NAME_ALBUM_FILES;

	private final String[] projection = {
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

	public AlbumFilesDb(Context context) {
		db = DatabaseManager.getInstance(context);
	}

	public long insertFile(StingleDbFile file){
		return insertAlbumFile(file.albumId, file.filename, file.isLocal, file.isRemote, file.version,file.headers, file.dateCreated, file.dateModified);
	}

	public long insertAlbumFile(String albumId, String filename, boolean isLocal, boolean isRemote, int version, String headers, long dateCreated, long dateModified){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID, albumId);
		values.put(StingleDbContract.Columns.COLUMN_NAME_FILENAME, filename);
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL, (isLocal ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, (isRemote ? 1 : 0));
		values.put(StingleDbContract.Columns.COLUMN_NAME_VERSION, version);
		values.put(StingleDbContract.Columns.COLUMN_NAME_REUPLOAD, GalleryTrashDb.REUPLOAD_NO);
		values.put(StingleDbContract.Columns.COLUMN_NAME_HEADERS, headers);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED, dateCreated);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, dateModified);

		return db.getDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);

	}

	public int updateFile(StingleDbFile file){
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

		return db.getDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}



	public void deleteAlbumFile(Long id){
		String selection = StingleDbContract.Columns._ID + " = ?";
		String[] selectionArgs = { String.valueOf(id) };

		db.getDb().delete(tableName, selection, selectionArgs);
	}
	public void deleteAlbumFile(String filename, String albumId){
		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = { filename, albumId };

		db.getDb().delete(tableName, selection, selectionArgs);
	}

	public void deleteAllFilesInAlbum(String albumId){
		String selection = StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = { albumId };

		db.getDb().delete(tableName, selection, selectionArgs);
	}

	public void deleteAlbumFilesIfNotNeeded(Context context, String albumId){
		try(AutoCloseableCursor autoCloseableCursor = getFilesList(AlbumFilesDb.GET_MODE_REMOTE, StingleDb.SORT_ASC, null, albumId)) {
			Cursor result = autoCloseableCursor.getCursor();
			while (result.moveToNext()) {
				StingleDbFile file = new StingleDbFile(result);
				if (!isFileExistsInOtherAlbums(file.filename, albumId)) {
					FileManager.deleteLocalFile(context, file.filename);
				}
			}
		}
	}

	public void truncateTable(){
		db.getDb().delete(tableName, null, null);
	}


	public AutoCloseableCursor getFilesList(int mode, int sort, String limit, String albumId){

		String selection = "";

		ArrayList<String> selectionArgs = new ArrayList<>();

		switch(mode){
			case GET_MODE_ALL:

				break;
			case GET_MODE_ONLY_LOCAL:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs.add("1");
				selectionArgs.add("0");
				break;
			case GET_MODE_ONLY_REMOTE:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs.add("0");
				selectionArgs.add("1");
				break;
			case GET_MODE_LOCAL_AND_REMOTE:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs.add("1");
				selectionArgs.add("1");
				break;
			case GET_MODE_LOCAL:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_LOCAL + " = ?";
				selectionArgs.add("1");
				break;
			case GET_MODE_REMOTE:
				selection = StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE + " = ?";
				selectionArgs.add("1");
				break;
		}

		if(albumId != null) {
			if (selection.length() > 0) {
				selection += " AND ";
			}
			selection += StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
			selectionArgs.add(albumId);
		}


		String sortOrder =
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		String[] selArgs = new String[selectionArgs.size()];
		for (int i = 0;i <selectionArgs.size(); i++){
			selArgs[i] = selectionArgs.get(i);
		}

		return new AutoCloseableCursor(db.getDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				sortOrder,               // The sort order
				limit
		));
	}

	public int getFilePositionByFilename(String filename, String albumId, int sort){
		String sign = (sort == StingleDb.SORT_DESC ? "<=" : ">=");

		String query = "SELECT (SELECT COUNT(*) FROM `"+tableName+"` b WHERE a.date_created "+sign+" b.date_created AND album_id='"+albumId+"') AS `position` FROM `"+tableName+"` a WHERE filename='"+filename+"' AND album_id='"+albumId+"'";
		Log.d("query-albumf", query);
		try(AutoCloseableCursor autoCloseableCursor = new AutoCloseableCursor(db.getDb().rawQuery(query, null))) {
			Cursor cursor = autoCloseableCursor.getCursor();
			if (cursor.getCount() == 1) {
				cursor.moveToNext();
				return cursor.getInt(cursor.getColumnIndexOrThrow("position")) - 1;
			}
		}
		return 0;
	}

	public AutoCloseableCursor getReuploadFilesList(){

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

	public int markFileAsRemote(String filename){
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_IS_REMOTE, 1);

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		String[] selectionArgs = { filename };

		return db.getDb().update(
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

		return db.getDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}

	public StingleDbFile getFileIfExists(String filename){
		return getFileIfExists(filename, null);
	}

	public StingleDbFile getFileIfExists(String filename, String albumId){

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ?";
		if(albumId != null) {
			selection += " AND " + StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		}
		String[] selectionArgs;
		if(albumId != null) {
			selectionArgs = new String[2];
			selectionArgs[0] = filename;
			selectionArgs[1] = albumId;
		}
		else{
			selectionArgs = new String[1];
			selectionArgs[0] = filename;
		}

		try(AutoCloseableCursor autoCloseableCursor = new AutoCloseableCursor(db.getDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				null               // The sort order
		))) {
			Cursor result = autoCloseableCursor.getCursor();
			if (result.getCount() > 0) {
				result.moveToNext();
				StingleDbFile dbFile = new StingleDbFile(result);
				result.close();
				return dbFile;
			}
		}
		return null;
	}

	public boolean isFileExistsInOtherAlbums(String filename, String albumId){

		String selection = StingleDbContract.Columns.COLUMN_NAME_FILENAME + " = ? AND " + StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " <> ?";
		String[] selectionArgs = {filename, albumId};

		long count = DatabaseUtils.queryNumEntries(db.getDb(), tableName, selection, selectionArgs);

		return count > 0;
	}

	public StingleDbFile getFileAtPosition(int pos, String albumId, int sort){

		String selection = StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = { albumId };

		String sortOrder =
				StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		try(AutoCloseableCursor autoCloseableCursor = new AutoCloseableCursor(db.getDb().query(
				false,
				tableName,
				projection,
				selection,
				selectionArgs,
				null,
				null,
				sortOrder,
				pos + ", 1"
		))) {
			Cursor result = autoCloseableCursor.getCursor();
			if (result.getCount() > 0) {
				result.moveToNext();
				return new StingleDbFile(result);
			}
		}
		return null;
	}

	public AutoCloseableCursor getAvailableDates(String albumId, int sort){
		return new AutoCloseableCursor(db.getDb().rawQuery("SELECT date(round(" + StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + "/1000), 'unixepoch', 'localtime') as `cdate`, COUNT(" + StingleDbContract.Columns.COLUMN_NAME_FILENAME + ") " +
						"FROM " + tableName + " " +
						"WHERE album_id='" + albumId + "' " +
						"GROUP BY cdate " +
						"ORDER BY cdate " + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC")
				, null));

	}

	public long getTotalFilesCount(String albumId){
		String selection = StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID + " = ?";
		String[] selectionArgs = { albumId };
		return DatabaseUtils.queryNumEntries(db.getDb(), tableName, selection, selectionArgs);
	}

	public void close(){
		db.closeDb();
	}
}

