/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Elias Naur 2006
 *
 * $Id: /jikesrvm/local/testing/tests/nio/src/TestFileChannel.java 10522 2006-11-14T22:42:56.816831Z dgrove-oss  $
 */
package test.org.jikesrvm.basic.java.nio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

/**
 * @author Elias Naur
 */
public class TestFileChannel {
  private static final int MAGIC_INT = 0xdeadbeef;

  public static void main(String[] args) {
    File file = null;
    try {
      file = File.createTempFile("TestFileChannel", ".dat");
      final ByteBuffer buffer = ByteBuffer.allocateDirect(4);
      final ByteChannel output = new FileOutputStream(file).getChannel();
      buffer.putInt(MAGIC_INT);
      buffer.flip();
      output.write(buffer);
      output.close();

      final ByteChannel input = new FileInputStream(file).getChannel();
      buffer.clear();
      while (buffer.hasRemaining()) { input.read(buffer); }
      input.close();
      buffer.flip();
      final int file_int = buffer.getInt();
      if (file_int != MAGIC_INT) {
        System.out.println("TestFileChannel FAILURE");
        System.out.println("Wrote " + Integer.toHexString(MAGIC_INT) + " but read " + Integer.toHexString(file_int));
      } else {
        System.out.println("TestFileChannel SUCCESS");
      }
    } catch (Exception e) {
      System.out.println("TestFileChannel FAILURE");
      e.printStackTrace(System.out);
    }
    finally {
      if (null != file) {
        file.delete();
      }
    }
  }
}
