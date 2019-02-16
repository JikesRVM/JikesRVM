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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.junit.Test;

public class LoggingInputStreamTest {

  private static final String ENCODING = "UTF-8";
  private static final int BUFFER_SIZE = 1024;

  private static final String STRING_DATA = "Test";

  @Test
  public void loggingInputStreamCanLogReads() throws Exception {
    LoggingInputStream lis = createLoggingInputStream();
    lis.startLogging();
    int read = lis.read();
    while (read != -1) {
      read = lis.read();
    }
    lis.stopLogging();
    lis.close();
    byte[] loggedBytes = lis.getLoggedBytes();
    String loggedString = createString(loggedBytes);
    assertThat(loggedString, is(STRING_DATA));
  }

  @Test
  public void loggingInputStreamCanLogReadsForByteArraysWithoutAdditionalParametesrr() throws Exception {
    LoggingInputStream lis = createLoggingInputStream();
    lis.startLogging();
    byte[] readBuffer = createBuffer();
    int readCount = lis.read(readBuffer);
    while (readCount > 0) {
      readCount = lis.read(readBuffer);
    }
    lis.stopLogging();
    lis.close();
    byte[] loggedBytes = lis.getLoggedBytes();
    String loggedString = createString(loggedBytes);
    assertThat(loggedString, is(STRING_DATA));
  }

  @Test
  public void loggingInputStreamCanLogReadsForByteArraysWithOffAndLen() throws Exception {
    LoggingInputStream lis = createLoggingInputStream();
    lis.startLogging();
    byte[] readBuffer = createBuffer();
    int readCount = lis.read(readBuffer, 0, readBuffer.length);
    while (readCount > 0) {
      readCount = lis.read(readBuffer, 0, readBuffer.length);
    }
    lis.stopLogging();
    lis.close();
    byte[] loggedBytes = lis.getLoggedBytes();
    String loggedString = createString(loggedBytes);
    assertThat(loggedString, is(STRING_DATA));
  }

  @Test
  public void noDataIsLoggedForByteReadsWhenLoggingIsDisabled() throws Exception {
    LoggingInputStream lis = createLoggingInputStream();
    int read = lis.read();
    while (read != -1) {
      read = lis.read();
    }
    lis.close();
    byte[] loggedBytes = lis.getLoggedBytes();
    assertThat(loggedBytes.length, is(0));
  }

  @Test
  public void noDataIsLoggedForBulkReadsWhenLoggingIsDisabled() throws Exception {
    LoggingInputStream lis = createLoggingInputStream();
    byte[] readBuffer = new byte[BUFFER_SIZE];
    int readCount = lis.read(readBuffer);
    while (readCount > 0) {
      readCount = lis.read(readBuffer);
    }
    lis.close();
    byte[] loggedBytes = lis.getLoggedBytes();
    assertThat(loggedBytes.length, is(0));
  }

  @Test
  public void noDataIsLoggedForBulkReadsWithOffAndLenWhenLoggingIsDisabled() throws Exception {
    LoggingInputStream lis = createLoggingInputStream();
    byte[] readBuffer = new byte[BUFFER_SIZE];
    int readCount = lis.read(readBuffer, 0, readBuffer.length);
    while (readCount > 0) {
      readCount = lis.read(readBuffer, 0, readBuffer.length);
    }
    lis.close();
    byte[] loggedBytes = lis.getLoggedBytes();
    assertThat(loggedBytes.length, is(0));
  }

  @Test
  public void bytesAreReadCorrectly() throws Exception {
    byte[] buf = { Byte.MIN_VALUE, Byte.MAX_VALUE, (byte) -1, 0, (byte) 1};
    ByteArrayInputStream bis = new ByteArrayInputStream(buf);
    LoggingInputStream lis = new LoggingInputStream(bis);
    lis.startLogging();
    int read = lis.read();
    while (read != -1) {
      read = lis.read();
    }
    lis.stopLogging();
    lis.close();
    byte[] loggedBytes = lis.getLoggedBytes();
    assertThat(Arrays.equals(loggedBytes, buf), is(true));
  }

  @Test
  public void loggingDataCanBeReset() throws Exception {
    byte[] buf = { Byte.MIN_VALUE, Byte.MAX_VALUE, 1 };
    ByteArrayInputStream bis = new ByteArrayInputStream(buf);
    LoggingInputStream lis = new LoggingInputStream(bis);
    lis.startLogging();
    lis.read();
    lis.read();
    lis.clearLoggedBytes();
    lis.read();
    lis.stopLogging();
    lis.close();
    byte[] loggedBytes = lis.getLoggedBytes();
    byte[] expected = { 1 };
    assertThat(Arrays.equals(loggedBytes, expected), is(true));
  }

  private LoggingInputStream createLoggingInputStream()
      throws UnsupportedEncodingException {
    byte[] buf = STRING_DATA.getBytes(ENCODING);
    ByteArrayInputStream bis = new ByteArrayInputStream(buf);
    LoggingInputStream lis = new LoggingInputStream(bis);
    return lis;
  }

  private String createString(byte[] loggedBytes)
      throws UnsupportedEncodingException {
    return new String(loggedBytes, ENCODING);
  }

  private byte[] createBuffer() {
    return new byte[BUFFER_SIZE];
  }

}
