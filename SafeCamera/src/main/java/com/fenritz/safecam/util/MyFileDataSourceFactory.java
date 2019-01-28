package com.fenritz.safecam.util;

import android.content.Context;

import com.google.android.exoplayer2.upstream.DataSource;


public class MyFileDataSourceFactory implements DataSource.Factory {


	public MyFileDataSourceFactory() {

	}

	@Override
	public DataSource createDataSource() {
		return new MyFileDataSource();
	}
}
