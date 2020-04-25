package org.stingle.photos.Sharing;

public class SharingPermissions {
	public static int PERMISSIONS_VERSION = 1;
	public static int PERMISSIONS_LENGTH = 4;


	public static int PERM_ALLOW_EDITING = 0;
	public static int PERM_ALLOW_RESHARING = 1;
	public static int PERM_ALLOW_COPYING = 2;

	public boolean allowEditing = true;
	public boolean allowResharing = true;
	public boolean allowCopying = true;

	public SharingPermissions(){

	}

	public SharingPermissions(String str){
		if(str.length() != PERMISSIONS_LENGTH){
			return;
		}

		String version = str.substring(0,1);

		if(!version.equals(String.valueOf(PERMISSIONS_VERSION))){
			throw new RuntimeException("Invalid permissions version");
		}

		String editing = str.substring(1,2);
		allowEditing = editing.equals("1");

		String resharing = str.substring(2,3);
		allowResharing = resharing.equals("1");

		String copying = str.substring(3,4);
		allowCopying = copying.equals("1");
	}

	public String toString(){
		StringBuilder str = new StringBuilder();
		str.append(PERMISSIONS_VERSION);
		str.append((allowEditing ? "1" : "0"));
		str.append((allowResharing ? "1" : "0"));
		str.append((allowCopying ? "1" : "0"));

		return str.toString();
	}
}
