/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_GCMapIterator;

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
 */
public final class VM_BaselineGCMapIterator extends VM_GCMapIterator 
  implements VM_BaselineConstants,
             VM_Uninterruptible {
  private static final boolean TRACE_ALL = false;
  private static final boolean TRACE_DL  = false; // dynamic link frames

  //-------------//
  // Constructor //
  //-------------//

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

  //-----------//
  // Interface //
  //-----------//

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
    currentMethod = (VM_BaselineCompiledMethod)compiledMethod;
      
    // setup superclass
    //
    framePtr = fp;
      
    // setup stackframe mapping
    //
    maps      = ((VM_BaselineCompiledMethod)compiledMethod).referenceMaps;
    mapId     = maps.locateGCPoint(instructionOffset, currentMethod.getMethod());
    mapOffset = 0;
    if (mapId < 0) {
      // lock the jsr lock to serialize jsr processing
      VM_ReferenceMaps.jsrLock.lock();
      maps.setupJSRSubroutineMap(framePtr, mapId, compiledMethod);
    }
    if (VM.TraceStkMaps || TRACE_ALL ) {
      VM.sysWrite("VM_BaselineGCMapIterator setupIterator mapId = ");
      VM.sysWrite(mapId);
      VM.sysWrite(" for ");
      VM.sysWrite(compiledMethod.getMethod());
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
    bridgeSpilledParamLocation     = VM_Address.zero();
    
    if (currentMethod.getMethod().getDeclaringClass().isDynamicBridge()) {
      VM_Address        ip                       = VM_Magic.getReturnAddress(fp);
                        fp                       = VM_Magic.getCallerFramePointer(fp);
      int               callingCompiledMethodId  = VM_Magic.getCompiledMethodID(fp);
      VM_CompiledMethod callingCompiledMethod    = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      int               callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

      callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);
      bridgeTarget                    = dynamicLink.methodRef().getResolvedMember();
      bridgeParameterTypes            = bridgeTarget.getParameterTypes();
      if (dynamicLink.isInvokedWithImplicitThisParameter()) {
        bridgeParameterInitialIndex     = -1;
        bridgeSpilledParamInitialOffset =  8; // this + return addr
      } else {  
        bridgeParameterInitialIndex     =  0;
        bridgeSpilledParamInitialOffset =  4; // return addr
      }
      bridgeSpilledParamInitialOffset  += (4 * bridgeTarget.getParameterWords());
      if (callingCompiledMethod.getCompilerType() == VM_CompiledMethod.BASELINE) {
        bridgeSpilledParameterMappingRequired = false;
      } else {
        bridgeSpilledParameterMappingRequired = true;
      }
    }
        
    reset();
  }
  
  // Reset iteration to initial state.
  // This allows a map to be scanned multiple times
  //
  public void reset() {
    mapOffset = 0;
    finishedWithRegularMap = false;

    // setup map to report EBX if this method is holding the base of counter array in it.
    counterArrayBase                 = currentMethod.hasCounterArray();

    if (bridgeTarget != null) {
      bridgeParameterMappingRequired = true;
      bridgeParameterIndex           = bridgeParameterInitialIndex;
      bridgeRegisterIndex            = 0;
      bridgeRegisterLocation         = framePtr.add(STACKFRAME_FIRST_PARAMETER_OFFSET); // top of frame
      bridgeSpilledParamLocation     = framePtr.add(bridgeSpilledParamInitialOffset);
    }
  }

  // Get location of next reference.
  // A zero return indicates that no more references exist.
  //
  public VM_Address getNextReferenceAddress() {
    if (!finishedWithRegularMap) {
      if (counterArrayBase) {
        counterArrayBase = false;
        return registerLocations.get(EBX).toAddress();
      }
      if (mapId < 0) {
        mapOffset = maps.getNextJSRRef(mapOffset);
      } else {
        mapOffset = maps.getNextRef(mapOffset, mapId);
      }
      
      if (mapOffset != 0) {
        if (VM.TraceStkMaps || TRACE_ALL) {
          VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = ");
          VM.sysWriteHex(mapOffset);
          VM.sysWrite(".\n");
          VM.sysWrite("Reference is ");
        }
        if (bridgeParameterMappingRequired) {
          if (VM.TraceStkMaps || TRACE_ALL) {
            VM.sysWriteHex ( VM_Magic.getMemoryInt ( framePtr.add(mapOffset - BRIDGE_FRAME_EXTRA_SIZE) ) );
            VM.sysWrite(".\n");
            if (mapId < 0) {
              VM.sysWrite("Offset is a JSR return address ie internal pointer.\n");
            }
          }
          
          // TODO  clean this
          return (framePtr.add(mapOffset - BRIDGE_FRAME_EXTRA_SIZE ));
        }
        else {
          if (VM.TraceStkMaps || TRACE_ALL) {
            VM.sysWriteHex ( VM_Magic.getMemoryInt ( framePtr.add(mapOffset) ) );
            VM.sysWrite(".\n");
            if (mapId < 0) {
              VM.sysWrite("Offset is a JSR return address ie internal pointer.\n");
            }
          }
          return (framePtr.add(mapOffset) );
        }
      } else {
        // remember that we are done with the map for future calls, and then
        //   drop down to the code below 
        finishedWithRegularMap = true;
      }
    }

    if (bridgeParameterMappingRequired) {
      if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
        VM.sysWrite("getNextReferenceAddress: bridgeTarget="); VM.sysWrite(bridgeTarget); VM.sysWrite("\n");
      }         

      if (!bridgeRegistersLocationUpdated) {
        // point registerLocations[] to our callers stackframe
        //
        registerLocations.set(JTOC, framePtr.add(JTOC_SAVE_OFFSET));
        registerLocations.set(T0, framePtr.add(T0_SAVE_OFFSET));
        registerLocations.set(T1, framePtr.add(T1_SAVE_OFFSET));
        registerLocations.set(EBX, framePtr.add(EBX_SAVE_OFFSET));
        
        bridgeRegistersLocationUpdated = true;
      }

      // handle implicit "this" parameter, if any
      //
      if (bridgeParameterIndex == -1) {
        bridgeParameterIndex       += 1;
        bridgeRegisterIndex        += 1;
        bridgeRegisterLocation     = bridgeRegisterLocation.sub(4);
        bridgeSpilledParamLocation = bridgeSpilledParamLocation.sub(4);
        
        if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
          VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR this ");
          VM.sysWrite(bridgeRegisterLocation.add(4));
          VM.sysWrite(".\n");
        }

        return bridgeRegisterLocation.add(4);
      }
         
      // now the remaining parameters
      //
      while(bridgeParameterIndex < bridgeParameterTypes.length) {
        VM_TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];
        
        if (bridgeParameterType.isReferenceType()) {
          bridgeRegisterIndex        += 1;
          bridgeRegisterLocation     = bridgeRegisterLocation.sub(4);
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.sub(4);
          
          if (bridgeRegisterIndex <= NUM_PARAMETER_GPRS) {
            if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
              VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR parameter ");
              VM.sysWrite(bridgeRegisterLocation.add(4));
              VM.sysWrite(".\n");
            }
            return bridgeRegisterLocation.add(4);
          } else {
            if (bridgeSpilledParameterMappingRequired) {
              if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
                VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = dynamic link spilled parameter ");
                VM.sysWrite(bridgeSpilledParamLocation.add(4));
                VM.sysWrite(".\n");
              }
              return bridgeSpilledParamLocation.add(4);
            } else {
              break;
            }
          }
        } else if (bridgeParameterType.isLongType()) {
          bridgeRegisterIndex        += 2;
          bridgeRegisterLocation     = bridgeRegisterLocation.sub(8);
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.sub(8);
        } else if (bridgeParameterType.isDoubleType()) {
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.sub(8);
        } else if (bridgeParameterType.isFloatType()) {
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.sub(4);
        } else { 
          // boolean, byte, char, short, int
          bridgeRegisterIndex        += 1;
          bridgeRegisterLocation     = bridgeRegisterLocation.sub(4);
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.sub(4);
        }
      }
    } else {
      // point registerLocations[] to our callers stackframe
      //
      registerLocations.set(JTOC, framePtr.add(JTOC_SAVE_OFFSET));
      registerLocations.set(EBX, framePtr.add(EBX_SAVE_OFFSET));
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
      if (VM.TraceStkMaps || TRACE_ALL) {
        VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
        VM.sysWrite(mapId);
        VM.sysWrite(".\n");
      }
      return VM_Address.zero();
    }
    mapOffset = maps.getNextJSRReturnAddr(mapOffset);
    if (VM.TraceStkMaps || TRACE_ALL) {
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

  // Iterator state for mapping any stackframe.
  //
  private VM_BaselineCompiledMethod  currentMethod;      // compiled method for the frame
  private int              mapOffset; // current offset in current map
  private int              mapId;     // id of current map out of all maps
  private VM_ReferenceMaps maps;      // set of maps for this method
  private boolean counterArrayBase;   // have we reported the base ptr of the edge counter array?

  // Additional iterator state for mapping dynamic bridge stackframes.
  //
  private VM_DynamicLink dynamicLink;                    // place to keep info returned by VM_CompiledMethod.getDynamicLink
  private VM_Method bridgeTarget;                        // method to be invoked via dynamic bridge (null: current frame is not a dynamic bridge)
  private VM_TypeReference[]      bridgeParameterTypes;           // parameter types passed by that method
  private boolean        bridgeParameterMappingRequired; // have all bridge parameters been mapped yet?
  private boolean        bridgeSpilledParameterMappingRequired; // do we need to map spilled params (baseline compiler = no, opt = yes)
  private boolean        bridgeRegistersLocationUpdated; // have the register location been updated
  private boolean        finishedWithRegularMap;         // have we processed all the values in the regular map yet?
  private int            bridgeParameterInitialIndex;    // first parameter to be mapped (-1 == "this")
  private int            bridgeParameterIndex;           // current parameter being mapped (-1 == "this")
  private int            bridgeRegisterIndex;            // gpr register it lives in
  private VM_Address     bridgeRegisterLocation;         // memory address at which that register was saved
  private VM_Address     bridgeSpilledParamLocation;     // current spilled param location
  private int            bridgeSpilledParamInitialOffset;// starting offset to stack location for param0
}
