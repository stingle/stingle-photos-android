package com.fenritz.safecam.util;

import android.content.Context;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;


public class StingleDataSourceFactory  implements DataSource.Factory {

	private Context context;
	private DataSource upstream;

	public StingleDataSourceFactory(Context context, DataSource upstream) {
		this.context = context;
		this.upstream = upstream;
	}

	@Override
	public DataSource createDataSource() {
		return new StingleDataSource(context, upstream);
	}
}
