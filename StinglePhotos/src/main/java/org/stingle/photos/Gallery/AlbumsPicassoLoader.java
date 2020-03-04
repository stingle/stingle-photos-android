package org.stingle.photos.Gallery;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.AlbumsDb;
import org.stingle.photos.Db.StingleDbAlbum;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;

import java.io.IOException;


public class AlbumsPicassoLoader extends RequestHandler {

	private Context context;
	private AlbumsDb db;
	private int thumbSize;

	public AlbumsPicassoLoader(Context context, AlbumsDb db, int thumbSize){
		this.context = context;
		this.db = db;
		this.thumbSize = thumbSize;
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
		/*try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			//Log.e("interrupted", String.valueOf(position));
		}*/
		String uri = request.uri.toString();
		String position = uri.substring(1);


		StingleDbAlbum album = db.getAlbumAtPosition(Integer.parseInt(position), AlbumsDb.SORT_ASC);

			try {
				/*File fileToDec = new File(FileManager.getThumbsDir(context) +"/"+ file.filename);
				FileInputStream input = new FileInputStream(fileToDec);
				byte[] decryptedData = StinglePhotosApplication.getCrypto().decryptFile(input);

				if (decryptedData != null) {
					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);

					FileInputStream streamForHeader = new FileInputStream(fileToDec);
					Crypto.Header header = StinglePhotosApplication.getCrypto().getFileHeader(streamForHeader);
					streamForHeader.close();


				}*/
				Drawable drawable = context.getDrawable(R.drawable.ic_no_image);
				if(drawable == null){
					return;
				}
				Result result = new Result(drawable, Picasso.LoadedFrom.DISK);

				AlbumsAdapterPisasso.AlbumProps props = new AlbumsAdapterPisasso.AlbumProps();
				Crypto.AlbumData albumData = StinglePhotosApplication.getCrypto().parseAlbumData(Crypto.base64ToByteArrayDefault(album.data));
				props.name = albumData.name;

				result.addProperty("albumProps", props);

				callback.onSuccess(result);

			} catch (IOException | CryptoException e) {
				e.printStackTrace();
			}


	}


}
