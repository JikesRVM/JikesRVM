/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002, 2004
 */


import java.util.Hashtable;
import java.util.Vector;
import java.util.Stack;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Arrays;

import java.io.*;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashSet;

import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import com.ibm.jikesrvm.jni.*;
import com.ibm.jikesrvm.classloader.*;

import org.vmmagic.unboxed.*;

/**
 * Construct an RVM virtual machine bootimage.
 * <pre>
 * Invocation args:
 *    -n  <filename>           list of typenames to be written to bootimage
 *    -X:bc:<bcarg>            pass bcarg to bootimage compiler as command
 *                             line argument
 *    -classpath <path>        list of places to look for bootimage classes
 *    -littleEndian            write words to bootimage in little endian format?
 *    -trace                   talk while we work? (increment "verbose")
 *    -detailed                print detailed info on traversed objects
 *                                  (double-increment "verbose")
 *    -demographics            show summary of how boot space is used
 *    -ca <addr>               address where code portion of boot image starts
 *    -da <addr>               address where data portion of boot image starts
 *    -ra <addr>               address where ref map portion of boot image starts
 *    -o <filename>            place to put bootimage
 *    -m <filename>            place to put bootimage map
 *    -profile                 time major phases of bootimage writing
 *    -xclasspath <path>       OBSOLETE compatibility aid
 *    -numThreads=N            number of parallel compilation threads we should create
 *
 * </pre>
 * @author Derek Lieber
 * @version 03 Jan 2000
 * (complete rewrite of John Barton's original, this time using java2
 * reflection)
 *
 * @modified Steven Augart 16 Mar 2004  Fixes to bootstrap under Kaffe
 * @modified Ian Rogers To support knowing about fields we can't reflect upon
 */
public class BootImageWriter extends BootImageWriterMessages
 implements BootImageWriterConstants {

  /**
   * Number of threads we should use for compilation
   *  1:  work done in this thread
   *  >1: create these many threads
   *  <1: error
   */
  public static int numThreads = 1;

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
  private static final Hashtable<String,VM_Type> bootImageTypes = 
    new Hashtable<String,VM_Type>(5000);

  /**
   * For all the scalar types to be placed into bootimage, keep
   * key/value pairs where key is a Key(jdkType) and value is
   * a FieldInfo. 
   */
  private static HashMap<Key,FieldInfo> bootImageTypeFields;

  /**
   * Class to collecting together field information
   */
  private static class FieldInfo {
    /**
     *  Field table from JDK verion of class
     */
    final Field[]  jdkFields;

    /**
     *  Fields that are the one-to-one match of rvm instanceFields
     *  includes superclasses
     */
    Field[]  jdkInstanceFields;

    /**
     *  Fields that are the one-to-one match of rvm staticFields
     */
    Field[]  jdkStaticFields;

    /**
     *  Rvm type associated with this Field info
     */
    VM_Type rvmType;

    /**
     *  Jdk type associated with this Field info
     */
    final Class jdkType;

    /**
     * Constructor.
     * @param jdkType the type to associate with the key
     */
    public FieldInfo(Class jdkType, VM_Type rvmType) {
      this.jdkFields = jdkType.getDeclaredFields();
      this.jdkType = jdkType;
      this.rvmType = rvmType;
    }
  }

  /**
   * Key for looking up fieldInfo
   */
  private static class Key {
    /**
     * Jdk type
     */
    final Class jdkType;

    /**
     * Constructor.
     * @param jdkType the type to associate with the key
     */
    public Key(Class jdkType) { this.jdkType = jdkType; }

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
   * The absolute address at which the data portion of the
   * bootImage is going to be loaded.
   */
  public static Address bootImageDataAddress = Address.zero();

  /**
   * The absolute address at which the code portion of the
   * bootImage is going to be loaded.
   */
  public static Address bootImageCodeAddress = Address.zero();

  /**
   * The absolute address at which the reference map portion of the
   * bootImage is going to be loaded.
   */
  public static Address bootImageRMapAddress = Address.zero();

  public static Address getBootImageDataAddress()  {
    if (bootImageDataAddress.isZero()) VM.sysFail("BootImageWrite.getBootImageAddress called before boot image established");
    return bootImageDataAddress;
  }

  public static Address getBootImageCodeAddress()  {
    if (bootImageCodeAddress.isZero()) VM.sysFail("BootImageWrite.getBootImageAddress called before boot image established");
    return bootImageCodeAddress;
  }

  public static Address getBootImageRMapAddress()  {
    if (bootImageRMapAddress.isZero()) VM.sysFail("BootImageWrite.getBootImageAddress called before boot image established");
    return bootImageRMapAddress;
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
  private static class TraceContext extends Stack<String> {
    private static final long serialVersionUID = -9048590130621822408L;
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
      traceNulledWord(": field not static in host jdk");
    }

    /* Report a field that is a different type  in the host JDK.  */
    public void traceFieldDifferentTypeInHostJdk() {
      traceNulledWord(": field different type in host jdk");
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
     * Report an object of a class that is not part of the bootImage.
     */
    public void traceObjectFoundThroughKnown() {
      say(this.toString(), ": object found through known");
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
      StringBuilder message = new StringBuilder();
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
      StringBuilder sb = new StringBuilder("(");
      sb.append(type).append(")");
      sb.append(fullName);
      push(sb.toString());
    }

    /**
     * Push a field access onto the context
     */
    public void push(String type, String decl, String fieldName) {
      StringBuilder sb = new StringBuilder("(");
      sb.append(type).append(")");
      sb.append(decl).append(".").append(fieldName);
      push(sb.toString());
    }

    /**
     * Push an array access onto the context
     */
    public void push(String type, String decl, int index) {
      StringBuilder sb = new StringBuilder("(");
      sb.append(type).append(")");
      sb.append(decl).append("[").append(index).append("]");
      push(sb.toString());
    }
  }

  /**
   * Global trace context.
   */
  private static TraceContext traceContext = new TraceContext();

  @SuppressWarnings({"unused", "UnusedDeclaration"})
  private static Object sillyhack;

  /**
   * Main.
   * @param args command line arguments
   */
  public static void main(String[] args) {
    String   bootImageCodeName     = null;
    String   bootImageDataName     = null;
    String   bootImageRMapName     = null;
    String   bootImageMapName      = null;
    Vector<String>   bootImageTypeNames    = null;
    String   bootImageTypeNamesFile = null;
    String[] bootImageCompilerArgs = {};

    //
    // This may look useless, but it is not: it is a kludge to prevent
    // forName blowing up.  By forcing the system to load some classes 
    // now, we ensure that forName does not cause security violations by
    // trying to load into java.util later.
    // 
    java.util.HashMap<Object,Class<?>> x = new java.util.HashMap<Object,Class<?>>();
    x.put(x, x.getClass());
    sillyhack = x;

    //
    // Process command line directives.
    //
    for (int i = 0; i < args.length; ++i) {
      // name of code image file
      if (args[i].equals("-oc")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -oc flag without a following code image file name");
        bootImageCodeName = args[i];
        continue;
      }
      // name of data image file
      if (args[i].equals("-od")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -od flag without a following data image file name");
        bootImageDataName = args[i];
        continue;
      }
      // name of ref map image file
      if (args[i].equals("-or")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -or flag without a following ref map file name");
        bootImageRMapName = args[i];
        continue;
      }
      // name of map file
      if (args[i].equals("-m")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -m flag without a following bootImageMap file name");
        bootImageMapName = args[i];
        continue;
      }
      // image code start address
      if (args[i].equals("-ca")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -ca flag without a following image address");
        bootImageCodeAddress = Address.fromIntZeroExtend(Integer.decode(args[i]));
        continue;
      }
      // image data start address
      if (args[i].equals("-da")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -da flag without a following image address");
        bootImageDataAddress = Address.fromIntZeroExtend(Integer.decode(args[i]));
        continue;
      }
      // image ref map start address
      if (args[i].equals("-ra")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -ra flag without a following image address");
        bootImageRMapAddress = Address.fromIntZeroExtend(Integer.decode(args[i]));
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
        ++verbose;
        continue;
      }
      // generate info by type
      if (args[i].equals("-demographics")) {
        demographics = true;
        continue;
      }
      // numThreads
      if (args[i].startsWith("-numThreads=")) {
        numThreads = Integer.parseInt(args[i].substring(12));
        if (numThreads < 1) {
          fail("numThreads must be a positive number, value supplied:  "+ numThreads);
        }
        continue;
      }
      // profile
      if (args[i].equals("-profile")) {
        profile = true;
        continue;
      }
      // generate detailed information about traversed objects (for debugging)
      if (args[i].equals("-detailed")) {
        verbose += 2;
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
      traversed = new Hashtable<Object,Integer>(500);

    //
    // Check command line directives for correctness.
    //
    if (bootImageCodeName == null)
      fail("please specify \"-oc <boot-image-code-filename>\"");

    if (bootImageDataName == null)
      fail("please specify \"-od <boot-image-data-filename>\"");

    if (bootImageRMapName == null)
      fail("please specify \"-or <boot-image-rmap-filename>\"");

    if (bootImageTypeNamesFile == null)
      fail("please specify \"-n <boot-image-type-names-filename>\"");

    if (bootImageRepositoriesAtBuildTime == null)
      fail("please specify \"-classpath <path>\"");

    if (bootImageRepositoriesAtExecutionTime == null)
      bootImageRepositoriesAtExecutionTime = bootImageRepositoriesAtBuildTime;

    if (bootImageDataAddress.isZero())
      fail("please specify boot-image address with \"-da <addr>\"");
    if (!(bootImageDataAddress.toWord().and(Word.fromIntZeroExtend(0x00FFFFFF)).isZero()))
      fail("please specify a boot-image address that is a multiple of 0x01000000");
    if (bootImageCodeAddress.isZero())
      fail("please specify boot-image address with \"-ca <addr>\"");
    if (!(bootImageCodeAddress.toWord().and(Word.fromIntZeroExtend(0x00FFFFFF)).isZero()))
      fail("please specify a boot-image address that is a multiple of 0x01000000");
    if (bootImageRMapAddress.isZero())
      fail("please specify boot-image address with \"-ra <addr>\"");
    if (!(bootImageRMapAddress.toWord().and(Word.fromIntZeroExtend(0x00FFFFFF)).isZero()))
      fail("please specify a boot-image address that is a multiple of 0x01000000");

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
      e.printStackTrace(System.out);
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
    Address bootRecordImageAddress = Address.zero();
    try {
      // copy just the boot record
      bootRecordImageAddress = copyToBootImage(bootRecord, true, Address.max(), null); 
      if (bootRecordImageAddress.EQ(OBJECT_NOT_PRESENT)) {
        fail("can't copy boot record");
      }
    } catch (IllegalAccessException e) {
      fail("can't copy boot record: "+e);
    }

    //
    // Next, copy the jtoc.
    //
    if (verbose >= 1) say("copying jtoc");
    // Pointer to middle of JTOC 
    Address jtocImageAddress = Address.zero();
    try {
      jtocImageAddress = copyToBootImage(VM_Statics.getSlotsAsIntArray(), false, Address.max(), null);
      if (jtocImageAddress.EQ(OBJECT_NOT_PRESENT)) {
        fail("can't copy jtoc");
      }
    } catch (IllegalAccessException e) {
      fail("can't copy jtoc: "+e);
    }
    Address jtocPtr = jtocImageAddress.plus(VM_Statics.middleOfTable << LOG_BYTES_IN_INT);
    if (jtocPtr.NE(bootRecord.tocRegister))
      fail("mismatch in JTOC placement "+VM.addressAsHexString(jtocPtr)+" != "+ VM.addressAsHexString(bootRecord.tocRegister));

    //
    // Now, copy all objects reachable from jtoc, replacing each object id
    // that was generated by object address remapper with the actual
    // bootimage address of that object.
    //
    if (verbose >= 1) say("copying statics");
    try {
      int refSlotSize = VM_Statics.getReferenceSlotSize();
      for (int i = VM_Statics.middleOfTable+refSlotSize, n = VM_Statics.getHighestInUseSlot();
           i <= n;
           i+= refSlotSize) {
        if(!VM_Statics.isReference(i)) {
          throw new Error("Static " + i + " of " + n + " isn't reference");
        }
        jtocCount = i; // for diagnostic

        Offset jtocOff = VM_Statics.slotAsOffset(i);
        int objCookie;
        if (VM.BuildFor32Addr)
          objCookie = VM_Statics.getSlotContentsAsInt(jtocOff);
        else
          objCookie = (int) VM_Statics.getSlotContentsAsLong(jtocOff);
        // if (verbose >= 3)
        // say("       jtoc[", String.valueOf(i), "] = ", String.valueOf(objCookie));
        Object jdkObject = BootImageMap.getObject(objCookie);
        if (jdkObject == null)
          continue;

        if (verbose >= 2) traceContext.push(jdkObject.getClass().getName(),
                                            getRvmStaticField(jtocOff) + "");
        Address imageAddress = copyToBootImage(jdkObject, false, Address.max(), VM_Statics.getSlotsAsIntArray());
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          // object not part of bootimage: install null reference
          if (verbose >= 2) traceContext.traceObjectNotInBootImage();
          bootImage.setNullAddressWord(jtocPtr.plus(jtocOff), false, false);
        } else {
          bootImage.setAddressWord(jtocPtr.plus(jtocOff),
                                   imageAddress.toWord(), false);
        }
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
    VM_CodeArray startupCode  = VM_Entrypoints.bootMethod.getCurrentEntryCodeArray();

    bootRecord.tiRegister  = startupThread.getLockingId();
    bootRecord.spRegister  = BootImageMap.getImageAddress(startupStack, true).plus(startupStack.length);
    bootRecord.ipRegister  = BootImageMap.getImageAddress(startupCode.getBacking(), true);

    bootRecord.processorsOffset = VM_Entrypoints.processorsField.getOffset();

    bootRecord.bootImageDataStart = bootImageDataAddress;
    bootRecord.bootImageDataEnd   = bootImageDataAddress.plus(bootImage.getDataSize());
    bootRecord.bootImageCodeStart = bootImageCodeAddress;
    bootRecord.bootImageCodeEnd   = bootImageCodeAddress.plus(bootImage.getCodeSize());
    bootRecord.bootImageRMapStart = bootImageRMapAddress;
    bootRecord.bootImageRMapEnd   = bootImageRMapAddress.plus(bootImage.getRMapSize());

    // Update field of boot record now by re-copying
    //
    if (verbose >= 1) say("re-copying boot record (and its TIB)");
    try {
      Address newBootRecordImageAddress = copyToBootImage(bootRecord, false, bootRecordImageAddress, null); 
      if (!newBootRecordImageAddress.EQ(bootRecordImageAddress)) {
        VM.sysWriteln("bootRecordImageOffset = ", bootRecordImageAddress);
        VM.sysWriteln("newBootRecordImageOffset = ", newBootRecordImageAddress);
        VM._assert(newBootRecordImageAddress.EQ(bootRecordImageAddress));
      }
    } catch (IllegalAccessException e) {
      fail("unable to update boot record: "+e);
    }

    if (VM.BuildWithGCTrace) {
      /* Set the values in fields updated during the build process */
      Offset prevAddrOffset = VM_Entrypoints.tracePrevAddressField.getOffset();
      bootImage.setAddressWord(jtocPtr.plus(prevAddrOffset), 
                               VM_MiscHeader.getBootImageLink().toWord(), false);
      Offset oIDOffset = VM_Entrypoints.traceOIDField.getOffset();
      bootImage.setAddressWord(jtocPtr.plus(oIDOffset), 
                               VM_MiscHeader.getOID(), false);
    }

    //
    // Write image to disk.
    //
    if (profile) startTime = System.currentTimeMillis();
    try {
      bootImage.write(bootImageCodeName, bootImageDataName, bootImageRMapName);
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
    if (verbose >= 2) {
      VM_Type[] types = VM_Type.getTypes();
      for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i) {
        VM_Type type = types[i];
        if (type == null) continue;
        if (!type.isResolved()) {
          say("type referenced but not resolved: ", type.toString());
        }
        else if (!type.isInstantiated()) {
          say("type referenced but not instantiated: ", type.toString());
        }
        else if (!type.isInitialized()) {
          say("type referenced but not initialized: ", type.toString());
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
   * Class holding per type details for demographics
   */
  private static class DemographicInformation {
    /** number of allocations */
    int count;
    /** size allocated */
    int size;
  }

  /**
   * Compare sizes of types allocated to boot image
   * @author Perry Cheng
   * @modified Ian Rogers
   */
  private static class TypeComparator<T> implements Comparator<T> {
    
    public int compare (T a, T b) {
      if (a == null) return 1;
      if (b == null) return -1;
      if ((a instanceof VM_Type) && (b instanceof VM_Type)) {
        VM_Type typeA = (VM_Type) a;
        VM_Type typeB = (VM_Type) b;
        DemographicInformation infoA = demographicData.get(typeA);
        if (infoA == null) return 1;
        DemographicInformation infoB = demographicData.get(typeB);
        if (infoB == null) return -1;

        if (infoA.size > infoB.size) return -1;
        if (infoA.size < infoB.size) return 1;
        return 0;
      }
      return 0;
    }
  }

  /**
   * Demographic data on all types
   */
  private static final HashMap<VM_Type,DemographicInformation> demographicData = 
    new HashMap<VM_Type,DemographicInformation>();

  /**
   * Log an allocation in the boot image
   * @param type the type allocated
   * @param size the size of the type
   */
  public static void logAllocation(VM_Type type, int size) {
    if(demographics) {
      DemographicInformation info = demographicData.get(type);
      if(info != null) {
        info.count++;
        info.size += size;
      }
      else {
        info = new DemographicInformation();
        info.count++;
        info.size += size;
        demographicData.put(type, info);
      }
    }
  }

  /**
   * Print a report of space usage in the boot image.
   */
  public static void spaceReport() {
    VM_Type[] types = VM_Type.getTypes();
    VM_Type[] tempTypes = new VM_Type[types.length - FIRST_TYPE_DICTIONARY_INDEX];
    for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i) 
      tempTypes[i - FIRST_TYPE_DICTIONARY_INDEX] = types[i];
    Arrays.sort(tempTypes, new TypeComparator<VM_Type>());
    int totalCount = 0, totalBytes = 0;
    for (VM_Type type : tempTypes) {
      if (type == null) continue;
      DemographicInformation info = demographicData.get(type);
      if (info == null) continue;      
      totalCount += info.count;
      totalBytes += info.size;
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
    for (VM_Type type : tempTypes) {
      if (type == null) continue;
      DemographicInformation info = demographicData.get(type);
      if (info == null) continue;      
      if (info.count > 0) {
        VM.sysWriteField(60, type.toString());
        VM.sysWriteField(15, info.count);
        VM.sysWriteField(15, info.size);
        VM.sysWriteln();
      }
    }
  }

  /**
   * Read list of type names from a file.
   * @param fileName the name of the file containing type names
   * @return list type names
   */
  public static Vector<String> readTypeNames(String fileName) throws IOException {
    Vector<String> typeNames = new Vector<String>(500);
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
  public static void createBootImageObjects(Vector<String> typeNames,
                                            String bootImageTypeNamesFile) 
    throws IllegalAccessException {    
      VM_Callbacks.notifyBootImage(typeNames.elements());
      long startTime = 0;
      long stopTime = 0;
      
      //
      // Create types.
      //
      if (verbose >= 1) say("loading");
      if (profile) startTime = System.currentTimeMillis();
      
      for (String typeName : typeNames) {
        //
        // get type name
        //
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
          ncdf.printStackTrace(System.out);
          fail(bootImageTypeNamesFile
               + " contains a class named \"" 
               + typeName + "\", but we can't find a class with that name: "
               + ncdf);
          return;               // NOTREACHED
        } catch (IllegalArgumentException ila) {
          /* We should've caught any illegal type names at the data validation
           * stage, when we read these in.  If not, though, 
           * VM_TypeReference.findOrCreate() will do its own sanity check.  */
          ila.printStackTrace(System.out);
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
      for (VM_Type type : bootImageTypes.values()) {
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
      // allocate storage for boot record
      bootImage.allocateDataStorage(rvmBRType.getInstanceSize(), 
                                    VM_ObjectModel.getAlignment(rvmBRType), 
                                    VM_ObjectModel.getOffsetForAlignment(rvmBRType));
      // allocate storeage for JTOC
      Address jtocAddress = bootImage.allocateDataStorage(intArrayType.getInstanceSize(0),
                                                          VM_ObjectModel.getAlignment(intArrayType),
                                                          VM_ObjectModel.getOffsetForAlignment(intArrayType));
      bootImage.resetAllocator();
      bootRecord.tocRegister = jtocAddress.plus(intArrayType.getInstanceSize(VM_Statics.middleOfTable));

      //
      // Compile methods and populate jtoc with literals, TIBs, and machine code.
      //
      if (profile) startTime = System.currentTimeMillis();
      if (verbose >= 1) say("instantiating");
      if (numThreads == 1) {
        int count = 0;
        for (VM_Type type : bootImageTypes.values()) {
            count++;
            long start2 = System.currentTimeMillis();
            if (verbose >= 1) say(startTime +": "+ count + " instantiating " + type);
            type.instantiate();
            long stop2 = System.currentTimeMillis();
            if (verbose >=1) say(stop2 + ":  " + count + " finish " + type + " duration: " + (stop2 - start2) + "ms");
            if (profile && stop2 - start2 > classCompileThreshold)
              System.out.println("PROF:\t\t"+type+" took "+((stop2 - start2+500)/1000)+" seconds to instantiate");
        }
      } else {
        say(" compiling with " + numThreads + " threads");
        BootImageWorker.startup(bootImageTypes.elements());
        BootImageWorker [] workers = new BootImageWorker[numThreads];
        for (int i=0; i<workers.length; i++) {
          workers[i] = new BootImageWorker();
          workers[i].id = i;
          workers[i].setName("BootImageWorker-" + i);
          workers[i].start();
        }
        try {
          for (BootImageWorker worker : workers) { worker.join(); }
        } catch (InterruptedException ie) {
          say("InterruptedException while instantiating");
        }
      }
      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tinstantiating types "+(stopTime-startTime)+" ms");
      }

      // Free up unnecessary VM_Statics data structures
      VM_Statics.bootImageInstantiationFinished();

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
      bootImageTypeFields = new HashMap<Key,FieldInfo>(bootImageTypes.size());
      HashSet<String> invalidEntrys = new HashSet<String>();

      // First retrieve the jdk Field table for each class of interest
      for (VM_Type rvmType : bootImageTypes.values()) {
        FieldInfo fieldInfo;
        if (!rvmType.isClassType())
          continue; // arrays and primitives have no static or instance fields

        Class jdkType = getJdkType(rvmType);
        if (jdkType == null)
          continue;  // won't need the field info

        Key key   = new Key(jdkType);
        fieldInfo = bootImageTypeFields.get(key);
        if (fieldInfo != null) {
          fieldInfo.rvmType = rvmType;
        } else {
          if (verbose >= 1) say("making fieldinfo for " + rvmType);
          fieldInfo = new FieldInfo(jdkType, rvmType);
          bootImageTypeFields.put(key, fieldInfo);
          // Now do all the superclasses if they don't already exist
          // Can't add them in next loop as Iterator's don't allow updates to collection
          for (Class cls = jdkType.getSuperclass(); cls != null; cls = cls.getSuperclass()) {
            key = new Key(cls);
            fieldInfo = bootImageTypeFields.get(key);
            if (fieldInfo != null) {
              break;  
            } else {
              if (verbose >= 1) say("making fieldinfo for " + jdkType);
              fieldInfo = new FieldInfo(cls, null);
              bootImageTypeFields.put(key, fieldInfo);
            }
          }
        }
      }
      // Now build the one-to-one instance and static field maps
      for (FieldInfo fieldInfo : bootImageTypeFields.values()) {
        VM_Type rvmType = fieldInfo.rvmType;
        if (rvmType == null) {
          if (verbose >= 1) say("bootImageTypeField entry has no rvmType:"+fieldInfo.jdkType);
          continue; 
        }
        Class jdkType   = fieldInfo.jdkType;
        if (verbose >= 1) say("building static and instance fieldinfo for " + rvmType);

        // First the static fields
        // 
        VM_Field[] rvmFields = rvmType.getStaticFields();
        fieldInfo.jdkStaticFields = new Field[rvmFields.length];

        for (int j = 0; j < rvmFields.length; j++) {
          String  rvmName = rvmFields[j].getName().toString();
          for (Field f : fieldInfo.jdkFields) {
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
          FieldInfo jdkFieldInfo = bootImageTypeFields.get(new Key(jdkType));
          if (jdkFieldInfo == null) continue;
          Field[] jdkFields = jdkFieldInfo.jdkFields;
          for (Field f : jdkFields) {
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
      VM_BootstrapClassLoader.setBootstrapRepositories(bootImageRepositoriesAtExecutionTime);
      
      //
      // Finally, populate jtoc with static field values.
      // This is equivalent to the VM_Class.initialize() phase that would have
      // executed each class's static constructors at run time.  We simulate
      // this by copying statics created in the host rvm into the appropriate
      // slots of the jtoc.
      //
      if (verbose >= 1) say("populating jtoc with static fields");
      if (profile) startTime = System.currentTimeMillis();
      for (VM_Type rvmType : bootImageTypes.values()) {
        if (verbose >= 1) say("  jtoc for ", rvmType.toString());
        if (!rvmType.isClassType())
          continue; // arrays and primitives have no static fields

        Class jdkType = getJdkType(rvmType);
        if (jdkType == null && verbose >= 1) {
          say("host has no class \"" + rvmType + "\"");
        }

        VM_Field[] rvmFields = rvmType.getStaticFields();
        for (int j = 0; j < rvmFields.length; ++j) {
          VM_Field rvmField     = rvmFields[j];
          VM_TypeReference rvmFieldType = rvmField.getType();
          Offset rvmFieldOffset = rvmField.getOffset();
          String   rvmFieldName = rvmField.getName().toString();
          Field    jdkFieldAcc  = null;

          if (jdkType != null) 
            jdkFieldAcc = getJdkFieldAccessor(jdkType, j, STATIC_FIELD);

          if (jdkFieldAcc == null) {
            // we failed to get a reflective field accessors
            if (jdkType != null) {
              // we know the type - probably a private field of a java.lang class
              if(!copyKnownClasspathStaticField(jdkType,
                                                rvmFieldName,
                                                rvmFieldType,
                                                rvmFieldOffset
                                                )) {
                // we didn't know the field so nullify
                if (verbose >= 2) {
                  traceContext.push(rvmFieldType.toString(),
                                    jdkType.getName(), rvmFieldName);
                  traceContext.traceFieldNotInHostJdk();
                  traceContext.pop();
                }
                VM_Statics.setSlotContents(rvmFieldOffset, 0);
                if (!VM.runningTool)
                  bootImage.countNulledReference();
                invalidEntrys.add(jdkType.getName());
              }
            }
            else {
              // no accessor and we don't know the type so nullify
              if (verbose >= 2) {
                traceContext.push(rvmFieldType.toString(),
                                  rvmFieldType.toString(), rvmFieldName);
                traceContext.traceFieldNotInHostJdk();
                traceContext.pop();
              }
              VM_Statics.setSlotContents(rvmFieldOffset, 0);
              if (!VM.runningTool)
                bootImage.countNulledReference();
              invalidEntrys.add(rvmField.getDeclaringClass().toString());
            }
            continue;
          }

          if (! Modifier.isStatic(jdkFieldAcc.getModifiers())) {
            if (verbose >= 2) traceContext.push(rvmFieldType.toString(),
                                                jdkType.getName(), rvmFieldName);
            if (verbose >= 2) traceContext.traceFieldNotStaticInHostJdk();
            if (verbose >= 2) traceContext.pop();
            VM_Statics.setSlotContents(rvmFieldOffset, 0);
            if (!VM.runningTool)
              bootImage.countNulledReference();
            invalidEntrys.add(jdkType.getName());
            continue;
          }

          if( !equalTypes(jdkFieldAcc.getType().getName(), rvmFieldType)) {
            if (verbose >= 2) traceContext.push(rvmFieldType.toString(),
                                                jdkType.getName(), rvmFieldName);
            if (verbose >= 2) traceContext.traceFieldDifferentTypeInHostJdk();
            if (verbose >= 2) traceContext.pop();
            VM_Statics.setSlotContents(rvmFieldOffset, 0);
            if (!VM.runningTool)
              bootImage.countNulledReference();
            invalidEntrys.add(jdkType.getName());
            continue;
          }

          if (verbose >= 2)
            say("    populating jtoc slot ", String.valueOf(VM_Statics.offsetAsSlot(rvmFieldOffset)), 
                " with ", rvmField.toString());
          if (rvmFieldType.isPrimitiveType()) {
            // field is logical or numeric type
            if (rvmFieldType.isBooleanType()) {
              VM_Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getBoolean(null) ? 1 : 0);
            } else if (rvmFieldType.isByteType()) {
              VM_Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getByte(null));
            } else if (rvmFieldType.isCharType()) {
              VM_Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getChar(null));
            } else if (rvmFieldType.isShortType()) {
              VM_Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getShort(null));
            } else if (rvmFieldType.isIntType()) {
                VM_Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getInt(null));
            } else if (rvmFieldType.isLongType()) {
              // note: Endian issues handled in setSlotContents.
              VM_Statics.setSlotContents(rvmFieldOffset,
                                         jdkFieldAcc.getLong(null));
            } else if (rvmFieldType.isFloatType()) {
              float f = jdkFieldAcc.getFloat(null);
              VM_Statics.setSlotContents(rvmFieldOffset,
                                         Float.floatToIntBits(f));
            } else if (rvmFieldType.isDoubleType()) {
              double d = jdkFieldAcc.getDouble(null);
              // note: Endian issues handled in setSlotContents.
              VM_Statics.setSlotContents(rvmFieldOffset,
                                         Double.doubleToLongBits(d));
            } else if (rvmFieldType.equals(VM_TypeReference.Address) ||
                       rvmFieldType.equals(VM_TypeReference.Word) ||
                       rvmFieldType.equals(VM_TypeReference.Extent) ||
                       rvmFieldType.equals(VM_TypeReference.Offset)){
              Object o = jdkFieldAcc.get(null);
              String msg = " static field " + rvmField.toString();
              boolean warn = rvmFieldType.equals(VM_TypeReference.Address);
              VM_Statics.setSlotContents(rvmFieldOffset, getWordValue(o, msg, warn));
            } else {
              fail("unexpected primitive field type: " + rvmFieldType);
            }
          } else {
            // field is reference type
            final Object o = jdkFieldAcc.get(null);
            if (verbose >= 3)
              say("       setting with ", VM.addressAsHexString(VM_Magic.objectAsAddress(o)));
            VM_Statics.setSlotContents(rvmFieldOffset, o);
          }
        }
      }
      if (verbose >= 2) {
        for (final String entry : invalidEntrys) {
          say("Static fields of type are invalid: ", entry);
        }
      }

      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tinitializing jtoc "+(stopTime-startTime)+" ms");
      }
  }

  private static boolean equalTypes(final String name, final VM_TypeReference rvmFieldType) {
    final String descriptor = rvmFieldType.getName().toString();
    if (name.equals("int")) return descriptor.equals("I");
    else if (name.equals("boolean")) return descriptor.equals("Z");
    else if (name.equals("byte")) return descriptor.equals("B");
    else if (name.equals("char")) return descriptor.equals("C");
    else if (name.equals("double")) return descriptor.equals("D");
    else if (name.equals("float")) return descriptor.equals("F");
    else if (name.equals("long")) return descriptor.equals("J");
    else if (name.equals("short")) return descriptor.equals("S");
    else if (name.startsWith("[")) {
      return name.replace('.','/').equals(descriptor);
    }
    else {
      return ('L' + name.replace('.', '/') + ';').equals(descriptor);
    }
  }

  private static final int LARGE_ARRAY_SIZE = 16*1024;
  private static final int LARGE_SCALAR_SIZE = 1024;
  private static int depth = -1;
  private static int jtocCount = -1;
  private static final String SPACES = "                                                                                                                                                                                                                                                                                                                                ";

  private static void check (Word value, String msg) {
    Word low = VM_ObjectModel.maximumObjectRef(Address.zero()).toWord();  // yes, max
    Word high = Word.fromIntZeroExtend(0x10000000);  // we shouldn't have that many objects
    if (value.GT(low) && value.LT(high) && !value.EQ(Word.fromIntZeroExtend(32767)) &&
        (value.LT(Word.fromIntZeroExtend(4088)) || value.GT(Word.fromIntZeroExtend(4096)))) {
      say("Warning: Suspicious Address value of ", VM.addressAsHexString(value.toAddress()),
          " written for " + msg);
    }
  }

  private static Word getWordValue(Object addr, String msg, boolean warn) {
    if (addr == null) return Word.zero();
    Word value = Word.zero();
    if (addr instanceof Address) {
      value = ((Address)addr).toWord();
    } else if (addr instanceof ObjectReference) {
      value = ((ObjectReference)addr).toAddress().toWord();
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
    if (warn) check(value, msg);
    return value;
  }

  /**
   * Copy an object (and, recursively, any of its fields or elements that
   * are references) from host jdk address space into image.
   *
   * @param jdkObject object to be copied
   * @param allocOnly if allocOnly is true, the TIB and other reference fields are not recursively copied
   * @param overwriteAddress if !overwriteAddress.isMax(), then copy object to given address
   *
   * @return offset of copied object within image, in bytes
   *         (OBJECT_NOT_PRESENT --> object not copied:
   *            it's not part of bootimage)
   */
  private static Address copyToBootImage(Object jdkObject, 
                                         boolean allocOnly,
                                         Address overwriteAddress,
                                         Object parentObject)
    throws IllegalAccessException
    {
      //
      // Return object if it is already copied and not being overwritten
      //
      BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
      if ((!mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) && overwriteAddress.isMax())
        return mapEntry.imageAddress;

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
        Address arrayImageAddress = (overwriteAddress.isMax()) ? bootImage.allocateArray(rvmArrayType, arrayCount) : overwriteAddress;
        mapEntry.imageAddress = arrayImageAddress;

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
            boolean[] values = (boolean[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setByte(arrayImageAddress.plus(i), values[i] ? 1 : 0);
          }
          else if (rvmElementType.equals(VM_Type.ByteType)) {
            byte[] values = (byte[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setByte(arrayImageAddress.plus(i), values[i]);
          }
          else if (rvmElementType.equals(VM_Type.CharType)) {
            char[] values = (char[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setHalfWord(arrayImageAddress.plus(i << LOG_BYTES_IN_CHAR), values[i]);
          }
          else if (rvmElementType.equals(VM_Type.ShortType)) {
            short[] values = (short[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setHalfWord(arrayImageAddress.plus(i << LOG_BYTES_IN_SHORT), values[i]);
          }
          else if (rvmElementType.equals(VM_Type.IntType)) {
            int[] values = (int[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setFullWord(arrayImageAddress.plus(i << LOG_BYTES_IN_INT), values[i]);
          }
          else if (rvmElementType.equals(VM_Type.LongType)) {
            long[] values = (long[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setDoubleWord(arrayImageAddress.plus(i << LOG_BYTES_IN_LONG), values[i]);
          }
          else if (rvmElementType.equals(VM_Type.FloatType)) {
            float[] values = (float[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setFullWord(arrayImageAddress.plus(i << LOG_BYTES_IN_FLOAT),
                                    Float.floatToIntBits(values[i]));
          }
          else if (rvmElementType.equals(VM_Type.DoubleType)) {
            double[] values = (double[]) jdkObject;
            for (int i = 0; i < arrayCount; ++i)
              bootImage.setDoubleWord(arrayImageAddress.plus(i << LOG_BYTES_IN_DOUBLE),
                                      Double.doubleToLongBits(values[i]));
          } 
          else
            fail("unexpected primitive array type: " + rvmArrayType);
        } else {
          // array element is reference type
          Object[] values = (Object []) jdkObject;
          Class jdkClass = jdkObject.getClass();
          if (!allocOnly) {
            for (int i = 0; i<arrayCount; ++i) {
              if (values[i] != null) {
                if (verbose >= 2) traceContext.push(values[i].getClass().getName(),
                                                    jdkClass.getName(), i);
                Address imageAddress = copyToBootImage(values[i], allocOnly, Address.max(), jdkObject);
                if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
                  // object not part of bootimage: install null reference
                  if (verbose >= 2) traceContext.traceObjectNotInBootImage();
                  bootImage.setNullAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), true, false);
                } else {
                  bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS),
                                           imageAddress.toWord(), true);
                }
                if (verbose >= 2) traceContext.pop();
              } else {
                bootImage.setNullAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), true, true);
              }
            }
          }
        }
      } else {
        if (rvmType == VM_Type.AddressArrayType) {
          if (verbose >= 2) depth--;
          AddressArray addrArray = (AddressArray) jdkObject;
          Object backing = addrArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteAddress, parentObject);
        }

        if (rvmType == VM_Type.ObjectReferenceArrayType) {
          if (verbose >= 2) depth--;
          ObjectReferenceArray orArray = (ObjectReferenceArray) jdkObject;
          Object backing = orArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteAddress, parentObject);
        }

        if (rvmType == VM_Type.OffsetArrayType) {
          if (verbose >= 2) depth--;
          OffsetArray addrArray = (OffsetArray) jdkObject;
          Object backing = addrArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteAddress, parentObject);
        }

        if (rvmType == VM_Type.WordArrayType) {
          if (verbose >= 2) depth--;
          WordArray addrArray = (WordArray) jdkObject;
          Object backing = addrArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteAddress, parentObject);
        }

        if (rvmType == VM_Type.ExtentArrayType) {
          if (verbose >= 2) depth--;
          ExtentArray addrArray = (ExtentArray) jdkObject;
          Object backing = addrArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteAddress, parentObject);
        }

        if (rvmType == VM_Type.CodeArrayType) {
          if (verbose >= 2) depth--;
          VM_CodeArray codeArray = (VM_CodeArray) jdkObject;
          Object backing = codeArray.getBacking();
          return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteAddress, parentObject);
        }

        if (rvmType.isMagicType()) { 
          VM.sysWriteln("Unhandled copying of magic type: " + rvmType.getDescriptor().toString() +
                        " in object of type " + parentObject.getClass().toString());
          VM.sysFail("incomplete boot image support");
        }

        //
        // allocate space in image
        //
        VM_Class rvmScalarType = rvmType.asClass();
        Address scalarImageAddress = (overwriteAddress.isMax()) ? bootImage.allocateScalar(rvmScalarType) : overwriteAddress;
        mapEntry.imageAddress = scalarImageAddress;

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
          Address rvmFieldAddress = scalarImageAddress.plus(rvmField.getOffset());
          String  rvmFieldName    = rvmField.getName().toString();
          Field   jdkFieldAcc     = getJdkFieldAccessor(jdkType, i, INSTANCE_FIELD);

          if (jdkFieldAcc == null) {
            // Field not found via reflection
            if (!copyKnownClasspathInstanceField(jdkObject, rvmFieldName, rvmFieldType, rvmFieldAddress)) {
              // Field wasn't a known Classpath field so write null
              if (verbose >= 2) traceContext.push(rvmFieldType.toString(),
                                                  jdkType.getName(), rvmFieldName);
              if (verbose >= 2) traceContext.traceFieldNotInHostJdk();
              if (verbose >= 2) traceContext.pop();
              if (rvmFieldType.isPrimitiveType()) {
                switch (rvmField.getType().getMemoryBytes()) {
                case 1: bootImage.setByte(rvmFieldAddress, 0);          break;
                case 2: bootImage.setHalfWord(rvmFieldAddress, 0);      break;
                case 4: bootImage.setFullWord(rvmFieldAddress, 0);      break;
                case 8: bootImage.setDoubleWord(rvmFieldAddress, 0L);   break;
                default:fail("unexpected field type: " + rvmFieldType); break;
                }
              }
              else {
                bootImage.setNullAddressWord(rvmFieldAddress, true, false);
              }
            }
            continue;
          }

          if (rvmFieldType.isPrimitiveType()) {
            // field is logical or numeric type
            if (rvmFieldType.isBooleanType()) {
              bootImage.setByte(rvmFieldAddress,
                                jdkFieldAcc.getBoolean(jdkObject) ? 1 : 0);
            } else if (rvmFieldType.isByteType()) {
              bootImage.setByte(rvmFieldAddress,
                                jdkFieldAcc.getByte(jdkObject));
            } else if (rvmFieldType.isCharType()) {
              bootImage.setHalfWord(rvmFieldAddress,
                                    jdkFieldAcc.getChar(jdkObject));
            } else if (rvmFieldType.isShortType()) {
              bootImage.setHalfWord(rvmFieldAddress,
                                    jdkFieldAcc.getShort(jdkObject));
            } else if (rvmFieldType.isIntType()) {
              try {
                bootImage.setFullWord(rvmFieldAddress,
                                      jdkFieldAcc.getInt(jdkObject));
              } catch (IllegalArgumentException ex) {
                System.out.println( "type " + rvmScalarType + ", field " + rvmField);
                throw ex;
              }
            } else if (rvmFieldType.isLongType()) {
              bootImage.setDoubleWord(rvmFieldAddress,
                                      jdkFieldAcc.getLong(jdkObject));
            } else if (rvmFieldType.isFloatType()) {
              float f = jdkFieldAcc.getFloat(jdkObject);
              bootImage.setFullWord(rvmFieldAddress,
                                    Float.floatToIntBits(f));
            } else if (rvmFieldType.isDoubleType()) {
              double d = jdkFieldAcc.getDouble(jdkObject);
              bootImage.setDoubleWord(rvmFieldAddress,
                                      Double.doubleToLongBits(d));
            } else if (rvmFieldType.equals(VM_TypeReference.Address) ||
                       rvmFieldType.equals(VM_TypeReference.Word) ||
                       rvmFieldType.equals(VM_TypeReference.Extent) ||
                       rvmFieldType.equals(VM_TypeReference.Offset)) {
              Object o = jdkFieldAcc.get(jdkObject);
              String msg = " instance field " + rvmField.toString();
              boolean warn = rvmFieldType.equals(VM_TypeReference.Address);
              bootImage.setAddressWord(rvmFieldAddress, getWordValue(o, msg, warn), false);
            } else {
              fail("unexpected primitive field type: " + rvmFieldType);
            }
          } else {
            // field is reference type
            Object value = jdkFieldAcc.get(jdkObject);
            if (!allocOnly) {
              if (value != null) {
                Class jdkClass = jdkFieldAcc.getDeclaringClass();
                if (verbose >= 2) traceContext.push(value.getClass().getName(),
                                                    jdkClass.getName(),
                                                    jdkFieldAcc.getName());
                Address imageAddress = copyToBootImage(value, allocOnly, Address.max(), jdkObject);
                if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
                  // object not part of bootimage: install null reference
                  if (verbose >= 2) traceContext.traceObjectNotInBootImage();
                  bootImage.setNullAddressWord(rvmFieldAddress, true, false);
                } else
                  bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), !rvmField.isFinal());
                if (verbose >= 2) traceContext.pop();
              } else {
                bootImage.setNullAddressWord(rvmFieldAddress, true, true);
              }
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
        Address tibImageAddress = copyToBootImage(rvmType.getTypeInformationBlock(), allocOnly, Address.max(), jdkObject);
        if (verbose >= 2) traceContext.pop();
        if (tibImageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          fail("can't copy tib for " + jdkObject);
        }
        VM_ObjectModel.setTIB(bootImage, mapEntry.imageAddress, tibImageAddress, rvmType);
      }

      if (verbose >= 2) depth--;

      return mapEntry.imageAddress;
    }


  private static Address copyMagicArrayToBootImage(Object jdkObject, 
                                                   VM_Array rvmArrayType,
                                                   boolean allocOnly, 
                                                   Address overwriteAddress,
                                                   Object parentObject) 
    throws IllegalAccessException {
    //
    // Return object if it is already copied and not being overwritten
    //
    BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
    if ((!mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) && overwriteAddress.isMax())
      return mapEntry.imageAddress;

    if (verbose >= 2) depth++;
    
    VM_Type rvmElementType = rvmArrayType.getElementType();

    // allocate space in image
    int arrayCount       = Array.getLength(jdkObject);
    Address arrayImageAddress;
    if (overwriteAddress.isMax()) {
      if (rvmElementType.equals(VM_Type.CodeType)) {
        arrayImageAddress = bootImage.allocateCode(rvmArrayType, arrayCount);
      } else {
        arrayImageAddress = bootImage.allocateArray(rvmArrayType, arrayCount);
      }
    } else {
      arrayImageAddress = overwriteAddress;
    }
    mapEntry.imageAddress = arrayImageAddress;

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
    if (rvmElementType.equals(VM_Type.CodeType)) {
      if (VM.BuildForIA32) {
        byte[] values = (byte[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setByte(arrayImageAddress.plus(i), values[i]);
      } else {
        int[] values = (int[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setFullWord(arrayImageAddress.plus(i << LOG_BYTES_IN_INT), values[i]);
      }
    } else if (rvmElementType.equals(VM_Type.AddressType)) {
      Address[] values = (Address[]) jdkObject;
      for (int i=0; i<arrayCount; i++) {
        Address addr = values[i];
        String msg = "Address array element";
        bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), 
                                 getWordValue(addr, msg, true), false);
      }
    } else if (rvmElementType.equals(VM_Type.ObjectReferenceType)) {
      ObjectReference[] values = (ObjectReference[]) jdkObject;
      for (int i=0; i<arrayCount; i++) {
        ObjectReference or = values[i];
        String msg = "ObjectReference array element";
        bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), 
                                 getWordValue(or, msg, true), true);
      }
    } else if (rvmElementType.equals(VM_Type.WordType)) {
      Word[] values = (Word[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        String msg = "Word array element ";
        Word addr = values[i];
        bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, false), false);
      }
    } else if (rvmElementType.equals(VM_Type.OffsetType)) {
      Offset[] values = (Offset[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        String msg = "Offset array element " + i;
        Offset addr = values[i];
        bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, false), false);
      }
    } else if (rvmElementType.equals(VM_Type.ExtentType)) {
      Extent[] values = (Extent[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        String msg = "Extent array element ";
        Extent addr = values[i];
        bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, false), false);
      }
    } else {
      fail("unexpected magic array type: " + rvmArrayType);
    }

    // copy object's TIB into image, if it's not there already
    if (!allocOnly) {
      if (verbose >= 2) traceContext.push("", jdkObject.getClass().getName(), "tib");
      Address tibImageAddress = copyToBootImage(rvmArrayType.getTypeInformationBlock(), allocOnly, Address.max(), jdkObject);
      if (verbose >= 2) traceContext.pop();
      if (tibImageAddress.EQ(OBJECT_NOT_ALLOCATED))
        fail("can't copy tib for " + jdkObject);
      VM_ObjectModel.setTIB(bootImage, mapEntry.imageAddress, tibImageAddress, rvmArrayType);
    }

    if (verbose >= 2) depth--;
    
    return mapEntry.imageAddress;
  }

  /**
   * If we can't find a field via reflection we may still determine
   * and copy a value because we know the internals of Classpath.
   * @param jdkType the class containing the field
   * @param rvmFieldName the name of the field
   * @param rvmFieldType the type reference of the field
   */
  private static boolean copyKnownClasspathStaticField(Class jdkType, String rvmFieldName, VM_TypeReference rvmFieldType, Offset rvmFieldOffset) {
    // java.lang.Number
    if ((jdkType.equals(java.lang.Number.class)) &&
        (rvmFieldName.equals("digits")) &&
        (rvmFieldType.isArrayType())) {
      char[] java_lang_Number_digits = new char[]{
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
        'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
        'u', 'v', 'w', 'x', 'y', 'z'
      };
      VM_Statics.setSlotContents(rvmFieldOffset, java_lang_Number_digits);
      return true;
    }
    else if (jdkType.equals(java.lang.Number.class)) {
      throw new Error("Unknown field in " + jdkType + " " + rvmFieldName + " " + rvmFieldType);
    }
    // java.lang.Boolean
    else if (jdkType.equals(java.lang.Boolean.class)) {
      throw new Error("Unknown field in " + jdkType + " " + rvmFieldName + " " + rvmFieldType);
    }
    // java.lang.Byte
    else if ((jdkType.equals(java.lang.Byte.class)) &&
             (rvmFieldName.equals("byteCache")) &&
             (rvmFieldType.isArrayType())) {
      Byte[] java_lang_Byte_byteCache = new Byte[256];
      // Populate table, although unnecessary
      for(int i=-128; i < 128; i++) {
        Byte value = (byte) i;
        BootImageMap.findOrCreateEntry(value);
        java_lang_Byte_byteCache[128+i] = value;
      }
      VM_Statics.setSlotContents(rvmFieldOffset, java_lang_Byte_byteCache);
      return true;
    }
    else if ((jdkType.equals(java.lang.Byte.class)) &&
             (rvmFieldName.equals("MIN_CACHE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, -128);
      return true;
    }
    else if ((jdkType.equals(java.lang.Byte.class)) &&
             (rvmFieldName.equals("MAX_CACHE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, 127);
      return true;
    }
    else if ((jdkType.equals(java.lang.Byte.class)) &&
             (rvmFieldName.equals("SIZE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, 8); // NB not present in Java 1.4
      return true;
    }
    else if (jdkType.equals(java.lang.Byte.class)) {
      throw new Error("Unknown field in " + jdkType + " " + rvmFieldName + " " + rvmFieldType);
    }
    // java.lang.Character - there are many static fields that need
    // initializing in Character, so we leave it for VM.boot

    // java.lang.Double
    else if ((jdkType.equals(java.lang.Double.class)) &&
             (rvmFieldName.equals("SIZE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, 64); // NB not present in Java 1.4
      return true;
    }
    else if (jdkType.equals(java.lang.Double.class)) {
      throw new Error("Unknown field in " + jdkType + " " + rvmFieldName + " " + rvmFieldType);
    }
    // java.lang.Float
    else if ((jdkType.equals(java.lang.Float.class)) &&
             (rvmFieldName.equals("SIZE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, 32); // NB not present in Java 1.4
      return true;
    }
    else if (jdkType.equals(java.lang.Float.class)) {
      throw new Error("Unknown field in " + jdkType + " " + rvmFieldName + " " + rvmFieldType);
    }
    // java.lang.Integer
    else if ((jdkType.equals(java.lang.Integer.class)) &&
             (rvmFieldName.equals("intCache")) &&
             (rvmFieldType.isArrayType())) {
      Integer[] java_lang_Integer_intCache = new Integer[256];
      // Populate table, although unnecessary
      for(int i=-128; i < 128; i++) {
        Integer value = i;
        java_lang_Integer_intCache[128+i] = value;
      }
      VM_Statics.setSlotContents(rvmFieldOffset, java_lang_Integer_intCache);
      return true;
    }
    else if ((jdkType.equals(java.lang.Integer.class)) &&
             (rvmFieldName.equals("MIN_CACHE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, -128);
      return true;
    }
    else if ((jdkType.equals(java.lang.Integer.class)) &&
             (rvmFieldName.equals("MAX_CACHE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, 127);
      return true;
    }
    else if ((jdkType.equals(java.lang.Integer.class)) &&
             (rvmFieldName.equals("SIZE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, 32); // NB not present in Java 1.4
      return true;
    }
    else if (jdkType.equals(java.lang.Integer.class)) {
      throw new Error("Unknown field in " + jdkType + " " + rvmFieldName + " " + rvmFieldType);
    }
    // java.lang.Long
    else if ((jdkType.equals(java.lang.Long.class)) &&
             (rvmFieldName.equals("SIZE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, 64); // NB not present in Java 1.4
      return true;
    }
    else if (jdkType.equals(java.lang.Long.class)) {
      throw new Error("Unknown field in " + jdkType + " " + rvmFieldName + " " + rvmFieldType);
    }
    // java.lang.Short
    else if ((jdkType.equals(java.lang.Short.class)) &&
             (rvmFieldName.equals("shortCache")) &&
             (rvmFieldType.isArrayType())) {
      Short[] java_lang_Short_shortCache = new Short[256];
      // Populate table, although unnecessary
      for(short i=-128; i < 128; i++) {
        Short value = i;
        BootImageMap.findOrCreateEntry(value);
        java_lang_Short_shortCache[128+i] = value;
      }
      VM_Statics.setSlotContents(rvmFieldOffset, java_lang_Short_shortCache);
      return true;
    }
    else if ((jdkType.equals(java.lang.Short.class)) &&
             (rvmFieldName.equals("MIN_CACHE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, -128);
      return true;
    }
    else if ((jdkType.equals(java.lang.Short.class)) &&
             (rvmFieldName.equals("MAX_CACHE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, 127);
      return true;
    }
    else if ((jdkType.equals(java.lang.Short.class)) &&
             (rvmFieldName.equals("SIZE")) &&
             (rvmFieldType.isIntType())) {
      VM_Statics.setSlotContents(rvmFieldOffset, 16); // NB not present in Java 1.4
      return true;
    }
    else if (jdkType.equals(java.lang.Short.class)) {
      throw new Error("Unknown field in " + jdkType + " " + rvmFieldName + " " + rvmFieldType);
    }
    // Unknown field
    else {
      return false;
    }
  }

  /**
   * If we can't find a field via reflection we may still determine
   * and copy a value because we know the internals of Classpath.
   * @param jdkObject the object containing the field
   * @param rvmFieldName the name of the field
   * @param rvmFieldType the type reference of the field
   * @param rvmFieldAddress
   */
  private static boolean copyKnownClasspathInstanceField(Object jdkObject, String rvmFieldName, VM_TypeReference rvmFieldType, Address rvmFieldAddress)
    throws IllegalAccessException
  {
    if ((jdkObject instanceof java.lang.String) &&
        (rvmFieldName.equals("cachedHashCode")) &&
        (rvmFieldType.isIntType())
        ) {
      // Populate String's cachedHashCode value
      bootImage.setFullWord(rvmFieldAddress, jdkObject.hashCode());
      return true;
    }
    else if (jdkObject instanceof java.lang.Class)   {
      Object value = null;
      String fieldName = null;
      boolean fieldIsFinal = false;
      if(rvmFieldName.equals("type")) {
        // Looks as though we're trying to write the type for Class,
        // lets go over the common ones
        if (jdkObject == java.lang.Boolean.TYPE) {
          value = VM_Type.BooleanType;
        }
        else if (jdkObject == java.lang.Byte.TYPE) {
          value = VM_Type.ByteType;
        }
        else if (jdkObject == java.lang.Character.TYPE) {
          value = VM_Type.CharType;
        }
        else if (jdkObject == java.lang.Double.TYPE) {
          value = VM_Type.DoubleType;
        }
        else if (jdkObject == java.lang.Float.TYPE) {
          value = VM_Type.FloatType;
        }
        else if (jdkObject == java.lang.Integer.TYPE) {
          value = VM_Type.IntType;
        }
        else if (jdkObject == java.lang.Long.TYPE) {
          value = VM_Type.LongType;
        }
        else if (jdkObject == java.lang.Short.TYPE) {
          value = VM_Type.ShortType;
        }
        else if (jdkObject == java.lang.Void.TYPE) {
          value = VM_Type.VoidType;
        }
        else {
          value = VM_TypeReference.findOrCreate((Class)jdkObject).peekResolvedType();
          if (value == null) {
             throw new Error("Failed to populate Class.type for " + jdkObject);
          }
        }
        fieldName = "type";
        fieldIsFinal = true;
      }
      if ((fieldName != null) && (value != null)) {
        if (verbose >= 2) traceContext.push(value.getClass().getName(),
                                            "java.lang.Class",
                                            fieldName);
        Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            // object not part of bootimage: install null reference
            if (verbose >= 2) traceContext.traceObjectNotInBootImage();
            bootImage.setNullAddressWord(rvmFieldAddress, true, false);
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = copyToBootImage(value, false, Address.max(), jdkObject);
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), !fieldIsFinal);
        } else {
          if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
          bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), !fieldIsFinal);
        }
        if (verbose >= 2) traceContext.pop();
        return true;
      } else {
        // Unknown Class field or value for type
        return false;
      }
    }
    else if (jdkObject instanceof java.lang.reflect.Constructor)   {
      Constructor cons = (Constructor)jdkObject;
      if(rvmFieldName.equals("constructor")) {
        // fill in this VM_Method field
        String typeName = "L" + cons.getDeclaringClass().getName().replace('.','/') + ";";
        VM_Type type = VM_TypeReference.findOrCreate(typeName).peekResolvedType();
        if (type == null) {
          throw new Error("Failed to find type for Constructor.constructor: " + cons + " " + typeName);
        }
        VM_Class klass = type.asClass();
        if (klass == null) {
          throw new Error("Failed to populate Constructor.constructor for " + cons);
        }
        Class[] consParams = cons.getParameterTypes();
        VM_Method constructor = null;
        loop_over_all_constructors:
        for (VM_Method vmCons : klass.getConstructorMethods()) {
          VM_TypeReference[] vmConsParams = vmCons.getParameterTypes();
          if (vmConsParams.length == consParams.length) {
            for (int j = 0; j < vmConsParams.length; j++) {
              if (!consParams[j].equals(vmConsParams[j].resolve().getClassForType())) {
                continue loop_over_all_constructors;
              }
            }
            constructor = vmCons;
            break;
          }
        }
        if (constructor == null) {
          throw new Error("Failed to populate Constructor.constructor for " + cons);
        }
        if (verbose >= 2) traceContext.push("VM_Method",
                                            "java.lang.Constructor",
                                            "constructor");
        Address imageAddress = BootImageMap.findOrCreateEntry(constructor).imageAddress;
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          // object not part of bootimage: install null reference
          if (verbose >= 2) traceContext.traceObjectNotInBootImage();
          bootImage.setNullAddressWord(rvmFieldAddress, true, false);
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = copyToBootImage(constructor, false, Address.max(), jdkObject);
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), false);
        } else {
          if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
          bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), false);
        }
        if (verbose >= 2) traceContext.pop();
        return true;
      } else if(rvmFieldName.equals("flag")) {
        // This field is inherited accesible flag is actually part of
        // AccessibleObject
        bootImage.setFullWord(rvmFieldAddress, cons.isAccessible() ? 1 : 0);
        return true;
      } else {
        // Unknown Constructor field
        return false;
      }
    } 
    else {
      // Unknown field
      return false;
    }
  }

  private static final int OBJECT_HEADER_SIZE = 8;
  private static Hashtable<Object,Integer> traversed = null;
  private static final Integer VISITED = 0;

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
      Integer sz = traversed.get(key);
      if (sz != null) return sz; // object already traversed
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
          Object[] values = (Object []) jdkObject;
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
                size += 1;
              else if (jdkFieldType == Byte.TYPE)
                size += 1;
              else if (jdkFieldType == Character.TYPE)
                size += 2;
              else if (jdkFieldType == Short.TYPE)
                size += 2;
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

      traversed.put(key, size);
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
         public <T> Address objectAsAddress(T jdkObject) {
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
    VM_Statics.setSlotContents(remapper.getOffset(), 0);
  }

  /**
   * Obtain rvm type corresponding to host jdk type.
   *
   * @param jdkType jdk type
   * @return rvm type (null --> type does not appear in list of classes
   *         comprising bootimage)
   */
  private static VM_Type getRvmType(Class jdkType) {
    return bootImageTypes.get(jdkType.getName());
  }

  /**
   * Obtain host jdk type corresponding to target rvm type.
   *
   * @param rvmType rvm type
   * @return jdk type (null --> type does not exist in host namespace)
   */
  private static Class getJdkType(VM_Type rvmType) {
    Throwable x;
    try {
      return Class.forName(rvmType.toString());
    }
    catch (ExceptionInInitializerError e) {x=e;}
    catch (UnsatisfiedLinkError e)        {x=e;}
    catch (NoClassDefFoundError e)        {x=e;}
    catch (SecurityException e)           {x=e;}
    catch (ClassNotFoundException e)      {x=e;}
    if (verbose >= 1) {
      say(x.toString());
    }
    return null;
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
    FieldInfo fInfo = bootImageTypeFields.get(new Key(jdkType));
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
  private static VM_Field getRvmStaticField(Offset jtocOff) {
    VM_Type[] types = VM_Type.getTypes();
    for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < types.length; ++i) {
      VM_Type type = types[i];
      if (type == null) continue;
      if (type.isPrimitiveType())
        continue;
      if (!type.isResolved())
        continue;
      for (VM_Field rvmField : types[i].getStaticFields()) {
        if (rvmField.getOffset().EQ(jtocOff))
          return rvmField;
      }
    }
    return null;
  }

  private static VM_CompiledMethod findMethodOfCode(Object code) {
    VM_CompiledMethod[] compiledMethods = VM_CompiledMethods.getCompiledMethods();
    for (int i = 0; i < VM_CompiledMethods.numCompiledMethods(); ++i) {
      VM_CompiledMethod compiledMethod = compiledMethods[i];
      if (compiledMethod != null &&
          compiledMethod.isCompiled() && 
          compiledMethod.getEntryCodeArray() == code)
        return compiledMethod;
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
    out.println("# Bootimage data: "+Integer.toHexString(BOOT_IMAGE_DATA_START.toInt())
                +"..."+Integer.toHexString(BOOT_IMAGE_DATA_START.toInt()+bootImage.getDataSize()));
    out.println("# Bootimage code: "+Integer.toHexString(BOOT_IMAGE_CODE_START.toInt())
                +"..."+Integer.toHexString(BOOT_IMAGE_CODE_START.toInt()+bootImage.getCodeSize()));
    out.println("# Bootimage refs: "+Integer.toHexString(BOOT_IMAGE_RMAP_START.toInt())
                +"..."+Integer.toHexString(BOOT_IMAGE_RMAP_START.toInt()+bootImage.getRMapSize()));
                                                            
    out.println();
    out.println("(/bin/grep 'code     0x' | /bin/sort -k 4.3,4) << EOF-EOF-EOF");
    out.println();
    out.println("JTOC Map");
    out.println("--------");
    out.println("slot  offset     category contents            details");
    out.println("----  ------     -------- --------            -------");

    String pad = "        ";

    // Numeric JTOC fields
    for (int jtocSlot = VM_Statics.getLowestInUseSlot();
         jtocSlot < VM_Statics.middleOfTable;
         jtocSlot++) {
      Offset jtocOff = VM_Statics.slotAsOffset(jtocSlot);
      String category;
      String contents;
      String details;
      if (VM_Statics.isIntSizeLiteral(jtocSlot)) {
        category = "literal";
        int ival = VM_Statics.getSlotContentsAsInt(jtocOff);
        contents = VM.intAsHexString(ival) + pad;
        details  = Integer.toString(ival);
      } else if(VM_Statics.isLongSizeLiteral(jtocSlot)) {
        category = "literal";
        long lval = VM_Statics.getSlotContentsAsLong(jtocOff);
        contents = VM.intAsHexString((int) (lval >> 32)) +
          VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
        details  = lval + "L";
        jtocSlot++;
      }
      else {
        VM_Field field = getRvmStaticField(jtocOff);
        if (field != null) {
          category = "field  ";
          details  = field.toString();
          VM_TypeReference type = field.getType();
          if (type.isIntLikeType()) {
            int ival = VM_Statics.getSlotContentsAsInt(jtocOff);
            contents = VM.intAsHexString(ival) + pad;
          }
          else if(type.isLongType()) {
            long lval= VM_Statics.getSlotContentsAsLong(jtocOff);
            contents = VM.intAsHexString((int) (lval >> 32)) +
              VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
            jtocSlot++;
          }
          else if(type.isFloatType()) {
            int ival = VM_Statics.getSlotContentsAsInt(jtocOff);
            contents = Float.toString(Float.intBitsToFloat(ival)) + pad;
          }
          else if(type.isDoubleType()) {
            long lval= VM_Statics.getSlotContentsAsLong(jtocOff);
            contents = Double.toString(Double.longBitsToDouble(lval)) + pad;
            jtocSlot++;
          }
          else {
            // Unknown?
            int ival = VM_Statics.getSlotContentsAsInt(jtocOff);
            category = "<?>    ";
            details  = "<?>";
            contents = VM.intAsHexString(ival) + pad;
          }
        }
        else {
          // Unknown?
          int ival = VM_Statics.getSlotContentsAsInt(jtocOff);
          category = "<?>    ";
          details  = "<?>";
          contents = VM.intAsHexString(ival) + pad;
        }
      }
      out.println((jtocSlot + "      ").substring(0,6) +
                  VM.addressAsHexString(jtocOff.toWord().toAddress()) + " " +
                  category + "  " + contents + "  " + details);
    }

    // Reference JTOC fields
    for (int jtocSlot = VM_Statics.middleOfTable,
           n = VM_Statics.getHighestInUseSlot();
         jtocSlot <= n;
         jtocSlot += VM_Statics.getReferenceSlotSize()) {
      Offset jtocOff = VM_Statics.slotAsOffset(jtocSlot);
      Object obj     = BootImageMap.getObject(getIVal(jtocOff));
      int foundOff   = VM_Statics.findObjectLiteral(obj);
      String category;
      String details;
      String contents = VM.addressAsHexString(getReferenceAddr(jtocOff, false)) + pad;
      if (foundOff == jtocOff.toInt()) {
        category = "literal";
        if (obj == null){
          details = "(null)";
        } else if (obj instanceof String) {
          details = "\""+ obj + "\"";
        } else if (obj instanceof Class) {
          details = "class "+ obj;
        } else {
          details = "object "+ obj.getClass();
        }
      }
      else {
        VM_Field field = getRvmStaticField(jtocOff);
        if (field != null) {
          category = "field  ";
          details  = field.toString();
        } else if(obj instanceof Object[]) {
          category = "tib    ";
          VM_Type type = VM_Statics.findTypeOfTIBSlot(jtocOff);
          details = (type == null) ? "?" : type.toString();
        } else {
          VM_CompiledMethod m = findMethodOfCode(obj);
          if (m != null) {
            category = "code   ";
            details = m.getMethod().toString();
          } else {
            category = "<?>    ";
            details  = "<?>";
          }
        }
      }
      out.println((jtocSlot + "      ").substring(0,6) +
                  VM.addressAsHexString(jtocOff.toWord().toAddress()) + " " +
                  category + "  " + contents + "  " + details);
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
          VM_CodeArray instructions = compiledMethod.getEntryCodeArray();
          Address code = BootImageMap.getImageAddress(instructions.getBacking(), true);
          out.println(".     .          code     " + VM.addressAsHexString(code) +
                      "          " + compiledMethod.getMethod());
        }
      }
    }

    // Extra information on the layout of objects in the boot image
    if (false) {
      out.println();
      out.println("Object Map");
      out.println("----------");
      out.println("                          address             type");
      out.println("                          -------             ------");
      out.println();
      
      for (Enumeration e = BootImageMap.elements() ; e.hasMoreElements() ;) {
        BootImageMap.Entry entry = (BootImageMap.Entry)e.nextElement();
        Address data = entry.imageAddress;
        out.println(".     .          data     " + VM.addressAsHexString(data) +
                    "          " + entry.jdkObject.getClass());
      }
    }

    out.println();
    out.println("EOF-EOF-EOF");
    out.flush();
    out.close();
  }

  /**
   * Read an integer value from the JTOC
   * @param jtocOff offset in JTOC
   * @return integer at offset
   */
  private static int getIVal(Offset jtocOff) {
    int ival;
    if (VM.BuildFor32Addr) {
      ival = VM_Statics.getSlotContentsAsInt(jtocOff);
    } else { 
      ival = (int)VM_Statics.getSlotContentsAsLong(jtocOff); // just a cookie 
    }
    return ival;
  }

  /**
   * Read a reference from the JTOC
   * @param jtocOff offset in JTOC
   * @param fatalIfNotFound whether to terminate on failure
   * @return address of object or zero if not found
   */
  private static Address getReferenceAddr(Offset jtocOff, boolean fatalIfNotFound) {
    int ival = getIVal(jtocOff);
    if (ival != 0) {
      Object jdkObject = BootImageMap.getObject(ival);
      if (jdkObject instanceof VM_CodeArray) {
        jdkObject = ((VM_CodeArray)jdkObject).getBacking();
      } else if (jdkObject instanceof AddressArray) {
        jdkObject = ((AddressArray)jdkObject).getBacking();
      } else if (jdkObject instanceof ObjectReferenceArray) {
        jdkObject = ((ObjectReferenceArray)jdkObject).getBacking();
      } else if (jdkObject instanceof ExtentArray) {
        jdkObject = ((ExtentArray)jdkObject).getBacking();
      } else if (jdkObject instanceof OffsetArray) {
        jdkObject = ((OffsetArray)jdkObject).getBacking();
      } else if (jdkObject instanceof WordArray) {
        jdkObject = ((WordArray)jdkObject).getBacking();
      }
      return BootImageMap.getImageAddress(jdkObject, fatalIfNotFound);
    }
    else {
      return Address.zero();
    }
  }
}
