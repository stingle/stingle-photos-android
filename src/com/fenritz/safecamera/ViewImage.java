package com.fenritz.safecamera;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import com.fenritz.safecamera.util.AESCrypt;
import com.fenritz.safecamera.util.AESCryptException;

public class ViewImage  extends Activity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.view_photo);
		
		Intent intent = getIntent();
		String imagePath = intent.getStringExtra("EXTRA_IMAGE_PATH");
		String password = intent.getStringExtra("EXTRA_PASS");
		
		try {
			AESCrypt crypto = new AESCrypt(password);
			FileInputStream input = new FileInputStream(imagePath);
			
			byte[] decryptedData = crypto.decrypt(input);
			
			if(decryptedData != null){
				Bitmap bMap = BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.length);
				ImageView image = (ImageView) findViewById(R.id.image);
				image.setImageBitmap(bMap);
			}
			else{
				Toast.makeText(this, "Unable to decrypt image", Toast.LENGTH_LONG).show();
			}
		}
		catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (AESCryptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
}
