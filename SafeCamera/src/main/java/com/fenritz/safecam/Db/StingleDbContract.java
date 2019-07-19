package com.fenritz.safecam.Db;

import android.provider.BaseColumns;

public class StingleDbContract {

	private StingleDbContract() {}

	/* Inner class that defines the table contents */
	public static class Files implements BaseColumns {
		public static final String TABLE_NAME_FILES = "files";
		public static final String TABLE_NAME_TRASH = "trash";

		public static final String COLUMN_NAME_FILENAME = "filename";
		public static final String COLUMN_NAME_IS_LOCAL = "is_local";
		public static final String COLUMN_NAME_IS_REMOTE = "is_remote";
		public static final String COLUMN_NAME_DATE_CREATED = "date_created";
		public static final String COLUMN_NAME_DATE_MODIFIED = "date_modified";
	}

	public static final String SQL_CREATE_FILES =
			"CREATE TABLE " + Files.TABLE_NAME_FILES + " (" +
					Files._ID + " INTEGER PRIMARY KEY," +
					Files.COLUMN_NAME_FILENAME + " TEXT NOT NULL UNIQUE," +
					Files.COLUMN_NAME_IS_LOCAL + " INTEGER," +
					Files.COLUMN_NAME_IS_REMOTE + " INTEGER," +
					Files.COLUMN_NAME_DATE_CREATED + " INTEGER," +
					Files.COLUMN_NAME_DATE_MODIFIED + " INTEGER" +
					")";

	public static final String SQL_DELETE_FILES =
			"DROP TABLE IF EXISTS " + Files.TABLE_NAME_FILES;


	public static final String SQL_CREATE_TRASH =
			"CREATE TABLE " + Files.TABLE_NAME_TRASH + " (" +
					Files._ID + " INTEGER PRIMARY KEY," +
					Files.COLUMN_NAME_FILENAME + " TEXT NOT NULL UNIQUE," +
					Files.COLUMN_NAME_IS_LOCAL + " INTEGER," +
					Files.COLUMN_NAME_IS_REMOTE + " INTEGER," +
					Files.COLUMN_NAME_DATE_CREATED + " INTEGER," +
					Files.COLUMN_NAME_DATE_MODIFIED + " INTEGER" +
					")";

	public static final String SQL_DELETE_TRASH =
			"DROP TABLE IF EXISTS " + Files.TABLE_NAME_TRASH;

}
