package com.fenritz.safecam.Gallery;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.GalleryActivityOld;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Util.Helpers;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.MyViewHolder> {
	private Context context;
	private StingleDbHelper db;

	// Provide a reference to the views for each data item
	// Complex data items may need more than one view per item, and
	// you provide access to all the views for a data item in a view holder
	public static class MyViewHolder extends RecyclerView.ViewHolder {
		// each data item is just a string in this case
		public RelativeLayout layout;

		public MyViewHolder(RelativeLayout v) {
			super(v);
			layout = v;
		}
	}

	// Provide a suitable constructor (depends on the kind of dataset)
	public GalleryAdapter(Context context) {
		this.context = context;
		this.db = new StingleDbHelper(context);
	}

	// Create new views (invoked by the layout manager)
	@Override
	public GalleryAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		// create a new view
		RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
				.inflate(R.layout.gallery_item, parent, false);

		MyViewHolder vh = new MyViewHolder(v);
		return vh;
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(MyViewHolder holder, int position) {
		// - get element from your dataset at this position
		// - replace the contents of the view with that element
		ImageView image = (ImageView)holder.layout.findViewById(R.id.thumbImage);

		image.setImageDrawable(null);
		//image.setImageResource(R.drawable.file);
		(new GetDecryptedBitmap(context, this, db, position, image)).execute();
	}

	// Return the size of your dataset (invoked by the layout manager)
	@Override
	public int getItemCount() {
		return (int)db.getTotalFilesCount();
	}

	public static class GetDecryptedBitmap extends AsyncTask<Void, Void, Bitmap> {

		protected Context context;
		protected GalleryAdapter adapter;
		protected StingleDbHelper db;
		protected int position;
		protected ImageView image;

		public GetDecryptedBitmap(Context context, GalleryAdapter adapter, StingleDbHelper db,  int position, ImageView image){
			this.context = context;
			this.adapter = adapter;
			this.db = db;
			this.position = position;
			this.image = image;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			//image.setImageResource(R.drawable.file);
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			StingleDbFile file = db.getFileAtPosition(position);
			if(file.isLocal) {
				try {
					FileInputStream input = new FileInputStream(Helpers.getThumbsDir(context) +"/"+ file.filename);
					byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(input);

					if (decryptedData != null) {
						return Helpers.decodeBitmap(decryptedData, Helpers.getThumbSize(context));

					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (CryptoException e) {
					e.printStackTrace();
				}

			}
			else if(!file.isLocal && file.isRemote){

				HashMap<String, String> postParams = new HashMap<String, String>();

				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("file", file.filename);
				postParams.put("thumb", "1");
				Log.d("params", postParams.toString());
				try {
					byte[] encFile = HttpsClient.getFileAsByteArray(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams);

					if(encFile == null || encFile.length == 0){
						return null;
					}

					Log.d("encFile", new String(encFile, "UTF-8"));

					/*FileOutputStream tmp = new FileOutputStream(Helpers.getHomeDir(context) + "/qaq.sp");
					tmp.write(encFile);
					tmp.close();*/

					byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(encFile);

					if (decryptedData != null) {
						return Helpers.decodeBitmap(decryptedData, Helpers.getThumbSize(context));
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (CryptoException e) {
					e.printStackTrace();
				}
			}

			return null;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			super.onPostExecute(bitmap);
			if(bitmap != null) {
				image.setImageBitmap(bitmap);
			}
			else{
				image.setImageResource(R.drawable.file);
			}
			//adapter.notifyItemChanged(position);
		}
	}
}

