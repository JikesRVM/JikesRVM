/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$ 
/**
 * Test static and instance field access from native code
 * The following JNI calls are tested:
 *      GetStaticIntField          SetStaticIntField         
 *      GetStaticBooleanField      SetStaticBooleanField     
 *      GetStaticByteField         SetStaticByteField        
 *      GetStaticCharField         SetStaticCharField        
 *      GetStaticShortField        SetStaticShortField       
 *      GetStaticLongField         SetStaticLongField        
 *      GetStaticFloatField        SetStaticFloatField       
 *      GetStaticDoubleField       SetStaticDoubleField      
 *      GetStaticObjectField       SetStaticObjectField      
 *                                                           
 *      GetIntField                SetIntField               
 *      GetBooleanField            SetBooleanField           
 *      GetByteField               SetByteField              
 *      GetCharField               SetCharField              
 *      GetShortField              SetShortField             
 *      GetLongField               SetLongField              
 *      GetFloatField              SetFloatField             
 *      GetDoubleField             SetDoubleField            
 *      GetObjectField             SetObjectField            
 *
 *
 * @author Ton Ngo, Steve Smith 
 * @date   1/25/00
 */
class FieldAccess {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;


  /**
   * static fields
   */
  static int staticInt;           
  static byte staticByte;
  static char staticChar;
  static short staticShort;
  static boolean staticBoolean;
  static float staticFloat;
  static double staticDouble;
  static long staticLong;
  static FieldAccess staticObject;

  /**
   * virtual fields
   */
  int instanceInt;  
  byte instanceByte;
  char instanceChar;
  short instanceShort;
  boolean instanceBoolean;
  float instanceFloat;
  double instanceDouble;
  long instanceLong;
  FieldAccess instanceObject;
  
  public static native void setVerboseOff();

  /**
   * Declare native methods that will call the JNI GetTYPEStaticField
   */
  static native int accessStaticIntField();
  static native int accessStaticBooleanField();
  static native int accessStaticByteField();
  static native int accessStaticCharField();
  static native int accessStaticShortField();
  static native int accessStaticLongField();
  static native int accessStaticFloatField();
  static native int accessStaticDoubleField();
  static native int accessStaticObjectField();

  static native int accessIntField(Object obj);
  static native int accessBooleanField(Object obj);
  static native int accessByteField(Object obj);
  static native int accessCharField(Object obj);
  static native int accessShortField(Object obj);
  static native int accessLongField(Object obj);
  static native int accessFloatField(Object obj);
  static native int accessDoubleField(Object obj);
  static native int accessObjectField(Object obj);

  /**
   * Declare native methods that will call the JNI Set<type>Field functions
   */
  static native int setStaticIntField();
  static native int setStaticBooleanField();
  static native int setStaticByteField();
  static native int setStaticCharField();
  static native int setStaticShortField();
  static native int setStaticLongField();
  static native int setStaticFloatField();
  static native int setStaticDoubleField();
  static native int setStaticObjectField(Object obj);
                      
  static native int setIntField(Object obj);
  static native int setBooleanField(Object obj);
  static native int setByteField(Object obj);
  static native int setCharField(Object obj);
  static native int setShortField(Object obj);
  static native int setLongField(Object obj);
  static native int setFloatField(Object obj);
  static native int setDoubleField(Object obj);
  static native int setObjectField(Object obj, Object obj2);


  /**
   * constructor initializes instance fields
   */
  public FieldAccess() {
    instanceInt = 456;
    instanceByte = 34;
    instanceChar = 't';
    instanceShort = 45;
    instanceBoolean = false;
    instanceFloat = .456f;
    instanceDouble = 1234.567d;
    instanceLong = 135l;
    instanceObject = this;
  }

  public static void main(String args[]) {
    int returnValue;
    FieldAccess tempObject;

    System.loadLibrary("FieldAccess");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;        
        setVerboseOff();
      }         
    }

    FieldAccess anObj = new FieldAccess();

    /**
     * initialize static fields
     */
    staticInt = 123;
    staticBoolean = true;
    staticByte = 12;
    staticChar = 'a';
    staticShort = 67;
    staticLong = 246l;
    staticFloat = .123f;
    staticDouble = 567.123d;
    staticObject = new FieldAccess();

    FieldAccess extraObject = new FieldAccess();

    /**
     * Test static GetStatic<type>Field
     */
    returnValue = accessStaticIntField();
    checkTest(returnValue, (staticInt==123), "accessStaticIntField");
    
    returnValue = accessStaticBooleanField();
    checkTest(returnValue, (staticBoolean==true), "accessStaticBooleanField");
    
    returnValue = accessStaticByteField();
    checkTest(returnValue, (staticByte==12), "accessStaticByteField");
    
    returnValue = accessStaticCharField();
    checkTest(returnValue, (staticChar=='a'), "accessStaticCharField");
    
    returnValue = accessStaticShortField();
    checkTest(returnValue, (staticShort==67), "accessStaticShortField");
    
    returnValue = accessStaticLongField();
    checkTest(returnValue, (staticLong==246l), "accessStaticLongField");
    
    returnValue = accessStaticFloatField();
    checkTest(returnValue, (staticFloat==.123f), "accessStaticFloatField");
    
    returnValue = accessStaticDoubleField();
    checkTest(returnValue, (staticDouble==567.123d), "accessStaticDoubleField");
    
    tempObject = staticObject;   
    returnValue = accessStaticObjectField();
    checkTest(returnValue, (staticObject.equals(tempObject)), "accessStaticObjectField");


    /**
     * Test instance Get<type>Field
     */
    returnValue = accessIntField(anObj);
    checkTest(returnValue, (anObj.instanceInt==456), "accessIntField");
    
    returnValue = accessBooleanField(anObj);
    checkTest(returnValue, (anObj.instanceBoolean==false), "accessBooleanField");
    
    returnValue = accessByteField(anObj);
    checkTest(returnValue, (anObj.instanceByte==34), "accessByteField");
    
    returnValue = accessCharField(anObj);
    checkTest(returnValue, (anObj.instanceChar=='t'), "accessCharField");
    
    returnValue = accessShortField(anObj);
    checkTest(returnValue, (anObj.instanceShort==45), "accessShortField");
    
    returnValue = accessLongField(anObj);
    checkTest(returnValue, (anObj.instanceLong==135l), "accessLongField");
    
    returnValue = accessFloatField(anObj);
    checkTest(returnValue, (anObj.instanceFloat==.456f), "accessFloatField");
    
    returnValue = accessDoubleField(anObj);
    checkTest(returnValue, (anObj.instanceDouble==1234.567d), "accessDoubleField");
    
    tempObject = anObj.instanceObject;    // to check that the reference doesn't get corrupted
    returnValue = accessObjectField(anObj);
    checkTest(returnValue, (anObj.instanceObject.equals(tempObject)), "accessObjectField");

    /**
     * Test static SetStatic<type>Field
     */
    returnValue = setStaticIntField();
    checkTest(returnValue, (staticInt==456), "setStaticIntField");
    
    returnValue = setStaticBooleanField();
    checkTest(returnValue, (staticBoolean==false), "setStaticBooleanField");
    
    returnValue = setStaticByteField();
    checkTest(returnValue, (staticByte==24), "setStaticByteField");
    
    returnValue = setStaticCharField();
    checkTest(returnValue, (staticChar=='b'), "setStaticCharField");
    
    returnValue = setStaticShortField();
    checkTest(returnValue, (staticShort==76), "setStaticShortField");
    
    returnValue = setStaticLongField();
    checkTest(returnValue, (staticLong==357l), "setStaticLongField");
    
    returnValue = setStaticFloatField();
    checkTest(returnValue, (staticFloat==.234f), "setStaticFloatField");
    
    returnValue = setStaticDoubleField();
    checkTest(returnValue, (staticDouble==123.456d), "setStaticDoubleField");
    
    returnValue = setStaticObjectField(extraObject);
    checkTest(returnValue, (staticObject.equals(extraObject)), "setStaticObjectField");


    /**
     * Test instance Get<type>Field
     */
    returnValue = setIntField(anObj);
    checkTest(returnValue, (anObj.instanceInt==789), "setIntField");
    
    returnValue = setBooleanField(anObj);
    checkTest(returnValue, (anObj.instanceBoolean==true), "setBooleanField");
    
    returnValue = setByteField(anObj);
    checkTest(returnValue, (anObj.instanceByte==77), "setByteField");
    
    returnValue = setCharField(anObj);
    checkTest(returnValue, (anObj.instanceChar=='q'), "setCharField");
    
    returnValue = setShortField(anObj);
    checkTest(returnValue, (anObj.instanceShort==25), "setShortField");
    
    returnValue = setLongField(anObj);
    checkTest(returnValue, (anObj.instanceLong==345l), "setLongField");
    
    returnValue = setFloatField(anObj);
    checkTest(returnValue, (anObj.instanceFloat==.789f), "setFloatField");
    
    returnValue = setDoubleField(anObj);
    checkTest(returnValue, (anObj.instanceDouble==234.456d), "setDoubleField");
    
    returnValue = setObjectField(anObj,extraObject);
    checkTest(returnValue, (anObj.instanceObject.equals(extraObject)), "setObjectField");




    // Summarize

    if (allTestPass)
      System.out.println("PASS: FieldAccess");
    else 
      System.out.println("FAIL: FieldAccess");
  
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
