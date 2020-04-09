package org.stingle.photos.Db.Query;

import android.database.Cursor;

import org.stingle.photos.Db.Objects.StingleDbFile;

public interface FilesDb {
	public static final int GET_MODE_ALL = 0;
	public static final int GET_MODE_ONLY_LOCAL = 1;
	public static final int GET_MODE_ONLY_REMOTE = 2;
	public static final int GET_MODE_LOCAL = 3;
	public static final int GET_MODE_REMOTE = 4;

	public static final int INITIAL_VERSION = 1;

	public static final int REUPLOAD_NO = 0;
	public static final int REUPLOAD_YES = 1;

	public Cursor getAvailableDates(String albumId, int sort);
	public StingleDbFile getFileAtPosition(int pos, String albumId, int sort);
	public long getTotalFilesCount(String albumId);
	public StingleDbFile getFileIfExists(String filename, String albumId);
	public long insertFile(StingleDbFile file);
	public int updateFile(StingleDbFile file);
	public Cursor getFilesList(int mode, int sort, String limit, String albumId);
	public Cursor getReuploadFilesList();
	public int markFileAsRemote(String filename);
	public int markFileAsReuploaded(String filename);
	public int getFilePositionByFilename(String filename, String albumId, int sort);
	public void close();
}
