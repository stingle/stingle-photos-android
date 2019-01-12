package com.fenritz.safecam.util;

public class CryptoProgress{
    private long total = 0;
    private long current = 0;

    public CryptoProgress(long pTotal){
        total = pTotal;
    }

    public void setProgress(long pCurrent){
        current = pCurrent;
    }

    public long getTotal(){
        return total;
    }

    public int getProgressPercents(){
        if(total == 0){
            return 0;
        }
        return (int) (current * 100 / total);
    }

    public long getProgress(){
        if(total == 0){
            return 0;
        }
        return current;
    }
}
