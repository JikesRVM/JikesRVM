/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * A pragma that can be used to declare that a 
 * particular method is logically uninterruptible
 * even though it contains bytecodes that are actually interruptible.
 * The effect of this pragma is to supress warning messages
 * about violations of uninterruptiblity when compiling a method 
 * that throws this exception.
 * There are two cases in which using the pragma is justified.
 * <ul>
 * <li> Uninterruptibility is ensured via some other mechansism. 
 *      For example, the method explicitly disables threadswitching
 *      around the interruptible regions (VM.sysWrite on String).  
 *      Or the interruptible regions are not reachable when the VM is 
 *      running (various VM.sysWrite that check VM.runningVM).
 * <li> The interruptible regions represent an 'error' condition that will
 *      never be executed unless the VM is already in the process of reporting
 *      an error, for example VM_Runtime.raiseClassCastException.
 * <ul>
 * Extreme care must be exercised when using this pragma since it supresses 
 * the checking of uninterruptibility.
 * 
 * @deprecated
 * @author Dave Grove
 */
public class VM_PragmaLogicallyUninterruptible extends VM_PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lcom/ibm/JikesRVM/VM_PragmaLogicallyUninterruptible;");
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(me, method);
  }
}
