/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.vm;

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
 * 
 * $Id: VM.java 10750 2006-09-05 05:10:30 +0000 (Tue, 05 Sep 2006) steveb-oss $
 * 
 * @author Steve Blackburn
 * @version $Revision: 10750 $
 * @date $Date: 2006-09-05 05:10:30 +0000 (Tue, 05 Sep 2006) $
 */
public final class VM {
  
  /*
   * VM-specific constant values
   */
  /** <code>true</code> if the references are implemented as heap objects */
  public static final boolean REFERENCES_ARE_OBJECTS;
  /** <code>true</code> if assertions should be verified */
  public static final boolean VERIFY_ASSERTIONS;
  /** The lowest address in virtual memory known to MMTk */
  public static final Address HEAP_START;
  /** The highest address in virtual memory known to MMTk */
  public static final Address HEAP_END;
  /** The lowest address in the contigiously available memory available to MMTk */
  public static final Address AVAILABLE_START;
  /** The highest address in the contigiously available memory available to MMTk */
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

  /*
   * VM-specific functionality captured in a series of singleton classs
   */
  public static final ActivePlan activePlan;
  public static final Assert assertions;
  public static final Barriers barriers;
  public static final Collection collection;
  public static final Memory memory;
  public static final ObjectModel objectModel;
  public static final Options options;
  public static final ReferenceGlue referenceTypes;
  public static final Scanning scanning;
  public static final Statistics statistics;
  public static final Strings strings;
  public static final TraceInterface traceInterface;
  
  /*
   * The remainder is does the static initialization of the
   * above, reflectively binding to the appropriate host jvm
   * classes.
   */
  private static final Factory factory;
  private static final String vmPackage;

  /**
   * This class initializer establishes a VM-specific factory class
   * using reflection, and then uses that to create VM-specific concrete
   * instances of all of the vm classes, initializing the singltons in
   * this class.  Finally the constants declared in this class are
   * initialized using the VM-specific singletons.
   */
  static {
    /* Identify the VM-specific factory using reflection */
    vmPackage = System.getProperty("mmtk.hostjvm");
    Factory xfa = null;
    try {
      xfa = (Factory) Class.forName(vmPackage+".Factory").newInstance();
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
    options = factory.newOptions();
    referenceTypes = factory.newReferenceGlue();
    scanning = factory.newScanning();
    statistics = factory.newStatistics();
    strings = factory.newStrings();
    traceInterface = factory.newTraceInterface();
    
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
    REFERENCES_ARE_OBJECTS = ReferenceGlue.referencesAreObjectsTrapdoor(referenceTypes);
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
}
