package org.stingle.photos.Sync.JobScheduler;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Sync.SyncAsyncTask;
import org.stingle.photos.BuildConfig;
import org.stingle.photos.Sync.SyncManager;

@SuppressLint("SpecifyJobSchedulerIdRange")
public class ImportJobSchedulerService extends JobService {

	private static final int JOB_ID = 12456453;

	// A pre-built JobInfo we use for scheduling our job.
	private static JobInfo JOB_INFO = null;

	public static int getJobInfo(Context context) {
		int schedule = context.getSystemService(JobScheduler.class).schedule(JOB_INFO);
		Log.i("ImportJob", "JOB SCHEDULED!");
		return schedule;
	}

	public static void scheduleJob(Context context) {
		if (JOB_INFO != null) {
			getJobInfo(context);
		}
		else {
			JobScheduler js = context.getSystemService(JobScheduler.class);
			JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, new ComponentName(BuildConfig.APPLICATION_ID, ImportJobSchedulerService.class.getName()));
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				builder.addTriggerContentUri(new JobInfo.TriggerContentUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS));
				builder.addTriggerContentUri(new JobInfo.TriggerContentUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS));
				builder.setTriggerContentMaxDelay(1000);
			}
			else{
				builder.setPeriodic(300000);
			}

			JOB_INFO = builder.build();
			js.schedule(JOB_INFO);
		}
	}

	@Override
	public boolean onStartJob(final JobParameters params) {
		Context context = this;

		if(!SyncManager.isImportEnabled(context)){
			return false;
		}

		Log.d("ImportRequest", "received");
		SyncManager.startSync(this, SyncAsyncTask.MODE_IMPORT_AND_UPLOAD, new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				jobFinished(params, true);
			}

			@Override
			public void onFail() {
				super.onFail();
				jobFinished(params, true);
			}
		});

		scheduleJob(this);

		return true;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		return false;
	}

}
