package com.fenritz.safecam.util;

import android.content.Context;
import android.os.AsyncTask;

import com.fenritz.safecam.SafeCameraApplication;
import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.AEAD;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.interfaces.PwHash;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;
import com.goterl.lazycode.lazysodium.interfaces.SecretStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Crypto {

    protected Context context;
    protected SodiumAndroid so;
    protected LazySodiumAndroid ls;
    protected Box.Lazy box;
    protected SecretStream.Lazy secretStream;

    private static final String HEADER = "SC";
    private static final int CURRENT_VERSION = 2;

    protected int bufSize = 1024*256;

    String PWD_SALT_FILENAME = "pwdSalt";
    String SK_NONCE_FILENAME = "skNonce";
    String PRIVATE_KEY_FILENAME = "private";
    String PUBLIC_KEY_FILENAME = "public";

    public Crypto(Context context){
        this.context = context;
        so = new SodiumAndroid();
        ls = new LazySodiumAndroid(so);
        box = (Box.Lazy) ls;
        secretStream = (SecretStream.Lazy) ls;
    }

    public void generateMainKeypair(String password) throws CryptoException{

        // Generate key derivation salt and save it
        byte[] pwdSalt = new byte[PwHash.ARGON2ID_SALTBYTES];
        so.randombytes_buf(pwdSalt, pwdSalt.length);
        savePrivateFile(PWD_SALT_FILENAME, pwdSalt);

        // Generate main keypair
        byte[] privateKey = new byte[Box.SECRETKEYBYTES];
        byte[] publicKey = new byte[Box.PUBLICKEYBYTES];
        so.crypto_box_keypair(publicKey, privateKey);

        // Derive symmetric encryption key from password
        byte[] pwdKey = getKeyFromPassword(password);

        // Generate random nonce, save it and encrypt private key
        byte[] pwdEncNonce = new byte[SecretBox.NONCEBYTES];
        so.randombytes_buf(pwdEncNonce, pwdEncNonce.length);
        savePrivateFile(SK_NONCE_FILENAME, pwdEncNonce);
        byte[] encryptedPrivateKey = encryptSymmetric(pwdKey, pwdEncNonce, privateKey);

        // Save public and private keys
        savePrivateFile(PRIVATE_KEY_FILENAME, encryptedPrivateKey);
        savePrivateFile(PUBLIC_KEY_FILENAME, publicKey);
    }

    public byte[] getPrivateKey(String password) throws CryptoException{
        byte[] encKey = getKeyFromPassword(password);

        byte[] encPrivKey = readPrivateFile(PRIVATE_KEY_FILENAME);

        return decryptSymmetric(encKey, readPrivateFile(SK_NONCE_FILENAME), encPrivKey);
    }

    public byte[] getKeyFromPassword(String password) throws CryptoException{
        byte[] salt = readPrivateFile(PWD_SALT_FILENAME);
        if(salt.length != PwHash.ARGON2ID_SALTBYTES){
            throw new CryptoException("Invalid salt for password derivation");
        }

        byte[] key = new byte[SecretBox.KEYBYTES];
        byte[] passwordBytes = password.getBytes();

        so.crypto_pwhash(key, key.length, passwordBytes, passwordBytes.length, salt, PwHash.OPSLIMIT_INTERACTIVE, PwHash.MEMLIMIT_INTERACTIVE, PwHash.Alg.PWHASH_ALG_ARGON2ID13.getValue());

        return key;
    }

    public String getPasswordHashForStorage(String password) throws CryptoException{
        byte[] hashedPassword = new byte[PwHash.ARGON2ID_STR_BYTES];
        byte[] passwordBytes = password.getBytes();

        if(so.crypto_pwhash_str(hashedPassword, passwordBytes, passwordBytes.length, PwHash.OPSLIMIT_INTERACTIVE, PwHash.MEMLIMIT_INTERACTIVE) != 0 ){
            throw new CryptoException("Unable to derive storage key");
        }

        return ls.toHexStr(hashedPassword);
    }

    public boolean verifyStoredPassword(String storedPassword, String providedPassword){
        byte[] hashedPassword = ls.toBinary(storedPassword);
        byte[] passwordBytes = providedPassword.getBytes();

        if(so.crypto_pwhash_str_verify(hashedPassword, passwordBytes, passwordBytes.length) == 0){
            return true;
        }
        return false;
    }

    protected byte[] encryptSymmetric(byte[] key, byte[] nonce, byte[] data){
        if(key.length != SecretBox.KEYBYTES){
            throw new InvalidParameterException("Invalid size of the key");
        }
        if(nonce.length != SecretBox.NONCEBYTES){
            throw new InvalidParameterException("Invalid size of the key");
        }

        byte[] cypherText = new byte[data.length + SecretBox.MACBYTES];
        so.crypto_secretbox_easy(cypherText, data, data.length, nonce, key);

        return cypherText;
    }

    protected byte[] decryptSymmetric(byte[] key, byte[] nonce, byte[] data) throws CryptoException{
        if(key.length != SecretBox.KEYBYTES){
            throw new InvalidParameterException("Invalid size of the key");
        }
        if(nonce.length != SecretBox.NONCEBYTES){
            throw new InvalidParameterException("Invalid size of the key");
        }

        byte[] plainText = new byte[data.length - SecretBox.MACBYTES];
        if(so.crypto_secretbox_open_easy(plainText, data, data.length, nonce, key) != 0){
            throw new CryptoException("Unable to decrypt");
        }

        return plainText;
    }

    protected boolean savePrivateFile(String filename, byte[] data){
        FileOutputStream outputStream;

        try {
            outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(data);
            outputStream.close();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    protected byte[] readPrivateFile(String filename){
        FileInputStream inputStream;

        try {
            int numRead = 0;
            byte[] buf = new byte[bufSize];
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            inputStream = context.openFileInput(filename);
            while ((numRead = inputStream.read(buf)) >= 0) {
                byteBuffer.write(buf, 0, numRead);
            }
            return byteBuffer.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    protected void generateAndWriteHeader(OutputStream out, byte[] publicKey, String filename, byte[] symmetricKey) throws IOException{
        out.write(HEADER.getBytes());
        out.write(CURRENT_VERSION);

        int encFilenameDataLength = 0;
        if(filename != null) {
            // Encrypt filename with public key
            byte[] filenameBytes = filename.getBytes();

            encFilenameDataLength = filenameBytes.length + Box.SEALBYTES;
            byte[] encryptedFilename = new byte[encFilenameDataLength];
            so.crypto_box_seal(encryptedFilename, filenameBytes, filenameBytes.length, publicKey);
            out.write(intToByteArray(encFilenameDataLength));
            out.write(encryptedFilename);

        }
        else{
            out.write(intToByteArray(encFilenameDataLength));
        }

        byte[] encryptedKey = new byte[symmetricKey.length + Box.SEALBYTES];
        so.crypto_box_seal(encryptedKey, symmetricKey, symmetricKey.length, publicKey);
        out.write(encryptedKey);
    }

    protected int readHeaderBeggining(InputStream in) throws IOException{
        byte[] header = new byte[HEADER.length()];
        in.read(header);

        if (!new String(header, "UTF-8").equals(HEADER)) {
            throw new IOException("Invalid file header");
        }

        int fileVersion = in.read();
        if (fileVersion != CURRENT_VERSION) {
            throw new IOException("Unsupported version number: " + fileVersion);
        }

        byte[] encFilenameLengthBytes = new byte[4];
        in.read(encFilenameLengthBytes);
        int encFilenameLength = byteArrayToInt(encFilenameLengthBytes);
        return encFilenameLength;
    }

    protected byte[] getFileSymmetricKey(InputStream in, byte[] publicKey, byte[] privateKey) throws IOException, CryptoException{
        int encFilenameLength = readHeaderBeggining(in);

        in.skip(encFilenameLength);

        byte[] encSymmetricKey = new byte[AEAD.CHACHA20POLY1305_KEYBYTES + Box.SEALBYTES];
        in.read(encSymmetricKey);

        byte[] symmetricKey = new byte[AEAD.CHACHA20POLY1305_KEYBYTES];

        if(so.crypto_box_seal_open(symmetricKey, encSymmetricKey, encSymmetricKey.length, publicKey, privateKey) != 0){
            throw new CryptoException("Unable to decrypt symmetric key");
        }

        return symmetricKey;
    }

    public String getFilename(InputStream in){
        String filename = "";
        byte[] publicKey = readPrivateFile(PUBLIC_KEY_FILENAME);
        byte[] privateKey = SafeCameraApplication.getKey();

        try {
            int encFilenameLength = readHeaderBeggining(in);
            if(encFilenameLength > 0 && encFilenameLength < 1024*1024*16) {
                byte[] encFilename = new byte[encFilenameLength];
                in.read(encFilename);

                byte[] decFilename = new byte[encFilenameLength - Box.SEALBYTES];

                if (so.crypto_box_seal_open(decFilename, encFilename, encFilename.length, publicKey, privateKey) != 0) {
                    throw new CryptoException("Unable to decrypt filename");
                }
                filename = new String(decFilename);
            }
        }
        catch (IOException e){}
        catch (CryptoException e){}

        return filename;
    }

    public void encryptFile(OutputStream out, byte[] data, String filename) throws IOException, CryptoException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        encryptFile(in, out, filename, data.length);
    }

    public void encryptFile(InputStream in, OutputStream out, String filename, long inputLength) throws IOException, CryptoException {
        byte[] publicKey = readPrivateFile(PUBLIC_KEY_FILENAME);

        byte[] symmetricKey = new byte[AEAD.CHACHA20POLY1305_KEYBYTES];
        so.crypto_secretstream_xchacha20poly1305_keygen(symmetricKey);

        generateAndWriteHeader(out, publicKey, filename, symmetricKey);

        encrypt(symmetricKey, in, out, inputLength);
    }


    public byte[] decryptFile(InputStream in) throws IOException, CryptoException{
        return decryptFile(in, null, null);
    }

    public byte[] decryptFile(InputStream in, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException{
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        decryptFile(in, out, progress, task);

        return out.toByteArray();
    }

    public void decryptFile(InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException{
        long time = System.nanoTime();
        byte[] publicKey = readPrivateFile(PUBLIC_KEY_FILENAME);
        byte[] privateKey = SafeCameraApplication.getKey();

        byte[] symmetricKey = getFileSymmetricKey(in, publicKey, privateKey);

        decrypt(symmetricKey, in, out, progress, task);
        long time2 = System.nanoTime();
    }


    protected boolean encrypt(byte[] key, InputStream in, OutputStream out, long inputLength) throws IOException, CryptoException {
        return this.encrypt(key, in, out, inputLength, null, null);
    }

    protected boolean encrypt(byte[] key, InputStream in, OutputStream out, long inputLength, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException {
        if(key == null) {
            throw new CryptoException("Key is empty");
        }

        out.write(intToByteArray(bufSize));

        byte[] header = new byte[SecretStream.HEADERBYTES];
        SecretStream.State state = new SecretStream.State.ByReference();
        so.crypto_secretstream_xchacha20poly1305_init_push(state, header, key);
        out.write(header);

        int numRead = 0;
        int count = 0;
        byte[] buf = new byte[bufSize];
        long totalRead = header.length;
        int chunkCount = (int)Math.ceil((double)inputLength/(double)bufSize);
        while ((numRead = in.read(buf)) >= 0) {
            count++;
            byte tag = SecretStream.XCHACHA20POLY1305_TAG_MESSAGE;
            if(numRead < bufSize || chunkCount==count) {
                tag = SecretStream.XCHACHA20POLY1305_TAG_FINAL;
            }

            byte[] encBytes = new byte[bufSize + SecretStream.ABYTES];
            long[] encSize = new long[1];
            int res = so.crypto_secretstream_xchacha20poly1305_push(state, encBytes, encSize, buf, numRead, null, 0, tag);
            if (res != 0) {
                throw new CryptoException("Error when encrypting a message using secret stream.");
            }

            out.write(encBytes, 0, (int)encSize[0]);

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
        return true;
    }

    protected boolean decrypt(byte[] key, InputStream in, OutputStream out) throws IOException, CryptoException {
        return this.decrypt(key, in, out, null, null);
    }
    protected boolean decrypt(byte[] key, InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException {
        //int curBufSize = bufSize;

        byte[] curBufSizeBytes = new byte[4];
        in.read(curBufSizeBytes);

        int curBufSize = byteArrayToInt(curBufSizeBytes);

        if(curBufSize < 1 || curBufSize > 1024*1024*16){
            curBufSize = bufSize;
        }

        byte[] header = new byte[SecretStream.HEADERBYTES];

        in.read(header);
        SecretStream.State state = new SecretStream.State.ByReference();
        int resInit = so.crypto_secretstream_xchacha20poly1305_init_pull(state, header, key);

        if (resInit != 0) {
            throw new CryptoException("Could not initialise a decryption state.");
        }

        int numRead = 0;
        byte[] bufDec = new byte[curBufSize+SecretStream.ABYTES];
        long totalRead = header.length;
        byte[] tag = new byte[1];
        while ((numRead = in.read(bufDec)) >= 0) {
            byte[] decBytes = new byte[curBufSize];
            long[] decSize = new long[1];
            int res = so.crypto_secretstream_xchacha20poly1305_pull(state, decBytes, decSize, tag, bufDec, numRead, null, 0);

            if(res != 0){
                throw new CryptoException("Error when decrypting a message using secret stream.");
            }

            out.write(decBytes, 0, (int)decSize[0]);
            if(tag[0] == SecretStream.XCHACHA20POLY1305_TAG_FINAL){
                break;
            }

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

        return true;
    }

    public byte[] sha256(byte[] data){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public String byte2hex(byte[] data){
        return ls.sodiumBin2Hex(data);
    }

    public byte[] hex2byte(String data){
        return ls.sodiumHex2Bin(data);
    }

    public byte[] intToByteArray(int a){
        byte[] ret = new byte[4];
        ret[3] = (byte) (a & 0xFF);
        ret[2] = (byte) ((a >> 8) & 0xFF);
        ret[1] = (byte) ((a >> 16) & 0xFF);
        ret[0] = (byte) ((a >> 24) & 0xFF);
        return ret;
    }

    public int byteArrayToInt(byte[] b)
    {
        return (b[3] & 0xFF) + ((b[2] & 0xFF) << 8) + ((b[1] & 0xFF) << 16) + ((b[0] & 0xFF) << 24);
    }
}
