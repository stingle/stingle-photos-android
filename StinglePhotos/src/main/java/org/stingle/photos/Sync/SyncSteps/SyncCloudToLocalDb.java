package org.stingle.photos.Sync.SyncSteps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.ContactsDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Gallery.Albums.AlbumsFragment;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class SyncCloudToLocalDb {

	private Context context;
	private final GalleryTrashDb galleryDb;
	private final GalleryTrashDb trashDb;
	private final AlbumsDb albumsDb;
	private final AlbumFilesDb albumFilesDb;
	private final ContactsDb contactsDb;
	private long lastSeenTime = 0;
	private long lastTrashSeenTime = 0;
	private long lastAlbumsSeenTime = 0;
	private long lastAlbumFilesSeenTime = 0;
	private long lastDelSeenTime = 0;
	private long lastContactsSeenTime = 0;
	private boolean isFirstSyncDone;

	public SyncCloudToLocalDb(Context context){
		this.context = context;
		galleryDb = new GalleryTrashDb(context, SyncManager.GALLERY);
		trashDb = new GalleryTrashDb(context, SyncManager.TRASH);
		albumsDb = new AlbumsDb(context);
		albumFilesDb = new AlbumFilesDb(context);
		contactsDb = new ContactsDb(context);
		isFirstSyncDone = Helpers.getPreference(context, SyncManager.PREF_FIRST_SYNC_DONE, false);
	}

	public boolean sync(){
		if(!LoginManager.isLoggedIn(context)) {
			return false;
		}
		SyncManager.setSyncStatus(context, SyncManager.STATUS_REFRESHING);

		lastSeenTime = Helpers.getPreference(context, SyncManager.PREF_LAST_SEEN_TIME, (long) 0);
		lastTrashSeenTime = Helpers.getPreference(context, SyncManager.PREF_TRASH_LAST_SEEN_TIME, (long) 0);
		lastAlbumsSeenTime = Helpers.getPreference(context, SyncManager.PREF_ALBUMS_LAST_SEEN_TIME, (long) 0);
		lastAlbumFilesSeenTime = Helpers.getPreference(context, SyncManager.PREF_ALBUM_FILES_LAST_SEEN_TIME, (long) 0);
		lastDelSeenTime = Helpers.getPreference(context, SyncManager.PREF_LAST_DEL_SEEN_TIME, (long) 0);
		lastContactsSeenTime = Helpers.getPreference(context, SyncManager.PREF_LAST_CONTACTS_SEEN_TIME, (long) 0);

		boolean needToUpdateUI = false;

		try {
			needToUpdateUI = getFileList(context);

			Helpers.storePreference(context, SyncManager.PREF_LAST_SEEN_TIME, lastSeenTime);
			Helpers.storePreference(context, SyncManager.PREF_TRASH_LAST_SEEN_TIME, lastTrashSeenTime);
			Helpers.storePreference(context, SyncManager.PREF_ALBUMS_LAST_SEEN_TIME, lastAlbumsSeenTime);
			Helpers.storePreference(context, SyncManager.PREF_ALBUM_FILES_LAST_SEEN_TIME, lastAlbumFilesSeenTime);
			Helpers.storePreference(context, SyncManager.PREF_LAST_DEL_SEEN_TIME, lastDelSeenTime);
			Helpers.storePreference(context, SyncManager.PREF_LAST_CONTACTS_SEEN_TIME, lastContactsSeenTime);
		} catch (JSONException | RuntimeException e) {
			e.printStackTrace();
		}

		galleryDb.close();
		trashDb.close();
		albumsDb.close();
		albumFilesDb.close();

		return needToUpdateUI;
	}

	private boolean getFileList(Context context) throws JSONException, RuntimeException {
		boolean needToUpdateUI = false;
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));

		postParams.put("filesST", String.valueOf(lastSeenTime));
		postParams.put("trashST", String.valueOf(lastTrashSeenTime));
		postParams.put("albumsST", String.valueOf(lastAlbumsSeenTime));
		postParams.put("albumFilesST", String.valueOf(lastAlbumFilesSeenTime));
		postParams.put("delST", String.valueOf(lastDelSeenTime));
		postParams.put("cntST", String.valueOf(lastContactsSeenTime));

		JSONObject resp = HttpsClient.postFunc(
				StinglePhotosApplication.getApiUrl() + context.getString(R.string.get_updates_path),
				postParams
		);
		StingleResponse response = new StingleResponse(context, resp, false);
		if(response.isLoggedOut()){
			throw new RuntimeException("Logged out");
		}
		if (response.isStatusOk()) {

			if (processFilesInSet(context, response.get("files"), SyncManager.GALLERY)) {
				needToUpdateUI = true;
			}
			if (processFilesInSet(context, response.get("trash"), SyncManager.TRASH)) {
				needToUpdateUI = true;
			}
			if (processAlbums(context, response.get("albums"))) {
				needToUpdateUI = true;
			}
			if (processFilesInSet(context, response.get("albumFiles"), SyncManager.ALBUM)) {
				needToUpdateUI = true;
			}
			if (processContacts(context, response.get("contacts"))) {
				needToUpdateUI = true;
			}
			if (processDeleteEvents(context, response.get("deletes"))) {
				needToUpdateUI = true;
			}


			String spaceUsedStr = response.get("spaceUsed");
			String spaceQuotaStr = response.get("spaceQuota");


			if (spaceUsedStr != null && spaceUsedStr.length() > 0) {
				int spaceUsed = Integer.parseInt(spaceUsedStr);
				int oldSpaceUsed = Helpers.getPreference(context, SyncManager.PREF_LAST_SPACE_USED, 0);
				if (spaceUsed != oldSpaceUsed) {
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_USED, spaceUsed);
					needToUpdateUI = true;
				}
			}

			if (spaceQuotaStr != null && spaceQuotaStr.length() > 0) {
				int spaceQuota = Integer.parseInt(spaceQuotaStr);
				int oldSpaceQuota = Helpers.getPreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, 0);
				if (spaceQuota != oldSpaceQuota) {
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, spaceQuota);
					needToUpdateUI = true;
				}
			}

		}

		return needToUpdateUI;
	}

	private boolean processFilesInSet(Context context, String filesStr, int set) throws JSONException {
		boolean result = false;
		if (filesStr != null && filesStr.length() > 0) {
			JSONArray files = new JSONArray(filesStr);
			for (int i = 0; i < files.length(); i++) {
				JSONObject file = files.optJSONObject(i);
				if (file != null) {
					StingleDbFile dbFile = new StingleDbFile(file);
					Log.d("receivedFile", set + " - " + dbFile.filename);
					processFile(context, dbFile, set);
					result = true;
				}
			}
		}
		return result;
	}

	private boolean processAlbums(Context context, String albumsStr) throws JSONException {
		boolean result = false;
		if (albumsStr != null && albumsStr.length() > 0) {
			JSONArray albums = new JSONArray(albumsStr);
			for (int i = 0; i < albums.length(); i++) {
				JSONObject album = albums.optJSONObject(i);
				if (album != null) {
					StingleDbAlbum dbAlbum = new StingleDbAlbum(album);
					Log.d("receivedAlbum", dbAlbum.albumId);
					processAlbum(dbAlbum);
					result = true;
				}
			}
		}
		return result;
	}

	private boolean processContacts(Context context, String contactsStr) throws JSONException {
		boolean result = false;
		if (contactsStr != null && contactsStr.length() > 0) {
			JSONArray contacts = new JSONArray(contactsStr);
			for (int i = 0; i < contacts.length(); i++) {
				JSONObject contact = contacts.optJSONObject(i);
				if (contact != null) {
					StingleContact dbContact = new StingleContact(contact);
					Log.d("receivedContact", dbContact.email);
					processContact(dbContact);
					result = true;
				}
			}
		}
		return result;
	}

	private boolean processDeleteEvents(Context context, String delsStr) throws JSONException {
		boolean result = false;
		if (delsStr != null && delsStr.length() > 0) {
			JSONArray deletes = new JSONArray(delsStr);
			for (int i = 0; i < deletes.length(); i++) {
				JSONObject deleteEvent = deletes.optJSONObject(i);
				if (deleteEvent != null) {
					Log.d("receivedDelete", deleteEvent.optString("file") + " - " + deleteEvent.optString("albumId"));
					processDeleteEvent(context, deleteEvent);
					result = true;
				}
			}
		}
		return result;
	}

	private boolean processFile(Context context, StingleDbFile remoteFile, int set) {

		FilesDb myDb;
		if (set == SyncManager.GALLERY) {
			myDb = galleryDb;
		} else if (set == SyncManager.TRASH) {
			myDb = trashDb;
		} else if (set == SyncManager.ALBUM) {
			myDb = albumFilesDb;
		} else {
			return false;
		}

		StingleDbFile file = myDb.getFileIfExists(remoteFile.filename, remoteFile.albumId);

		File dir = new File(FileManager.getHomeDir(context));
		File fsFile = new File(dir.getPath() + "/" + remoteFile.filename);

		remoteFile.isLocal = false;
		if (fsFile.exists()) {
			remoteFile.isLocal = true;
		}

		if (file == null) {
			remoteFile.isRemote = true;
			myDb.insertFile(remoteFile);
		} else {
			boolean needUpdate = false;
			boolean needDownload = false;
			if (file.dateModified != remoteFile.dateModified) {
				file.dateModified = remoteFile.dateModified;
				needUpdate = true;
			}
			if (!file.isRemote) {
				file.isRemote = true;
				needUpdate = true;
			}
			if (file.isLocal != remoteFile.isLocal) {
				file.isLocal = remoteFile.isLocal;
				needUpdate = true;
			}
			if (file.version < remoteFile.version) {
				file.version = remoteFile.version;
				needUpdate = true;
				needDownload = true;
			}
			if (needUpdate) {
				myDb.updateFile(file);
			}
			if (needDownload) {
				String homeDir = FileManager.getHomeDir(context);
				String thumbDir = FileManager.getThumbsDir(context);
				String mainFilePath = homeDir + "/" + file.filename;
				String thumbPath = thumbDir + "/" + file.filename;

				try {
					SyncManager.downloadFile(context, file.filename, mainFilePath, false, set, null);
					SyncManager.downloadFile(context, file.filename, thumbPath, true, set, null);
				} catch (NoSuchAlgorithmException | IOException | KeyManagementException e) {
					e.printStackTrace();
				}
			}
		}

		moveForwardFileSeenTime(remoteFile, set);

		return true;
	}

	private void moveForwardFileSeenTime(StingleDbFile remoteFile, int set) {
		if (set == SyncManager.GALLERY) {
			if (remoteFile.dateModified > lastSeenTime) {
				lastSeenTime = remoteFile.dateModified;
			}
		} else if (set == SyncManager.TRASH) {
			if (remoteFile.dateModified > lastTrashSeenTime) {
				lastTrashSeenTime = remoteFile.dateModified;
			}
		} else if (set == SyncManager.ALBUM) {
			if (remoteFile.dateModified > lastAlbumFilesSeenTime) {
				lastAlbumFilesSeenTime = remoteFile.dateModified;
			}
		}
	}

	private boolean processAlbum(StingleDbAlbum remoteAlbum) {

		StingleDbAlbum album = albumsDb.getAlbumById(remoteAlbum.albumId);
		if (album == null) {
			albumsDb.insertAlbum(remoteAlbum);
			if(isFirstSyncDone && remoteAlbum.isShared && !remoteAlbum.isOwner){
				showSharingNotification(remoteAlbum);
				StinglePhotosApplication.isSyncedThumbs = false;
			}
		} else {
			if (album.dateModified != remoteAlbum.dateModified) {
				albumsDb.updateAlbum(remoteAlbum);
			}
		}

		moveForwardAlbumSeenTime(remoteAlbum);

		return true;
	}

	private boolean processContact(StingleContact remoteContact) {
		StingleContact contact = contactsDb.getContactByUserId(remoteContact.userId);

		if (contact == null) {
			contactsDb.insertContact(remoteContact);
		} else {
			boolean needUpdate = false;
			if (contact.dateUsed != remoteContact.dateUsed) {
				contact.dateUsed = remoteContact.dateUsed;
				needUpdate = true;
			}
			if (contact.dateModified != remoteContact.dateModified) {
				contact.dateModified = remoteContact.dateModified;
				needUpdate = true;
			}
			if (!contact.email.equals(remoteContact.email)) {
				contact.email = remoteContact.email;
				needUpdate = true;
			}

			if (needUpdate) {
				contactsDb.updateContact(contact);
			}
		}

		if (remoteContact.dateModified > lastContactsSeenTime) {
			lastContactsSeenTime = remoteContact.dateModified;
		}

		return true;
	}

	private void moveForwardAlbumSeenTime(StingleDbAlbum remoteAlbum) {
		if (remoteAlbum.dateModified > lastAlbumsSeenTime) {
			lastAlbumsSeenTime = remoteAlbum.dateModified;
		}
	}

	protected void processDeleteEvent(Context context, JSONObject event) throws JSONException {

		String filename = event.getString("file");
		String albumId = event.optString("albumId");
		Integer type = event.getInt("type");
		Long date = event.getLong("date");

		if (type == SyncManager.DELETE_EVENT_MAIN) {
			StingleDbFile file = galleryDb.getFileIfExists(filename);
			if (file != null && file.dateModified < date) {
				galleryDb.deleteFile(file.filename);
			}
		} else if (type == SyncManager.DELETE_EVENT_TRASH) {
			StingleDbFile file = trashDb.getFileIfExists(filename);
			if (file != null && file.dateModified < date) {
				trashDb.deleteFile(file.filename);

			}
		} else if (type == SyncManager.DELETE_EVENT_ALBUM) {
			StingleDbAlbum album = albumsDb.getAlbumById(albumId);
			if (album != null && album.dateModified < date) {
				albumFilesDb.deleteAlbumFilesIfNotNeeded(context, albumId);
				albumFilesDb.deleteAllFilesInAlbum(albumId);
				albumsDb.deleteAlbum(albumId);
			}
		} else if (type == SyncManager.DELETE_EVENT_ALBUM_FILE) {
			StingleDbFile file = albumFilesDb.getFileIfExists(filename);
			if (file != null && file.dateModified < date) {
				albumFilesDb.deleteAlbumFile(file.filename, albumId);
			}
		} else if (type == SyncManager.DELETE_EVENT_DELETE) {
			StingleDbFile file = trashDb.getFileIfExists(filename);
			if (file != null && file.dateModified < date) {
				trashDb.deleteFile(file.filename);

				deleteFileIfNotUsed(context, file.filename);
			}
		} else if (type == SyncManager.DELETE_EVENT_CONTACT) {
			contactsDb.deleteContact(Long.parseLong(filename));
		}

		if (date > lastDelSeenTime) {
			lastDelSeenTime = date;
		}
	}


	private void deleteFileIfNotUsed(Context context, String filename){
		boolean needToDeleteFile = true;

		if (galleryDb.getFileIfExists(filename) != null || albumFilesDb.getFileIfExists(filename) != null) {
			needToDeleteFile = false;
		}

		if (needToDeleteFile) {
			FileManager.deleteLocalFile(context, filename);
		}
	}

	private void showSharingNotification(StingleDbAlbum album) {
		NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification.Builder notificationBuilder;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String NOTIFICATION_CHANNEL_ID = "org.stingle.photos.sharing";
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.sharing_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
			chan.setLightColor(context.getColor(R.color.primaryLightColor));
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			chan.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
			assert manager != null;
			manager.createNotificationChannel(chan);
			notificationBuilder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
		} else {
			notificationBuilder = new Notification.Builder(context);
		}

		Intent intent = new Intent(context, GalleryActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		intent.putExtra("set", SyncManager.ALBUM);
		intent.putExtra("view", AlbumsFragment.VIEW_SHARES);
		intent.putExtra("albumId", album.albumId);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		String albumName = GalleryHelpers.getAlbumName(album);
		String message = context.getString(R.string.new_shared_album, albumName);

		Notification notification = notificationBuilder
				.setSmallIcon(R.drawable.ic_sp)  // the status icon
				.setContentTitle(message)
				.setContentText(context.getString(R.string.tap_to_view))
				.setWhen(System.currentTimeMillis())  // the time stamp
				.setPriority(Notification.PRIORITY_DEFAULT)
				.setContentIntent(contentIntent)  // The intent to send when the entry is clicked
				.setAutoCancel(true)
				.build();

		assert manager != null;
		int oneTimeID = (int) SystemClock.uptimeMillis();
		manager.notify(oneTimeID, notification);
	}
}
