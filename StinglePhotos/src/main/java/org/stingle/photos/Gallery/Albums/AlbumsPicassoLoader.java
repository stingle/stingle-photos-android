package org.stingle.photos.Gallery.Albums;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbAlbumFile;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class AlbumsPicassoLoader extends RequestHandler {

	private Context context;
	private AlbumsDb db;
	private AlbumFilesDb filesDb;
	private int thumbSize;
	private Crypto crypto;

	public AlbumsPicassoLoader(Context context, AlbumsDb db, AlbumFilesDb filesDb, int thumbSize){
		this.context = context;
		this.db = db;
		this.filesDb = filesDb;
		this.thumbSize = thumbSize;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	@Override
	public boolean canHandleRequest(com.squareup.picasso3.Request data) {
		if(data.uri.toString().startsWith("a")){
			return true;
		}
		return false;
	}

	@Override
	public void load(@NonNull Picasso picasso, @NonNull com.squareup.picasso3.Request request, @NonNull Callback callback) throws IOException {
		String uri = request.uri.toString();
		String position = uri.substring(1);


			try {
				StingleDbAlbum album = db.getAlbumAtPosition(Integer.parseInt(position), StingleDb.SORT_ASC);
				Crypto.AlbumData albumData = StinglePhotosApplication.getCrypto().parseAlbumData(album.data);

				Result result = null;

				StingleDbAlbumFile albumFile = filesDb.getFileAtPosition(0, album.id, StingleDb.SORT_ASC);

				if(albumFile != null){
					File fileToDec = new File(FileManager.getThumbsDir(context) +"/"+ albumFile.filename);
					FileInputStream input = new FileInputStream(fileToDec);
					byte[] decryptedData = crypto.decryptFile(input, crypto.getThumbHeaderFromHeadersStr(albumFile.headers, albumData.privateKey, Crypto.base64ToByteArrayDefault(album.albumPK)));

					if (decryptedData != null) {
						Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
						bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);

						result = new Result(bitmap, Picasso.LoadedFrom.DISK);

					}
				}

				if(result == null) {
					Drawable drawable = context.getDrawable(R.drawable.ic_no_image);
					if (drawable == null) {
						return;
					}
					result = new Result(drawable, Picasso.LoadedFrom.DISK);
				}

				AlbumsAdapterPisasso.AlbumProps props = new AlbumsAdapterPisasso.AlbumProps();

				props.name = albumData.name;

				result.addProperty("albumProps", props);

				callback.onSuccess(result);

			} catch (IOException | CryptoException e) {
				e.printStackTrace();
			}

	}
}
