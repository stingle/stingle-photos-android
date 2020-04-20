package org.stingle.photos.Sharing;

public class SharingPermissions {
	public static int PERMISSIONS_VERSION = 1;

	public boolean allowEditing = true;
	public boolean allowResharing = true;
	public boolean allowCopying = true;

	public SharingPermissions(){

	}

	public SharingPermissions(String str){
		StringBuffer buf = new StringBuffer(str);
		String version = buf.substring(0,1);

		if(PERMISSIONS_VERSION != Integer.parseInt(version)){
			throw new RuntimeException("Invalid permissions version");
		}

		String editing = buf.substring(1,1);
		allowEditing = editing.equals("1");

		String resharing = buf.substring(2,1);
		allowResharing = resharing.equals("1");

		String copying = buf.substring(3,1);
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
