/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.librarySupport;
import com.ibm.JikesRVM.VM_Thread;

/**
 * This class provides a base class which java.lang.Thread must extend.
 *
 * @author Stephen Fink
 */
public class ThreadBase extends VM_Thread {
  //-#if RVM_WITH_OSR
  public ThreadBase() {
    super.isSystemThread = false;
  }
  //-#endif
  public String toString() {
    return "ThreadBase";
  }
}
