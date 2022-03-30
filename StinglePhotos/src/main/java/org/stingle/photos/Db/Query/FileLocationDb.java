package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.stingle.photos.Db.DatabaseManager;
import org.stingle.photos.Db.Objects.StingleFileInfo;
import org.stingle.photos.Db.StingleDbContract;

public class FileLocationDb {

    private final DatabaseManager db;

    private final String tableName = StingleDbContract.Columns.TABLE_NAME_LOCATIONS;

    public FileLocationDb(Context context) {
        db = DatabaseManager.getInstance(context);
    }

    public Long insert(String locId, StingleFileInfo data) {
        ContentValues values = new ContentValues();
        values.put(StingleDbContract.Columns.COLUMN_NAME_LOC_ID, locId);
        values.put(StingleDbContract.Columns.COLUMN_NAME_DATA, data.encrypt());
        values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, System.currentTimeMillis());

        return db.getDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void delete(String locId) {
        String query = "DELETE FROM " + tableName + " WHERE loc_id= " + locId;
        db.getDb().rawQuery(query, null);
    }

    public void deleteAll() {
        String query = "DELETE FROM " + tableName;
        db.getDb().rawQuery(query, null);
    }

    public void close(){
        db.closeDb();
    }
}
