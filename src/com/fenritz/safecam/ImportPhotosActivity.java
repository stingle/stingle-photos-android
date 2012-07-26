package com.fenritz.safecam;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

public class ImportPhotosActivity extends Activity {

	private GridView photosGrid;
	private Cursor cursor;
	private int columnIndex;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.import_photos);

		   // Set up an array of the Thumbnail Image ID column we want
	    String[] projection = {MediaStore.Images.Thumbnails._ID};
	    // Create the cursor pointing to the SDCard
	    cursor = managedQuery( MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
	            projection, // Which columns to return
	            null,       // Return all rows
	            null,
	            MediaStore.Images.Thumbnails.IMAGE_ID);
	    // Get the column index of the Thumbnails Image ID
	    columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Thumbnails._ID);

	    GridView sdcardImages = (GridView) findViewById(R.id.photosGrid);
	    sdcardImages.setAdapter(new ImageAdapter(this));

	    // Set up a click listener
	    sdcardImages.setOnItemClickListener(new OnItemClickListener() {
	        public void onItemClick(AdapterView parent, View v, int position, long id) {
	            // Get the data location of the image
	            String[] projection = {MediaStore.Images.Media.DATA};
	            cursor = managedQuery( MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
	                    projection, // Which columns to return
	                    null,       // Return all rows
	                    null,
	                    null);
	            columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
	            cursor.moveToPosition(position);
	            // Get image filename
	            String imagePath = cursor.getString(columnIndex);
	            // Use this path to do further processing, i.e. full screen display
	        }
	    });
	}

	/**
	 * Adapter for our image files.
	 */
	private class ImageAdapter extends BaseAdapter {

	    private final Context context;

	    public ImageAdapter(Context localContext) {
	        context = localContext;
	    }

	    public int getCount() {
	        return cursor.getCount();
	    }
	    public Object getItem(int position) {
	        return position;
	    }
	    public long getItemId(int position) {
	        return position;
	    }
	    public View getView(int position, View convertView, ViewGroup parent) {
	        ImageView picturesView;
            picturesView = new ImageView(context);
            // Move cursor to current position
            cursor.moveToPosition(position);
            // Get the current value for the requested column
            int imageID = cursor.getInt(columnIndex);
            // Set the content of the image based on the provided URI
            picturesView.setImageURI(Uri.withAppendedPath(
                    MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, "" + imageID));
            picturesView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            picturesView.setPadding(8, 8, 8, 8);
            //picturesView.setLayoutParams(new GridView.LayoutParams(300, 300));
	        return picturesView;
	    }
	}

}
