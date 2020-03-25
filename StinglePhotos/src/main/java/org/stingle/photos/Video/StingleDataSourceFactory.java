package org.stingle.photos.Video;

import android.content.Context;

import com.google.android.exoplayer2.upstream.DataSource;

import org.stingle.photos.Crypto.Crypto;


public class StingleDataSourceFactory  implements DataSource.Factory {

	private Context context;
	private DataSource upstream;
	private Crypto.Header header;

	public StingleDataSourceFactory(Context context, DataSource upstream, Crypto.Header header) {
		this.context = context;
		this.upstream = upstream;
		this.header = header;
	}

	@Override
	public DataSource createDataSource() {
		return new StingleDataSource(context, upstream, header);
	}
}
