/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.swordfish.libretrodroid;

class LibretroDroid {

     static {
         System.loadLibrary("libretrodroid");
     }

    public static final int MOTION_SOURCE_DPAD = 0;
    public static final int MOTION_SOURCE_ANALOG_LEFT = 1;
    public static final int MOTION_SOURCE_ANALOG_RIGHT = 2;

    public static final int SHADER_DEFAULT = 0;
    public static final int SHADER_CRT = 1;
    public static final int SHADER_LCD = 2;

    public static native void create(String coreFilePath, String gameFilePath, String systemDir, String savesDir, int shaderType);
    public static native void resume();

    public static native void onSurfaceCreated();
    public static native void onSurfaceChanged(int width, int height);

    public static native void pause();
    public static native void destroy();

    public static native void step();

    public static native void reset();

    public static native byte[] serialize();
    public static native boolean unserialize(byte[] state);

    public static native void onMotionEvent(int port, int motionSource, float xAxis, float yAxis);
    public static native void onKeyEvent(int port, int action, int keyCode);
}
