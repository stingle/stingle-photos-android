package com.fenritz.safecam.Util;

import android.os.AsyncTask;


public abstract class LimitedThreadAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
    private static final int CORE_POOL_SIZE = 1;
    private static final int MAXIMUM_POOL_SIZE = 3;
    private static final int KEEP_ALIVE = 1;
}
