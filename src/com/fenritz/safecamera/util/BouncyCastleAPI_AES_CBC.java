package com.fenritz.safecamera.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/**
 * Implementation of AES Bouncy Castle API installed as a library CBC mode for
 * encryption and decryption
 * 
 * @author Catalin Boja
 */

public class BouncyCastleAPI_AES_CBC {
	PaddedBufferedBlockCipher encryptCipher = null;
	PaddedBufferedBlockCipher decryptCipher = null;

	// Buffer used to transport the bytes from one stream to another
	byte[] buf = new byte[16]; // input buffer
	byte[] obuf = new byte[512]; // output buffer
	// The key
	byte[] key = null;
	// The initialization vector needed by the CBC mode
	byte[] IV = null;

	// The default block size
	public static int blockSize = 16;

	public BouncyCastleAPI_AES_CBC() {
		// default 192 bit key
		key = "SECRET_1SECRET_2SECRET_3".getBytes();
		// default IV vector with all bytes to 0
		IV = new byte[blockSize];
	}

	public BouncyCastleAPI_AES_CBC(byte[] keyBytes) {
		// get the key
		key = new byte[keyBytes.length];
		System.arraycopy(keyBytes, 0, key, 0, keyBytes.length);

		// default IV vector with all bytes to 0
		IV = new byte[blockSize];
	}

	public BouncyCastleAPI_AES_CBC(byte[] keyBytes, byte[] iv) {
		// get the key
		key = new byte[keyBytes.length];
		System.arraycopy(keyBytes, 0, key, 0, keyBytes.length);

		// get the IV
		IV = new byte[blockSize];
		System.arraycopy(iv, 0, IV, 0, iv.length);
	}

	public void InitCiphers() {
		// create the ciphers
		// AES block cipher in CBC mode with padding
		encryptCipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));

		decryptCipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));

		// create the IV parameter
		ParametersWithIV parameterIV = new ParametersWithIV(new KeyParameter(key), IV);

		encryptCipher.init(true, parameterIV);
		decryptCipher.init(false, parameterIV);
	}

	public void ResetCiphers() {
		if (encryptCipher != null) encryptCipher.reset();
		if (decryptCipher != null) decryptCipher.reset();
	}

	public void CBCEncrypt(InputStream in, OutputStream out) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException,
			DataLengthException, IllegalStateException, InvalidCipherTextException, IOException {
		// Bytes written to out will be encrypted
		// Read in the cleartext bytes from in InputStream and
		// write them encrypted to out OutputStream

		// optionaly put the IV at the beggining of the cipher file
		out.write(IV, 0, IV.length);

		int noBytesRead = 0; // number of bytes read from input
		int noBytesProcessed = 0; // number of bytes processed

		while ((noBytesRead = in.read(buf)) >= 0) {
			// System.out.println(noBytesRead +" bytes read");

			noBytesProcessed = encryptCipher.processBytes(buf, 0, noBytesRead, obuf, 0);
			// System.out.println(noBytesProcessed +" bytes processed");
			out.write(obuf, 0, noBytesProcessed);
		}

		// System.out.println(noBytesRead +" bytes read");
		noBytesProcessed = encryptCipher.doFinal(obuf, 0);

		// System.out.println(noBytesProcessed +" bytes processed");
		out.write(obuf, 0, noBytesProcessed);

		out.flush();

		in.close();
		out.close();
	}

	public void CBCDecrypt(InputStream in, OutputStream out) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException,
			DataLengthException, IllegalStateException, InvalidCipherTextException, IOException {
		// Bytes read from in will be decrypted
		// Read in the decrypted bytes from in InputStream and and
		// write them in cleartext to out OutputStream

		// get the IV from the file
		// DO NOT FORGET TO reinit the cipher with the IV
		in.read(IV, 0, IV.length);
		this.InitCiphers();

		int noBytesRead = 0; // number of bytes read from input
		int noBytesProcessed = 0; // number of bytes processed

		while ((noBytesRead = in.read(buf)) >= 0) {
			// System.out.println(noBytesRead +" bytes read");
			noBytesProcessed = decryptCipher.processBytes(buf, 0, noBytesRead, obuf, 0);
			// System.out.println(noBytesProcessed +" bytes processed");
			out.write(obuf, 0, noBytesProcessed);
		}
		// System.out.println(noBytesRead +" bytes read");
		noBytesProcessed = decryptCipher.doFinal(obuf, 0);
		// System.out.println(noBytesProcessed +" bytes processed");
		out.write(obuf, 0, noBytesProcessed);

		out.flush();

		in.close();
		out.close();
	}
}
