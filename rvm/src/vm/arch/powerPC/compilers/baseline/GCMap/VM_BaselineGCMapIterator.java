/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_GCMapIterator;
import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

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
             Uninterruptible  {

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
  private boolean        finishedWithRegularMap;         // have we processed all the values in the regular map yet?
  private int            bridgeParameterInitialIndex;    // first parameter to be mapped (-1 == "this")
  private int            bridgeParameterIndex;           // current parameter being mapped (-1 == "this")
  private int            bridgeRegisterIndex;            // gpr register it lives in
  private Address     bridgeRegisterLocation;         // memory address at which that register was saved
  private Address     bridgeSpilledParamLocation;     // current spilled param location

  //
  // Remember the location array for registers. This array needs to be updated
  // with the location of any saved registers.
  // This information is not used by this iterator but must be updated for the
  // other types of iterators (ones for the quick and opt compiler built frames)
  // The locations are kept as addresses within the stack.
  //
  public VM_BaselineGCMapIterator(WordArray registerLocations) {
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
  public void setupIterator(VM_CompiledMethod compiledMethod, Offset instructionOffset, Address fp) {
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
    bridgeRegisterLocation         = Address.zero();
    bridgeSpilledParamLocation     = Address.zero();

    if (currentMethod.getDeclaringClass().isDynamicBridge()) {
      fp                       = VM_Magic.getCallerFramePointer(fp);
      Address        ip                       = VM_Magic.getNextInstructionAddress(fp);
      int               callingCompiledMethodId  = VM_Magic.getCompiledMethodID(fp);
      VM_CompiledMethod callingCompiledMethod    = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      Offset            callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

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
    finishedWithRegularMap = false;

    if (bridgeTarget != null) {
      // point to first saved gpr
      bridgeParameterMappingRequired = true;
      bridgeParameterIndex   = bridgeParameterInitialIndex;
      bridgeRegisterIndex    = FIRST_VOLATILE_GPR;
      bridgeRegisterLocation = framePtr.loadAddress();
      bridgeRegisterLocation = bridgeRegisterLocation.sub(BYTES_IN_DOUBLE * (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) +
                                                          BYTES_IN_ADDRESS * (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1));

      // get to my caller's frameptr and then walk up to the spill area
      Address callersFP = VM_Magic.getCallerFramePointer(framePtr);
      bridgeSpilledParamLocation     = callersFP.add(STACKFRAME_HEADER_SIZE);
    }
  }

  // Get location of next reference.
  // A zero return indicates that no more references exist.
  //
  public Address getNextReferenceAddress() {

    if (!finishedWithRegularMap) {
      if (mapId < 0) {
        mapOffset = maps.getNextJSRRef(mapOffset);
      } else {
        mapOffset = maps.getNextRef(mapOffset, mapId);
      }

      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = ");
        VM.sysWrite(mapOffset);
        VM.sysWrite(".\n");
        if (mapId < 0) {
          VM.sysWrite("Offset is a JSR return address ie internal pointer.\n");
        }
      }

      if (mapOffset != 0) {
        return (framePtr.add(mapOffset));
      } else {
        // remember that we are done with the map for future calls, and then
        //   drop down to the code below 
        finishedWithRegularMap = true;
      }
    }

    if (bridgeParameterMappingRequired) {
      if (!bridgeRegistersLocationUpdated) {
        // point registerLocations[] to our callers stackframe
        //
        Address location = framePtr.add(VM_Compiler.getFrameSize(currentMethod));
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
        bridgeRegisterLocation = bridgeRegisterLocation.add(BYTES_IN_ADDRESS);

        if (VM.TraceStkMaps) {
          VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset, ");
          VM.sysWrite("  this, bridge, returning: "); 
          VM.sysWrite(bridgeRegisterLocation.sub(BYTES_IN_ADDRESS)); 
          VM.sysWrite("\n");
        }
        return bridgeRegisterLocation.sub(BYTES_IN_ADDRESS);
      }
         
      // now the remaining parameters
      //
      while (bridgeParameterIndex < bridgeParameterTypes.length) {
        VM_TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];

        // are we still processing the regs?
        if (bridgeRegisterIndex <= LAST_VOLATILE_GPR) {

          // update the bridgeRegisterLocation (based on type) and return a value if it is a ref
          if (bridgeParameterType.isReferenceType()) {
            bridgeRegisterLocation = bridgeRegisterLocation.add(BYTES_IN_ADDRESS);
            bridgeRegisterIndex    += 1;

            if (VM.TraceStkMaps) {
              VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset, ");
              VM.sysWrite("  parm: "); 
              VM.sysWrite(bridgeRegisterLocation.sub(BYTES_IN_ADDRESS)); 
              VM.sysWrite("\n");
            }
            return bridgeRegisterLocation.sub(BYTES_IN_ADDRESS);
          } else if (bridgeParameterType.isLongType()) {
            bridgeRegisterIndex += VM.BuildFor64Addr ? 1 : 2;
            bridgeRegisterLocation = bridgeRegisterLocation.add(BYTES_IN_LONG);
          } else if (bridgeParameterType.isDoubleType() || bridgeParameterType.isFloatType()) {
            // nothing to do, these are not stored in gprs
          } else {
            // boolean, byte, char, short, int
            bridgeRegisterIndex    += 1;
            bridgeRegisterLocation = bridgeRegisterLocation.add(BYTES_IN_ADDRESS);
          }
        } else {  // now process the register spill area for the remain params
          // no need to update BridgeRegisterIndex anymore, it isn't used and is already
          //  big enough (> LAST_VOLATILE_GPR) to ensure we'll live in this else code in the future
          if (bridgeParameterType.isReferenceType()) {
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.add(BYTES_IN_ADDRESS);

            if (VM.TraceStkMaps) {
              VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset, dynamic link spilled parameter, returning: ");
              VM.sysWrite(bridgeSpilledParamLocation.sub(BYTES_IN_ADDRESS));    
              VM.sysWrite(".\n");
            }
            return bridgeSpilledParamLocation.sub(BYTES_IN_ADDRESS);
          } else if (bridgeParameterType.isLongType()) {
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.add(BYTES_IN_LONG);
          } else if (bridgeParameterType.isDoubleType()) {
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.add(BYTES_IN_DOUBLE);
          } else if (bridgeParameterType.isFloatType()) {
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.add(BYTES_IN_FLOAT);
          } else {
            // boolean, byte, char, short, int
            bridgeSpilledParamLocation = bridgeSpilledParamLocation.add(BYTES_IN_ADDRESS);
          }
        }
      }
    }
    return Address.zero();
  }

  //
  // Gets the location of the next return address
  // after the current position.
  //  a zero return indicates that no more references exist
  //
  public Address getNextReturnAddressAddress() {

    if (mapId >= 0) {
      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
        VM.sysWrite(mapId);
        VM.sysWrite(".\n");
      }
      return Address.zero();
    }
    mapOffset = maps.getNextJSRReturnAddr(mapOffset);
    if (VM.TraceStkMaps) {
      VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset = ");
      VM.sysWrite(mapOffset);
      VM.sysWrite(".\n");
    }
    return (mapOffset == 0) ? Address.zero() : framePtr.add(mapOffset);
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
