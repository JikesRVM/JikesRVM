/* -*-coding: iso-8859-1 -*-
 *
 * (C) Copyright IBM Corp. 2001
 *
 * $Id$
 */
package com.ibm.JikesRVM.memoryManagers.mmInterface;

import org.mmtk.policy.Space;
import org.mmtk.utility.heap.LazyMmapper;
import org.mmtk.utility.Constants;
import org.mmtk.vm.Memory;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;

/**
 * Common debugging utility functions used by various garbage collectors
 *
 * @author Stephen Smith
 */  
public class DebugUtil implements VM_Constants, Constants, Uninterruptible {

  private static Object[] tibForArrayType;
  private static Object[] tibForClassType;
  private static Object[] tibForPrimitiveType;

  static final void boot (VM_BootRecord theBootRecord)
    throws InterruptiblePragma {
    // get addresses of TIBs for VM_Array & VM_Class used for testing Type ptrs
    VM_Type t = VM_Array.getPrimitiveArrayType(10);
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
  public static boolean validType(ObjectReference typeAddress)
    throws UninterruptiblePragma {
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
  public static void dumpAllThreadStacks() throws UninterruptiblePragma {
      Address ip, fp;
      VM_Thread  t;
      VM_Scheduler.trace("\ndumpAllThreadStacks",
                         "dumping stacks for all threads");
      for (int i=0; i<VM_Scheduler.threads.length; i++) {
          t = VM_Scheduler.threads[i];
          if (t == null) continue;
          VM.sysWrite("\n Thread "); t.dump(); VM.sysWrite("\n");
          // start stack dump using fp & ip in threads saved context registers
          ip = t.contextRegisters.getInnermostInstructionAddress();
          fp = t.contextRegisters.getInnermostFramePointer();
          VM_Scheduler.dumpStack(ip,fp);
      }
      VM.sysWrite("\ndumpAllThreadStacks: end of thread stacks\n\n");
  }  // dumpAllThreadStacks

  /**
   * Check if a ref, its tib pointer & type pointer are all in the heap
   */
  public static boolean validObject(Object ref)
    throws UninterruptiblePragma {
      return validRef(ObjectReference.fromObject(ref));
  }

  public static boolean validRef(ObjectReference ref)
    throws UninterruptiblePragma {

    if (ref.isNull()) return true;
    if (!Space.isMappedObject(ref)) {
      VM.sysWrite("validRef: REF outside heap, ref = ");
      VM.sysWrite(ref); VM.sysWrite("\n");
      Space.printVMMap();
      return false;
    }
    if (MM_Interface.MOVES_OBJECTS) {
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
    if (!Space.isMappedObject(ref)) {
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

  public static boolean mappedVMRef(ObjectReference ref)
    throws UninterruptiblePragma {
    return Space.isMappedObject(ref) && LazyMmapper.objectIsMapped(ref);
  }

  public static void dumpRef(ObjectReference ref) throws UninterruptiblePragma {
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
    return (addr.GE(BOOT_IMAGE_START) && addr.LT(BOOT_IMAGE_END));
  }
} 
