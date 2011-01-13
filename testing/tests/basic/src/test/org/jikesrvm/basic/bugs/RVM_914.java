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

public class RVM_914 {

  private enum Foo {FOO}

  static class Bar implements java.io.Serializable {
    Foo a = Foo.FOO;
    Foo[] b = {Foo.FOO};
  }

  public static void main(String[] args) throws Exception {
    byte[] ba = null;
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
    oos.writeObject(new Bar());
    oos.close();
    ba = baos.toByteArray();
    java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(ba));
    ois.readObject();
    ois.close();
    System.out.println("PASSED");
  }
}
