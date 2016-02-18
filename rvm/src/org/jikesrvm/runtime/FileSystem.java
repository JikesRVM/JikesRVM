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
import org.jikesrvm.scheduler.RVMThread;
import static org.jikesrvm.runtime.SysCall.sysCall;
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

  /**
   * Read single byte from file.
   *
   * @param fd file descriptor
   * @return byte that was read (&lt; -2: i/o error, -2: timeout, -1: eof, &gt;= 0: data)
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  public static int readByte(int fd) {
    RVMThread.saveThreadState();
    RVMThread.enterNative();
    int result = sysCall.sysReadByte(fd);
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
    int result = sysCall.sysWriteByte(fd,b);
    RVMThread.leaveNative();
    return result;
  }

  /**
   * Reads multiple bytes.
   *
   * @param fd the file descriptor for the file that should be read from
   * @param buf a pinned byte array to read into
   * @param off the offset in the buffer to read into
   * @param cnt the number of bytes to read
   * @return -2: i/o error, -1: timeout, &gt;=0: number of bytes read
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  public static int readBytes(int fd, byte[] buf, int off, int cnt) {
    RVMThread.saveThreadState();
    RVMThread.enterNative();
    int result = sysCall.sysReadBytes(fd,Magic.objectAsAddress(buf).plus(off),cnt);
    RVMThread.leaveNative();
    return result;
  }

  /**
   * Writes multiple bytes.
   *
   * @param fd the file descriptor for the file that should be written to
   * @param buf a pinned byte array to write from
   * @param off the offset in the buffer to start writing from
   * @param cnt the number of bytes to write
   * @return -2: i/o error, -1: timeout, &gt;=0: number of bytes written
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  public static int writeBytes(int fd, byte[] buf, int off, int cnt) {
    RVMThread.saveThreadState();
    RVMThread.enterNative();
    int result = sysCall.sysWriteBytes(fd,Magic.objectAsAddress(buf).plus(off),cnt);
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
      @Override
      public void notifyExit(int value) {
        try {
          System.err.flush();
          System.out.flush();
        } catch (Throwable e) {
          VM.sysWriteln("vm: error flushing stdout, stderr");
          e.printStackTrace();
        }
      }
    });
  }
}


