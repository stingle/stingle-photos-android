package org.stingle.photos.Gallery.Helpers;

import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import org.stingle.photos.AsyncTasks.Gallery.AddFolderAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.R;

public class GalleryHelpers {
	public static void addFolder(Context context, OnAsyncTaskFinish onFinish){
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setView(R.layout.add_folder_dialog);
		builder.setCancelable(true);
		AlertDialog addFolderDialog = builder.create();
		addFolderDialog.show();

		Button okButton = addFolderDialog.findViewById(R.id.okButton);
		Button cancelButton = addFolderDialog.findViewById(R.id.cancelButton);
		final EditText folderNameText = addFolderDialog.findViewById(R.id.folder_name);

		final InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

		okButton.setOnClickListener(v -> {
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			String folderName = folderNameText.getText().toString();
			(new AddFolderAsyncTask(context, folderName, new OnAsyncTaskFinish() {
				@Override
				public void onFinish(Object folder) {
					super.onFinish(folder);
					addFolderDialog.dismiss();
					onFinish.onFinish(folder);
				}

				@Override
				public void onFail() {
					super.onFail();
					addFolderDialog.dismiss();
					onFinish.onFail();
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		});

		cancelButton.setOnClickListener(v -> {
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			addFolderDialog.dismiss();
		});
	}
}
