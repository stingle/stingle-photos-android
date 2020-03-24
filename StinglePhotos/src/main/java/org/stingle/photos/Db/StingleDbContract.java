package org.stingle.photos.Db;

import android.provider.BaseColumns;

public class StingleDbContract {

	private StingleDbContract() {}

	/* Inner class that defines the table contents */
	public static class Files implements BaseColumns {
		public static final String TABLE_NAME_FILES = "files";
		public static final String TABLE_NAME_TRASH = "trash";
		public static final String TABLE_NAME_ALBUMS = "albums";
		public static final String TABLE_NAME_ALBUM_FILES = "album_files";
		public static final String TABLE_NAME_SHARES = "shares";

		public static final String COLUMN_NAME_FILENAME = "filename";
		public static final String COLUMN_NAME_IS_LOCAL = "is_local";
		public static final String COLUMN_NAME_IS_REMOTE = "is_remote";
		public static final String COLUMN_NAME_VERSION = "version";
		public static final String COLUMN_NAME_REUPLOAD = "reupload";
		public static final String COLUMN_NAME_DATE_CREATED = "date_created";
		public static final String COLUMN_NAME_DATE_MODIFIED = "date_modified";
		public static final String COLUMN_NAME_HEADERS = "headers";
		public static final String COLUMN_NAME_DATA = "data";
		public static final String COLUMN_NAME_ALBUM_PK = "album_pk";
		public static final String COLUMN_NAME_ALBUM_ID = "album_id";
		public static final String COLUMN_NAME_TO_USER_ID = "to_user_id";
		public static final String COLUMN_NAME_FROM_USER_ID = "from_user_id";
		public static final String COLUMN_NAME_TO_DATA = "to_data";
		public static final String COLUMN_NAME_FROM_DATA = "from_data";
	}

	public static final String SQL_CREATE_FILES =
			"CREATE TABLE " + Files.TABLE_NAME_FILES + " (" +
					Files._ID + " INTEGER PRIMARY KEY," +
					Files.COLUMN_NAME_FILENAME + " TEXT NOT NULL UNIQUE," +
					Files.COLUMN_NAME_IS_LOCAL + " INTEGER," +
					Files.COLUMN_NAME_IS_REMOTE + " INTEGER," +
					Files.COLUMN_NAME_VERSION + " INTEGER," +
					Files.COLUMN_NAME_REUPLOAD + " INTEGER," +
					Files.COLUMN_NAME_DATE_CREATED + " INTEGER," +
					Files.COLUMN_NAME_DATE_MODIFIED + " INTEGER, " +
					Files.COLUMN_NAME_HEADERS + " TEXT" +
					")";

	public static final String SQL_DELETE_FILES =
			"DROP TABLE IF EXISTS " + Files.TABLE_NAME_FILES;


	public static final String SQL_CREATE_TRASH =
			"CREATE TABLE " + Files.TABLE_NAME_TRASH + " (" +
					Files._ID + " INTEGER PRIMARY KEY," +
					Files.COLUMN_NAME_FILENAME + " TEXT NOT NULL UNIQUE," +
					Files.COLUMN_NAME_IS_LOCAL + " INTEGER," +
					Files.COLUMN_NAME_IS_REMOTE + " INTEGER," +
					Files.COLUMN_NAME_VERSION + " INTEGER," +
					Files.COLUMN_NAME_REUPLOAD + " INTEGER," +
					Files.COLUMN_NAME_DATE_CREATED + " INTEGER," +
					Files.COLUMN_NAME_DATE_MODIFIED + " INTEGER, " +
					Files.COLUMN_NAME_HEADERS + " TEXT" +
					")";

	public static final String SQL_DELETE_TRASH =
			"DROP TABLE IF EXISTS " + Files.TABLE_NAME_TRASH;


	public static final String SQL_CREATE_ALBUMS =
			"CREATE TABLE " + Files.TABLE_NAME_ALBUMS + " (" +
					Files._ID + " INTEGER PRIMARY KEY," +
					Files.COLUMN_NAME_DATA + " TEXT NOT NULL," +
					Files.COLUMN_NAME_ALBUM_PK + " TEXT NOT NULL, " +
					Files.COLUMN_NAME_DATE_CREATED + " INTEGER," +
					Files.COLUMN_NAME_DATE_MODIFIED + " INTEGER" +
					")";

	public static final String SQL_DELETE_ALBUMS =
			"DROP TABLE IF EXISTS " + Files.TABLE_NAME_ALBUMS;

	public static final String SQL_CREATE_ALBUM_FILES =
			"CREATE TABLE " + Files.TABLE_NAME_ALBUM_FILES + " (" +
					Files._ID + " INTEGER PRIMARY KEY," +
					Files.COLUMN_NAME_ALBUM_ID + " INTEGER," +
					Files.COLUMN_NAME_FILENAME + " TEXT NOT NULL UNIQUE," +
					Files.COLUMN_NAME_IS_LOCAL + " INTEGER," +
					Files.COLUMN_NAME_IS_REMOTE + " INTEGER," +
					Files.COLUMN_NAME_VERSION + " INTEGER," +
					Files.COLUMN_NAME_REUPLOAD + " INTEGER," +
					Files.COLUMN_NAME_HEADERS + " TEXT, " +
					Files.COLUMN_NAME_DATE_CREATED + " INTEGER, " +
					Files.COLUMN_NAME_DATE_MODIFIED + " INTEGER" +
					")";

	public static final String SQL_DELETE_ALBUM_FILES =
			"DROP TABLE IF EXISTS " + Files.TABLE_NAME_ALBUM_FILES;

	public static final String SQL_CREATE_SHARES =
			"CREATE TABLE " + Files.TABLE_NAME_SHARES + " (" +
					Files._ID + " INTEGER PRIMARY KEY," +
					Files.COLUMN_NAME_TO_USER_ID + " INTEGER," +
					Files.COLUMN_NAME_FROM_USER_ID + " INTEGER," +
					Files.COLUMN_NAME_TO_DATA + " TEXT NOT NULL," +
					Files.COLUMN_NAME_FROM_DATA + " TEXT NOT NULL," +
					Files.COLUMN_NAME_DATE_CREATED + " INTEGER," +
					Files.COLUMN_NAME_DATE_MODIFIED + " INTEGER" +
					")";

	public static final String SQL_DELETE_SHARES =
			"DROP TABLE IF EXISTS " + Files.TABLE_NAME_SHARES;
}
