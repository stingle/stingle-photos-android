package org.stingle.photos.Crypto;

import android.content.Context;
import android.os.AsyncTask;

import org.json.JSONObject;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public class CryptoHelpers {

	public static String encryptParamsForServer(HashMap<String, String> params) throws CryptoException {
		return  encryptParamsForServer(params, null, null);
	}

	public static String encryptParamsForServer(HashMap<String, String> params, byte[] serverPK, byte[] privateKey) throws CryptoException {
		JSONObject json = new JSONObject(params);

		if(serverPK == null){
			serverPK = StinglePhotosApplication.getCrypto().getServerPublicKey();
		}
		if(privateKey == null){
			privateKey = StinglePhotosApplication.getKey();
		}

		if(serverPK == null || privateKey == null){
			return "";
		}

		return Crypto.byteArrayToBase64(
				StinglePhotosApplication.getCrypto().encryptCryptoBox(
						json.toString().getBytes(),
						serverPK,
						privateKey
				)
		);
	}


	public static byte[] decryptDbFile(Context context, int set, String albumId, String headers, boolean isThumb, byte[] bytes) throws IOException, CryptoException {
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		return decryptDbFile(context, set, albumId, headers, isThumb, in, null, null);
	}

	public static byte[] decryptDbFile(Context context, int set, String albumId, String headers, boolean isThumb, byte[] bytes, AsyncTask<?,?,?> task) throws IOException, CryptoException {
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		return decryptDbFile(context, set, albumId, headers, isThumb, in, null, task);
	}

	public static byte[] decryptDbFile(Context context, int set, String albumId, String headers, boolean isThumb, InputStream in) throws IOException, CryptoException {
		return decryptDbFile(context, set, albumId, headers, isThumb, in, null, null);
	}

	public static byte[] decryptDbFile(Context context, int set, String albumId, String headers, boolean isThumb, InputStream in, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		decryptDbFile(context, set, albumId, headers, isThumb, in, out, progress, task);

		return out.toByteArray();
	}

	public static boolean decryptDbFile(Context context, int set, String albumId, String headers, boolean isThumb, InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task) throws IOException, CryptoException {
		Crypto crypto = StinglePhotosApplication.getCrypto();
		Crypto.Header header;
		if(set == SyncManager.ALBUM){
			AlbumsDb albumsDb = new AlbumsDb(context);
			StingleDbAlbum dbAlbum = albumsDb.getAlbumById(albumId);
			Crypto.AlbumData albumData = crypto.parseAlbumData(dbAlbum.publicKey, dbAlbum.encPrivateKey, dbAlbum.metadata);

			if(isThumb){
				header = crypto.getThumbHeaderFromHeadersStr(headers, albumData.privateKey, albumData.publicKey);
			}
			else{
				header = crypto.getFileHeaderFromHeadersStr(headers, albumData.privateKey, albumData.publicKey);
			}
		}
		else {
			if(isThumb){
				header = crypto.getThumbHeaderFromHeadersStr(headers);
			}
			else{
				header = crypto.getFileHeaderFromHeadersStr(headers);
			}
		}

		return crypto.decryptFile(in, out, progress, task, header);
	}

	public static Crypto.Header decryptFileHeaders(Context context, int set, String albumId, String headers, boolean isThumb) throws IOException, CryptoException {
		Crypto crypto = StinglePhotosApplication.getCrypto();
		Crypto.Header header;
		if(set == SyncManager.ALBUM){
			AlbumsDb albumsDb = new AlbumsDb(context);
			StingleDbAlbum dbAlbum = albumsDb.getAlbumById(albumId);
			Crypto.AlbumData albumData = crypto.parseAlbumData(dbAlbum.publicKey, dbAlbum.encPrivateKey, dbAlbum.metadata);

			if(isThumb){
				header = crypto.getThumbHeaderFromHeadersStr(headers, albumData.privateKey, albumData.publicKey);
			}
			else{
				header = crypto.getFileHeaderFromHeadersStr(headers, albumData.privateKey, albumData.publicKey);
			}
		}
		else {
			if(isThumb){
				header = crypto.getThumbHeaderFromHeadersStr(headers);
			}
			else{
				header = crypto.getFileHeaderFromHeadersStr(headers);
			}
		}

		return header;
	}

	public static String getRandomString(int length){
		Crypto crypto = StinglePhotosApplication.getCrypto();
		return crypto.getRandomString(length);
	}
}
