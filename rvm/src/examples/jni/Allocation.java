/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$:
/*
 * @author Ton Ngo
 */

class Allocation {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native void setVerboseOff();

  /**
   * Declare native methods that will call the JNI NewObject Functions
   */
  static native String testNewObjectA(Class cls, char inputCharArray[]);
  static native String testNewObjectV(Class cls, char inputCharArray[]);
  static native String testNewObject (Class cls, char inputCharArray[]);

  public static void main(String args[]) {

    String returnObj;
    Class classObj=null;
      
    System.loadLibrary("Allocation");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;        
        setVerboseOff();
      }         
    }

    // the class to create a new instance

    try {
      classObj = Class.forName("java.lang.String");
    } catch (Exception e) {
      System.out.println("Fix test program");
    }    

    // get some input for creating the new instance of String
    String inputStr = "Month Of March";
    char inputCharArray[] = new char[inputStr.length()];
    inputStr.getChars(0, inputStr.length(), inputCharArray, 0);

    returnObj = testNewObjectA(classObj, inputCharArray);
    // System.out.println("The new object:  " + returnObj);
    checkTest(0, (returnObj.equals(inputStr)), "NewObjectA");
    
    returnObj = null;
    returnObj = testNewObjectV(classObj, inputCharArray);
    // System.out.println("The new object:  " + returnObj);
    checkTest(0, (returnObj.equals(inputStr)), "NewObjectV");

    returnObj = null;
    returnObj = testNewObject(classObj, inputCharArray);
    // System.out.println("The new object:  " + returnObj);
    checkTest(0, (returnObj.equals(inputStr)), "NewObject");


    // Summarize

    if (allTestPass)
      System.out.println("PASS: Allocation");
    else 
      System.out.println("FAIL: Allocation");
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
