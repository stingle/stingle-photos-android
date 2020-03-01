package org.stingle.photos.Gallery;

import android.content.Context;

import androidx.annotation.NonNull;

import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.Db.AlbumsDb;
import org.stingle.photos.Db.StingleDbAlbum;
import org.stingle.photos.Sync.SyncManager;

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
		String folderStr = uri.substring(1, 2);
		String position = uri.substring(2);

		int folder = SyncManager.FOLDER_MAIN;
		if(folderStr.equals("t")){
			folder = SyncManager.FOLDER_TRASH;
		}

		StingleDbAlbum file = db.getAlbumAtPosition(Integer.parseInt(position), AlbumsDb.SORT_ASC);

			/*try {
				File fileToDec = new File(FileManager.getThumbsDir(context) +"/"+ file.filename);
				FileInputStream input = new FileInputStream(fileToDec);
				byte[] decryptedData = StinglePhotosApplication.getCrypto().decryptFile(input);

				if (decryptedData != null) {
					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);

					FileInputStream streamForHeader = new FileInputStream(fileToDec);
					Crypto.Header header = StinglePhotosApplication.getCrypto().getFileHeader(streamForHeader);
					streamForHeader.close();

					Result result = new Result(bitmap, Picasso.LoadedFrom.DISK);

					GalleryAdapterPisasso.FileProps props = new GalleryAdapterPisasso.FileProps();

					props.fileType = header.fileType;
					props.videoDuration = header.videoDuration;
					props.isUploaded = file.isRemote;

					result.addProperty("fileProps", props);

					callback.onSuccess(result);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (CryptoException e) {
				e.printStackTrace();
			}*/



	}
}
