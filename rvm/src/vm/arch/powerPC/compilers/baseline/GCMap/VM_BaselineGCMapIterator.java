/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_GCMapIterator;
import com.ibm.JikesRVM.classloader.*;

/**
 * Iterator for stack frame  built by the Baseline compiler
 * An Instance of this class will iterate through a particular 
 * reference map of a method returning the offsets of any refereces
 * that are part of the input parameters, local variables, and 
 * java stack for the stack frame.
 *
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Derek Lieber
 */
public final class VM_BaselineGCMapIterator extends VM_GCMapIterator 
  implements VM_BaselineConstants,
             VM_Uninterruptible  {

  // Iterator state for mapping any stackframe.
  //
  private   int              mapOffset; // current offset in current map
  private   int              mapId;     // id of current map out of all maps
  private   VM_ReferenceMaps maps;      // set of maps for this method

  // Additional iterator state for mapping dynamic bridge stackframes.
  //
  private VM_DynamicLink dynamicLink;                    // place to keep info returned by VM_CompiledMethod.getDynamicLink
  private VM_Method      bridgeTarget;                   // method to be invoked via dynamic bridge (null: current frame is not a dynamic bridge)
  private VM_NormalMethod currentMethod;                  // method for the frame
  private VM_TypeReference[]      bridgeParameterTypes;           // parameter types passed by that method
  private boolean        bridgeParameterMappingRequired; // have all bridge parameters been mapped yet?
  private boolean        bridgeRegistersLocationUpdated; // have the register location been updated
  private int            bridgeParameterInitialIndex;    // first parameter to be mapped (-1 == "this")
  private int            bridgeParameterIndex;           // current parameter being mapped (-1 == "this")
  private int            bridgeRegisterIndex;            // gpr register it lives in
  private VM_Address     bridgeRegisterLocation;         // memory address at which that register was saved


  //
  // Remember the location array for registers. This array needs to be updated
  // with the location of any saved registers.
  // This information is not used by this iterator but must be updated for the
  // other types of iterators (ones for the quick and opt compiler built frames)
  // The locations are kept as addresses within the stack.
  //
  public VM_BaselineGCMapIterator(VM_WordArray registerLocations) {
    this.registerLocations = registerLocations; // (in superclass)
    dynamicLink  = new VM_DynamicLink();
  }

  //
  // Set the iterator to scan the map at the machine instruction offset provided.
  // The iterator is positioned to the beginning of the map
  //
  //   method - identifies the method and class
  //   instruction offset - identifies the map to be scanned.
  //   fp  - identifies a specific occurrance of this method and
  //         allows for processing instance specific information
  //         i.e JSR return address values
  //
  //  NOTE: An iterator may be reused to scan a different method and map.
  //
  public void setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, VM_Address fp) {
    currentMethod = (VM_NormalMethod)compiledMethod.getMethod();

    // setup superclass
    //
    framePtr = fp;
      
    // setup stackframe mapping
    //
    maps      = ((VM_BaselineCompiledMethod)compiledMethod).referenceMaps;
    mapId     = maps.locateGCPoint(instructionOffset, currentMethod);
    mapOffset = 0;
    if (mapId < 0) {
      // lock the jsr lock to serialize jsr processing
      VM_ReferenceMaps.jsrLock.lock();
      maps.setupJSRSubroutineMap( framePtr, mapId, compiledMethod);
    }
    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_BaselineGCMapIterator setupIterator mapId = ");
      VM.sysWrite(mapId);
      VM.sysWrite(" for ");
      VM.sysWrite(currentMethod);
      VM.sysWrite(".\n");
    }
      
    // setup dynamic bridge mapping
    //
    bridgeTarget                   = null;
    bridgeParameterTypes           = null;
    bridgeParameterMappingRequired = false;
    bridgeRegistersLocationUpdated = false;
    bridgeParameterIndex           = 0;
    bridgeRegisterIndex            = 0;
    bridgeRegisterLocation         = VM_Address.zero();

    if (currentMethod.getDeclaringClass().isDynamicBridge()) {
      fp                       = VM_Magic.getCallerFramePointer(fp);
      VM_Address        ip                       = VM_Magic.getNextInstructionAddress(fp);
      int               callingCompiledMethodId  = VM_Magic.getCompiledMethodID(fp);
      VM_CompiledMethod callingCompiledMethod    = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      int               callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

      callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);
      bridgeTarget                = dynamicLink.methodRef().getResolvedMember();
      bridgeParameterInitialIndex = dynamicLink.isInvokedWithImplicitThisParameter() ? -1 : 0;
      bridgeParameterTypes        = bridgeTarget.getParameterTypes();
    }
        
    reset();
  }
  
  // Reset iteration to initial state.
  // This allows a map to be scanned multiple times
  //
  public void reset() {

    mapOffset = 0;

    if (bridgeTarget != null) {
      // point to first saved gpr
      bridgeParameterMappingRequired = true;
      bridgeParameterIndex   = bridgeParameterInitialIndex;
      bridgeRegisterIndex    = FIRST_VOLATILE_GPR;
      bridgeRegisterLocation = VM_Magic.getMemoryAddress(framePtr);
      bridgeRegisterLocation = bridgeRegisterLocation.sub(8 * (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) +
                                                          4 * (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1));
    }
  }

  // Get location of next reference.
  // A zero return indicates that no more references exist.
  //
  public VM_Address getNextReferenceAddress() {

    if (mapId < 0)
      mapOffset = maps.getNextJSRRef(mapOffset);
    else
      mapOffset = maps.getNextRef(mapOffset, mapId);
    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = ");
      VM.sysWrite(mapOffset);
      VM.sysWrite(".\n");
      if (mapId < 0) 
        VM.sysWrite("Offset is a JSR return address ie internal pointer.\n");
    }

    if (mapOffset != 0) {
      return (framePtr.add(mapOffset));

    } else if (bridgeParameterMappingRequired) {

      if (VM.TraceStkMaps) {
        VM.sysWrite("getNextReferenceAddress: bridgeTarget="); VM.sysWrite(bridgeTarget); VM.sysWrite("\n");
      }
      if (!bridgeRegistersLocationUpdated) {
        // point registerLocations[] to our callers stackframe
        //
        VM_Address location = framePtr.add(VM_Compiler.getFrameSize(currentMethod));
        location = location.sub((LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * BYTES_IN_DOUBLE); 
        // skip non-volatile and volatile fprs
        for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i) {
          location = location.sub(BYTES_IN_ADDRESS);
          registerLocations.set(i, location);
        }

        bridgeRegistersLocationUpdated = true;
      }

      // handle implicit "this" parameter, if any
      //
      if (bridgeParameterIndex == -1) {
        bridgeParameterIndex   += 1;
        bridgeRegisterIndex    += 1;
        bridgeRegisterLocation = bridgeRegisterLocation.add(4);
        return bridgeRegisterLocation.sub(4);
      }
         
      // now the remaining parameters
      //
      while (true) {
        if (bridgeParameterIndex == bridgeParameterTypes.length || bridgeRegisterIndex > LAST_VOLATILE_GPR) {
          bridgeParameterMappingRequired = false;
          break;
        }
        VM_TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];
        if (bridgeParameterType.isReferenceType()) {
          bridgeRegisterIndex    += 1;
          bridgeRegisterLocation = bridgeRegisterLocation.add(4);
          return bridgeRegisterLocation.sub(4);
        } else if (bridgeParameterType.isLongType()) {
          bridgeRegisterIndex    += 2;
          bridgeRegisterLocation = bridgeRegisterLocation.add(8);
        } else if (bridgeParameterType.isDoubleType() || bridgeParameterType.isFloatType()) {
          // no gpr's used
        } else {
          // boolean, byte, char, short, int
          bridgeRegisterIndex    += 1;
          bridgeRegisterLocation = bridgeRegisterLocation.add(4);
        }
      }
    }
      
    return VM_Address.zero();
  }

  //
  // Gets the location of the next return address
  // after the current position.
  //  a zero return indicates that no more references exist
  //
  public VM_Address getNextReturnAddressAddress() {

    if (mapId >= 0) {
      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
        VM.sysWrite(mapId);
        VM.sysWrite(".\n");
      }
      return VM_Address.zero();
    }
    mapOffset = maps.getNextJSRReturnAddr(mapOffset);
    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset = ");
      VM.sysWrite(mapOffset);
      VM.sysWrite(".\n");
    }
    return (mapOffset == 0) ? VM_Address.zero() : framePtr.add(mapOffset);
  }

  // cleanup pointers - used with method maps to release data structures
  //    early ... they may be in temporary storage ie storage only used
  //    during garbage collection
  //
  public void cleanupPointers() {
    maps.cleanupPointers();
    maps = null;
    if (mapId < 0) 
      VM_ReferenceMaps.jsrLock.unlock();
    bridgeTarget         = null;
    bridgeParameterTypes = null;
  }
       
  public int getType() {
    return VM_CompiledMethod.BASELINE;
  }

  // For debugging (used with checkRefMap)
  //
  public int getStackDepth() {
    return maps.getStackDepth(mapId);
  }

}
