package org.stingle.photos.Sharing;


import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.Gallery.ShareAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.util.ArrayList;


public class SharingDialogFragment extends AppCompatDialogFragment {

	private static final int NUM_PAGES = 2;

	private ViewPager2 viewPager;
	private SharingDialogStep1Fragment step1Fragment;
	private SharingDialogStep2Fragment step2Fragment;
	private Toolbar toolbar;

	private ArrayList<StingleDbFile> files;
	private int set;
	private String albumId;
	private StingleDbAlbum album;
	private String albumName;
	private boolean onlyAddMembers = false;
	private OnAsyncTaskFinish onFinish;


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(AppCompatDialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		Context contextThemeWrapper = new ContextThemeWrapper(getActivity(), R.style.AppTheme);
		LayoutInflater localInflater = inflater.cloneInContext(contextThemeWrapper);
		View view = localInflater.inflate(R.layout.fragment_sharing_parent, container, false);

		//View view = inflater.inflate(R.layout.fragment_sharing_parent, container, false);

		toolbar = view.findViewById(R.id.toolbar);
		toolbar.setNavigationIcon(R.drawable.ic_close);
		toolbar.setNavigationOnClickListener(view1 -> dismiss());
		toolbar.setTitle(getString(R.string.share_via_sp));
		toolbar.setOnMenuItemClickListener(onMenuClicked());

		step1Fragment = new SharingDialogStep1Fragment(this);
		step2Fragment = new SharingDialogStep2Fragment();

		if(onlyAddMembers && (albumId == null || albumId.length() == 0)){
			requireDialog().dismiss();
			return null;
		}

		if (albumName == null || albumName.length() == 0) {
			albumName = Helpers.generateAlbumName();
		}

		if(files == null) {
			if (albumId != null && albumId.length() > 0) {
				step2Fragment.disableAlbumName();
				album = GalleryHelpers.getAlbum(requireContext(), albumId);
				if (album.isShared && album.permissionsObj != null) {
					step1Fragment.setExcludedIds(album.members);
					step2Fragment.setPermissions(album.permissionsObj);
				}
			}
		}

		viewPager = view.findViewById(R.id.viewPager);
		FragmentStateAdapter pagerAdapter = new ScreenSlidePagerAdapter(this);
		viewPager.setUserInputEnabled(false);
		viewPager.setAdapter(pagerAdapter);
		viewPager.registerOnPageChangeCallback(onPageChange());

		requireDialog().setOnKeyListener((dialog, keyCode, event) -> {
			if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
				if (viewPager.getCurrentItem() == 1) {
					viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
					return true;
				}
			}
			return false;
		});

		return view;
	}

	private ViewPager2.OnPageChangeCallback onPageChange() {
		return new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);

				toolbar.getMenu().clear();
				if(!onlyAddMembers) {
					if (position == 0) {
						toolbar.inflateMenu(R.menu.sharing_dialog_step1);
					} else if (position == 1) {
						toolbar.inflateMenu(R.menu.sharing_dialog_step2);
					}
				}
				else{
					toolbar.inflateMenu(R.menu.sharing_dialog_step2);
				}
			}
		};
	}

	private Toolbar.OnMenuItemClickListener onMenuClicked() {
		return item -> {
			int id = item.getItemId();

			if (id == R.id.nextButton) {
				step1Fragment.addRecipient(new OnAsyncTaskFinish(){
					@Override
					public void onFinish() {
						super.onFinish();
						if(step1Fragment.getSelectedRecipients().size() > 0) {
							viewPager.setCurrentItem(1, true);
							step2Fragment.setAlbumName(albumName);
						}
						else{
							showSnack(requireContext().getString(R.string.select_recp));
						}
					}

					@Override
					public void onFail() {
						super.onFail();
					}
				});
			}
			else if (id == R.id.shareButton) {
				if(onlyAddMembers){
					step1Fragment.addRecipient(new OnAsyncTaskFinish(){
						@Override
						public void onFinish() {
							super.onFinish();
							if(step1Fragment.getSelectedRecipients().size() > 0) {
								share();
							}
							else{
								showSnack(requireContext().getString(R.string.select_recp));
							}
						}
					});
				}
				else {
					share();
				}
			}
			return false;
		};
	}

	private void validateStep1(){

	}

	private void share() {
		final ProgressDialog spinner = Helpers.showProgressDialog(getActivity(), requireContext().getString(R.string.spinner_sharing), null);
		ShareAlbumAsyncTask shareTask = new ShareAlbumAsyncTask(getActivity(), new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				spinner.dismiss();
				requireDialog().dismiss();
				Object activity = getActivity();
				if(activity instanceof GalleryActivity) {
					((GalleryActivity)activity).exitActionMode();
				}
				Snackbar.make(requireActivity().findViewById(R.id.drawer_layout), requireContext().getString(R.string.share_success), Snackbar.LENGTH_LONG).show();
				if(onFinish != null){
					onFinish.onFinish();
				}
				SyncManager.startSync(requireContext());
			}

			@Override
			public void onFail(String msg) {
				super.onFail();
				spinner.dismiss();
				if(msg != null){
					showSnack(msg);
				}
				else {
					showSnack(requireContext().getString(R.string.failed_to_share));
				}
				if(onFinish != null){
					onFinish.onFail();
				}
			}
		});

		if(albumId != null && (onlyAddMembers || set == SyncManager.ALBUM) && files == null) {
			shareTask.setAlbumId(albumId);
		}
		else{
			shareTask.setFiles(files);
			shareTask.setSourceAlbumId(albumId);
			shareTask.setSourceSet(set);
			shareTask.setAlbumName(step2Fragment.getAlbumName());
		}
		shareTask.setRecipients(step1Fragment.getSelectedRecipients());

		if(onlyAddMembers){
			shareTask.setPermissions(album.permissionsObj);
		}
		else {
			shareTask.setPermissions(step2Fragment.getPermissions());
		}

		shareTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public void setFiles(ArrayList<StingleDbFile> files){
		this.files = files;
	}
	public void setSourceSet(int set){
		this.set = set;
	}
	public void setAlbumId(String albumId){
		this.albumId = albumId;
	}
	public void setAlbumName(String albumName){
		this.albumName = albumName;
	}
	public void setOnlyAddMembers(boolean onlyAddMembers){
		this.onlyAddMembers = onlyAddMembers;
	}
	public void setOnFinish(OnAsyncTaskFinish onFinish){
		this.onFinish = onFinish;
	}

	private class ScreenSlidePagerAdapter extends FragmentStateAdapter {


		ScreenSlidePagerAdapter(AppCompatDialogFragment parent) {
			super(parent);
		}

		@NonNull
		@Override
		public Fragment createFragment(int position) {
			if(position == 0) {
				return step1Fragment;
			}
			else if(position == 1){
				return step2Fragment;
			}
			return step1Fragment;
		}

		@Override
		public int getItemCount() {
			return NUM_PAGES;
		}
	}

	public void showSnack(String message){
		Snackbar.make(requireDialog().findViewById(R.id.drawer_layout), message, Snackbar.LENGTH_LONG).show();
	}

}
