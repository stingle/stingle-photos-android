package com.fenritz.safecamera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.fenritz.safecamera.util.AESCrypt;

public class SafeCameraActivity extends Activity {
    /** Called when the activity is first created. */
	AESCrypt crypto;
	File sdcard;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        sdcard = Environment.getExternalStorageDirectory();
        
        
        ((Button)findViewById(R.id.encrypt)).setOnClickListener(encrypt());
        ((Button)findViewById(R.id.decrypt)).setOnClickListener(decrypt());
    }
    
    private void setupAES(){
    	crypto = new AESCrypt(((EditText)findViewById(R.id.pass)).getText().toString());
    }
    
    private OnClickListener encrypt(){
		return new OnClickListener() {
			public void onClick(View v) {
				try {
					setupAES();
					crypto.encrypt(new FileInputStream(sdcard.toString() + "/SafeCamera/1.jpg"), new FileOutputStream(sdcard.toString() + "/SafeCamera/1.jpg.sc"));
				}
				catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
	}
    
    private OnClickListener decrypt(){
		return new OnClickListener() {
			public void onClick(View v) {
				try {
					setupAES();
					crypto.decrypt(new FileInputStream(sdcard.toString() + "/SafeCamera/1.jpg.sc"), new FileOutputStream(sdcard.toString() + "/SafeCamera/1_dec.jpg"));
				}
				catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
	}
}