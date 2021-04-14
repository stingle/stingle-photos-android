package org.stingle.photos.Db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseManager {
	private int mOpenCounter;

	private static DatabaseManager instance;
	private static SQLiteOpenHelper mDatabaseHelper;
	private SQLiteDatabase mDatabase;

	public static synchronized DatabaseManager getInstance(Context context) {
		if (instance == null) {
			instance = new DatabaseManager();
			mDatabaseHelper = new StingleDb(context);
		}

		return instance;
	}

	public synchronized SQLiteDatabase getDb() {
		mOpenCounter++;
		if(mOpenCounter == 1) {
			// Opening new database
			mDatabase = mDatabaseHelper.getWritableDatabase();
		}
		return mDatabase;
	}

	public synchronized void closeDb() {
		if(mOpenCounter > 0) {
			mOpenCounter--;
		}
		if(mOpenCounter == 0 && mDatabase != null) {
			// Closing database
			//mDatabase.close();
		}
	}
}
