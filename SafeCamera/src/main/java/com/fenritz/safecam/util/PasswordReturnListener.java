package com.fenritz.safecam.util;

public abstract class PasswordReturnListener {
    public abstract void passwordReceived(String password);
    public abstract void passwordCanceled();
}
