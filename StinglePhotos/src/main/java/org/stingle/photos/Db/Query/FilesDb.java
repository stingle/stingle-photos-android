package org.stingle.photos.Db.Query;

import android.database.Cursor;

import org.stingle.photos.Db.Objects.StingleFile;

public interface FilesDb {
	public Cursor getAvailableDates(String folderId);
	public StingleFile getFileAtPosition(int pos, String folderId, int sort);
	public long getTotalFilesCount(String folderId);
	public void close();
}
