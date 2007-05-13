/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.memorymanagers.mminterface;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_BootRecord;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.heap.Mmapper;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Common debugging utility functions used by various garbage collectors
 */
@Uninterruptible public class DebugUtil implements VM_Constants, Constants {

  private static Object[] tibForArrayType;
  private static Object[] tibForClassType;
  private static Object[] tibForPrimitiveType;

  @Interruptible
  static void boot (VM_BootRecord theBootRecord) {
    // get addresses of TIBs for VM_Array & VM_Class used for testing Type ptrs
    VM_Type t = VM_Array.IntArray;
    tibForArrayType = VM_ObjectModel.getTIB(t);
    tibForPrimitiveType = VM_ObjectModel.getTIB(VM_Type.IntType);
    t = VM_Magic.getObjectType(VM_BootRecord.the_boot_record);
    tibForClassType = VM_ObjectModel.getTIB(t);
  }    

  /**
   * Check if an address appears to point to an instance of VM_Type
   * 
   * @param typeAddress the address to check
   */
  @Uninterruptible
  public static boolean validType(ObjectReference typeAddress) { 
     if (!Space.isMappedObject(typeAddress))
      return false;  // type address is outside of heap

    // check if types tib is one of three possible values
    Object[] typeTib = VM_ObjectModel.getTIB(typeAddress);
    return ( (typeTib == tibForClassType) || 
             (typeTib == tibForArrayType) ||
             (typeTib == tibForPrimitiveType));
  }

  /**
   * Dump all threads & their stacks starting at the frame identified
   * by the threads saved contextRegisters (ip & fp fields).
   */
  @Uninterruptible
  public static void dumpAllThreadStacks() { 
      Address ip, fp;
      VM_Thread t;
      VM_Scheduler.trace("\ndumpAllThreadStacks",
                         "dumping stacks for all threads");
    for (VM_Thread thread : VM_Scheduler.threads) {
      t = thread;
      if (t == null) continue;
      VM.sysWrite("\n Thread ");
      t.dump();
      VM.sysWrite("\n");
      // start stack dump using fp & ip in threads saved context registers
      ip = t.contextRegisters.getInnermostInstructionAddress();
      fp = t.contextRegisters.getInnermostFramePointer();
      VM_Scheduler.dumpStack(ip, fp);
    }
    VM.sysWrite("\ndumpAllThreadStacks: end of thread stacks\n\n");
  }  // dumpAllThreadStacks

  /**
   * Check if a ref, its tib pointer & type pointer are all in the heap
   */
  @Uninterruptible
  public static boolean validObject(Object ref) { 
      return validRef(ObjectReference.fromObject(ref));
  }

  @Uninterruptible
  public static boolean validRef(ObjectReference ref) { 

    if (ref.isNull()) return true;
    if (!Space.isMappedObject(ref)) {
      VM.sysWrite("validRef: REF outside heap, ref = ");
      VM.sysWrite(ref); VM.sysWrite("\n");
      Space.printVMMap();
      return false;
    }
    if (MM_Constants.MOVES_OBJECTS) {
        /*
      TODO: Work out how to check if forwarded
      if (Plan.isForwardedOrBeingForwarded(ref)) {
        // TODO: actually follow forwarding pointer
        // (need to bound recursion when things are broken!!)
        return true; 
      }
      */
    }
    
    Object[] tib = VM_ObjectModel.getTIB(ref);
    Address tibAddr = VM_Magic.objectAsAddress(tib);
    if (!Space.isMappedObject(ObjectReference.fromObject(tib))) {
      VM.sysWrite("validRef: TIB outside heap, ref = "); VM.sysWrite(ref);
      VM.sysWrite(" tib = ");VM.sysWrite(tibAddr);
      VM.sysWrite("\n");
      return false;
    }
    if (tibAddr.isZero()) {
      VM.sysWrite("validRef: TIB is Zero! "); VM.sysWrite(ref);
      VM.sysWrite("\n");
      return false;
    }
    if (tib.length == 0) {
      VM.sysWrite("validRef: TIB length zero, ref = "); VM.sysWrite(ref);
      VM.sysWrite(" tib = ");VM.sysWrite(tibAddr);
      VM.sysWrite("\n");
      return false;
    }

    ObjectReference type = ObjectReference.fromObject(tib[0]);
    if (!validType(type)) {
      VM.sysWrite("validRef: invalid TYPE, ref = "); VM.sysWrite(ref);
      VM.sysWrite(" tib = ");
      VM.sysWrite(VM_Magic.objectAsAddress(tib));
      VM.sysWrite(" type = ");VM.sysWrite(type); VM.sysWrite("\n");
      return false;
    }
    return true;
  }  // validRef

  @Uninterruptible
  public static boolean mappedVMRef(ObjectReference ref) { 
    return Space.isMappedObject(ref) && Mmapper.objectIsMapped(ref);
  }

  @Uninterruptible
  public static void dumpRef(ObjectReference ref) { 
    VM.sysWrite("REF=");
    if (ref.isNull()) {
      VM.sysWrite("NULL\n");
      return;
    }
    VM.sysWrite(ref);
    if (!mappedVMRef(ref)) {
      VM.sysWrite(" (REF OUTSIDE OF HEAP OR NOT MAPPED)\n");
      return;
    }
    VM_ObjectModel.dumpHeader(ref);
    ObjectReference tib = ObjectReference.fromObject(VM_ObjectModel.getTIB(ref));
    if (!MM_Interface.mightBeTIB(tib)) {
      VM.sysWrite(" (INVALID TIB: CLASS NOT ACCESSIBLE)\n");
      return;
    }
    VM_Type type = VM_Magic.getObjectType(ref.toObject());
    ObjectReference itype = ObjectReference.fromObject(type);
    VM.sysWrite(" TYPE=");
    VM.sysWrite(itype);
    if (!validType(itype)) {
      VM.sysWrite(" (INVALID TYPE: CLASS NOT ACCESSIBLE)\n");
      return;
    }
    VM.sysWrite(" CLASS=");
    VM.sysWrite(type.getDescriptor());
    VM.sysWrite("\n");
  }

  public static boolean addrInBootImage(Address addr) {
    return (addr.GE(BOOT_IMAGE_DATA_START) && addr.LT(BOOT_IMAGE_DATA_END)) ||
        (addr.GE(BOOT_IMAGE_CODE_START) && addr.LT(BOOT_IMAGE_CODE_END));
  }
} 
