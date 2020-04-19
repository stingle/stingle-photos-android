package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;

public class GetContactAsyncTask extends AsyncTask<Void, Void, StingleContact> {

	private WeakReference<Context> context;
	private final OnAsyncTaskFinish onFinishListener;
	private String email;

	public GetContactAsyncTask(Context context, String email, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.onFinishListener = onFinishListener;
		this.email = email;
	}

	@Override
	protected StingleContact doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}

		StingleContact contact = SyncManager.getContactData(myContext, email);

		return contact;
	}

	@Override
	protected void onPostExecute(StingleContact contact) {
		super.onPostExecute(contact);

		if(contact != null){
			onFinishListener.onFinish(contact);
		}
		else{
			onFinishListener.onFail();
		}

	}
}
