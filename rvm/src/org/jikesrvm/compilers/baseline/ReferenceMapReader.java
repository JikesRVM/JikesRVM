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

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * This class encapsulates the functionality that the baseline GC map iterators
 * require to read baseline compiler reference maps.
 */
@Uninterruptible
public final class ReferenceMapReader {
  /** Current index in current map */
  protected int mapIndex;
  /** id of current map out of all maps */
  protected int mapId;
  /** set of maps for this method */
  protected ReferenceMaps maps;
  /** have we processed all the values in the regular map yet? */
  protected boolean finishedWithRegularMap;

  protected NormalMethod currentMethod;

  public void setMethod(NormalMethod currentMethod, CompiledMethod compiledMethod) {
    this.currentMethod = currentMethod;
    maps = ((BaselineCompiledMethod) compiledMethod).referenceMaps;
  }

  public int getMapIndex() {
    return mapIndex;
  }

  public int getMapId() {
    return mapId;
  }

  public boolean isFinishedWithRegularMap() {
    return finishedWithRegularMap;
  }

  public void setFinishedWithRegularMap() {
    this.finishedWithRegularMap = true;
  }

  public boolean currentMapIsForJSR() {
    return mapId < 0;
  }

  public boolean currentMapHasMorePointers() {
    return mapIndex != 0;
  }

  public void locateGCPoint(Offset instructionOffset) {
    mapId = maps.locateGCPoint(instructionOffset, currentMethod);
    mapIndex = 0;
  }

  public void updateMapIndex() {
    if (currentMapIsForJSR()) {
      mapIndex = maps.getNextJSRRefIndex(mapIndex);
    } else {
      mapIndex = maps.getNextRefIndex(mapIndex, mapId);
    }
  }

  public void updateMapIndexWithJSRReturnAddrIndex() {
    mapIndex = maps.getNextJSRReturnAddrIndex(mapIndex);
  }

  public void acquireLockForJSRProcessing() {
    // lock the jsr lock to serialize jsr processing
    ReferenceMaps.jsrLock.lock();
  }

  public void releaseLockForJSRProcessing() {
    ReferenceMaps.jsrLock.unlock();
  }

  public int setupJSRSubroutineMap() {
    return maps.setupJSRSubroutineMap(mapId);
  }

  public int getNextJSRAddressIndex(Offset nextMachineCodeOffset) {
    return maps.getNextJSRAddressIndex(nextMachineCodeOffset, currentMethod);
  }

  public void reset() {
    mapIndex = 0;
    finishedWithRegularMap = false;
  }

  public void cleanupPointers() {
    maps.cleanupPointers();
    maps = null;
  }

}
