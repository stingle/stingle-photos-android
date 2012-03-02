package com.fenritz.safecamera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.Set;

import javax.crypto.Cipher;

import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.util.encoders.Hex;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
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
        ((Button)findViewById(R.id.encText)).setOnClickListener(encryptText());
        ((Button)findViewById(R.id.decText)).setOnClickListener(decryptText());
        ((Button)findViewById(R.id.decTextB)).setOnClickListener(decryptTextB());
    }
    
    private void setupAES(){
    	try {
			crypto = new AESCrypt(((EditText)findViewById(R.id.pass)).getText().toString());
		}
		catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (CryptoException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private OnClickListener encrypt(){
		return new OnClickListener() {
			public void onClick(View v) {
				try {
					setupAES();
					FileInputStream in = new FileInputStream(sdcard.toString() + "/SafeCamera/1.jpg");
					FileOutputStream out = new FileOutputStream(sdcard.toString() + "/SafeCamera/1.jpg.sc");
					crypto.encrypt(in, out);
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
    
    private OnClickListener encryptText(){
		return new OnClickListener() {
			public void onClick(View v) {
				setupAES();
				EditText text = (EditText)findViewById(R.id.text);
				text.setText(crypto.encrypt(text.getText().toString()));
			}
		};
	}
    
    private OnClickListener decryptText(){
		return new OnClickListener() {
			public void onClick(View v) {
				setupAES();
				EditText text = (EditText)findViewById(R.id.text);
				
				String clearText = crypto.decrypt(text.getText().toString());
				if(clearText != null){
					text.setText(clearText);
				}
			}
		};
	}
    
    private OnClickListener decryptTextB(){
		return new OnClickListener() {
			public void onClick(View v) {
				setupAES();
				EditText text = (EditText)findViewById(R.id.text);
				
				String clearText = crypto.decrypt(Hex.decode(text.getText().toString()));
				if(clearText != null){
					text.setText(clearText);
				}
			}
		};
	}
    
	public static void printMaxKeySizes() {
		try {
			Set<String> algorithms = Security.getAlgorithms("Cipher");
			for(String algorithm: algorithms) {
			    int max = Cipher.getMaxAllowedKeyLength(algorithm);
			    Log.d("keys", String.format("%s: %dbit",algorithm, max));
			}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
}