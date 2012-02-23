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
package org.mmtk.vm;

import org.vmutil.options.OptionSet;
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
 * build time, we leave it to a concrete vm-specific instance of this class
 * to perform the object instantiation.
 */
public abstract class Factory {

  /**
   * Create or retrieve the OptionSet used for MMTk options.
   *
   * @return A concrete VM-specific OptionSet instance
   */
  public abstract OptionSet getOptionSet();

  /**
   * Create a new ActivePlan instance using the appropriate VM-specific
   * concrete ActivePlan sub-class.
   *
   * @see ActivePlan
   * @return A concrete VM-specific ActivePlan instance.
   */
  public abstract ActivePlan newActivePlan();

  /**
   * Create a new Assert instance using the appropriate VM-specific
   * concrete Assert sub-class.
   *
   * @see Assert
   * @return A concrete VM-specific Assert instance.
   */
  public abstract Assert newAssert();

  /**
   * Create a new Barriers instance using the appropriate VM-specific
   * concrete Barriers sub-class.
   *
   * @see Barriers
   * @return A concrete VM-specific Barriers instance.
   */
  public abstract Barriers newBarriers();

  /**
   * Create a new Collection instance using the appropriate VM-specific
   * concrete Collection sub-class.
   *
   * @see Collection
   * @return A concrete VM-specific Collection instance.
   */
  public abstract Collection newCollection();

  /**
   * Create a new Config instance using the appropriate VM-specific
   * concrete Config sub-class.
   *
   * @see Collection
   * @return A concrete VM-specific Collection instance.
   */
  public abstract BuildTimeConfig newBuildTimeConfig();

  /**
   * Create a new Lock instance using the appropriate VM-specific
   * concrete Lock sub-class.
   *
   * @see Lock
   * @param name The string to be associated with this lock instance
   * @return A concrete VM-specific Lock instance.
   */
  public abstract Lock newLock(String name);

  /**
   * Create a new Monitor instance using the appropriate VM-specific
   * concrete Lock sub-class.
   *
   * @see Monitor
   * @param name The string to be associated with this monitor instance
   * @return A concrete VM-specific Monitor instance.
   */
  public abstract Monitor newMonitor(String name);

  /**
   * Create a new Memory instance using the appropriate VM-specific
   * concrete Memory sub-class.
   *
   * @see Memory
   * @return A concrete VM-specific Memory instance.
   */
  public abstract Memory newMemory();

  /**
   * Create a new ObjectModel instance using the appropriate VM-specific
   * concrete ObjectModel sub-class.
   *
   * @see ObjectModel
   * @return A concrete VM-specific ObjectModel instance.
   */
  public abstract ObjectModel newObjectModel();

  /**
   * Create a new ReferenceProcessor instance using the appropriate VM-specific
   * concrete ReferenceProcessor sub-class.
   *
   * @see ReferenceProcessor
   * @return A concrete VM-specific ReferenceProcessor instance.
   */
  public abstract ReferenceProcessor newReferenceProcessor(ReferenceProcessor.Semantics semantics);


  /**
   * Create a new FinalizbleProcessor instance using the appropriate VM-specific
   * concrete FinalizableProcessor sub-class.
   *
   * @see FinalizableProcessor
   * @return A concrete VM-specific FinalizableProcessor instance.
   */
  public abstract FinalizableProcessor newFinalizableProcessor();

  /**
   * Create a new Scanning instance using the appropriate VM-specific
   * concrete Scanning sub-class.
   *
   * @see Scanning
   * @return A concrete VM-specific Scanning instance.
   */
  public abstract Scanning newScanning();

  /**
   * Create a new Statistics instance using the appropriate VM-specific
   * concrete Statistics sub-class.
   *
   * @see Statistics
   * @return A concrete VM-specific Statistics instance.
   */
  public abstract Statistics newStatistics();

  /**
   * Create a new Strings instance using the appropriate VM-specific
   * concrete Strings sub-class.
   *
   * @see Strings
   * @return A concrete VM-specific Strings instance.
   */
  public abstract Strings newStrings();

  /**
   * Create a new SynchronizedCounter instance using the appropriate
   * VM-specific concrete SynchronizedCounter sub-class.
   *
   * @see SynchronizedCounter
   *
   * @return A concrete VM-specific SynchronizedCounter instance.
   */
  public abstract SynchronizedCounter newSynchronizedCounter();

  /**
   * Create a new TraceInterface instance using the appropriate VM-specific
   * concrete TraceInterface sub-class.
   *
   * @see TraceInterface
   * @return A concrete VM-specific TraceInterface instance.
   */
  public abstract TraceInterface newTraceInterface();

  /**
   * Create a new MMTk_Events instance using the appropriate VM-specific
   * concrete MMTk_Events sub-class.
   */
  public abstract MMTk_Events newEvents();

  /**
   * Create a new debug object
   */
  public abstract Debug newDebug();

  /**********************************************************************
   * GCspy methods
   */

  /**
   * Create a new Util instance using the appropriate VM-specific
   * concrete Util sub-class.
   *
   * @see Util
   * @return A concrete VM-specific Util instance.
   */
  public abstract Util newGCspyUtil();

  /**
   * Create a new ServerInterpreter instance using the appropriate VM-specific
   * concrete ServerInterpreter sub-class.
   *
   * @see ServerInterpreter
   * @return A concrete VM-specific ServerInterpreter instance.
   */
  public abstract ServerInterpreter newGCspyServerInterpreter();

  /**
   * Create a new ServerSpace instance using the appropriate VM-specific
   * concrete ServerSpace sub-class.
   *
   * @param serverInterpreter The server that owns this space
   * @param serverName The server's name
   * @param driverName The space driver's name
   * @param title Title for the space
   * @param blockInfo A label for each block
   * @param tileNum Max number of tiles in this space
   * @param unused A label for unused blocks
   * @param mainSpace Whether this space is the main space
   *
   * @see ServerSpace
   * @return A concrete VM-specific ServerSpace instance.
   */
  public abstract ServerSpace newGCspyServerSpace(
      ServerInterpreter serverInterpreter,
      String serverName,
      String driverName,
      String title,
      String blockInfo,
      int tileNum,
      String unused,
      boolean mainSpace);

  /**
   * Create a new IntStream instance using the appropriate
   * VM-specific concrete IntStream sub-class.
   *
   * @param driver        The driver that owns this Stream
   * @param name           The name of the stream (e.g. "Used space")
   * @param minValue       The minimum value for any item in this stream.
   *                       Values less than this will be represented as "minValue-"
   * @param maxValue       The maximum value for any item in this stream.
   *                       Values greater than this will be represented as "maxValue+"
   * @param zeroValue      The zero value for this stream
   * @param defaultValue   The default value for this stream
   * @param stringPre      A string to prefix values (e.g. "Used: ")
   * @param stringPost     A string to suffix values (e.g. " bytes.")
   * @param presentation   How a stream value is to be presented.
   * @param paintStyle     How the value is to be painted.
   * @param indexMaxStream The index of the maximum stream if the presentation is *_VAR.
   * @param colour         The default colour for tiles of this stream
   * @see IntStream
   *
   * @return A concrete VM-specific IntStream instance.
   */
  public abstract IntStream newGCspyIntStream(
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
      boolean summary);

  /**
   * Create a new ByteStream instance using the appropriate
   * VM-specific concrete ByteStream sub-class.
   *
   * @param driver        The driver that owns this Stream
   * @param name           The name of the stream (e.g. "Used space")
   * @param minValue       The minimum value for any item in this stream.
   *                       Values less than this will be represented as "minValue-"
   * @param maxValue       The maximum value for any item in this stream.
   *                       Values greater than this will be represented as "maxValue+"
   * @param zeroValue      The zero value for this stream
   * @param defaultValue   The default value for this stream
   * @param stringPre      A string to prefix values (e.g. "Used: ")
   * @param stringPost     A string to suffix values (e.g. " bytes.")
   * @param presentation   How a stream value is to be presented.
   * @param paintStyle     How the value is to be painted.
   * @param indexMaxStream The index of the maximum stream if the presentation is *_VAR.
   * @param colour         The default colour for tiles of this stream
   * @see IntStream
   *
   * @return A concrete VM-specific ByteStream instance.
   */
  public abstract ByteStream newGCspyByteStream(
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
      boolean summary);

  /**
   * Create a new ShortStream instance using the appropriate
   * VM-specific concrete ShortStream sub-class.
   *
   * @param driver        The driver that owns this Stream
   * @param name           The name of the stream (e.g. "Used space")
   * @param minValue       The minimum value for any item in this stream.
   *                       Values less than this will be represented as "minValue-"
   * @param maxValue       The maximum value for any item in this stream.
   *                       Values greater than this will be represented as "maxValue+"
   * @param zeroValue      The zero value for this stream
   * @param defaultValue   The default value for this stream
   * @param stringPre      A string to prefix values (e.g. "Used: ")
   * @param stringPost     A string to suffix values (e.g. " bytes.")
   * @param presentation   How a stream value is to be presented.
   * @param paintStyle     How the value is to be painted.
   * @param indexMaxStream The index of the maximum stream if the presentation is *_VAR.
   * @param colour         The default colour for tiles of this stream
   * @see IntStream
   *
   * @return A concrete VM-specific ShortStream instance.
   */
  public abstract ShortStream newGCspyShortStream(
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
      boolean summary);
}
