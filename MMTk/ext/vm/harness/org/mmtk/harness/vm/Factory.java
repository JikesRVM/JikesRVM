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
package org.mmtk.harness.vm;

import org.vmutil.options.OptionSet;
import org.mmtk.harness.Harness;
import org.mmtk.harness.scheduler.Lock;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;
import org.mmtk.vm.gcspy.ByteStream;
import org.mmtk.vm.gcspy.IntStream;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.ServerSpace;
import org.mmtk.vm.gcspy.ShortStream;
import org.mmtk.vm.gcspy.Util;

/**
 * This class defines factory methods for VM-specific types which must
 * be instantiated within MMTk.  Since the concrete type is defined at
 * build time, we leave it to a concrete VM-specific instance of this class
 * to perform the object instantiation.
 */
public class Factory extends org.mmtk.vm.Factory {

  @Override
  public OptionSet getOptionSet() {
    return Harness.options;
  }

  @Override
  public ActivePlan newActivePlan() {
    return new ActivePlan();
  }

  @Override
  public Assert newAssert() {
    return new Assert();
  }

  @Override
  public Barriers newBarriers() {
    return new Barriers();
  }

  @Override
  public Collection newCollection() {
    return new Collection();

  }

  @Override
  public BuildTimeConfig newBuildTimeConfig() {
    return new BuildTimeConfig();
  }

  @Override
  public Lock newLock(String name) {
    return Scheduler.newLock(name);
  }

  @Override
  public org.mmtk.vm.Monitor newMonitor(String name) {
    return Scheduler.newMonitor(name);
  }

  @Override
  public Memory newMemory() {
    return new Memory();
  }

  @Override
  public ObjectModel newObjectModel() {
    return new ObjectModel();
  }

  @Override
  public ReferenceProcessor newReferenceProcessor(ReferenceProcessor.Semantics semantics) {
    return ReferenceProcessor.getProcessorFor(semantics);
  }

  @Override
  public FinalizableProcessor newFinalizableProcessor() {
    return new FinalizableProcessor();
  }

  @Override
  public Scanning newScanning() {
    return new Scanning();
  }

  @Override
  public Statistics newStatistics() {
    return new Statistics();
  }

  @Override
  public Strings newStrings() {
    return new Strings();
  }

  @Override
  public SynchronizedCounter newSynchronizedCounter() {
    return new SynchronizedCounter();
  }

  @Override
  public org.mmtk.vm.TraceInterface newTraceInterface() {
    /* Not supported */
    return null;
  }


  /**********************************************************************
   * GCspy methods
   */

  /**
   * {@inheritDoc}
   */
  @Override
  public Util newGCspyUtil() {
    Assert.notImplemented();
    return null;
  }

  @Override
  public ServerInterpreter newGCspyServerInterpreter() {
    Assert.notImplemented();
    return null;
  }

  @Override
  public ServerSpace newGCspyServerSpace(
      ServerInterpreter serverInterpreter,
      String serverName,
      String driverName,
      String title,
      String blockInfo,
      int tileNum,
      String unused,
      boolean mainSpace) {
    Assert.notImplemented();
    return null;
  }

  @Override
  public IntStream newGCspyIntStream(
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
    Assert.notImplemented();
    return null;
  }

  @Override
  public ByteStream newGCspyByteStream(
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
    Assert.notImplemented();
    return null;
  }

  @Override
  public ShortStream newGCspyShortStream(
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
    Assert.notImplemented();
    return null;
  }

  /*
   * TuningFork support
   */

  @Override
  public org.mmtk.vm.MMTk_Events newEvents() {
    return new MMTkEvents();
  }

  @Override
  public Debug newDebug() {
    return new Debug();
  }
}
