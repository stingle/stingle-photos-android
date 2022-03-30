package org.stingle.photos.Db.Objects;

import org.stingle.photos.StinglePhotosApplication;

public class StingleFileSearch {

    public String fileName;
    public String objHash;
    public String isProc;
    public long dateModified;

    public StingleFileSearch() {}

    public StingleFileSearch(String fileName, String type, String obj, String isProc) {
        this.fileName = fileName;
        // type can be: obj, face, loc
        this.objHash = StinglePhotosApplication.getCrypto().hashString(type + obj);
        this.isProc = isProc;
        this.dateModified = System.currentTimeMillis();
    }

    public StingleFileSearch(String fileName, String objHash, String isProc, long date) {
        this.fileName = fileName;
        this.objHash = objHash;
        this.isProc = isProc;
        this.dateModified = date;
    }
}
