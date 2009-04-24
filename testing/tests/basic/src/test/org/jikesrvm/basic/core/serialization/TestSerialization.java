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
package test.org.jikesrvm.basic.core.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

class TestSerialization {
  public static void main(String[] args) throws Exception {
    //Uncomment when need to generate serialized data from jdk again
/*
    try {
      final ObjectOutputStream output = new ObjectOutputStream(new java.io.FileOutputStream("MySerializationData.dat"));
      output.writeObject(new SerializationData());
      output.close();
    } catch (java.io.IOException e) {
      e.printStackTrace(System.out);
    }
*/
    final byte[] data = loadData();

    try {
      final ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(data));
      System.out.println("Attemping read");
      final SerializationData ts = (SerializationData) input.readObject();
      System.out.println("Existing serialization read");
      System.out.println(ts);
    } catch (java.io.IOException e) {
      e.printStackTrace(System.out);
    } catch (ClassNotFoundException e) {
      e.printStackTrace(System.out);
    }

    byte[] bytes = new byte[0];
    try {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final ObjectOutputStream output = new ObjectOutputStream(baos);
      System.out.println("Attemping write");
      SerializationData sd = new SerializationData();
      sd.jitter();
      output.writeObject(sd);
      output.flush();
      output.close();
      bytes = baos.toByteArray();
      System.out.println("write success. Checking consistency of data written ...");
      System.out.println("actual.length (" + bytes.length + ") vs expected.length (" + data.length);
      for (int i = 0; i < data.length && i < bytes.length; i++) {
        if(data[i] != bytes[i]) {
          System.out.println("data differs at " + i);
          break;
        }
      }
    } catch (java.io.IOException e) {
      e.printStackTrace(System.out);
    }

    try {
      final ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes));
      System.out.println("Attemping read of output data");
      final SerializationData ts = (SerializationData) input.readObject();
      System.out.println("Existing serialization read");
      System.out.println(ts);
    } catch (java.io.IOException e) {
      e.printStackTrace(System.out);
    } catch (ClassNotFoundException e) {
      e.printStackTrace(System.out);
    }
  }

  private static byte[] loadData() throws IOException {
    final String resource = "SerializationData.dat";
    System.out.println("Loading resource " + resource);
    final InputStream input = TestSerialization.class.getResourceAsStream(resource);
    System.out.println("Loaded resource? = " + (null != input));
    final int size = input.available();
    final byte[] data = new byte[size];
    int count = 0;
    while (count < data.length) {
      count += input.read(data, count, data.length - count);
    }
    return data;
  }
}
