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

import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.runtime.DynamicLink;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This class provides a skeleton for extracting data from dynamic bridge
 * frames for the GC map iterators. It contains the common functionality
 * required by all currently implemented architectures.
 * <p>
 * Subclasses must implement the actual extraction of the data. They
 * may also provide tracing.
 */
@Uninterruptible
public abstract class AbstractBridgeDataExtractor {

  /** the current method */
  protected NormalMethod currentMethod;

  /** place to keep info returned by CompiledMethod.getDynamicLink */
  protected DynamicLink dynamicLink;

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

  protected AbstractBridgeDataExtractor() {
    dynamicLink = new DynamicLink();
  }

  protected void initForMethod(NormalMethod nm) {
    currentMethod = nm;
    bridgeTarget = null;
    bridgeParameterTypes = null;
    bridgeParameterMappingRequired = false;
    bridgeRegistersLocationUpdated = false;
    bridgeParameterIndex = 0;
    bridgeRegisterLocation = Address.zero();
    bridgeSpilledParamLocation = Address.zero();
  }

  public final void setupDynamicBridgeMapping(NormalMethod method, Address fp) {
    initForMethod(method);
    setupBridgeRegisterIndex();

    if (currentMethod.getDeclaringClass().hasDynamicBridgeAnnotation()) {
      setupArchitectureSpecificDynamicBridgeMapping(fp);
    }
  }


  protected abstract void setupArchitectureSpecificDynamicBridgeMapping(Address fp);

  protected abstract void setupBridgeRegisterIndex();

  public final boolean hasBridgeInfo() {
    return bridgeTarget != null;
  }

  /**
   * @return {@code true} if there is still an implicit this
   * parameter that needs to be processed, {@code false} otherwise
   *
   * @see #getImplicitThisAddress()
   */
  public final boolean hasUnprocessedImplicitThis() {
    return bridgeParameterIndex == -1;
  }

  public final boolean hasMoreBridgeParameters() {
    return bridgeParameterIndex < bridgeParameterTypes.length;
  }

  public final boolean isBridgeParameterMappingRequired() {
    return bridgeParameterMappingRequired;
  }

  public final boolean needsBridgeRegisterLocationsUpdate() {
    return !bridgeRegistersLocationUpdated;
  }

  public final void setBridgeRegistersLocationUpdated() {
    this.bridgeRegistersLocationUpdated = true;
  }

  public final void setBridgeRegisterLocation(Address bridgeRegisterLocation) {
    this.bridgeRegisterLocation = bridgeRegisterLocation;
  }

  public final void decBrigeRegisterLocation(int bytes) {
    bridgeRegisterLocation = bridgeRegisterLocation.minus(bytes);
  }

  protected final void incBrigeRegisterLocation(int bytes) {
    bridgeRegisterLocation = bridgeRegisterLocation.plus(bytes);
  }

  protected final void cleanupPointers() {
    bridgeTarget = null;
    bridgeParameterTypes = null;
  }

  protected final void updateWithInfoForDynamicLink(CompiledMethod callingCompiledMethod, Offset callingInstructionOffset) {
    callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);

    bridgeTarget = dynamicLink.methodRef();
    bridgeParameterTypes = bridgeTarget.getParameterTypes();

    if (dynamicLink.isInvokedWithImplicitThisParameter()) {
      bridgeParameterInitialIndex = -1;
    } else {
      bridgeParameterInitialIndex = 0;
    }
  }

  public abstract void resetBridgeRegisterIndex();

  protected abstract void incBridgeRegisterIndex();

  /**
   * Checks if any unprocessed bridge <em>registers</em>
   * remain. When all bridge registers are processed, there
   * may still be spilled values that need to be processed.
   * It is the responsibility of the subclasses to handle those.
   *
   * @return {@code true} if any unprocessed bridge <em>registers</em>
   * remain
   */
  protected abstract boolean unprocessedBridgeRegistersRemain();

  protected final void reset() {
    bridgeParameterMappingRequired = true;
    bridgeParameterIndex = bridgeParameterInitialIndex;
  }

  /**
   * Resets the spilled param location in an architecture-specific way,
   * based on the given address.
   *
   * @param addr the address to use for the computation
   */
  public abstract void resetBridgeSpilledParamLocation(Address addr);

  /**
   * NOTE: it is the responsibility of the caller to check
   * that the method actually has an implicit this parameter
   * and that it still needs to be processed.
   *
   * @return the address of the implicit this parameter
   * @see #hasUnprocessedImplicitThis()
   */
  public abstract Address getImplicitThisAddress();

  /**
   * @return the address of the next bridge parameter or
   *  {@code Address.zero()} if no parameters are left
   */
  public abstract Address getNextBridgeParameterAddress();

}
