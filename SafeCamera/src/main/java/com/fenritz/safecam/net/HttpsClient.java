package com.fenritz.safecam.Net;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class HttpsClient {

	public static void post(Context context, String urlStr, HashMap<String, String> params, OnNetworkFinish onFinish) {
		new PostRequest(context, urlStr, params, onFinish).execute();
	}
	public static void uploadFiles(Context context, String urlStr, HashMap<String, String> params, ArrayList<FileToUpload> files, OnUploadFinish onFinish) {
		new UploadRequest(context, urlStr, params, files, onFinish).execute();
	}

	public static JSONObject postFunc(String urlStr, HashMap<String, String> params) {
		JSONObject json = null;
		try {
			Log.e("url", urlStr);
			URL url = new URL(urlStr);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

			// Create the SSL connection
			SSLContext sc;
			sc = SSLContext.getInstance("TLS");
			sc.init(null, null, new java.security.SecureRandom());
			conn.setSSLSocketFactory(sc.getSocketFactory());

			// Use this if you need SSL authentication
			//String userpass = user + ":" + password;
			//String basicAuth = "Basic " + Base64.encodeToString(userpass.getBytes(), Base64.DEFAULT);
			//conn.setRequestProperty("Authorization", basicAuth);

			// set Timeout and method
			conn.setReadTimeout(7000);
			conn.setConnectTimeout(7000);
			conn.setRequestMethod("POST");
			conn.setDoInput(true);

			// Add any data you wish to post here
			String data = "";
			if(params != null) {
				for (String key : params.keySet()) {
					data += URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(params.get(key), "UTF-8") + "&";
				}
			}

			if (data.length() > 0) {
				data = data.substring(0, data.length() - 1);

				OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());

				wr.write(data);
				wr.flush();
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String line = null;

			// Read Server Response
			while ((line = reader.readLine()) != null) {
				// Append server response in string
				sb.append(line + "\n");
			}
			Log.e("resultStr", sb.toString());
			json = new JSONObject(sb.toString());

			reader.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}

		//conn.connect();
		return json;
	}

	public static void downloadFile(String urlStr, HashMap<String, String> params, String outputPath) throws IOException, NoSuchAlgorithmException, KeyManagementException {
		Log.e("url", urlStr);
		URL url = new URL(urlStr);
		HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

		// Create the SSL connection
		SSLContext sc;
		sc = SSLContext.getInstance("TLS");
		sc.init(null, null, new java.security.SecureRandom());
		conn.setSSLSocketFactory(sc.getSocketFactory());

		// Use this if you need SSL authentication
		//String userpass = user + ":" + password;
		//String basicAuth = "Basic " + Base64.encodeToString(userpass.getBytes(), Base64.DEFAULT);
		//conn.setRequestProperty("Authorization", basicAuth);

		// set Timeout and method
		conn.setReadTimeout(7000);
		conn.setConnectTimeout(7000);
		conn.setRequestMethod("POST");
		conn.setDoInput(true);
		conn.setDoOutput(true);

		// Add any data you wish to post here
		String data = "";
		if(params != null) {
			for (String key : params.keySet()) {
				data += URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(params.get(key), "UTF-8") + "&";
			}
		}

		if (data.length() > 0) {
			data = data.substring(0, data.length() - 1);

			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());

			wr.write(data);
			wr.flush();
		}

		int lenghtOfFile = conn.getContentLength();

		// download the file
		InputStream input = new BufferedInputStream(conn.getInputStream(),8192);

		// Output stream
		FileOutputStream output = new FileOutputStream(outputPath);

		byte buf[] = new byte[1024];

		long total = 0;
		int count;

		while ((count = input.read(buf)) != -1) {
			total += count;
			// publishing the progress....
			// After this onProgressUpdate will be called
			//publishProgress("" + (int) ((total * 100) / lenghtOfFile));

			// writing data to
			//Log.d("file", new String(buf));
			output.write(buf, 0, count);
		}

		// flushing output
		output.flush();

		// closing streams
		output.close();
		input.close();

	}

	public static byte[] getFileAsByteArray(String urlStr, HashMap<String, String> params) throws IOException, NoSuchAlgorithmException, KeyManagementException {
		Log.e("url", urlStr);
		URL url = new URL(urlStr);
		HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

		// Create the SSL connection
		SSLContext sc;
		sc = SSLContext.getInstance("TLS");
		sc.init(null, null, new java.security.SecureRandom());
		conn.setSSLSocketFactory(sc.getSocketFactory());

		// Use this if you need SSL authentication
		//String userpass = user + ":" + password;
		//String basicAuth = "Basic " + Base64.encodeToString(userpass.getBytes(), Base64.DEFAULT);
		//conn.setRequestProperty("Authorization", basicAuth);

		// set Timeout and method
		conn.setReadTimeout(7000);
		conn.setConnectTimeout(7000);
		conn.setRequestMethod("POST");
		conn.setDoInput(true);
		conn.setDoOutput(true);

		// Add any data you wish to post here
		String data = "";
		if(params != null) {
			for (String key : params.keySet()) {
				Log.d("param" , key + " - " + params.get(key));
				data += URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(params.get(key), "UTF-8") + "&";
			}
		}

		if (data.length() > 0) {
			data = data.substring(0, data.length() - 1);

			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());

			wr.write(data);
			wr.flush();
		}

		int lenghtOfFile = conn.getContentLength();

		// download the file
		InputStream input = new BufferedInputStream(conn.getInputStream(),8192);

		// Output stream
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		byte buf[] = new byte[1024];

		long total = 0;
		int count;

		while ((count = input.read(buf)) != -1) {
			total += count;
			// publishing the progress....
			// After this onProgressUpdate will be called
			//publishProgress("" + (int) ((total * 100) / lenghtOfFile));

			// writing data to file
			output.write(buf, 0, count);
		}

		// closing streams
		input.close();

		return output.toByteArray();
	}

	public static JSONObject multipartUpload(String urlTo, HashMap<String, String> params, FileToUpload file)  {
		ArrayList<FileToUpload> files = new ArrayList<FileToUpload>();
		files.add(file);
		return multipartUpload(urlTo, params, files);
	}

	public static JSONObject multipartUpload(String urlTo, HashMap<String, String> params, ArrayList<FileToUpload> files)  {
		HttpsURLConnection connection = null;
		DataOutputStream outputStream = null;
		InputStream inputStream = null;

		JSONObject json = null;

		String twoHyphens = "--";
		String boundary = "*****" + Long.toString(System.currentTimeMillis()) + "*****";
		String lineEnd = "\r\n";

		String result = "";

		int bytesRead, bytesAvailable, bufferSize;
		byte[] buffer;
		int maxBufferSize = 1 * 1024 * 1024;



		try {
			URL url = new URL(urlTo);
			connection = (HttpsURLConnection) url.openConnection();

			// Create the SSL connection
			SSLContext sc;
			sc = SSLContext.getInstance("TLS");
			sc.init(null, null, new java.security.SecureRandom());
			connection.setSSLSocketFactory(sc.getSocketFactory());

			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setChunkedStreamingMode(0);

			connection.setRequestMethod("POST");
			connection.setRequestProperty("Connection", "Keep-Alive");
			connection.setRequestProperty("User-Agent", "Stingle Photos HTTP Client 1.0");
			connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);


			for(FileToUpload file : files) {
				String[] q = file.filePath.split("/");
				int idx = q.length - 1;

				FileInputStream fileInputStream = new FileInputStream(file.filePath);

				outputStream = new DataOutputStream(connection.getOutputStream());
				outputStream.writeBytes(twoHyphens + boundary + lineEnd);
				outputStream.writeBytes("Content-Disposition: form-data; name=\"" + file.name + "\"; filename=\"" + q[idx] + "\"" + lineEnd);
				outputStream.writeBytes("Content-Type: " + file.mimeType + lineEnd);
				outputStream.writeBytes("Content-Transfer-Encoding: binary" + lineEnd);

				outputStream.writeBytes(lineEnd);

				bytesAvailable = fileInputStream.available();
				bufferSize = Math.min(bytesAvailable, maxBufferSize);
				buffer = new byte[bufferSize];

				bytesRead = fileInputStream.read(buffer, 0, bufferSize);
				while (bytesRead > 0) {
					outputStream.write(buffer, 0, bufferSize);
					bytesAvailable = fileInputStream.available();
					bufferSize = Math.min(bytesAvailable, maxBufferSize);
					bytesRead = fileInputStream.read(buffer, 0, bufferSize);
				}

				outputStream.writeBytes(lineEnd);
				fileInputStream.close();
			}

			// Upload POST Data
			Iterator<String> keys = params.keySet().iterator();
			while (keys.hasNext()) {
				String key = keys.next();
				String value = params.get(key);

				outputStream.writeBytes(twoHyphens + boundary + lineEnd);
				outputStream.writeBytes("Content-Disposition: form-data; name=\"" + key + "\"" + lineEnd);
				outputStream.writeBytes("Content-Type: text/plain" + lineEnd);
				outputStream.writeBytes(lineEnd);
				outputStream.writeBytes(value);
				outputStream.writeBytes(lineEnd);
			}

			outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);


			if (200 != connection.getResponseCode()) {
				throw new IOException("Failed to upload code:" + connection.getResponseCode() + " " + connection.getResponseMessage());
			}

			inputStream = connection.getInputStream();

			result = convertStreamToString(inputStream);


			inputStream.close();
			outputStream.flush();
			outputStream.close();
			Log.e("resultStr", result);
			json = new JSONObject(result);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (ProtocolException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return json;
	}

	private static String convertStreamToString(InputStream is) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return sb.toString();
	}

	public static class PostRequest extends AsyncTask<Void, Void, JSONObject> {

		protected Context context;
		protected String url;
		protected HashMap<String, String> params;
		protected OnNetworkFinish onFinish;

		public PostRequest(Context context, String url){
			this(context, url, null, null);
		}

		public PostRequest(Context context, String url, HashMap<String, String> params){
			this(context, url, params, null);
		}

		public PostRequest(Context context, String url, HashMap<String, String> params, OnNetworkFinish onFinish){
			this.context = context;
			this.url = url;
			this.params = params;
			this.onFinish = onFinish;
		}

		@Override
		protected JSONObject doInBackground(Void... params) {
			return postFunc(this.url, this.params);
		}

		@Override
		protected void onPostExecute(JSONObject result) {
			super.onPostExecute(result);
			if(this.onFinish != null) {
				this.onFinish.onFinish(new StingleResponse(this.context, result));
			}
		}
	}

	public static class UploadRequest extends AsyncTask<Void, Void, JSONObject> {

		protected Context context;
		protected String url;
		protected HashMap<String, String> params;
		protected ArrayList<FileToUpload> files;
		protected OnUploadFinish onFinish;

		public UploadRequest(Context context, String url, HashMap<String, String> params, ArrayList<FileToUpload> files, OnUploadFinish onFinish){
			this.context = context;
			this.url = url;
			this.params = params;
			this.files = files;
			this.onFinish = onFinish;
		}

		@Override
		protected JSONObject doInBackground(Void... params) {
			return multipartUpload(this.url, this.params, files);
			//"application/stinglephoto"
		}

		@Override
		protected void onPostExecute(JSONObject result) {
			super.onPostExecute(result);
			if(this.onFinish != null) {
				this.onFinish.onFinish(new StingleResponse(this.context, result));
			}
		}
	}

	abstract public static class OnNetworkFinish {
		abstract public void onFinish(StingleResponse response);
	}

	abstract public static class OnUploadFinish {
		abstract public void onFinish(StingleResponse response);
	}

	public static class FileToUpload {
		public String name;
		public String filePath;
		public String mimeType;

		public FileToUpload(String name, String filePath, String mimeType){
			this.name = name;
			this.filePath = filePath;
			this.mimeType = mimeType;
		}
	}

}
