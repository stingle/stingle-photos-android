package org.stingle.photos.Db.Query;

import android.database.Cursor;

public class AutoCloseableCursor implements AutoCloseable {
	private final Cursor cursor;

	public AutoCloseableCursor(Cursor cursor) {
		this.cursor = cursor;
	}

	public Cursor getCursor() {
		return cursor;
	}

	@Override
	public void close() {
		cursor.close();
	}
}
