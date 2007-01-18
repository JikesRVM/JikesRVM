/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
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
    System.out.println("Class defined Expected: test.org.jikesrvm.basic.core.TestClassLoading Actual: " + clazz.getName() );
  }
}
