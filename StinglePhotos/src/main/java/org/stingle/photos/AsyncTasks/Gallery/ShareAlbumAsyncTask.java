package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Sharing.SharingPermissions;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

public class ShareAlbumAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private String albumId;
	private final OnAsyncTaskFinish onFinishListener;
	private ArrayList<StingleDbFile> files;
	private Crypto crypto;
	private SharingPermissions permissions;
	private ArrayList<StingleContact> recipients;
	private int sourceSet = -1;
	private String sourceAlbumId;

	public ShareAlbumAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;

		this.onFinishListener = onFinishListener;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	public ShareAlbumAsyncTask setAlbumId(String albumId){
		this.albumId = albumId;
		return this;
	}

	public ShareAlbumAsyncTask setFiles(ArrayList<StingleDbFile> files){
		this.files = files;
		return this;
	}

	public ShareAlbumAsyncTask setSourceSet(int set){
		this.sourceSet = set;
		return this;
	}
	public ShareAlbumAsyncTask setSourceAlbumId(String sourceAlbumId){
		this.sourceAlbumId = sourceAlbumId;
		return this;
	}

	public ShareAlbumAsyncTask setRecipients(ArrayList<StingleContact> recipients){
		this.recipients = recipients;
		return this;
	}

	public ShareAlbumAsyncTask setPermissions(SharingPermissions permissions){
		this.permissions = permissions;
		return this;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}

		if(recipients == null || recipients.size() == 0){
			return false;
		}

		if(permissions == null){
			return false;
		}

		if(files == null && albumId == null){
			return false;
		}

		if(files != null && albumId != null){
			return false;
		}

		try {
			if (files != null) {
				if(sourceSet == -1 || sourceAlbumId == null){
					return false;
				}
				StingleDbAlbum newAlbum = SyncManager.addAlbum(myContext, Helpers.generateAlbumName());
				if(newAlbum == null){
					return false;
				}
				SyncManager.moveFiles(myContext, files, sourceSet, SyncManager.ALBUM, sourceAlbumId, newAlbum.albumId, false);
				albumId = newAlbum.albumId;
			}

			AlbumsDb db = new AlbumsDb(myContext);
			long now = System.currentTimeMillis();

			StingleDbAlbum album = db.getAlbumById(albumId);
			Crypto.AlbumData albumData = crypto.parseAlbumData(album.publicKey, album.encPrivateKey, album.metadata);

			HashMap<Long, String> sharingKeys = new HashMap<>();
			ArrayList<String> members = new ArrayList<>();

			if(album.members.size() == 0){
				String myUserId = Helpers.getPreference(myContext, StinglePhotosApplication.USER_ID, "");
				if(myUserId.length() > 0) {
					album.members.add(myUserId);
				}
			}

			for (StingleContact recp : recipients){
				byte[] userPK = Crypto.base64ToByteArray(recp.publicKey);
				byte[] albumSKForRecp = crypto.encryptAlbumSK(albumData.privateKey, userPK);
				sharingKeys.put(recp.userId, Crypto.byteArrayToBase64(albumSKForRecp));
				album.members.add(String.valueOf(recp.userId));
			}

			album.dateModified = now;
			album.isShared = true;
			album.isOwner = true;
			if (files != null) {
				album.isHidden = true;
			}

			boolean notifyResult = SyncManager.notifyCloudAboutShare(myContext, album, sharingKeys, permissions);
			if(notifyResult){
				db.updateAlbum(album);
			}

			db.close();
		}
		catch (IOException | CryptoException e){
			return false;
		}

		return true;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(result){
			onFinishListener.onFinish();
		}
		else{
			onFinishListener.onFail();
		}

	}
}
