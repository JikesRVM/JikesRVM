/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.scheduler.RVMThread;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.util.StringUtilities;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.pragma.Unpreemptible;

/**
 * Interface to filesystem of underlying operating system.  Historically
 * this has provided a blocking IO abstraction on top of non-blocking IO,
 * which was necessary for green threads.  The current code contains only
 * abstractions for dealing with things like file status.
 */
public class FileSystem {

  // options for open()
  public static final int OPEN_READ = 0; // open for read/only  access
  public static final int OPEN_WRITE = 1; // open for read/write access, create if doesn't already exist,
  // truncate if already exists
  public static final int OPEN_MODIFY = 2; // open for read/write access, create if doesn't already exist
  public static final int OPEN_APPEND = 3; // open for read/write access, create if doesn't already exist, append writes

  // options for seek()
  public static final int SEEK_SET = 0;    // set i/o position to start of file plus "offset"
  public static final int SEEK_CUR = 1;    // set i/o position to current position plus "offset"
  public static final int SEEK_END = 2;    // set i/o position to end of file plus "offset"

  // options for stat()
  public static final int STAT_EXISTS = 0;
  public static final int STAT_IS_FILE = 1;
  public static final int STAT_IS_DIRECTORY = 2;
  public static final int STAT_IS_READABLE = 3;
  public static final int STAT_IS_WRITABLE = 4;
  public static final int STAT_LAST_MODIFIED = 5;
  public static final int STAT_LENGTH = 6;

  // options for access()
  public static final int ACCESS_F_OK = 00;
  public static final int ACCESS_R_OK = 04;
  public static final int ACCESS_W_OK = 02;
  public static final int ACCESS_X_OK = 01;

  /**
   * Get file status.
   * @param fileName file name
   * @param kind     kind of info desired (one of STAT_XXX, above)
   * @return desired info (-1 -> error)
   *    The boolean ones return 0 in case of non-true, 1 in case of
   *    true status.
   */
  public static int stat(String fileName, int kind) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    byte[] asciiName = StringUtilities.stringToBytesNullTerminated(fileName);
    int rc = sysCall.sysStat(asciiName, kind);
    if (VM.TraceFileSystem) VM.sysWrite("FileSystem.stat: name=" + fileName + " kind=" + kind + " rc=" + rc + "\n");
    return rc;
  }

  /**
   * Get user's perms for a file.
   * @param fileName file name
   * @param kind     kind of access perm(s) to check for (ACCESS_W_OK,...)
   * @return 0 if access ok (-1 -> error)
   */
  public static int access(String fileName, int kind) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    byte[] asciiName = StringUtilities.stringToBytesNullTerminated(fileName);

    int rc = sysCall.sysAccess(asciiName, kind);

    if (VM.TraceFileSystem) {
      VM.sysWrite("FileSystem.access: name=" + fileName + " kind=" + kind + " rc=" + rc + "\n");
    }
    return rc;
  }

  /**
   * Read single byte from file.
   *
   * @param fd file descriptor
   * @return byte that was read (< -2: i/o error, -2: timeout, -1: eof, >= 0: data)
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  public static int readByte(int fd) {
    RVMThread.saveThreadState();
    RVMThread.enterNative();
    int result=sysCall.sysReadByte(fd);
    RVMThread.leaveNative();
    return result;
  }

  /**
   * Write single byte to file
   *
   * @param fd file descriptor
   * @param b  byte to be written
   * @return  -2: i/o error, -1: timeout, 0: ok
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  public static int writeByte(int fd, int b) {
    RVMThread.saveThreadState();
    RVMThread.enterNative();
    int result=sysCall.sysWriteByte(fd,b);
    RVMThread.leaveNative();
    return result;
  }

  /**
   * Read multiple bytes.
   *
   * @param buf a pinned byte array to read into
   * @return -2: i/o error, -1: timeout, >=0: number of bytes read
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  public static int readBytes(int fd, byte[] buf, int off, int cnt) {
    RVMThread.saveThreadState();
    RVMThread.enterNative();
    int result=sysCall.sysReadBytes(fd,Magic.objectAsAddress(buf).plus(off),cnt);
    RVMThread.leaveNative();
    return result;
  }

  /**
   * Write multiple bytes.
   *
   * @param buf a pinned byte array to write from
   * @return -2: i/o error, -1: timeout, >=0: number of bytes written
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  public static int writeBytes(int fd, byte[] buf, int off, int cnt) {
    RVMThread.saveThreadState();
    RVMThread.enterNative();
    int result=sysCall.sysWriteBytes(fd,Magic.objectAsAddress(buf).plus(off),cnt);
    RVMThread.leaveNative();
    return result;
  }

  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  public static boolean sync(int fd) {
    RVMThread.saveThreadState();
    RVMThread.enterNative();
    boolean result=sysCall.sysSyncFile(fd) == 0;
    RVMThread.leaveNative();
    return result;
  }

  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  public static int bytesAvailable(int fd) {
    RVMThread.saveThreadState();
    RVMThread.enterNative();
    int result=sysCall.sysBytesAvailable(fd);
    RVMThread.leaveNative();
    return result;
  }

  // not sure if this is the right place to have this.
  /**
   * Called from VM.boot to set up java.lang.System.in, java.lang.System.out,
   * and java.lang.System.err
   */
  public static void initializeStandardStreams() {
    FileInputStream fdIn = new FileInputStream(FileDescriptor.in);
    FileOutputStream fdOut = new FileOutputStream(FileDescriptor.out);
    FileOutputStream fdErr = new FileOutputStream(FileDescriptor.err);
    System.setIn(new BufferedInputStream(fdIn));
    System.setOut(new PrintStream(new BufferedOutputStream(fdOut, 128), true));
    System.setErr(new PrintStream(new BufferedOutputStream(fdErr, 128), true));
    Callbacks.addExitMonitor(new Callbacks.ExitMonitor() {
      public void notifyExit(int value) {
        try {
          System.err.flush();
          System.out.flush();
        } catch (Throwable e) {
          VM.sysWriteln("vm: error flushing stdout, stderr");
        }
      }
    });
  }
}


