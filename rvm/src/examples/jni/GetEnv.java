/*
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
