package com.fenritz.safecam.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
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
	
	private SecretKey key;
	private final String password;

	public static final String PROVIDER = "BC";
	public static final int SALT_LENGTH = 32;
	public static final int IV_LENGTH = 16;
	public static final int PBE_ITERATION_COUNT = 2048;

	private static final String RANDOM_ALGORITHM = "SHA1PRNG";
	private static final String HASH_ALGORITHM = "SHA-512";
	private static final String PBE_ALGORITHM = "PBEWithSHA256And256BitAES-CBC-BC";
	private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS7Padding";
	private static final String SECRET_KEY_ALGORITHM = "AES";
	
	private static final int VERSION = 1;
	private static final String HEADER = "SCAES";

	private byte[] iv = new byte[IV_LENGTH];
	private byte[] salt = new byte[SALT_LENGTH];
	private int currentVersion = 1;
	/**
	 * Input a string that will be md5 hashed to create the key.
	 * 
	 * @return void, cipher initialized
	 */

	public AESCrypt(String password) {
		this.password = password;
	}
	
	public boolean setupCrypto(){
		return this.setupCrypto(null, null);
	}

	private boolean setupCrypto(byte[] paramIv, byte[] paramSalt){
		
		if(password == null){
			return false;
		}
		
		// Create an 8-byte initialization vector
		if (paramIv != null) {
			iv = paramIv;
		}
		else {
			iv = generateIv();
		}
		
		if(paramSalt != null){
			try {
				this.salt = paramSalt;
				this.key = getSecretKey(password, paramSalt);
			}
			catch (AESCryptException e) {
				return false;
			}
		}
		else{
			try {
				this.salt = generateSalt();
				this.key = getSecretKey(password, this.salt);
			}
			catch (AESCryptException e) {
				return false;
			}
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
			return false;
		}
		
		return true;
	}
	
	public Cipher getEncryptionCipher(){
		return getEncryptionCipher(null, null);
	}
	
	public Cipher getEncryptionCipher(byte[] paramIv, byte[] salt){
		if(!setupCrypto(paramIv, salt)){
			return null;
		}
		return encryptionCipher;
	}
	
	public Cipher getDecryptionCipher(byte[] paramIv, byte[] salt){
		if(!setupCrypto(paramIv, salt)){
			return null; 
		}
		return decryptionCipher;
	}
	
	public byte[] getIV(){
		return iv;
	}
	
	public byte[] getSalt(){
		return salt;
	}

	private void writeHeader(OutputStream out, byte[] pIv, byte[] pSalt) throws IOException{
		out.write(HEADER.getBytes());
		out.write(VERSION);
		out.write(pIv);
		out.write(pSalt);
	}
	
	private int readHeader(InputStream in, byte[] pIv, byte[] pSalt) throws IOException{
		byte[] header = new byte[HEADER.length()];
		in.read(header);
		
		if (!new String(header, "UTF-8").equals(HEADER)) {
			throw new IOException("Invalid file header");
		}

		currentVersion = in.read();
		if (currentVersion != VERSION) {
			throw new IOException("Unsupported version number: " + currentVersion);
		}
		
		in.read(pIv);
		in.read(pSalt);
		
		return header.length + 1 + pIv.length + pSalt.length;
	}
	
	public void encrypt(InputStream in, OutputStream out) {
		this.encrypt(in, out, null, null);
	}
	public void encrypt(InputStream in, OutputStream out, CryptoProgress progress) {
		this.encrypt(in, out, progress, null);
	}
	public void encrypt(InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task) {
		try {
			if(!this.setupCrypto()){
				return;
			}
			writeHeader(out, iv, salt);
			
			if(progress != null){
				progress.setProgress(iv.length+salt.length);
			}

			// Bytes written to out will be encrypted
			out = new CipherOutputStream(out, encryptionCipher);

			// Read in the cleartext bytes and write to out to encrypt
			long totalRead = iv.length+salt.length;
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
		}
	}
	
	public void encrypt(byte[] data, OutputStream out) {
		try {
			if(!this.setupCrypto()){
				return;
			}
			if(data == null){
				return;
			}
			writeHeader(out, iv, salt);

			// Bytes written to out will be encrypted
			out = new CipherOutputStream(out, encryptionCipher);

			out.write(data);
			out.close();
		}
		catch (java.io.IOException e) { }
	}

	public boolean reEncrypt(InputStream in, OutputStream out, AESCrypt secondaryCrypt) {
		return this.reEncrypt(in, out, secondaryCrypt, null, null, false);
	}
	public boolean reEncrypt(InputStream in, OutputStream out, AESCrypt secondaryCrypt, CryptoProgress progress) {
		return this.reEncrypt(in, out, secondaryCrypt, progress, null, false);
	}
	public boolean reEncrypt(InputStream in, OutputStream out, AESCrypt secondaryCrypt, CryptoProgress progress, AsyncTask<?,?,?> task){
		return this.reEncrypt(in, out, secondaryCrypt, progress, task, false);
	}
	public boolean reEncrypt(InputStream in, OutputStream out, AESCrypt secondaryCrypt, CryptoProgress progress, AsyncTask<?,?,?> task, boolean encryptionIsMy) {
		try {
			long totalRead;
			
			// Decrypt other's file and reencrypt to our key
			if(encryptionIsMy){
				byte[] decIV = new byte[IV_LENGTH];
				byte[] decSalt = new byte[SALT_LENGTH];
				totalRead = readHeader(in, decIV, decSalt);
				
				if(!this.setupCrypto()){
					return false;
				}
				
				writeHeader(out, iv, salt);
				
				in = new OptimizedCipherInputStream(in, secondaryCrypt.getDecryptionCipher(decIV, decSalt));
				out = new CipherOutputStream(out, encryptionCipher);
			}
			// Decrypt our file and reencrypt to their key
			else{
				totalRead = readHeader(in, iv, salt);
				if(!this.setupCrypto(iv, salt)){
					return false;
				}
				
				Cipher secEncryptionCipher = secondaryCrypt.getEncryptionCipher();
				writeHeader(out, secondaryCrypt.getIV(), secondaryCrypt.getSalt());
				
				in = new OptimizedCipherInputStream(in, decryptionCipher);
				out = new CipherOutputStream(out, secEncryptionCipher);
			}

			// Do reencrypt process
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
		catch (Exception e) {
			try {
				out.close();
				in.close();
			}
			catch (IOException e1) { }
			return false;
		}
		
		return true;
	}

	public void decrypt(InputStream in, OutputStream out) {
		this.decrypt(in, out, null, null);
	}
	
	public void decrypt(InputStream in, OutputStream out, CryptoProgress progress) {
		this.decrypt(in, out, progress, null);
	}
	
	public void decrypt(InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task) {
		try {
			long totalRead = readHeader(in, iv, salt);
			if(!this.setupCrypto(iv, salt)){
				return;
			}
			if(progress != null){
				progress.setProgress(iv.length+salt.length);
			}

			// Bytes read from in will be decrypted
			in = new OptimizedCipherInputStream(in, decryptionCipher);

			// Read in the decrypted bytes and write the cleartext to out
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
			try {
				out.close();
				in.close();
			}
			catch (IOException e1) { }
		}
	}
	
	public byte[] decrypt(InputStream in) {
		return this.decrypt(in, (CryptoProgress)null, null);
	}
	public byte[] decrypt(InputStream in, CryptoProgress progress) {
		return this.decrypt(in, progress, null);
	}
	
	public byte[] decrypt(InputStream in, AsyncTask<?,?,?> task) {
		return this.decrypt(in, null, task);
	}
	
	public byte[] decrypt(InputStream in, CryptoProgress progress, AsyncTask<?,?,?> task) {
		try {
			long totalRead = readHeader(in, iv, salt);
			if(!this.setupCrypto(iv, salt)){
				return null;
			}

			ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
			int numRead = 0;
			if(progress != null){
				in = new OptimizedCipherInputStream(in, decryptionCipher);
				progress.setProgress(totalRead);
			}
			
			while ((numRead = in.read(buf)) >= 0) {
				byteBuffer.write(buf, 0, numRead);
				
				if(task != null){
					if(task.isCancelled()){
						return null;
					}
				}
				if(progress != null){
					totalRead += numRead;
					progress.setProgress(totalRead);
				}
			}
			in.close();
			
			if(progress != null){
				return byteBuffer.toByteArray();
			}
			else{
				return decryptionCipher.doFinal(byteBuffer.toByteArray());
			}
				
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
	}
	
	/**
	 * Input is a string to encrypt.
	 * 
	 * @return a Hex string of the byte array
	 */
	public String encrypt(String plaintext) {
		try {
			if(!this.setupCrypto()){
				return null;
			}
			byte[] ciphertext = encryptionCipher.doFinal(plaintext.getBytes("UTF-8"));
			String ivHexString = byteToHex(iv);
			String saltHexString = byteToHex(salt);
			String cipherHexString = byteToHex(ciphertext);
			return ivHexString + saltHexString + cipherHexString;
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
		if(hexCipherText.length() > (IV_LENGTH * 2) + (SALT_LENGTH * 2)){
			String ivHexText = hexCipherText.substring(0, IV_LENGTH * 2);
			String saltHexText = hexCipherText.substring(IV_LENGTH * 2, (IV_LENGTH * 2) + (SALT_LENGTH * 2));
			hexCipherText = hexCipherText.substring((IV_LENGTH * 2) + (SALT_LENGTH * 2));
			
			byte[] pIv = hexToByte(ivHexText);
			byte[] pSalt = hexToByte(saltHexText);
			setupCrypto(pIv, pSalt);
			
			try {
				String plaintext = new String(decryptionCipher.doFinal(hexToByte(hexCipherText)), "UTF-8");
				return plaintext;
			}
			catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return null;
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
			System.arraycopy(ciphertext, IV_LENGTH, salt, 0, SALT_LENGTH);
			
			if(!setupCrypto(iv, salt)){
				return null;
			}
			
			byte[] cipherBytes = new byte[ciphertext.length-IV_LENGTH-SALT_LENGTH];
			System.arraycopy(ciphertext, IV_LENGTH+SALT_LENGTH, cipherBytes, 0, ciphertext.length-IV_LENGTH);
			
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

	public static byte[] getHash(String string) throws AESCryptException {
		return getHash(string, HASH_ALGORITHM);
	}
	
	public static byte[] getHash(String string, String hashAlgorithm) throws AESCryptException {
		try {
			MessageDigest md = MessageDigest.getInstance(hashAlgorithm, PROVIDER);
			return md.digest(string.getBytes("UTF-8"));
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
			return new SecretKeySpec(tmp.getEncoded(), SECRET_KEY_ALGORITHM);
		}
		catch (Exception e) {
			throw new AESCryptException("Unable to get secret key");
		}
	}
	
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
