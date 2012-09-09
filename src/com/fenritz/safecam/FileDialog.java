package com.fenritz.safecam;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore.Video.Thumbnails;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.MemoryCache;

/**
 * Activity para escolha de arquivos/diretorios.
 * 
 * @author android
 * 
 */
public class FileDialog extends SherlockListActivity {

	public static final int MODE_CREATE = 0;

	public static final int MODE_OPEN = 1;
	
	public static final int MODE_SINGLE = 0;
	public static final int MODE_MULTIPLE = 1;
	
	private static final int TYPE_FOLDER = 0;
	private static final int TYPE_FILE = 1;
	
	/**
	 * Chave de um item da lista de paths.
	 */
	private static final String ITEM_KEY = "key";
	
	/**
	 * Imagem de um item da lista de paths (diretorio ou arquivo).
	 */
	private static final String ITEM_TYPE = "type";

	/**
	 * Diretorio raiz.
	 */
	private static final String ROOT = "/";

	/**
	 * Parametro de entrada da Activity: path inicial. Padrao: ROOT.
	 */
	public static final String START_PATH = "START_PATH";

	/**
	 * Parametro de entrada da Activity: filtro de formatos de arquivos. Padrao:
	 * null.
	 */
	public static final String FORMAT_FILTER = "FORMAT_FILTER";

	/**
	 * Parametro de saida da Activity: path escolhido. Padrao: null.
	 */
	public static final String RESULT_PATH = "RESULT_PATH";

	/**
	 * Parametro de entrada da Activity: tipo de selecao: pode criar novos paths
	 * ou nao. Padrao: nao permite.
	 * 
	 * @see {@link SelectionMode}
	 */
	public static final String SELECTION_MODE = "SELECTION_MODE";
	
	public static final String FILE_SELECTION_MODE = "FILE_SELECTION_MODE";

	/**
	 * Parametro de entrada da Activity: se e permitido escolher diretorios.
	 * Padrao: falso.
	 */
	public static final String CAN_SELECT_FILE = "CAN_SELECT_FILE";
	public static final String CAN_SELECT_DIR = "CAN_SELECT_DIR";

	private List<String> path = null;
	private TextView myPath;
	private EditText mFileName;
	private ArrayList<HashMap<String, Object>> mList;

	private Button selectButton;

	private LinearLayout layoutSelect;
	private LinearLayout layoutCreate;
	private InputMethodManager inputManager;
	private String parentPath;
	private String currentPath = ROOT;

	private int selectionMode = MODE_CREATE;
	private int fileSelectionMode = MODE_SINGLE;

	private String[] formatFilter = null;
	
	
	private static final int SELECT_NONE = 0;
	private static final int SELECT_ALL = 1;
	
	private int selectedMode = SELECT_NONE;
	private MenuItem selectAllMenuItem;

	private boolean canSelectFile = true;
	private boolean canSelectDir = false;

	private final ArrayList<File> selectedFiles = new ArrayList<File>();
	private File selectedFile;
	private final HashMap<String, Integer> lastPositions = new HashMap<String, Integer>();

	private final MemoryCache cache = new MemoryCache();
	
	private BroadcastReceiver receiver;
	
	/**
	 * Called when the activity is first created. Configura todos os parametros
	 * de entrada e das VIEWS..
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED, getIntent());

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		
		setContentView(R.layout.file_dialog_main);
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
		
		myPath = (TextView) findViewById(R.id.path);
		mFileName = (EditText) findViewById(R.id.fdEditTextFile);

		inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

		selectButton = (Button) findViewById(R.id.fdButtonSelect);
		selectButton.setEnabled(false);
		selectButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if(fileSelectionMode == MODE_SINGLE && selectedFile != null){
					getIntent().putExtra(RESULT_PATH, selectedFile.getPath());
					setResult(RESULT_OK, getIntent());
					finish();
				}
				else if(fileSelectionMode == MODE_MULTIPLE && selectedFiles.size() > 0){
					String[] filePaths = new String[selectedFiles.size()];
					int counter = 0;
					for(File selectedFile : selectedFiles){
						filePaths[counter++] = selectedFile.getPath();
					}
					getIntent().putExtra(RESULT_PATH, filePaths);
					setResult(RESULT_OK, getIntent());
					finish();
				}
			}
		});

		final Button newButton = (Button) findViewById(R.id.fdButtonNew);
		newButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				setCreateVisible(v);

				mFileName.setText("");
				mFileName.requestFocus();
			}
		});

		selectionMode = getIntent().getIntExtra(SELECTION_MODE, MODE_CREATE);
		fileSelectionMode = getIntent().getIntExtra(FILE_SELECTION_MODE, MODE_SINGLE);

		formatFilter = getIntent().getStringArrayExtra(FORMAT_FILTER);

		canSelectFile = getIntent().getBooleanExtra(CAN_SELECT_FILE, true);
		canSelectDir = getIntent().getBooleanExtra(CAN_SELECT_DIR, false);

		if (selectionMode == MODE_OPEN) {
			newButton.setVisibility(View.GONE);
		}

		layoutSelect = (LinearLayout) findViewById(R.id.fdLinearLayoutSelect);
		layoutCreate = (LinearLayout) findViewById(R.id.fdLinearLayoutCreate);
		layoutCreate.setVisibility(View.GONE);

		final Button cancelButton = (Button) findViewById(R.id.fdButtonCancel);
		cancelButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				setSelectVisible(v);
			}

		});
		final Button createButton = (Button) findViewById(R.id.fdButtonCreate);
		createButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if (mFileName.getText().length() > 0) {
					getIntent().putExtra(RESULT_PATH, currentPath + "/" + mFileName.getText());
					setResult(RESULT_OK, getIntent());
					finish();
				}
			}
		});

		String startPath = getIntent().getStringExtra(START_PATH);
		startPath = startPath != null ? startPath : ROOT;
		if (canSelectDir) {
			File file = new File(startPath);
			selectedFile = file;
			selectButton.setEnabled(true);
		}
		getDir(startPath);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(receiver != null){
			unregisterReceiver(receiver);
		}
	}

	private void selectAll(){
		for(int i=0;i<path.size();i++){
			File file = new File(path.get(i));
			if(file.isFile()){
				selectedFiles.add(file);
			}
		}
		((BaseAdapter)getListView().getAdapter()).notifyDataSetChanged();
		selectButton.setEnabled(true);
		selectedMode = SELECT_ALL;
		if(selectAllMenuItem != null){
			selectAllMenuItem.setIcon(R.drawable.ic_action_checkbox_checked);
		}
	}
	
	private void unSelectAll(){
		selectedFiles.clear();
		
		BaseAdapter adapter = ((BaseAdapter)getListView().getAdapter());
		if(adapter != null){
			adapter.notifyDataSetChanged();
		}
		selectButton.setEnabled(false);
		selectedMode = SELECT_NONE;
		if(selectAllMenuItem != null){
			selectAllMenuItem.setIcon(R.drawable.ic_action_checkbox_unchecked);
		}
	}
	
	private void getDir(String dirPath) {

		boolean useAutoSelection = dirPath.length() < currentPath.length();
		
		unSelectAll();

		Integer position = lastPositions.get(parentPath);

		getDirImpl(dirPath);

		if (position != null && useAutoSelection) {
			getListView().setSelection(position);
		}

	}

	/**
	 * Monta a estrutura de arquivos e diretorios filhos do diretorio fornecido.
	 * 
	 * @param dirPath
	 *            Diretorio pai.
	 */
	private void getDirImpl(final String dirPath) {

		currentPath = dirPath;

		final List<String> item = new ArrayList<String>();
		path = new ArrayList<String>();
		mList = new ArrayList<HashMap<String, Object>>();

		File f = new File(currentPath);
		File[] files = f.listFiles();
		if (files == null) {
			currentPath = ROOT;
			f = new File(currentPath);
			files = f.listFiles();
		}
		myPath.setText(getText(R.string.location) + ": " + currentPath);

		/*Arrays.sort(files, new Comparator<File>() {
			public int compare(File lhs, File rhs) {
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});*/
		
		if (!currentPath.equals(ROOT)) {

			/*item.add(ROOT);
			addItem(ROOT, TYPE_FOLDER);
			path.add(ROOT);*/

			item.add("../");
			addItem("../", TYPE_FOLDER);
			path.add(f.getParent());
			parentPath = f.getParent();

		}

		TreeMap<String, String> dirsMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		TreeMap<String, String> dirsPathMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		TreeMap<String, String> filesMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		TreeMap<String, String> filesPathMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		for (File file : files) {
			if (file.isDirectory()) {
				String dirName = file.getName();
				dirsMap.put(dirName, dirName);
				dirsPathMap.put(dirName, file.getPath());
			} else {
				final String fileName = file.getName();
				final String fileNameLwr = fileName.toLowerCase();
				// se ha um filtro de formatos, utiliza-o
				if (formatFilter != null) {
					boolean contains = false;
					for (int i = 0; i < formatFilter.length; i++) {
						final String formatLwr = formatFilter[i].toLowerCase();
						if (fileNameLwr.endsWith(formatLwr)) {
							contains = true;
							break;
						}
					}
					if (contains) {
						filesMap.put(fileName, fileName);
						filesPathMap.put(fileName, file.getPath());
					}
					// senao, adiciona todos os arquivos
				} else {
					filesMap.put(fileName, fileName);
					filesPathMap.put(fileName, file.getPath());
				}
			}
		}
		item.addAll(dirsMap.tailMap("").values());
		item.addAll(filesMap.tailMap("").values());
		path.addAll(dirsPathMap.tailMap("").values());
		path.addAll(filesPathMap.tailMap("").values());

		/*SimpleAdapter fileList = new SimpleAdapter(this, mList, R.layout.file_dialog_row, new String[] {
				ITEM_KEY, ITEM_IMAGE }, new int[] { R.id.fdrowtext, R.id.fdrowimage });*/
		
		//fileList.setViewBinder(new BitmapViewBinder());

		FilesAdapter fileList = new FilesAdapter(mList);
		
		for (String dir : dirsMap.tailMap("").values()) {
			addItem(dir, TYPE_FOLDER);
		}

		for (String file : filesMap.tailMap("").values()) {
			addItem(file, TYPE_FILE);
		}

		fileList.notifyDataSetChanged();

		setListAdapter(fileList);

	}
	
	public class FilesAdapter extends BaseAdapter {
		private final ArrayList<HashMap<String,Object>> list;
		
		public FilesAdapter(ArrayList<HashMap<String,Object>> mList){
			list = mList;
		}
		
		public int getCount() {
			return list.size();
		}

		public Object getItem(int position) {
			return list.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater layoutInflater = LayoutInflater.from(FileDialog.this);
			View fileRow = layoutInflater.inflate(R.layout.file_dialog_row, null);
			
			HashMap<String,Object> fileArr = list.get(position);
			String filePath = currentPath + "/" + fileArr.get(ITEM_KEY).toString();
			File file = new File(filePath);
			
			
			((TextView)fileRow.findViewById(R.id.fdrowtext)).setText(fileArr.get(ITEM_KEY).toString());
			if(selectedFiles.contains(file) || (selectedFile != null && selectedFile.equals(file))){
				((TextView)fileRow.findViewById(R.id.fdrowtext)).setTextColor(getResources().getColor(R.color.file_manager_selected));
			}
			
			Integer type = (Integer)fileArr.get(ITEM_TYPE);
			
			ImageView image = (ImageView)fileRow.findViewById(R.id.fdrowimage);
			
			Bitmap fileIcon = BitmapFactory.decodeResource(getResources(), R.drawable.file);
			switch(type){
				case TYPE_FILE:
					Bitmap cachedImage = cache.get(filePath);
					if(cachedImage != null){
						fileIcon = cachedImage;
					}
					else{
						new ShowImageThumb(image).execute(file);
					}
					break;
				case TYPE_FOLDER:
					fileIcon = BitmapFactory.decodeResource(getResources(), R.drawable.folder);
					break;
			}
			image.setImageBitmap(fileIcon);
			return fileRow;
		}
	}
	
	private class ShowImageThumb extends AsyncTask<File, Void, Bitmap> {

		private final ImageView imageView;
		
		public ShowImageThumb(ImageView pImageView){
			imageView = pImageView;
		}
		
		@Override
		protected Bitmap doInBackground(File... params) {
			Bitmap image = Helpers.decodeFile(params[0], 50);
			
			if(image != null){
				cache.put(params[0].getPath(), image);
				return image;
			}
			
			image = ThumbnailUtils.createVideoThumbnail(params[0].getPath(), Thumbnails.MICRO_KIND);
			
			if(image != null){
				cache.put(params[0].getPath(), image);
				return image;
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			if(result != null){
				imageView.setImageBitmap(result);
			}
		}

	}

	 public class BitmapViewBinder implements ViewBinder {
         public boolean setViewValue(View view, Object data, String textRepresentation) {
                 if( (view instanceof ImageView) & (data instanceof Bitmap) ) {
                         ImageView iv = (ImageView) view;
                         Bitmap bm = (Bitmap) data;     
                         iv.setImageBitmap(bm); 
                         return true;
                 }
                 return false;
         }

	 }
	
	private void addItem(String fileName, int type) {
		HashMap<String, Object> item = new HashMap<String, Object>();
		item.put(ITEM_KEY, fileName);
		item.put(ITEM_TYPE, type);
		mList.add(item);
	}

	/**
	 * Quando clica no item da lista, deve-se: 1) Se for diretorio, abre seus
	 * arquivos filhos; 2) Se puder escolher diretorio, define-o como sendo o
	 * path escolhido. 3) Se for arquivo, define-o como path escolhido. 4) Ativa
	 * botao de selecao.
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		File file = new File(path.get(position));

		if (file.isDirectory()) {
			setSelectVisible(v);
			selectButton.setEnabled(false);
			if (file.canRead()) {
				lastPositions.put(currentPath, position);
				getDir(path.get(position));
				if (canSelectDir) {
					selectedFile = file;
					v.setSelected(true);
					selectButton.setEnabled(true);
				}
			} else {
				new AlertDialog.Builder(this).setIcon(R.drawable.icon)
						.setTitle("[" + file.getName() + "] " + getText(R.string.cant_read_folder))
						.setPositiveButton("OK", new DialogInterface.OnClickListener() {

							public void onClick(DialogInterface dialog, int which) {

							}
						}).show();
			}
		} else if (canSelectFile) {
			setSelectVisible(v);
			if(fileSelectionMode == MODE_SINGLE){
				selectedFile = file;
				v.setSelected(true);
				selectButton.setEnabled(true);
			}
			else if(fileSelectionMode == MODE_MULTIPLE){
				if(selectedFiles.contains(file)){
					selectedFiles.remove(file);
				}
				else{
					selectedFiles.add(file);
				}
				
				if(selectedFiles.size() > 0){
					selectButton.setEnabled(true);
				}
				else{
					selectButton.setEnabled(false);
				}
			}
			((BaseAdapter)getListView().getAdapter()).notifyDataSetChanged();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			selectButton.setEnabled(false);

			if (layoutCreate.getVisibility() == View.VISIBLE) {
				layoutCreate.setVisibility(View.GONE);
				layoutSelect.setVisibility(View.VISIBLE);
			} else {
				if (!currentPath.equals(ROOT)) {
					getDir(parentPath);
				} else {
					return super.onKeyDown(keyCode, event);
				}
			}

			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	/**
	 * Define se o botao de CREATE e visivel.
	 * 
	 * @param v
	 */
	private void setCreateVisible(View v) {
		layoutCreate.setVisibility(View.VISIBLE);
		layoutSelect.setVisibility(View.GONE);

		inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
		selectButton.setEnabled(false);
	}

	/**
	 * Define se o botao de SELECT e visivel.
	 * 
	 * @param v
	 */
	private void setSelectVisible(View v) {
		layoutCreate.setVisibility(View.GONE);
		layoutSelect.setVisibility(View.VISIBLE);

		inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
		selectButton.setEnabled(false);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		Helpers.setLockedTime(this);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.file_dialog_menu, menu);
		selectAllMenuItem = menu.findItem(R.id.select_all);
		if(fileSelectionMode == MODE_SINGLE){
			selectAllMenuItem.setVisible(false);
		}
        return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			case R.id.select_all:
				if (selectedMode == SELECT_NONE) {
					selectAll();
				}
				else {
					unSelectAll();
				}
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
}
