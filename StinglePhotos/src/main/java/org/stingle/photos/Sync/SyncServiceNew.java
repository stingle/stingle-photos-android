package org.stingle.photos.Sync;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;

@SuppressLint("SpecifyJobSchedulerIdRange")
public class SyncServiceNew extends JobService {

	static final int JOB_ID = 1045300;

	public SyncServiceNew() {
	}

	@Override
	public void onCreate() {
		super.onCreate();


	}

	@Override
	public void onDestroy() {
		super.onDestroy();

	}

	@Override
	public boolean onStartJob(JobParameters params) {

		return true;
	}

	@Override
	public boolean onStopJob(JobParameters params) {

		return false;
	}



}
