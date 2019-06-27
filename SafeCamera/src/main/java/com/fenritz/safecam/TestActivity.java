package com.fenritz.safecam;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Video.StingleDataSourceFactory;
import com.fenritz.safecam.Video.StingleHttpDataSource;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;

public class TestActivity extends Activity {


	private ParcelFileDescriptor parcelRead;
	private ParcelFileDescriptor parcelWrite;

	private Thread reader;
	private Thread writer;
	private SimpleExoPlayer player;
	private PlayerView playerView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test);


		playerView =(PlayerView)findViewById(R.id.video);
		//SodiumAndroid so = new SodiumAndroid();
		//LazySodiumAndroid ls = new LazySodiumAndroid(so);

		/*byte[] symmetricKey = new byte[AEAD.CHACHA20POLY1305_KEYBYTES];
		so.crypto_secretstream_xchacha20poly1305_keygen(symmetricKey);
		Log.d("key", SafeCameraApplication.getCrypto().byte2hex(symmetricKey));*/

		///////////////////////////////////////////////

		/*byte[] symmetricKey = SafeCameraApplication.getCrypto().hex2byte("8EE2B448F12ECCC85D3AC39EC2086F424E49D4DC54A592C522FDD95C1B1C871C");


		byte[] header = SafeCameraApplication.getCrypto().hex2byte("ADAAD56EE7CE45EDF88D3BA6437920A2B6A1F737B089639C");

		SecretStream.State state = new SecretStream.State.ByReference();
		state.nonce[1] = 3;
		int resInit = so.crypto_secretstream_xchacha20poly1305_init_pull(state, header, symmetricKey);

		byte[] decBytes = new byte[64];
		long[] decSize = new long[1];
		byte[] tag = new byte[1];

		Log.d("nonce", SafeCameraApplication.getCrypto().byte2hex(state.nonce));

		byte[] bufDec = SafeCameraApplication.getCrypto().hex2byte("46EED7D42A47C57EEAA0D24DE119286B38452534AFB8964E4DD465CA02D0237AD3A715F67402C2B528DA073B457EAD2E49F2BB2C9E587F7337340182D178FE775B2ABBDBB07B8CFB7F3F992589D1A63582");
		int res = so.crypto_secretstream_xchacha20poly1305_pull(state, decBytes, decSize, tag, bufDec, bufDec.length, null, 0);

		if(res != 0){
			Log.d("qaq", "error");
		}

		Log.d("dec", new String(decBytes));
		Log.d("nonce", SafeCameraApplication.getCrypto().byte2hex(state.nonce));

		bufDec = SafeCameraApplication.getCrypto().hex2byte("454908BCA56AD9EBF1E129E9EB8B236D7B42029CFE224412E70C3430CC3AA11AD5646BD8535A7933F8615244BE27E4DB38BB432C0F45F8F34101BFB4842B1B214C292E1704033BA6F421E329A7AD8C3DCA");
		res = so.crypto_secretstream_xchacha20poly1305_pull(state, decBytes, decSize, tag, bufDec, bufDec.length, null, 0);

		if(res != 0){
			Log.d("qaq", "error");
		}

		Log.d("dec", new String(decBytes));
		Log.d("nonce", SafeCameraApplication.getCrypto().byte2hex(state.nonce));


		bufDec = SafeCameraApplication.getCrypto().hex2byte("C3D031F2C4F7C1EA047B626A25C458A166584392AE1814200460CDBF8598E1589CEE9E400404D92EEBD2BB649C032EF33A24F92841E299C9A9A218D71CEE29B45942B87856ED6FE63CAC541A9D7C03CA1A");
		res = so.crypto_secretstream_xchacha20poly1305_pull(state, decBytes, decSize, tag, bufDec, bufDec.length, null, 0);

		if(res != 0){
			Log.d("qaq", "error");
		}

		Log.d("dec", new String(decBytes));
		Log.d("nonce", SafeCameraApplication.getCrypto().byte2hex(state.nonce));*/

		//////////////////////////////////////////////////////

		/*String text1 = "Creates a random, secret key to encrypt a stream, and stores it!";
		String text2 = "Zhops qaq qaqemv, secret key to encrypt a stream, and stores it!";
		String text3 = "Boz govno qaqemv, secret key to encrypt a stream, and stores it!";

		byte[] header = new byte[SecretStream.HEADERBYTES];
		SecretStream.State state = new SecretStream.State.ByReference();
		so.crypto_secretstream_xchacha20poly1305_init_push(state, header, symmetricKey);
		Log.d("header", SafeCameraApplication.getCrypto().byte2hex(header));

		byte tag = SecretStream.XCHACHA20POLY1305_TAG_MESSAGE;
		byte tagFinal = SecretStream.XCHACHA20POLY1305_TAG_FINAL;

		int bufSize = 64;
		byte[] buf = new byte[bufSize];
		byte[] encBytes = new byte[bufSize + SecretStream.ABYTES];
		long[] encSize = new long[1];
		buf = text1.getBytes();
		Log.d("bufLen", String.valueOf(buf.length));
		int res = so.crypto_secretstream_xchacha20poly1305_push(state, encBytes, encSize, buf, buf.length, null, 0, tag);
		if (res != 0) {
			Log.d("qaq", "error");
		}

		Log.d("text1enc", SafeCameraApplication.getCrypto().byte2hex(encBytes));

		buf = text2.getBytes();
		res = so.crypto_secretstream_xchacha20poly1305_push(state, encBytes, encSize, buf, buf.length, null, 0, tag);
		if (res != 0) {
			Log.d("qaq", "error");
		}

		Log.d("text2enc", SafeCameraApplication.getCrypto().byte2hex(encBytes));

		buf = text3.getBytes();
		res = so.crypto_secretstream_xchacha20poly1305_push(state, encBytes, encSize, buf, buf.length, null, 0, tag);
		if (res != 0) {
			Log.d("qaq", "error");
		}

		Log.d("text3enc", SafeCameraApplication.getCrypto().byte2hex(encBytes));*/
	}

	@Override
	protected void onResume() {
		super.onResume();
		initializePlayer();
		/*try {
			ParcelFileDescriptor[] parcelFileDescriptors = ParcelFileDescriptor.createPipe();
			parcelRead = new ParcelFileDescriptor(parcelFileDescriptors[0]);
			parcelWrite  = new ParcelFileDescriptor(parcelFileDescriptors[1]);
		} catch (IOException e) {
			e.printStackTrace();
		}
		reader = new Thread(new Reader());
		writer = new Thread(new SockMonitor());
		reader.start();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		writer.start();*/



		//Creating MediaController
		/*MediaController mediaController= new MediaController(this);
		mediaController.setAnchorView(videoView);

		//specify the location of media file
		//Uri uri=Uri.parse(TestActivity.this.getExternalFilesDir(null).getAbsolutePath() + "/vid.mp4");
		//String path = "https://safecamera.org/vid.mp4";
		String path = "http://192.168.1.108/vid.mp4";
		Uri uri=Uri.parse(path);

		//Setting MediaController and URI, then starting the videoView
		videoView.setMediaController(mediaController);
		videoView.setVideoURI(uri);
		//videoView.requestFocus();
		videoView.start();
		/*videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			// Close the progress bar and play the video
			public void onPrepared(MediaPlayer mp) {

			}
		});*/
		//videoView.start();
	}

	private void initializePlayer() {
		player = ExoPlayerFactory.newSimpleInstance(
				new DefaultRenderersFactory(this),
				new DefaultTrackSelector(), new DefaultLoadControl());

		playerView.setPlayer(player);
		//AesCipherDataSource
		String path = Helpers.getHomeDir(this) + "/vid1.sc";
		//path = "/storage/emulated/0/vid.mp4";
		//Uri uri = Uri.fromFile(new File(path));
		Uri uri = Uri.parse("https://www.safecamera.org/vid1.sc");
		MediaSource mediaSource = buildMediaSource(uri);
		player.prepare(mediaSource, true, false);

		//player.setPlayWhenReady(playWhenReady);
		//player.seekTo(currentWindow, playbackPosition);
	}

	private MediaSource buildMediaSource(Uri uri) {
		//MyFileDataSourceFactory stingle = new MyFileDataSourceFactory();
		//FileDataSource file = new FileDataSource();
		StingleHttpDataSource http = new StingleHttpDataSource("stingle", null);
		StingleDataSourceFactory stingle = new StingleDataSourceFactory(this, http);

		return new ExtractorMediaSource.Factory(stingle).createMediaSource(uri);
	}

	@Override
	protected void onPause() {
		super.onPause();
		releasePlayer();
	}

	private void releasePlayer() {
		if (player != null) {
			//playbackPosition = player.getCurrentPosition();
			//currentWindow = player.getCurrentWindowIndex();
			//playWhenReady = player.getPlayWhenReady();
			player.release();
			player = null;
		}
	}

	/*private class Reader implements Runnable {

		@Override
		public void run() {
			File dir = TestActivity.this.getExternalFilesDir(null);
			String path = dir.getAbsolutePath() + "/pic.jpg";
			try {
				FileInputStream in  = new FileInputStream(path);
				FileOutputStream out = new FileOutputStream(parcelWrite.getFileDescriptor());
				int num;
				byte[] buf = new byte[2048];
				while((num = in.read(buf)) > 0){
					Log.d("read", String.valueOf(num));
					out.write(buf, 0, num);
				}
				Log.d("read", "readFinish");
				out.close();
				in.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class SockMonitor implements Runnable {

		InputStream in;
		FileOutputStream out;

		public SockMonitor(){

		}

		@Override
		public void run() {
			try {
				try {
					File dir = TestActivity.this.getExternalFilesDir(null);
					String path = dir.getAbsolutePath() + "/picOut.jpg";

					out = new FileOutputStream(path);
					in = new ParcelFileDescriptor.AutoCloseInputStream(parcelRead);
					Log.d("write", "Init streams");
				} catch (IOException e) {
					e.printStackTrace();
				}

				byte[] buffer = new byte[1024];
				while (!Thread.interrupted()) {
					Log.d("write", "RUN");
					int read;
					if((read = in.read(buffer)) > 0) {
						try {
							out.write(buffer,0, read);
							out.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				Log.d("write", "writeFinish");
				out.close();

			}
			catch (IOException e){}
		}

	}*/
}
