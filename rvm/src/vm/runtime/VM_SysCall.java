/*
 * (C) Copyright IBM Corp 2001,2002,2003
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Support for lowlevel (ie non-JNI) invocation of C functions.
 *
 * @author Dave Grove
 * @author Derek Lieber
 * @author Kris Venstermans
 */
public class VM_SysCall implements VM_Uninterruptible { 

  /**
   * call0
   * @param ip  address of a function in sys.C 
   * @return  integer value returned by function in sys.C
   */
  public static int call0(VM_Address ip) throws VM_PragmaInline {
    return  VM_Magic.sysCall0(ip);
  }

  /**
   * call1
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return  integer value returned by function in sys.C
   */
  public static int call1(VM_Address ip, int p1) throws VM_PragmaInline {
    return  VM_Magic.sysCall1(ip, p1);
  }

  /**
   * call_I_A
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return  integer value returned by function in sys.C
   */
  public static int call_I_A(VM_Address ip, VM_Address p1) throws VM_PragmaInline {
    return  call1(ip, p1.toInt());
  }

  /**
   * call_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return  address value returned by function in sys.C
   */
  public static VM_Address call_A_I(VM_Address ip, int p1) throws VM_PragmaInline {
    return  VM_Address.fromInt(call1(ip, p1));
  }

  /**
   * call2
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int call2(VM_Address ip, int p1, int p2) throws VM_PragmaInline {
    return  VM_Magic.sysCall2(ip, p1, p2);
  }

  /**
   * call_I_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int call_I_A_I(VM_Address ip, VM_Address p1, int p2) throws VM_PragmaInline {
    return  call2(ip, p1.toInt(), p2);
  }

  /**
   * call_I_A_A
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int call_I_A_A(VM_Address ip, VM_Address p1, VM_Address p2) throws VM_PragmaInline {
    return  call2(ip, p1.toInt(), p2.toInt());
  }

  /**
   * call_A_I_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  address value returned by function in sys.C
   */
  public static VM_Address call_A_I_I(VM_Address ip, int p1, int p2) throws VM_PragmaInline {
    return  VM_Address.fromInt(call2(ip, p1, p2));
  }

  /**
   * call_A_I_A
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  address value returned by function in sys.C
   */
  public static VM_Address call_A_I_A(VM_Address ip, int p1, VM_Address p2) throws VM_PragmaInline {
    return  VM_Address.fromInt(call2(ip, p1, p2.toInt()));
  }

  /**
   * call_A_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  address value returned by function in sys.C
   */
  public static VM_Address call_A_A_I(VM_Address ip, VM_Address p1, int p2) throws VM_PragmaInline {
    return  VM_Address.fromInt(call2(ip, p1.toInt(), p2));
  }

  /**
   * call3
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return  integer value returned by function in sys.C
   */
  public static int call3(VM_Address ip, int p1, int p2, int p3) throws VM_PragmaInline {
    return  VM_Magic.sysCall3(ip, p1, p2, p3);
  }

  /**
   * call_I_A_I_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return  integer value returned by function in sys.C
   */
  public static int call_I_A_I_I(VM_Address ip, VM_Address p1, int p2, int p3) throws VM_PragmaInline {
    return  call3(ip, p1.toInt(), p2, p3);
  }
  
  /**
   * call_I_I_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return  integer value returned by function in sys.C
   */
  public static int call_I_I_A_I(VM_Address ip, int p1, VM_Address p2, int p3) throws VM_PragmaInline {
    return  call3(ip, p1, p2.toInt(), p3);
  }

  /**
   * call_I_A_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return  integer value returned by function in sys.C
   */
  public static int call_I_A_A_I(VM_Address ip, VM_Address p1, VM_Address p2, int p3) throws VM_PragmaInline {
    return  call3(ip, p1.toInt(), p2.toInt(), p3);
  }

  /**
   * call_A_I_A_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return  address value returned by function in sys.C
   */
  public static VM_Address call_A_I_A_I(VM_Address ip, int p1, VM_Address p2, int p3) throws VM_PragmaInline {
    return  VM_Address.fromInt(call3(ip, p1, p2.toInt(), p3));
  }

  /**
   * call4
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return  integer value returned by function in sys.C
   */
  public static int call4(VM_Address ip, int p1, int p2, int p3, int p4) throws VM_PragmaInline {
    return VM_Magic.sysCall4(ip, p1, p2, p3, p4);
  }

  /**
   * call_I_A_I_I_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return  address value returned by function in sys.C
   */
  public static int call_I_A_I_I_I(VM_Address ip, VM_Address p1, int p2, int p3, int p4) throws VM_PragmaInline {
    return call4(ip, p1.toInt(), p2, p3, p4);
  }

  /**
   * call_I_A_A_I_A
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return  address value returned by function in sys.C
   */
  public static int call_I_A_A_I_A(VM_Address ip, VM_Address p1, VM_Address p2, int p3, VM_Address p4) throws VM_PragmaInline {
    return call4(ip, p1.toInt(), p2.toInt(), p3, p4.toInt());
  }

  /**
   * call_A_A_I_I_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return  address value returned by function in sys.C
   */
  public static VM_Address call_A_A_I_I_I(VM_Address ip, VM_Address p1, int p2, int p3, int p4) throws VM_PragmaInline {
    return VM_Address.fromInt(call4(ip, p1.toInt(), p2, p3, p4));
  }

  /**
   * call_L_0
   * @param ip  address of a function in sys.C 
   * @return long value returned by function in sys.C
   */
  public static long call_L_0(VM_Address ip) throws VM_PragmaInline {
    return VM_Magic.sysCall_L_0(ip);
  }

  /**
   * call_L_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return long value returned by function in sys.C
   */
  public static long call_L_I(VM_Address ip, int p1) throws VM_PragmaInline {
    return  VM_Magic.sysCall_L_I(ip, p1);
  }

  /**
   * call_I_L_I
   * @param ip  address of a function in sys.C 
   * @param p1  long value
   * @param p2
   * @return int value returned by function in sys.C
   */
  public static int call_I_L_I(VM_Address ip, long p1, int p2) throws VM_PragmaInline {
    VM.sysFail("TODO: VM_SysCall.call_I_L_I");
    return 0; // TODO
  }

  /**
   * callAD
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int callAD(VM_Address ip, VM_Address p1, double p2) throws VM_PragmaInline {
    return VM_Magic.sysCallAD(ip, p1.toInt(), p2);
  }
}
