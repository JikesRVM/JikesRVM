/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Test native method
 *
 * @author unascribed
 */

class tNative
{
  public static native int nativeFoo(int count);

  public static void main(String args[])
  {
    System.out.println("Attempting to load dynamic library ...");
    System.out.println("(the LIBPATH env variable must be set for this directory)");

    System.loadLibrary("tNative");
    
    int returnValue = nativeFoo(17);
    System.out.println("First nativeFoo return " + returnValue);
    
    returnValue = nativeFoo(30);
    System.out.println("Second nativeFoo return " + returnValue);
    
  }
}
