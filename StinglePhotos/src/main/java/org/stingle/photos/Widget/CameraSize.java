package org.stingle.photos.Widget;


import android.content.Context;
import android.util.Size;

import org.stingle.photos.R;

public class CameraSize {

    int width;
    int height;
    float ratio;

    public CameraSize(int width, int height){
        this.width = width;
        this.height = height;
        this.ratio = (float)width/(float)height;
    }

    public Size toSize(){
        return new Size(width, height);
    }

    public String getAspectRatio(Context context){
        if(ratio == 16 / 9f){
            return "16:9";
        }
        else if(ratio == 5 / 3f){
            return "5:3";
        }
        else if(ratio == 4 / 3f){
            return "4:3";
        }
        else if(ratio == 2f){
            return "2:1";
        }
        else if(ratio == 3 / 4f){
            return "3:4";
        }
        else if(ratio == 3 / 2f){
            return "3:2";
        }
        else if(ratio == 6 / 5f){
            return "6:5";
        }
        else if(ratio == 19 / 9f){
            return "19:9";
        }
        else if(ratio == 19 / 8f){
            return "19:8";
        }
        else if(ratio == 1.9f){
            return "1.9:1";
        }
        else if(width == height){
            return "1:1";
        }
        else{
            return context.getString(R.string.other);
        }
    }


}
