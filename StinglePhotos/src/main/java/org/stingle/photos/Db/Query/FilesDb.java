package org.stingle.photos.Db.Query;

import org.stingle.photos.Db.Objects.StingleDbFile;

public interface FilesDb {
	int GET_MODE_ALL = 0;
	int GET_MODE_ONLY_LOCAL = 1;
	int GET_MODE_ONLY_REMOTE = 2;
	int GET_MODE_LOCAL_AND_REMOTE = 3;
	int GET_MODE_LOCAL = 4;
	int GET_MODE_REMOTE = 5;

	int INITIAL_VERSION = 1;

	int REUPLOAD_NO = 0;
	int REUPLOAD_YES = 1;

	AutoCloseableCursor getAvailableDates(String albumId, int sort);
	StingleDbFile getFileAtPosition(int pos, String albumId, int sort);
	long getTotalFilesCount(String albumId);
	StingleDbFile getFileIfExists(String filename, String albumId);
	long insertFile(StingleDbFile file);
	int updateFile(StingleDbFile file);
	AutoCloseableCursor getFilesList(int mode, int sort, String limit, String albumId);
	AutoCloseableCursor getReuploadFilesList();
	int markFileAsRemote(String filename);
	int markFileAsReuploaded(String filename);
	int getFilePositionByFilename(String filename, String albumId, int sort);
	void close();
}
