package com.fenritz.safecam.util;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

		import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;
		import java.io.EOFException;
		import java.io.IOException;
		import java.io.RandomAccessFile;

/**
 * A {@link DataSource} for reading local files.
 */
public final class MyFileDataSource implements DataSource {

	/**
	 * Thrown when IOException is encountered during local file read operation.
	 */
	public static class FileDataSourceException extends IOException {

		public FileDataSourceException(IOException cause) {
			super(cause);
		}

	}


	private RandomAccessFile file;
	private Uri uri;
	private long bytesRemaining;
	private boolean opened;

	public MyFileDataSource() {

	}


	@Override
	public long open(DataSpec dataSpec) throws com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException {
		try {
			uri = dataSpec.uri;
			file = new RandomAccessFile(dataSpec.uri.getPath(), "r");
			file.seek(dataSpec.position);
			bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? file.length() - dataSpec.position
					: dataSpec.length;

			Log.d("open", "pos:" + String.valueOf(dataSpec.position) + " - filelen:" + String.valueOf(file.length()) + " - rem:"+String.valueOf(bytesRemaining));
			if (bytesRemaining < 0) {
				throw new EOFException();
			}
		} catch (IOException e) {
			throw new com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException(e);
		}

		opened = true;

		return bytesRemaining;
	}

	@Override
	public int read(byte[] buffer, int offset, int readLength) throws com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException {
		/*try {
			Log.d("position", String.valueOf(Math.round(file.getFilePointer()/1000000)) + " - " + String.valueOf(offset) + " - " + String.valueOf(readLength));
		} catch (IOException e) {
			e.printStackTrace();
		}*/
		if (readLength == 0) {
			return 0;
		} else if (bytesRemaining == 0) {
			return C.RESULT_END_OF_INPUT;
		} else {
			int bytesRead;
			try {
				bytesRead = file.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
			} catch (IOException e) {
				throw new com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException(e);
			}


			if (bytesRead > 0) {
				bytesRemaining -= bytesRead;
			}
			Log.d("read", String.valueOf(offset) + " - " +String.valueOf(readLength) + " - " +String.valueOf(bytesRead));
			return bytesRead;
		}
	}

	@Override
	public Uri getUri() {
		return uri;
	}

	@Override
	public void close() throws com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException {
		uri = null;
		Log.d("close", "close qaq");
		try {
			if (file != null) {
				file.close();
			}
		} catch (IOException e) {
			throw new com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException(e);
		} finally {
			file = null;
			if (opened) {
				opened = false;
			}
		}
	}

}
