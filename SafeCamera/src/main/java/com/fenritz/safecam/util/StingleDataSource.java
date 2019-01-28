package com.fenritz.safecam.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.AEAD;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class StingleDataSource implements DataSource {

	private RandomAccessFile file;
	private Uri uri;
	private long bytesRemaining;
	private boolean opened;
	private SodiumAndroid so;
	private Crypto crypto;
	private Crypto.Header header;

	private int positionInChunk = 0;
	private int currentChunkNumber = 1;
	private byte[] currentChunk;

	public StingleDataSource(Context context) {
		so = new SodiumAndroid();
		crypto = new Crypto(context);
	}


	@Override
	public long open(DataSpec dataSpec) throws StingleDataSourceException {
		try {
			uri = dataSpec.uri;
			String path = dataSpec.uri.getPath();
			FileInputStream in = new FileInputStream(path);
			header = crypto.getFileHeader(in);

			file = new RandomAccessFile(dataSpec.uri.getPath(), "r");


			long chunkOffset = header.overallHeaderSize;
			if(dataSpec.absoluteStreamPosition > 0){
				currentChunkNumber = (int) Math.floor(dataSpec.absoluteStreamPosition / header.chunkSize) + 1;
				chunkOffset = header.overallHeaderSize +  (currentChunkNumber - 1) * (AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES + header.chunkSize + AEAD.XCHACHA20POLY1305_IETF_ABYTES);

				positionInChunk = (int)(dataSpec.absoluteStreamPosition - ((currentChunkNumber-1) * header.chunkSize));
			}
			else {
				positionInChunk = 0;
				currentChunk = null;
				currentChunkNumber = 1;
			}
			file.seek(chunkOffset);

			bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? header.dataSize - dataSpec.position : dataSpec.length;
			if (bytesRemaining < 0) {
				throw new EOFException();
			}

			Log.d("open", "pos:" + String.valueOf(dataSpec.absoluteStreamPosition) + " - pos:" + String.valueOf(positionInChunk) + " - off:" + String.valueOf(chunkOffset) + " - rem:"+String.valueOf(bytesRemaining));

			currentChunk = getChunk();



		} catch (IOException | CryptoException e) {
			throw new StingleDataSourceException(e);
		}

		opened = true;

		return bytesRemaining;
	}

	@Override
	public int read(byte[] buffer, int offset, int readLength) throws StingleDataSourceException {

		if (readLength == 0) {
			return 0;
		} else if (bytesRemaining == 0) {
			return C.RESULT_END_OF_INPUT;
		} else {
			int bytesRead;
			int howMuchNeeded = (int) Math.min(bytesRemaining, readLength);
			try {
				int bytesRemainingInChunk = currentChunk.length - positionInChunk;
				ByteArrayOutputStream data = new ByteArrayOutputStream();
				while(data.size() < howMuchNeeded){
					try {
						if(bytesRemainingInChunk < howMuchNeeded){
							Log.d("read", "next chunk");
							byte[] neededBytes = Arrays.copyOfRange(currentChunk, positionInChunk, currentChunk.length);
							howMuchNeeded -= currentChunk.length - positionInChunk;
							data.write(neededBytes);
							currentChunkNumber++;
							positionInChunk = 0;
							currentChunk = getChunk();
							bytesRemainingInChunk = currentChunk.length;
							Log.d("readNext", "len:" + String.valueOf(readLength) + " | need:" + String.valueOf(howMuchNeeded) + " | chunkNum:" +String.valueOf(currentChunkNumber) + " | bytesRem:" +String.valueOf(bytesRemainingInChunk) + " | position:" +String.valueOf(positionInChunk));
						}
						else {
							byte[] neededBytes = Arrays.copyOfRange(currentChunk, positionInChunk, positionInChunk+howMuchNeeded);
							data.write(neededBytes);
							positionInChunk += howMuchNeeded;
							Log.d("readNormal", "len:" + String.valueOf(readLength) + " | need:" + String.valueOf(howMuchNeeded) + " | chunkNum:" +String.valueOf(currentChunkNumber) + " | bytesRem:" +String.valueOf(bytesRemainingInChunk) + " | position:" +String.valueOf(positionInChunk));
						}

					} catch (CryptoException e) {
						throw new StingleDataSourceException(e);
					}

				}

				ByteArrayInputStream in = new ByteArrayInputStream(data.toByteArray());

				bytesRead = in.read(buffer, offset, readLength);
			} catch (IOException e) {
				throw new StingleDataSourceException(e);
			}


			if (bytesRead > 0) {
				bytesRemaining -= bytesRead;
			}


			return bytesRead;
		}
	}

	private byte[] getChunk() throws IOException, CryptoException {
		byte[] chunkKey = new byte[AEAD.XCHACHA20POLY1305_IETF_KEYBYTES];
		byte[] chunkNonce = new byte[AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES];
		byte[] contextBytes = Crypto.XCHACHA20POLY1305_IETF_CONTEXT.getBytes();

		byte[] encChunkBytes = new byte[header.chunkSize + AEAD.XCHACHA20POLY1305_IETF_ABYTES];

		int numRead = file.read(chunkNonce);
		if(numRead != AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES){
			throw new CryptoException("Invalid nonce length");
		}
		numRead = file.read(encChunkBytes);

		so.crypto_kdf_derive_from_key(chunkKey, chunkKey.length, currentChunkNumber, contextBytes, header.symmentricKey);

		byte[] decBytes = new byte[header.chunkSize];
		long[] decSize = new long[1];
		if(so.crypto_aead_xchacha20poly1305_ietf_decrypt(decBytes, decSize, null, encChunkBytes, numRead, null, 0, chunkNonce, chunkKey) != 0){
			throw new CryptoException("Error when decrypting data.");
		}

		return Arrays.copyOfRange(decBytes, 0, (int)decSize[0]);

	}

	@Override
	public Uri getUri() {
		return uri;
	}

	@Override
	public void close() throws com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException {
		Log.d("close", "close qaq");
		uri = null;
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

	/**
	 * Thrown when IOException is encountered during local file read operation.
	 */
	public static class StingleDataSourceException extends IOException {

		public StingleDataSourceException(Exception cause) {
			super(cause);
		}

	}
}
