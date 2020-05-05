package org.stingle.photos.Db.Objects;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Sharing.SharingPermissions;
import org.stingle.photos.Util.Helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class StingleDbAlbum {
	public static String MEMBERS_SEPARATOR = ",";

	public String albumId;
	public String encPrivateKey;
	public String publicKey;
	public String metadata;
	public Boolean isShared = false;
	public Boolean isHidden = false;
	public Boolean isOwner = true;
	public String permissions = null;
	public ArrayList<String> members = new ArrayList<>();
	public Boolean isLocked = false;
	public String cover = null;
	public Long dateCreated;
	public Long dateModified;

	public SharingPermissions permissionsObj = null;

	public StingleDbAlbum(){
		this.albumId = CryptoHelpers.getRandomString(AlbumsDb.ALBUM_ID_LEN);
	}

	public StingleDbAlbum(Cursor cursor){
		this.albumId = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID));
		this.encPrivateKey = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_ALBUM_SK));
		this.publicKey = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_ALBUM_PK));
		this.metadata = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_METADATA));
		this.isShared = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_IS_SHARED)) == 1);
		this.isHidden = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_IS_HIDDEN)) == 1);
		this.isOwner = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_IS_OWNER)) == 1);
		this.permissions = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_PERMISSIONS));
		this.isLocked = (cursor.getInt(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_IS_LOCKED)) == 1);
		this.cover = cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_COVER));
		this.dateCreated = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED));
		this.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED));

		setMembers(cursor.getString(cursor.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_MEMBERS)));
		initPermissionsObj();
	}

	public StingleDbAlbum(JSONObject json) throws JSONException {
		this.albumId = json.getString("albumId");
		this.encPrivateKey = json.getString("encPrivateKey");
		this.publicKey = json.getString("publicKey");
		this.metadata = json.getString("metadata");
		this.isShared = (json.getInt("isShared") == 1);
		this.isHidden = (json.getInt("isHidden") == 1);
		this.isOwner = (json.getInt("isOwner") == 1);
		this.permissions = json.getString("permissions");
		this.isLocked = (json.getInt("isLocked") == 1);
		this.cover = json.getString("cover");
		this.dateCreated = json.getLong("dateCreated");
		this.dateModified = json.getLong("dateModified");

		setMembers(json.getString("members"));
		initPermissionsObj();
	}

	public void setMembers(String membersStr){
		if(membersStr == null || membersStr.length() == 0 || membersStr.equals("null")){
			return;
		}
		members = new ArrayList<>(Arrays.asList(membersStr.split(MEMBERS_SEPARATOR)));
	}

	public void addMember(Long userId){
		if(!members.contains(String.valueOf(userId))){
			members.add(String.valueOf(userId));
		}
	}

	public void setMembers(ArrayList<String> members){
		this.members = members;
	}

	public String getMembersAsString(){
		return Helpers.impode(MEMBERS_SEPARATOR, members);
	}

	private void initPermissionsObj(){
		if(permissions != null && permissions.length() == SharingPermissions.PERMISSIONS_LENGTH){
			try {
				this.permissionsObj = new SharingPermissions(permissions);
			}
			catch (RuntimeException e){
				this.permissionsObj = null;
			}
		}
	}

	public String toJSON(){
		HashMap<String,String> map = new HashMap<>();
		map.put("albumId", albumId);
		map.put("encPrivateKey", encPrivateKey);
		map.put("publicKey", publicKey);
		map.put("metadata", metadata);
		map.put("isShared", (isShared ? "1" : "0"));
		map.put("isHidden", (isHidden ? "1" : "0"));
		map.put("isOwner", (isOwner ? "1" : "0"));
		map.put("permissions", permissions);
		map.put("members", getMembersAsString());
		map.put("isLocked", (isLocked ? "1" : "0"));
		map.put("cover", cover);
		map.put("dateCreated", String.valueOf(dateCreated));
		map.put("dateModified", String.valueOf(dateModified));

		return (new JSONObject(map)).toString();
	}
}
