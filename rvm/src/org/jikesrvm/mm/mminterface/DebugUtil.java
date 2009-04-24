/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.BootRecord;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.Mmapper;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Common debugging utility functions used by various garbage collectors
 */
@Uninterruptible
public class DebugUtil implements org.mmtk.utility.Constants, org.jikesrvm.Constants {

  private static TIB tibForArrayType;
  private static TIB tibForClassType;
  private static TIB tibForPrimitiveType;

  @Interruptible
  static void boot(BootRecord theBootRecord) {
    // get addresses of TIBs for RVMArray & RVMClass used for testing Type ptrs
    RVMType t = RVMArray.IntArray;
    tibForArrayType = ObjectModel.getTIB(t);
    tibForPrimitiveType = ObjectModel.getTIB(RVMType.IntType);
    t = Magic.getObjectType(BootRecord.the_boot_record);
    tibForClassType = ObjectModel.getTIB(t);
  }

  /**
   * Check if an address appears to point to an instance of RVMType
   *
   * @param typeAddress the address to check
   */
  @Uninterruptible
  public static boolean validType(ObjectReference typeAddress) {
    if (!Space.isMappedObject(typeAddress)) {
      return false;  // type address is outside of heap
    }

    // check if types tib is one of three possible values
    TIB typeTib = ObjectModel.getTIB(typeAddress);
    return ((typeTib == tibForClassType) || (typeTib == tibForArrayType) || (typeTib == tibForPrimitiveType));
  }

  /**
   * Dump all threads & their stacks starting at the frame identified
   * by the threads saved contextRegisters (ip & fp fields).
   */
  @Uninterruptible
  public static void dumpAllThreadStacks() {
    RVMThread.dumpVirtualMachine();
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
      VM.sysWrite(ref);
      VM.sysWrite("\n");
      Space.printVMMap();
      return false;
    }
    if (MemoryManagerConstants.MOVES_OBJECTS) {
      /*
      TODO: Work out how to check if forwarded
      if (Plan.isForwardedOrBeingForwarded(ref)) {
        // TODO: actually follow forwarding pointer
        // (need to bound recursion when things are broken!!)
        return true;
      }
      */
    }

    TIB tib = ObjectModel.getTIB(ref);
    Address tibAddr = Magic.objectAsAddress(tib);
    if (!Space.isMappedObject(ObjectReference.fromObject(tib))) {
      VM.sysWrite("validRef: TIB outside heap, ref = ");
      VM.sysWrite(ref);
      VM.sysWrite(" tib = ");
      VM.sysWrite(tibAddr);
      VM.sysWrite("\n");
      ObjectModel.dumpHeader(ref);
      return false;
    }
    if (tibAddr.isZero()) {
      VM.sysWrite("validRef: TIB is Zero! ");
      VM.sysWrite(ref);
      VM.sysWrite("\n");
      ObjectModel.dumpHeader(ref);
      return false;
    }
    if (tib.length() == 0) {
      VM.sysWrite("validRef: TIB length zero, ref = ");
      VM.sysWrite(ref);
      VM.sysWrite(" tib = ");
      VM.sysWrite(tibAddr);
      VM.sysWrite("\n");
      ObjectModel.dumpHeader(ref);
      return false;
    }

    ObjectReference type = ObjectReference.fromObject(tib.getType());
    if (!validType(type)) {
      VM.sysWrite("validRef: invalid TYPE, ref = ");
      VM.sysWrite(ref);
      VM.sysWrite(" tib = ");
      VM.sysWrite(Magic.objectAsAddress(tib));
      VM.sysWrite(" type = ");
      VM.sysWrite(type);
      VM.sysWrite("\n");
      ObjectModel.dumpHeader(ref);
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
    ObjectModel.dumpHeader(ref);
    ObjectReference tib = ObjectReference.fromObject(ObjectModel.getTIB(ref));
    if (!MemoryManager.mightBeTIB(tib)) {
      VM.sysWrite(" (INVALID TIB: CLASS NOT ACCESSIBLE)\n");
      return;
    }
    RVMType type = Magic.getObjectType(ref.toObject());
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
