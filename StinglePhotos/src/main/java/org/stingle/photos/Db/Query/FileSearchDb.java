package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.stingle.photos.Db.DatabaseManager;
import org.stingle.photos.Db.Objects.StingleFileSearch;
import org.stingle.photos.Db.StingleDbContract;

import java.util.ArrayList;
import java.util.List;

public class FileSearchDb {

    private final DatabaseManager db;

    private final String tableName = StingleDbContract.Columns.TABLE_NAME_SEARCH_INDEX;

    public FileSearchDb(Context context) {
        db = DatabaseManager.getInstance(context);
    }

    public Long insert(StingleFileSearch data) {
        ContentValues values = new ContentValues();
        values.put(StingleDbContract.Columns.COLUMN_NAME_FILENAME, data.fileName);
        values.put(StingleDbContract.Columns.COLUMN_NAME_HASH, data.objHash);
        values.put(StingleDbContract.Columns.COLUMN_NAME_IS_PROC, data.isProc);
        values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, data.dateModified);

        return db.getDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<StingleFileSearch> getAll() {
        String query = "SELECT * FROM " + tableName;
        Cursor cursor = db.getDb().rawQuery(query, null);
        ArrayList<StingleFileSearch> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            System.out.println(cursor.getString(0) + " : " + cursor.getString(1));
            list.add(new StingleFileSearch(cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getLong(6)));
        }
        return list;
    }

    public void delete(String fileName) {
        String query = "DELETE FROM " + tableName + " WHERE filename= " + fileName;
        db.getDb().rawQuery(query, null);
    }

    public void deleteAll() {
        String query = "DELETE FROM " + tableName;
        db.getDb().rawQuery(query, null);
    }

    public void close() {
        db.closeDb();
    }
}
