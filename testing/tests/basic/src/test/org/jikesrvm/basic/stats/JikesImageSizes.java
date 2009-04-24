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
package test.org.jikesrvm.basic.stats;

import java.io.File;

/**
 * This "test" just aids in extracting and displaying image sizes so that they can be tracked through the testing framework.
 */
public class JikesImageSizes {

  public static void main(String[] args) {
    if(args.length != 3) {
      System.err.println("Expect 3 arguments. <RVM.file.image> <RVM.data.image> <RVM.rmap.image>");
      System.exit(1);
    }
    final long code = getFileLength("code", args[0]);
    final long data = getFileLength("data", args[1]);
    final long rmap = getFileLength("rmap", args[2]);
    final long total = code + data + rmap;
    System.out.println("Code Size: " + code);
    System.out.println("Data Size: " + data);
    System.out.println("Rmap Size: " + rmap);
    System.out.println("Total Size: " + total);
  }

  private static long getFileLength(final String name, final String location) {
    final File file = new File(location);
    if(!file.exists()) {
      System.err.println("Location for " + name + " given as " + location + " does not exist.");
      System.exit(2);
    }
    return file.length();
  }
}
