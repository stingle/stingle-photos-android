package org.stingle.photos.Db.Query;

import android.database.Cursor;

import org.stingle.photos.Db.Objects.StingleDbFile;

public interface FilesDb {
	public Cursor getAvailableDates(String folderId, int sort);
	public StingleDbFile getFileAtPosition(int pos, String folderId, int sort);
	public long getTotalFilesCount(String folderId);
	public StingleDbFile getFileIfExists(String filename, String folderId);
	public long insertFile(StingleDbFile file);
	public int updateFile(StingleDbFile file);
	public void close();
}
