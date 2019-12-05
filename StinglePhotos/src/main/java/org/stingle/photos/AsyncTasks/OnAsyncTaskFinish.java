package org.stingle.photos.AsyncTasks;

import java.io.File;
import java.util.ArrayList;

public abstract class OnAsyncTaskFinish {
	public void onFinish(){}
	public void onFinish(ArrayList<File> files){
		onFinish();
	}
	public void onFinish(Integer result){
		onFinish();
	}
	public void onFinish(Object object){ onFinish(); };
}
