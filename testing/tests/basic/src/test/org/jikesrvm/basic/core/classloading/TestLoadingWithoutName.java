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
package test.org.jikesrvm.basic.core.classloading;

import java.io.IOException;
import java.io.InputStream;

public class TestLoadingWithoutName
    extends ClassLoader {

  public static void main(String[] args) throws IOException {

    final String resource = "TestClassLoading.class";
    System.out.println("Loading resource " + resource);
    final InputStream input = TestLoadingWithoutName.class.getResourceAsStream(resource);
    System.out.println("Loaded resource? = " + (null != input));
    final int size = input.available();
    final byte[] data = new byte[size];
    int count = 0;
    while (count < data.length) {
      count += input.read(data,count,data.length - count);
    }

    final TestLoadingWithoutName loader = new TestLoadingWithoutName();
    System.out.println("Defining class");
    final Class<?> clazz = loader.defineClass(null, data, 0, data.length);
    System.out.println("Class defined Expected: test.org.jikesrvm.basic.core.TestClassLoading Actual: " + clazz.getName());
  }
}
