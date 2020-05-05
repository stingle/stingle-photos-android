package org.stingle.photos.Gallery.Helpers;

import android.content.Context;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.IOException;

public class GalleryHelpers {

	public static StingleDbAlbum getCurrentAlbum(GalleryActivity activity){
		String albumId = activity.getCurrentAlbumId();
		if(activity.getCurrentSet() == SyncManager.ALBUM && albumId != null){
			return getAlbum(activity, albumId);
		}

		return null;
	}
	public static StingleDbAlbum getAlbum(Context context, String albumId){
		AlbumsDb albumsDb = new AlbumsDb(context);
		StingleDbAlbum album = albumsDb.getAlbumById(albumId);
		albumsDb.close();
		return album;
	}

	public static String getAlbumName(StingleDbAlbum album){
		String albumName = "";
		try {
			Crypto.AlbumData albumData = StinglePhotosApplication.getCrypto().parseAlbumData(album.publicKey, album.encPrivateKey, album.metadata);
			albumName = albumData.metadata.name;
			albumData = null;
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}

		return albumName;
	}
	public static String getAlbumName(Context context, String albumId){
		AlbumsDb db = new AlbumsDb(context);
		StingleDbAlbum album = db.getAlbumById(albumId);

		if(album != null){
			return getAlbumName(album);
		}
		return "";
	}
}
