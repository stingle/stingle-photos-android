package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;

public class RemoveMemberAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private String albumId;
	private final OnAsyncTaskFinish onFinishListener;
	private StingleContact member;
	private int sourceSet = -1;

	public RemoveMemberAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;

		this.onFinishListener = onFinishListener;
	}

	public RemoveMemberAsyncTask setAlbumId(String albumId){
		this.albumId = albumId;
		return this;
	}

	public RemoveMemberAsyncTask setMember(StingleContact member){
		this.member = member;
		return this;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}

		if(member == null){
			return false;
		}

		AlbumsDb db = new AlbumsDb(myContext);

		StingleDbAlbum album = db.getAlbumById(albumId);

		if(!album.isOwner || !album.isShared){
			return false;
		}

		if(!album.members.contains(String.valueOf(member.userId))){
			return false;
		}

		album.members.remove(String.valueOf(member.userId));
		db.close();

		boolean notifyResult = SyncManager.notifyCloudAboutMemberRemove(myContext, album, member.userId);
		if(notifyResult){
			db.updateAlbum(album);
		}
		else{
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
