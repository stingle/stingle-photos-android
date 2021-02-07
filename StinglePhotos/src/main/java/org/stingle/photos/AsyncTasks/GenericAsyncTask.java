package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.Util.Helpers;

import java.lang.ref.WeakReference;

public class GenericAsyncTask extends AsyncTask<Void, Void, Object> {

	private WeakReference<Context> context;
	private OnAsyncTaskFinish onFinishListener;
	private GenericTaskWork work;
	private boolean showSpinner = false;
	private String spinnerMessage = "";
	private ProgressDialog spinner;
	private String altData = "";

	public GenericAsyncTask(Context context) {
		this.context = new WeakReference<>(context);;
	}

	public GenericAsyncTask setWork(GenericTaskWork work){
		this.work = work;
		return this;
	}

	public GenericAsyncTask setAltData(String altData){
		this.altData = altData;
		return this;
	}

	public String getAltData(){
		return altData;
	}

	public GenericAsyncTask setOnFinish(OnAsyncTaskFinish onFinishListener){
		this.onFinishListener = onFinishListener;
		return this;
	}

	public GenericAsyncTask showSpinner(String message){
		showSpinner = true;
		spinnerMessage = message;
		return this;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();

		Context myContext = context.get();
		if(myContext == null){
			return;
		}

		if(showSpinner){
			spinner = Helpers.showProgressDialog(myContext, spinnerMessage, null);
		}
	}

	@Override
	protected Object doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}

		if(work == null){
			return null;
		}

		return work.execute(myContext);
	}

	@Override
	protected void onPostExecute(Object result) {
		super.onPostExecute(result);

		if(onFinishListener != null) {
			if (result != null) {
				onFinishListener.onFinish(result);
			}
			else {
				onFinishListener.onFail();
			}
		}

		if(showSpinner && spinner != null){
			spinner.dismiss();
		}

	}

	public abstract static class GenericTaskWork{
		public abstract Object execute(Context context);
	}
}
