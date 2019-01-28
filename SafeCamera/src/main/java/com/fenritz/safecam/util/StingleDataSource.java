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

	//private RandomAccessFile file;
	private Uri uri;
	private long bytesRemaining;
	private boolean opened;
	private SodiumAndroid so;
	private Crypto crypto;
	private Crypto.Header header;

	private int positionInChunk = 0;
	private int currentChunkNumber = 1;
	private byte[] currentChunk;
	private DataSource upstream;

	public StingleDataSource(Context context, DataSource upstream) {
		this.so = new SodiumAndroid();
		this.crypto = new Crypto(context);
		this.upstream = upstream;
	}

	private void getHeader(DataSpec dataSpec) throws IOException {
		DataSpec specUp = new DataSpec(dataSpec.uri, 0, C.LENGTH_UNSET, null, 0);
		upstream.open(specUp);

		int headerLen = Crypto.FILE_BEGGINIG_LEN + Crypto.FILE_FILE_VERSION_LEN + Crypto.FILE_CHUNK_SIZE_LEN + Crypto.FILE_DATA_SIZE_LEN + Crypto.FILE_HEADER_SIZE_LEN;
		byte[] buf = new byte[headerLen];
		int bytesRead = upstream.read(buf, 0, headerLen);
		if(bytesRead != headerLen){
			throw new IOException("Invalid header length");
		}
		int encHeaderSize = Crypto.byteArrayToInt(Arrays.copyOfRange(buf, headerLen-Crypto.FILE_HEADER_SIZE_LEN, headerLen));
		if(encHeaderSize < 1 || encHeaderSize > Crypto.MAX_BUFFER_LENGTH){
			throw new IOException("Invalid header length");
		}

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bytes.write(buf);
		buf = new byte[encHeaderSize];
		bytesRead = upstream.read(buf, 0, encHeaderSize);
		if(bytesRead != encHeaderSize){
			throw new IOException("Invalid header length");
		}
		bytes.write(buf);
		upstream.close();

		try {
			header = crypto.getFileHeader(new ByteArrayInputStream(bytes.toByteArray()));
		} catch (CryptoException e) {
			throw new IOException("Error getting file header");
		}
	}

	@Override
	public long open(DataSpec dataSpec) throws StingleDataSourceException {
		try {
			uri = dataSpec.uri;
			if(header == null){
				getHeader(dataSpec);
			}

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
			//file.seek(chunkOffset);
			DataSpec specUp = new DataSpec(dataSpec.uri, chunkOffset, C.LENGTH_UNSET, null, 0);
			upstream.open(specUp);

			bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? header.dataSize - dataSpec.position : dataSpec.length;
			if (bytesRemaining < 0) {
				throw new EOFException();
			}


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
							byte[] neededBytes = Arrays.copyOfRange(currentChunk, positionInChunk, currentChunk.length);
							howMuchNeeded -= currentChunk.length - positionInChunk;
							data.write(neededBytes);
							currentChunkNumber++;
							positionInChunk = 0;
							currentChunk = getChunk();
							bytesRemainingInChunk = currentChunk.length;
						}
						else {
							byte[] neededBytes = Arrays.copyOfRange(currentChunk, positionInChunk, positionInChunk+howMuchNeeded);
							data.write(neededBytes);
							positionInChunk += howMuchNeeded;
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

		int numRead;
		//numRead= file.read(chunkNonce);
		numRead = upstream.read(chunkNonce, 0, chunkNonce.length);
		if(numRead != AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES){
			throw new CryptoException("Invalid nonce length");
		}
		//numRead = file.read(encChunkBytes);
		numRead = upstream.read(encChunkBytes, 0, encChunkBytes.length);

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
	public void close() throws StingleDataSourceException {
		uri = null;
		try {
			upstream.close();
		} catch (IOException e) {
			throw new StingleDataSourceException(e);
		}
		/*try {
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
		}*/
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
