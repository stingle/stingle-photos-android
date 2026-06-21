package org.stingle.photos.Video;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;

import org.stingle.photos.Crypto.Crypto;


@OptIn(markerClass = UnstableApi.class)
public class StingleDataSourceFactory  implements DataSource.Factory {

	private Context context;
	private DataSource.Factory upstreamFactory;
	private Crypto.Header header;

	public StingleDataSourceFactory(Context context, DataSource.Factory upstreamFactory, Crypto.Header header) {
		this.context = context;
		this.upstreamFactory = upstreamFactory;
		this.header = header;
	}

	@Override
	public DataSource createDataSource() {
		// Build a FRESH upstream per data source. ProgressiveMediaSource calls this
		// multiple times (initial extraction, playback load, and again on every seek);
		// a single reused HTTP DataSource cannot be re-opened like that, which broke
		// playback of remote (non-cached) videos. A new upstream per call fixes it.
		return new StingleDataSource(context, upstreamFactory.createDataSource(), header);
	}
}
