package org.mmtk.vm;

import org.mmtk.vm.SynchronizedCounter;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

public class VM {
  
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
  public static final ObjectModel objectModel;
  public static final ActivePlan activePlan;
  public static final Assert assertions;
  public static final Barriers barriers;
  public static final Collection collection;
  public static final Memory memory;
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
  private static String vmPackage;
 
  static {
    vmPackage = System.getProperty("mmtk.hostjvm");
    ObjectModel xom = null;
    ActivePlan xap = null;
    Assert xas = null;
    Barriers xba = null;
    Collection xco = null;
    Memory xme = null;
    Options xop = null;
    ReferenceGlue xrg = null;
    Scanning xsc = null;
    Statistics xst = null;
    Strings xsr = null;
    TraceInterface xtr = null;
    try {
      xom = (ObjectModel) Class.forName(vmPackage+".ObjectModel").newInstance();
      xap = (ActivePlan) Class.forName(vmPackage+".ActivePlan").newInstance();
      xas = (Assert) Class.forName(vmPackage+".Assert").newInstance();
      xba = (Barriers) Class.forName(vmPackage+".Barriers").newInstance();
      xco = (Collection) Class.forName(vmPackage+".Collection").newInstance();
      xme = (Memory) Class.forName(vmPackage+".Memory").newInstance();
      xop = (Options) Class.forName(vmPackage+".Options").newInstance();
      xrg = (ReferenceGlue) Class.forName(vmPackage+".ReferenceGlue").newInstance();
      xsc = (Scanning) Class.forName(vmPackage+".Scanning").newInstance();
      xst = (Statistics) Class.forName(vmPackage+".Statistics").newInstance();
      xsr = (Strings) Class.forName(vmPackage+".Strings").newInstance();
      xtr = (TraceInterface) Class.forName(vmPackage+".TraceInterface").newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);     // we must *not* go on if the above has failed
    }
    objectModel = xom;
    activePlan = xap;
    assertions = xas;
    barriers = xba;
    collection = xco;
    memory = xme;
    options = xop;
    referenceTypes = xrg;
    scanning = xsc;
    statistics = xst;
    strings = xsr;
    traceInterface = xtr;
    REFERENCES_ARE_OBJECTS = ReferenceGlue.referencesAreObjectsTrapdoor(referenceTypes);
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
 }
  
  public static Lock newLock(String name) {
    Lock lock = null;
    try {
      lock = (Lock) Class.forName(vmPackage+".Lock").newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);     // we must *not* go on if the above has failed
    }
    lock.setName(name);
    return lock;
  }
  
  public static SynchronizedCounter newSynchronizedCounter() {
    SynchronizedCounter counter = null;
    try {
      counter = (SynchronizedCounter) Class.forName(vmPackage+".SynchronizedCounter").newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);     // we must *not* go on if the above has failed
    }
    return counter;
  }

}
