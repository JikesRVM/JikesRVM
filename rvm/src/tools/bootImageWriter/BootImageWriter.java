/*
 * (C) Copyright IBM Corp 2001,2002, 2004
 */

//$Id$

import java.util.Hashtable;
import java.util.Vector;
import java.util.Stack;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Arrays;

import java.io.*;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.Thread;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.jni.*;
import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.unboxed.*;

/**
 * Construct a RVM virtual machine bootimage.
 * <pre>
 * Invocation args:
 *    -n  <filename>           list of typenames to be written to bootimage
 *    -X:bc:<bcarg>            pass bcarg to bootimage compiler as command
 *                             line argument
 *    -classpath <path>        list of places to look for bootimage classes
 *    -littleEndian            write words to bootimage in little endian format?
 *    -trace                   talk while we work?
 *    -detailed                print detailed info on traversed objects
 *    -demographics            show summary of how boot space is used
 *    -ia <addr>               address where boot image starts
 *    -o <filename>            place to put bootimage
 *    -m <filename>            place to put bootimage map
 *    -profile                 time major phases of bootimage writing
 *    -xclasspath <path>       OBSOLETE compatibility aid
 * </pre>
 * @author Derek Lieber
 * @version 03 Jan 2000
 * (complete rewrite of John Barton's original, this time using java2
 * reflection)
 *
 * @modified Steven Augart 16 Mar 2004	Fixes to bootstrap under Kaffe

 */
public class BootImageWriter extends BootImageWriterMessages
  implements BootImageWriterConstants {

  public static int PARALLELIZE = 0;

  public static void setVerbose(int value) {
    verbose = value;
  }

  /**
   * How much talking while we work?
   */
  private static int verbose = 0;
  private static int depthCutoff = 3;  // how deeply in the recursive copy do we continue to blather

  /**
   * Places to look for classes when building bootimage.
   */
  private static String bootImageRepositoriesAtBuildTime;

  /**
   * Places to look for classes when executing bootimage.
   */
  private static String bootImageRepositoriesAtExecutionTime;

  /**
   * the bootimage
   */
  private static BootImage bootImage;

  /**
   * Types to be placed into bootimage, stored as key/value pairs
   * where key is a String like "java.lang.Object" or "[Ljava.lang.Object;"
   * and value is the corresponding VM_Type.
   */
  private static Hashtable bootImageTypes = new Hashtable(5000);

  /**
   * For all the scalar types to be placed into bootimage, keep
   * key/value pairs where key is a Key(jdkType) and value is
   * a FieldInfo. 
   */
  private static HashMap bootImageTypeFields;

  /**
   * Class to collecting together field information
   */
  private static class FieldInfo {
    /**
     *  Field table from JDK verion of class
     */
    Field  jdkFields[];

    /**
     *  Fields that are the one-to-one match of rvm instanceFields
     *  includes superclasses
     */
    Field  jdkInstanceFields[];

    /**
     *  Fields that are the one-to-one match of rvm staticFields
     */
    Field  jdkStaticFields[];

    /**
     *  Rvm type associated with this Field info
     */
    VM_Type rvmType;

    /**
     *  Jdk type associated with this Field info
     */
    Class jdkType;
  }

  /**
   * Key for looking up fieldInfo
   */
  private static class Key {
    /**
     * Jdk type
     */
    Object jdkType;

    /**
     * Constructor.
     * @param jdkType the type to associate with the key
     */
    public Key(Object jdkType) { this.jdkType = jdkType; }

    /**
     * Returns a hash code value for the key.
     * @return a hash code value for this key
     */
    public int hashCode() { return System.identityHashCode(jdkType); }

    /**
     * Indicates whether some other key is "equal to" this one.
     * @param that the object with which to compare
     * @return true if this key is the same as the that argument;
     *         false otherwise
     */
    public boolean equals(Object that) {
      return (that instanceof Key) && jdkType == ((Key)that).jdkType;
    }
  }

  private static final boolean STATIC_FIELD = true;
  private static final boolean INSTANCE_FIELD = false;

  /**
   * The absolute address at which the bootImage is going to be loaded.
   */
  public static Address bootImageAddress = Address.zero();

  public static Address getBootImageAddress()  {
    if (bootImageAddress.isZero()) VM.sysFail("BootImageWrite.getBootImageAddress called before boot image established");
    return bootImageAddress;
  }

  /**
   * Write words to bootimage in little endian format?
   */
  private static boolean littleEndian = false;

  /**
   * Show how boot space is used by type
   */
  private static boolean demographics = false;

  /**
   * Report time costs of each stage in bootimage writing and
   * report any methods that take 'too long' to compile.
   */
  private static boolean profile = false;

  /**
   * What is the threshold (in ms) for compilation of a single class
   * to be reported as excessively long (when profiling is true)
   */
  private static final int classCompileThreshold = 5000;
  
  /**
   * A wrapper around the calling context to aid in tracing.
   */
  private static class TraceContext extends Stack {
    /**
     * Report a field that is part of our library's (GNU Classpath's)
     implementation, but not the host JDK's implementation.
     */
    public void traceFieldNotInHostJdk() {
      traceNulledWord(": field not in host jdk");
    }

    /* Report a field that is an instance field in the host JDK but a static
       field in ours.  */
    public void traceFieldNotStaticInHostJdk() {
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
  public static void main(String args[]) 
    throws Exception
  {
    String   bootImageName         = null;
    String   bootImageMapName      = null;
    Vector   bootImageTypeNames    = null;
    String   bootImageTypeNamesFile = null;
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
        if (++i >= args.length)
          fail("argument syntax error: Got a -o flag without a following image file name");
        bootImageName = args[i];
        continue;
      }
      // name of map file
      if (args[i].equals("-m")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -m flag without a following bootImageMap file name");
        bootImageMapName = args[i];
        continue;
      }
      // image address
      if (args[i].equals("-ia")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -ia flag without a following image address");
        bootImageAddress = Address.fromIntZeroExtend(Integer.decode(args[i]).intValue());
        continue;
      }
      // file containing names of types to be placed into bootimage
      if (args[i].equals("-n")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -n flag without a following file name");
          
        if (bootImageTypeNamesFile != null )
          fail("argument syntax error: We've already read in the bootImageTypeNames from"
               + bootImageTypeNamesFile + "; just got another -n argument"
               + " telling us to read them from " + args[i]);
        bootImageTypeNamesFile = args[i];

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
        if (++i >= args.length)
          fail("argument syntax error: Got a -classpath flag without a following classpath for build-time RVM components");
        bootImageRepositoriesAtBuildTime = args[i];
        continue;
      }
      // places where rvm components live, at execution time
      if (args[i].equals("-xclasspath")) {
        if (++i >= args.length)
          fail("argument syntax error: Got an -xclasspath flag without a following execution-time classpath for RVM components");
        bootImageRepositoriesAtExecutionTime = args[i];
        continue;
      }
      // generate trace messages while writing bootimage (for debugging)
      if (args[i].equals("-trace")) {
        verbose = 1;
        continue;
      }
      // generate info by type
      if (args[i].equals("-demographics")) {
        demographics = true;
        continue;
      }
      // parallelize
      if (args[i].equals("-parallelize=")) {
        PARALLELIZE = Integer.parseInt(args[i].substring(13));
        continue;
      }
      // profile
      if (args[i].equals("-profile")) {
        profile = true;
        continue;
      }
      // generate detailed information about traversed objects (for debugging)
      if (args[i].equals("-detailed")) {
        verbose = 2;
        continue;
      }
      // write words to bootimage in little endian format
      if (args[i].equals("-littleEndian")) {
        littleEndian = true;
        continue;
      }
      fail("unrecognized command line argument: " + args[i]);
    }

    if (verbose >= 2)
      traversed = new Hashtable(500);

    //
    // Check command line directives for correctness.
    //
    if (bootImageName == null)
      fail("please specify \"-o <boot-image-filename>\"");

    if (bootImageTypeNamesFile == null)
      fail("please specify \"-n <boot-image-type-names-filename>\"");

    if (bootImageRepositoriesAtBuildTime == null)
      fail("please specify \"-classpath <path>\"");

    if (bootImageRepositoriesAtExecutionTime == null)
      bootImageRepositoriesAtExecutionTime = bootImageRepositoriesAtBuildTime;

    if (bootImageAddress.isZero())
      fail("please specify boot-image address with \"-ia <addr>\"");
    if (!(bootImageAddress.toWord().and(Word.fromIntZeroExtend(0x00FFFFFF)).isZero()))
      fail("please specify a boot-image address that is a multiple of 0x01000000");
    // MM_Interface.checkBootImageAddress(bootImageAddress);

    //
    // Initialize the bootimage.
    // Do this earlier than we logically need to because we need to
    // allocate a massive byte[] that will hold the bootimage in core and
    // on some host JDKs it is essential to do that early while there
    // is still lots of virgin storage left. 
    // (need to get contiguous storage before it gets fragmented by pinned objects)
    //
    bootImage = new BootImage(littleEndian, verbose >= 1);

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
    if (verbose >= 1) say("starting up");
    //    try {
      VM.initForBootImageWriter(bootImageRepositoriesAtBuildTime,
                                bootImageCompilerArgs);
      //    } catch (Exception e) {
      //      fail("unable to initialize VM: "+e);
      //    }

    //
    // Create (in host jdk address space) the rvm objects that will be
    // needed at run time to execute enough of the virtual machine
    // to dynamically load and compile the remainder of itself.
    //
    long startTime = 0;
    long stopTime = 0;
    if (profile) startTime = System.currentTimeMillis();
    try {
      bootImageTypeNames = readTypeNames(bootImageTypeNamesFile);
    } catch (IOException e) {
      fail("unable to read the type names from "+ bootImageTypeNamesFile 
           +": "+e);
    }
    if (profile) {
      stopTime = System.currentTimeMillis();
      System.out.println("PROF: readingTypeNames "+(stopTime-startTime)+" ms");
    }
      
    if (profile) startTime = System.currentTimeMillis();
    try {
      createBootImageObjects(bootImageTypeNames, bootImageTypeNamesFile);
    } catch (Exception e) {
      e.printStackTrace();
      fail("unable to create objects: "+e);
    }
    if (profile) {
      stopTime = System.currentTimeMillis();
      System.out.println("PROF: createBootImageObjects "+(stopTime-startTime)+" ms");
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

    VM.writingImage = true;

    if (verbose >= 1) say("Memory available: ",
                          String.valueOf(Runtime.getRuntime().freeMemory()),
                          " bytes out of ",
                          String.valueOf(Runtime.getRuntime().totalMemory()),
                          " bytes");

    if (profile) startTime = System.currentTimeMillis();

    //
    // First object in image must be boot record (so boot loader will know
    // where to find it).  We'll write out an uninitialized record and not recurse 
    // to reserve the space, then go back and fill it in later.
    //
    if (verbose >= 1) say("copying boot record");
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int bootRecordImageOffset = 0;
    try {
      // copy just the boot record
      bootRecordImageOffset = copyToBootImage(bootRecord, true, -1, null); 
      if (bootRecordImageOffset == OBJECT_NOT_PRESENT)
        fail("can't copy boot record");
    } catch (IllegalAccessException e) {
      fail("can't copy boot record: "+e);
    }

    //
    // Next, copy the jtoc.
    //
    if (verbose >= 1) say("copying jtoc");
    int jtocImageOffset = 0;
    try {
      jtocImageOffset = copyToBootImage(VM_Statics.getSlotsAsIntArray(), false, -1, null);
      if (jtocImageOffset == OBJECT_NOT_PRESENT)
        fail("can't copy jtoc");
    } catch (IllegalAccessException e) {
      fail("can't copy jtoc: "+e);
    }

    if (bootImageAddress.add(jtocImageOffset).NE(bootRecord.tocRegister))
      fail("mismatch in JTOC placement "+(jtocImageOffset + bootImageAddress.toInt())+" != "+bootRecord.tocRegister);

    //
    // Now, copy all objects reachable from jtoc, replacing each object id
    // that was generated by object address remapper with the actual
    // bootimage address of that object.
    //
    if (verbose >= 1) say("copying statics");
    try {
      // The following seems to crash java for some reason...
      //for (int i = 0; i < VM_Statics.getNumberOfSlots(); ++i)
      for (int i = 0, n = VM_Statics.getNumberOfSlots(); i < n; i += VM_Statics.getSlotSize(i)) {

        jtocCount = i; // for diagnostic
        if (!VM_Statics.isReference(i))
          continue;

        //-#if RVM_FOR_32_ADDR
        int objCookie = VM_Statics.getSlotContentsAsInt(i);
        //-#endif
        //-#if RVM_FOR_64_ADDR
        int objCookie = (int) VM_Statics.getSlotContentsAsLong(i);
        //-#endif
        // if (verbose >= 3)
        // say("       jtoc[", String.valueOf(i), "] = ", String.valueOf(objCookie));
        Object jdkObject = BootImageMap.getObject(objCookie);
        if (jdkObject == null)
          continue;

        if (verbose >= 2) traceContext.push(jdkObject.getClass().getName(),
                                            getRvmStaticFieldName(i));
        int imageOffset = copyToBootImage(jdkObject, false, -1, VM_Statics.getSlotsAsIntArray());
        if (imageOffset == OBJECT_NOT_PRESENT) {
          // object not part of bootimage: install null reference
          if (verbose >= 2) traceContext.traceObjectNotInBootImage();
          bootImage.setNullAddressWord(jtocImageOffset + (i << LOG_BYTES_IN_INT)); // jtoc is int[]!
        } else
          bootImage.setAddressWord(jtocImageOffset + (i << LOG_BYTES_IN_INT), // jtoc is int[]!
                                   bootImageAddress.add(imageOffset).toWord());
        if (verbose >= 2) traceContext.pop();
      }
    } catch (IllegalAccessException e) {
      fail("unable to copy statics: "+e);
    }
    jtocCount = -1;

    if (profile) {
      stopTime = System.currentTimeMillis();
      System.out.println("PROF: filling bootimage byte[] "+(stopTime-startTime)+" ms");
    }
    //
    // Record startup context in boot record.
    //
    if (verbose >= 1) say("updating boot record");

    int initProc = VM_Scheduler.PRIMORDIAL_PROCESSOR_ID;
    VM_Thread startupThread = VM_Scheduler.processors[initProc].activeThread;
    byte[] startupStack = startupThread.stack;
    VM_CodeArray startupCode  = VM_Entrypoints.bootMethod.getCurrentInstructions();

    bootRecord.tiRegister  = startupThread.getLockingId();
    bootRecord.spRegister  = bootImageAddress.add(BootImageMap.getImageOffset(startupStack) +
                                                          startupStack.length);
    bootRecord.ipRegister  = bootImageAddress.add(BootImageMap.getImageOffset(startupCode.getBacking()));

    bootRecord.processorsOffset = VM_Entrypoints.processorsField.getOffset();

    bootRecord.bootImageStart = bootImageAddress;
    bootRecord.bootImageEnd   = bootImageAddress.add(bootImage.getSize());

    // Update field of boot record now by re-copying
    //
    if (verbose >= 1) say("re-copying boot record (and its TIB)");
    try {
      int newBootRecordImageOffset = copyToBootImage(bootRecord, false, bootRecordImageOffset, null); 
      if (newBootRecordImageOffset != bootRecordImageOffset) {
        VM.sysWriteln("bootRecordImageOffset = ", bootRecordImageOffset);
        VM.sysWriteln("newBootRecordImageOffset = ", newBootRecordImageOffset);
        VM._assert(newBootRecordImageOffset == bootRecordImageOffset);
      }
    } catch (IllegalAccessException e) {
      fail("unable to update boot record: "+e);
    }

    //-#if RVM_WITH_GCTRACE
    /* Set the values in fields updated during the build process */
    int prevAddrOffset = VM_Entrypoints.tracePrevAddressField.getOffset();
    bootImage.setAddressWord(jtocImageOffset + prevAddrOffset, 
			     VM_MiscHeader.getBootImageLink().toWord());
    int oIDOffset = VM_Entrypoints.traceOIDField.getOffset();
    bootImage.setAddressWord(jtocImageOffset + oIDOffset, 
			     VM_MiscHeader.getOID());
    //-#endif

    //
    // Write image to disk.
    //
    if (profile) startTime = System.currentTimeMillis();
    try {
      bootImage.write(bootImageName);
    } catch (IOException e) {
      fail("unable to write bootImage: "+e);
    }

    if (profile) {
      stopTime = System.currentTimeMillis();
      System.out.println("PROF: writing RVM.map "+(stopTime-startTime)+" ms");
    }

    //
    // Show space usage in boot image by type
    //
    if (demographics) {
      spaceReport();
    }


    //
    // Summarize status of types that were referenced by objects we put into
    // the bootimage but which are not, themselves, in the bootimage.  Any
    // such types had better not be needed to dynamically link in the
    // remainder of the virtual machine at run time!
    //
    if (false) {
      VM_Type[] types = VM_Type.getTypes();
      for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i) {
        VM_Type type = types[i];
        if (type == null) continue;
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

    if (verbose >= 1) say("done");
  }

  /**
   * Print a report of space usage in the boot image.
   */
  public static void spaceReport() {
    VM_Type[] types = VM_Type.getTypes();
    VM_Type[] tempTypes = new VM_Type[types.length - FIRST_TYPE_DICTIONARY_INDEX];
    for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i) 
      tempTypes[i - FIRST_TYPE_DICTIONARY_INDEX] = types[i];
    Arrays.sort(tempTypes, new TypeComparator());
    int totalCount = 0, totalBytes = 0;
    for (int i = 0; i < tempTypes.length; i++) {
      VM_Type type = tempTypes[i];
      if (type == null) continue;
      totalCount += type.bootCount;
      totalBytes += type.bootBytes;
    }
    VM.sysWriteln("\nBoot image space report:");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    VM.sysWriteField(60, "TOTAL");
    VM.sysWriteField(15, totalCount);
    VM.sysWriteField(15, totalBytes);
    VM.sysWriteln();

    VM.sysWriteln("\nCompiled methods space report:");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    VM_CompiledMethods.spaceReport();

    VM.sysWriteln("------------------------------------------------------------------------------------------");
    VM.sysWriteln("\nBoot image space usage by types:");
    VM.sysWriteln("Type                                                               Count             Bytes");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    VM.sysWriteField(60, "TOTAL");
    VM.sysWriteField(15, totalCount);
    VM.sysWriteField(15, totalBytes);
    VM.sysWriteln();
    for (int i = 0; i < tempTypes.length; i++) {
      VM_Type type = tempTypes[i];
      if (type == null) continue;
      if (type.bootCount > 0) {
        VM.sysWriteField(60, type.toString());
        VM.sysWriteField(15, type.bootCount);
        VM.sysWriteField(15, type.bootBytes);
        VM.sysWriteln();
      }
    }
  }

  /**
   * Read list of type names from a file.
   * @param fileName the name of the file containing type names
   * @return list type names
   */
  public static Vector readTypeNames(String fileName) throws IOException {
    Vector typeNames = new Vector(500);
    //    DataInputStream ds = new DataInputStream(new FileInputStream(fileName));
    LineNumberReader in = new LineNumberReader(new FileReader(fileName));

    String typeName;
    while ((typeName = in.readLine()) != null) { // stop at EOF
      int index = typeName.indexOf('#');
      if (index >= 0) // ignore trailing comments
        typeName = typeName.substring(0, index);
      typeName = typeName.trim(); // ignore leading and trailing whitespace
      if (typeName.length() == 0)
        continue; // ignore comment-only and whitespace-only lines
      // debugging:
      VM_TypeDescriptorParsing.validateAsTypeDescriptor(typeName);
      if (VM_TypeDescriptorParsing.isValidTypeDescriptor(typeName))
        typeNames.addElement(typeName);
      else
        fail(fileName + ":" + in.getLineNumber() 
             + ": syntax error: \"" 
             + typeName + "\" does not describe any Java type.");
    }
    in.close();

    return typeNames;
  }

  /**
   * Create (in host jdk address space) the rvm objects that will be
   * needed at run time to execute enough of the virtual machine
   * to dynamically load and compile the remainder of itself.
   *
   * Side effect: rvm objects are created in host jdk address space
   *              VM_Statics is populated
   *              "bootImageTypes" dictionary is populated with name/type pairs
   *
   * @param typeNames names of rvm classes whose static fields will contain
   *                  the objects comprising the virtual machine bootimage
   */
  public static void createBootImageObjects(Vector typeNames,
                                            String bootImageTypeNamesFile) 
    throws IllegalAccessException, ClassNotFoundException {    
      VM_Callbacks.notifyBootImage(typeNames.elements());
      long startTime = 0;
      long stopTime = 0;
      
      //
      // Create types.
      //
      if (verbose >= 1) say("loading");
      if (profile) startTime = System.currentTimeMillis();
      
      for (Enumeration e = typeNames.elements(); e.hasMoreElements(); ) {
        //
        // get type name
        //
        String typeName = (String) e.nextElement();
        if (verbose >= 4)
          say("typeName:", typeName);
        

        //
        // create corresponding rvm type
        //
        VM_Type type;        
        try {
          VM_TypeReference tRef = VM_TypeReference.findOrCreate(typeName);
          type = tRef.resolve();
        } catch (NoClassDefFoundError ncdf) {
          ncdf.printStackTrace();
          fail(bootImageTypeNamesFile
               + " contains a class named \"" 
               + typeName + "\", but we can't find a class with that name: "
               + ncdf);
          return;               // NOTREACHED
        } catch (IllegalArgumentException ila) {
          /* We should've caught any illegal type names at the data validation
           * stage, when we read these in.  If not, though, 
           * VM_TypeReference.findOrCreate() will do its own sanity check.  */
          ila.printStackTrace();
          fail(bootImageTypeNamesFile
               + " is supposed to contain type names.  It contains \"" 
               + typeName + "\", which does not parse as a legal type name: "
               + ila);
          return;               // NOTREACHED
        }
        type.markAsBootImageClass();

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

      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tloading types "+(stopTime-startTime)+" ms");
      }
        
      if (verbose >= 1) say(String.valueOf(bootImageTypes.size()), " types");

      //
      // Lay out fields and method tables.
      //
      if (profile) startTime = System.currentTimeMillis();
      if (verbose >= 1) say("resolving");
      for (Enumeration e = bootImageTypes.elements(); e.hasMoreElements(); ) {
        VM_Type type = (VM_Type) e.nextElement();
        if (verbose >= 2) say("resolving " + type);
        // The resolution is supposed to be cached already.
        type.resolve();
      }
      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tresolving types "+(stopTime-startTime)+" ms");
      }

      // Set tocRegister early so opt compiler can access it to
      //   perform fixed_jtoc optimization (compile static addresses into code).

      // In the boot image, the bootrecord comes first followed by a
      // Address array and then the TOC.  To do this, we must fully
      // simulate the alignment logic in the allocation code!  Rather
      // than replicate the allocation code here, we perform dummy
      // allocations and then reset the boot image allocator.
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      VM_Class rvmBRType = getRvmType(bootRecord.getClass()).asClass();
      VM_Array intArrayType =  VM_Array.getPrimitiveArrayType(10);
      int brOffset = bootImage.allocateStorage(rvmBRType.getInstanceSize(), 
                                               VM_ObjectModel.getAlignment(rvmBRType), 
                                               VM_ObjectModel.getOffsetForAlignment(rvmBRType));
      int jtocOffset = bootImage.allocateStorage(intArrayType.getInstanceSize(0),
                                                 VM_ObjectModel.getAlignment(intArrayType),
                                                 VM_ObjectModel.getOffsetForAlignment(intArrayType));
      bootImage.resetAllocator();
      bootRecord.tocRegister = bootImageAddress.add(jtocOffset).add(intArrayType.getInstanceSize(0));

      //
      // Compile methods and populate jtoc with literals, TIBs, and machine code.
      //
      if (profile) startTime = System.currentTimeMillis();
      if (verbose >= 1) say("instantiating");
      if (PARALLELIZE < 1) {
        int count = 0;
        for (Enumeration e = bootImageTypes.elements(); e.hasMoreElements(); ) {
            VM_Type type = (VM_Type) e.nextElement();
            count++;
            if (verbose >= 1) say(count + " instantiating " + type);
            long start2 = System.currentTimeMillis();
            type.instantiate();
            long stop2 = System.currentTimeMillis();
            if (profile && stop2 - start2 > classCompileThreshold)
              System.out.println("PROF:\t\t"+type+" took "+((stop2 - start2+500)/1000)+" seconds to instantiate");
        }
      } else {
        if (verbose >= 1) say("parallelizing with " + PARALLELIZE + " threads");
        BootImageWorker.startup(bootImageTypes.elements());
        BootImageWorker [] workers = new BootImageWorker[PARALLELIZE];
        for (int i=0; i<workers.length; i++) {
          workers[i].id = i;
          workers[i].start();
        }
        try {
          for (int i=0; i<workers.length; i++)
            workers[i].join();
        } catch (InterruptedException ie) {
          say("InterruptedException while instantiating");
        }
      }
      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tinstantiating types "+(stopTime-startTime)+" ms");
      }

      // Do the portion of JNIEnvironment initialization that can be done
      // at bootimage writing time.
      VM_CodeArray[] functionTable = BuildJNIFunctionTable.buildTable();
      VM_JNIEnvironment.initFunctionTable(functionTable);

      //
      // Collect the VM class Field to JDK class Field correspondence
      // This will be needed when building the images of each object instance
      // and for processing the static fields of the boot image classes
      //
      if (verbose >= 1) say("field info gathering");
      if (profile) startTime = System.currentTimeMillis();
      bootImageTypeFields = new HashMap(bootImageTypes.size());

      // First retrieve the jdk Field table for each class of interest
      for (Enumeration e = bootImageTypes.elements(); e.hasMoreElements(); ) {
        VM_Type rvmType = (VM_Type) e.nextElement();
        FieldInfo fieldInfo;
        if (!rvmType.isClassType())
          continue; // arrays and primitives have no static or instance fields

        Class jdkType = getJdkType(rvmType);
        if (jdkType == null)
          continue;  // won't need the field info

        Key key   = new Key(jdkType);
        fieldInfo = (FieldInfo)bootImageTypeFields.get(key);
        if (fieldInfo != null) {
          fieldInfo.rvmType = rvmType;
        } else {
          if (verbose >= 1) say("making fieldinfo for " + rvmType);
          fieldInfo = new FieldInfo();
          fieldInfo.jdkFields = jdkType.getDeclaredFields();
          fieldInfo.jdkType = jdkType;
          fieldInfo.rvmType = rvmType;
          bootImageTypeFields.put(key, fieldInfo);
          // Now do all the superclasses if they don't already exist
          // Can't add them in next loop as Iterator's don't allow updates to collection
          for (Class cls = jdkType.getSuperclass(); cls != null; cls = cls.getSuperclass()) {
            key = new Key(cls);
            fieldInfo = (FieldInfo)bootImageTypeFields.get(key);
            if (fieldInfo != null) {
              break;  
            } else {
              if (verbose >= 1) say("making fieldinfo for " + jdkType);
              fieldInfo = new FieldInfo();
              fieldInfo.jdkFields = cls.getDeclaredFields();
              fieldInfo.jdkType = cls;
              fieldInfo.rvmType = null;    
              bootImageTypeFields.put(key, fieldInfo);
            }
          }
        }
      }
      // Now build the one-to-one instance and static field maps
      for (Iterator iter = bootImageTypeFields.values().iterator(); iter.hasNext();) {
        FieldInfo fieldInfo = (FieldInfo)iter.next();
        VM_Type rvmType = fieldInfo.rvmType;
        if (rvmType == null) {
          if (verbose >= 1) say("bootImageTypeField entry has no rvmType:"+fieldInfo.jdkType);
          continue; 
        }
        Class jdkType   = fieldInfo.jdkType;
        if (verbose >= 1) say("building static and instance fieldinfo for " + rvmType);

        // First the static fields
        // 
        VM_Field rvmFields[] = rvmType.getStaticFields();
        fieldInfo.jdkStaticFields = new Field[rvmFields.length];

        for (int j = 0; j < rvmFields.length; j++) {
          String  rvmName = rvmFields[j].getName().toString();
          for (int k = 0; k < fieldInfo.jdkFields.length; k++) {
            Field f = fieldInfo.jdkFields[k];
            if (f.getName().equals(rvmName)) {
              fieldInfo.jdkStaticFields[j] = f;
              f.setAccessible(true);
              break;
            }
          }
        }

        // Now the instance fields
        // 
        rvmFields = rvmType.getInstanceFields();
        fieldInfo.jdkInstanceFields = new Field[rvmFields.length];

        for (int j = 0; j<rvmFields.length; j++) {
          String  rvmName = rvmFields[j].getName().toString();
          // We look only in the JDK type that corresponds to the
          // VM_Type of the field's declaring class.
          // This is the only way to correctly handle private fields.
          jdkType = getJdkType(rvmFields[j].getDeclaringClass());
          if (jdkType == null) continue;
          FieldInfo jdkFieldInfo = (FieldInfo)bootImageTypeFields.get(new Key(jdkType));
          if (jdkFieldInfo == null) continue;
          Field[] jdkFields = jdkFieldInfo.jdkFields;
          for (int k = 0; k <jdkFields.length; k++) {
            Field f = jdkFields[k];
            if (f.getName().equals(rvmName)) {
              fieldInfo.jdkInstanceFields[j] = f;
              f.setAccessible(true);
              break;
            }
          }
        }
      }

      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tcreating type mapping "+(stopTime-startTime)+" ms");
      }
      
      //
      // Create stack, thread, and processor context in which rvm will begin
      // execution.
      //
      int initProc = VM_Scheduler.PRIMORDIAL_PROCESSOR_ID;
      // It's actually useless to set the name of the primordial thread here;
      // that data is really stored as part of the java.lang.Thread structure,
      // which we can not safely create yet.
      VM_Thread startupThread = new VM_Thread(new byte[STACK_SIZE_BOOT], null, "Jikes_RVM_Boot_Thread");
      VM_Scheduler.processors[initProc].activeThread = startupThread;
      // sanity check for bootstrap loader
      int idx = startupThread.stack.length - 1;
      if (VM.LittleEndian) {
        startupThread.stack[idx--] = (byte)0xde;
        startupThread.stack[idx--] = (byte)0xad;
        startupThread.stack[idx--] = (byte)0xba;
        startupThread.stack[idx--] = (byte)0xbe;
      } else {
        startupThread.stack[idx--] = (byte)0xbe;
        startupThread.stack[idx--] = (byte)0xba;
        startupThread.stack[idx--] = (byte)0xad;
        startupThread.stack[idx--] = (byte)0xde;
      }        
      
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
      if (verbose >= 1) say("populating jtoc with static fields");
      if (profile) startTime = System.currentTimeMillis();
      for (Enumeration e = bootImageTypes.elements(); e.hasMoreElements(); ) {
        VM_Type rvmType = (VM_Type) e.nextElement();
        if (verbose >= 1) say("  jtoc for ", rvmType.toString());
        if (!rvmType.isClassType())
          continue; // arrays and primitives have no static fields

        Class jdkType = getJdkType(rvmType);
        if (jdkType == null && verbose >= 1) {
          say("host has no class \"" + rvmType + "\"");
        }

        VM_Field rvmFields[] = rvmType.getStaticFields();
        for (int j = 0; j < rvmFields.length; ++j) {
          VM_Field rvmField     = rvmFields[j];
          VM_TypeReference rvmFieldType = rvmField.getType();
          int      rvmFieldSlot = (rvmField.getOffset() >>> 2);
          String   rvmFieldName = rvmField.getName().toString();
          Field    jdkFieldAcc  = null;

          if (jdkType != null) 
            jdkFieldAcc = getJdkFieldAccessor(jdkType, j, STATIC_FIELD);

          if (jdkFieldAcc == null) {
            if (jdkType != null) {
              if (verbose >= 2) traceContext.push(rvmFieldType.toString(),
                                                  jdkType.getName(), rvmFieldName);
              if (verbose >= 2) traceContext.traceFieldNotInHostJdk();
              if (verbose >= 2) traceContext.pop();
            }
            VM_Statics.setSlotContents(rvmFieldSlot, 0);
            if (!VM.runningTool)
              bootImage.countNulledReference();
            continue;
          }

          if (! Modifier.isStatic(jdkFieldAcc.getModifiers())) {
              if (verbose >= 2) traceContext.push(rvmFieldType.toString(),
                                                  jdkType.getName(), rvmFieldName);
              if (verbose >= 2) traceContext.traceFieldNotStaticInHostJdk();
              if (verbose >= 2) traceContext.pop();
              VM_Statics.setSlotContents(rvmFieldSlot, 0);
            if (!VM.runningTool)
              bootImage.countNulledReference();
            continue;
          }

          if (verbose >= 2) 
            say("    populating jtoc slot ", String.valueOf(rvmFieldSlot), 
                " with ", rvmField.toString());
          if (rvmFieldType.isPrimitiveType()) {
            // field is logical or numeric type
            if (rvmFieldType.isBooleanType()) {
              VM_Statics.setSlotContents(rvmFieldSlot,
                                         jdkFieldAcc.getBoolean(null) ? 1 : 0);
            } else if (rvmFieldType.isByteType()) {
              VM_Statics.setSlotContents(rvmFieldSlot, jdkFieldAcc.getByte(null));
            } else if (rvmFieldType.isCharType()) {
              VM_Statics.setSlotContents(rvmFieldSlot, jdkFieldAcc.getChar(null));
            } else if (rvmFieldType.isShortType()) {
              VM_Statics.setSlotContents(rvmFieldSlot, jdkFieldAcc.getShort(null));
            } else if (rvmFieldType.isIntType()) {
              try {
                VM_Statics.setSlotContents(rvmFieldSlot,
                                           jdkFieldAcc.getInt(null));
              } catch (IllegalArgumentException ex) {
                System.err.println( "type " + rvmType + ", field " + rvmField);
                throw ex;
              }
            } else if (rvmFieldType.isLongType()) {
              // note: Endian issues handled in setSlotContents.
              VM_Statics.setSlotContents(rvmFieldSlot,
                                         jdkFieldAcc.getLong(null));
            } else if (rvmFieldType.isFloatType()) {
              float f = jdkFieldAcc.getFloat(null);
              VM_Statics.setSlotContents(rvmFieldSlot,
                                         Float.floatToIntBits(f));
            } else if (rvmFieldType.isDoubleType()) {
              double d = jdkFieldAcc.getDouble(null);
              // note: Endian issues handled in setSlotContents.
              VM_Statics.setSlotContents(rvmFieldSlot,
                                         Double.doubleToLongBits(d));
            } else if (rvmFieldType.equals(VM_TypeReference.Address) ||
                       rvmFieldType.equals(VM_TypeReference.Word) ||
                       rvmFieldType.equals(VM_TypeReference.Extent) ||
                       rvmFieldType.equals(VM_TypeReference.Offset)){
              Object o = jdkFieldAcc.get(null);
              String msg = " static field " + rvmField.toString();
              boolean warn = rvmFieldType.equals(VM_TypeReference.Address);
            //-#if RVM_FOR_32_ADDR
              VM_Statics.setSlotContents(rvmFieldSlot, getWordValue(o, msg, warn).toInt());
            //-#endif
            //-#if RVM_FOR_64_ADDR
              VM_Statics.setSlotContents(rvmFieldSlot, getWordValue(o, msg, warn).toLong());
            //-#endif
            } else {
              fail("unexpected primitive field type: " + rvmFieldType);
            }
          } else {
            // field is reference type

            /* Consistency check. */
            if (jdkFieldAcc == null)
              fail("internal inconistency: jdkFieldAcc == null");

            int modifs = jdkFieldAcc.getModifiers();

            /* This crash, realistically, should never occur. */
            if (! Modifier.isStatic(modifs)) 
              fail("Consistency failure: About to try to access an instance field (via jdkFieldAcc) as if it were a static one!  The culprit is the field " + jdkFieldAcc.toString());
            Object o;
            try {
              o = jdkFieldAcc.get(null);
            } catch (NullPointerException npe) {
              fail("Got a NullPointerException while using reflection to retrieve the value of the field " + jdkFieldAcc.toString() + ": " + npe.toString());
              o = null;         // Unreached
            }
            if (verbose >= 3)
              say("       setting with ", String.valueOf(VM_Magic.objectAsAddress(o).toInt()));
            VM_Statics.setSlotContents(rvmFieldSlot, o);
          }
        }
      }
      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tinitializing jtoc "+(stopTime-startTime)+" ms");
      }
  }

  private static final int LARGE_ARRAY_SIZE = 16*1024;
  private static final int LARGE_SCALAR_SIZE = 1024;
  private static int depth = -1;
  private static int jtocCount = -1;
  private static final String SPACES = "                                                                                                                                                                                                                                                                                                                                ";

  private static void check (int value, String msg) {
    int low = VM_ObjectModel.maximumObjectRef(Address.zero()).toInt();  // yes, max
    int high = 0x10000000;  // we shouldn't have that many objects
    if (value > low && value < high && value != 32767 &&
        (value < 4088 || value > 4096)) {
      say("Warning: Suspicious Address value of ", String.valueOf(value),
          " written for " + msg);
    }
  }

  private static Word getWordValue(Object addr, String msg, boolean warn) {
    if (addr == null) return Word.zero();
    Word value = Word.zero();
    if (addr instanceof Address) {
      value = ((Address)addr).toWord();
    } else if (addr instanceof Word) {
      value = (Word)addr;
    } else if (addr instanceof Extent) {
      value = ((Extent)addr).toWord();
    } else if (addr instanceof Offset) {
      value = ((Offset)addr).toWord();
    } else {
      VM.sysWriteln("Unhandled supposed address value: " + addr);
      VM.sysFail("incomplete boot image support");
    }
    if (warn) check(value.toInt(), msg);
    return value;
  }

  /**
   * Copy an object (and, recursively, any of its fields or elements that
   * are references) from host jdk address space into image.
   *
   * @param jdkObject object to be copied
   * @param if allocOnly is true, the TIB and other reference fields are not recursively copied
   * @param if overwriteOffset is > 0, then copy object to given address
   *
   * @return offset of copied object within image, in bytes
   *         (OBJECT_NOT_PRESENT --> object not copied:
   *            it's not part of bootimage)
   */
  private static int copyToBootImage(Object jdkObject, 
                                     boolean allocOnly, int overwriteOffset, 
                                     Object parentObject)
    throws IllegalAccessException
    {
      //
      // Return object if it is already copied and not being overwritten
      //
      BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
      if (mapEntry.imageOffset != OBJECT_NOT_ALLOCATED && overwriteOffset == -1)
        return mapEntry.imageOffset;

      if (verbose >= 2) depth++;

      //
      // fetch object's type information
      //
      Class   jdkType = jdkObject.getClass();
      VM_Type rvmType = getRvmType(jdkType);
      if (rvmType == null) {
        if (verbose >= 2) traverseObject(jdkObject);
        if (verbose >= 2) depth--;
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
        int arrayImageOffset = (overwriteOffset == -1) ? bootImage.allocateArray(rvmArrayType, arrayCount) : overwriteOffset;
        mapEntry.imageOffset = arrayImageOffset;

        if (verbose >= 2) {
          if (depth == depthCutoff) 
            say(SPACES.substring(0,depth+1), "TOO DEEP: cutting off");
          else if (depth < depthCutoff) {
            String tab = SPACES.substring(0,depth+1);
            if (depth == 0 && jtocCount >= 0)
              tab = tab + "jtoc #" + String.valueOf(jtocCount) + ": ";
            int arraySize = rvmArrayType.getInstanceSize(arrayCount);
            say(tab, "Copying array  ", jdkType.getName(), 
                "   length=", String.valueOf(arrayCount),
                (arraySize >= LARGE_ARRAY_SIZE) ? " large object!!!" : "");
          }
        }

        VM_Type rvmElementType = rvmArrayType.getElementType();

        // Show info on reachability of int arrays
        //
        if (false && rvmElementType.equals(VM_Type.IntType)) {
          if (parentObject != null) {
            Class parentObjectType = parentObject.getClass();
            VM.sysWrite("Copying int array (", 4 * ((int []) jdkObject).length);
            VM.sysWriteln(" bytes) from parent object of type ", parentObjectType.toString());
          } else {
            VM.sysWriteln("Copying int array from no parent object");
          }
        }

        //
        // copy array elements from host jdk address space into image
        // recurse on values that are references
        //
        if (rvmElementType.isPrimitiveType()) {
          // array element is logical or numeric type
          if (rvmElementType.equals(VM_Type.BooleanType)) {
            boolean values[] = (boolean[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setByte(arrayImageOffset + i, values[i] ? 1 : 0);
          }
          else if (rvmElementType.equals(VM_Type.ByteType)) {
            byte values[] = (byte[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setByte(arrayImageOffset + i, values[i]);
          }
          else if (rvmElementType.equals(VM_Type.CharType)) {
            char values[] = (char[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setHalfWord(arrayImageOffset + (i << LOG_BYTES_IN_CHAR), values[i]);
          }
          else if (rvmElementType.equals(VM_Type.ShortType)) {
            short values[] = (short[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setHalfWord(arrayImageOffset + (i << LOG_BYTES_IN_SHORT), values[i]);
          }
          else if (rvmElementType.equals(VM_Type.IntType)) {
            int values[] = (int[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setFullWord(arrayImageOffset + (i << LOG_BYTES_IN_INT), values[i]);
          }
          else if (rvmElementType.equals(VM_Type.LongType)) {
            long values[] = (long[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setDoubleWord(arrayImageOffset + (i << LOG_BYTES_IN_LONG), values[i]);
          }
          else if (rvmElementType.equals(VM_Type.FloatType)) {
            float values[] = (float[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setFullWord(arrayImageOffset + (i << LOG_BYTES_IN_FLOAT),
                                    Float.floatToIntBits(values[i]));
          }
          else if (rvmElementType.equals(VM_Type.DoubleType)) {
            double values[] = (double[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setDoubleWord(arrayImageOffset + (i << LOG_BYTES_IN_DOUBLE),
                                      Double.doubleToLongBits(values[i]));
          } 
          else
            fail("unexpected primitive array type: " + rvmArrayType);
        } else {
          // array element is reference type
          Object values[] = (Object []) jdkObject;
          Class jdkClass = jdkObject.getClass();
          if (!allocOnly) {
            for (int i = 0; i<arrayCount; ++i) {
              if (values[i] != null) {
                if (verbose >= 2) traceContext.push(values[i].getClass().getName(),
                                                    jdkClass.getName(), i);
                int imageOffset = copyToBootImage(values[i], allocOnly, -1, jdkObject);
                if (imageOffset == OBJECT_NOT_PRESENT) {
                  // object not part of bootimage: install null reference
                  if (verbose >= 2) traceContext.traceObjectNotInBootImage();
                  bootImage.setNullAddressWord(arrayImageOffset + (i << LOG_BYTES_IN_ADDRESS));
                } else {
                  bootImage.setAddressWord(arrayImageOffset + (i << LOG_BYTES_IN_ADDRESS),
                                           bootImageAddress.add(imageOffset).toWord());
                }
                if (verbose >= 2) traceContext.pop();
              }
            }
          }
        }
      } else {
        if (rvmType == VM_Type.AddressArrayType) {
          if (verbose >= 2) depth--;
          AddressArray addrArray = (AddressArray) jdkObject;
          Object backing = addrArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteOffset, parentObject);
        }

        if (rvmType == VM_Type.OffsetArrayType) {
          if (verbose >= 2) depth--;
          OffsetArray addrArray = (OffsetArray) jdkObject;
          Object backing = addrArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteOffset, parentObject);
        }

        if (rvmType == VM_Type.WordArrayType) {
          if (verbose >= 2) depth--;
          WordArray addrArray = (WordArray) jdkObject;
          Object backing = addrArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteOffset, parentObject);
        }

        if (rvmType == VM_Type.ExtentArrayType) {
          if (verbose >= 2) depth--;
          ExtentArray addrArray = (ExtentArray) jdkObject;
          Object backing = addrArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteOffset, parentObject);
        }

        if (rvmType == VM_Type.CodeArrayType) {
          if (verbose >= 2) depth--;
          VM_CodeArray codeArray = (VM_CodeArray) jdkObject;
          Object backing = codeArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteOffset, parentObject);
        }

        if (rvmType.isMagicType()) {
          VM.sysWriteln("Unhandled copying of magic type: " + rvmType.getDescriptor().toString());
          VM.sysFail("incomplete boot image support");
        }

        //
        // allocate space in image
        //
        VM_Class rvmScalarType = rvmType.asClass();
        int scalarImageOffset = (overwriteOffset == -1) ? bootImage.allocateScalar(rvmScalarType) : overwriteOffset;
        mapEntry.imageOffset = scalarImageOffset;

        if (verbose >= 2) {
          if (depth == depthCutoff) 
            say(SPACES.substring(0,depth+1), "TOO DEEP: cutting off");
          else if (depth < depthCutoff) {
            String tab = SPACES.substring(0,depth+1);
            if (depth == 0 && jtocCount >= 0)
              tab = tab + "jtoc #" + String.valueOf(jtocCount) + " ";
            int scalarSize = rvmScalarType.getInstanceSize();
            say(tab, "Copying object ", jdkType.getName(),
                "   size=", String.valueOf(scalarSize),
                (scalarSize >= LARGE_SCALAR_SIZE) ? " large object!!!" : "");
          }
        }

        //
        // copy object fields from host jdk address space into image
        // recurse on values that are references
        //
        VM_Field[] rvmFields = rvmScalarType.getInstanceFields();
        for (int i = 0, n = rvmFields.length; i < n; ++i) {
          VM_Field rvmField       = rvmFields[i];
          VM_TypeReference rvmFieldType   = rvmField.getType();
          int      rvmFieldOffset = scalarImageOffset + rvmField.getOffset();
          String   rvmFieldName   = rvmField.getName().toString();
          Field    jdkFieldAcc    = getJdkFieldAccessor(jdkType, i, INSTANCE_FIELD);

          if (jdkFieldAcc == null) {
            if (verbose >= 2) traceContext.push(rvmFieldType.toString(),
                                                jdkType.getName(), rvmFieldName);
            if (verbose >= 2) traceContext.traceFieldNotInHostJdk();
            if (verbose >= 2) traceContext.pop();
            if (rvmFieldType.isPrimitiveType())
              switch (rvmField.getType().getSize()) {
                case 4: bootImage.setFullWord(rvmFieldOffset, 0);       break;
                case 8: bootImage.setDoubleWord(rvmFieldOffset, 0L);    break;
                default:fail("unexpected field type: " + rvmFieldType); break;
              }
            else
              bootImage.setNullAddressWord(rvmFieldOffset);
            continue;
          }

          if (rvmFieldType.isPrimitiveType()) {
            // field is logical or numeric type
            if (rvmFieldType.isBooleanType()) {
              bootImage.setFullWord(rvmFieldOffset,
                                    jdkFieldAcc.getBoolean(jdkObject) ? 1 : 0);
            } else if (rvmFieldType.isByteType()) {
              bootImage.setFullWord(rvmFieldOffset,
                                    jdkFieldAcc.getByte(jdkObject));
            } else if (rvmFieldType.isCharType()) {
              bootImage.setFullWord(rvmFieldOffset,
                                    jdkFieldAcc.getChar(jdkObject));
            } else if (rvmFieldType.isShortType()) {
              bootImage.setFullWord(rvmFieldOffset,
                                    jdkFieldAcc.getShort(jdkObject));
            } else if (rvmFieldType.isIntType()) {
              try {
                bootImage.setFullWord(rvmFieldOffset,
                                      jdkFieldAcc.getInt(jdkObject));
              } catch (IllegalArgumentException ex) {
                System.err.println( "type " + rvmScalarType + ", field " + rvmField);
                throw ex;
              }
            } else if (rvmFieldType.isLongType()) {
              bootImage.setDoubleWord(rvmFieldOffset,
                                      jdkFieldAcc.getLong(jdkObject));
            } else if (rvmFieldType.isFloatType()) {
              float f = jdkFieldAcc.getFloat(jdkObject);
              bootImage.setFullWord(rvmFieldOffset,
                                    Float.floatToIntBits(f));
            } else if (rvmFieldType.isDoubleType()) {
              double d = jdkFieldAcc.getDouble(jdkObject);
              bootImage.setDoubleWord(rvmFieldOffset,
                                      Double.doubleToLongBits(d));
            } else if (rvmFieldType.equals(VM_TypeReference.Address) ||
                       rvmFieldType.equals(VM_TypeReference.Word) ||
                       rvmFieldType.equals(VM_TypeReference.Extent) ||
                       rvmFieldType.equals(VM_TypeReference.Offset)) {
              Object o = jdkFieldAcc.get(jdkObject);
              String msg = " instance field " + rvmField.toString();
              boolean warn = rvmFieldType.equals(VM_TypeReference.Address);
              bootImage.setAddressWord(rvmFieldOffset, getWordValue(o, msg, warn));
            } else {
              fail("unexpected primitive field type: " + rvmFieldType);
            }
          } else {
            // field is reference type
            Object value = jdkFieldAcc.get(jdkObject);
            if (!allocOnly && value != null) {
              Class jdkClass = jdkFieldAcc.getDeclaringClass();
              if (verbose >= 2) traceContext.push(value.getClass().getName(),
                                                  jdkClass.getName(),
                                                  jdkFieldAcc.getName());
              int imageOffset = copyToBootImage(value, allocOnly, -1, jdkObject);
              if (imageOffset == OBJECT_NOT_PRESENT) {
                // object not part of bootimage: install null reference
                if (verbose >= 2) traceContext.traceObjectNotInBootImage();
                bootImage.setNullAddressWord(rvmFieldOffset);
              } else
                bootImage.setAddressWord(rvmFieldOffset,
                                         bootImageAddress.add(imageOffset).toWord());
              if (verbose >= 2) traceContext.pop();
            }
          }
        }
      }

      //
      // copy object's type information block into image, if it's not there
      // already
      //
      if (!allocOnly) {

        if (verbose >= 2) traceContext.push("", jdkObject.getClass().getName(), "tib");
        int tibImageOffset = copyToBootImage(rvmType.getTypeInformationBlock(), allocOnly, -1, jdkObject);
        if (verbose >= 2) traceContext.pop();
        if (tibImageOffset == OBJECT_NOT_ALLOCATED)
          fail("can't copy tib for " + jdkObject);
        Address tibAddress = bootImageAddress.add(tibImageOffset);
        VM_ObjectModel.setTIB(bootImage, mapEntry.imageOffset, tibAddress, rvmType);
      }

      if (verbose >= 2) depth--;

      return mapEntry.imageOffset;
    }


  private static int copyMagicArrayToBootImage(Object jdkObject, 
                                               VM_Array rvmArrayType,
                                               boolean allocOnly, 
                                               int overwriteOffset, 
                                               Object parentObject) 
    throws IllegalAccessException {
    //
    // Return object if it is already copied and not being overwritten
    //
    BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
    if (mapEntry.imageOffset != OBJECT_NOT_ALLOCATED && overwriteOffset == -1)
      return mapEntry.imageOffset;

    if (verbose >= 2) depth++;
    
    // allocate space in image
    int arrayCount       = Array.getLength(jdkObject);
    int arrayImageOffset = (overwriteOffset == -1) ? bootImage.allocateArray(rvmArrayType, arrayCount) : overwriteOffset;
    mapEntry.imageOffset = arrayImageOffset;

    if (verbose >= 2) {
      if (depth == depthCutoff) 
        say(SPACES.substring(0,depth+1), "TOO DEEP: cutting off");
      else if (depth < depthCutoff) {
        String tab = SPACES.substring(0,depth+1);
        if (depth == 0 && jtocCount >= 0)
          tab = tab + "jtoc #" + String.valueOf(jtocCount) + ": ";
        int arraySize = rvmArrayType.getInstanceSize(arrayCount);
        say(tab, "Copying array  ", rvmArrayType.toString(), 
            "   length=", String.valueOf(arrayCount),
            (arraySize >= LARGE_ARRAY_SIZE) ? " large object!!!" : "");
      }
    }

    // copy array elements from host jdk address space into image
    VM_Type rvmElementType = rvmArrayType.getElementType();
    if (rvmElementType.equals(VM_Type.CodeType)) {
      if (VM.BuildForIA32) {
        byte values[] = (byte[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setByte(arrayImageOffset + i, values[i]);
      } else {
        int values[] = (int[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setFullWord(arrayImageOffset + (i << LOG_BYTES_IN_INT), values[i]);
      }
    } else if (rvmElementType.equals(VM_Type.AddressType)) {
      Address values[] = (Address[]) jdkObject;
      for (int i=0; i<arrayCount; i++) {
        Address addr = values[i];
        String msg = "Address array element";
        bootImage.setAddressWord(arrayImageOffset + (i << LOG_BYTES_IN_ADDRESS), 
                                 getWordValue(addr, msg, true));
      }
    } else if (rvmElementType.equals(VM_Type.WordType)) {
      Word values[] = (Word[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        String msg = "Word array element ";
        Word addr = values[i];
        bootImage.setAddressWord(arrayImageOffset + (i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, false));
      }
    } else if (rvmElementType.equals(VM_Type.OffsetType)) {
      Offset values[] = (Offset[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        String msg = "Offset array element " + i;
        Offset addr = values[i];
        bootImage.setAddressWord(arrayImageOffset + (i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, false));
      }
    } else if (rvmElementType.equals(VM_Type.ExtentType)) {
      Extent values[] = (Extent[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        String msg = "Extent array element ";
        Extent addr = values[i];
        bootImage.setAddressWord(arrayImageOffset + (i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, false));
      }
    } else {
      fail("unexpected magic array type: " + rvmArrayType);
    }

    // copy object's TIB into image, if it's not there already
    if (!allocOnly) {
      if (verbose >= 2) traceContext.push("", jdkObject.getClass().getName(), "tib");
      int tibImageOffset = copyToBootImage(rvmArrayType.getTypeInformationBlock(), allocOnly, -1, jdkObject);
      if (verbose >= 2) traceContext.pop();
      if (tibImageOffset == OBJECT_NOT_ALLOCATED)
        fail("can't copy tib for " + jdkObject);
      Address tibAddress = bootImageAddress.add(tibImageOffset);
      VM_ObjectModel.setTIB(bootImage, mapEntry.imageOffset, tibAddress, rvmArrayType);
    }

    if (verbose >= 2) depth--;
    
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

      if (verbose >= 2) depth++;

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
              if (verbose >= 2) traceContext.push(values[i].getClass().getName(),
                                                  jdkType.getName(), i);
              traverseObject(values[i]);
              if (verbose >= 2) traceContext.pop();
            }
          }
        }
        if (verbose >= 2) {
          if (depth == depthCutoff) 
            say(SPACES.substring(0,depth+1), "TOO DEEP: cutting off");
          else if (depth < depthCutoff) {
            String tab = SPACES.substring(0,depth+1);
            if (depth == 0 && jtocCount >= 0)
              tab = tab + "jtoc #" + String.valueOf(jtocCount);
            say(tab, "Traversed array  ", jdkType.getName(), 
                "  length=", String.valueOf(arrayCount), 
                "  total size=", String.valueOf(size),
                (size >= LARGE_ARRAY_SIZE) ?"  large object!!!" : "");
          }
        }
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
                if (verbose >= 2) traceContext.push(value.getClass().getName(),
                                                    jdkType.getName(),
                                                    jdkField.getName());
                traverseObject(value);
                if (verbose >= 2) traceContext.pop();
              }
            }
          }
        }
        if (verbose >= 2) {
          if (depth == depthCutoff) 
            say(SPACES.substring(0,depth+1), "TOO DEEP: cutting off");
          else if (depth < depthCutoff) {
            String tab = SPACES.substring(0,depth+1);
            if (depth == 0 && jtocCount >= 0)
              tab = tab + "#" + String.valueOf(jtocCount);
            say(tab, "Traversed object ", jdkType.getName(), 
                "   total size=", String.valueOf(size),
                (size >= LARGE_SCALAR_SIZE) ? " large object!!!" : "");
          }
        }
      }

      traversed.put(key, new Integer(size));
      if (verbose >= 2) depth--;
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
         public Address objectAsAddress(Object jdkObject) {
           return BootImageMap.findOrCreateEntry(jdkObject).objectId;
         }
         
         public Object addressAsObject(Address address) {
           VM.sysWriteln("anonymous VM_ObjectAddressMapper: called addressAsObject");
           VM._assert(VM.NOT_REACHED);
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

    VM_Member remapper = VM_Entrypoints.magicObjectRemapperField;
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
      return Class.forName(rvmType.toString());
    } catch (Throwable x) {
      if (verbose >= 1) {
        say(x.toString());
      }
      return null;
    }
  }

  /**
   * Obtain accessor via which a field value may be fetched from host jdk
   * address space.
   *
   * @param jdkType class whose field is sought
   * @param index index in FieldInfo of field sought
   * @param isStatic is field from Static field table, indicates which table to consult
   * @return field accessor (null --> host class does not have specified field)
   */
  private static Field getJdkFieldAccessor(Class jdkType, int index, boolean isStatic) {
    FieldInfo fInfo = (FieldInfo)bootImageTypeFields.get(new Key(jdkType));
    Field     f;
    if (isStatic == STATIC_FIELD) {
      f = fInfo.jdkStaticFields[index];
      return f;
    } else {
      f = fInfo.jdkInstanceFields[index];
      return f;
    }
  }

  /**
   * Figure out name of static rvm field whose value lives in specified jtoc
   * slot.
   *
   * @param jtocSlot jtoc slot number
   * @return field name
   */
  private static String getRvmStaticFieldName(int jtocSlot) {
    VM_Type[] types = VM_Type.getTypes();
    for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i) {
      VM_Type type = types[i];
      if (type == null) continue;
      if (type.isPrimitiveType())
        continue;
      if (!type.isResolved())
        continue;
      VM_Field rvmFields[] = types[i].getStaticFields();
      for (int j = 0; j < rvmFields.length; ++j) {
        VM_Field rvmField = rvmFields[j];
        if ((rvmField.getOffset() >>> 2) == jtocSlot)
          return rvmField.getDeclaringClass() + "." + rvmField.getName();
      }
    }
    return VM_Statics.getSlotDescriptionAsString(jtocSlot) +
      "@jtoc[" + jtocSlot + "]";
  }

  private static VM_CompiledMethod findMethodOfCode(Object code) {
    VM_CompiledMethod[] compiledMethods = VM_CompiledMethods.getCompiledMethods();
    for (int i = 0; i < VM_CompiledMethods.numCompiledMethods(); ++i) {
      VM_CompiledMethod compiledMethod = compiledMethods[i];
      if (compiledMethod != null &&
          compiledMethod.isCompiled() && 
          compiledMethod.getInstructions() == code)
        return compiledMethod;
    }
    return null;
  }

  private static VM_Type findTypeOfTIBSlot (int tibSlot) {
    // search for a type that this is the TIB for
    VM_Type[] types = VM_Type.getTypes();
    for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i) {
      if (types[i] != null && types[i].getTibSlot() == tibSlot) 
        return types[i];
    }
    return null;
  }


  /**
   * Write method address map for use with dbx debugger.
   *
   * @param fileName name of file to write the map to
   */
  private static void writeAddressMap(String mapFileName) throws IOException {
    if (verbose >= 1) say("writing ", mapFileName);

    FileOutputStream fos = new FileOutputStream(mapFileName);
    BufferedOutputStream bos = new BufferedOutputStream(fos, 128);
    PrintStream out = new PrintStream(bos, false);

    out.println("#! /bin/bash");
    out.println("# This is a method address map, for use with the ``dbx'' debugger.");
    out.println("# To sort by \"code\" address, type \"bash <name-of-this-file>\".");
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
      String category = "<?>    ";
      String contents = "<?>       " + pad;
      String details  = "<?>";
      int ival;   // temporaries 
      long lval;
      String sval;

      switch (description) {
        case VM_Statics.EMPTY:
          category = "unused ";
          ival  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          contents = VM.intAsHexString(ival) + pad;
          details  = "";
          break;

        case VM_Statics.INT_LITERAL:
          category = "literal";
          ival  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          contents = VM.intAsHexString(ival) + pad;
          details  = VM_Statics.getSlotContentsAsInt(jtocSlot) + "";
          break;

        case VM_Statics.FLOAT_LITERAL:
          category = "literal";
          ival  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          contents = VM.intAsHexString(ival) + pad;
          details  = Float.intBitsToFloat(ival) + "F";
          break;

        case VM_Statics.LONG_LITERAL:
          category = "literal";
          lval = VM_Statics.getSlotContentsAsLong(jtocSlot);
          contents = VM.intAsHexString((int) (lval >> 32)) +
                     VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
          details  = lval + "L";
          break;

        case VM_Statics.DOUBLE_LITERAL:
          category = "literal";
          lval = VM_Statics.getSlotContentsAsLong(jtocSlot);
          contents = VM.intAsHexString((int) (lval >> 32)) +
                     VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
          details  = Double.longBitsToDouble(lval) + "D";
          break;

        case VM_Statics.NUMERIC_FIELD:
          category = "field  ";
          ival  = VM_Statics.getSlotContentsAsInt(jtocSlot);
          contents = VM.intAsHexString(ival) + pad;
          details  = getRvmStaticFieldName(jtocSlot);
          break;

        case VM_Statics.WIDE_NUMERIC_FIELD:
          category = "field  ";
          lval  = VM_Statics.getSlotContentsAsLong(jtocSlot);
          contents = VM.intAsHexString((int) (lval >> 32)) +
                     VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
          details  = getRvmStaticFieldName(jtocSlot);
          break;

        case VM_Statics.STRING_LITERAL: 
          category = "literal";
          contents = VM.addressAsHexString(getReferenceAddr(jtocSlot)) + pad;
          details  = "\"" + ((String) BootImageMap.getObject(getIVal(jtocSlot))).replace('\n', ' ') +"\"";
          break;
          
        case VM_Statics.REFERENCE_FIELD:
          category = "field  ";
          contents = VM.addressAsHexString(getReferenceAddr(jtocSlot)) + pad;
          details  = getRvmStaticFieldName(jtocSlot);
          break;

        case VM_Statics.METHOD:
          category = "code   ";
          contents = VM.addressAsHexString(getReferenceAddr(jtocSlot)) + pad;
          VM_CompiledMethod m = findMethodOfCode(BootImageMap.getObject(getIVal(jtocSlot)));
          details = m == null ? "<?>" : m.getMethod().toString();
          break;

        case VM_Statics.TIB:
          contents = VM.addressAsHexString(getReferenceAddr(jtocSlot)) + pad;
          category = "tib    ";
          VM_Type type = findTypeOfTIBSlot(jtocSlot);
          details = (type == null) ? "?" : type.toString();
          break;
          
        default:
          break;
      }

      out.println((jtocSlot + "      ").substring(0,6) +
                  VM.intAsHexString(jtocSlot << LOG_BYTES_IN_INT) + " " +
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
    VM_CompiledMethod[] compiledMethods = VM_CompiledMethods.getCompiledMethods();
    for (int i = 0; i < VM_CompiledMethods.numCompiledMethods(); ++i) {
      VM_CompiledMethod compiledMethod = compiledMethods[i];
      if (compiledMethod != null) {
        VM_Method m = compiledMethod.getMethod();
        if (m != null && compiledMethod.isCompiled()) {
          VM_CodeArray instructions = compiledMethod.getInstructions();
          Address code = BootImageMap.getImageAddress(bootImageAddress, instructions.getBacking());
          out.println(".     .          code     " + VM.addressAsHexString(code) +
                      "          " + compiledMethod.getMethod());
        }
      }
    }

    out.println();
    out.println("EOF-EOF-EOF");
    out.flush();
    out.close();
  }

  private static int getIVal(int jtocSlot) {
    int ival;
    if (VM.BuildFor32Addr) {
      ival = VM_Statics.getSlotContentsAsInt(jtocSlot);
    } else { 
      ival = (int)VM_Statics.getSlotContentsAsLong(jtocSlot); // just a cookie 
    }
    return ival;
  }

  private static Address getReferenceAddr(int jtocSlot) {
    int ival = getIVal(jtocSlot);
    Address addr = Address.fromIntZeroExtend(ival);
    if (ival != 0) {
      Object jdkObject = BootImageMap.getObject(ival);
      if (jdkObject instanceof VM_CodeArray) {
        jdkObject = ((VM_CodeArray)jdkObject).getBacking();
      } else if (jdkObject instanceof AddressArray) {
        jdkObject = ((AddressArray)jdkObject).getBacking();
      } else if (jdkObject instanceof ExtentArray) {
        jdkObject = ((ExtentArray)jdkObject).getBacking();
      } else if (jdkObject instanceof OffsetArray) {
        jdkObject = ((OffsetArray)jdkObject).getBacking();
      } else if (jdkObject instanceof WordArray) {
        jdkObject = ((WordArray)jdkObject).getBacking();
      }
      addr = BootImageMap.getImageAddress(bootImageAddress, jdkObject);
    }
    return addr;
  }
}
