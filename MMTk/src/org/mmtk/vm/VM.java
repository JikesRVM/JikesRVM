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

import org.mmtk.utility.options.Options;
import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;
import org.mmtk.vm.gcspy.ByteStream;
import org.mmtk.vm.gcspy.IntStream;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.ServerSpace;
import org.mmtk.vm.gcspy.ShortStream;
import org.mmtk.vm.gcspy.Util;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This class is responsible for all VM-specific functionality required
 * by MMTk.<p>
 *
 * The class has three major elements.  First it defines VM-specific
 * constants which are used throughout MMTk, second, it declares
 * singleton instances of each of the abstract classes in this
 * package, and third, it provides factory methods for VM-specific
 * instances which are needed by MMTk (such as <code>Lock</code>).<p>
 *
 * Both the constants and the singleton instances are initialized to
 * VM-specific values at build time using reflection and a VM-specific
 * factory class. The system property <code>mmtk.hostjvm</code> is
 * interrogated at build time to establish concrete instantations of
 * the abstract classes in this package. By <b>convention</b>,
 * <code>mmtk.hostjvm</code> will identify a VM-provided package which
 * includes concrete instances of each of the abstract classes, with
 * each concrete class having the same base class name (but different
 * package name) as the abstract classes defined here.  The class
 * initializer for this class then uses the system property
 * <code>mmtk.hostjvm</code> to load the VM-specific concrete classes
 * and initialize the constants and singletons defined here.
 */
public final class VM {
  /*
   * VM-specific constant values
   */
  /** <code>true</code> if assertions should be verified */
  public static final boolean VERIFY_ASSERTIONS;
  /** The lowest address in virtual memory known to MMTk */
  public static final Address HEAP_START;
  /** The highest address in virtual memory known to MMTk */
  public static final Address HEAP_END;
  /** The lowest address in the contiguously available memory available to MMTk */
  public static final Address AVAILABLE_START;
  /** The highest address in the contiguously available memory available to MMTk */
  public static final Address AVAILABLE_END;
  /** The log base two of the size of an address */
  public static final byte LOG_BYTES_IN_ADDRESS;
  /** The log base two of the size of a word */
  public static final byte LOG_BYTES_IN_WORD;
  /** The log base two of the size of an OS page */
  public static final byte LOG_BYTES_IN_PAGE;
  /** The log base two of the minimum allocation alignment */
  public static final byte LOG_MIN_ALIGNMENT;
  /** The log base two of (MAX_ALIGNMENT/MIN_ALIGNMENT) */
  public static final byte MAX_ALIGNMENT_SHIFT;
  /** The maximum number of bytes of padding to prepend to an object */
  public static final int MAX_BYTES_PADDING;
  /** The value to store in alignment holes */
  public static final int ALIGNMENT_VALUE;
  /** The offset from an array reference to element zero */
  public static final Offset ARRAY_BASE_OFFSET;
  /** Global debugging switch */
  public static final boolean DEBUG;

  /*
   * VM-specific functionality captured in a series of singleton classs
   */
  @Untraced
  public static final ActivePlan activePlan;
  @Untraced
  public static final Assert assertions;
  @Untraced
  public static final Barriers barriers;
  @Untraced
  public static final Collection collection;
  @Untraced
  public static final Config config;
  @Untraced
  public static final Memory memory;
  @Untraced
  public static final ObjectModel objectModel;
  @Untraced
  public static final ReferenceProcessor weakReferences;
  @Untraced
  public static final ReferenceProcessor softReferences;
  @Untraced
  public static final ReferenceProcessor phantomReferences;
  @Untraced
  public static final FinalizableProcessor finalizableProcessor;
  @Untraced
  public static final Scanning scanning;
  @Untraced
  public static final Statistics statistics;
  @Untraced
  public static final Strings strings;
  @Untraced
  public static final TraceInterface traceInterface;
  @Untraced
  public static final MMTk_Events events;
  @Untraced
  public static final Debug debugging;

  /*
   * The remainder is does the static initialization of the
   * above, reflectively binding to the appropriate host jvm
   * classes.
   */
  private static final Factory factory;
  private static final String vmFactory;

  /**
   * This class initializer establishes a VM-specific factory class
   * using reflection, and then uses that to create VM-specific concrete
   * instances of all of the vm classes, initializing the singltons in
   * this class.  Finally the constants declared in this class are
   * initialized using the VM-specific singletons.
   */
  static {
    /* Identify the VM-specific factory using reflection */
    vmFactory = System.getProperty("mmtk.hostjvm");
    Factory xfa = null;
    try {
      xfa = (Factory) Class.forName(vmFactory).newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);     // we must *not* go on if the above has failed
    }
    factory = xfa;

    /* Now instantiate the singletons using the factory */
    activePlan = factory.newActivePlan();
    assertions = factory.newAssert();
    barriers = factory.newBarriers();
    collection = factory.newCollection();
    memory = factory.newMemory();
    objectModel = factory.newObjectModel();
    Options.set = factory.getOptionSet();
    weakReferences = factory.newReferenceProcessor(ReferenceProcessor.Semantics.WEAK);
    softReferences = factory.newReferenceProcessor(ReferenceProcessor.Semantics.SOFT);
    phantomReferences = factory.newReferenceProcessor(ReferenceProcessor.Semantics.PHANTOM);
    finalizableProcessor = factory.newFinalizableProcessor();
    scanning = factory.newScanning();
    statistics = factory.newStatistics();
    strings = factory.newStrings();
    traceInterface = factory.newTraceInterface();
    events = factory.newEvents();
    debugging = factory.newDebug();
    config = new Config(factory.newBuildTimeConfig());

    /* Now initialize the constants using the vm-specific singletons */
    VERIFY_ASSERTIONS = Assert.verifyAssertionsTrapdoor(assertions);
    HEAP_START = Memory.heapStartTrapdoor(memory);
    HEAP_END = Memory.heapEndTrapdoor(memory);
    AVAILABLE_START = Memory.availableStartTrapdoor(memory);
    AVAILABLE_END = Memory.availableEndTrapdoor(memory);
    LOG_BYTES_IN_ADDRESS = Memory.logBytesInAddressTrapdoor(memory);
    LOG_BYTES_IN_WORD = Memory.logBytesInWordTrapdoor(memory);
    LOG_BYTES_IN_PAGE = Memory.logBytesInPageTrapdoor(memory);
    LOG_MIN_ALIGNMENT = Memory.logMinAlignmentTrapdoor(memory);
    MAX_ALIGNMENT_SHIFT = Memory.maxAlignmentShiftTrapdoor(memory);
    MAX_BYTES_PADDING = Memory.maxBytesPaddingTrapdoor(memory);
    ALIGNMENT_VALUE = Memory.alignmentValueTrapdoor(memory);
    ARRAY_BASE_OFFSET = ObjectModel.arrayBaseOffsetTrapdoor(objectModel);
    DEBUG = Debug.isEnabledTrapdoor(debugging);
  }

  /**
   * Create a new Lock instance using the appropriate VM-specific
   * concrete Lock sub-class.
   *
   * @see Lock
   *
   * @param name The string to be associated with this lock instance
   * @return A concrete VM-specific Lock instance.
   */
  public static Lock newLock(String name) {
    return factory.newLock(name);
  }

  /**
   * Create a new HeavyCondLock instance using the appropriate VM-specific
   * concrete Lock sub-class.
   *
   * @see Monitor
   *
   * @param name The string to be associated with this instance
   * @return A concrete VM-specific HeavyCondLock instance.
   */
  public static Monitor newHeavyCondLock(String name) {
    return factory.newMonitor(name);
  }

  /**
   * Create a new SynchronizedCounter instance using the appropriate
   * VM-specific concrete SynchronizedCounter sub-class.
   *
   * @see SynchronizedCounter
   *
   * @return A concrete VM-specific SynchronizedCounter instance.
   */
  public static SynchronizedCounter newSynchronizedCounter() {
    return factory.newSynchronizedCounter();
  }

  /**********************************************************************
   * GCspy methods
   */

  /**
   * Create a new Util instance using the appropriate
   * VM-specific concrete Util sub-class.
   *
   * @see Util
   *
   * @return A concrete VM-specific Util instance.
   */
  public static Util newGCspyUtil() {
    return factory.newGCspyUtil();
  }

  /**
   * Create a new ServerInterpreter instance using the appropriate
   * VM-specific concrete ServerInterpreter sub-class.
   *
   * @see ServerInterpreter
   *
   * @return A concrete VM-specific ServerInterpreter instance.
   */
  public static ServerInterpreter newGCspyServerInterpreter() {
    return factory.newGCspyServerInterpreter();
  }

  /**
   * Create a new ServerInterpreter instance using the appropriate
   * VM-specific concrete ServerInterpreter sub-class.
   *
   * @see ServerInterpreter
   *
   * @return A concrete VM-specific ServerInterpreter instance.
   */
  public static ServerSpace newGCspyServerSpace(
      ServerInterpreter serverInterpreter,
      String serverName,
      String driverName,
      String title,
      String blockInfo,
      int tileNum,
      String unused,
      boolean mainSpace) {
    return factory.newGCspyServerSpace(serverInterpreter, serverName, driverName,
                                       title, blockInfo, tileNum, unused,
                                       mainSpace);
  }

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
  public static ByteStream newGCspyByteStream(
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
    return factory.newGCspyByteStream(driver, name, minValue,  maxValue,
                                     zeroValue, defaultValue, stringPre, stringPost,
                                     presentation, paintStyle, indexMaxStream,
                                     colour, summary);
  }

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
  public static IntStream newGCspyIntStream(
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
    return factory.newGCspyIntStream(driver, name, minValue,  maxValue,
                                     zeroValue, defaultValue, stringPre, stringPost,
                                     presentation, paintStyle, indexMaxStream,
                                     colour, summary);
  }

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
   * @return A concrete VM-specific IntStream instance.
   */
  public static ShortStream newGCspyShortStream(
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
    return factory.newGCspyShortStream(driver, name, minValue,  maxValue,
                                     zeroValue, defaultValue, stringPre, stringPost,
                                     presentation, paintStyle, indexMaxStream,
                                     colour, summary);
  }
}
