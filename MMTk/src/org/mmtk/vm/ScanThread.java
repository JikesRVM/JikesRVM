/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.vm;

import org.mmtk.utility.deque.*;




/**
 * Class that supports scanning thread stacks for references during
 * collections. References are located using GCMapIterators and are
 * inserted into a set of root locations.  Optionally, a set of 
 * interior pointer locations paired with the object is created.
 *
 * @author Stephen Smith
 * @author Perry Cheng
 */  
public class ScanThread {

  /**
   * quietly validates each ref reported by map iterators
   */
  static final boolean VALIDATE_STACK_REFS = true;

  /**
   * debugging options to produce printout during scanStack
   * MULTIPLE GC THREADS WILL PRODUCE SCRAMBLED OUTPUT so only
   * use these when running with PROCESSORS=1
   */
  static int DUMP_STACK = 0;

  /**
   * Threads, stacks,  jni environments,  and register objects  have a
   * complex  interaction  in terms  of  scanning.   The operation  of
   * scanning the  stack reveals not  only roots inside the  stack but
   * also the  state of the register  objects's gprs and  the JNI refs
   * array.  They are all associated  via the thread object, making it
   * natural for  scanThread to be considered a  single operation with
   * the  method  directly  accessing  these objects  via  the  thread
   * object's fields. <p>
   *
   * One pitfall occurs when scanning the thread object (plus
   * dependents) when not all of the objects have been copied.  Then
   * it may be that the innards of the register object has not been
   * copied while the stack object has.  The result is that an
   * inconsistent set of slots is reported.  In this case, the copied
   * register object may not be correct if the copy occurs after the
   * root locations are discovered but before those locations are
   * processed. In essence, all of these objects form one logical unit
   * but are physically separated so that sometimes only part of it
   * has been copied causing the scan to be incorrect. <p>
   *
   * The caller of this routine must ensure that all of these
   * components's descendants are consistent (all copied) when this
   * method is called. <p>
   *
   *   t
   *   t.stack (if stack moving is enabled)
   *   t.jniEnv.jniRefs (t.jniEnv might be null)      
   *   t.contextRegisters 
   *   t.contextRegisters.gprs
   *   t.hardwareExceptionRegisters
   *   t.hardwareExceptionRegisters.gprs 
   */
  public static void scanThread(Thread t, AddressDeque rootLocations, 
                                AddressPairDeque codeLocations) {
        
  }



}
