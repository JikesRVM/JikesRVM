/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * @author Julian Dolby
 * @date May 20, 2002
 */

import java.io.*;

public class VM_Process extends java.lang.Process {

    static {
	System.loadLibrary("jpnexec");
    }

    private int pid;
    private int status;
    private int inputDescriptor;
    private int outputDescriptor;
    private int errorDescriptor;
    
    private native boolean isDead();
    
    private native int exitValueInternal();
    
    private native int waitForInternal();
    
    private native int exec2(String program, String[] args);
    
    private native int exec3(String program, String[] args, String[] env);
    
    public VM_Process(String program, String[] args) {
	pid = exec2(program, args);
    }

    public VM_Process(String program, String[] args, String[] env) {
	pid = exec3(program, args, env);
    }
    
    public native void destroy();
    
    public int exitValue() {
	if (isDead())
	    return exitValueInternal();
	else
	    throw new IllegalThreadStateException("I'm not dead yet!");
    }
    
    public InputStream getErrorStream() {
	return new FileInputStream( new FileDescriptor(errorDescriptor) );
    }
    
    public InputStream getInputStream() {
	return new FileInputStream( new FileDescriptor(outputDescriptor) );
    }
    
    public OutputStream getOutputStream() {
	return new FileOutputStream( new FileDescriptor(inputDescriptor) );
    }
    
    public int waitFor() throws InterruptedException {
	return waitForInternal();
    }
}

