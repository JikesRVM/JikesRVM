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
package test.org.jikesrvm.basic.bugs;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

/**
 * [ 1722506 ] dacapo eclipse fails EOF exceptions
 * http://sourceforge.net/tracker/index.php?func=detail&aid=1722506&group_id=128805&atid=712768
 */
public class R1722506 {
  public static void main(String[] args) {
    String filename = System.getProperty("java.io.tmpdir")+"/filetest.tmp";
    try {
      File newIndexFile = new File(filename);
      DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(newIndexFile, false), 2048));
      for (int i = 0; i < 50; i++)
        stream.writeInt(i);
      stream.close();
      long streamLen = (new File(filename)).length();
      RandomAccessFile raFile = new RandomAccessFile(filename, "rw");
      System.out.println(streamLen == (new File(filename)).length());
      raFile.close();
    } catch (Exception e) {
      System.err.println("Caught exception: "+e);
    } finally {
      (new File(filename)).delete();
    }
  }
}
