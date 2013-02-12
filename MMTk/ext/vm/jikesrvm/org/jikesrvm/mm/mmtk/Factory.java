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
package org.jikesrvm.mm.mmtk;

import org.vmutil.options.OptionSet;
import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;
import org.mmtk.vm.ReferenceProcessor.Semantics;

import org.jikesrvm.VM;

/**
 * This is a VM-specific class which defines factory methods for
 * VM-specific types which must be instantiated within MMTk.
 *
 * @see org.mmtk.vm.Factory
 */
public final class Factory extends org.mmtk.vm.Factory {

  private static final String DEFAULT_MMTK_PROPERTIES = ".mmtk.properties";
  private static final String CONFIG_FILE_PROPERTY = "mmtk.properties";

  @Override
  public OptionSet getOptionSet() {
    return org.jikesrvm.options.OptionSet.gc;
  }

  @Override
  public org.mmtk.vm.ActivePlan newActivePlan() {
    try {
      return new ActivePlan();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ActivePlan!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Assert newAssert() {
    try {
      return new Assert();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Assert!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Barriers newBarriers() {
    try {
      return new Barriers();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Barriers!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Collection newCollection() {
    try {
      return new Collection();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Collection!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.BuildTimeConfig newBuildTimeConfig() {
    try {
      return new BuildTimeConfig(CONFIG_FILE_PROPERTY, DEFAULT_MMTK_PROPERTIES);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new BuildTimeConfiguration!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Lock newLock(String name) {
    try {
      return new Lock(name);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Lock!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Monitor newMonitor(String name) {
    try {
      return new Monitor(name);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Monitor!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Memory newMemory() {
    try {
      return new Memory();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Memory!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.ObjectModel newObjectModel() {
    try {
      return new ObjectModel();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ObjectModel!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.ReferenceProcessor newReferenceProcessor(Semantics semantics) {
    try {
      return ReferenceProcessor.get(semantics);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ReferenceProcessor!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.FinalizableProcessor newFinalizableProcessor() {
    try {
      return FinalizableProcessor.getProcessor();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new FinalizableProcessor!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Scanning newScanning() {
    try {
      return new Scanning();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Scanning!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Statistics newStatistics() {
    try {
      return new Statistics();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Statistics!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Strings newStrings() {
    try {
      return new Strings();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Strings!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.SynchronizedCounter newSynchronizedCounter() {
    try {
      return new SynchronizedCounter();
    } catch (Exception e) {
     VM.sysFail("Failed to allocate new SynchronizedCounter!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.TraceInterface newTraceInterface() {
    try {
      return new TraceInterface();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new TraceInterface!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.MMTk_Events newEvents() {
    try {
      return new MMTk_Events(org.jikesrvm.tuningfork.TraceEngine.engine);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new MMTk_Events!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.Debug newDebug() {
    return new Debug();
  }

  /**********************************************************************
   * GCspy methods
   */

  /**
   * {@inheritDoc}
   */
  @Override
  public org.mmtk.vm.gcspy.Util newGCspyUtil() {
    try {
      return new org.jikesrvm.mm.mmtk.gcspy.Util();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Util!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.gcspy.ServerInterpreter newGCspyServerInterpreter() {
    try {
      return new org.jikesrvm.mm.mmtk.gcspy.ServerInterpreter();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ServerInterpreter!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.gcspy.ServerSpace newGCspyServerSpace(
      org.mmtk.vm.gcspy.ServerInterpreter serverInterpreter,
      String serverName,
      String driverName,
      String title,
      String blockInfo,
      int tileNum,
      String unused,
      boolean mainSpace){
    try {
      return new org.jikesrvm.mm.mmtk.gcspy.ServerSpace(
          serverInterpreter, serverName, driverName, title,
          blockInfo, tileNum, unused, mainSpace);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ServerSpace!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.gcspy.ByteStream newGCspyByteStream(
      AbstractDriver driver,
      String name,
      byte minValue,
      byte maxValue,
      byte zeroValue,
      byte defaultValue,
      String stringPre,
      String stringPost,
      int presentation,
      int paintStyle,
      int indexMaxStream,
      Color colour,
      boolean summary) {
    try {
      return new org.jikesrvm.mm.mmtk.gcspy.ByteStream(
          driver, name, minValue,  maxValue,
          zeroValue, defaultValue, stringPre, stringPost,
          presentation, paintStyle, indexMaxStream,
          colour, summary);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ByteStream!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.gcspy.IntStream newGCspyIntStream(
      AbstractDriver driver,
      String name,
      int minValue,
      int maxValue,
      int zeroValue,
      int defaultValue,
      String stringPre,
      String stringPost,
      int presentation,
      int paintStyle,
      int indexMaxStream,
      Color colour,
      boolean summary) {
    try {
      return new org.jikesrvm.mm.mmtk.gcspy.IntStream(
          driver, name, minValue,  maxValue,
          zeroValue, defaultValue, stringPre, stringPost,
          presentation, paintStyle, indexMaxStream,
          colour, summary);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new IntStream!");
      return null; // never get here
    }
  }

  @Override
  public org.mmtk.vm.gcspy.ShortStream newGCspyShortStream(
      AbstractDriver driver,
      String name,
      short minValue,
      short maxValue,
      short zeroValue,
      short defaultValue,
      String stringPre,
      String stringPost,
      int presentation,
      int paintStyle,
      int indexMaxStream,
      Color colour,
      boolean summary) {
    try {
      return new org.jikesrvm.mm.mmtk.gcspy.ShortStream(
          driver, name, minValue,  maxValue,
          zeroValue, defaultValue, stringPre, stringPost,
          presentation, paintStyle, indexMaxStream,
          colour, summary);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ShortStream!");
      return null; // never get here
    }
  }
}
