/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;
import com.ibm.JikesRVM.classloader.*;
import java.io.*;

/**
 * Jikes RVM implementation of <code>java.lang.Process</code>.
 *
 * @author Julian Dolby
 * @author David Hovemeyer
 * @date May 20, 2002
 */
public class VM_Process extends java.lang.Process {

  static {
    System.loadLibrary("jpnexec");
  }

  // VM_Processor from which the child process was created.
  // Linux forces us to use the same pthread when we wait
  // for the child process to exit.
  VM_Processor creatingProcessor;

  // Child's Unix process id
  private int pid;

  // File descriptors and streams connecting VM to child process.
  private int inputDescriptor;
  private int outputDescriptor;
  private int errorDescriptor;
  private OutputStream outputStream; // connected to process's stdin
  private InputStream inputStream;   // connected to process's stdout
  private InputStream errorStream;   // connected to process's stderr

  // Data members for getting the process's exit status.
  // exitStatusLock must be held to access or modify.
  private Object exitStatusLock = new Object();
  private boolean waiting = false;
  private boolean hasExited = false;
  private int exitStatus;

  /**
   * Constructor.
   * @param program name of program being executed
   * @param args array of program arguments
   * @param env array of environment variable bindings (optional)
   * @param dirPath name of directory to use as working directory (optional)
   */
  public VM_Process(String program, String[] args, String[] env, String dirPath) {
    pid = exec4(program, args, env, dirPath);
    creatingProcessor = VM_Processor.getCurrentProcessor();
    // FIXME: check for error creating process

    // initialize file descriptors
    VM_FileSystem.onCreateFileDescriptor( inputDescriptor, false );
    VM_FileSystem.onCreateFileDescriptor( outputDescriptor, false );
    VM_FileSystem.onCreateFileDescriptor( errorDescriptor, false );
  }

  /**
   * Get the <code>VM_Processor</code> that the child process was
   * created from.
   */
  VM_Processor getCreatingProcessor() throws UninterruptiblePragma {
    return creatingProcessor;
  }

  /**
   * Get the pid of this process.
   */
  int getPid() {
    return pid;
  }
    
  /**
   * Get the exit value of the process.
   * @throws IllegalThreadStateException if the process hasn't exited yet
   */
  public int exitValue() {
    synchronized (exitStatusLock) {
      if (!hasExited) {
        // If someone else is waiting for the process to exit,
        // then it obviously hasn't exited.  Otherwise, poll to see if
        // the process has exited.
        if (waiting || !isDead())
          throw new IllegalThreadStateException("I'm not dead yet!");

        // Process is dead, and we've recorded its exit status,
        // so let anyone waiting for its status know that
        // we've got it.
        hasExited = true;
        exitStatusLock.notifyAll();
      }
      return exitStatus;
    }
  }
    
  /**
   * Wait for the process to exit.
   * @return the process's exit value
   */
  public int waitFor() throws InterruptedException {
    synchronized (exitStatusLock) {
      // Make sure no other thread is waiting
      // for the process to exit.
      while (waiting) {
        exitStatusLock.wait();
      }

      // Maybe the process has exited already?
      if (hasExited)
        return exitStatus;

      // We know that the process hasn't exited, and that no one
      // else is waiting for it.  Set waiting = true, to
      // mark the fact that WE are now doing the wait.
      waiting = true;
    }

    // exitStatusLock is now not held, so other threads can find out
    // that we're waiting without themselves being blocked,
    // i.e., threads that are calling exitValue().

    // Try to wait for the process to exit.
    // We may be interrupted.
    int code;
    try {
      code = waitForInternal();
    }
    catch (InterruptedException e) {
      // We were interrupted, so end the wait
      synchronized (exitStatusLock) {
        waiting = false;
        exitStatusLock.notifyAll();
      }
      throw e;
    }

    // Process has exited!  Broadcast the exit status.
    synchronized (exitStatusLock) {
      waiting = false;
      hasExited = true;
      exitStatus = code;
      exitStatusLock.notifyAll();
    }

    return code;
  }

  /**
   * Kill the process.
   */
  public void destroy() {
    // We only attempt to kill the process if
    // we think it's still alive.
    synchronized (exitStatusLock) {
      if (!hasExited) {
        destroyInternal();
      }
    }
  }
    
  /**
   * Get an <code>InputStream</code> to read process's stderr.
   */
  public InputStream getErrorStream() {
    if (errorStream == null) errorStream = getInputStream(errorDescriptor);

    return errorStream;
  }
    
  /**
   * Get an <code>InputStream</code> to read process's stdout.
   */
  public InputStream getInputStream() {
    if (inputStream == null) inputStream = getInputStream(outputDescriptor);

    return inputStream;
  }
    
  /**
   * Get an <code>OutputStream</code> to write process's stdin.
   */
  public OutputStream getOutputStream() {
    if (outputStream == null) outputStream = getOutputStream(inputDescriptor);

    return outputStream;
  }

  /**
   * Poll to see if process is dead.
   * <code>exitStatusLock</code> must be held.
   */
  private boolean isDead() {
    try {
      // Using a timeout of 1 second should give the
      // VM_Processor a couple chances to poll to see if the
      // process has exited.
      VM_ThreadProcessWaitData waitData = VM_Wait.processWait(this, 1.0d);
      boolean finished = waitData.finished;
      if (finished)
        exitStatus = waitData.exitStatus;
      return finished;
    }
    catch (InterruptedException e) {
      // The thread can get interrupted while
      // on the process wait queue.  However,
      // this is not a blocking call,
      // so we don't want to actually throw an exception.
      java.lang.Thread.currentThread().interrupt();
      return false;
    }
  }

  /**
   * Blocking wait to see if process is dead.
   * Returns exit status.  Should be called with
   * <code>exitStatusLock</code> NOT held, but <code>waiting</code>
   * set to true so no other thread tries to wait
   * at the same time.  (Unix semantics require us to
   * do only a single <code>waitpid()</code> for the same process).
   */
  private int waitForInternal() throws InterruptedException {
    VM_ThreadProcessWaitData waitData =
      VM_Wait.processWait(this, VM_ThreadEventConstants.WAIT_INFINITE);
    // InterruptedException may be thrown,
    // in which case we definitely did NOT do the waitpid()

    if (VM.VerifyAssertions) VM._assert(waitData.finished);
    return waitData.exitStatus;
  }
    
  /**
   * Call fork() and execvp() to spawn the process.
   * @return the process id
   */
  private native int exec4(String program, String[] args, String[] env, String dirPath);
    
  /**
   * Send the process a kill signal.
   */
  private native void destroyInternal();


  private InputStream getInputStream(final int fd) {
    return new InputStream() {
        public int available() throws IOException {
          return VM_FileSystem.bytesAvailable( fd );
        }
        
        public void close() throws IOException {
            // stream closed implicitly when process exits
        }
              
        public int read() throws IOException {
          return VM_FileSystem.readByte( fd );
        }
              
        public int read(byte[] buffer) throws IOException {
          return VM_FileSystem.readBytes(fd, buffer, 0, buffer.length);
        }
              
        public int read(byte[] buf, int off, int len) throws IOException {
          return VM_FileSystem.readBytes(fd, buf, off, len);
        }
      };
  }
              
  private OutputStream getOutputStream(final int fd) {
    return new OutputStream() {
        public void write (int b) throws IOException {
          VM_FileSystem.writeByte(fd, b);
        }

        public void write (byte[] b) throws IOException {
          VM_FileSystem.writeBytes(fd, b, 0, b.length);
        }

        public void write (byte[] b, int off, int len) throws IOException {
          VM_FileSystem.writeBytes(fd, b, off, len);
        }

        public void flush () throws IOException {
          VM_FileSystem.sync( fd );
        }

        public void close () throws IOException {
            // stream closed implicitly when process exits
        }
      };
  }
}

