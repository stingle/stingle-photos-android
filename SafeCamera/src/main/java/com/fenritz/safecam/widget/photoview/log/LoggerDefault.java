/*******************************************************************************
 * Copyright 2011, 2012 Chris Banes.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.fenritz.safecam.Widget.photoview.log;

import android.util.Log;

/**
 * Helper class to redirect {@link LogManager#logger} to {@link Log}
 */
public class LoggerDefault implements Logger {

    public int v(String tag, String msg) {
        return Log.v(tag, msg);
    }

    public int v(String tag, String msg, Throwable tr) {
        return Log.v(tag, msg, tr);
    }

    public int d(String tag, String msg) {
        return Log.d(tag, msg);
    }

    public int d(String tag, String msg, Throwable tr) {
        return Log.d(tag, msg, tr);
    }

    public int i(String tag, String msg) {
        return Log.i(tag, msg);
    }

    public int i(String tag, String msg, Throwable tr) {
        return Log.i(tag, msg, tr);
    }

    public int w(String tag, String msg) {
        return Log.w(tag, msg);
    }

    public int w(String tag, String msg, Throwable tr) {
        return Log.w(tag, msg, tr);
    }

    public int e(String tag, String msg) {
        return Log.e(tag, msg);
    }

    public int e(String tag, String msg, Throwable tr) {
        return Log.e(tag, msg, tr);
    }


}
