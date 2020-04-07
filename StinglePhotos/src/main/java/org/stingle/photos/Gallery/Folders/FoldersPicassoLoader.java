package org.stingle.photos.Gallery.Folders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.FolderFilesDb;
import org.stingle.photos.Db.Query.FoldersDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.Objects.StingleDbFolder;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class FoldersPicassoLoader extends RequestHandler {

	private Context context;
	private FoldersDb db;
	private FolderFilesDb filesDb;
	private int thumbSize;
	private Crypto crypto;

	public FoldersPicassoLoader(Context context, FoldersDb db, FolderFilesDb filesDb, int thumbSize){
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
				StingleDbFolder folder = db.getFolderAtPosition(Integer.parseInt(position), StingleDb.SORT_ASC);
				Crypto.FolderData folderData = StinglePhotosApplication.getCrypto().parseFolderData(folder.data);

				Result result = null;

				StingleDbFile folderDbFile = filesDb.getFileAtPosition(0, folder.folderId, StingleDb.SORT_ASC);

				if(folderDbFile != null){
					byte[] decryptedData;
					if(folderDbFile.isLocal) {
						File fileToDec = new File(FileManager.getThumbsDir(context) + "/" + folderDbFile.filename);
						FileInputStream input = new FileInputStream(fileToDec);
						decryptedData = crypto.decryptFile(input, crypto.getThumbHeaderFromHeadersStr(folderDbFile.headers, folderData.privateKey, Crypto.base64ToByteArray(folder.folderPK)));
					}
					else{
						byte[] encFile = FileManager.getAndCacheThumb(context, folderDbFile.filename, SyncManager.FOLDER);
						decryptedData = crypto.decryptFile(encFile, crypto.getThumbHeaderFromHeadersStr(folderDbFile.headers, folderData.privateKey, Crypto.base64ToByteArray(folder.folderPK)));
					}

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

				FoldersAdapterPisasso.FolderProps props = new FoldersAdapterPisasso.FolderProps();

				props.name = folderData.name;

				result.addProperty("folderProps", props);

				callback.onSuccess(result);

			} catch (IOException | CryptoException e) {
				e.printStackTrace();
			}

	}
}
