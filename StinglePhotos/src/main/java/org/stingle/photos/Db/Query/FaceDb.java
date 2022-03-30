package org.stingle.photos.Db.Query;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.stingle.photos.Db.DatabaseManager;
import org.stingle.photos.Db.Objects.StingleFace;
import org.stingle.photos.Db.StingleDbContract;

public class FaceDb {

    private final DatabaseManager db;

    private final String tableName = StingleDbContract.Columns.TABLE_NAME_FACES;

    public FaceDb(Context context) {
        db = DatabaseManager.getInstance(context);
    }

    public Long insert(String faceId, StingleFace data) {
        ContentValues values = new ContentValues();
        values.put(StingleDbContract.Columns.COLUMN_NAME_FACE_ID, faceId);
        values.put(StingleDbContract.Columns.COLUMN_NAME_DATA, data.encrypt());
        values.put(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED, System.currentTimeMillis());

        return db.getDb().insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void delete(String faceId) {
        String query = "DELETE FROM " + tableName + " WHERE face_id= " + faceId;
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
