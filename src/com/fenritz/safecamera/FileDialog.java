package com.fenritz.safecamera;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.fenritz.safecamera.util.Helpers;

/**
 * Activity para escolha de arquivos/diretorios.
 * 
 * @author android
 * 
 */
public class FileDialog extends ListActivity {

	public static final int MODE_CREATE = 0;

	public static final int MODE_OPEN = 1;
	
	public static final int MODE_SINGLE = 0;
	public static final int MODE_MULTIPLE = 1;
	
	/**
	 * Chave de um item da lista de paths.
	 */
	private static final String ITEM_KEY = "key";

	/**
	 * Imagem de um item da lista de paths (diretorio ou arquivo).
	 */
	private static final String ITEM_IMAGE = "image";

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

	private boolean canSelectFile = true;
	private boolean canSelectDir = false;

	private final ArrayList<File> selectedFiles = new ArrayList<File>();
	private File selectedFile;
	private final HashMap<String, Integer> lastPositions = new HashMap<String, Integer>();

	
	private BroadcastReceiver receiver;
	
	/**
	 * Called when the activity is first created. Configura todos os parametros
	 * de entrada e das VIEWS..
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED, getIntent());

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
		unregisterReceiver(receiver);
	}

	private void getDir(String dirPath) {

		boolean useAutoSelection = dirPath.length() < currentPath.length();

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

		if (!currentPath.equals(ROOT)) {

			item.add(ROOT);
			addItem(ROOT, R.drawable.folder);
			path.add(ROOT);

			item.add("../");
			addItem("../", R.drawable.folder);
			path.add(f.getParent());
			parentPath = f.getParent();

		}

		TreeMap<String, String> dirsMap = new TreeMap<String, String>();
		TreeMap<String, String> dirsPathMap = new TreeMap<String, String>();
		TreeMap<String, String> filesMap = new TreeMap<String, String>();
		TreeMap<String, String> filesPathMap = new TreeMap<String, String>();
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

		SimpleAdapter fileList = new SimpleAdapter(this, mList, R.layout.file_dialog_row, new String[] {
				ITEM_KEY, ITEM_IMAGE }, new int[] { R.id.fdrowtext, R.id.fdrowimage });

		for (String dir : dirsMap.tailMap("").values()) {
			addItem(dir, R.drawable.folder);
		}

		for (String file : filesMap.tailMap("").values()) {
			addItem(file, R.drawable.file);
		}

		fileList.notifyDataSetChanged();

		setListAdapter(fileList);

	}

	private void addItem(String fileName, int imageId) {
		HashMap<String, Object> item = new HashMap<String, Object>();
		item.put(ITEM_KEY, fileName);
		item.put(ITEM_IMAGE, imageId);
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
					((TextView)v.findViewById(R.id.fdrowtext)).setTextColor(getResources().getColor(R.color.file_manager_text));
				}
				else{
					selectedFiles.add(file);
					((TextView)v.findViewById(R.id.fdrowtext)).setTextColor(getResources().getColor(R.color.file_manager_selected));
				}
				if(selectedFiles.size() > 0){
					selectButton.setEnabled(true);
				}
				else{
					selectButton.setEnabled(false);
				}
			}
			
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
}
