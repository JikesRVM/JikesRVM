/*
 * (C) Copyright IBM Corp 2001,2002,2003
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Support for lowlevel (ie non-JNI) invocation of C functions.
 * 
 * All methods of this class have the following signature:
 * <pre>
 * static <TYPE> callNAME(VM_Address code, <var args to pass via native calling convention>)
 * </pre>
 * When one of Jikes RVM's compilers encounters an invokestatic that invokes
 * a method of this class, instead of generating code that calls the method
 * it generates code to invoke the function pointer that is the method's 
 * first argument, passing the rest of the arguments using the native OS 
 * calling convention.  The result of the call is assumed to
 * be returned using the native OS calling convention.
 *
 * NOTE: From the standpoint of the rest of the VM, an invocation 
 * to VM_SysCall is uninterruptible

 * @author Dave Grove
 * @author Derek Lieber
 * @author Kris Venstermans
 */
public class VM_SysCall implements VM_Uninterruptible { 

  /**
   * call0
   * @param ip  address of a function in sys.C 
   * @return integer value returned by function in sys.C
   */
  public static int call0(VM_Address ip) { return 0; }
  
  /**
   * call1
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return integer value returned by function in sys.C
   */
  public static int call1(VM_Address ip, int p1) { return 0; }

  /**
   * call_I_A
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return integer value returned by function in sys.C
   */
  public static int call_I_A(VM_Address ip, VM_Address p1)  {
    return 0;
  }

  /**
   * call_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return address value returned by function in sys.C
   */
  public static VM_Address call_A_I(VM_Address ip, int p1)  {
    return null;
  }

  /**
   * call2
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return integer value returned by function in sys.C
   */
  public static int call2(VM_Address ip, int p1, int p2)  {
    return 0;
  }
  
  /**
   * call_I_I_A
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return integer value returned by function in sys.C
   */
  public static int call_I_I_A(VM_Address ip, int p1, VM_Address p2)  {
    return 0;
  }

  /**
   * call_I_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return integer value returned by function in sys.C
   */
  public static int call_I_A_I(VM_Address ip, VM_Address p1, int p2)  {
    return 0;
  }

  /**
   * call_I_A_A
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return integer value returned by function in sys.C
   */
  public static int call_I_A_A(VM_Address ip, VM_Address p1, VM_Address p2)  {
    return 0;
  }

  /**
   * call_A_I_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return address value returned by function in sys.C
   */
  public static VM_Address call_A_I_I(VM_Address ip, int p1, int p2)  {
    return null;
  }

  /**
   * call_A_I_A
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return address value returned by function in sys.C
   */
  public static VM_Address call_A_I_A(VM_Address ip, int p1, VM_Address p2)  {
    return null;
  }

  /**
   * call_A_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return address value returned by function in sys.C
   */
  public static VM_Address call_A_A_I(VM_Address ip, VM_Address p1, int p2)  {
    return null;
  }

  /**
   * call3
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return integer value returned by function in sys.C
   */
  public static int call3(VM_Address ip, int p1, int p2, int p3)  {
    return 0;
  }

  /**
   * call_I_A_I_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return integer value returned by function in sys.C
   */
  public static int call_I_A_I_I(VM_Address ip, VM_Address p1, int p2, int p3)  {
    return 0;
  }
  
  /**
   * call_I_I_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return integer value returned by function in sys.C
   */
  public static int call_I_I_A_I(VM_Address ip, int p1, VM_Address p2, int p3)  {
    return 0;
  }

  public static long call_L_L_L(VM_Address ip, long v1, long v2) {
    return 0L;
  }

  public static int call_I_D(VM_Address ip, double v2) {
    return 20;
  }
  public static double call_D_L(VM_Address ip, long v2) {
    return 20;
  }
  public static float call_F_L(VM_Address ip, long v2) {
    return 20f;
  }
  public static float call_F_I(VM_Address ip, int v2) {
    return 20f;
  }

  /**
   * call_I_A_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return integer value returned by function in sys.C
   */
  public static int call_I_A_A_I(VM_Address ip, VM_Address p1, VM_Address p2, int p3)  {
    return 0;
  }

  /**
   * call_A_I_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return address value returned by function in sys.C
   */
  public static VM_Address call_A_I_A_I(VM_Address ip, int p1, VM_Address p2, int p3)  {
    return null;
  }

  /**
   * call4
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return integer value returned by function in sys.C
   */
  public static int call4(VM_Address ip, int p1, int p2, int p3, int p4)  {
    return 0;
  }

  /**
   * call_I_A_I_I_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return address value returned by function in sys.C
   */
  public static int call_I_A_I_I_I(VM_Address ip, VM_Address p1, int p2, int p3, int p4)  {
    return 0;
  }

  /**
   * call_I_A_A_A_A
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return address value returned by function in sys.C
   */
  public static int call_I_A_A_A_A(VM_Address ip, VM_Address p1, VM_Address p2, VM_Address p3, VM_Address p4)  {
    return 0;
  }

  /**
   * call_A_A_I_I_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return address value returned by function in sys.C
   */
  public static VM_Address call_A_A_I_I_I(VM_Address ip, VM_Address p1, int p2, int p3, int p4)  {
    return null;
  }

  /**
   * call_L_0
   * @param ip  address of a function in sys.C 
   * @return long value returned by function in sys.C
   */
  public static long call_L_0(VM_Address ip)  {
    return 0;
  }

  /**
   * call_L_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return long value returned by function in sys.C
   */
  public static long call_L_I(VM_Address ip, int p1)  {
    return 0;
  }

  /**
   * call_I_L_I
   * @param ip  address of a function in sys.C 
   * @param p1  long value
   * @param p2
   * @return int value returned by function in sys.C
   */
  public static int call_I_L_I(VM_Address ip, long p1, int p2)  {
    return 0; // TODO
  }

  /**
   * callAD
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return integer value returned by function in sys.C
   */
  public static int callAD(VM_Address ip, VM_Address p1, double p2)  {
    return 0;
  }
}
