package org.stingle.photos.Auth;


import androidx.appcompat.app.AlertDialog;

public abstract class PasswordReturnListener {
    public abstract void passwordReceived(String password, AlertDialog dialog);
    public abstract void passwordReceiveFailed(AlertDialog dialog);
}
