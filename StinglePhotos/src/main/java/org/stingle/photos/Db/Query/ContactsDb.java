package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import org.stingle.photos.Db.DatabaseManager;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Util.Helpers;

import java.util.ArrayList;

public class ContactsDb {

	private final DatabaseManager db;

	private final String tableName = StingleDbContract.Columns.TABLE_NAME_CONTACTS;

	public ContactsDb(Context context) {
		db = DatabaseManager.getInstance(context);
	}

	private final String[] projection = {
			StingleDbContract.Columns.COLUMN_NAME_USER_ID,
			StingleDbContract.Columns.COLUMN_NAME_EMAIL,
			StingleDbContract.Columns.COLUMN_NAME_PUBLIC_KEY,
			StingleDbContract.Columns.COLUMN_NAME_DATE_USED,
			StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED
	};


	public Long insertContact(StingleContact contact) {
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_USER_ID, contact.userId);
		values.put(StingleDbContract.Columns.COLUMN_NAME_EMAIL, contact.email);
		values.put(StingleDbContract.Columns.COLUMN_NAME_PUBLIC_KEY, contact.publicKey);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_USED, contact.dateUsed);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, contact.dateModified);

		db.getDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);

		return contact.userId;
	}

	public int updateContact(StingleContact contact) {
		ContentValues values = new ContentValues();
		values.put(StingleDbContract.Columns.COLUMN_NAME_USER_ID, contact.userId);
		values.put(StingleDbContract.Columns.COLUMN_NAME_EMAIL, contact.email);
		values.put(StingleDbContract.Columns.COLUMN_NAME_PUBLIC_KEY, contact.publicKey);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_USED, contact.dateUsed);
		values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, contact.dateModified);

		String selection = StingleDbContract.Columns.COLUMN_NAME_USER_ID + " = ?";
		String[] selectionArgs = {String.valueOf(contact.userId)};

		return db.getDb().update(
				tableName,
				values,
				selection,
				selectionArgs);
	}


	public int deleteContact(Long userId) {
		String selection = StingleDbContract.Columns.COLUMN_NAME_USER_ID + " = ?";
		String[] selectionArgs = {String.valueOf(userId)};

		return db.getDb().delete(tableName, selection, selectionArgs);
	}

	public int truncateTable() {
		return db.getDb().delete(tableName, null, null);
	}


	public AutoCloseableCursor getContactsList(int sort) {
		//String sortOrder = StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		return new AutoCloseableCursor(db.getDb().query(
				tableName,   // The table to query
				projection,             // The array of columns to return (pass null to get all)
				null,              // The columns for the WHERE clause
				null,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				null               // The sort order
		));

	}

	public StingleContact getContactAtPosition(int pos, int sort) {
		String sortOrder = StingleDbContract.Columns.COLUMN_NAME_DATE_USED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		try(AutoCloseableCursor autoCloseableCursor = new AutoCloseableCursor(db.getDb().query(
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
			Cursor result = autoCloseableCursor.getCursor();
			if (result.getCount() > 0) {
				result.moveToNext();
				return new StingleContact(result);
			}
		}
		return null;
	}

	public StingleContact getContactAtPosition(int pos, int sort, String filter) {
		String selection = StingleDbContract.Columns.COLUMN_NAME_EMAIL + " LIKE ?";
		String[] selectionArgs = {"%" + filter + "%"};

		String sortOrder = StingleDbContract.Columns.COLUMN_NAME_DATE_USED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

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
				return new StingleContact(result);
			}
		}
		return null;
	}
	public StingleContact getContactAtPosition(int pos, int sort, String filter, ArrayList<String> excludedIds) {
		if(excludedIds == null || excludedIds.size() == 0){
			return getContactAtPosition(pos, sort, filter);
		}
		String excludedIdsStr = Helpers.impode(",", excludedIds);

		String selection = StingleDbContract.Columns.COLUMN_NAME_EMAIL + " LIKE ? AND " + StingleDbContract.Columns.COLUMN_NAME_USER_ID + " NOT IN ("+excludedIdsStr+")";
		String[] selectionArgs = {"%" + filter + "%"};

		String sortOrder = StingleDbContract.Columns.COLUMN_NAME_DATE_USED + (sort == StingleDb.SORT_DESC ? " DESC" : " ASC");

		Cursor result = db.getDb().query(
				false,
				tableName,
				projection,
				selection,
				selectionArgs,
				null,
				null,
				sortOrder,
				pos + ", 1"
		);

		if (result.getCount() > 0) {
			result.moveToNext();
			return new StingleContact(result);
		}
		return null;
	}

	public StingleContact getContactByUserId(Long userId) {
		String selection = StingleDbContract.Columns.COLUMN_NAME_USER_ID + " = ?";
		String[] selectionArgs = {String.valueOf(userId)};

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
				return new StingleContact(result);
			}
		}
		return null;
	}

	public StingleContact getContactByEmail(String email) {
		String selection = StingleDbContract.Columns.COLUMN_NAME_EMAIL + " = ?";
		String[] selectionArgs = {email};

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
				return new StingleContact(result);
			}
		}
		return null;
	}

	public long getTotalContactsCount() {
		return DatabaseUtils.queryNumEntries(db.getDb(), tableName);
	}

	public long getTotalContactsCount(String filter) {
		String selection = StingleDbContract.Columns.COLUMN_NAME_EMAIL + " LIKE ?";
		String[] selectionArgs = {"%" + filter + "%"};
		return DatabaseUtils.queryNumEntries(db.getDb(), tableName, selection, selectionArgs);
	}

	public long getTotalContactsCount(String filter, ArrayList<String> excludedIds) {
		if(excludedIds == null || excludedIds.size() == 0){
			return getTotalContactsCount(filter);
		}
		String excludedIdsStr = Helpers.impode(",", excludedIds);

		String selection = StingleDbContract.Columns.COLUMN_NAME_EMAIL + " LIKE ? AND " + StingleDbContract.Columns.COLUMN_NAME_USER_ID + " NOT IN ("+excludedIdsStr+")";
		String[] selectionArgs = {"%" + filter + "%"};
		return DatabaseUtils.queryNumEntries(db.getDb(), tableName, selection, selectionArgs);
	}

	public void close() {
		db.closeDb();
	}
}

