/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.memoryManagers.JMTk.Statistics;
import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;

/**
 * Class that supports scanning Objects or Arrays for references
 * during Collection and processing those references
 *
 * @author Stephen Smith
 */  
public class ScanObject implements VM_Constants, Constants {

  /**
   * Scan a object, processing each pointer field encountered.  The
   * object is not known to be a root object.
   *
   * @param object The object to be scanned.
   */
  public static void scan(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    scan(object, false, null, true);
  }

  /**
   * Scan a root object, processing each pointer field encountered.
   *
   * @param object The root object to be scanned.
   */
  public static void rootScan(Object objRef)
    throws VM_PragmaUninterruptible, VM_PragmaNoInline {
    scan(VM_Magic.objectAsAddress(objRef), true, null, true);
  }

  /**
   * Enumerate the pointers in an object, calling back to a given plan
   * for each pointer encountered. <i>NOTE</i> that only the "real"
   * pointer fields are enumerated, not the TIB.
   *
   * @param object The object to be scanned.
   * @param plan The plan with respect to which the callback should be made.
   */
  public static void enumeratePointers(VM_Address object, Plan plan) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    scan(object, false, plan, false);
  }

  /**
   * Scans an object or array for internal object references and
   * processes those references (calls processPtrField)
   *
   * @param objRef  reference for object to be scanned (as int)
   */
  private static void scan(VM_Address objRef, boolean root, Plan plan,
			   boolean trace)
    throws VM_PragmaUninterruptible, VM_PragmaInline {

    if (VM.VerifyAssertions) VM._assert(!objRef.isZero());

    // First process the TIB to relocate it.
    // Necessary only if the allocator/collector moves objects
    // and the object model is actually storing the TIB as a pointer.
    // 
    if (MM_Interface.MOVES_OBJECTS) 
      VM_ObjectModel.gcProcessTIB(objRef, root);

    Object obj = VM_Magic.addressAsObject(objRef);
    Object[] tib = VM_ObjectModel.getTIB(obj);
    if (VM.VerifyAssertions) {
      if (tib == null || VM_ObjectModel.getObjectType(tib) != VM_Type.JavaLangObjectArrayType) {
	VM.sysWriteln("ScanObject: objRef = ", objRef, "   tib = ", VM_Magic.objectAsAddress(tib));
	VM.sysWriteln("            tib's type is not Object[]");
        VM._assert(false);
      }
    }
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    if (VM.VerifyAssertions) {
      if (type == null) {
        VM.sysWriteln("ScanObject: null type for objRef = ", objRef);
        VM._assert(false);
      }
    }
    if (type.isClassType()) {
      int[] referenceOffsets = type.asClass().getReferenceOffsets();
      for(int i = 0, n=referenceOffsets.length; i < n; i++) {
	if (trace)
	  MM_Interface.processPtrField(objRef.add(referenceOffsets[i]), root);
	else
	  VM_Interface.enumeratePtrLoc(objRef.add(referenceOffsets[i]), plan);
      }
      Statistics.profileScan(obj, 4 * referenceOffsets.length, tib);
    }
    else {
      if (VM.VerifyAssertions) VM._assert(type.isArrayType());
      VM_Type elementType = type.asArray().getElementType();
      if (elementType.isReferenceType()) {
        int num_elements = VM_Magic.getArrayLength(obj);
        int numBytes = num_elements * WORD_SIZE;
        VM_Address location = objRef;    // for arrays = address of [0] entry
        VM_Address end      = objRef.add(numBytes);
        while ( location.LT(end) ) {
	  if (trace)
	    MM_Interface.processPtrField(location, root);
	  else
	    VM_Interface.enumeratePtrLoc(location, plan);
          location = location.add(WORD_SIZE);  // is this size_of_pointer ?
        }
        Statistics.profileScan(obj, numBytes, tib);
      }
    }
  } 


  public static boolean validateRefs( VM_Address ref, int depth ) 
      throws VM_PragmaUninterruptible, VM_PragmaNoInline {

    VM_Type    type;

    if (ref.isZero()) return true;   // null is always valid

    // First check passed ref, before looking into it for refs
    if ( !Util.validRef (ref) ) {
      VM.sysWrite("ScanObject.validateRefs: Bad Ref = ");
      VM_Memory.dumpMemory(ref, 32, 32 );  // dump 16 words on either side of bad ref
      return false;
    }

    if (depth==0) return true;  //this ref valid, stop depth first scan

    type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
    if ( type.isClassType() ) {
      int[] referenceOffsets = type.asClass().getReferenceOffsets();
      for (int i = 0, n=referenceOffsets.length; i < n; i++) {
        VM_Address iref = VM_Magic.getMemoryAddress(ref.add(referenceOffsets[i]));
        if ( ! validateRefs( iref, depth-1 ) ) {
          VM.sysWrite("Referenced from Object: Ref = ", ref);
          VM.sysWrite("                  At Offset = ");
          VM.sysWriteln(referenceOffsets[i]);
          return false;
        }
      }
    }
    else {
      VM_Type elementType = type.asArray().getElementType();
      if (elementType.isReferenceType()) {
        int num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(ref));
        int location = 0;    // for arrays = offset of [0] entry
        int end      = num_elements * 4;
        for ( location = 0; location < end; location += 4 ) {
          VM_Address iref = VM_Magic.getMemoryAddress(ref.add(location));
          if ( ! validateRefs( iref, depth-1 ) ) {
            VM.sysWrite("Referenced from Array: Ref = ", ref);
            VM.sysWrite("                  At Index = ", location>>2);
            VM.sysWriteln("              Array Length = ", num_elements);
            return false;
          }
        }
      }
    } 
    return true;
  } // validateRefs

}   // ScanObject
