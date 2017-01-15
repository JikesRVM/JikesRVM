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
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.mm.mminterface.GCMapIterator;
import org.jikesrvm.runtime.DynamicLink;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Offset;

/**
 * This class implements the architecture-independent functionality that is
 * used by all currently implemented baseline GC map iterators.
 */
@Uninterruptible
public abstract class AbstractBaselineGCMapIterator extends GCMapIterator {

  /** trace all actions */
  protected static final boolean TRACE_ALL = false;
  /** trace actions relating to dynamic link (= dynamic bridge) frames */
  protected static final boolean TRACE_DL = false;

  // Iterator state for mapping any stackframe.

  /** Current index in current map */
  protected int mapIndex;
  /** id of current map out of all maps */
  protected int mapId;
  /** set of maps for this method */
  protected ReferenceMaps maps;
  /** method for the frame */
  protected NormalMethod currentMethod;
  /** have we processed all the values in the regular map yet? */
  protected boolean finishedWithRegularMap;

  protected int currentNumLocals;

  /** place to keep info returned by CompiledMethod.getDynamicLink */
  protected final DynamicLink dynamicLink;
  /** method to be invoked via dynamic bridge ({@code null}: current frame is not a dynamic bridge) */
  protected MethodReference bridgeTarget;
  /** parameter types passed by that method */
  protected TypeReference[] bridgeParameterTypes;
  /** have all bridge parameters been mapped yet? */
  protected boolean bridgeParameterMappingRequired;
  /** have the register location been updated? */
  protected boolean bridgeRegistersLocationUpdated;
  /** first parameter to be mapped (-1 == "this") */
  protected int bridgeParameterInitialIndex;
  /** current parameter being mapped (-1 == "this") */
  protected int bridgeParameterIndex;
  /** memory address at which that register was saved */
  protected Address bridgeRegisterLocation;
  /** current spilled param location */
  protected Address bridgeSpilledParamLocation;

  /**
   * Note: the location array for registers needs to be remembered. It also needs to
   * be updated with the location of any saved registers. The locations are kept
   * as addresses within the stack. This information is not used by this iterator
   * but must be updated for the other types of iterators (e.g. iterators for
   * the opt compiler built frames).
   *
   * @param registerLocations locations of saved registers
   */
  public AbstractBaselineGCMapIterator(AddressArray registerLocations) {
    super(registerLocations);
    dynamicLink = new DynamicLink();
  }

  /**
   * Cleanup pointers - used with method maps to release data structures early
   * ... they may be in temporary storage i.e. storage only used during garbage
   * collection
   */
  @Override
  public void cleanupPointers() {
    maps.cleanupPointers();
    maps = null;
    if (mapId < 0) {
      ReferenceMaps.jsrLock.unlock();
    }
    bridgeTarget = null;
    bridgeParameterTypes = null;
  }

  @Override
  public final int getType() {
    return CompiledMethod.BASELINE;
  }

  @Override
  public final void reset() {
    resetMapState();
    resetOtherState();
    if (bridgeTarget != null) {
      resetArchitectureIndependentBridgeState();
      resetArchitectureSpecificBridgeSState();
    }
  }

  /**
   * Resets state for processing of reference maps.
   */
  protected final void resetMapState() {
    mapIndex = 0;
    finishedWithRegularMap = false;
  }

  /**
   * Resets non-map state that is architecture-specific.
   * <p>
   * The default implementation does nothing.
   */
  protected void resetOtherState() {

  }

  /**
   * Resets architecture-independent state relating to mapping
   * of dynamic bridge frames.
   */
  protected final void resetArchitectureIndependentBridgeState() {
    bridgeParameterMappingRequired = true;
    bridgeParameterIndex = bridgeParameterInitialIndex;
  }

  /**
   * Resets architecture-specific state relating to mapping
   * of dynamic bridge frames.
   */
  protected abstract void resetArchitectureSpecificBridgeSState();

  protected void traceSetupJSRsubroutineMap(int JSRindex,
      Address nextCallerAddress, Offset nextMachineCodeOffset) {
    VM.sysWriteln("     setupJSRsubroutineMap- nested jsrs end of loop- = ");
    VM.sysWriteln("      next jsraddress offset = ", JSRindex);
    VM.sysWriteln("      next callers address = ", nextCallerAddress);
    VM.sysWriteln("      next machinecodeoffset = ", nextMachineCodeOffset);
    if (nextMachineCodeOffset.sLT(Offset.zero())) {
      VM.sysWriteln("BAD MACHINE CODE OFFSET");
    }
  }

  protected void traceMapIdForGetNextReturnAddressAddress() {
    VM.sysWrite("BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
    VM.sysWrite(mapId);
    VM.sysWriteln(".");
  }

}
