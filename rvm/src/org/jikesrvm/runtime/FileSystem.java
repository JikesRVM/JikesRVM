/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
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
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.util.StringUtilities;

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
   * FIXME: should throw an IOException to indicate an error?
   *
   * @param fd file descriptor
   * @return byte that was read (< -1: i/o error, -1: eof, >= 0: data)
   */
  public static int readByte(int fd) {
    int b = sysCall.sysReadByte(fd);
    if (b >= -1) {
      // Either a valid read, or we reached EOF
      return b;
    } else {
      // Read returned with a genuine error
      return -2;
    }
  }

  // PNT: not sure if this is the right place to have this.
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


