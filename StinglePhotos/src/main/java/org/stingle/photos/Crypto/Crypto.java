package org.stingle.photos.Crypto;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Base64;

import com.goterl.lazysodium.LazySodium;
import com.goterl.lazysodium.LazySodiumAndroid;
import com.goterl.lazysodium.exceptions.SodiumException;
import com.goterl.lazysodium.interfaces.AEAD;
import com.goterl.lazysodium.interfaces.Box;
import com.goterl.lazysodium.interfaces.GenericHash;
import com.goterl.lazysodium.interfaces.KeyDerivation;
import com.goterl.lazysodium.interfaces.PwHash;
import com.goterl.lazysodium.interfaces.SecretBox;
import com.goterl.lazysodium.SodiumAndroid;
import com.sun.jna.NativeLong;

import org.stingle.photos.StinglePhotosApplication;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public class Crypto {

	protected Context context;
	protected final SodiumAndroid so;
	protected final LazySodium ls;

    public static final int FILE_TYPE_GENERAL = 1;
    public static final int FILE_TYPE_PHOTO = 2;
    public static final int FILE_TYPE_VIDEO = 3;

    public static final String FILE_BEGGINING = "SP";
    protected static final String KEY_FILE_BEGGINING = "SPK";
    protected static final int CURRENT_FILE_VERSION = 1;
    protected static final int CURRENT_HEADER_VERSION = 1;
    protected static final int CURRENT_KEY_FILE_VERSION = 1;
    protected static final int CURRENT_ALBUM_METADATA_VERSION = 1;

    protected static final String PWD_SALT_FILENAME = "pwdSalt";
    protected static final String SK_NONCE_FILENAME = "skNonce";
    protected static final String PRIVATE_KEY_FILENAME = "private";
    protected static final String PUBLIC_KEY_FILENAME = "public";
    protected static final String SERVER_PUBLIC_KEY_FILENAME = "server_public";
    protected static final String PLAIN_PRIVATE_KEY_FILENAME = "private_plain";

    public static final String XCHACHA20POLY1305_IETF_CONTEXT = "__data__";
    public static final int MAX_BUFFER_LENGTH = 1024*1024*64;

    public static final int FILE_BEGGINIG_LEN = FILE_BEGGINING.length();
    public static final int KEY_FILE_BEGGINIG_LEN = KEY_FILE_BEGGINING.length();
    public static final int FILE_FILE_VERSION_LEN = 1;
    public static final int FILE_CHUNK_SIZE_LEN = 4;
    public static final int FILE_DATA_SIZE_LEN = 8;
    public static final int FILE_VIDEO_DURATION_LEN = 4;
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

    public static final int KEY_FILE_BEGGINING_LEN = KEY_FILE_BEGGINING.length();
    public static final int KEY_FILE_VER_LEN = 1;
    public static final int KEY_FILE_TYPE_LEN = 1;
    public static final int KEY_FILE_HEADER_LEN = KEY_FILE_BEGGINING_LEN + KEY_FILE_VER_LEN + KEY_FILE_TYPE_LEN;

    public static final int KDF_DIFFICULTY_NORMAL = 1;
    public static final int KDF_DIFFICULTY_HARD = 2;
    public static final int KDF_DIFFICULTY_ULTRA = 3;

    public static final int PWHASH_LEN = 64;

    protected int bufSize = 1024 * 1024;


    public Crypto(Context context){
        this.context = context;
        so = new SodiumAndroid();
        ls = new LazySodiumAndroid(so);
    }

    public void generateMainKeypair(String password) throws CryptoException{
        generateMainKeypair(password, null, null);
    }

    public void generateMainKeypair(String password, byte[] privateKey, byte[] publicKey) throws CryptoException{

        // Generate key derivation salt and save it
        byte[] pwdSalt = new byte[PwHash.ARGON2ID_SALTBYTES];
        so.randombytes_buf(pwdSalt, pwdSalt.length);
        savePrivateFile(PWD_SALT_FILENAME, pwdSalt);

        if(privateKey == null || publicKey == null) {
            // Generate main keypair
            privateKey = new byte[Box.SECRETKEYBYTES];
            publicKey = new byte[Box.PUBLICKEYBYTES];
            so.crypto_box_keypair(publicKey, privateKey);
        }

        // Derive symmetric encryption key from password
        byte[] pwdKey = getKeyFromPassword(password, KDF_DIFFICULTY_NORMAL);

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
        byte[] encKey = getKeyFromPassword(password, KDF_DIFFICULTY_NORMAL);

        byte[] encPrivKey = readPrivateFile(PRIVATE_KEY_FILENAME);

        return decryptSymmetric(encKey, readPrivateFile(SK_NONCE_FILENAME), encPrivKey);
    }

    public void reencryptPrivateKey(String oldPassword, String newPassword) throws CryptoException{
        byte[] privateKey = getPrivateKey(oldPassword);

        byte[] pwdKey = getKeyFromPassword(newPassword, KDF_DIFFICULTY_NORMAL);
        byte[] pwdEncNonce = readPrivateFile(SK_NONCE_FILENAME);

        byte[] encryptedPrivateKey = encryptSymmetric(pwdKey, pwdEncNonce, privateKey);

        savePrivateFile(PRIVATE_KEY_FILENAME, encryptedPrivateKey);
    }

    public byte[] getEncryptedPrivateKey(){
        return readPrivateFile(PRIVATE_KEY_FILENAME);
    }

    public byte[] getPublicKey(){
        return readPrivateFile(PUBLIC_KEY_FILENAME);
    }

    public byte[] getServerPublicKey(){
        return readPrivateFile(SERVER_PUBLIC_KEY_FILENAME);
    }

    public void saveEncryptedPrivateKey(byte[] encryptedPrivateKey){
        savePrivateFile(PRIVATE_KEY_FILENAME, encryptedPrivateKey);
    }

    public byte[] getPrivateKeyForExport(String password) throws CryptoException{
        byte[] encPrivKey = readPrivateFile(PRIVATE_KEY_FILENAME);

        byte[] nonce = readPrivateFile(SK_NONCE_FILENAME);
        byte[] decPrivKey = decryptSymmetric(getKeyFromPassword(password, KDF_DIFFICULTY_NORMAL), nonce, encPrivKey);

       return encryptSymmetric(getKeyFromPassword(password, KDF_DIFFICULTY_HARD), nonce, decPrivKey);
    }

    public byte[] getPrivateKeyFromExportedKey(String password, byte[] encPrivKey) throws CryptoException{
        byte[] nonce = readPrivateFile(SK_NONCE_FILENAME);
        byte[] decPrivKey = decryptSymmetric(getKeyFromPassword(password, KDF_DIFFICULTY_HARD), nonce, encPrivKey);

        return encryptSymmetric(getKeyFromPassword(password, KDF_DIFFICULTY_NORMAL), nonce, decPrivKey);
    }

    public boolean saveDecryptedKey(String password) throws CryptoException {
        return savePrivateFile(PLAIN_PRIVATE_KEY_FILENAME, getPrivateKey(password));
    }

    public boolean deleteDecryptedKey(){
        return deletePrivateFile(PLAIN_PRIVATE_KEY_FILENAME);
    }

    public byte[] getDecryptedKey(){
        return readPrivateFile(PLAIN_PRIVATE_KEY_FILENAME);
    }

    public byte[] exportKeyBundle(String password) throws IOException, CryptoException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ls.bytes(KEY_FILE_BEGGINING));
        out.write(CURRENT_KEY_FILE_VERSION);
        out.write(KEY_FILE_TYPE_BUNDLE_ENCRYPTED);
        out.write(readPrivateFile(PUBLIC_KEY_FILENAME));
        out.write(getPrivateKeyForExport(password));
        out.write(readPrivateFile(PWD_SALT_FILENAME));
        out.write(readPrivateFile(SK_NONCE_FILENAME));
        return out.toByteArray();
    }

    public byte[] exportPublicKey() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ls.bytes(KEY_FILE_BEGGINING));
        out.write(CURRENT_KEY_FILE_VERSION);
        out.write(KEY_FILE_TYPE_PUBLIC_PLAIN);
        out.write(readPrivateFile(PUBLIC_KEY_FILENAME));
        return out.toByteArray();
    }

    public static byte[] getPublicKeyFromExport(byte[] exportedPublicKey) throws IOException {
        return Arrays.copyOfRange(exportedPublicKey, KEY_FILE_HEADER_LEN, exportedPublicKey.length);
    }

    public void importKeyBundle(byte[] keys, String password) throws IOException, CryptoException {
        ByteArrayInputStream in = new ByteArrayInputStream(keys);

        byte[] fileBeginning = new byte[KEY_FILE_BEGGINIG_LEN];
        in.read(fileBeginning);

        if (!ls.str(fileBeginning).equals(KEY_FILE_BEGGINING)) {
            throw new CryptoException("Invalid file header, not our file");
        }

        int keyFileVersion = in.read();
        if (keyFileVersion > CURRENT_KEY_FILE_VERSION) {
            throw new CryptoException("Unsupported key file version number: " + String.valueOf(keyFileVersion));
        }

        int keyFileType = in.read();
        if (keyFileType != KEY_FILE_TYPE_BUNDLE_ENCRYPTED && keyFileType != KEY_FILE_TYPE_BUNDLE_PLAIN && keyFileType != KEY_FILE_TYPE_PUBLIC_PLAIN) {
            throw new CryptoException("Can't import! This is not a encrypted key file");
        }

        if(keyFileType == KEY_FILE_TYPE_BUNDLE_ENCRYPTED) {
            byte[] publicKey = new byte[Box.PUBLICKEYBYTES];
            byte[] encryptedPrivateKey = new byte[Box.SECRETKEYBYTES + SecretBox.MACBYTES];
            byte[] pwdSalt = new byte[PwHash.ARGON2ID_SALTBYTES];
            byte[] skNonce = new byte[SecretBox.NONCEBYTES];

            in.read(publicKey);
            in.read(encryptedPrivateKey);
            in.read(pwdSalt);
            in.read(skNonce);

            savePrivateFile(PUBLIC_KEY_FILENAME, publicKey);
            savePrivateFile(PWD_SALT_FILENAME, pwdSalt);
            savePrivateFile(SK_NONCE_FILENAME, skNonce);
            savePrivateFile(PRIVATE_KEY_FILENAME, getPrivateKeyFromExportedKey(password, encryptedPrivateKey));
        }
        else if(keyFileType == KEY_FILE_TYPE_PUBLIC_PLAIN) {
            byte[] publicKey = new byte[Box.PUBLICKEYBYTES];

            in.read(publicKey);

            savePrivateFile(PUBLIC_KEY_FILENAME, publicKey);
        }
    }

    public void importServerPublicKey(byte[] publicKey) throws IOException {
        savePrivateFile(SERVER_PUBLIC_KEY_FILENAME, publicKey);
    }

    public byte[] decryptSeal(byte[] enc, byte[] publicKey, byte[] privateKey) throws CryptoException {
        byte[] msg = new byte[enc.length - Box.SEALBYTES];
        if(so.crypto_box_seal_open(msg, enc, enc.length, publicKey, privateKey) != 0){
            throw new CryptoException("Unable to decrypt sealed message");
        }

        return msg;
    }

    public byte[] encryptCryptoBox(byte[] message, byte[] publicKey, byte[] privateKey) throws CryptoException {
        byte[] cypherText = new byte[message.length + Box.MACBYTES];

        byte[] nonce = new byte[SecretBox.NONCEBYTES];
        so.randombytes_buf(nonce, nonce.length);

        if(so.crypto_box_easy(cypherText, message, message.length, nonce, publicKey, privateKey) != 0){
            throw new CryptoException("Unable to encrypt box");
        }

        byte[] result = new byte[SecretBox.NONCEBYTES + cypherText.length];
        System.arraycopy(nonce,0, result,0, nonce.length);
        System.arraycopy(cypherText,0, result, nonce.length, cypherText.length);

        return result;
    }

    public byte[] getPublicKeyFromPrivateKey(byte[] privateKey) {
        byte[] publicKey = new byte[Box.PUBLICKEYBYTES];
        so.crypto_scalarmult_base(publicKey, privateKey);

        return publicKey;
    }

    public void deleteKeys(){
        deletePrivateFile(PUBLIC_KEY_FILENAME);
        deletePrivateFile(PWD_SALT_FILENAME);
        deletePrivateFile(SK_NONCE_FILENAME);
        deletePrivateFile(PRIVATE_KEY_FILENAME);
        deletePrivateFile(SERVER_PUBLIC_KEY_FILENAME);
        deletePrivateFile(PLAIN_PRIVATE_KEY_FILENAME);
    }

    public byte[] getKeyFromPassword(String password, int difficulty) throws CryptoException{
        byte[] salt = readPrivateFile(PWD_SALT_FILENAME);
        if(salt == null || salt.length != PwHash.ARGON2ID_SALTBYTES){
            throw new CryptoException("Invalid salt for password derivation");
        }

        byte[] key = new byte[SecretBox.KEYBYTES];
        byte[] passwordBytes = ls.bytes(password);

        long opsLimit = PwHash.OPSLIMIT_INTERACTIVE;
        NativeLong memlimit = PwHash.MEMLIMIT_INTERACTIVE;

        switch (difficulty){
            case KDF_DIFFICULTY_HARD:
                opsLimit = PwHash.OPSLIMIT_MODERATE;
                memlimit = PwHash.MEMLIMIT_MODERATE;
                break;
            case KDF_DIFFICULTY_ULTRA:
                opsLimit = PwHash.OPSLIMIT_SENSITIVE;
                memlimit = PwHash.MEMLIMIT_SENSITIVE;
                break;
        }

        so.crypto_pwhash(key, key.length, passwordBytes, passwordBytes.length, salt, opsLimit, memlimit, PwHash.Alg.PWHASH_ALG_ARGON2ID13.getValue());

        return key;
    }

    public HashMap<String, String> getPasswordHashForStorage(String password){
        byte[] salt = new byte[PwHash.ARGON2ID_SALTBYTES];
        so.randombytes_buf(salt, salt.length);

        String hashedPassword = getPasswordHashForStorage(password, salt);


        HashMap<String, String> result = new HashMap<String, String>();
        result.put("hash", hashedPassword);
        result.put("salt", byte2hex(salt));

        return result;
    }

    public String getPasswordHashForStorage(String password, String salt){
        return getPasswordHashForStorage(password, hex2byte(salt));
    }

    public String getPasswordHashForStorage(String password, byte[] salt){
        byte[] passwordBytes = ls.bytes(password);
        byte[] hashedPassword = new byte[PWHASH_LEN];

        so.crypto_pwhash(hashedPassword, hashedPassword.length, passwordBytes, passwordBytes.length, salt, PwHash.OPSLIMIT_MODERATE, PwHash.MEMLIMIT_MODERATE, PwHash.Alg.PWHASH_ALG_ARGON2ID13.getValue());

        return byte2hex(hashedPassword);
    }

    public boolean verifyStoredPassword(String storedPassword, String providedPassword){
        byte[] hashedPassword = hex2byte(storedPassword);
        byte[] passwordBytes = ls.bytes(providedPassword);

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
        if(key == null || key.length != SecretBox.KEYBYTES){
            throw new InvalidParameterException("Invalid size of the key");
        }
        if(nonce == null || nonce.length != SecretBox.NONCEBYTES){
            throw new InvalidParameterException("Invalid size of the key");
        }

        byte[] plainText = new byte[data.length - SecretBox.MACBYTES];
        if(so.crypto_secretbox_open_easy(plainText, data, data.length, nonce, key) != 0){
            throw new CryptoException("Unable to decrypt");
        }

        return plainText;
    }


    public Header getNewHeader(byte[] symmetricKey, long dataSize, String filename, int fileType, byte[] fileId, int videoDuration) throws CryptoException {
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
        header.videoDuration = videoDuration;

        return header;
    }

    protected void writeHeader(OutputStream out, Header header, byte[] publicKey) throws IOException, CryptoException {
        // File beggining - 2 bytes
        out.write(ls.bytes(FILE_BEGGINING));

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
            byte[] filenameBytes = ls.bytes(header.filename);

            // Filename length
            headerByteStream.write(intToByteArray(filenameBytes.length));

            // Filename itself
            headerByteStream.write(filenameBytes);
        }
        else{
            // If filename is empty then write that filename length is 0
            headerByteStream.write(intToByteArray(0));
        }

        // Video duration - 4 bytes
        headerByteStream.write(intToByteArray(header.videoDuration));

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
        return getFileHeader(bytes, null, null);
    }

    public Header getFileHeader(byte[] bytes, byte[] privateKey, byte[] publicKey) throws IOException, CryptoException{
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return getFileHeader(in, privateKey, publicKey);
    }

    public Header getFileHeader(InputStream in) throws IOException, CryptoException{
        return getFileHeader(in, null, null);
    }

    public Header getFileHeader(InputStream in, byte[] privateKey, byte[] publicKey) throws IOException, CryptoException{
        int overallHeaderSize = 0;
        Header header = new Header();

        if(publicKey == null) {
            publicKey = readPrivateFile(PUBLIC_KEY_FILENAME);
        }
        if(privateKey == null) {
            privateKey = StinglePhotosApplication.getKey();
            if(privateKey == null){
                throw new CryptoException("Failed to get private key");
            }
        }

        // Read and validate file beginning
        byte[] fileBeginning = new byte[FILE_BEGGINIG_LEN];
        in.read(fileBeginning);
        overallHeaderSize += FILE_BEGGINIG_LEN;

        if (!ls.str(fileBeginning).equals(FILE_BEGGINING)) {
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
            header.filename = ls.str(filenameBytes);
        }
        else{
            header.filename = "";
        }

        // Read video duration
        byte[] videoDuration = new byte[FILE_VIDEO_DURATION_LEN];
        headerStream.read(videoDuration);
        header.videoDuration = byteArrayToInt(videoDuration);

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

    public Header encryptFile(OutputStream out, byte[] data, String filename, int fileType, int videoDuration) throws IOException, CryptoException {
        return encryptFile(out, data, filename, fileType, null, videoDuration);
    }

    public Header encryptFile(OutputStream out, byte[] data, String filename, int fileType, byte[] fileId, int videoDuration) throws IOException, CryptoException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        return encryptFile(in, out, filename, fileType, data.length, fileId, videoDuration);
    }

    public Header encryptFile(InputStream in, OutputStream out, String filename, int fileType, long dataLength, int videoDuration) throws IOException, CryptoException {
        return encryptFile(in, out, filename, fileType, dataLength, null, videoDuration, null, null);
    }

    public Header encryptFile(InputStream in, OutputStream out, String filename, int fileType, long dataLength, byte[] fileId, int videoDuration) throws IOException, CryptoException {
        return encryptFile(in, out, filename, fileType, dataLength, fileId, videoDuration, null, null);
    }

    public Header encryptFile(InputStream in, OutputStream out, String filename, int fileType, long dataLength, byte[] fileId, int videoDuration, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException {
        byte[] publicKey = readPrivateFile(PUBLIC_KEY_FILENAME);

        byte[] symmetricKey = new byte[KeyDerivation.MASTER_KEY_BYTES];
        so.crypto_kdf_keygen(symmetricKey);

        if(fileId == null) {
            fileId = new byte[FILE_FILE_ID_LEN];
            so.randombytes_buf(fileId, FILE_FILE_ID_LEN);
        }

        Header header = getNewHeader(symmetricKey, dataLength, filename, fileType, fileId, videoDuration);
        writeHeader(out, header, publicKey);

        encryptData(in, out, header, progress, task);

        return header;
    }

    public byte[] getNewFileId(){
        byte[] fileId = new byte[FILE_FILE_ID_LEN];
        so.randombytes_buf(fileId, FILE_FILE_ID_LEN);

        return fileId;
    }

    public String getRandomString(int length){
        byte[] random = new byte[length];
        so.randombytes_buf(random, length);

        return byteArrayToBase64UrlSafe(random);
    }


    public byte[] decryptFile(byte[] bytes, Header header) throws IOException, CryptoException{
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return decryptFile(in, null, null, header);
    }

    public byte[] decryptFile(byte[] bytes, AsyncTask<?,?,?> task, Header header) throws IOException, CryptoException{
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return decryptFile(in, null, task, header);
    }

    public byte[] decryptFile(InputStream in, Header header) throws IOException, CryptoException{
        return decryptFile(in, null, null, header);
    }

    public byte[] decryptFile(InputStream in, CryptoProgress progress, AsyncTask<?,?,?> task, Header header) throws IOException, CryptoException{
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        decryptFile(in, out, progress, task, header);

        return out.toByteArray();
    }

    public boolean decryptFile(InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task, Header header) throws IOException, CryptoException{
        long time = System.nanoTime();

        if(header == null) {
            header = getFileHeader(in);
        }
        else{
            getOverallHeaderSize(in, false);
        }

        return decryptData(in, out, header, progress, task);
    }

    public byte[] getRandomData(int length){
        byte[] data = new byte[length];
        so.randombytes_buf(data, data.length);
        return data;
    }

	public String blake2bHash(String data) throws SodiumException {
		return ls.cryptoGenericHash(data);
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

        if(task != null && task.isCancelled()){
            return false;
        }
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
        try (FileInputStream inputStream = context.openFileInput(filename)){
            int numRead = 0;
            byte[] buf = new byte[bufSize];
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

            while ((numRead = inputStream.read(buf)) >= 0) {
                byteBuffer.write(buf, 0, numRead);
            }
            return byteBuffer.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    protected boolean deletePrivateFile(String filename){
        return context.deleteFile(filename);
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

    public static String byteArrayToBase64UrlSafe(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP); //base64 encoding
    }
    public static byte[] base64ToByteArrayUrlSafe(String base64str) {
        try {
            return Base64.decode(base64str, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP); //base64 decoding
        }
        catch (IllegalArgumentException e){
            return null;
        }
    }

    public static String byteArrayToBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP); //base64 encoding
    }
    public static byte[] base64ToByteArray(String base64str) {
        try {
            return Base64.decode(base64str, Base64.NO_WRAP); //base64 decoding
        }
        catch (IllegalArgumentException e){
            return null;
        }
    }

    public static String assembleHeadersString(byte[] fileHeader, byte[] thumbHeader){
        return byteArrayToBase64UrlSafe(fileHeader) + "*" + byteArrayToBase64UrlSafe(thumbHeader);
    }

    public static String getFileHeadersFromFile(String encFilePath, String encThumbPath) throws IOException, CryptoException {
        byte[] fileEncHeader = getFileHeaderAsIs(encFilePath);
        byte[] thumbEncHeader = getFileHeaderAsIs(encThumbPath);

        return assembleHeadersString(fileEncHeader, thumbEncHeader);
    }

    public static byte[] getFileHeaderAsIs(String filePath) throws IOException, CryptoException {

        int overallHeaderSize = getOverallHeaderSize(new FileInputStream(filePath));

        FileInputStream in = new FileInputStream(filePath);

        byte[] encHeader = new byte[overallHeaderSize];
        in.read(encHeader);

        return encHeader;
    }

    public static FileThumbHeaders parseFileHeaders(String headersStr) {
        String[] headersStrArr = headersStr.split("\\*");

        FileThumbHeaders headers = new FileThumbHeaders();

        headers.file = base64ToByteArrayUrlSafe(headersStrArr[0]);
        headers.thumb = base64ToByteArrayUrlSafe(headersStrArr[1]);

        if(headers.file == null || headers.thumb == null){
            return null;
        }

        return headers;
    }

    public Header getFileHeaderFromHeadersStr(String headersStr) throws IOException, CryptoException {
        return getFileHeaderFromHeadersStr(headersStr, null, null);
    }
    public Header getThumbHeaderFromHeadersStr(String headersStr) throws IOException, CryptoException {
        return getThumbHeaderFromHeadersStr(headersStr, null, null);
    }

    public Header getFileHeaderFromHeadersStr(String headersStr, byte[] privateKey, byte[] publicKey) throws IOException, CryptoException {
        FileThumbHeaders headers = parseFileHeaders(headersStr);

        if(headers != null) {
            return getFileHeader(headers.file, privateKey, publicKey);
        }
        return null;
    }
    public Header getThumbHeaderFromHeadersStr(String headersStr, byte[] privateKey, byte[] publicKey) throws IOException, CryptoException {
        FileThumbHeaders headers = parseFileHeaders(headersStr);

        if(headers != null) {
            return getFileHeader(headers.thumb, privateKey, publicKey);
        }
        return null;
    }

    public static int getOverallHeaderSize(InputStream in) throws IOException, CryptoException {
        return getOverallHeaderSize(in, true);
    }

    public static int getOverallHeaderSize(InputStream in, boolean closeStream) throws IOException, CryptoException {
        int overallHeaderSize = 0;

        // Read and validate file beginning
        byte[] fileBeginning = new byte[FILE_BEGGINIG_LEN];
        in.read(fileBeginning);
        overallHeaderSize += FILE_BEGGINIG_LEN;

        if (!new String(fileBeginning, "UTF-8").equals(FILE_BEGGINING)) {
            throw new CryptoException("Invalid file header, not our file");
        }

        // Read and validate file version
        int fileVersion = in.read();
        if (fileVersion != CURRENT_FILE_VERSION) {
            throw new CryptoException("Unsupported version number: " + String.valueOf(fileVersion));
        }
        overallHeaderSize += FILE_FILE_VERSION_LEN;

        // Read File ID
        byte[] fileId = new byte[FILE_FILE_ID_LEN];
        in.read(fileId);
        overallHeaderSize += FILE_FILE_ID_LEN;


        // Read header size
        byte[] headerSizeBytes = new byte[FILE_HEADER_SIZE_LEN];
        in.read(headerSizeBytes);
        int headerSize = byteArrayToInt(headerSizeBytes);

        if(headerSize < 1 || headerSize > MAX_BUFFER_LENGTH){
            throw new CryptoException("Invalid header size");
        }
        overallHeaderSize += FILE_HEADER_SIZE_LEN;
        in.skip(headerSize);
        overallHeaderSize += headerSize;

        if(closeStream) {
            in.close();
        }

        return overallHeaderSize;
    }

    public AlbumEncData generateEncryptedAlbumData(byte[] userPK, AlbumMetadata metadata) throws IOException {

        byte[] albumSK = new byte[Box.SECRETKEYBYTES];
        byte[] albumPK = new byte[Box.PUBLICKEYBYTES];

        so.crypto_box_keypair(albumPK, albumSK);

        byte[] encryptedMetadata = encryptAlbumMetadata(metadata, albumPK);

        // Encrypt albumSK
        byte[] encryptedSK = encryptAlbumSK(albumSK, userPK);

        AlbumEncData encData = new AlbumEncData();
        encData.encPrivateKey = byteArrayToBase64(encryptedSK);
        encData.publicKey = byteArrayToBase64(albumPK);
        encData.metadata = byteArrayToBase64(encryptedMetadata);

        return encData;
    }

    public byte[] encryptAlbumMetadata(AlbumMetadata metadata, byte[] albumPK) throws IOException {
        // Encrypt metadata
        ByteArrayOutputStream metadataByteStream = new ByteArrayOutputStream();
        // Current metadata version - 1 byte
        metadataByteStream.write(CURRENT_ALBUM_METADATA_VERSION);

        byte[] albumNameBytes = ls.bytes(metadata.name);
        // name length
        metadataByteStream.write(intToByteArray(albumNameBytes.length));
        // name itself
        metadataByteStream.write(albumNameBytes);
        byte[] metadataBytes = metadataByteStream.toByteArray();

        int encMetadataLength = metadataBytes.length + Box.SEALBYTES;
        byte[] encryptedMetadata = new byte[encMetadataLength];
        so.crypto_box_seal(encryptedMetadata, metadataBytes, metadataBytes.length, albumPK);

        return encryptedMetadata;
    }

    public byte[] encryptAlbumSK(byte[] albumSK, byte[] userPK){
        int encSKLength = albumSK.length + Box.SEALBYTES;
        byte[] encryptedSK = new byte[encSKLength];
        so.crypto_box_seal(encryptedSK, albumSK, albumSK.length, userPK);

        return encryptedSK;
    }

    public AlbumData parseAlbumData(String albumPKStr, String encAlbumSKStr, String metadataStr) throws IOException, CryptoException {
        byte[] albumPK = base64ToByteArray(albumPKStr);

        byte[] encAlbumSK = base64ToByteArray(encAlbumSKStr);

        byte[] publicKey = readPrivateFile(PUBLIC_KEY_FILENAME);
        byte[] privateKey = StinglePhotosApplication.getKey();

        if(privateKey == null){
            throw new CryptoException("Failed to get private key from memory");
        }

        byte[] albumSK = new byte[encAlbumSK.length - Box.SEALBYTES];

        if(so.crypto_box_seal_open(albumSK, encAlbumSK, encAlbumSK.length, publicKey, privateKey) != 0){
            throw new CryptoException("Unable to decrypt albumSK");
        }

        AlbumMetadata metadata = parseAlbumMetadata(metadataStr, albumSK, albumPK);

        AlbumData albumData = new AlbumData();

        albumData.publicKey = albumPK;
        albumData.privateKey = albumSK;
        albumData.metadata = metadata;

        return albumData;
    }

    public AlbumMetadata parseAlbumMetadata(String metadataStr, byte[] albumSK, byte[] albumPK) throws CryptoException, IOException {
        byte[] encMetadata = base64ToByteArray(metadataStr);

        byte[] metadataBytes = new byte[encMetadata.length - Box.SEALBYTES];

        if(so.crypto_box_seal_open(metadataBytes, encMetadata, encMetadata.length, albumPK, albumSK) != 0){
            throw new CryptoException("Unable to decrypt album data");
        }
        ByteArrayInputStream in = new ByteArrayInputStream(metadataBytes);

        // Read and validate metadata version
        int metadataVersion = in.read();
        if (metadataVersion != CURRENT_ALBUM_METADATA_VERSION) {
            throw new CryptoException("Unsupported album metadata version number: " + String.valueOf(metadataVersion));
        }

        AlbumMetadata metadata = new AlbumMetadata();

        // Read album name size
        byte[] albumNameSizeBytes = new byte[4];
        in.read(albumNameSizeBytes);
        int albumNameSize = byteArrayToInt(albumNameSizeBytes);

        if(albumNameSize > 0 || albumNameSize > MAX_BUFFER_LENGTH) {
            // Read filename
            byte[] albumNameBytes = new byte[albumNameSize];
            in.read(albumNameBytes);
            metadata.name = ls.str(albumNameBytes);
        }
        else{
            metadata.name = "";
        }

        return metadata;
    }

    public String reencryptFileHeaders(String headersStr, byte[] publicKeyTo, byte[] privateKeyFrom, byte[] publicKeyFrom) throws IOException, CryptoException {
        FileThumbHeaders headers = parseFileHeaders(headersStr);

        if(headers == null){
            return null;
        }

        Header fileHeader = getFileHeader(headers.file, privateKeyFrom, publicKeyFrom);
        ByteArrayOutputStream fileOut = new ByteArrayOutputStream();
        writeHeader(fileOut, fileHeader, publicKeyTo);
        byte[] fileNewHeader = fileOut.toByteArray();

        Header thumbHeader = getFileHeader(headers.thumb, privateKeyFrom, publicKeyFrom);
        ByteArrayOutputStream thumbOut = new ByteArrayOutputStream();
        writeHeader(thumbOut, thumbHeader, publicKeyTo);
        byte[] thumbNewHeader = thumbOut.toByteArray();

        return assembleHeadersString(fileNewHeader, thumbNewHeader);
    }

    public String encryptFileHeaders(Header fileHeader, Header thumbHeader, byte[] publicKeyTo) throws IOException, CryptoException {

        ByteArrayOutputStream fileOut = new ByteArrayOutputStream();
        writeHeader(fileOut, fileHeader, publicKeyTo);
        byte[] fileNewHeader = fileOut.toByteArray();

        ByteArrayOutputStream thumbOut = new ByteArrayOutputStream();
        writeHeader(thumbOut, thumbHeader, publicKeyTo);
        byte[] thumbNewHeader = thumbOut.toByteArray();

        return assembleHeadersString(fileNewHeader, thumbNewHeader);
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
        public int videoDuration;

        public int overallHeaderSize = 0;

        public String toString(){
            return "\n" +
                    "File Version - " + String.valueOf(fileVersion) + "\n" +
                    "File ID - " + byteArrayToBase64UrlSafe(fileId) + "\n" +
                    "Header Size - " + String.valueOf(headerSize) + "\n\n" +

                    "Header Version - " + String.valueOf(headerVersion) + "\n" +
                    "Chunk Size - " + String.valueOf(chunkSize) + "\n" +
                    "Data Size - " + String.valueOf(dataSize) + "\n" +
                    "Symmetric Key - " + byteArrayToBase64UrlSafe(symmetricKey) + "\n" +
                    "File Type - " + String.valueOf(fileType) + "\n" +
                    "Filename - " + filename + "\n\n" +
                    "Video Duration - " + String.valueOf(videoDuration) + "\n\n" +

                    "Overall Header Size - " + String.valueOf(overallHeaderSize);
        }
    }

    public static class AlbumData {
        public byte[] publicKey;
        public byte[] privateKey;
        public AlbumMetadata metadata;
    }
    public static class AlbumMetadata {
        public String name;

        public AlbumMetadata(){}

        public AlbumMetadata(String name){
            this.name = name;
        }
    }
    public static class AlbumEncData {
        public String publicKey;
        public String encPrivateKey;
        public String metadata;
    }

    public static class FileThumbHeaders{
        public byte[] file;
        public byte[] thumb;
    }
}
