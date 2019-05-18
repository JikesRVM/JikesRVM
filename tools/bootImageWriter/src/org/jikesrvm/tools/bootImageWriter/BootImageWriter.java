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
package org.jikesrvm.tools.bootImageWriter;

import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_BOOLEAN;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_BYTE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_CHAR;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_FLOAT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_LONG;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_SHORT;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_CHAR;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_FLOAT;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_INT;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_LONG;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_SHORT;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterConstants.FIRST_TYPE_DICTIONARY_INDEX;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterConstants.OBJECT_ALLOCATION_DEFERRED;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterConstants.OBJECT_NOT_ALLOCATED;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterConstants.OBJECT_NOT_PRESENT;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.fail;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.say;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.DETAILED;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.NONE;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.SUMMARY;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.TYPE_NAMES;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jikesrvm.VM;
import org.jikesrvm.architecture.ArchitectureFactory;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.JMXSupport;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeDescriptorParsing;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.LazyCompilationTrampoline;
import org.jikesrvm.jni.FunctionTable;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.mm.mminterface.AlignmentEncoding;
import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.RuntimeTable;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.BootRecord;
import org.jikesrvm.runtime.Callbacks;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.tools.bootImageWriter.entrycomparators.ClassNameComparator;
import org.jikesrvm.tools.bootImageWriter.entrycomparators.IdenticalComparator;
import org.jikesrvm.tools.bootImageWriter.entrycomparators.NonFinalReferenceDensityComparator;
import org.jikesrvm.tools.bootImageWriter.entrycomparators.NumberOfNonFinalReferencesComparator;
import org.jikesrvm.tools.bootImageWriter.entrycomparators.NumberOfReferencesComparator;
import org.jikesrvm.tools.bootImageWriter.entrycomparators.ObjectSizeComparator;
import org.jikesrvm.tools.bootImageWriter.entrycomparators.ReferenceDensityComparator;
import org.jikesrvm.tools.bootImageWriter.entrycomparators.TypeReferenceComparator;
import org.jikesrvm.tools.bootImageWriter.types.BootImageTypes;
import org.jikesrvm.util.Services;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

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
 *    -log <filename>          place to write log file to
 *    -o <filename>            place to put bootimage
 *    -m <filename>            place to put bootimage map
 *    -profile                 time major phases of bootimage writing
 *    -xclasspath <path>       OBSOLETE compatibility aid
 *    -numThreads=N            number of parallel compilation threads we should create
 *
 * </pre>
 */
public class BootImageWriter {

  /**
   * The name of the class library, used when performing oracle operations of
   * trying to fill in fields when they cannot be reflected upon. Always lower
   * case.
   */
  private static String classLibrary;

  /**
   * Number of threads we should use for compilation. Default to number of
   * available processors +1. This is the ideal thread pool size for something
   * compute bound, but should be larger if IO is an issue.
   */
  public static int numThreads = Runtime.getRuntime().availableProcessors() + 1;

  /**
   * The boot thread
   */
  private static RVMThread startupThread;

  /**
   * How much talking while we work?
   */
  private static Verbosity verbosity = NONE;

  /** how deeply in the recursive copy do we continue to blather */
  private static final int DEPTH_CUTOFF = 3;

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
   * Linked list that operates in FIFO manner rather than LIFO
   */
  private static final class FIFOLinkedList<T> extends LinkedList<T> {
    /** Serialization support to avoid warning */
    static final long serialVersionUID = 242526399904424920L;
    /** Remove from the other end of the linked list */
    @Override
    public T remove() {
      return removeLast();
    }
  }

  /**
   * Entries yet to be written into the boot image
   */
  private static final Queue<BootImageMap.Entry> pendingEntries;
  static {
    if (true) // depth first traversal
      pendingEntries = new FIFOLinkedList<BootImageMap.Entry>();
      else if (false) pendingEntries = new LinkedList<BootImageMap.Entry>(); // breadth first traversal
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11, new TypeReferenceComparator());
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11, new ClassNameComparator());
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new ObjectSizeComparator(new IdenticalComparator()));
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new NumberOfReferencesComparator(new IdenticalComparator()));
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new NumberOfNonFinalReferencesComparator(new IdenticalComparator()));
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new NumberOfNonFinalReferencesComparator(new ObjectSizeComparator(new TypeReferenceComparator())));
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new NumberOfReferencesComparator(new ObjectSizeComparator(new ClassNameComparator())));
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new NumberOfReferencesComparator(new ObjectSizeComparator(new TypeReferenceComparator())));
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new ReferenceDensityComparator(new IdenticalComparator()));
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new NonFinalReferenceDensityComparator(new IdenticalComparator()));
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new ReferenceDensityComparator(new TypeReferenceComparator()));
      else if (false) pendingEntries = new PriorityQueue<BootImageMap.Entry>(11,
          new NonFinalReferenceDensityComparator(new TypeReferenceComparator()));
  }

  public static final boolean STATIC_FIELD = true;
  public static final boolean INSTANCE_FIELD = false;

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
    if (bootImageDataAddress.isZero()) fail("BootImageWrite.getBootImageAddress called before boot image established");
    return bootImageDataAddress;
  }

  public static Address getBootImageCodeAddress()  {
    if (bootImageCodeAddress.isZero()) fail("BootImageWrite.getBootImageAddress called before boot image established");
    return bootImageCodeAddress;
  }

  public static Address getBootImageRMapAddress()  {
    if (bootImageRMapAddress.isZero()) fail("BootImageWrite.getBootImageAddress called before boot image established");
    return bootImageRMapAddress;
  }

  public static int bootImageDataSize() {
    return bootImage.getDataSize();
  }

  public static int bootImageCodeSize() {
    return bootImage.getCodeSize();
  }

  public static int bootImageRMapSize() {
    return bootImage.getRMapSize();
  }

  public static Verbosity verbosity() {
    return verbosity;
  }

  public static BootImage bootImage() {
    return bootImage;
  }

  public static String classLibrary() {
    return classLibrary;
  }

  public static TraceContext traceContext() {
    return traceContext;
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
   * Global trace context.
   */
  private static TraceContext traceContext = new TraceContext();

  @SuppressWarnings({"unused", "UnusedDeclaration"})
  private static Object sillyhack;

  private static Address decodeAddress(String s) {
    if (s.endsWith("L")) {
      s = s.substring(0, s.length() - 1);
    }
    return Address.fromLong(Long.decode(s));
  }

  /**
   * Main.
   * @param args command line arguments
   */
  public static void main(String[] args) {
    String   logFile               = null;
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
      // name of class library
      if (args[i].equals("-classlib")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -classlib flag without a following class library name");
        classLibrary = args[i].toLowerCase().intern();
        continue;
      }
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
        bootImageCodeAddress = decodeAddress(args[i]);
        continue;
      }
      // image data start address
      if (args[i].equals("-da")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -da flag without a following image address");
        bootImageDataAddress = decodeAddress(args[i]);
        continue;
      }
      // image ref map start address
      if (args[i].equals("-ra")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -ra flag without a following image address");
        bootImageRMapAddress = decodeAddress(args[i]);
        continue;
      }
      // file containing names of types to be placed into bootimage
      if (args[i].equals("-n")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -n flag without a following file name");

        if (bootImageTypeNamesFile != null)
          fail("argument syntax error: We've already read in the bootImageTypeNames from" +
               bootImageTypeNamesFile + "; just got another -n argument" +
               " telling us to read them from " + args[i]);
        bootImageTypeNamesFile = args[i];

        continue;
      }
      // bootimage compiler argument
      if (args[i].startsWith("-X:bc:")) {
        String[] nbca = new String[bootImageCompilerArgs.length + 1];
        for (int j = 0; j < bootImageCompilerArgs.length; j++) {
          nbca[j] = bootImageCompilerArgs[j];
        }
        nbca[nbca.length - 1] = args[i].substring(6);
        bootImageCompilerArgs = nbca;
        say("compiler arg: ", bootImageCompilerArgs[nbca.length - 1]);
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
        verbosity = verbosity.increaseBy(1);
        continue;
      }
      // generate info by type
      if (args[i].equals("-demographics")) {
        demographics = true;
        continue;
      }
      // numThreads
      if (args[i].startsWith("-numThreads=")) {
        int desiredThreadCount = Integer.parseInt(args[i].substring(12));
        if (desiredThreadCount < 0) {
          fail("numThreads must be a non-negative number, value supplied:  " + desiredThreadCount);
        }
        // thread count 0 means "use everything that's available". The default
        // already does this so there's nothing to do here.
        if (desiredThreadCount == 0) continue;
        // use the explicit value provided by the user
        numThreads = desiredThreadCount;
        continue;
      }
      // profile
      if (args[i].equals("-profile")) {
        profile = true;
        continue;
      }
      // log
      if (args[i].equals("-log")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -log flag without a following argument for the log file");
        logFile = args[i];
        continue;
      }
      // generate detailed information about traversed objects (for debugging)
      if (args[i].equals("-detailed")) {
        verbosity = verbosity.increaseBy(2);
        continue;
      }
      // write words to bootimage in little endian format
      if (args[i].equals("-littleEndian")) {
        littleEndian = true;
        continue;
      }
      fail("unrecognized command line argument: " + args[i]);
    }

    if (verbosity.isAtLeast(Verbosity.DETAILED))
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

    // Redirect the log file
    if (logFile != null) {
      try {
        PrintStream logFileStream = new PrintStream(logFile);
        System.setOut(logFileStream);
        System.setErr(logFileStream);
      } catch (IOException ex) {
        fail("Failed to open log file for redirecting: " + ex.getMessage());
      }
    }

    //
    // Initialize the bootimage.
    // Do this earlier than we logically need to because we need to
    // allocate a massive byte[] that will hold the bootimage in core and
    // on some host JDKs it is essential to do that early while there
    // is still lots of virgin storage left.
    // (need to get contiguous storage before it gets fragmented by pinned objects)
    //
    try {
      bootImage = new BootImage(littleEndian, verbosity.isAtLeast(Verbosity.SUMMARY), bootImageCodeName, bootImageDataName, bootImageRMapName);
    } catch (IOException e) {
      fail("unable to write bootImage: " + e);
    }

    //
    // Install handler that intercepts all object address references made by
    // xxx classes executed on host jdk and substitutes a value that can be
    // fixed up later when those objects are copied from host jdk to bootimage.
    //
    enableObjectAddressRemapper();

    //
    // Initialize rvm classes for use in "bootimage writing" mode.
    // These rvm classes are used two ways:
    //   - they are used now, by host jdk, to create the bootimage
    //   - they are used later, by target rvm, to execute the bootimage
    //
    if (verbosity.isAtLeast(SUMMARY)) say("starting up");
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
      fail("unable to read the type names from " + bootImageTypeNamesFile + ": " + e);
    }
    if (profile) {
      stopTime = System.currentTimeMillis();
      System.out.println("PROF: readingTypeNames " + (stopTime - startTime) + " ms");
    }

    if (profile) startTime = System.currentTimeMillis();
    try {
      createBootImageObjects(bootImageTypeNames, bootImageTypeNamesFile);
    } catch (Exception e) {
      e.printStackTrace(System.out);
      fail("unable to create objects: " + e);
    }
    if (profile) {
      stopTime = System.currentTimeMillis();
      System.out.println("PROF: createBootImageObjects " + (stopTime - startTime) + " ms");
    }

    //
    // No further bootimage object references should get generated.
    // If they are, they'll be assigned an objectId of "-1" (see Magic)
    // and will manifest themselves as an array subscript out of bounds
    // error when BootImageMap attempts to look up the object references.
    //
    disableObjectAddressRemapper();

    ////////////////////////////////////////////////////
    // Copy rvm objects from host jdk into bootimage.
    ////////////////////////////////////////////////////

    VM.writingImage = true;

    if (verbosity.isAtLeast(SUMMARY)) say("Memory available: ",
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
    if (verbosity.isAtLeast(SUMMARY)) say("copying boot record");
    BootRecord bootRecord = BootRecord.the_boot_record;
    Address bootRecordImageAddress = Address.zero();
    try {
      // copy just the boot record
      bootRecordImageAddress = copyToBootImage(bootRecord, true, Address.max(), null, false, AlignmentEncoding.ALIGN_CODE_NONE);
      if (bootRecordImageAddress.EQ(OBJECT_NOT_PRESENT)) {
        fail("can't copy boot record");
      }
    } catch (IllegalAccessException e) {
      fail("can't copy boot record: " + e);
    }

    //
    // Next, copy the jtoc.
    //
    if (verbosity.isAtLeast(SUMMARY)) say("copying jtoc");
    // Pointer to middle of JTOC
    Address jtocImageAddress = Address.max();
    try {
      if (VM.BuildForIA32) {
        // Force 16byte alignment of the JTOC on Intel
        int[] slots = Statics.getSlotsAsIntArray();
        jtocImageAddress = bootImage.allocateArray(RVMArray.IntArray, slots.length, false, 0, 16, AlignmentEncoding.ALIGN_CODE_NONE);
        BootImageMap.Entry jtocEntry = BootImageMap.findOrCreateEntry(slots);
        jtocEntry.imageAddress = jtocImageAddress;
      }
      jtocImageAddress = copyToBootImage(Statics.getSlotsAsIntArray(), false, jtocImageAddress, null, false, AlignmentEncoding.ALIGN_CODE_NONE);
      if (jtocImageAddress.EQ(OBJECT_NOT_PRESENT)) {
        fail("can't copy jtoc");
      }
    } catch (IllegalAccessException e) {
      fail("can't copy jtoc: " + e);
    }
    Address jtocPtr = jtocImageAddress.plus(Statics.middleOfTable << LOG_BYTES_IN_INT);
    if (jtocPtr.NE(bootRecord.tocRegister))
      fail("mismatch in JTOC placement " + Services.addressAsHexString(jtocPtr) + " != " + Services.addressAsHexString(bootRecord.tocRegister));

    //
    // Now, copy all objects reachable from jtoc, replacing each object id
    // that was generated by object address remapper with the actual
    // bootimage address of that object.
    //
    if (verbosity.isAtLeast(SUMMARY)) say("copying statics");
    try {
      int refSlotSize = Statics.getReferenceSlotSize();
      for (int i = Statics.middleOfTable + refSlotSize, n = Statics.getHighestInUseSlot();
           i <= n;
           i += refSlotSize) {
        if (!Statics.isReference(i)) {
          throw new Error("Static " + i + " of " + n + " isn't reference");
        }
        jtocCount = i; // for diagnostic

        Offset jtocOff = Statics.slotAsOffset(i);
        int objCookie;
        if (VM.BuildFor32Addr)
          objCookie = Statics.getSlotContentsAsInt(jtocOff);
        else
          objCookie = (int) Statics.getSlotContentsAsLong(jtocOff);
        // if (verbose.isAtLeast(ADDRESSES))
        // say("       jtoc[", String.valueOf(i), "] = ", String.valueOf(objCookie));
        Object jdkObject = BootImageMap.getObject(objCookie);
        if (jdkObject == null) {
          if (verbosity.isAtLeast(DETAILED)) say("Can not find objectid in BootImageMap " + jtocOff + " " + objCookie);
          continue;
        }

        if (verbosity.isAtLeast(DETAILED)) traceContext.push(jdkObject.getClass().getName(),
                                            getRvmStaticField(jtocOff) + "");
        copyReferenceFieldToBootImage(jtocPtr.plus(jtocOff), jdkObject, Statics.getSlotsAsIntArray(), false, false, null, null);
        if (verbosity.isAtLeast(DETAILED)) traceContext.pop();
      }
      // Copy entries that are in the pending queue
      processPendingEntries();
      // Find and copy unallocated entries
      for (int i = 0; i < BootImageMap.objectIdToEntry.size(); i++) {
        BootImageMap.Entry mapEntry = BootImageMap.objectIdToEntry.get(i);
        if (mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          mapEntry.imageAddress = copyToBootImage(mapEntry.jdkObject, false, Address.max(), null, false, AlignmentEncoding.ALIGN_CODE_NONE);
          fixupLinkAddresses(mapEntry);
        }
      }
    } catch (IllegalAccessException e) {
      fail("unable to copy statics: " + e);
    }
    jtocCount = -1;

    if (profile) {
      stopTime = System.currentTimeMillis();
      System.out.println("PROF: filling bootimage byte[] " + (stopTime - startTime) + " ms");
    }
    //
    // Record startup context in boot record.
    //
    if (verbosity.isAtLeast(SUMMARY)) say("updating boot record");

    byte[] startupStack = startupThread.getStack();
    CodeArray startupCode  = Entrypoints.bootMethod.getCurrentEntryCodeArray();

    bootRecord.spRegister  = BootImageMap.getImageAddress(startupStack, true).plus(startupStack.length);
    bootRecord.ipRegister  = BootImageMap.getImageAddress(startupCode.getBacking(), true);

    bootRecord.bootThreadOffset = Entrypoints.bootThreadField.getOffset();

    bootRecord.bootImageDataStart = bootImageDataAddress;
    bootRecord.bootImageDataEnd   = bootImageDataAddress.plus(bootImageDataSize());
    bootRecord.bootImageCodeStart = bootImageCodeAddress;
    bootRecord.bootImageCodeEnd   = bootImageCodeAddress.plus(bootImageCodeSize());
    bootRecord.bootImageRMapStart = bootImageRMapAddress;
    bootRecord.bootImageRMapEnd   = bootImageRMapAddress.plus(bootImageRMapSize());

    // Update field of boot record now by re-copying
    //
    if (verbosity.isAtLeast(SUMMARY)) say("re-copying boot record (and its TIB)");
    try {
      Address newBootRecordImageAddress = copyToBootImage(bootRecord, false, bootRecordImageAddress, null, false, AlignmentEncoding.ALIGN_CODE_NONE);
      if (!newBootRecordImageAddress.EQ(bootRecordImageAddress)) {
        VM.sysWriteln("bootRecordImageOffset = ", bootRecordImageAddress);
        VM.sysWriteln("newBootRecordImageOffset = ", newBootRecordImageAddress);
        if (VM.VerifyAssertions) {
          VM._assert(newBootRecordImageAddress.EQ(bootRecordImageAddress));
        }
      }
      // Make sure pending entries are fully written out
      processPendingEntries();
    } catch (IllegalAccessException e) {
      fail("unable to update boot record: " + e);
    }

    if (VM.BuildWithGCTrace) {
      /* Set the values in fields updated during the build process */
      Offset prevAddrOffset = Entrypoints.tracePrevAddressField.getOffset();
      bootImage.setAddressWord(jtocPtr.plus(prevAddrOffset),
                               MiscHeader.getBootImageLink().toWord(), false, false);
      Offset oIDOffset = Entrypoints.traceOIDField.getOffset();
      bootImage.setAddressWord(jtocPtr.plus(oIDOffset), MiscHeader.getOID(), false, false);
    }

    //
    // Write image to disk.
    //
    if (profile) startTime = System.currentTimeMillis();
    try {
      bootImage.write();
    } catch (IOException e) {
      fail("unable to write bootImage: " + e);
    }

    if (profile) {
      stopTime = System.currentTimeMillis();
      System.out.println("PROF: writing RVM.map " + (stopTime - startTime) + " ms");
    }

    // Print report about field differences
    if (verbosity.isAtLeast(SUMMARY)) {
      BootImageTypes.printFieldDifferenceReport();
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
    if (verbosity.isAtLeast(DETAILED)) {
      for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < RVMType.numTypes(); ++i) {
        RVMType type = RVMType.getType(i);
        if (type == null) continue;
        if (!type.isResolved()) {
          say("type referenced but not resolved: ", type.toString());
        } else if (!type.isInstantiated()) {
          say("type referenced but not instantiated: ", type.toString());
        } else if (!type.isInitialized()) {
          say("type referenced but not initialized: ", type.toString());
        }
      }
    }

    //
    // Generate address map for debugging.
    //
    try {
      if (bootImageMapName != null)
        MethodAddressMap.writeAddressMap(bootImageMapName);
    } catch (IOException e) {
      fail("unable to write address map: " + e);
    }

    if (verbosity.isAtLeast(SUMMARY)) say("done");
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
   */
  private static class TypeComparator<T> implements Comparator<T> {

    @Override
    public int compare(T a, T b) {
      if (a == null && b == null) return 0;
      if (a == null) return 1;
      if (b == null) return -1;
      if ((a instanceof RVMType) && (b instanceof RVMType)) {
        RVMType typeA = (RVMType) a;
        RVMType typeB = (RVMType) b;
        DemographicInformation infoA = demographicData.get(typeA);
        DemographicInformation infoB = demographicData.get(typeB);
        if (infoA == null && infoB == null) return 0;
        if (infoA == null) return 1;
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
  private static final HashMap<RVMType,DemographicInformation> demographicData =
    new HashMap<RVMType,DemographicInformation>();

  /**
   * Log an allocation in the boot image
   * @param type the type allocated
   * @param size the size of the type
   */
  public static void logAllocation(RVMType type, int size) {
    if (demographics) {
      DemographicInformation info = demographicData.get(type);
      if (info != null) {
        info.count++;
        info.size += size;
      } else {
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
    RVMType[] tempTypes = new RVMType[RVMType.numTypes() - FIRST_TYPE_DICTIONARY_INDEX];
    for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < RVMType.numTypes(); ++i)
      tempTypes[i - FIRST_TYPE_DICTIONARY_INDEX] = RVMType.getType(i);
    Arrays.sort(tempTypes, new TypeComparator<RVMType>());
    int totalCount = 0, totalBytes = 0;
    for (RVMType type : tempTypes) {
      if (type == null) continue;
      DemographicInformation info = demographicData.get(type);
      if (info == null) continue;
      totalCount += info.count;
      totalBytes += info.size;
    }
    VM.sysWriteln();
    VM.sysWriteln("Boot image space report:");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    VM.sysWriteField(60, "TOTAL");
    VM.sysWriteField(15, totalCount);
    VM.sysWriteField(15, totalBytes);
    VM.sysWriteln();

    VM.sysWriteln();
    VM.sysWriteln("Compiled methods space report:");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    CompiledMethods.spaceReport();

    VM.sysWriteln("------------------------------------------------------------------------------------------");
    VM.sysWriteln();
    VM.sysWriteln("Boot image space usage by types:");
    VM.sysWriteln("Type                                                               Count             Bytes");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    VM.sysWriteField(60, "TOTAL");
    VM.sysWriteField(15, totalCount);
    VM.sysWriteField(15, totalBytes);
    VM.sysWriteln();
    for (RVMType type : tempTypes) {
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
      if (File.separatorChar != '/') {
        typeName = typeName.replace(File.separatorChar, '/');
      }
      // debugging:
      TypeDescriptorParsing.validateAsTypeDescriptor(typeName);
      if (TypeDescriptorParsing.isValidTypeDescriptor(typeName))
        typeNames.add(typeName);
      else
        fail(fileName + ":" + in.getLineNumber() +
             ": syntax error: \"" +
             typeName + "\" does not describe any Java type.");
    }
    in.close();

    return typeNames;
  }

  /**
   * Create (in host JDK address space) the RVM objects that will be
   * needed at run time to execute enough of the virtual machine
   * to dynamically load and compile the remainder of itself.<p>
   *
   * Side effects:
   * <ul>
   *   <li>RVM objects are created in host JDK address space
   *   <li>Statics is populated
   *   <li>"bootImageTypes" dictionary is populated with name/type pairs
   * </ul>
   *
   * @param typeNames names of RVM classes whose static fields will contain
   *                  the objects comprising the virtual machine bootimage
   */
  public static void createBootImageObjects(Vector<String> typeNames,
                                            String bootImageTypeNamesFile)
    throws IllegalAccessException {
      Callbacks.notifyBootImage(typeNames.elements());
      long startTime = 0;
      long stopTime = 0;

      //
      // Create types.
      //
      if (verbosity.isAtLeast(SUMMARY)) say("loading");
      if (profile) startTime = System.currentTimeMillis();

      for (String typeName : typeNames) {
        //
        // get type name
        //
        if (verbosity.isAtLeast(TYPE_NAMES))
          say("typeName:", typeName);


        //
        // create corresponding rvm type
        //
        RVMType type;
        try {
          TypeReference tRef = TypeReference.findOrCreate(typeName);
          type = tRef.resolve();
        } catch (NoClassDefFoundError ncdf) {
          ncdf.printStackTrace(System.out);
          fail(bootImageTypeNamesFile +
               " contains a class named \"" + typeName +
               "\", but we can't find a class with that name: " + ncdf);
          return;               // NOTREACHED
        } catch (IllegalArgumentException ila) {
          /* We should've caught any illegal type names at the data validation
           * stage, when we read these in.  If not, though,
           * TypeReference.findOrCreate() will do its own sanity check.  */
          ila.printStackTrace(System.out);
          fail(bootImageTypeNamesFile +
               " is supposed to contain type names.  It contains \"" + typeName +
               "\", which does not parse as a legal type name: " + ila);
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
        BootImageTypes.record(typeName, type);
      }

      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tloading types " + (stopTime - startTime) + " ms");
      }

      int typeCount = BootImageTypes.typeCount();
      JMXSupport.CLASS_LOADING_JMX_SUPPORT.setClassLoadedCountForBootimage(typeCount);
      if (verbosity.isAtLeast(SUMMARY)) say(String.valueOf(typeCount), " types");

      //
      // Lay out fields and method tables.
      //
      if (profile) startTime = System.currentTimeMillis();
      if (verbosity.isAtLeast(SUMMARY)) say("resolving");

      ArrayList<RVMType> replacementClasses = new ArrayList<RVMType>();
      for (RVMType type : BootImageTypes.allTypes()) {
        if (verbosity.isAtLeast(DETAILED)) say("resolving " + type);

        // Resolve replacement classer later because we need to subject them to additional checks.
        Atom typeDescriptor = type.getDescriptor();
        if (VM.BuildForOpenJDK && typeDescriptor.toString().startsWith("Lorg/jikesrvm/classlibrary/openjdk/replacements/")) {
          if (verbosity.isAtLeast(DETAILED)) say("SKIPPING resolving " + type + " , will be done later");
          replacementClasses.add(type);
          continue;
        }

        // The resolution is supposed to be cached already.
        type.resolve();
      }
      // Resolve replacement classes
      for (RVMType type : replacementClasses) {
        if (verbosity.isAtLeast(DETAILED)) say("resolving previously skipped replacement class " + type);
        boolean resolved = type.isResolved();
        if (!resolved) {
          String message = "Type for replacement class " + type + " wasn't resolved yet: is the class that's" +
              " supposed to be replaced in the boot image? Note: Inner classes of the to be replaced" +
              " class may also need to be in the boot image";
          if (VM.VerifyAssertions) {
            VM._assert(resolved, message);
          } else {
            VM.sysFail(message);
          }
        }
        type.resolve();
      }

      //
      // Now that all types are resolved, do some additional fixup before we do any compilation
      //
      for (RVMType type : BootImageTypes.allTypes()) {
        type.allBootImageTypesResolved();
      }

      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tresolving types " + (stopTime - startTime) + " ms");
      }


      // Set tocRegister early so opt compiler can access it to
      //   perform fixed_jtoc optimization (compile static addresses into code).

      // In the boot image, the bootrecord comes first followed by a
      // Address array and then the TOC.  To do this, we must fully
      // simulate the alignment logic in the allocation code!  Rather
      // than replicate the allocation code here, we perform dummy
      // allocations and then reset the boot image allocator.
      BootRecord bootRecord = BootRecord.the_boot_record;
      RVMClass rvmBRType = BootImageTypes.getRvmTypeForHostType(bootRecord.getClass()).asClass();
      RVMArray intArrayType =  RVMArray.IntArray;
      // allocate storage for boot record
      bootImage.allocateDataStorage(rvmBRType.getInstanceSize(),
                                    ObjectModel.getAlignment(rvmBRType),
                                    ObjectModel.getOffsetForAlignment(rvmBRType, false));
      // allocate storage for JTOC (force 16byte alignment of the JTOC on Intel)
      Address jtocAddress = bootImage.allocateDataStorage(intArrayType.getInstanceSize(0),
                                                          VM.BuildForIA32 ? 16 : ObjectModel.getAlignment(intArrayType),
                                                          ObjectModel.getOffsetForAlignment(intArrayType, false));
      bootImage.resetAllocator();
      bootRecord.tocRegister = jtocAddress.plus(intArrayType.getInstanceSize(Statics.middleOfTable));

      // set up some stuff we need for compiling
      ArchitectureFactory.initOutOfLineMachineCode();

      //
      // Compile methods and populate jtoc with literals, TIBs, and machine code.
      //
      if (profile) startTime = System.currentTimeMillis();
      if (verbosity.isAtLeast(SUMMARY)) say("instantiating");

      if (verbosity.isAtLeast(SUMMARY)) say("setting up compilation infrastructure and pre-compiling easy cases");
      CompilationOrder order = new CompilationOrder(typeCount, numThreads);
      for (RVMType type: BootImageTypes.allTypes()) {
        order.addType(type);
      }
      order.fixUpMissingSuperClasses();

      if (verbosity.isAtLeast(SUMMARY)) say(" compiling with " + numThreads + " threads");
      ThreadFactory threadFactory = new KillVMonUncaughtExceptionThreadFactory();
      ExecutorService threadPool = Executors.newFixedThreadPool(numThreads, threadFactory);
      int runnableCount = order.getCountOfNeededWorkers();
      while (runnableCount > 0) {
        try {
          threadPool.execute(order.getNextRunnable());
          runnableCount--;
        } catch (InterruptedException e) {
          throw new Error("Build interrupted", e);
        }
      }
      threadPool.shutdown();
      try {
        while (!threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)) {
          say("Compilation really shouldn't take this long");
        }
      } catch (InterruptedException e) {
        throw new Error("Build interrupted", e);
      }
      if (BootImageWorker.instantiationFailed) {
        throw new Error("Error during instantiaion");
      }

      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tinstantiating types " + (stopTime - startTime) + " ms");
      }

      // Free up unnecessary Statics data structures
      MethodAddressMap.setStaticsJunk(Statics.bootImageInstantiationFinished());

      // Do the portion of JNIEnvironment initialization that can be done
      // at bootimage writing time.
      FunctionTable functionTable = BuildJNIFunctionTable.buildTable();
      JNIEnvironment.initFunctionTable(functionTable);

      //
      // Collect the VM class Field to JDK class Field correspondence
      // This will be needed when building the images of each object instance
      // and for processing the static fields of the boot image classes
      //
      if (verbosity.isAtLeast(SUMMARY)) say("field info gathering");
      if (profile) startTime = System.currentTimeMillis();
      HashSet<String> invalidEntrys = BootImageTypes.createBootImageTypeFields();

      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tcreating type mapping " + (stopTime - startTime) + " ms");
      }

      //
      // Create stack, thread, and processor context in which rvm will begin
      // execution.
      startupThread = RVMThread.setupBootThread();
      byte[] stack = startupThread.getStack();
      // sanity check for bootstrap loader
      int idx = stack.length - 1;
      if (VM.LittleEndian) {
        stack[idx--] = (byte)0xde;
        stack[idx--] = (byte)0xad;
        stack[idx--] = (byte)0xba;
        stack[idx--] = (byte)0xbe;
      } else {
        stack[idx--] = (byte)0xbe;
        stack[idx--] = (byte)0xba;
        stack[idx--] = (byte)0xad;
        stack[idx--] = (byte)0xde;
      }

      //
      // Tell RVM where to find itself at execution time.
      // This may not be the same place it was at build time, ie. if image is
      // moved to another machine with different directory structure.
      //
      BootstrapClassLoader.setBootstrapRepositories(bootImageRepositoriesAtExecutionTime);

      //
      // Finally, populate JTOC with static field values.
      // This is equivalent to the RVMClass.initialize() phase that would have
      // executed each class's static constructors at run time.  We simulate
      // this by copying statics created in the host RVM into the appropriate
      // slots of the JTOC.
      //
      if (verbosity.isAtLeast(SUMMARY)) say("populating jtoc with static fields");
      if (profile) startTime = System.currentTimeMillis();
      for (RVMType rvmType : BootImageTypes.allTypes()) {
        if (verbosity.isAtLeast(SUMMARY)) say("  jtoc for ", rvmType.toString());
        if (!rvmType.isClassType())
          continue; // arrays and primitives have no static fields

        Class<?> jdkType = BootImageTypes.getJdkType(rvmType);
        if (jdkType == null && verbosity.isAtLeast(SUMMARY)) {
          say("host has no class \"" + rvmType + "\"");
        }

        RVMField[] rvmFields = rvmType.getStaticFields();
        for (int j = 0; j < rvmFields.length; ++j) {
          RVMField rvmField     = rvmFields[j];
          TypeReference rvmFieldType = rvmField.getType();
          Offset rvmFieldOffset = rvmField.getOffset();
          String   rvmFieldName = rvmField.getName().toString();

          FieldValues.copyStaticFieldValue(invalidEntrys, rvmType, jdkType, j, rvmField,
              rvmFieldType, rvmFieldOffset, rvmFieldName);
        }
      }
      if (verbosity.isAtLeast(DETAILED)) {
        for (final String entry : invalidEntrys) {
          say("Static fields of type are invalid: ", entry);
        }
      }

      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tinitializing jtoc " + (stopTime - startTime) + " ms");
      }
  }

  static boolean equalTypes(final String name, final TypeReference rvmFieldType) {
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
    } else {
      return ('L' + name.replace('.', '/') + ';').equals(descriptor);
    }
  }

  private static final int LARGE_ARRAY_SIZE = 16 * 1024;
  private static final int LARGE_SCALAR_SIZE = 1024;
  private static int depth = -1;
  private static int jtocCount = -1;
  private static final String SPACES = "                                                                                                                                                                                                                                                                                                                                ";

  private static void check(Word value, String msg) {
    Word low = ObjectModel.maximumObjectRef(Address.zero()).toWord();  // yes, max
    Word high = Word.fromIntZeroExtend(0x10000000);  // we shouldn't have that many objects
    if (value.GT(low) && value.LT(high) && !value.EQ(Word.fromIntZeroExtend(32767)) &&
        (value.LT(Word.fromIntZeroExtend(4088)) || value.GT(Word.fromIntZeroExtend(4096)))) {
      say("Warning: Suspicious Address value of ", Services.addressAsHexString(value.toAddress()),
          " written for " + msg);
    }
  }

  static Word getWordValue(Object addr, String msg, boolean warn) {
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
      say("Unhandled supposed address value: " + addr);
      say(msg);
      fail("incomplete boot image support");
    }
    if (warn) check(value, msg);
    return value;
  }

  /**
   * Write a field that contains a reference to the boot image
   * @param fieldLocation address in boot image of field
   * @param referencedObject the object whose address will be written at this
   * location
   * @param parentObject object containing this fieldLocation
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   * @param root Does this slot contain a possible reference into the heap?
   * (objField must also be true)
   * @param rvmFieldName Name of the field
   * @param rvmFieldType Type of the field
    */
  static void copyReferenceFieldToBootImage(Address fieldLocation, Object referencedObject,
      Object parentObject, boolean objField, boolean root, String rvmFieldName,
      TypeReference rvmFieldType) throws IllegalAccessException {
    if (referencedObject == null) {
      bootImage.setNullAddressWord(fieldLocation, objField, root, true);
    } else {
      BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(referencedObject);
      if (mapEntry.imageAddress.EQ(OBJECT_NOT_PRESENT)) {
        if (rvmFieldName == null || !FieldValues.copyKnownValueForInstanceField(parentObject, rvmFieldName, rvmFieldType, fieldLocation)) {
          // object not part of bootimage: install null reference
          if (verbosity.isAtLeast(DETAILED)) traceContext.traceObjectNotInBootImage();
          bootImage.setNullAddressWord(fieldLocation, objField, root, false);
        }
      } else if (mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
        Address imageAddress;
        if (true) {
          // Normal collection based traversal
          mapEntry.addLinkingAddress(fieldLocation, objField, root, rvmFieldName, rvmFieldType, parentObject);
          if (!mapEntry.isPendingEntry()) {
            mapEntry.setPendingEntry();
            pendingEntries.add(mapEntry);
          }
          imageAddress = OBJECT_ALLOCATION_DEFERRED;
          root = false;
        } else {
          // Recurse placing work on the stack
          mapEntry.imageAddress = copyToBootImage(referencedObject, false, Address.max(), parentObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          imageAddress = mapEntry.imageAddress;
        }
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          if (verbosity.isAtLeast(DETAILED)) traceContext.traceObjectNotInBootImage();
          if (!FieldValues.copyKnownValueForInstanceField(parentObject, rvmFieldName, rvmFieldType, fieldLocation)) {
            // object not part of bootimage: install null reference
            if (verbosity.isAtLeast(DETAILED)) traceContext.traceObjectNotInBootImage();
            bootImage.setNullAddressWord(fieldLocation, objField, root, false);
          }
        } else {
          bootImage.setAddressWord(fieldLocation, imageAddress.toWord(), objField, root);
        }
      } else {
        bootImage.setAddressWord(fieldLocation, mapEntry.imageAddress.toWord(), objField, root);
      }
    }
  }

  /**
   * Process any entries that have been deferred
   * @throws IllegalAccessException
   */
  private static void processPendingEntries() throws IllegalAccessException {
    while (!pendingEntries.isEmpty()) {
      BootImageMap.Entry mapEntry = pendingEntries.remove();
      mapEntry.clearPendingEntry();
      if (mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
        mapEntry.imageAddress = copyToBootImage(mapEntry.jdkObject, false, Address.max(), null, false, AlignmentEncoding.ALIGN_CODE_NONE);
      }
      fixupLinkAddresses(mapEntry);
    }
  }

  /**
   * Iterate over link address registered with entry writing out boot image address
   * @param mapEntry entry containing addresses to fix up
   * @return number of entries fixed up
   * @throws IllegalAccessException
   */
  private static int fixupLinkAddresses(BootImageMap.Entry mapEntry) throws IllegalAccessException {
    int count = 0;
    BootImageMap.Entry.LinkInfo info = mapEntry.removeLinkingAddress();
    while (info != null) {
      if (mapEntry.imageAddress.EQ(OBJECT_NOT_PRESENT)) {
        if (info.rvmFieldName == null || !FieldValues.copyKnownValueForInstanceField(info.parent, info.rvmFieldName, info.rvmFieldType, info.addressToFixup)) {
          // object not part of bootimage: install null reference
          if (verbosity.isAtLeast(DETAILED)) traceContext.traceObjectNotInBootImage();
          bootImage.setNullAddressWord(info.addressToFixup, info.objField, info.root, false);
        }
      } else {
        bootImage.setAddressWord(info.addressToFixup, mapEntry.imageAddress.toWord(), info.objField, info.root);
      }
      info = mapEntry.removeLinkingAddress();
      count++;
    }
    return count;
  }

  /**
   * Copy an object (and, recursively, any of its fields or elements that
   * are references) from host jdk address space into image.
   *
   * @param jdkObject object to be copied
   * @param allocOnly if allocOnly is true, the TIB and other reference fields are not recursively copied
   * @param overwriteAddress if !overwriteAddress.isMax(), then copy object to given address
   * @param parentObject
   * @param untraced Do not report any fields of this object as references
   * @param alignCode Alignment-encoded value (TIB allocation only)
   * @return offset of copied object within image, in bytes
   *         (OBJECT_NOT_PRESENT --> object not copied:
   *            it's not part of bootimage)
   */
  static Address copyToBootImage(Object jdkObject, boolean allocOnly,
      Address overwriteAddress, Object parentObject, boolean untraced, int alignCode) throws IllegalAccessException {
    try {
      // Return object if it is already copied and not being overwritten
      BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
      if ((!mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) && overwriteAddress.isMax()) {
        return mapEntry.imageAddress;
      }

      if (verbosity.isAtLeast(DETAILED)) depth++;

      // fetch object's type information
      Class<?>   jdkType = jdkObject.getClass();
      RVMType rvmType = BootImageTypes.getRvmTypeForHostType(jdkType);
      if (rvmType == null) {
        if (verbosity.isAtLeast(DETAILED)) traverseObject(jdkObject);
        if (verbosity.isAtLeast(DETAILED)) say("Object Not Present " + jdkType.getName() + " for " + jdkObject.toString());
        if (verbosity.isAtLeast(DETAILED)) depth--;
        return OBJECT_NOT_PRESENT; // object not part of bootimage
      }

      // copy object to image
      if (jdkType.isArray()) {
        // allocate space in image prior to recursing
        int arrayCount       = Array.getLength(jdkObject);
        RVMArray rvmArrayType = rvmType.asArray();
        boolean needsIdentityHash = mapEntry.requiresIdentityHashCode();
        int identityHashValue = mapEntry.getIdentityHashCode();
        Address arrayImageAddress = (overwriteAddress.isMax()) ? bootImage.allocateArray(rvmArrayType, arrayCount, needsIdentityHash, identityHashValue, alignCode) : overwriteAddress;
        mapEntry.imageAddress = arrayImageAddress;
        mapEntry.imageAddress = copyArrayToBootImage(arrayCount, arrayImageAddress, jdkObject, jdkType,
            rvmArrayType, allocOnly, overwriteAddress, parentObject, untraced);
        // copy object's type information block into image, if it's not there
        // already
        if (!allocOnly) {
          if (verbosity.isAtLeast(DETAILED)) traceContext.push("", jdkObject.getClass().getName(), "tib");
          Address tibImageAddress = copyToBootImage(rvmType.getTypeInformationBlock(), allocOnly, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (verbosity.isAtLeast(DETAILED)) traceContext.pop();
          if (tibImageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            fail("can't copy tib for " + jdkObject);
          }
          ObjectModel.setTIB(bootImage, mapEntry.imageAddress, tibImageAddress, rvmType);
        }
      } else if (jdkObject instanceof TIB) {
        Object backing = ((RuntimeTable<?>)jdkObject).getBacking();

        int alignCodeValue = ((TIB)jdkObject).getAlignData();
        if (verbosity.isAtLeast(DETAILED)) say("Encoding value " + alignCodeValue + " into tib");

        /* Copy the backing array, and then replace its TIB */
        mapEntry.imageAddress = copyToBootImage(backing, allocOnly, overwriteAddress, jdkObject, rvmType.getTypeRef().isRuntimeTable(), alignCodeValue);

        if (verbosity.isAtLeast(DETAILED)) say(String.format("TIB address = %x, encoded value = %d, requested = %d%n",
            mapEntry.imageAddress.toInt(),
            AlignmentEncoding.extractTibCode(mapEntry.imageAddress),alignCodeValue));

        if (!allocOnly) {
          copyTIBToBootImage(rvmType, jdkObject, mapEntry.imageAddress);
        }
      } else if (rvmType == RVMType.ObjectReferenceArrayType || rvmType.getTypeRef().isRuntimeTable()) {
        Object backing = ((RuntimeTable<?>)jdkObject).getBacking();

        /* Copy the backing array, and then replace its TIB */
        mapEntry.imageAddress = copyToBootImage(backing, allocOnly, overwriteAddress, jdkObject, rvmType.getTypeRef().isRuntimeTable(), AlignmentEncoding.ALIGN_CODE_NONE);

        if (!allocOnly) {
          copyTIBToBootImage(rvmType, jdkObject, mapEntry.imageAddress);
        }
      } else if (jdkObject instanceof RuntimeTable) {
        Object backing = ((RuntimeTable<?>)jdkObject).getBacking();
        mapEntry.imageAddress = copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteAddress, parentObject);
      } else if (rvmType == RVMType.CodeArrayType) {
        // Handle the code array that is represented as either byte or int arrays
        if (verbosity.isAtLeast(DETAILED)) depth--;
        Object backing = ((CodeArray)jdkObject).getBacking();
        return copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteAddress, parentObject);
      } else if (rvmType.getTypeRef().isMagicType()) {
        say("Unhandled copying of magic type: " + rvmType.getDescriptor().toString() +
            " in object of type " + parentObject.getClass().toString());
        fail("incomplete boot image support");
      } else {
        // allocate space in image
        if (rvmType instanceof RVMArray) fail("This isn't a scalar " + rvmType);
        RVMClass rvmScalarType = rvmType.asClass();
        boolean needsIdentityHash = mapEntry.requiresIdentityHashCode();
        int identityHashValue = mapEntry.getIdentityHashCode();
        Address scalarImageAddress = (overwriteAddress.isMax()) ? bootImage.allocateScalar(rvmScalarType, needsIdentityHash, identityHashValue) : overwriteAddress;
        mapEntry.imageAddress = scalarImageAddress;
        mapEntry.imageAddress = copyClassToBootImage(scalarImageAddress, jdkObject, jdkType, rvmScalarType,
            allocOnly, overwriteAddress, parentObject, untraced);
        // copy object's type information block into image, if it's not there
        // already
        if (!allocOnly) {
          copyTIBToBootImage(rvmType, jdkObject, mapEntry.imageAddress);
        }
      }
      if (verbosity.isAtLeast(DETAILED)) depth--;
      return mapEntry.imageAddress;
    } catch (Error e) {
      e = new Error(e.getMessage() + "\nwhile copying " +
          jdkObject + (jdkObject != null ? ":" + jdkObject.getClass() : "") + " from " +
          parentObject + (parentObject != null ? ":" + parentObject.getClass() : ""),
          e.getCause() != null ? e.getCause() : e);
      throw e;
    }
  }

  /**
   * Allocate and set TIB
   *
   * @param rvmType type for TIB
   * @param jdkObject parent object
   * @param imageAddress address of object to set TIB of
   * @throws IllegalAccessException
   */
  private static void copyTIBToBootImage(RVMType rvmType, Object jdkObject, Address imageAddress) throws IllegalAccessException {
    if (verbosity.isAtLeast(DETAILED)) {
      depth--;
      traceContext.push("", jdkObject.getClass().getName(), "tib");
    }
    Address tibImageAddress = copyToBootImage(rvmType.getTypeInformationBlock(), false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
    if (verbosity.isAtLeast(DETAILED)) {
      traceContext.pop();
      depth++;
    }
    if (tibImageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
      fail("can't copy tib for " + jdkObject);
    }
    ObjectModel.setTIB(bootImage, imageAddress, tibImageAddress, rvmType);
  }

  /**
   * Write an object instantiating a class to the boot image
   * @param scalarImageAddress address already allocated for object
   * @param jdkObject object to write
   * @param jdkType java.lang.Class of object
   * @param rvmScalarType RVM class loader version of type
   * @param allocOnly allocate the object only?
   * @param overwriteAddress
   * @param parentObject
   * @param untraced
   * @return
   * @throws IllegalAccessException
   */
  private static Address copyClassToBootImage(Address scalarImageAddress, Object jdkObject, Class<?> jdkType,
      RVMClass rvmScalarType, boolean allocOnly, Address overwriteAddress, Object parentObject, boolean  untraced)
  throws IllegalAccessException {
    if (verbosity.isAtLeast(DETAILED)) {
      if (depth == DEPTH_CUTOFF)
        say(SPACES.substring(0, depth + 1), "TOO DEEP: cutting off");
      else if (depth < DEPTH_CUTOFF) {
        String tab = SPACES.substring(0, depth + 1);
        if (depth == 0 && jtocCount >= 0)
          tab = tab + "jtoc #" + String.valueOf(jtocCount) + " ";
        int scalarSize = rvmScalarType.getInstanceSize();
        say(tab, "Copying object ", jdkType.getName(),
            "   size=", String.valueOf(scalarSize),
            (scalarSize >= LARGE_SCALAR_SIZE) ? " large object!!!" : "");
      }
    }

    // copy object fields from host jdk address space into image
    // recurse on values that are references
    RVMField[] rvmFields = rvmScalarType.getInstanceFields();
    for (int i = 0; i < rvmFields.length; ++i) {
      RVMField rvmField       = rvmFields[i];
      TypeReference rvmFieldType   = rvmField.getType();
      Address rvmFieldAddress = scalarImageAddress.plus(rvmField.getOffset());
      String  rvmFieldName    = rvmField.getName().toString();
      Field   jdkFieldAcc     = BootImageTypes.getJdkFieldAccessor(jdkType, i, INSTANCE_FIELD);

      boolean untracedField = rvmField.isUntraced() || untraced;

      FieldValues.copyInstanceFieldValue(jdkObject, jdkType, rvmScalarType, allocOnly,
          rvmField, rvmFieldType, rvmFieldAddress, rvmFieldName, jdkFieldAcc,
          untracedField);
    }
    return scalarImageAddress;
  }

  /**
   * Write array to boot image
   * @param arrayCount
   * @param arrayImageAddress
   * @param jdkObject
   * @param jdkType
   * @param rvmArrayType
   * @param allocOnly
   * @param overwriteAddress
   * @param parentObject
   * @param untraced
   * @return
   * @throws IllegalAccessException
   */
  private static Address copyArrayToBootImage(int arrayCount, Address arrayImageAddress, Object jdkObject, Class<?> jdkType, RVMArray rvmArrayType,
      boolean allocOnly, Address overwriteAddress, Object parentObject, boolean  untraced)
  throws IllegalAccessException {
    if (verbosity.isAtLeast(DETAILED)) {
      if (depth == DEPTH_CUTOFF)
        say(SPACES.substring(0, depth + 1), "TOO DEEP: cutting off");
      else if (depth < DEPTH_CUTOFF) {
        String tab = SPACES.substring(0, depth + 1);
        if (depth == 0 && jtocCount >= 0)
          tab = tab + "jtoc #" + String.valueOf(jtocCount) + ": ";
        int arraySize = rvmArrayType.getInstanceSize(arrayCount);
        say(tab, "Copying array  ", jdkType.getName(),
            "   length=", String.valueOf(arrayCount),
            (arraySize >= LARGE_ARRAY_SIZE) ? " large object!!!" : "");
      }
    }

    RVMType rvmElementType = rvmArrayType.getElementType();

    // copy array elements from host jdk address space into image
    // recurse on values that are references
    if (rvmElementType.isPrimitiveType()) {
      // array element is logical or numeric type
      if (rvmElementType.equals(RVMType.BooleanType)) {
        boolean[] values = (boolean[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setByte(arrayImageAddress.plus(i), values[i] ? 1 : 0);
      } else if (rvmElementType.equals(RVMType.ByteType)) {
        byte[] values = (byte[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setByte(arrayImageAddress.plus(i), values[i]);
      } else if (rvmElementType.equals(RVMType.CharType)) {
        char[] values = (char[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setHalfWord(arrayImageAddress.plus(i << LOG_BYTES_IN_CHAR), values[i]);
      } else if (rvmElementType.equals(RVMType.ShortType)) {
        short[] values = (short[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setHalfWord(arrayImageAddress.plus(i << LOG_BYTES_IN_SHORT), values[i]);
      } else if (rvmElementType.equals(RVMType.IntType)) {
        int[] values = (int[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setFullWord(arrayImageAddress.plus(i << LOG_BYTES_IN_INT), values[i]);
      } else if (rvmElementType.equals(RVMType.LongType)) {
        long[] values = (long[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setDoubleWord(arrayImageAddress.plus(i << LOG_BYTES_IN_LONG), values[i]);
      } else if (rvmElementType.equals(RVMType.FloatType)) {
        float[] values = (float[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setFullWord(arrayImageAddress.plus(i << LOG_BYTES_IN_FLOAT),
              Float.floatToIntBits(values[i]));
      } else if (rvmElementType.equals(RVMType.DoubleType)) {
        double[] values = (double[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setDoubleWord(arrayImageAddress.plus(i << LOG_BYTES_IN_DOUBLE),
              Double.doubleToLongBits(values[i]));
      } else {
        fail("unexpected primitive array type: " + rvmArrayType);
      }
    } else {
      // array element is reference type
      boolean isTIB = parentObject instanceof TIB;
      Object[] values = (Object []) jdkObject;
      Class<?> jdkClass = jdkObject.getClass();
      if (!allocOnly) {
        for (int i = 0; i < arrayCount; ++i) {
          if (values[i] != null) {
            if (verbosity.isAtLeast(DETAILED)) traceContext.push(values[i].getClass().getName(), jdkClass.getName(), i);
            if (isTIB && values[i] instanceof Word) {
              bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), (Word)values[i], false, false);
            } else if (isTIB && values[i] == LazyCompilationTrampoline.getInstructions()) {
              Address codeAddress = arrayImageAddress.plus(((TIB)parentObject).lazyMethodInvokerTrampolineIndex() << LOG_BYTES_IN_ADDRESS);
              bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), codeAddress.toWord(), false, false);
            } else {
              copyReferenceFieldToBootImage(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), values[i],
                  jdkObject, !untraced, !untraced, null, null);
            }
            if (verbosity.isAtLeast(DETAILED)) traceContext.pop();
          } else {
            bootImage.setNullAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), !untraced, !untraced, true);
          }
        }
      }
    }
    return arrayImageAddress;
  }

  /**
   * Copy a unboxed array type to the boot image
   * @param jdkObject object representation
   * @param rvmArrayType type of array
   * @param allocOnly allocate object don't write to fields
   * @param overwriteAddress addresss to write to if overwriting
   * @param parentObject object containing array
   * @return address of array
   * @throws IllegalAccessException
   */
  private static Address copyMagicArrayToBootImage(Object jdkObject,
                                                   RVMArray rvmArrayType,
                                                   boolean allocOnly,
                                                   Address overwriteAddress,
                                                   Object parentObject)
    throws IllegalAccessException {
    // Return object if it is already copied and not being overwritten
    BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
    if ((!mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) && overwriteAddress.isMax()) {
      return mapEntry.imageAddress;
    }

    if (verbosity.isAtLeast(DETAILED)) depth++;

    RVMType rvmElementType = rvmArrayType.getElementType();

    // allocate space in image
    int arrayCount       = Array.getLength(jdkObject);
    Address arrayImageAddress;
    if (overwriteAddress.isMax()) {
      if (rvmElementType.equals(RVMType.CodeType)) {
        arrayImageAddress = bootImage.allocateCode(rvmArrayType, arrayCount);
      } else {
        boolean needsIdentityHash = mapEntry.requiresIdentityHashCode();
        int identityHashValue = mapEntry.getIdentityHashCode();
        arrayImageAddress = bootImage.allocateArray(rvmArrayType, arrayCount, needsIdentityHash, identityHashValue, AlignmentEncoding.ALIGN_CODE_NONE);
      }
    } else {
      arrayImageAddress = overwriteAddress;
    }
    mapEntry.imageAddress = arrayImageAddress;

    if (verbosity.isAtLeast(DETAILED)) {
      if (depth == DEPTH_CUTOFF)
        say(SPACES.substring(0, depth + 1), "TOO DEEP: cutting off");
      else if (depth < DEPTH_CUTOFF) {
        String tab = SPACES.substring(0, depth + 1);
        if (depth == 0 && jtocCount >= 0)
          tab = tab + "jtoc #" + String.valueOf(jtocCount) + ": ";
        int arraySize = rvmArrayType.getInstanceSize(arrayCount);
        say(tab, "Copying array  ", rvmArrayType.toString(),
            "   length=", String.valueOf(arrayCount),
            (arraySize >= LARGE_ARRAY_SIZE) ? " large object!!!" : "");
      }
    }

    // copy array elements from host jdk address space into image
    if (rvmElementType.equals(RVMType.CodeType)) {
      if (VM.BuildForIA32) {
        byte[] values = (byte[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setByte(arrayImageAddress.plus(i), values[i]);
      } else {
        int[] values = (int[]) jdkObject;
        for (int i = 0; i < arrayCount; ++i)
          bootImage.setFullWord(arrayImageAddress.plus(i << LOG_BYTES_IN_INT), values[i]);
      }
    } else if (rvmElementType.equals(RVMType.AddressType)) {
      Address[] values = (Address[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        Address addr = values[i];
        String msg = "Address array element";
        bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, true), false, false);
      }
    } else if (rvmElementType.equals(RVMType.WordType)) {
      Word[] values = (Word[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        String msg = "Word array element ";
        Word addr = values[i];
        bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, false), false, false);
      }
    } else if (rvmElementType.equals(RVMType.OffsetType)) {
      Offset[] values = (Offset[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        String msg = "Offset array element " + i;
        Offset addr = values[i];
        bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, false), false, false);
      }
    } else if (rvmElementType.equals(RVMType.ExtentType)) {
      Extent[] values = (Extent[]) jdkObject;
      for (int i = 0; i < arrayCount; i++) {
        String msg = "Extent array element ";
        Extent addr = values[i];
        bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS),
                                 getWordValue(addr, msg, false), false, false);
      }
    } else {
      fail("unexpected magic array type: " + rvmArrayType);
    }

    // copy object's TIB into image, if it's not there already
    if (!allocOnly) {
      copyTIBToBootImage(rvmArrayType, jdkObject, mapEntry.imageAddress);
    }

    if (verbosity.isAtLeast(DETAILED)) depth--;

    return mapEntry.imageAddress;
  }

  private static final int OBJECT_HEADER_SIZE = 8;
  private static Hashtable<Object,Integer> traversed = null;
  private static final Integer VISITED = 0;

  /**
   * Traverse an object (and, recursively, any of its fields or elements that
   * are references) in host JDK address space.
   *
   * @param jdkObject object to be traversed
   * @return offset of copied object within image, in bytes
   *         (OBJECT_NOT_PRESENT --> object not copied:
   *            it's not part of bootimage)
   */
  private static int traverseObject(Object jdkObject) throws IllegalAccessException {
      //
      // don't traverse an object twice
      //
      final Object wrapper = jdkObject;
      Object key = new Object() {
        @Override
        public int hashCode() {
          return System.identityHashCode(wrapper);
        }
        @Override
        public boolean equals(Object o) {
          return o != null && getClass() == o.getClass() && hashCode() == o.hashCode();
        }
      };
      Integer sz = traversed.get(key);
      if (sz != null) return sz; // object already traversed
      traversed.put(key, VISITED);

      if (verbosity.isAtLeast(DETAILED)) depth++;

      //
      // fetch object's type information
      //
      Class<?> jdkType = jdkObject.getClass();
      int size = OBJECT_HEADER_SIZE;

      //
      // recursively traverse object
      //
      if (jdkType.isArray()) {
        size += BYTES_IN_INT; // length is int
        int arrayCount       = Array.getLength(jdkObject);
        //
        // traverse array elements
        // recurse on values that are references
        //
        Class<?> jdkElementType = jdkType.getComponentType();
        if (jdkElementType.isPrimitive()) {
          // array element is logical or numeric type
          if (jdkElementType == Boolean.TYPE) {
            size += arrayCount * BYTES_IN_BOOLEAN;
          } else if (jdkElementType == Byte.TYPE) {
            size += arrayCount * BYTES_IN_BYTE;
          } else if (jdkElementType == Character.TYPE) {
            size += arrayCount * BYTES_IN_CHAR;
          } else if (jdkElementType == Short.TYPE) {
            size += arrayCount * BYTES_IN_SHORT;
          } else if (jdkElementType == Integer.TYPE) {
            size += arrayCount * BYTES_IN_INT;
          } else if (jdkElementType == Long.TYPE) {
            size += arrayCount * BYTES_IN_LONG;
          } else if (jdkElementType == Float.TYPE) {
            size += arrayCount * BYTES_IN_FLOAT;
          } else if (jdkElementType == Double.TYPE) {
            size += arrayCount * BYTES_IN_DOUBLE;
          } else
            fail("unexpected array type: " + jdkType);
        } else {
          // array element is reference type
          size += arrayCount * BYTES_IN_ADDRESS;
          Object[] values = (Object []) jdkObject;
          for (int i = 0; i < arrayCount; ++i) {
            if (values[i] != null) {
              if (verbosity.isAtLeast(DETAILED)) traceContext.push(values[i].getClass().getName(),
                                                  jdkType.getName(), i);
              traverseObject(values[i]);
              if (verbosity.isAtLeast(DETAILED)) traceContext.pop();
            }
          }
        }
        if (verbosity.isAtLeast(DETAILED)) {
          if (depth == DEPTH_CUTOFF)
            say(SPACES.substring(0, depth + 1), "TOO DEEP: cutting off");
          else if (depth < DEPTH_CUTOFF) {
            String tab = SPACES.substring(0, depth + 1);
            if (depth == 0 && jtocCount >= 0)
              tab = tab + "jtoc #" + String.valueOf(jtocCount);
            say(tab, "Traversed array  ", jdkType.getName(),
                "  length=", String.valueOf(arrayCount),
                "  total size=", String.valueOf(size),
                (size >= LARGE_ARRAY_SIZE) ? "  large object!!!" : "");
          }
        }
      } else {
        //
        // traverse object fields
        // recurse on values that are references
        //
        for (Class<?> type = jdkType; type != null; type = type.getSuperclass()) {
          Field[] jdkFields = type.getDeclaredFields();
          for (int i = 0, n = jdkFields.length; i < n; ++i) {
            Field  jdkField       = jdkFields[i];
            jdkField.setAccessible(true);
            Class<?>  jdkFieldType   = jdkField.getType();

            if (jdkFieldType.isPrimitive()) {
              // field is logical or numeric type
              if (jdkFieldType == Boolean.TYPE)
                size += BYTES_IN_BOOLEAN;
              else if (jdkFieldType == Byte.TYPE)
                size += BYTES_IN_BYTE;
              else if (jdkFieldType == Character.TYPE)
                size += BYTES_IN_CHAR;
              else if (jdkFieldType == Short.TYPE)
                size += BYTES_IN_SHORT;
              else if (jdkFieldType == Integer.TYPE)
                size += BYTES_IN_INT;
              else if (jdkFieldType == Long.TYPE)
                size += BYTES_IN_LONG;
              else if (jdkFieldType == Float.TYPE)
                size += BYTES_IN_FLOAT;
              else if (jdkFieldType == Double.TYPE)
                size += BYTES_IN_DOUBLE;
              else
                fail("unexpected field type: " + jdkFieldType);
            } else {
              // field is reference type
              size += BYTES_IN_ADDRESS;
              Object value = jdkField.get(jdkObject);
              if (value != null) {
                if (verbosity.isAtLeast(DETAILED)) traceContext.push(value.getClass().getName(),
                                                    jdkType.getName(),
                                                    jdkField.getName());
                traverseObject(value);
                if (verbosity.isAtLeast(DETAILED)) traceContext.pop();
              }
            }
          }
        }
        if (verbosity.isAtLeast(DETAILED)) {
          if (depth == DEPTH_CUTOFF)
            say(SPACES.substring(0, depth + 1), "TOO DEEP: cutting off");
          else if (depth < DEPTH_CUTOFF) {
            String tab = SPACES.substring(0,depth + 1);
            if (depth == 0 && jtocCount >= 0)
              tab = tab + "#" + String.valueOf(jtocCount);
            say(tab, "Traversed object ", jdkType.getName(),
                "   total size=", String.valueOf(size),
                (size >= LARGE_SCALAR_SIZE) ? " large object!!!" : "");
          }
        }
      }

      traversed.put(key, size);
      if (verbosity.isAtLeast(DETAILED)) depth--;
      return size;
    }

  /**
   * Begin recording objects referenced by RVM classes during
   * loading/resolution/instantiation.  These references will be converted
   * to bootimage addresses when those objects are copied into bootimage.
   */
  private static void enableObjectAddressRemapper() {
    Magic.setObjectAddressRemapper(BootImageObjectAddressRemapper.getInstance());
  }

  /**
   * Stop recording objects referenced by RVM classes during
   * loading/resolution/instantiation.
   */
  private static void disableObjectAddressRemapper() {
    Magic.setObjectAddressRemapper(null);

    // Remove bootimage writer's remapper object that was present when JTOC
    // was populated with JDK objects. It's not part of the bootimage and we
    // don't want to see warning messages about it when the bootimage is
    // written.

    RVMMember remapper = Entrypoints.magicObjectRemapperField;
    Statics.setSlotContents(remapper.getOffset(), 0);
  }


  /**
   * Figure out name of static RVM field whose value lives in specified JTOC
   * slot.
   *
   * @param jtocSlot JTOC slot number
   * @return field name
   */
  static RVMField getRvmStaticField(Offset jtocOff) {
    for (int i = FIRST_TYPE_DICTIONARY_INDEX; i < RVMType.numTypes(); ++i) {
      RVMType type = RVMType.getType(i);
      if (type == null) continue;
      if (type.isPrimitiveType() || type.isUnboxedType())
        continue;
      if (!type.isResolved())
        continue;
      for (RVMField rvmField : type.getStaticFields()) {
        if (rvmField.getOffset().EQ(jtocOff))
          return rvmField;
      }
    }
    return null;
  }

}
