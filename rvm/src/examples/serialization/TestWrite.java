/*
 * (C) Copyright IBM Corp. 2001
 */

import java.io.ObjectOutputStream;

class TestWrite
{
  public static void main(String args[]) {
    try {
      TestSerialization ts = new TestSerialization();
      ObjectOutputStream out = new ObjectOutputStream(System.out);
      out.writeObject(ts);
    } catch (java.io.IOException e) {
      e.printStackTrace(System.err);
    }
  }
}

