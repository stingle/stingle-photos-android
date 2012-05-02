package com.fenritz.safecamera.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
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

import android.os.AsyncTask;

public class AESCrypt {
	private Cipher encryptionCipher;
	private Cipher decryptionCipher;

	// Buffer used to transport the bytes from one stream to another
	byte[] buf = new byte[1024*4];
	
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
	private static final String SECRET_KEY_ALGORITHM = "AES";

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
		key = getSecretKey(stringKey);
		this.setupCrypto();
	}
	
	public AESCrypt(SecretKey pKey) throws NoSuchAlgorithmException, NoSuchProviderException, AESCryptException {
		key = pKey;
		this.setupCrypto();
	}

	public static SecretKey getSecretKey(String stringKey) throws NoSuchAlgorithmException, NoSuchProviderException, AESCryptException{
		return getSecretKey(stringKey, getHash(stringKey));
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
	
	public Cipher getEncryptionCipher(){
		return encryptionCipher;
	}
	
	public Cipher getDecryptionCipher(){
		return decryptionCipher;
	}
	
	public byte[] getIV(){
		return iv;
	}

	public void encrypt(InputStream in, OutputStream out) {
		this.encrypt(in, out, null, null);
	}
	public void encrypt(InputStream in, OutputStream out, CryptoProgress progress) {
		this.encrypt(in, out, progress, null);
	}
	public void encrypt(InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task) {
		try {
			out.write(iv, 0, iv.length);
			if(progress != null){
				progress.setProgress(iv.length);
			}

			// Bytes written to out will be encrypted
			out = new CipherOutputStream(out, encryptionCipher);

			// Read in the cleartext bytes and write to out to encrypt
			long totalRead = iv.length;
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
				if(progress != null){
					totalRead += numRead;
					progress.setProgress(totalRead);
				}
				if(task != null){
					if(task.isCancelled()){
						break;
					}
				}
			}
			out.close();
			in.close();
		}
		catch (java.io.IOException e) {
			e.printStackTrace();
			return;
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
			return;
		}
	}

	public void reEncrypt(InputStream in, OutputStream out, AESCrypt secondaryCrypt) {
		this.reEncrypt(in, out, secondaryCrypt, null, null);
	}
	public void reEncrypt(InputStream in, OutputStream out, AESCrypt secondaryCrypt, CryptoProgress progress) {
		this.reEncrypt(in, out, secondaryCrypt, progress, null);
	}
	public void reEncrypt(InputStream in, OutputStream out, AESCrypt secondaryCrypt, CryptoProgress progress, AsyncTask<?,?,?> task) {
		try {
			/*byte[] sIV = secondaryCrypt.getIV();
			out.write(sIV, 0, sIV.length);
			if(progress != null){
				progress.setProgress(sIV.length);
			}*/

			// Bytes written to out will be encrypted
			in = new CipherInputStream(in, decryptionCipher);
			out = new CipherOutputStream(out, secondaryCrypt.getEncryptionCipher());

			// Read in the cleartext bytes and write to out to encrypt
			//long totalRead = sIV.length;
			long totalRead = 0;
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
				if(progress != null){
					totalRead += numRead;
					progress.setProgress(totalRead);
				}
				if(task != null){
					if(task.isCancelled()){
						break;
					}
				}
			}
			out.close();
			in.close();
		}
		catch (java.io.IOException e) {
			e.printStackTrace();
			return;
		}
	}

	public void decrypt(InputStream in, OutputStream out) {
		this.decrypt(in, out, null, null);
	}
	
	public void decrypt(InputStream in, OutputStream out, CryptoProgress progress) {
		this.decrypt(in, out, progress, null);
	}
	
	public void decrypt(InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task) {
		try {
			in.read(iv, 0, iv.length);
			this.setupCrypto(iv);
			if(progress != null){
				progress.setProgress(iv.length);
			}

			// Bytes read from in will be decrypted
			in = new CipherInputStream(in, decryptionCipher);

			// Read in the decrypted bytes and write the cleartext to out
			long totalRead = iv.length;
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
				if(progress != null){
					totalRead += numRead;
					progress.setProgress(totalRead);
				}
				if(task != null){
					if(task.isCancelled()){
						break;
					}
				}
			}
			out.close();
			in.close();
		}
		catch (java.io.IOException e) {
			e.printStackTrace();
			return;
		}
	}
	
	public byte[] decrypt(InputStream in) {
		return this.decrypt(in, (CryptoProgress)null, null);
	}
	public byte[] decrypt(InputStream in, CryptoProgress progress) {
		return this.decrypt(in, progress, null);
	}
	
	public byte[] decrypt(InputStream in, CryptoProgress progress, AsyncTask<?,?,?> task) {
		try {
			in.read(iv, 0, iv.length);
			this.setupCrypto(iv);
			if(progress != null){
				progress.setProgress(iv.length);
			}

			// Bytes read from in will be decrypted
			in = new OptimizedCipherInputStream(in, decryptionCipher);

			// Read in the decrypted bytes and write the cleartext to out
			ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {
				byteBuffer.write(buf, 0, numRead);
				if(progress != null){
					progress.setProgress(byteBuffer.size() + iv.length);
				}
				if(task != null){
					if(task.isCancelled()){
						return null;
					}
				}
			}
			
			in.close();
			
			return byteBuffer.toByteArray();
		}
		catch (java.io.IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/*public byte[] decrypt(InputStream in, CryptoProgress progress, AsyncTask<?,?,?> task) {
		try {
			in.read(iv, 0, iv.length);
			this.setupCrypto(iv);
			if(progress != null){
				progress.setProgress(iv.length);
			}
			
			
			// Read in the decrypted bytes and write the cleartext to out
			BufferedInputStream inb = new BufferedInputStream(in);
			
			
			byte[] out = new byte[0];
			int bufLen = 128 * 1024;
			byte[] buf = new byte[bufLen];
			byte[] tmp = null;
			int len = 0;
			
			while ((len = inb.read(buf, 0, bufLen)) != -1) {
				// extend array
				tmp = new byte[out.length + len];
				
				// copy data
				System.arraycopy(out, 0, tmp, 0, out.length);
				System.arraycopy(buf, 0, tmp, out.length, len);
				out = tmp;
				tmp = null;
				if(task != null){
					if(task.isCancelled()){
						return null;
					}
				}
				
				if(progress != null){
					progress.setProgress(out.length + iv.length);
				}
			}
			
			byte[] decryptedData = decryptionCipher.doFinal(out);
			inb.close();
			
			return decryptedData;
		}
		catch (java.io.IOException e) {
			e.printStackTrace();
		}
		catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		}
		catch (BadPaddingException e) {
			e.printStackTrace();
		}
		
		return null;
	}*/

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

	public String decryptForString(byte[] ciphertext) {
		try {
			return new String(decrypt(ciphertext), "UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public byte[] decrypt(byte[] ciphertext) {
		try {
			System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH);
			setupCrypto(iv);
			byte[] cipherBytes = new byte[ciphertext.length-IV_LENGTH];
			System.arraycopy(ciphertext, IV_LENGTH, cipherBytes, 0, ciphertext.length-IV_LENGTH);
			
			return decryptionCipher.doFinal(cipherBytes);
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

	public static byte[] getHash(String password) throws AESCryptException {
		try {
			MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM, PROVIDER);
			return md.digest(password.getBytes("UTF-8"));
		}
		catch (Exception e) {
			throw new AESCryptException("Unable to get hash");
		}
	}

	private static SecretKey getSecretKey(String password, byte[] salt) throws AESCryptException {
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
    
    public static class CryptoProgress{
    	private long total = 0;
    	private long current = 0;
    	
    	public CryptoProgress(long pTotal){
    		total = pTotal;
    	}
    	
    	public void setProgress(long pCurrent){
    		current = pCurrent;
    	}
    	
    	public long getTotal(){
    		return total;
    	}
    	
    	public int getProgressPercents(){
    		if(total == 0){
    			return 0;
    		}
    		return (int) (current * 100 / total);
    	}
    	
    	public long getProgress(){
    		if(total == 0){
    			return 0;
    		}
    		return current;
    	}
    }
}
