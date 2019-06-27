package com.fenritz.safecam.Db;

import android.provider.BaseColumns;

public class StingleDbContract {

	private StingleDbContract() {}

	/* Inner class that defines the table contents */
	public static class Files implements BaseColumns {
		public static final String TABLE_NAME = "files";
		public static final String COLUMN_NAME_FILENAME = "filename";
		public static final String COLUMN_NAME_IS_LOCAL = "is_local";
		public static final String COLUMN_NAME_IS_REMOTE = "is_remote";
		public static final String COLUMN_NAME_DATE_CREATED = "date_created";
		public static final String COLUMN_NAME_DATE_MODIFIED = "date_modified";
	}

	public static final String SQL_CREATE_ENTRIES =
			"CREATE TABLE " + Files.TABLE_NAME + " (" +
					Files._ID + " INTEGER PRIMARY KEY," +
					Files.COLUMN_NAME_FILENAME + " TEXT NOT NULL UNIQUE," +
					Files.COLUMN_NAME_IS_LOCAL + " INTEGER," +
					Files.COLUMN_NAME_IS_REMOTE + " INTEGER," +
					Files.COLUMN_NAME_DATE_CREATED + " INTEGER," +
					Files.COLUMN_NAME_DATE_MODIFIED + " INTEGER" +
					")";

	public static final String SQL_DELETE_ENTRIES =
			"DROP TABLE IF EXISTS " + Files.TABLE_NAME;

}
