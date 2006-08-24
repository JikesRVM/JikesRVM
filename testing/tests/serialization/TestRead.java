/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import java.io.ObjectInputStream;
import java.io.FileInputStream;

/**
 * @author unascribed
 */
class TestRead
{
  public static void main(String args[]) {
    try {
      System.out.println("TestRead");
      FileInputStream fin = new FileInputStream(args[0]);
      ObjectInputStream in = new ObjectInputStream(fin);
      TestSerialization ts = (TestSerialization) in.readObject();
      System.out.println(ts);
      System.out.println("Done");
    } catch (java.io.IOException e) {
      e.printStackTrace(System.err);
    } catch (java.lang.ClassNotFoundException e) {
      e.printStackTrace(System.err);
    }
  }
}

