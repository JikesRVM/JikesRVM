/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Hashtable;
import java.util.Vector;
import java.util.Stack;
import java.util.Enumeration;

import java.io.*;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

/**
 * Construct a RVM virtual machine bootimage.
 * Invocation args:
 *    -n  <filename>           list of typenames to be written to bootimage
 *    -X:bc:<bcarg>            pass bcarg to bootimage compiler as command
 *                             line argument
 *    -classpath <path>        list of places to look for bootimage classes
 *    -littleEndian            write words to bootimage in little endian format?
 *    -trace                   talk while we work?
 *    -detailed                print detailed info on traversed objects
 *    -o <filename>            place to put bootimage
 *    -m <filename>            place to put bootimage map
 *    -sf <filename>           OBSOLETE compatibility aid
 *    -xclasspath <path>       OBSOLETE compatibility aid
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 * (complete rewrite of John Barton's original, this time using java2
 * reflection)
 */
public class BootImageWriter2 extends BootImageWriterMessages
  implements BootImageWriterConstants
{
  /**
   * Talk while we work?
   */
  private static boolean trace = false;

  /**
   * Print detailed info on traversed objects?
   */
  private static boolean detailed_trace = false;

  /**
   * Places to look for classes when building bootimage.
   */
  private static String bootImageRepositoriesAtBuildTime;

  /**
   * Places to look for classes when executing bootimage.
   */
  private static String bootImageRepositoriesAtExecutionTime;

  /**
   * Types to be placed into bootimage, stored as key/value pairs
   * where key is a String like "java.lang.Object" or "[Ljava.lang.Object;"
   * and value is the corresponding VM_Type.
   */
  private static Hashtable bootImageTypes = new Hashtable(5000);

  /**
   * The absolute address at which the bootImage is going to be loaded.
   */
  private static int bootImageAddress = IMAGE_ADDRESS;

  /**
   * Write words to bootimage in little endian format?
   */
  private static boolean littleEndian = false;

  /**
   * A wrapper around the calling context to aid in tracing.
   */
  private static class TraceContext extends Stack {
    /**
     * Report a field that is part of the OTI but not host JDK implementation.
     */
    public void traceFieldNotInHostJdk() {
      traceNulledWord(": field not in host jdk");
    }

    /**
     * Report an object of a class that is not part of the bootImage.
     */
    public void traceObjectNotInBootImage() {
      traceNulledWord(": object not in bootimage");
    }

    /**
     * Report nulling out a pointer.
     */
    private void traceNulledWord(String message) {
      say(this.toString(), message, ", writing a null");
    }

    /**
     * Generic trace routine.
     */
    public void trace(String message) {
      say(this.toString(), message);
    }

    /**
     * Return a string representation of the context.
     * @return string representation of this context
     */
    public String toString() {
      StringBuffer message = new StringBuffer();
      for (int i = 0; i < size(); i++) {
        if (i > 0) message.append(" --> ");
        message.append(elementAt(i));
      }
      return message.toString();
    }

    /**
     * Push an entity onto the context
     */
    public void push(String type, String fullName) {
      StringBuffer sb = new StringBuffer("(");
      sb.append(type).append(")");
      sb.append(fullName);
      push(sb.toString());
    }

    /**
     * Push a field access onto the context
     */
    public void push(String type, String decl, String fieldName) {
      StringBuffer sb = new StringBuffer("(");
      sb.append(type).append(")");
      sb.append(decl).append(".").append(fieldName);
      push(sb.toString());
    }

    /**
     * Push an array access onto the context
     */
    public void push(String type, String decl, int index) {
      StringBuffer sb = new StringBuffer("(");
      sb.append(type).append(")");
      sb.append(decl).append("[").append(index).append("]");
      push(sb.toString());
    }
  }

  /**
   * Global trace context.
   */
  private static TraceContext traceContext = new TraceContext();

    private static Object sillyhack;

  /**
   * Main.
   * @param args command line arguments
   */
  public static void main(String args[]) {
    String   bootImageName         = null;
    String   bootImageMapName      = null;
    Vector   bootImageTypeNames    = null;
    String[] bootImageCompilerArgs = {};

    //
    // This may look useless, but it is not: it is a kludge to prevent
    // forName blowing up.  By forcing the system to load some classes 
    // now, we ensure that forName does not cause security violations by
    // trying to load into java.util later.
    // 
    java.util.HashMap x = new java.util.HashMap();
    x.put(x, x.getClass());
    sillyhack = x;

    //
    // Process command line directives.
    //
    for (int i = 0; i < args.length; ++i) {
      // name of image file
      if (args[i].equals("-o")) {
        bootImageName = args[++i];
        continue;
      }
      // name of map file
      if (args[i].equals("-m")) {
        bootImageMapName = args[++i];
        continue;
      }
      // image address
      if (args[i].equals("-ia")) {
        bootImageAddress = Integer.decode(args[++i]).intValue();
        continue;
      }
      // file containing names of types to be placed into bootimage
      if (args[i].equals("-n")) {
        try {
          bootImageTypeNames = readTypeNames(args[++i]);
        } catch (IOException e) {
          fail("unable to read type names from "+args[i]+": "+e);
        }
        continue;
      }
      // file containing names of fields to be nulled in bootimage
      if (args[i].equals("-sf")) {
        say("-sf not supported (OBSOLETE)");
        ++i;
        continue;
      }
      // bootimage compiler argument
      if (args[i].startsWith("-X:bc:")) {
        String[] nbca = new String[bootImageCompilerArgs.length+1];
        for (int j = 0; j < bootImageCompilerArgs.length; j++) {
          nbca[j] = bootImageCompilerArgs[j];
        }
        nbca[nbca.length-1] = args[i].substring(6);
        bootImageCompilerArgs = nbca;
        say("compiler arg: ", bootImageCompilerArgs[nbca.length-1]);
        continue;
      }
      // places where rvm components live, at build time
      if (args[i].equals("-classpath")) {
        bootImageRepositoriesAtBuildTime = args[++i];
        continue;
      }
      // places where rvm components live, at execution time
      if (args[i].equals("-xclasspath")) {
        bootImageRepositoriesAtExecutionTime = args[++i];
        continue;
      }
      // generate trace messages while writing bootimage (for debugging)
      if (args[i].equals("-trace")) {
        trace = true;
        continue;
      }
      // generate detailed information about traversed objects (for debugging)
      if (args[i].equals("-detailed")) {
        detailed_trace = true;
        trace = true;
        // initialize traversal table
        traversed = new Hashtable(500);
        continue;
      }
      // write words to bootimage in little endian format
      if (args[i].equals("-littleEndian")) {
        littleEndian = true;
        continue;
      }
      fail("unrecognized command line argument: " + args[i]);
    }

    //
    // Check command line directives for correctness.
    //
    if (bootImageName == null)
      fail("please specify \"-o <filename>\"");

    if (bootImageTypeNames == null)
      fail("please specify \"-n <filename>\"");

    if (bootImageRepositoriesAtBuildTime == null)
      fail("please specify \"-classpath <path>\"");

    if (bootImageRepositoriesAtExecutionTime == null)
      bootImageRepositoriesAtExecutionTime = bootImageRepositoriesAtBuildTime;

    //
    // Initialize the bootimage.
    // Do this earlier than we logically need to because we need to
    // allocate a massive byte[] that will hold the bootimage in core and
    // on some host JDKs it is essential to do that early while there
    // is still lots of virgin storage left. 
    // (need to get contiguous storage before it gets fragmented by pinned objects)
    //
    BootImage.init(littleEndian, trace);

    //
    // Install handler that intercepts all object address references made by
    // VM_xxx classes executed on host jdk and substitutes a value that can be
    // fixed up later when those objects are copied from host jdk to bootimage.
    //
    BootImageMap.init();
    enableObjectAddressRemapper();

    //
    // Initialize rvm classes for use in "bootimage writing" mode.
    // These rvm classes are used two ways:
    //   - they are used now, by host jdk, to create the bootimage
    //   - they are used later, by target rvm, to execute the bootimage
    //
    if (trace) say("starting up");
    try {
      VM.initForBootImageWriter(bootImageRepositoriesAtBuildTime,
                                bootImageCompilerArgs);
    } catch (VM_ResolutionException e) {
      fail("unable to initialize VM: "+e);
    }

    //
    // Create (in host jdk address space) the rvm objects that will be
    // needed at run time to execute enough of the virtual machine
    // to dynamically load and compile the remainder of itself.
    //
    try {
      createBootImageObjects(bootImageTypeNames);
    } catch (VM_ResolutionException e) {
      fail("unable to create objects: "+e);
    } catch (IllegalAccessException e) {
      fail("unable to create objects: "+e);
    }

    //
    // No further bootimage object references should get generated.
    // If they are, they'll be assigned an objectId of "-1" (see VM_Magic)
    // and will manifest themselves as an array subscript out of bounds
    // error when BootImageMap attempts to look up the object references.
    //
    disableObjectAddressRemapper();

    ////////////////////////////////////////////////////
    // Copy rvm objects from host jdk into bootimage.
    ////////////////////////////////////////////////////

    if (trace) say("Memory available: ",
                   String.valueOf(Runtime.getRuntime().freeMemory()),
                   " bytes out of ",
                   String.valueOf(Runtime.getRuntime().totalMemory()),
                   " bytes");

    //
    // First object in image must be boot record (so boot loader will know
    // where to find it).  We'll write out an uninitialized record to
    // reserve the space, then go back and fill it in later.
    //
    if (trace) say("copying boot record");
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int bootRecordImageOffset = 0;
    try {
      bootRecordImageOffset = copyToBootImage(bootRecord);
      if (bootRecordImageOffset == OBJECT_NOT_PRESENT)
        fail("can't copy boot record");
    } catch (IllegalAccessException e) {
      fail("can't copy boot record: "+e);
    }

    //
    // Next, copy the jtoc.
    //
    if (trace) say("copying jtoc");
    int[] jtoc = VM_Statics.getSlots();
    int jtocImageOffset = 0;
    try {
      jtocImageOffset = copyToBootImage(jtoc);
      if (jtocImageOffset == OBJECT_NOT_PRESENT)
        fail("can't copy jtoc");
    } catch (IllegalAccessException e) {
      fail("can't copy jtoc: "+e);
    }

    //
    // Now, copy all objects reachable from jtoc, replacing each object id
    // that was generated by object address remapper with the actual
    // bootimage address of that object.
    //
    if (trace) say("copying statics");
    try {
      // The following seems to crash java for some reason...
      //for (int i = 0; i < VM_Statics.getNumberOfSlots(); ++i)
      for (int i = 0, n = VM_Statics.getNumberOfSlots(); i < n; ++i) {
        if (!VM_Statics.isReference(i))
          continue;

        Object jdkObject = BootImageMap.getObject(jtoc[i]);
        if (jdkObject == null)
          continue;

        if (trace) traceContext.push(jdkObject.getClass().getName(),
                                     getRvmStaticFieldName(i));
        int imageOffset = copyToBootImage(jdkObject);
        if (imageOffset == OBJECT_NOT_PRESENT) {
          // object not part of bootimage: install null reference
          if (trace) traceContext.traceObjectNotInBootImage();
          BootImage.setNullAddressWord(jtocImageOffset + (i << 2));
        } else
          BootImage.setAddressWord(jtocImageOffset + (i << 2),
                                   bootImageAddress + imageOffset);
        if (trace) traceContext.pop();
      }
    } catch (IllegalAccessException e) {
      fail("unable to copy statics: "+e);
    }

    //
    // Record startup context in boot record.
    //
    if (trace) say("updating boot record");
    int initProc = VM_Scheduler.PRIMORDIAL_PROCESSOR_ID;
    VM_Thread startupThread = VM_Scheduler.processors[initProc].activeThread;
    int[] startupStack = startupThread.stack;
    VM_Method VMboot = (VM_Method) VM.getMember("LVM;", "boot", "()V");
    INSTRUCTION[] startupCode  = VMboot.getMostRecentlyGeneratedInstructions();

    bootRecord.tiRegister  = startupThread.getLockingId();
    bootRecord.spRegister  = bootImageAddress +
                             BootImageMap.getImageOffset(startupStack) +
                             (startupStack.length << 2);
    bootRecord.ipRegister  = bootImageAddress +
                             BootImageMap.getImageOffset(startupCode);
    bootRecord.tocRegister = bootImageAddress +
                             BootImageMap.getImageOffset(jtoc);

    VM_Member VMS_processors = VM.getMember("LVM_Scheduler;", "processors",
                                            "[LVM_Processor;");
    bootRecord.processorsOffset = VMS_processors.getOffset();
    VM_Member VMS_threads = VM.getMember("LVM_Scheduler;", "threads",
                                         "[LVM_Thread;");
    bootRecord.threadsOffset = VMS_threads.getOffset();

    bootRecord.startAddress = bootImageAddress;
    bootRecord.endAddress   = bootImageAddress + BootImage.getSize();

    //
    // Copy updated boot record fields into image, overlaying uninitialized
    // values written earlier.
    //
    VM_Member VMBR_ti = VM.getMember("LVM_BootRecord;", "tiRegister", "I");
    BootImage.setFullWord(bootRecordImageOffset + VMBR_ti.getOffset(),
                          bootRecord.tiRegister);
    VM_Member VMBR_sp = VM.getMember("LVM_BootRecord;", "spRegister", "I");
    BootImage.setAddressWord(bootRecordImageOffset + VMBR_sp.getOffset(),
                             bootRecord.spRegister);
    VM_Member VMBR_ip = VM.getMember("LVM_BootRecord;", "ipRegister", "I");
    BootImage.setAddressWord(bootRecordImageOffset + VMBR_ip.getOffset(),
                             bootRecord.ipRegister);
    VM_Member VMBR_toc = VM.getMember("LVM_BootRecord;", "tocRegister", "I");
    BootImage.setAddressWord(bootRecordImageOffset + VMBR_toc.getOffset(),
                             bootRecord.tocRegister);
    VM_Member VMBR_proc = VM.getMember("LVM_BootRecord;",
                                       "processorsOffset", "I");
    BootImage.setFullWord(bootRecordImageOffset + VMBR_proc.getOffset(),
                          bootRecord.processorsOffset);
    VM_Member VMBR_thd = VM.getMember("LVM_BootRecord;", "threadsOffset", "I");
    BootImage.setFullWord(bootRecordImageOffset + VMBR_thd.getOffset(),
                          bootRecord.threadsOffset);
    VM_Member VMBR_sa = VM.getMember("LVM_BootRecord;", "startAddress", "I");
    BootImage.setAddressWord(bootRecordImageOffset + VMBR_sa.getOffset(),
                             bootRecord.startAddress);
    VM_Member VMBR_ea = VM.getMember("LVM_BootRecord;", "endAddress", "I");
    BootImage.setAddressWord(bootRecordImageOffset + VMBR_ea.getOffset(),
                             bootRecord.endAddress);

    //
    // Write image to disk.
    //
    try {
      BootImage.write(bootImageName);
    } catch (IOException e) {
      fail("unable to write bootImage: "+e);
    }

    //
    // Summarize status of types that were referenced by objects we put into
    // the bootimage but which are not, themselves, in the bootimage.  Any
    // such types had better not be needed to dynamically link in the
    // remainder of the virtual machine at run time!
    //
    // Note: currently disabled
    //
    if (false) {
      say("summarizing status of object types placed in bootimage");
      VM_Type[] types = VM_TypeDictionary.getValues();
      for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i) {
        VM_Type type = types[i];
        if (type.isPrimitiveType())
          continue;
        if (!type.isLoaded()) {
          say("type referenced but not loaded: ", type.toString());
          continue;
        }
        if (!type.isResolved()) {
          say("type referenced but not resolved: ", type.toString());
          continue;
        }
        if (!type.isInstantiated()) {
          say("type referenced but not instantiated: ", type.toString());
          continue;
        }
        if (!type.isInitialized()) {
          say("type referenced but not initialized: ", type.toString());
          continue;
        }
      }
    }

    //
    // Generate address map for debugging.
    //
    try {
      if (bootImageMapName != null)
        writeAddressMap(bootImageMapName);
    } catch (IOException e) {
      fail("unable to write address map: "+e);
    }

    if (trace) say("done");
  }

  /**
   * Read list of type names from a file.
   * Note: this method is also used by jdp debugger
   * @param fileName the name of the file containing type names
   * @return list type names
   */
  public static Vector readTypeNames(String fileName) throws IOException {
    Vector typeNames = new Vector(500);
    DataInputStream ds = new DataInputStream(new FileInputStream(fileName));

    String typeName;
    while ((typeName = ds.readLine()) != null) { // stop at EOF
      int index = typeName.indexOf('#');
      if (index >= 0) // ignore trailing comments
        typeName = typeName.substring(0, index);
      typeName = typeName.trim(); // ignore trailing whitespace
      if (typeName.length() == 0)
        continue; // ignore comment-only lines
      typeNames.addElement(typeName);
    }
    ds.close();

    return typeNames;
  }

  /**
   * Wrapper for call from jdp, when the boot image repository name has not
   * been specified.
   * @param typeNames names of rvm classes whose static fields will contain
   *                  the objects comprising the virtual machine bootimage
   * @param bootImageRepositories places to look for classes when
   *                              writing and executing bootimage
   */
  public static void createBootImageObjects(Vector typeNames,
                                            String bootImageRepositories)
    throws VM_ResolutionException, IllegalAccessException
  {
    bootImageRepositoriesAtExecutionTime = bootImageRepositories;
    bootImageRepositoriesAtBuildTime     = bootImageRepositories;
    // trace = true;
    createBootImageObjects(typeNames);
  }

  /**
   * Create (in host jdk address space) the rvm objects that will be
   * needed at run time to execute enough of the virtual machine
   * to dynamically load and compile the remainder of itself.
   *
   * Side effect: rvm objects are created in host jdk address space
   *              VM_Statics is populated
   *              "bootImageTypes" dictionary is populated with name/type pairs
   * Note:        this method is also used by jdp debugger
   *
   * @param typeNames names of rvm classes whose static fields will contain
   *                  the objects comprising the virtual machine bootimage
   */
  public static void createBootImageObjects(Vector typeNames)
    throws VM_ResolutionException, IllegalAccessException
  {
    VM_Callbacks.notifyBootImage(typeNames.elements());

    //
    // Create types.
    //
    if (trace) say("creating");
    for (Enumeration e = typeNames.elements(); e.hasMoreElements(); ) {
      //
      // get type name
      //
      String typeName = (String) e.nextElement();

      //
      // create corresponding rvm type
      //
      VM_Atom name = VM_Atom.findOrCreateAsciiAtom(typeName);
      VM_Type type = VM_ClassLoader.findOrCreateType(name);
      if (type.isClassType())
        type.asClass().markAsBootImageClass();

      //
      // convert type name from internal form to external form
      // ie:    Ljava/lang/Object;   -->     java.lang.Object
      //       [Ljava/lang/Object;   -->   [Ljava.lang.Object;
      //
      // NOTE: duplicate functionality.  There is a method that does the same.
      //
      typeName = typeName.replace('/','.');
      if (typeName.startsWith("L"))
        typeName = typeName.substring(1, typeName.length() - 1);

      //
      // record name/type pair for later lookup by getRvmType()
      //
      bootImageTypes.put(typeName, type);
    }

    if (trace) say(String.valueOf(bootImageTypes.size()), " types");

    //
    // Load class files.
    //
    if (trace) say("loading");
    for (Enumeration e = bootImageTypes.elements(); e.hasMoreElements(); ) {
      VM_Type type = (VM_Type) e.nextElement();
      // if (trace) say("loading ", type);
      type.load();
    }

    //
    // Lay out fields and method tables.
    //
    if (trace) say("resolving");
    for (Enumeration e = bootImageTypes.elements(); e.hasMoreElements(); ) {
      VM_Type type = (VM_Type) e.nextElement();
      // if (trace) say("resolving ", type);
      type.resolve();
    }

    //
    // Compile methods and populate jtoc with literals, TIBs, and machine code.
    //
    if (trace) say("instantiating");
    for (Enumeration e = bootImageTypes.elements(); e.hasMoreElements(); ) {
      VM_Type type = (VM_Type) e.nextElement();
      if (trace) say("instantiating " + type);
      type.instantiate();
      System.gc();
    }

    //
    // Create stack, thread, and processor context in which rvm will begin
    // execution.
    //
    int initProc = VM_Scheduler.PRIMORDIAL_PROCESSOR_ID;
    VM_Thread startupThread = new VM_Thread(new int[STACK_SIZE_BOOT >> 2]);
    VM_Scheduler.processors[initProc].activeThread = startupThread;
    // sanity check for bootstrap loader
    startupThread.stack[(STACK_SIZE_BOOT >> 2) - 1] = 0xdeadbabe;

    //
    // Tell rvm where to find itself at execution time.
    // This may not be the same place it was at build time, ie. if image is
    // moved to another machine with different directory structure.
    //
    VM_ClassLoader.setVmRepositories(bootImageRepositoriesAtExecutionTime);

    //
    // Finally, populate jtoc with static field values.
    // This is equivalent to the VM_Class.initialize() phase that would have
    // executed each class's static constructors at run time.  We simulate
    // this by copying statics created in the host rvm into the appropriate
    // slots of the jtoc.
    //
    if (trace) say("populating jtoc with static fields");
    for (Enumeration e = bootImageTypes.elements(); e.hasMoreElements(); ) {
      VM_Type rvmType = (VM_Type) e.nextElement();
      if (trace) say(" jtoc for ", rvmType.getName());
      if (!rvmType.isClassType())
        continue; // arrays and primitives have no static fields

      Class jdkType = getJdkType(rvmType);
      if (jdkType == null && trace) {
        say("host has no class \"" + rvmType.getName() + "\"");
      }

      VM_Field rvmFields[] = rvmType.getStaticFields();
      for (int j = 0; j < rvmFields.length; ++j) {
        VM_Field rvmField     = rvmFields[j];
        VM_Type  rvmFieldType = rvmField.getType();
        int      rvmFieldSlot = (rvmField.getOffset() >>> 2);
        String   rvmFieldName = rvmField.getName().toString();
        Field    jdkFieldAcc  = null;

	if (jdkType != null) 
	    jdkFieldAcc = getJdkFieldAccessor(jdkType, rvmFieldName);

        if (jdkFieldAcc == null) {
	    if (jdkType != null) {
		if (trace) traceContext.push(rvmFieldType.toString(),
					     jdkType.getName(), rvmFieldName);
		if (trace) traceContext.traceFieldNotInHostJdk();
		if (trace) traceContext.pop();
	    }
	    VM_Statics.setSlotContents(rvmFieldSlot, 0);
	    BootImage.countNulledReference();
	    continue;
        }

        // if (trace) say("populating jtoc slot ", rvmFieldSlot, " with ",
        //                rvmField);
        if (rvmFieldType.isPrimitiveType()) {
          // field is logical or numeric type
          if (rvmFieldType.equals(VM_Type.BooleanType))
            VM_Statics.setSlotContents(rvmFieldSlot,
                                       jdkFieldAcc.getBoolean(null) ? 1 : 0);
          else if (rvmFieldType.equals(VM_Type.ByteType))
            VM_Statics.setSlotContents(rvmFieldSlot,
                                       jdkFieldAcc.getByte(null));
          else if (rvmFieldType.equals(VM_Type.CharType))
            VM_Statics.setSlotContents(rvmFieldSlot,
                                       jdkFieldAcc.getChar(null));
          else if (rvmFieldType.equals(VM_Type.ShortType))
            VM_Statics.setSlotContents(rvmFieldSlot,
                                       jdkFieldAcc.getShort(null));
          else if (rvmFieldType.equals(VM_Type.IntType))
            VM_Statics.setSlotContents(rvmFieldSlot,
                                       jdkFieldAcc.getInt(null));
          else if (rvmFieldType.equals(VM_Type.LongType)) {
	    // note: Endian issues handled in setSlotContents.
	    VM_Statics.setSlotContents(rvmFieldSlot,
				       jdkFieldAcc.getLong(null));
          }
          else if (rvmFieldType.equals(VM_Type.FloatType)) {
            float f = jdkFieldAcc.getFloat(null);
            VM_Statics.setSlotContents(rvmFieldSlot,
                                       Float.floatToIntBits(f));
          }
          else if (rvmFieldType.equals(VM_Type.DoubleType)) {
            double d = jdkFieldAcc.getDouble(null);
	    // note: Endian issues handled in setSlotContents.
	    VM_Statics.setSlotContents(rvmFieldSlot,
				       Double.doubleToLongBits(d));
          }
          else
            fail("unexpected field type: " + rvmFieldType);
        } else {
          // field is reference type
          Object o = jdkFieldAcc.get(null);
          VM_Statics.setSlotContents(rvmFieldSlot,
                                     VM_Magic.objectAsAddress(o));
        }
      }
    }
  }

  private static final int LARGE_SIZE = 70000;
  private static int depth = -1;
  private static final String SPACES = "                                                                                                                                                                                                                                                                                                                                ";

  /** INTERNAL KLUDGE */
  private static final int GC_FREQUENCY = 1000;
  private static int gcKludge = 0;

  /**
   * Copy an object (and, recursively, any of its fields or elements that
   * are references) from host jdk address space into image.
   *
   * @param jdkObject object to be copied
   * @return offset of copied object within image, in bytes
   *         (OBJECT_NOT_PRESENT --> object not copied:
   *            it's not part of bootimage)
   */
  private static int copyToBootImage(Object jdkObject)
    throws IllegalAccessException
  {
    //
    // don't copy an object twice
    //
    BootImageMap.Entry mapEntry =
      BootImageMap.findOrCreateEntry(jdkObject);
    if (mapEntry.imageOffset != OBJECT_NOT_ALLOCATED)
      return mapEntry.imageOffset; // object already copied

    // KLUDGE: gc every so many objects
    if (gcKludge++ == GC_FREQUENCY) {
      System.gc();
      gcKludge = 0;
    }

    if (detailed_trace) depth++;

    //
    // fetch object's type information
    //
    Class   jdkType = jdkObject.getClass();
    VM_Type rvmType = getRvmType(jdkType);
    if (rvmType == null) {
      if (detailed_trace) traverseObject(jdkObject);
      if (detailed_trace) depth--;
      return OBJECT_NOT_PRESENT; // object not part of bootimage
    }

    //
    // copy object to image
    //
    if (jdkType.isArray()) {
      VM_Array rvmArrayType = rvmType.asArray();

      //
      // allocate space in image
      //
      int arrayCount       = Array.getLength(jdkObject);
      int arraySize        = rvmArrayType.getInstanceSize(arrayCount);
      int arrayImageOffset = BootImage.allocateArray(arraySize, arrayCount);
      mapEntry.imageOffset = arrayImageOffset;

      if (detailed_trace) say(SPACES.substring(0,depth+1), "Copying array, type=", jdkType.getName(), " length=", String.valueOf(arrayCount), "; instance size=", String.valueOf(arraySize));
      if (detailed_trace && arraySize >= LARGE_SIZE) traceContext.trace(": large object!!!");

      //
      // copy array elements from host jdk address space into image
      // recurse on values that are references
      //
      VM_Type rvmElementType = rvmArrayType.getElementType();
      if (rvmElementType.isPrimitiveType()) {
        // array element is logical or numeric type
        if (rvmElementType.equals(VM_Type.BooleanType)) {
          boolean values[] = (boolean[]) jdkObject;
          for (int i = 0; i < arrayCount; ++i)
            BootImage.setByte(arrayImageOffset + i, values[i] ? 1 : 0);
        }
        else if (rvmElementType.equals(VM_Type.ByteType)) {
          byte values[] = (byte[]) jdkObject;
          for (int i = 0; i < arrayCount; ++i)
            BootImage.setByte(arrayImageOffset + i, values[i]);
        }
        else if (rvmElementType.equals(VM_Type.CharType)) {
          char values[] = (char[]) jdkObject;
          for (int i = 0; i < arrayCount; ++i)
            BootImage.setHalfWord(arrayImageOffset + (i << 1), values[i]);
        }
        else if (rvmElementType.equals(VM_Type.ShortType)) {
          short values[] = (short[]) jdkObject;
          for (int i = 0; i < arrayCount; ++i)
            BootImage.setHalfWord(arrayImageOffset + (i << 1), values[i]);
        }
        else if (rvmElementType.equals(VM_Type.IntType)) {
          int values[] = (int[]) jdkObject;
          for (int i = 0; i < arrayCount; ++i)
            BootImage.setFullWord(arrayImageOffset + (i << 2), values[i]);
        }
        else if (rvmElementType.equals(VM_Type.LongType)) {
          long values[] = (long[]) jdkObject;
          for (int i = 0; i < arrayCount; ++i)
            BootImage.setDoubleWord(arrayImageOffset + (i << 3), values[i]);
        }
        else if (rvmElementType.equals(VM_Type.FloatType)) {
          float values[] = (float[]) jdkObject;
          for (int i = 0; i < arrayCount; ++i)
            BootImage.setFullWord(arrayImageOffset + (i << 2),
                                  Float.floatToIntBits(values[i]));
        }
        else if (rvmElementType.equals(VM_Type.DoubleType)) {
          double values[] = (double[]) jdkObject;
          for (int i = 0; i < arrayCount; ++i)
            BootImage.setDoubleWord(arrayImageOffset + (i << 3),
                                    Double.doubleToLongBits(values[i]));
        }
        else
          fail("unexpected array type: " + rvmArrayType);
      } else {
        // array element is reference type
        Object values[] = (Object []) jdkObject;
        Class jdkClass = jdkObject.getClass();
        for (int i = 0; i < arrayCount; ++i) {
          if (values[i] != null) {
            if (trace) traceContext.push(values[i].getClass().getName(),
                                         jdkClass.getName(), i);
            int imageOffset = copyToBootImage(values[i]);
            if (imageOffset == OBJECT_NOT_PRESENT) {
              // object not part of bootimage: install null reference
              if (trace) traceContext.traceObjectNotInBootImage();
              BootImage.setNullAddressWord(arrayImageOffset + (i << 2));
            } else
              BootImage.setAddressWord(arrayImageOffset + (i << 2),
                                       bootImageAddress + imageOffset);
            if (trace) traceContext.pop();
          }
        }
      }
    } else {
      VM_Class rvmScalarType = rvmType.asClass();

      //
      // allocate space in image
      //
      int scalarSize        = rvmScalarType.getInstanceSize();
      int scalarImageOffset = BootImage.allocateScalar(scalarSize);
      mapEntry.imageOffset = scalarImageOffset;

      if (detailed_trace) say(SPACES.substring(0,depth+1), "Copying object, type=", jdkType.getName(), " instance size=", String.valueOf(scalarSize));
      if (detailed_trace && scalarSize >= LARGE_SIZE) traceContext.trace(": large object!!!");

      //
      // copy object fields from host jdk address space into image
      // recurse on values that are references
      //
      VM_Field[] rvmFields = rvmScalarType.getInstanceFields();
      for (int i = 0, n = rvmFields.length; i < n; ++i) {
        VM_Field rvmField       = rvmFields[i];
        VM_Type  rvmFieldType   = rvmField.getType();
        int      rvmFieldOffset = scalarImageOffset + rvmField.getOffset();
        String   rvmFieldName   = rvmField.getName().toString();
        Field    jdkFieldAcc    = getJdkFieldAccessor(jdkType, rvmFieldName);

        if (jdkFieldAcc == null) {
          if (trace) traceContext.push(rvmFieldType.toString(),
                                       jdkType.getName(), rvmFieldName);
          if (trace) traceContext.traceFieldNotInHostJdk();
          if (trace) traceContext.pop();
          if (rvmFieldType.isPrimitiveType())
            switch (rvmField.getSize()) {
              case 4: BootImage.setFullWord(rvmFieldOffset, 0);       break;
              case 8: BootImage.setDoubleWord(rvmFieldOffset, 0L);    break;
              default:fail("unexpected field type: " + rvmFieldType); break;
            }
          else
            BootImage.setNullAddressWord(rvmFieldOffset);
          continue;
        }

        if (rvmFieldType.isPrimitiveType()) {
          // field is logical or numeric type
          if (rvmFieldType.equals(VM_Type.BooleanType))
            BootImage.setFullWord(rvmFieldOffset,
                                  jdkFieldAcc.getBoolean(jdkObject) ? 1 : 0);
          else if (rvmFieldType.equals(VM_Type.ByteType))
            BootImage.setFullWord(rvmFieldOffset,
                                  jdkFieldAcc.getByte(jdkObject));
          else if (rvmFieldType.equals(VM_Type.CharType))
            BootImage.setFullWord(rvmFieldOffset,
                                  jdkFieldAcc.getChar(jdkObject));
          else if (rvmFieldType.equals(VM_Type.ShortType))
            BootImage.setFullWord(rvmFieldOffset,
                                  jdkFieldAcc.getShort(jdkObject));
          else if (rvmFieldType.equals(VM_Type.IntType))
            BootImage.setFullWord(rvmFieldOffset,
                                  jdkFieldAcc.getInt(jdkObject));
          else if (rvmFieldType.equals(VM_Type.LongType))
            BootImage.setDoubleWord(rvmFieldOffset,
                                    jdkFieldAcc.getLong(jdkObject));
          else if (rvmFieldType.equals(VM_Type.FloatType)) {
            float f = jdkFieldAcc.getFloat(jdkObject);
            BootImage.setFullWord(rvmFieldOffset,
                                  Float.floatToIntBits(f));
          }
          else if (rvmFieldType.equals(VM_Type.DoubleType)) {
            double d = jdkFieldAcc.getDouble(jdkObject);
            BootImage.setDoubleWord(rvmFieldOffset,
                                    Double.doubleToLongBits(d));
          }
          else
            fail("unexpected field type: " + rvmFieldType);
        } else {
          // field is reference type
          Object value = jdkFieldAcc.get(jdkObject);
          Class jdkClass = jdkFieldAcc.getDeclaringClass();
          if (value != null) {
            if (trace) traceContext.push(value.getClass().getName(),
                                         jdkClass.getName(),
                                         jdkFieldAcc.getName());
            int imageOffset = copyToBootImage(value);
            if (imageOffset == OBJECT_NOT_PRESENT) {
              // object not part of bootimage: install null reference
              if (trace) traceContext.traceObjectNotInBootImage();
              BootImage.setNullAddressWord(rvmFieldOffset);
            } else
              BootImage.setAddressWord(rvmFieldOffset,
                                       bootImageAddress + imageOffset);
            if (trace) traceContext.pop();
          }
        }
      }
    }

    //
    // copy object's type information block into image, if it's not there
    // already
    //
    if (trace) traceContext.push("", jdkObject.getClass().getName(), "tib");
    int tibImageOffset = copyToBootImage(rvmType.getTypeInformationBlock());
    if (trace) traceContext.pop();
    if (tibImageOffset == OBJECT_NOT_ALLOCATED)
      fail("can't copy tib for " + jdkObject);

    //
    // set object's .tib field
    //
    BootImage.setAddressWord(mapEntry.imageOffset + OBJECT_TIB_OFFSET,
                             bootImageAddress + tibImageOffset);

    if (detailed_trace) depth--;
    return mapEntry.imageOffset;
  }

  private static final int OBJECT_HEADER_SIZE = 8;
  private static Hashtable traversed = null;
  private static final Integer VISITED = new Integer(0);

  /**
   * Traverse an object (and, recursively, any of its fields or elements that
   * are references) in host jdk address space.
   *
   * @param jdkObject object to be traversed
   * @return offset of copied object within image, in bytes
   *         (OBJECT_NOT_PRESENT --> object not copied:
   *            it's not part of bootimage)
   */
  private static int traverseObject(Object jdkObject)
    throws IllegalAccessException
  {
    //
    // don't traverse an object twice
    //
    final Object wrapper = jdkObject;
    Object key = new Object() {
      public int hashCode() { return System.identityHashCode(wrapper); }
      public boolean equals(Object o) {
        return getClass() == o.getClass() && hashCode() == o.hashCode();
      }
    };
    Integer sz = (Integer) traversed.get(key);
    if (sz != null) return sz.intValue(); // object already traversed
    traversed.put(key, VISITED);

    if (detailed_trace) depth++;

    //
    // fetch object's type information
    //
    Class jdkType = jdkObject.getClass();
    int size = OBJECT_HEADER_SIZE;

    //
    // recursively traverse object
    //
    if (jdkType.isArray()) {
      size += 4; // length
      int arrayCount       = Array.getLength(jdkObject);
      //
      // traverse array elements
      // recurse on values that are references
      //
      Class jdkElementType = jdkType.getComponentType();
      if (jdkElementType.isPrimitive()) {
        // array element is logical or numeric type
        if (jdkElementType == Boolean.TYPE) {
          size += arrayCount*4;
        } else if (jdkElementType == Byte.TYPE) {
          size += arrayCount*1;
        } else if (jdkElementType == Character.TYPE) {
          size += arrayCount*2;
        } else if (jdkElementType == Short.TYPE) {
          size += arrayCount*2;
        } else if (jdkElementType == Integer.TYPE) {
          size += arrayCount*4;
        } else if (jdkElementType == Long.TYPE) {
          size += arrayCount*8;
        } else if (jdkElementType == Float.TYPE) {
          size += arrayCount*4;
        } else if (jdkElementType == Double.TYPE) {
          size += arrayCount*8;
        } else
          fail("unexpected array type: " + jdkType);
      } else {
        // array element is reference type
        size += arrayCount*4;
        Object values[] = (Object []) jdkObject;
        for (int i = 0; i < arrayCount; ++i) {
          if (values[i] != null) {
            if (trace) traceContext.push(values[i].getClass().getName(),
                                         jdkType.getName(), i);
            traverseObject(values[i]);
            if (trace) traceContext.pop();
          }
        }
      }
      if (detailed_trace) say(SPACES.substring(0,depth+1), "Traversed array, type=", jdkType.getName(), " length=", String.valueOf(arrayCount), "; total size=", String.valueOf(size));
      if (detailed_trace && size >= LARGE_SIZE) traceContext.trace(": large object!!!");
    } else {
      //
      // traverse object fields
      // recurse on values that are references
      //
      for (Class type = jdkType; type != null; type = type.getSuperclass()) {
        Field[] jdkFields = type.getDeclaredFields();
        for (int i = 0, n = jdkFields.length; i < n; ++i) {
          Field  jdkField       = jdkFields[i];
          jdkField.setAccessible(true);
          Class  jdkFieldType   = jdkField.getType();

          if (jdkFieldType.isPrimitive()) {
            // field is logical or numeric type
            if (jdkFieldType == Boolean.TYPE)
              size += 4;
            else if (jdkFieldType == Byte.TYPE)
              size += 4;
            else if (jdkFieldType == Character.TYPE)
              size += 4;
            else if (jdkFieldType == Short.TYPE)
              size += 4;
            else if (jdkFieldType == Integer.TYPE)
              size += 4;
            else if (jdkFieldType == Long.TYPE)
              size += 8;
            else if (jdkFieldType == Float.TYPE)
              size += 4;
            else if (jdkFieldType == Double.TYPE)
              size += 8;
            else
              fail("unexpected field type: " + jdkFieldType);
          } else {
            // field is reference type
            size += 4;
            Object value = jdkField.get(jdkObject);
            if (value != null) {
              if (trace) traceContext.push(value.getClass().getName(),
                                           jdkType.getName(),
                                           jdkField.getName());
              traverseObject(value);
              if (trace) traceContext.pop();
            }
          }
        }
      }
      if (detailed_trace) say(SPACES.substring(0,depth+1), "Traversed object, type=", jdkType.getName(), " total size=", String.valueOf(size));
      if (detailed_trace && size >= LARGE_SIZE) traceContext.trace(": large object!!!");
    }

    traversed.put(key, new Integer(size));
    if (detailed_trace) depth--;
    return size;
  }

  /**
   * Begin recording objects referenced by rvm classes during
   * loading/resolution/instantiation.  These references will be converted
   * to bootimage addresses when those objects are copied into bootimage.
   */
  private static void enableObjectAddressRemapper() {
    VM_Magic.setObjectAddressRemapper(
      new VM_ObjectAddressRemapper() {
        public int objectAsAddress(Object jdkObject) {
          return BootImageMap.findOrCreateEntry(jdkObject).objectId;
        }

        public Object addressAsObject(int address) {
          VM.assert(VM.NOT_REACHED);
          return null;
        }
      }
    );
  }

  /**
   * Stop recording objects referenced by rvm classes during
   * loading/resolution/instantiation.
   */
  private static void disableObjectAddressRemapper() {
    VM_Magic.setObjectAddressRemapper(null);

    // Remove bootimage writer's remapper object that was present when jtoc
    // was populated with jdk objects. It's not part of the bootimage and we
    // don't want to see warning messages about it when the bootimage is
    // written.

    VM_Member remapper = VM.getMember("LVM_Magic;", "objectAddressRemapper",
                                      "LVM_ObjectAddressRemapper;");
    int remapperIndex = remapper.getOffset() >>> 2;
    VM_Statics.setSlotContents(remapperIndex, 0);
  }

  /**
   * Obtain rvm type corresponding to host jdk type.
   *
   * @param jdkType jdk type
   * @return rvm type (null --> type does not appear in list of classes
   *         comprising bootimage)
   */
  private static VM_Type getRvmType(Class jdkType) {
    return (VM_Type) bootImageTypes.get(jdkType.getName());
  }

  /**
   * Obtain host jdk type corresponding to target rvm type.
   *
   * @param rvmType rvm type
   * @return jdk type (null --> type does not exist in host namespace)
   */
  private static Class getJdkType(VM_Type rvmType) {
    try {
      return Class.forName(rvmType.getName());
    } catch (Throwable x) {
      if (trace) {
	  say(x.toString());
      }
      return null;
    }
  }

  /**
   * Obtain accessor via which a field value may be fetched from host jdk
   * address space.
   *
   * @param jdkClass class whose field is sought
   * @param fieldName name of field sought
   * @return field accessor (null --> host class does not have specified field)
   */
  private static Field getJdkFieldAccessor(Class jdkClass, String fieldName) {
    for (Class cls = jdkClass; cls != null; cls = cls.getSuperclass()) {
      Field[] fields = cls.getDeclaredFields();
      for (int i = 0; i < fields.length; ++i) {
        Field f = fields[i];
        if (f.getName().equals(fieldName)) {
          f.setAccessible(true);
          return f;
        }
      }
    }
    return null;
  }

  /**
   * Figure out name of static rvm field whose value lives in specified jtoc
   * slot.
   *
   * @param jtocSlot jtoc slot number
   * @return field name
   */
  private static String getRvmStaticFieldName(int jtocSlot) {
    VM_Type[] types = VM_TypeDictionary.getValues();
    for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i) {
      VM_Type type = types[i];
      if (type.isPrimitiveType())
        continue;
      if (!type.isLoaded())
        continue;
      VM_Field rvmFields[] = types[i].getStaticFields();
      for (int j = 0; j < rvmFields.length; ++j) {
        VM_Field rvmField = rvmFields[j];
        if ((rvmField.getOffset() >>> 2) == jtocSlot)
          return rvmField.getDeclaringClass().getName() + "." +
                 rvmField.getName();
      }
    }
    return VM_Statics.getSlotDescriptionAsString(jtocSlot) +
           "@jtoc[" + jtocSlot + "]";
  }

  /**
   * Write method address map for use with dbx debugger.
   *
   * @param fileName name of file to write the map to
   */
  private static void writeAddressMap(String mapFileName) throws IOException {
    if (trace) say("writing ", mapFileName);

    FileOutputStream fos = new FileOutputStream(mapFileName);
    BufferedOutputStream bos = new BufferedOutputStream(fos, 128);
    PrintStream out = new PrintStream(bos, false);

    out.println("#!/bin/ksh");
    out.println("# Note: to sort by \"code\" address, type \"ksh <name-of-this-file>\".");
    out.println();
    out.println("(/bin/grep 'code     0x' | /bin/sort -k 4.3,4) << EOF-EOF-EOF");
    out.println();
    out.println("JTOC Map");
    out.println("--------");
    out.println("slot  offset     category contents            details");
    out.println("----  ------     -------- --------            -------");

    String pad = "        ";

    for (int jtocSlot = 0;
         jtocSlot < VM_Statics.getNumberOfSlots();
         ++jtocSlot)
    {
      byte   description = VM_Statics.getSlotDescription(jtocSlot);
      String category;
      int    rawslot;
      int    rawslot1;  // used for longs, doubles and address conversion
      String contents;
      String details;

      switch (description) {
        case VM_Statics.EMPTY:
          category = "unused ";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          contents = VM.intAsHexString(rawslot) + pad;
          details  = "";
          break;

        case VM_Statics.INT_LITERAL:
          category = "literal";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          contents = VM.intAsHexString(rawslot) + pad;
          details  = VM_Statics.getSlotContentsAsInt(jtocSlot) + "";
          break;

        case VM_Statics.FLOAT_LITERAL:
          category = "literal";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          contents = VM.intAsHexString(rawslot) + pad;
          details  = Float.intBitsToFloat(rawslot) + "F";
          break;

        case VM_Statics.LONG_LITERAL:
          category = "literal";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          rawslot1 = VM_Statics.getSlotContentsAsInt(jtocSlot + 1);
          contents = VM.intAsHexString(rawslot) +
                     VM.intAsHexString(rawslot1).substring(2);
          details  = VM_Statics.getSlotContentsAsLong(jtocSlot) + "L";
          break;

        case VM_Statics.DOUBLE_LITERAL:
          category = "literal";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          rawslot1 = VM_Statics.getSlotContentsAsInt(jtocSlot + 1);
          contents = VM.intAsHexString(rawslot) +
                     VM.intAsHexString(rawslot+1).substring(2);
          details  = Double.longBitsToDouble(
                       VM_Statics.getSlotContentsAsLong(jtocSlot)) + "D";
          break;

        case VM_Statics.STRING_LITERAL:
          category = "literal";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          rawslot1 = BootImageMap.getImageAddress(bootImageAddress,
                       BootImageMap.getObject(rawslot));
          contents = VM.intAsHexString(rawslot1) + pad;
          details  = "\"" +
                     ((String) BootImageMap.getObject(rawslot)).
                       replace('\n', ' ') +
                     "\"";
          break;

        case VM_Statics.NUMERIC_FIELD:
          category = "field  ";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          contents = VM.intAsHexString(rawslot) + pad;
          details  = getRvmStaticFieldName(jtocSlot);
          break;

        case VM_Statics.WIDE_NUMERIC_FIELD:
          category = "field  ";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          rawslot1 = VM_Statics.getSlotContentsAsInt(jtocSlot + 1);
          contents = VM.intAsHexString(rawslot) +
                     VM.intAsHexString(rawslot1).substring(2);
          details  = getRvmStaticFieldName(jtocSlot);
          break;

        case VM_Statics.REFERENCE_FIELD:
          category = "field  ";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          rawslot1 = BootImageMap.getImageAddress(bootImageAddress,
                       BootImageMap.getObject(rawslot));
          contents = VM.intAsHexString(rawslot1) + pad;
          details  = getRvmStaticFieldName(jtocSlot);
          break;

        case VM_Statics.METHOD:
          category = "code   ";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          rawslot1 = BootImageMap.getImageAddress(bootImageAddress,
                       BootImageMap.getObject(rawslot));
          contents = VM.intAsHexString(rawslot1) + pad;
          details = "<?>";
          // search for a method that this is the code for
          INSTRUCTION[] instructions =
            (INSTRUCTION[]) BootImageMap.getObject(rawslot);
          VM_CompiledMethod[] compiledMethods =
            VM_ClassLoader.getCompiledMethods();
          for (int i = 0; i < VM_ClassLoader.numCompiledMethods(); ++i) {
            VM_CompiledMethod compiledMethod = compiledMethods[i];
            if (compiledMethod != null &&
                compiledMethod.getInstructions() == instructions)
            {
              details = compiledMethod.getMethod().toString();
              break;
            }
          }
          break;

        case VM_Statics.TIB:
          category = "tib    ";
          rawslot  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          rawslot1 = BootImageMap.getImageAddress(bootImageAddress,
                       BootImageMap.getObject(rawslot));
          contents = VM.intAsHexString(rawslot1) + pad;
          details = "<?>";
          // search for a type that this is the TIB for
          VM_Type[] types = VM_TypeDictionary.getValues();
          for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i)
            if (types[i].getTibSlot() == jtocSlot) {
              details = types[i].toString();
              break;
            }
          break;

        default:
          category = "<?>    ";
          contents = "<?>       " + pad;
          details  = "<?>";
          break;
      }

      out.println((jtocSlot + "      ").substring(0,6) +
                  VM.intAsHexString(jtocSlot << 2) + " " +
                  category + "  " + contents + "  " + details);

      if ((description & VM_Statics.WIDE_TAG) != 0)
        jtocSlot += 1;
    }

    out.println();
    out.println("Method Map");
    out.println("----------");
    out.println("                          address             method");
    out.println("                          -------             ------");
    out.println();
    // If java crashes, replace the following with
    //for (int i = 0, n = VM_MethodDictionary.getNumValues(); i < n; ++i)
    for (int i = 0; i < VM_MethodDictionary.getNumValues(); ++i) {
      VM_Method method = VM_MethodDictionary.getValue(i);
      if (method == null)       continue;
      if (!method.isLoaded()) continue; // bogus or unresolved
      if (!method.isCompiled()) continue;
      Object instructions = method.getMostRecentlyGeneratedInstructions();
      int code = BootImageMap.getImageAddress(bootImageAddress, instructions);
      out.println(".     .          code     " + VM.intAsHexString(code) +
                  "          " + method);
    }

    out.println();
    out.println("EOF-EOF-EOF");
    out.flush();
    out.close();
  }

  // /**
  //  * Write map for use by Ton's jdp debugger.
  //  */
  // public static void writeJdpMap() {
  //   VM_Member m;
  //   m = VM.getMember("LVM_AtomDictionary;", "keys",   "[LVM_Atom;");
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_AtomDictionary;", "values", "[LVM_Atom;");
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_AtomDictionary;", "nextId", "I"         );
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_AtomDictionary;", "chains", "[[I"       );
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //
  //   m = VM.getMember("LVM_TypeDictionary;", "keys",   "[LVM_Atom;");
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_TypeDictionary;", "values", "[LVM_Type;");
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_TypeDictionary;", "nextId", "I"         );
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_TypeDictionary;", "chains", "[[I"       );
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //
  //   m = VM.getMember("LVM_FieldDictionary;", "keys",   "[LVM_Triplet;");
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_FieldDictionary;", "values", "[LVM_Field;"  );
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_FieldDictionary;", "nextId", "I"            );
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_FieldDictionary;", "chains", "[[I"          );
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //
  //   m = VM.getMember("LVM_ClassLoader;", "compiledMethods",
  //                    "[LVM_CompiledMethod;"):
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  //   m = VM.getMember("LVM_ClassLoader;", "currentCompiledMethodId", "I");
  //   System.out.println((m.getOffset() >>> 2) + " xx xx xx " + m);
  // }

  /**
   * Flip the order of words in a long.
   *
   * @param value initial value
   * @return the value with words flipped
   */
  private  static long flipWords(long value) {
    return (value << 32) | (value >>> 32);
  }
}

