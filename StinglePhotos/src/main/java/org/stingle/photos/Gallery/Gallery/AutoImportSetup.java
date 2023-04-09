package org.stingle.photos.Gallery.Gallery;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.stingle.photos.Files.FileManager;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Sync.JobScheduler.ImportJobSchedulerService;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.util.Arrays;

public class AutoImportSetup {

	private static View dialogView;
	private static BottomSheetDialog dialog;

	public static void setImportDeleteSwitch(Context context, boolean value){
		if(dialog != null) {
			SwitchMaterial importDelete = dialog.findViewById(R.id.import_delete);
			importDelete.setChecked(value);
		}
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		sharedPrefs.edit().putBoolean(SyncManager.PREF_IMPORT_DELETE, value).apply();
	}

	public static void showAutoImportSetup(GalleryActivity activity){
		if(dialogView != null && dialogView.isShown()){
			return;
		}

		boolean isImportSettedUp = Helpers.getPreference(activity, SyncManager.PREF_IMPORT_SETUP, false);

		if(isImportSettedUp){
			return;
		}

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);

		dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_autoimport_setup, null);
		dialog = new BottomSheetDialog(activity);
		dialog.setContentView(dialogView);
		dialog.setCancelable(false);
		BottomSheetBehavior<FrameLayout> mBehavior = dialog.getBehavior();

		dialog.setOnShowListener(dialogInterface -> {
			//mBehavior.setPeekHeight(dialogView.getHeight());
			mBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
		});

		SwitchMaterial importDelete = dialog.findViewById(R.id.import_delete);
		TextView importFromValue = dialog.findViewById(R.id.import_from_value);
		SwitchMaterial importExisting = dialog.findViewById(R.id.import_existing);

		dialog.findViewById(R.id.import_skip).setOnClickListener(v -> {
			sharedPrefs.edit().putBoolean(SyncManager.PREF_IMPORT_ENABLED, false).apply();
			Helpers.storePreference(activity, SyncManager.LAST_IMPORTED_FILE_DATE, System.currentTimeMillis() / 1000);
			Helpers.storePreference(activity, SyncManager.PREF_IMPORT_SETUP, true);
			dialog.dismiss();
		});

		dialog.findViewById(R.id.import_enable).setOnClickListener(v -> {
			FileManager.requestReadMediaPermissions(activity);
			sharedPrefs.edit().putBoolean(SyncManager.PREF_IMPORT_ENABLED, true).apply();
			if(!importExisting.isChecked()){
				Helpers.storePreference(activity, SyncManager.LAST_IMPORTED_FILE_DATE, System.currentTimeMillis() / 1000);
			}
			else{
				Helpers.storePreference(activity, SyncManager.LAST_IMPORTED_FILE_DATE, 0L);
			}
			ImportJobSchedulerService.scheduleJob(activity);
			SyncManager.startSync(activity);
			Helpers.storePreference(activity, SyncManager.PREF_IMPORT_SETUP, true);
			dialog.dismiss();
		});



		dialog.findViewById(R.id.import_delete_text).setOnClickListener(v -> importDelete.toggle());
		dialog.findViewById(R.id.import_from_text).setOnClickListener(v -> importFromValue.callOnClick());
		dialog.findViewById(R.id.import_existing_text).setOnClickListener(v -> importExisting.toggle());

		String[] items = activity.getResources().getStringArray(R.array.import_from);
		String[] itemVals = activity.getResources().getStringArray(R.array.import_from_values);

		String currentImportFromVal = sharedPrefs.getString(SyncManager.PREF_IMPORT_FROM, itemVals[0]);
		boolean currentImportDeleteVal = sharedPrefs.getBoolean(SyncManager.PREF_IMPORT_DELETE, false);

		importFromValue.setText(items[Arrays.asList(itemVals).indexOf(currentImportFromVal)]);

		if(currentImportDeleteVal){
			importDelete.setChecked(true);
		}
		else{
			importDelete.setChecked(false);
		}

		importDelete.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(isChecked) {
					activity.requestManageExternalStoragePermission();
					sharedPrefs.edit().putBoolean(SyncManager.PREF_IMPORT_DELETE, true).apply();
				}
				else {
					sharedPrefs.edit().putBoolean(SyncManager.PREF_IMPORT_DELETE, false).apply();
				}
			}
		});

		importFromValue.setOnClickListener(v -> {
			ListPopupWindow list = new ListPopupWindow(activity);


			list.setAdapter(new ArrayAdapter(activity,android.R.layout.simple_list_item_1, items));

			list.setAnchorView(importFromValue);
			list.setContentWidth(600);

			list.setModal(true);
			list.setOnItemClickListener((parent, view, position, id) -> {
				String val = itemVals[position];
				sharedPrefs.edit().putString(SyncManager.PREF_IMPORT_FROM, val).apply();

				importFromValue.setText(items[position]);
				list.dismiss();

			});
			list.show();
		});

		dialog.show();


	}
}
