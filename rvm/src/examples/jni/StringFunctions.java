/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Test JNI Functions dealing with Strings
 * The following JNI calls are tested:
 *      NewString                  GetStringLength
 *      NewStringUTF               GetStringUTFLength
 *
 * @author Ton Ngo, Steve Smith 
 * @date   2/29/00
 */
class StringFunctions {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;
  
  static String hiTon = "hiTon";
  static String hiSteve = "hiSteve";
  static String hiTony = "hiTony";
  static String hiDick = "hiDick";

  public static native void setVerboseOff();

  /**
   * Declare native methods that will call the JNI String Functions
   */
  static native String accessNewString(String s);
  static native int accessGetStringLength(String s);
  static native String accessNewStringUTF(String s);
  static native int accessGetStringUTFLength(String s);
  static native String testGetReleaseStringChars(String s);
  static native String testGetReleaseStringUTFChars(String s);

  /**
   * constructor
   */
  public StringFunctions() {
  }

  public static void main(String args[]) {

    int returnValue;
    String returnString;

    System.loadLibrary("StringFunctions");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;        
        setVerboseOff();
      }         
    }

    /**
     * initialize static fields
     */

    /**
     * Test static GetStatic<type>Field
     */
    returnString = accessNewString(hiTon);
    printVerbose("accessNewString returnString = " + returnString + ".");
    checkTest(0, (returnString.equals(hiTon)), "accessNewString");
    
    returnValue = accessGetStringLength(hiTon);
    checkTest(0, (returnValue==5), "accessGetStringLength");
    
    returnString = accessNewStringUTF(hiSteve);
    printVerbose("accessNewStringUTF returnString = " + returnString + ".");
    checkTest(0, (returnString.equals(hiSteve)), "accessNewStringUTF");
    
    returnValue = accessGetStringUTFLength(hiSteve);
    checkTest(0, (returnValue==7), "accessGetStringUTFLength");

    returnString = testGetReleaseStringChars(hiTony);
    checkTest(0, (returnString.equals(hiTony)), "testGetReleaseStringChars");

    returnString = testGetReleaseStringUTFChars(hiDick);
    checkTest(0, (returnString.equals(hiDick)), "testGetReleaseStringUTFChars");

    // Summarize

    if (allTestPass)
      System.out.println("PASS: StringFunctions");
    else 
      System.out.println("FAIL: StringFunctions");
  
  }

  static void printVerbose(String str) {
    if (verbose) 
      System.out.println(str);
  }

  static void checkTest(int returnValue, boolean postCheck, String testName) {
    if (returnValue==0 && postCheck) {
      printVerbose("PASS: " + testName);
    } else {
      allTestPass = false;
      printVerbose("FAIL: " + testName);
    }
  }

}
