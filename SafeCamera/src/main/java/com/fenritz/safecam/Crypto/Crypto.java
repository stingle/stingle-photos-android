package com.fenritz.safecam.Crypto;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.fenritz.safecam.SafeCameraApplication;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.AEAD;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.interfaces.KeyDerivation;
import com.goterl.lazycode.lazysodium.interfaces.PwHash;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

public class Crypto {

    protected Context context;
    protected SodiumAndroid so;

    public static final int FILE_TYPE_GENERAL = 1;
    public static final int FILE_TYPE_PHOTO = 2;
    public static final int FILE_TYPE_VIDEO = 3;

    protected static final String FILE_BEGGINING = "SP";
    protected static final String KEY_FILE_BEGGINING = "SPK";
    protected static final int CURRENT_FILE_VERSION = 1;
    protected static final int CURRENT_HEADER_VERSION = 1;
    protected static final int CURRENT_KEY_FILE_VERSION = 1;

    protected static final String PWD_SALT_FILENAME = "pwdSalt";
    protected static final String SK_NONCE_FILENAME = "skNonce";
    protected static final String PRIVATE_KEY_FILENAME = "private";
    protected static final String PUBLIC_KEY_FILENAME = "public";

    public static final String XCHACHA20POLY1305_IETF_CONTEXT = "__data__";
    public static final int MAX_BUFFER_LENGTH = 1024*1024*64;

    public static final int FILE_BEGGINIG_LEN = FILE_BEGGINING.length();
    public static final int KEY_FILE_BEGGINIG_LEN = KEY_FILE_BEGGINING.length();
    public static final int FILE_FILE_VERSION_LEN = 1;
    public static final int FILE_CHUNK_SIZE_LEN = 4;
    public static final int FILE_DATA_SIZE_LEN = 8;
    public static final int FILE_HEADER_SIZE_LEN = 4;
    public static final int FILE_FILE_ID_LEN = 32;
    public static final int FILE_HEADER_BEGINNING_LEN =
            FILE_BEGGINIG_LEN +
            FILE_FILE_VERSION_LEN +
            FILE_FILE_ID_LEN +
            FILE_HEADER_SIZE_LEN;

    public static final int KEY_FILE_TYPE_BUNDLE_ENCRYPTED = 0;
    public static final int KEY_FILE_TYPE_BUNDLE_PLAIN = 1;
    public static final int KEY_FILE_TYPE_PUBLIC_PLAIN = 2;

    protected int bufSize = 1024 * 1024;


    public Crypto(Context context){
        this.context = context;
        so = new SodiumAndroid();
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

    public byte[] exportKeyBundle() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(KEY_FILE_BEGGINING.getBytes());
        out.write(CURRENT_KEY_FILE_VERSION);
        out.write(KEY_FILE_TYPE_BUNDLE_ENCRYPTED);
        out.write(readPrivateFile(PUBLIC_KEY_FILENAME));
        out.write(readPrivateFile(PRIVATE_KEY_FILENAME));
        out.write(readPrivateFile(PWD_SALT_FILENAME));
        out.write(readPrivateFile(SK_NONCE_FILENAME));
        return out.toByteArray();
    }

    public byte[] exportPublicKey() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(KEY_FILE_BEGGINING.getBytes());
        out.write(CURRENT_KEY_FILE_VERSION);
        out.write(KEY_FILE_TYPE_PUBLIC_PLAIN);
        out.write(readPrivateFile(PUBLIC_KEY_FILENAME));
        return out.toByteArray();
    }

    public void importKeyBundle(byte[] keys) throws IOException, CryptoException {
        ByteArrayInputStream in = new ByteArrayInputStream(keys);

        byte[] fileBeginning = new byte[KEY_FILE_BEGGINIG_LEN];
        in.read(fileBeginning);

        if (!new String(fileBeginning, "UTF-8").equals(KEY_FILE_BEGGINING)) {
            throw new CryptoException("Invalid file header, not our file");
        }

        int keyFileVersion = in.read();
        if (keyFileVersion != CURRENT_KEY_FILE_VERSION) {
            throw new CryptoException("Unsupported key file version number: " + String.valueOf(keyFileVersion));
        }

        int keyFileType = in.read();
        if (keyFileType != KEY_FILE_TYPE_BUNDLE_ENCRYPTED) {
            throw new CryptoException("Can't import! This is not a encrypted key file");
        }

        byte[] publicKey = new byte[Box.PUBLICKEYBYTES];
        byte[] encryptedPrivateKey = new byte[Box.SECRETKEYBYTES + SecretBox.MACBYTES];
        byte[] pwdSalt = new byte[PwHash.ARGON2ID_SALTBYTES];
        byte[] skNonce = new byte[SecretBox.NONCEBYTES];

        in.read(publicKey);
        in.read(encryptedPrivateKey);
        in.read(pwdSalt);
        in.read(skNonce);

        savePrivateFile(PUBLIC_KEY_FILENAME, publicKey);
        savePrivateFile(PRIVATE_KEY_FILENAME, encryptedPrivateKey);
        savePrivateFile(PWD_SALT_FILENAME, pwdSalt);
        savePrivateFile(SK_NONCE_FILENAME, skNonce);
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

        return byte2hex(hashedPassword);
    }

    public boolean verifyStoredPassword(String storedPassword, String providedPassword){
        byte[] hashedPassword = hex2byte(storedPassword);
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


    protected Header getNewHeader(byte[] symmetricKey, long dataSize, String filename, int fileType, byte[] fileId) throws CryptoException {
        if(symmetricKey.length != KeyDerivation.MASTER_KEY_BYTES){
            throw new CryptoException("Symmetric key length is incorrect");
        }

        Header header = new Header();

        header.fileVersion = CURRENT_FILE_VERSION;
        header.headerVersion = CURRENT_HEADER_VERSION;
        header.fileType = fileType;
        header.chunkSize = bufSize;
        header.dataSize = dataSize;
        header.symmetricKey = symmetricKey;
        header.fileId = fileId;
        header.filename = filename;

        return header;
    }

    protected void writeHeader(OutputStream out, Header header, byte[] publicKey) throws IOException, CryptoException {

        // File beggining - 2 bytes
        out.write(FILE_BEGGINING.getBytes());

        // File version number - 1 byte
        out.write(CURRENT_FILE_VERSION);

        // File ID - 32 bytes
        out.write(header.fileId);

        ///////////////////////////
        // Create encrypted header
        ///////////////////////////

        ByteArrayOutputStream headerByteStream = new ByteArrayOutputStream();
        // Current header version - 1 byte
        headerByteStream.write(header.headerVersion);

        // Chunk size - 4 bytes
        headerByteStream.write(intToByteArray(header.chunkSize));

        // Data size - 8 bytes
        headerByteStream.write(longToByteArray(header.dataSize));

        // Symmentric key - KeyDerivation.MASTER_KEY_BYTES(32 bytes)
        headerByteStream.write(header.symmetricKey);

        // File type - 1 byte
        headerByteStream.write(header.fileType);

        if(header.filename != null && header.filename != "") {
            byte[] filenameBytes = header.filename.getBytes();

            // Filename length
            headerByteStream.write(intToByteArray(filenameBytes.length));

            // Filename itself
            headerByteStream.write(filenameBytes);
        }
        else{
            // If filename is empty then write that filename length is 0
            headerByteStream.write(intToByteArray(0));
        }

        byte[] headerBytes = headerByteStream.toByteArray();

        int encHeaderLength = headerBytes.length + Box.SEALBYTES;

        byte[] encryptedHeader = new byte[encHeaderLength];
        so.crypto_box_seal(encryptedHeader, headerBytes, headerBytes.length, publicKey);

        // Write header size
        out.write(intToByteArray(encHeaderLength));

        // Write header
        out.write(encryptedHeader);
    }


    public Header getFileHeader(byte[] bytes) throws IOException, CryptoException{
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return getFileHeader(in);
    }

    public Header getFileHeader(InputStream in) throws IOException, CryptoException{
        int overallHeaderSize = 0;
        Header header = new Header();

        byte[] publicKey = readPrivateFile(PUBLIC_KEY_FILENAME);
        byte[] privateKey = SafeCameraApplication.getKey();

        // Read and validate file beginning
        byte[] fileBeginning = new byte[FILE_BEGGINIG_LEN];
        in.read(fileBeginning);
        overallHeaderSize += FILE_BEGGINIG_LEN;

        if (!new String(fileBeginning, "UTF-8").equals(FILE_BEGGINING)) {
            throw new CryptoException("Invalid file header, not our file");
        }

        // Read and validate file version
        header.fileVersion = in.read();
        if (header.fileVersion != CURRENT_FILE_VERSION) {
            throw new CryptoException("Unsupported version number: " + String.valueOf(header.fileVersion));
        }
        overallHeaderSize += FILE_FILE_VERSION_LEN;

        // Read File ID
        header.fileId = new byte[FILE_FILE_ID_LEN];
        in.read(header.fileId);
        overallHeaderSize += FILE_FILE_ID_LEN;


        // Read header size
        byte[] headerSizeBytes = new byte[FILE_HEADER_SIZE_LEN];
        in.read(headerSizeBytes);
        int headerSize = byteArrayToInt(headerSizeBytes);

        if(headerSize < 1 || headerSize > MAX_BUFFER_LENGTH){
            throw new CryptoException("Invalid header size");
        }
        header.headerSize = headerSize;
        overallHeaderSize += FILE_HEADER_SIZE_LEN;

        // Read header
        byte[] encHeader = new byte[headerSize];
        in.read(encHeader);
        overallHeaderSize += headerSize;

        // Decrypt header
        byte[] headerBytes = new byte[headerSize - Box.SEALBYTES];

        if(so.crypto_box_seal_open(headerBytes, encHeader, encHeader.length, publicKey, privateKey) != 0){
            throw new CryptoException("Unable to decrypt file header");
        }

        // Parse header
        ByteArrayInputStream headerStream = new ByteArrayInputStream(headerBytes);


        // Read and validate header version
        header.headerVersion = headerStream.read();
        if (header.headerVersion != CURRENT_HEADER_VERSION) {
            throw new CryptoException("Unsupported header version number: " + header.headerVersion);
        }

        // Read chunk size
        byte[] chunkSizeBytes = new byte[FILE_CHUNK_SIZE_LEN];
        headerStream.read(chunkSizeBytes);
        header.chunkSize = byteArrayToInt(chunkSizeBytes);

        if(header.chunkSize < 1 || header.chunkSize > MAX_BUFFER_LENGTH){
            throw new CryptoException("Invalid chunk size");
        }


        // Read data size
        byte[] dataSizeBytes = new byte[FILE_DATA_SIZE_LEN];
        headerStream.read(dataSizeBytes);
        header.dataSize = byteArrayToLong(dataSizeBytes);


        // Read symmetric master key
        byte[] symmetricKey = new byte[KeyDerivation.MASTER_KEY_BYTES];
        headerStream.read(symmetricKey);
        header.symmetricKey = symmetricKey;


        // Read file type
        header.fileType = headerStream.read();


        // Read filename size
        byte[] filenameSizeBytes = new byte[4];
        headerStream.read(filenameSizeBytes);
        int filenameSize = byteArrayToInt(filenameSizeBytes);

        if(filenameSize > 0) {
            // Read filename
            byte[] filenameBytes = new byte[filenameSize];
            headerStream.read(filenameBytes);
            header.filename = new String(filenameBytes);
        }
        else{
            header.filename = "";
        }

        header.overallHeaderSize = overallHeaderSize;

        return header;
    }

    public String getFilename(InputStream in){
        String filename = "";

        try {
            Header header = getFileHeader(in);
            filename = header.filename;
        }
        catch (IOException | CryptoException e){}

        return filename;
    }

    public byte[] encryptFile(OutputStream out, byte[] data, String filename, int fileType) throws IOException, CryptoException {
        return encryptFile(out, data, filename, fileType, null);
    }

    public byte[] encryptFile(OutputStream out, byte[] data, String filename, int fileType, byte[] fileId) throws IOException, CryptoException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        return encryptFile(in, out, filename, fileType, data.length, fileId);
    }

    public byte[] encryptFile(InputStream in, OutputStream out, String filename, int fileType, long dataLength) throws IOException, CryptoException {
        return encryptFile(in, out, filename, fileType, dataLength, null, null, null);
    }

    public byte[] encryptFile(InputStream in, OutputStream out, String filename, int fileType, long dataLength, byte[] fileId) throws IOException, CryptoException {
        return encryptFile(in, out, filename, fileType, dataLength, fileId, null, null);
    }
    public byte[] encryptFile(InputStream in, OutputStream out, String filename, int fileType, long dataLength, byte[] fileId, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException {
        long time = System.nanoTime();
        byte[] publicKey = readPrivateFile(PUBLIC_KEY_FILENAME);

        byte[] symmetricKey = new byte[KeyDerivation.MASTER_KEY_BYTES];
        so.crypto_kdf_keygen(symmetricKey);

        if(fileId == null) {
            fileId = new byte[FILE_FILE_ID_LEN];
            so.randombytes_buf(fileId, FILE_FILE_ID_LEN);
        }

        Header header = getNewHeader(symmetricKey, dataLength, filename, fileType, fileId);
        writeHeader(out, header, publicKey);

        encryptData(in, out, header, progress, task);
        long time2 = System.nanoTime();
        long diff = time2 - time;
        //Log.d("time", String.valueOf((double)diff / 1000000000.0));

        return fileId;
    }


    public byte[] decryptFile(byte[] bytes) throws IOException, CryptoException{
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return decryptFile(in, null, null);
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

        Header header = getFileHeader(in);

        decryptData(in, out, header, progress, task);
        long time2 = System.nanoTime();
        long diff = time2 - time;
        //Log.d("time", String.valueOf((double)diff / 1000000000.0));
    }

    public byte[] getRandomData(int length){
        byte[] data = new byte[length];
        so.randombytes_buf(data, data.length);
        return data;
    }


    protected boolean encryptData(InputStream in, OutputStream out, Header header) throws IOException, CryptoException {
        return encryptData(in, out, header, null, null);
    }

    protected boolean encryptData(InputStream in, OutputStream out, Header header, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException {
        if(header.symmetricKey == null) {
            throw new CryptoException("Key is empty");
        }

        long totalRead = 0;

        int numRead = 0;
        int chunkNumber = 1;
        byte[] buf = new byte[header.chunkSize];

        byte[] chunkKey = new byte[AEAD.XCHACHA20POLY1305_IETF_KEYBYTES];
        byte[] chunkNonce = new byte[AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES];
        byte[] contextBytes = XCHACHA20POLY1305_IETF_CONTEXT.getBytes();

        while ((numRead = in.read(buf)) >= 0) {
            so.randombytes_buf(chunkNonce, chunkNonce.length);
            so.crypto_kdf_derive_from_key(chunkKey, chunkKey.length, chunkNumber, contextBytes, header.symmetricKey);

            byte[] encBytes = new byte[header.chunkSize + AEAD.XCHACHA20POLY1305_IETF_ABYTES];
            long[] encSize = new long[1];
            if(so.crypto_aead_xchacha20poly1305_ietf_encrypt(encBytes, encSize, buf, numRead, null, 0, null, chunkNonce, chunkKey) != 0){
                throw new CryptoException("Error when encrypting data.");
            }

            out.write(chunkNonce);
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

            chunkNumber++;
        }

        out.close();
        in.close();
        return true;
    }

    protected boolean decryptData(InputStream in, OutputStream out, Header header) throws IOException, CryptoException {
        return decryptData(in, out, header, null, null);
    }
    protected boolean decryptData(InputStream in, OutputStream out, Header header, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException {

        if(header.chunkSize < 1 || header.chunkSize > MAX_BUFFER_LENGTH){
            throw new CryptoException("Invalid chunk size");
        }

        int numRead = 0;
        long totalRead = 0;

        byte[] bufIn = new byte[AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES + header.chunkSize + AEAD.XCHACHA20POLY1305_IETF_ABYTES];
        byte[] encChunkBytes = new byte[header.chunkSize + AEAD.XCHACHA20POLY1305_IETF_ABYTES];

        int chunkNumber = 1;
        byte[] chunkKey = new byte[AEAD.XCHACHA20POLY1305_IETF_KEYBYTES];
        byte[] chunkNonce = new byte[AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES];
        byte[] contextBytes = XCHACHA20POLY1305_IETF_CONTEXT.getBytes();


        while ((numRead = in.read(bufIn)) >= 0) {
            if(numRead < AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES + AEAD.XCHACHA20POLY1305_IETF_ABYTES + 1){
                throw new CryptoException("Invalid chunk length");
            }
            chunkNonce = Arrays.copyOfRange(bufIn, 0, AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES);
            encChunkBytes = Arrays.copyOfRange(bufIn, AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES, numRead);
            so.crypto_kdf_derive_from_key(chunkKey, chunkKey.length, chunkNumber, contextBytes, header.symmetricKey);

            byte[] decBytes = new byte[header.chunkSize];
            long[] decSize = new long[1];
            if(so.crypto_aead_xchacha20poly1305_ietf_decrypt(decBytes, decSize, null, encChunkBytes, encChunkBytes.length, null, 0, chunkNonce, chunkKey) != 0){
                throw new CryptoException("Error when decrypting data.");
            }

            out.write(decBytes, 0, (int)decSize[0]);

            if(progress != null){
                totalRead += numRead;
                progress.setProgress(totalRead);
            }
            if(task != null){
                if(task.isCancelled()){
                    break;
                }
            }

            chunkNumber++;
        }

        out.close();
        in.close();

        return true;
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

    public static byte[] sha256(byte[] data){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String byte2hex(byte[] bytes){
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hex2byte(String s){
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static byte[] intToByteArray(int a){
        byte[] ret = new byte[4];
        ret[3] = (byte) (a & 0xFF);
        ret[2] = (byte) ((a >> 8) & 0xFF);
        ret[1] = (byte) ((a >> 16) & 0xFF);
        ret[0] = (byte) ((a >> 24) & 0xFF);
        return ret;
    }

    public static int byteArrayToInt(byte[] b)
    {
        return (b[3] & 0xFF) + ((b[2] & 0xFF) << 8) + ((b[1] & 0xFF) << 16) + ((b[0] & 0xFF) << 24);
    }

    public static byte[] longToByteArray(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public static long byteArrayToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public static String byteArrayToBase64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); //base64 encoding
    }
    public static byte[] base64ToByteArray(String base64str) {
        return Base64.getUrlDecoder().decode(base64str); //base64 decoding
    }

    public class Header{
        public int fileVersion;
        public byte[] fileId;
        public int headerSize;

        public int headerVersion;
        public int chunkSize;
        public long dataSize;
        public byte[] symmetricKey;
        public int fileType;
        public String filename;

        public int overallHeaderSize = 0;

        public String toString(){
            return "\n" +
                    "File Version - " + String.valueOf(fileVersion) + "\n" +
                    "File ID - " + byteArrayToBase64(fileId) + "\n" +
                    "Header Size - " + String.valueOf(headerSize) + "\n\n" +

                    "Header Version - " + String.valueOf(headerVersion) + "\n" +
                    "Chunk Size - " + String.valueOf(chunkSize) + "\n" +
                    "Data Size - " + String.valueOf(dataSize) + "\n" +
                    "Symmetric Key - " + byteArrayToBase64(symmetricKey) + "\n" +
                    "File Type - " + String.valueOf(fileType) + "\n" +
                    "Filename - " + filename + "\n\n" +

                    "Overall Header Size - " + String.valueOf(overallHeaderSize);
        }
    }
}
