package com.fenritz.safecam.Auth;

public abstract class PasswordReturnListener {
    public abstract void passwordReceived(String password);
    public abstract void passwordCanceled();
}
