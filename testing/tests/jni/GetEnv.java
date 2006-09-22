/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 * $Id$
 */

/* Test the JavaVM and GetEnv JNI functionality 
 * 
 * @author Julian Dolby
 */
class GetEnv {

    private static void javaCall() {
        System.err.println("called into Java");
    }

    private static native void nativeCall();

    public static void main(String args[]) {    
        System.err.println("starting");

        System.loadLibrary("getenv");
        System.err.println("loaded libgetenv");

        nativeCall();
        System.err.println("nativeCall completed");

    }

}
