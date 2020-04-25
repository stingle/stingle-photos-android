package org.stingle.photos.Sharing;

public class SharingPermissions {
	public static int PERMISSIONS_VERSION = 1;
	public static int PERMISSIONS_LENGTH = 4;


	public boolean allowAdd = true;
	public boolean allowShare = true;
	public boolean allowCopy = true;

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

		String addFlag = str.substring(1,2);
		allowAdd = addFlag.equals("1");

		String shareFlag = str.substring(2,3);
		allowShare = shareFlag.equals("1");

		String copyFlag = str.substring(3,4);
		allowCopy = copyFlag.equals("1");
	}

	public String toString(){
		StringBuilder str = new StringBuilder();
		str.append(PERMISSIONS_VERSION);
		str.append((allowAdd ? "1" : "0"));
		str.append((allowShare ? "1" : "0"));
		str.append((allowCopy ? "1" : "0"));

		return str.toString();
	}
}
