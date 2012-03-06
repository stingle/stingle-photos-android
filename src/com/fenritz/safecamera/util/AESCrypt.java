package com.fenritz.safecamera.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class AESCrypt {
	Cipher encryptionCipher;
	Cipher decryptionCipher;

	private byte[] iv;
	private SecretKey key;

	public static final String PROVIDER = "BC";
	public static final int SALT_LENGTH = 20;
	public static final int IV_LENGTH = 16;
	public static final int PBE_ITERATION_COUNT = 1000;

	private static final String RANDOM_ALGORITHM = "SHA1PRNG";
	private static final String HASH_ALGORITHM = "SHA-512";
	private static final String PBE_ALGORITHM = "PBEWithSHA256And256BitAES-CBC-BC";
	private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
	private static final String SECRET_KEY_ALGORITHM = "AES";;

	/**
	 * Input a string that will be md5 hashed to create the key.
	 * 
	 * @return void, cipher initialized
	 */

	public AESCrypt() {
		try {
			KeyGenerator kgen = KeyGenerator.getInstance("AES");
			kgen.init(256);
			key = kgen.generateKey();
			this.setupCrypto();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public AESCrypt(String stringKey) throws NoSuchAlgorithmException, NoSuchProviderException, AESCryptException {
		byte[] salt = getHash(stringKey);
		key = getSecretKey(stringKey, salt);
		this.setupCrypto();
	}

	private void setupCrypto() throws NoSuchAlgorithmException, NoSuchProviderException {
		this.setupCrypto(null);
	}

	private void setupCrypto(byte[] paramIv) {
		// Create an 8-byte initialization vector
		if (paramIv != null) {
			iv = paramIv;
		}
		else {
			iv = generateIv();
		}

		AlgorithmParameterSpec ivParamSpec = new IvParameterSpec(iv);
		try {
			encryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM, PROVIDER);
			decryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM, PROVIDER);

			// CBC requires an initialization vector
			encryptionCipher.init(Cipher.ENCRYPT_MODE, key, ivParamSpec);
			decryptionCipher.init(Cipher.DECRYPT_MODE, key, ivParamSpec);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Buffer used to transport the bytes from one stream to another
	byte[] buf = new byte[1024];

	public void encrypt(InputStream in, OutputStream out) {
		try {
			out.write(iv, 0, iv.length);

			// Bytes written to out will be encrypted
			out = new CipherOutputStream(out, encryptionCipher);

			// Read in the cleartext bytes and write to out to encrypt
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
			}
			out.close();
			in.close();
		}
		catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}
	
	public void encrypt(byte[] data, OutputStream out) {
		try {
			out.write(iv, 0, iv.length);

			// Bytes written to out will be encrypted
			out = new CipherOutputStream(out, encryptionCipher);

			out.write(data);
			out.close();
		}
		catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}


	public void decrypt(InputStream in, OutputStream out) {
		try {
			in.read(iv, 0, iv.length);
			this.setupCrypto(iv);

			// Bytes read from in will be decrypted
			in = new CipherInputStream(in, decryptionCipher);

			// Read in the decrypted bytes and write the cleartext to out
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
			}
			out.close();
			in.close();
		}
		catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Input is a string to encrypt.
	 * 
	 * @return a Hex string of the byte array
	 */
	public String encrypt(String plaintext) {
		try {
			byte[] ciphertext = encryptionCipher.doFinal(plaintext.getBytes("UTF-8"));
			String ivHexString = new String(byteToHex(iv));
			String cipherHexString = new String(byteToHex(ciphertext));
			return ivHexString.concat(cipherHexString);
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
	}
	
	/**
	 * Input encrypted String represented in HEX
	 * 
	 * @return a string decrypted in plain text
	 */
	public String decrypt(String hexCipherText) {
			String ivHexText = hexCipherText.substring(0, 32);
			hexCipherText = hexCipherText.substring(32);
			
			iv = hexToByte(ivHexText);
			setupCrypto(iv);
			
			try {
				String plaintext = new String(decryptionCipher.doFinal(hexToByte(hexCipherText)), "UTF-8");
				return plaintext;
			}
			catch (Exception e) {
				e.printStackTrace();
				return null;
			}
	}

	public String decrypt(byte[] ciphertext) {
		try {
			System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH);
			setupCrypto(iv);
			byte[] cipherBytes = new byte[ciphertext.length-IV_LENGTH];
			System.arraycopy(ciphertext, IV_LENGTH, cipherBytes, 0, ciphertext.length-IV_LENGTH);
			
			String plaintext = new String(decryptionCipher.doFinal(cipherBytes), "UTF-8");
			return plaintext;
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private byte[] generateIv() {
		SecureRandom random;
		try {
			random = SecureRandom.getInstance(RANDOM_ALGORITHM);
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(iv);
			return iv;
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return new byte[IV_LENGTH];
	}

	private byte[] getHash(String password) throws AESCryptException {
		try {
			MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM, PROVIDER);
			return md.digest(password.getBytes("UTF-8"));
		}
		catch (Exception e) {
			throw new AESCryptException("Unable to get hash");
		}
	}

	private SecretKey getSecretKey(String password, byte[] salt) throws AESCryptException {
		try {
			PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray(), salt, PBE_ITERATION_COUNT, 256);
			SecretKeyFactory factory = SecretKeyFactory.getInstance(PBE_ALGORITHM, PROVIDER);
			SecretKey tmp = factory.generateSecret(pbeKeySpec);
			SecretKey secret = new SecretKeySpec(tmp.getEncoded(), SECRET_KEY_ALGORITHM);
			return secret;
		}
		catch (Exception e) {
			throw new AESCryptException("Unable to get secret key");
		}
	}
	
	@SuppressWarnings("unused")
	private byte[] generateSalt() throws AESCryptException {
		try {
			SecureRandom random = SecureRandom.getInstance(RANDOM_ALGORITHM);
			byte[] salt = new byte[SALT_LENGTH];
			random.nextBytes(salt);
			return salt;
		}
		catch (Exception e) {
			throw new AESCryptException("Unable to generate salt");
		}
	}
	
	static final String HEXES = "0123456789ABCDEF";

    public static String byteToHex( byte [] raw ) {
        if ( raw == null ) {
          return null;
        }
        final StringBuilder hex = new StringBuilder( 2 * raw.length );
        for ( final byte b : raw ) {
          hex.append(HEXES.charAt((b & 0xF0) >> 4))
             .append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    public static byte[] hexToByte( String hexString){
        int len = hexString.length();
        byte[] ba = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            ba[i/2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i+1), 16));
        }
        return ba;
    }
}
