package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Sharing.SharingPermissions;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;

public class EditAlbumPermissionsAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private String albumId;
	private final OnAsyncTaskFinish onFinishListener;
	private SharingPermissions permissions;
	private int sourceSet = -1;

	public EditAlbumPermissionsAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;

		this.onFinishListener = onFinishListener;
	}

	public EditAlbumPermissionsAsyncTask setAlbumId(String albumId){
		this.albumId = albumId;
		return this;
	}

	public EditAlbumPermissionsAsyncTask setPermissions(SharingPermissions permissions){
		this.permissions = permissions;
		return this;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}

		if(permissions == null){
			return false;
		}

		AlbumsDb db = new AlbumsDb(myContext);

		StingleDbAlbum album = db.getAlbumById(albumId);

		if(album == null || !album.isOwner || !album.isShared){
			return false;
		}

		album.permissions = permissions.toString();

		boolean notifyResult = SyncManager.notifyCloudAboutAlbumEditPerms(myContext, album);
		if(notifyResult){
			db.updateAlbum(album);
			db.close();
			return true;
		}

		db.close();
		return false;
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
