package org.stingle.photos.Sync;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Sync.ImportMediaAsyncTask;
import org.stingle.photos.BuildConfig;

@SuppressLint("SpecifyJobSchedulerIdRange")
public class JobSchedulerService extends JobService {

	private static final int ASJOBSERVICE_JOB_ID = 12;

	// A pre-built JobInfo we use for scheduling our job.
	private static JobInfo JOB_INFO = null;

	public static int getJobInfo(Context context) {
		int schedule = ((JobScheduler) context.getSystemService(JobScheduler.class)).schedule(JOB_INFO);
		Log.i("PhotosContentJob", "JOB SCHEDULED!");
		return schedule;
	}

	// Schedule this job, replace any existing one.
	@RequiresApi(api = Build.VERSION_CODES.N)
	public static void scheduleJob(Context context) {
		if (JOB_INFO != null) {
			getJobInfo(context);
		}
		else {
			JobScheduler js = context.getSystemService(JobScheduler.class);
			JobInfo.Builder builder = new JobInfo.Builder(ASJOBSERVICE_JOB_ID, new ComponentName(BuildConfig.APPLICATION_ID, JobSchedulerService.class.getName()));

			builder.addTriggerContentUri(new JobInfo.TriggerContentUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS));
			builder.addTriggerContentUri(new JobInfo.TriggerContentUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS));
			builder.setTriggerContentMaxDelay(500);
			JOB_INFO = builder.build();
			js.schedule(JOB_INFO);
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.N)
	@Override
	public boolean onStartJob(final JobParameters params) {
		Context context = this;

		if(!SyncManager.isImportEnabled(context)){
			return true;
		}

		// Did we trigger due to a content change?
		if (params.getTriggeredContentAuthorities() != null) {
			if (params.getTriggeredContentUris() != null) {
				// If we have details about which URIs changed, then iterate through them
				// and collect either the ids that were impacted or note that a generic
				// change has happened.
				Log.e("ImportRequest", "received");
				//if(ImportMediaAsyncTask.getInstance() == null) {
					//Log.e("ImportRequest", "accepted");
					(new ImportMediaAsyncTask(context, new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
						}
					})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				/*}
				else{
					Log.e("ImportRequest", "rejected");
				}*/
				jobFinished(params, true); // see this, we are saying we just finished the job
				// We will emulate taking some time to do this work, so we can see batching happen.
				scheduleJob(this);
			}
		}
		return true;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		return false;
	}

}
