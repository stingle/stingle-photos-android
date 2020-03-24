package org.stingle.photos.Db.Query;

import android.database.Cursor;

import org.stingle.photos.Db.Objects.StingleFile;

public interface FilesDb {
	public Cursor getAvailableDates(int folderId);
	public StingleFile getFileAtPosition(int pos, int folderId, int sort);
	public long getTotalFilesCount(int folderId);
	public void close();
}
