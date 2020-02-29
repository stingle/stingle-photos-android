package org.stingle.photos.Gallery;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class GalleryFragment extends Fragment{

	private DragSelectRecyclerView recyclerView;
	private GalleryAdapterPisasso adapter;
	private AutoFitGridLayoutManager layoutManager;

	private ViewGroup syncBar;
	private ProgressBar syncProgress;
	private ProgressBar refreshCProgress;
	private ImageView syncPhoto;
	private TextView syncText;
	private ImageView backupCompleteIcon;
	private View topBar;
	private int lastScrollPosition = 0;
	private SwipeRefreshLayout pullToRefresh;
	protected ActionMode actionMode;

	private boolean sendBackDecryptedFile = false;
	private Intent originalIntent = null;

	/*@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.gallery_fragment,	container, false);

		recyclerView = view.findViewById(R.id.gallery_recycler_view);
		syncBar = view.findViewById(R.id.syncBar);
		syncProgress = view.findViewById(R.id.syncProgress);
		refreshCProgress = view.findViewById(R.id.refreshCProgress);
		syncPhoto = view.findViewById(R.id.syncPhoto);
		syncText = view.findViewById(R.id.syncText);
		backupCompleteIcon = view.findViewById(R.id.backupComplete);
		pullToRefresh = view.findViewById(R.id.pullToRefresh);
		topBar = view.findViewById(R.id.topBar);

		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		Bundle bundle = getArguments();
		if (bundle != null) {
			sendBackDecryptedFile = bundle.getBoolean("sendBackDecryptedFile", false);
			originalIntent = (Intent)bundle.get("originalIntent");
		}


		pullToRefresh.setOnRefreshListener(() -> {
			sendMessageToSyncService(SyncService.MSG_START_SYNC);
			pullToRefresh.setRefreshing(false);
		});

		((SimpleItemAnimator) Objects.requireNonNull(recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
		recyclerView.setHasFixedSize(true);
		layoutManager = new AutoFitGridLayoutManager(getContext(), Helpers.getThumbSize(getContext()));
		layoutManager.setSpanSizeLookup(new AutoFitGridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				if (adapter.getItemViewType(position) == GalleryAdapterPisasso.TYPE_DATE) {
					return layoutManager.getCurrentCalcSpanCount();
				}
				return 1;
			}
		});
		recyclerView.setLayoutManager(layoutManager);
		adapter = new GalleryAdapterPisasso(getContext(), this, layoutManager, SyncManager.FOLDER_MAIN);
		recyclerView.addOnScrollListener(new HidingScrollListener() {
			@Override
			public void onHide() {
				syncBar.animate().translationY(-syncBar.getHeight()-Helpers.convertDpToPixels(getContext(), 20)).setInterpolator(new AccelerateInterpolator(4));
			}


			@Override
			public void onShow() {
				syncBar.animate().translationY(0).setInterpolator(new DecelerateInterpolator(4));
			}
		});

		if(savedInstanceState != null && savedInstanceState.containsKey("scroll")){
			lastScrollPosition = savedInstanceState.getInt("scroll");
		}
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
	}

	@Override
	public void onDetach() {
		super.onDetach();
	}

	@Override
	public void onClick(int index) {
		if (adapter.isSelectionModeActive()){
			adapter.toggleSelected(index);
		}
		else {
			if(sendBackDecryptedFile){
				ArrayList<StingleDbFile> selectedFiles = new ArrayList<>();
				selectedFiles.add(adapter.getStingleFileAtPosition(index));
				ShareManager.sendBackSelection(this, originalIntent, selectedFiles, currentFolder);
			}
			else {
				Intent intent = new Intent();
				intent.setClass(this, ViewItemActivity.class);
				intent.putExtra("EXTRA_ITEM_POSITION", adapter.getDbPositionFromRaw(index));
				intent.putExtra("EXTRA_ITEM_FOLDER", currentFolder);
				startActivity(intent);
			}
		}
	}

	@Override
	public void onLongClick(int index) {
		recyclerView.setDragSelectActive(true, index);
		if(!adapter.isSelectionModeActive()){
			actionMode = getActivity().startActionMode(getActionModeCallback());
			onSelectionChanged(1);
		}
		adapter.setSelectionModeActive(true);
	}


	@Override
	public void onSelectionChanged(int count) {
		if(actionMode != null) {
			if(count == 0){
				actionMode.setTitle("");
			}
			else {
				actionMode.setTitle(String.valueOf(count));
			}
		}
	}

	protected ActionMode.Callback getActionModeCallback(){
		return new ActionMode.Callback() {
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				if(!sendBackDecryptedFile) {
					switch (currentFolder) {
						case SyncManager.FOLDER_MAIN:
							mode.getMenuInflater().inflate(R.menu.gallery_action_mode, menu);
							break;
						case SyncManager.FOLDER_TRASH:
							mode.getMenuInflater().inflate(R.menu.gallery_trash_action_mode, menu);
							break;
					}
				}
				else{
					mode.getMenuInflater().inflate(R.menu.gallery_send_back, menu);
				}

				return true;
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return true;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				switch(item.getItemId()){
					case R.id.share:
						shareSelected();
						break;
					case R.id.decrypt:
						decryptSelected();
						break;
					case R.id.trash :
						trashSelected();
						break;
					case R.id.restore :
						restoreSelected();
						break;
					case R.id.delete :
						deleteSelected();
						break;
					case R.id.send_back :
						sendBackSelected();
						break;
				}
				return true;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				exitActionMode();
				actionMode = null;
			}
		};
	}

	private void shareSelected() {
		List<Integer> indices = adapter.getSelectedIndices();
		ArrayList<StingleDbFile> files = new ArrayList<>();
		for(Integer index : indices){
			files.add(adapter.getStingleFileAtPosition(index));
		}

		ShareManager.shareDbFiles(this, files, currentFolder);
	}

	private void decryptSelected() {
		final List<Integer> indices = adapter.getSelectedIndices();
		Helpers.showConfirmDialog(getContext(), String.format(getString(R.string.confirm_decrypt_files)), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog spinner = Helpers.showProgressDialog(getContext(), getString(R.string.decrypting_files), null);
						ArrayList<StingleDbFile> files = new ArrayList<>();
						for(Integer index : indices){
							files.add(adapter.getStingleFileAtPosition(index));
						}

						File decryptDir = new File(Environment.getExternalStorageDirectory().getPath() + "/" + FileManager.DECRYPT_DIR);
						DecryptFilesAsyncTask decFilesJob = new DecryptFilesAsyncTask(getContext(), decryptDir, new OnAsyncTaskFinish() {
							@Override
							public void onFinish(ArrayList<File> files) {
								super.onFinish(files);
								exitActionMode();
								spinner.dismiss();
							}
						});
						decFilesJob.setFolder(currentFolder);
						decFilesJob.setPerformMediaScan(true);
						decFilesJob.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, files);
					}
				},
				null);
	}

	private void sendBackSelected() {
		List<Integer> indices = adapter.getSelectedIndices();
		ArrayList<StingleDbFile> files = new ArrayList<>();
		for(Integer index : indices){
			files.add(adapter.getStingleFileAtPosition(index));
		}

		ShareManager.sendBackSelection(this, originalIntent, files, currentFolder);
	}

	protected void trashSelected(){
		final List<Integer> indices = adapter.getSelectedIndices();
		Helpers.showConfirmDialog(getContext(), String.format(getString(R.string.confirm_trash_files), String.valueOf(indices.size())), (dialog, which) -> {
			final ProgressDialog spinner = Helpers.showProgressDialog(getContext(), getString(R.string.trashing_files), null);
			ArrayList<String> filenames = new ArrayList<>();
			for(Integer index : indices){
				StingleDbFile file = adapter.getStingleFileAtPosition(index);
				filenames.add(file.filename);
			}

			new SyncManager.MoveToTrashAsyncTask(getContext(), filenames, new SyncManager.OnFinish(){
				@Override
				public void onFinish(Boolean needToUpdateUI) {
					adapter.updateDataSet();
					exitActionMode();
					spinner.dismiss();
				}
			}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		},
				null);
	}

	protected void restoreSelected(){
		final List<Integer> indices = adapter.getSelectedIndices();

		final ProgressDialog spinner = Helpers.showProgressDialog(getContext(), getString(R.string.restoring_files), null);
		ArrayList<String> filenames = new ArrayList<>();
		for(Integer index : indices){
			StingleDbFile file = adapter.getStingleFileAtPosition(index);
			filenames.add(file.filename);
		}

		new SyncManager.RestoreFromTrashAsyncTask(getContext(), filenames, new SyncManager.OnFinish(){
			@Override
			public void onFinish(Boolean needToUpdateUI) {
				adapter.updateDataSet();
				exitActionMode();
				spinner.dismiss();
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

	}

	protected void deleteSelected(){
		final List<Integer> indices = adapter.getSelectedIndices();
		Helpers.showConfirmDialog(getContext(), String.format(getString(R.string.confirm_delete_files), String.valueOf(indices.size())), (dialog, which) -> {
			final ProgressDialog spinner = Helpers.showProgressDialog(getContext(), getString(R.string.deleting_files), null);
			ArrayList<String> filenames = new ArrayList<>();
			for(Integer index : indices){
				StingleDbFile file = adapter.getStingleFileAtPosition(index);
				filenames.add(file.filename);
			}

			new SyncManager.DeleteFilesAsyncTask(getContext(), filenames, new SyncManager.OnFinish(){
				@Override
				public void onFinish(Boolean needToUpdateUI) {
					adapter.updateDataSet();
					exitActionMode();
					spinner.dismiss();
				}
			}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		},
				null);
	}

	public void emptyTrash(){
		Helpers.showConfirmDialog(getContext(), String.format(getString(R.string.confirm_empty_trash)), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog spinner = Helpers.showProgressDialog(getContext(), getString(R.string.emptying_trash), null);

						new SyncManager.EmptyTrashAsyncTask(getContext(), new SyncManager.OnFinish(){
							@Override
							public void onFinish(Boolean needToUpdateUI) {
								adapter.updateDataSet();
								spinner.dismiss();
							}
						}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
				},
				null);
	}


	protected void exitActionMode(){
		adapter.clearSelected();
		recyclerView.setDragSelectActive(false, 0);
		if(actionMode != null){
			actionMode.finish();
		}
	}

	private void setSyncStatus(int syncStatus){
		if (syncStatus == SyncService.STATUS_UPLOADING) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.VISIBLE);
			syncProgress.setVisibility(View.VISIBLE);
			backupCompleteIcon.setVisibility(View.GONE);
		} else if (syncStatus == SyncService.STATUS_REFRESHING) {
			refreshCProgress.setVisibility(View.VISIBLE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.refreshing));
			backupCompleteIcon.setVisibility(View.GONE);
		} else if (syncStatus == SyncService.STATUS_NO_SPACE_LEFT) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.no_space_left));
			backupCompleteIcon.setVisibility(View.GONE);
			updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_DISABLED) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.sync_disabled));
			backupCompleteIcon.setVisibility(View.GONE);
			updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_NOT_WIFI) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.sync_not_on_wifi));
			backupCompleteIcon.setVisibility(View.GONE);
			updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_BATTERY_LOW) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.sync_battery_low));
			backupCompleteIcon.setVisibility(View.GONE);
			updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_IDLE) {
			syncText.setText(getString(R.string.backup_complete));
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			refreshCProgress.setVisibility(View.GONE);
			backupCompleteIcon.setVisibility(View.VISIBLE);
			updateQuotaInfo();
		}
	}

*/

}
