/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$ 

/**
 * Simple test to see if hijacking of select works.
 * 
 * @author Dave Grove
 */
class SelectTest {

  public static native void doit();

  public static void main(String args[]) {
    System.loadLibrary("SelectTest");
    doit();
  }
}
