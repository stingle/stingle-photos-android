package org.stingle.photos.Db.Objects;

import org.stingle.photos.Db.Query.FilesTrashDb;

public abstract class StingleFile {
	public long id;
	public String filename;
	public Boolean isLocal;
	public Boolean isRemote;
	public Integer version = FilesTrashDb.INITIAL_VERSION;
	public Integer reupload = FilesTrashDb.REUPLOAD_NO;
	public Long dateCreated;
	public Long dateModified;
	public String headers;
}
