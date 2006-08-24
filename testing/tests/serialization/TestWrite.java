/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import java.io.ObjectOutputStream;

/**
 * @author unascribed
 */
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

