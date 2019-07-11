package com.fenritz.safecam.Gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.GalleryActivityOld;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

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
		StingleDbFile file = db.getFileAtPosition(position);
		if(file.isLocal) {
			try {
				FileInputStream input = new FileInputStream(Helpers.getThumbsDir(context) +"/"+ file.filename);
				byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(input);

				if (decryptedData != null) {
					final Bitmap bitmap = Helpers.decodeBitmap(decryptedData, Helpers.getThumbSize(context));
					image.setImageBitmap(bitmap);
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
			
		}

	}

	// Return the size of your dataset (invoked by the layout manager)
	@Override
	public int getItemCount() {
		return (int)db.getTotalFilesCount();
	}
}

