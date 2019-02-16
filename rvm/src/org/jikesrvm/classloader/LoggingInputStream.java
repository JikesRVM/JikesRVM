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
package org.jikesrvm.classloader;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Provides an implementation of a stream that can log bytes that are read.
 * <p>
 * The read bytes are logged to an internal buffer.
 */
public class LoggingInputStream extends FilterInputStream {

  private ByteArrayOutputStream out;
  private boolean loggingEnabled;

  protected LoggingInputStream(InputStream in) {
    super(in);
    this.out = new ByteArrayOutputStream();
  }

  @Override
  public int read() throws IOException {
    int read = super.read();
    if (loggingEnabled && read != -1) {
      out.write((byte) read);
    }
    return read;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int readBytes = super.read(b, off, len);
    if (loggingEnabled && readBytes > 0) {
      out.write(b, off, readBytes);
    }
    return readBytes;
  }

  public void startLogging() {
    this.loggingEnabled = true;
  }

  public void stopLogging() {
    this.loggingEnabled = false;
  }

  public byte[] getLoggedBytes() {
    return out.toByteArray();
  }

  public void clearLoggedBytes() {
    out = new ByteArrayOutputStream();
  }

}
