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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jikesrvm.Callbacks;
import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.ArchitectureSpecific.LazyCompilationTrampoline;
import org.jikesrvm.ArchitectureSpecific.OutOfLineMachineCode;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeDescriptorParsing;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.jni.FunctionTable;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.mm.mminterface.AlignmentEncoding;
import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.RuntimeTable;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.BootRecord;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.RVMThread;
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
public class BootImageWriter extends BootImageWriterMessages
 implements BootImageWriterConstants {

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
  public static int numThreads = Runtime.getRuntime().availableProcessors()+1;

  /**
   * The boot thread
   */
  private static RVMThread startupThread;

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
   * and value is the corresponding RVMType.
   */
  private static final Hashtable<String,RVMType> bootImageTypes =
    new Hashtable<String,RVMType>(5000);

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
     *  Fields that are the one-to-one match of RVM staticFields
     */
    Field[]  jdkStaticFields;

    /**
     *  RVM type associated with this Field info
     */
    RVMType rvmType;

    /**
     *  JDK type associated with this Field info
     */
    final Class<?> jdkType;

    /**
     * Constructor.
     * @param jdkType the type to associate with the key
     */
    public FieldInfo(Class<?> jdkType, RVMType rvmType) {
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
     * JDK type
     */
    final Class<?> jdkType;

    /**
     * Constructor.
     * @param jdkType the type to associate with the key
     */
    public Key(Class<?> jdkType) { this.jdkType = jdkType; }

    /**
     * Returns a hash code value for the key.
     * @return a hash code value for this key
     */
    @Override
    public int hashCode() { return jdkType.hashCode(); }

    /**
     * Indicates whether some other key is "equal to" this one.
     * @param that the object with which to compare
     * @return true if this key is the same as the that argument;
     *         false otherwise
     */
    @Override
    public boolean equals(Object that) {
      return (that instanceof Key) && jdkType == ((Key)that).jdkType;
    }
  }

  /**
   * Comparator that always says entries are equivalent. For use when
   * comparator defers to another comparator.
   */
  private static final class IdenticalComparator implements Comparator<BootImageMap.Entry> {
    @Override
    public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
      return 0;
    }
  }

  /**
   * Comparator of boot image entries that sorts according to the type
   * reference ID.
   */
  private static final class TypeReferenceComparator implements Comparator<BootImageMap.Entry> {
    @Override
    public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
      TypeReference aRef = TypeReference.findOrCreate(a.jdkObject.getClass());
      TypeReference bRef = TypeReference.findOrCreate(b.jdkObject.getClass());
      return aRef.getId() - bRef.getId();
    }
  }

  /**
   * Comparator of boot image entries that sorts according to the name of the
   * classes.
   */
  private static final class ClassNameComparator implements Comparator<BootImageMap.Entry> {
    @Override
    public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
      return -a.jdkObject.getClass().toString().compareTo(b.jdkObject.getClass().toString());
    }
  }

  /**
   * Comparator of boot image entries that sorts according to the size of
   * the objects.
   */
  private static final class ObjectSizeComparator implements Comparator<BootImageMap.Entry> {
    private final Comparator<BootImageMap.Entry> identicalSizeComparator;
    ObjectSizeComparator(Comparator<BootImageMap.Entry> identicalSizeComparator) {
      this.identicalSizeComparator = identicalSizeComparator;
    }
    @Override
    public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
      TypeReference aRef = TypeReference.findOrCreate(a.jdkObject.getClass());
      TypeReference bRef = TypeReference.findOrCreate(b.jdkObject.getClass());
      if ((!aRef.isResolved() && !aRef.isResolved()) || (aRef == bRef)) {
        return identicalSizeComparator.compare(a, b);
      } else if (!aRef.isResolved()) {
        return -1;
      } else if (!bRef.isResolved()) {
        return 1;
      } else {
        int aSize = getSize(aRef.peekType(), a.jdkObject);
        int bSize = getSize(bRef.peekType(), b.jdkObject);
        if (aSize == bSize) {
          return identicalSizeComparator.compare(a, b);
        } else {
          return aSize - bSize;
        }
      }
    }
  }

  /**
   * Comparator of boot image entries that sorts according to the number of
   * references within the objects.
   */
  private static final class NumberOfReferencesComparator implements Comparator<BootImageMap.Entry> {
    private final Comparator<BootImageMap.Entry> identicalSizeComparator;
    NumberOfReferencesComparator(Comparator<BootImageMap.Entry> identicalSizeComparator) {
      this.identicalSizeComparator = identicalSizeComparator;
    }
    @Override
    public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
      TypeReference aRef = TypeReference.findOrCreate(a.jdkObject.getClass());
      TypeReference bRef = TypeReference.findOrCreate(b.jdkObject.getClass());
      if ((!aRef.isResolved() && !aRef.isResolved()) || (aRef == bRef)) {
        return identicalSizeComparator.compare(a, b);
      } else if (!aRef.isResolved()) {
        return 1;
      } else if (!bRef.isResolved()) {
        return -1;
      } else {
        int aSize = getNumberOfReferences(aRef.peekType(), a.jdkObject);
        int bSize = getNumberOfReferences(bRef.peekType(), b.jdkObject);
        if (aSize == bSize) {
          return identicalSizeComparator.compare(a, b);
        } else {
          return bSize - aSize;
        }
      }
    }
  }

  /**
   * Comparator of boot image entries that sorts according to the number of
   * non-final references within the objects.
   */
  private static final class NumberOfNonFinalReferencesComparator implements Comparator<BootImageMap.Entry> {
    private final Comparator<BootImageMap.Entry> identicalSizeComparator;
    NumberOfNonFinalReferencesComparator(Comparator<BootImageMap.Entry> identicalSizeComparator) {
      this.identicalSizeComparator = identicalSizeComparator;
    }
    @Override
    public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
      TypeReference aRef = TypeReference.findOrCreate(a.jdkObject.getClass());
      TypeReference bRef = TypeReference.findOrCreate(b.jdkObject.getClass());
      if ((!aRef.isResolved() && !aRef.isResolved()) || (aRef == bRef)) {
        return identicalSizeComparator.compare(a, b);
      } else if (!aRef.isResolved()) {
        return 1;
      } else if (!bRef.isResolved()) {
        return -1;
      } else {
        int aSize = getNumberOfNonFinalReferences(aRef.peekType(), a.jdkObject);
        int bSize = getNumberOfNonFinalReferences(bRef.peekType(), b.jdkObject);
        if (aSize == bSize) {
          return identicalSizeComparator.compare(a, b);
        } else {
          return bSize - aSize;
        }
      }
    }
  }

  /**
   * Comparator of boot image entries that sorts according to the density of
   * non-final references within the objects.
   */
  private static final class NonFinalReferenceDensityComparator implements Comparator<BootImageMap.Entry> {
    private final Comparator<BootImageMap.Entry> identicalSizeComparator;
    NonFinalReferenceDensityComparator(Comparator<BootImageMap.Entry> identicalSizeComparator) {
      this.identicalSizeComparator = identicalSizeComparator;
    }
    @Override
    public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
      TypeReference aRef = TypeReference.findOrCreate(a.jdkObject.getClass());
      TypeReference bRef = TypeReference.findOrCreate(b.jdkObject.getClass());
      if ((!aRef.isResolved() && !aRef.isResolved()) || (aRef == bRef)) {
        return identicalSizeComparator.compare(a, b);
      } else if (!aRef.isResolved()) {
        return 1;
      } else if (!bRef.isResolved()) {
        return -1;
      } else {
        double aSize = (double)getNumberOfNonFinalReferences(aRef.peekType(), a.jdkObject) / (double)getSize(aRef.peekType(), a.jdkObject);
        double bSize = (double)getNumberOfNonFinalReferences(bRef.peekType(), b.jdkObject) / (double)getSize(bRef.peekType(), b.jdkObject);
        int result = Double.compare(aSize, bSize);
        if (result == 0) {
          return identicalSizeComparator.compare(a, b);
        } else {
          return -result;
        }
      }
    }
  }

  /**
   * Comparator of boot image entries that sorts according to the density of
   * references within the objects.
   */
  private static final class ReferenceDensityComparator implements Comparator<BootImageMap.Entry> {
    private final Comparator<BootImageMap.Entry> identicalSizeComparator;
    ReferenceDensityComparator(Comparator<BootImageMap.Entry> identicalSizeComparator) {
      this.identicalSizeComparator = identicalSizeComparator;
    }
    @Override
    public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
      TypeReference aRef = TypeReference.findOrCreate(a.jdkObject.getClass());
      TypeReference bRef = TypeReference.findOrCreate(b.jdkObject.getClass());
      if ((!aRef.isResolved() && !aRef.isResolved()) || (aRef == bRef)) {
        return identicalSizeComparator.compare(a, b);
      } else if (!aRef.isResolved()) {
        return 1;
      } else if (!bRef.isResolved()) {
        return -1;
      } else {
        double aSize = (double)getNumberOfReferences(aRef.peekType(), a.jdkObject) / (double)getSize(aRef.peekType(), a.jdkObject);
        double bSize = (double)getNumberOfReferences(bRef.peekType(), b.jdkObject) / (double)getSize(bRef.peekType(), b.jdkObject);
        int result = Double.compare(aSize, bSize);
        if (result == 0) {
          return identicalSizeComparator.compare(a, b);
        } else {
          return -result;
        }
      }
    }
  }

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

    /** Report a field that is an instance field in the host JDK but a static
       field in ours.  */
    public void traceFieldNotStaticInHostJdk() {
      traceNulledWord(": field not static in host jdk");
    }

    /** Report a field that is a different type  in the host JDK.  */
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
    @Override
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
   * Junk released from Statics when instantiation finishes and may
   * be needed to generate the boot image report.
   */
  private static Object staticsJunk;

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

        if (bootImageTypeNamesFile != null)
          fail("argument syntax error: We've already read in the bootImageTypeNames from" +
               bootImageTypeNamesFile + "; just got another -n argument" +
               " telling us to read them from " + args[i]);
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
        verbose++;
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
      // log
      if (args[i].equals("-log")) {
        if (++i >= args.length)
          fail("argument syntax error: Got a -log flag without a following argument for the log file");
        logFile = args[i];
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
      bootImage = new BootImage(littleEndian, verbose >= 1, bootImageCodeName, bootImageDataName, bootImageRMapName);
    } catch (IOException e) {
      fail("unable to write bootImage: "+e);
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
      fail("unable to read the type names from " + bootImageTypeNamesFile + ": " +e);
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
    // If they are, they'll be assigned an objectId of "-1" (see Magic)
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
    BootRecord bootRecord = BootRecord.the_boot_record;
    Address bootRecordImageAddress = Address.zero();
    try {
      // copy just the boot record
      bootRecordImageAddress = copyToBootImage(bootRecord, true, Address.max(), null, false, AlignmentEncoding.ALIGN_CODE_NONE);
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
      fail("can't copy jtoc: "+e);
    }
    Address jtocPtr = jtocImageAddress.plus(Statics.middleOfTable << LOG_BYTES_IN_INT);
    if (jtocPtr.NE(bootRecord.tocRegister))
      fail("mismatch in JTOC placement "+VM.addressAsHexString(jtocPtr)+" != "+ VM.addressAsHexString(bootRecord.tocRegister));

    //
    // Now, copy all objects reachable from jtoc, replacing each object id
    // that was generated by object address remapper with the actual
    // bootimage address of that object.
    //
    if (verbose >= 1) say("copying statics");
    try {
      int refSlotSize = Statics.getReferenceSlotSize();
      for (int i = Statics.middleOfTable+refSlotSize, n = Statics.getHighestInUseSlot();
           i <= n;
           i+= refSlotSize) {
        if(!Statics.isReference(i)) {
          throw new Error("Static " + i + " of " + n + " isn't reference");
        }
        jtocCount = i; // for diagnostic

        Offset jtocOff = Statics.slotAsOffset(i);
        int objCookie;
        if (VM.BuildFor32Addr)
          objCookie = Statics.getSlotContentsAsInt(jtocOff);
        else
          objCookie = (int) Statics.getSlotContentsAsLong(jtocOff);
        // if (verbose >= 3)
        // say("       jtoc[", String.valueOf(i), "] = ", String.valueOf(objCookie));
        Object jdkObject = BootImageMap.getObject(objCookie);
        if (jdkObject == null)
          continue;

        if (verbose >= 2) traceContext.push(jdkObject.getClass().getName(),
                                            getRvmStaticField(jtocOff) + "");
        copyReferenceFieldToBootImage(jtocPtr.plus(jtocOff), jdkObject, Statics.getSlotsAsIntArray(), false, false, null, null);
        if (verbose >= 2) traceContext.pop();
      }
      // Copy entries that are in the pending queue
      processPendingEntries();
      // Find and copy unallocated entries
      for (int i=0; i < BootImageMap.objectIdToEntry.size(); i++) {
        BootImageMap.Entry mapEntry = BootImageMap.objectIdToEntry.get(i);
        if (mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          mapEntry.imageAddress = copyToBootImage(mapEntry.jdkObject, false, Address.max(), null, false, AlignmentEncoding.ALIGN_CODE_NONE);
          fixupLinkAddresses(mapEntry);
        }
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

    byte[] startupStack = startupThread.getStack();
    CodeArray startupCode  = Entrypoints.bootMethod.getCurrentEntryCodeArray();

    bootRecord.tiRegister  = startupThread.getLockingId();
    bootRecord.spRegister  = BootImageMap.getImageAddress(startupStack, true).plus(startupStack.length);
    bootRecord.ipRegister  = BootImageMap.getImageAddress(startupCode.getBacking(), true);

    bootRecord.bootThreadOffset = Entrypoints.bootThreadField.getOffset();

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
      fail("unable to update boot record: "+e);
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
    if(demographics) {
      DemographicInformation info = demographicData.get(type);
      if(info != null) {
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
    VM.sysWriteln("\nBoot image space report:");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    VM.sysWriteField(60, "TOTAL");
    VM.sysWriteField(15, totalCount);
    VM.sysWriteField(15, totalBytes);
    VM.sysWriteln();

    VM.sysWriteln("\nCompiled methods space report:");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    CompiledMethods.spaceReport();

    VM.sysWriteln("------------------------------------------------------------------------------------------");
    VM.sysWriteln("\nBoot image space usage by types:");
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
        typeNames.addElement(typeName);
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
        RVMType type;
        try {
          TypeReference tRef = TypeReference.findOrCreate(typeName);
          type = tRef.resolve();
        } catch (NoClassDefFoundError ncdf) {
          ncdf.printStackTrace(System.out);
          fail(bootImageTypeNamesFile +
               " contains a class named \"" +typeName +
               "\", but we can't find a class with that name: "+ ncdf);
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
      for (RVMType type : bootImageTypes.values()) {
        if (verbose >= 2) say("resolving " + type);
        // The resolution is supposed to be cached already.
        type.resolve();
      }

      //
      // Now that all types are resolved, do some additional fixup before we do any compilation
      //
      for (RVMType type : bootImageTypes.values()) {
        type.allBootImageTypesResolved();
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
      BootRecord bootRecord = BootRecord.the_boot_record;
      RVMClass rvmBRType = getRvmType(bootRecord.getClass()).asClass();
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
      OutOfLineMachineCode.init();

      //
      // Compile methods and populate jtoc with literals, TIBs, and machine code.
      //
      if (profile) startTime = System.currentTimeMillis();
      if (verbose >= 1) say("instantiating");

      if (verbose >= 1) say(" compiling with " + numThreads + " threads");
      ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
      for (RVMType type: bootImageTypes.values()) {
        threadPool.execute(new BootImageWorker(type));
      }
      threadPool.shutdown();
      try {
        while(!threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)) {
          say("Compilation really shouldn't take this long");
        }
      } catch (InterruptedException e){
        throw new Error("Build interrupted", e);
      }
      if (BootImageWorker.instantiationFailed) {
        throw new Error("Error during instantiaion");
      }

      if (profile) {
        stopTime = System.currentTimeMillis();
        System.out.println("PROF: \tinstantiating types "+(stopTime-startTime)+" ms");
      }

      // Free up unnecessary Statics data structures
      staticsJunk = Statics.bootImageInstantiationFinished();

      // Do the portion of JNIEnvironment initialization that can be done
      // at bootimage writing time.
      FunctionTable functionTable = BuildJNIFunctionTable.buildTable();
      JNIEnvironment.initFunctionTable(functionTable);

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
      for (RVMType rvmType : bootImageTypes.values()) {
        FieldInfo fieldInfo;
        if (!rvmType.isClassType())
          continue; // arrays and primitives have no static or instance fields

        Class<?> jdkType = getJdkType(rvmType);
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
          for (Class<?> cls = jdkType.getSuperclass(); cls != null; cls = cls.getSuperclass()) {
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
        RVMType rvmType = fieldInfo.rvmType;
        if (rvmType == null) {
          if (verbose >= 1) say("bootImageTypeField entry has no rvmType:"+fieldInfo.jdkType);
          continue;
        }
        Class<?> jdkType   = fieldInfo.jdkType;
        if (verbose >= 1) say("building static and instance fieldinfo for " + rvmType);

        // First the static fields
        //
        RVMField[] rvmFields = rvmType.getStaticFields();
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
          // RVMType of the field's declaring class.
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
      if (verbose >= 1) say("populating jtoc with static fields");
      if (profile) startTime = System.currentTimeMillis();
      for (RVMType rvmType : bootImageTypes.values()) {
        if (verbose >= 1) say("  jtoc for ", rvmType.toString());
        if (!rvmType.isClassType())
          continue; // arrays and primitives have no static fields

        Class<?> jdkType = getJdkType(rvmType);
        if (jdkType == null && verbose >= 1) {
          say("host has no class \"" + rvmType + "\"");
        }

        RVMField[] rvmFields = rvmType.getStaticFields();
        for (int j = 0; j < rvmFields.length; ++j) {
          RVMField rvmField     = rvmFields[j];
          TypeReference rvmFieldType = rvmField.getType();
          Offset rvmFieldOffset = rvmField.getOffset();
          String   rvmFieldName = rvmField.getName().toString();
          Field    jdkFieldAcc  = null;

          if (jdkType!=null &&
              jdkType.equals(java.util.concurrent.locks.AbstractQueuedSynchronizer.class)) {
            RVMClass c=(RVMClass)rvmType;
            if (rvmFieldName.equals("stateOffset")) {
              Statics.setSlotContents(
                rvmFieldOffset,
                c.findDeclaredField(Atom.findOrCreateAsciiAtom("state")).getOffset().toLong());
              continue;
            } else if (rvmFieldName.equals("headOffset")) {
              Statics.setSlotContents(
                rvmFieldOffset,
                c.findDeclaredField(Atom.findOrCreateAsciiAtom("head")).getOffset().toLong());
              continue;
            } else if (rvmFieldName.equals("tailOffset")) {
              Statics.setSlotContents(
                rvmFieldOffset,
                c.findDeclaredField(Atom.findOrCreateAsciiAtom("tail")).getOffset().toLong());
              continue;
            } else if (rvmFieldName.equals("waitStatusOffset")) {
              try {
              Statics.setSlotContents(
                rvmFieldOffset,
                ((RVMClass)getRvmType(Class.forName("java.util.concurrent.locks.AbstractQueuedSynchronizer$Node"))).findDeclaredField(Atom.findOrCreateAsciiAtom("waitStatus")).getOffset().toLong());
              } catch (ClassNotFoundException e) {
                throw new Error(e);
              }
              continue;
            }
          } else if (jdkType!=null &&
                     jdkType.equals(java.util.concurrent.locks.LockSupport.class)) {
            RVMClass c=(RVMClass)rvmType;
            if (rvmFieldName.equals("parkBlockerOffset")) {
              Statics.setSlotContents(
                rvmFieldOffset,
                ((RVMClass)getRvmType(java.lang.Thread.class)).findDeclaredField(Atom.findOrCreateAsciiAtom("parkBlocker")).getOffset().toLong());
              continue;
            }
          }

          if (jdkType != null)
            jdkFieldAcc = getJdkFieldAccessor(jdkType, j, STATIC_FIELD);

          if (jdkFieldAcc == null) {
            // we failed to get a reflective field accessors
            if (jdkType != null) {
              // we know the type - probably a private field of a java.lang class
              if(!copyKnownStaticField(jdkType,
                                       rvmFieldName,
                                       rvmFieldType,
                                       rvmFieldOffset)) {
                // we didn't know the field so nullify
                if (verbose >= 2) {
                  traceContext.push(rvmFieldType.toString(),
                                    jdkType.getName(), rvmFieldName);
                  traceContext.traceFieldNotInHostJdk();
                  traceContext.pop();
                }
                Statics.setSlotContents(rvmFieldOffset, 0);
                if (!VM.runningTool)
                  bootImage.countNulledReference();
                invalidEntrys.add(jdkType.getName());
              }
            } else {
              // no accessor and we don't know the type so nullify
              if (verbose >= 2) {
                traceContext.push(rvmFieldType.toString(),
                                  rvmFieldType.toString(), rvmFieldName);
                traceContext.traceFieldNotInHostJdk();
                traceContext.pop();
              }
              Statics.setSlotContents(rvmFieldOffset, 0);
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
            Statics.setSlotContents(rvmFieldOffset, 0);
            if (!VM.runningTool)
              bootImage.countNulledReference();
            invalidEntrys.add(jdkType.getName());
            continue;
          }

          if(!equalTypes(jdkFieldAcc.getType().getName(), rvmFieldType)) {
            if (verbose >= 2) traceContext.push(rvmFieldType.toString(),
                                                jdkType.getName(), rvmFieldName);
            if (verbose >= 2) traceContext.traceFieldDifferentTypeInHostJdk();
            if (verbose >= 2) traceContext.pop();
            Statics.setSlotContents(rvmFieldOffset, 0);
            if (!VM.runningTool)
              bootImage.countNulledReference();
            invalidEntrys.add(jdkType.getName());
            continue;
          }

          if (verbose >= 2)
            say("    populating jtoc slot ", String.valueOf(Statics.offsetAsSlot(rvmFieldOffset)),
                " with ", rvmField.toString());
          if (rvmFieldType.isPrimitiveType()) {
            // field is logical or numeric type
            if (rvmFieldType.isBooleanType()) {
              Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getBoolean(null) ? 1 : 0);
            } else if (rvmFieldType.isByteType()) {
              Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getByte(null));
            } else if (rvmFieldType.isCharType()) {
              Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getChar(null));
            } else if (rvmFieldType.isShortType()) {
              Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getShort(null));
            } else if (rvmFieldType.isIntType()) {
                Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getInt(null));
            } else if (rvmFieldType.isLongType()) {
              // note: Endian issues handled in setSlotContents.
              Statics.setSlotContents(rvmFieldOffset,
                                         jdkFieldAcc.getLong(null));
            } else if (rvmFieldType.isFloatType()) {
              float f = jdkFieldAcc.getFloat(null);
              Statics.setSlotContents(rvmFieldOffset,
                                         Float.floatToIntBits(f));
            } else if (rvmFieldType.isDoubleType()) {
              double d = jdkFieldAcc.getDouble(null);
              // note: Endian issues handled in setSlotContents.
              Statics.setSlotContents(rvmFieldOffset,
                                         Double.doubleToLongBits(d));
            } else if (rvmFieldType.equals(TypeReference.Address) ||
                       rvmFieldType.equals(TypeReference.Word) ||
                       rvmFieldType.equals(TypeReference.Extent) ||
                       rvmFieldType.equals(TypeReference.Offset)){
              Object o = jdkFieldAcc.get(null);
              String msg = " static field " + rvmField.toString();
              boolean warn = rvmFieldType.equals(TypeReference.Address);
              Statics.setSlotContents(rvmFieldOffset, getWordValue(o, msg, warn));
            } else {
              fail("unexpected primitive field type: " + rvmFieldType);
            }
          } else {
            // field is reference type
            final Object o = jdkFieldAcc.get(null);
            if (verbose >= 3)
              say("       setting with ", VM.addressAsHexString(Magic.objectAsAddress(o)));
            Statics.setSlotContents(rvmFieldOffset, o);
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

  private static boolean equalTypes(final String name, final TypeReference rvmFieldType) {
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

  private static final int LARGE_ARRAY_SIZE = 16*1024;
  private static final int LARGE_SCALAR_SIZE = 1024;
  private static int depth = -1;
  private static int jtocCount = -1;
  private static final String SPACES = "                                                                                                                                                                                                                                                                                                                                ";

  private static void check(Word value, String msg) {
    Word low = ObjectModel.maximumObjectRef(Address.zero()).toWord();  // yes, max
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
  private static void copyReferenceFieldToBootImage(Address fieldLocation, Object referencedObject,
      Object parentObject, boolean objField, boolean root, String rvmFieldName,
      TypeReference rvmFieldType) throws IllegalAccessException {
    if (referencedObject == null) {
      bootImage.setNullAddressWord(fieldLocation, objField, root, true);
    } else {
      BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(referencedObject);
      if (mapEntry.imageAddress.EQ(OBJECT_NOT_PRESENT)) {
        if (rvmFieldName == null || !copyKnownInstanceField(parentObject, rvmFieldName, rvmFieldType, fieldLocation)) {
          // object not part of bootimage: install null reference
          if (verbose >= 2) traceContext.traceObjectNotInBootImage();
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
          if (verbose >= 2) traceContext.traceObjectNotInBootImage();
          if (!copyKnownInstanceField(parentObject, rvmFieldName, rvmFieldType, fieldLocation)) {
            // object not part of bootimage: install null reference
            if (verbose >= 2) traceContext.traceObjectNotInBootImage();
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
    while(info != null) {
      if (mapEntry.imageAddress.EQ(OBJECT_NOT_PRESENT)) {
        if (info.rvmFieldName == null || !copyKnownInstanceField(info.parent, info.rvmFieldName, info.rvmFieldType, info.addressToFixup)) {
          // object not part of bootimage: install null reference
          if (verbose >= 2) traceContext.traceObjectNotInBootImage();
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
  private static Address copyToBootImage(Object jdkObject, boolean allocOnly,
      Address overwriteAddress, Object parentObject, boolean untraced, int alignCode) throws IllegalAccessException
  {
    try {
      // Return object if it is already copied and not being overwritten
      BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
      if ((!mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) && overwriteAddress.isMax()) {
        return mapEntry.imageAddress;
      }

      if (verbose >= 2) depth++;

      // fetch object's type information
      Class<?>   jdkType = jdkObject.getClass();
      RVMType rvmType = getRvmType(jdkType);
      if (rvmType == null) {
        if (verbose >= 2) traverseObject(jdkObject);
        if (verbose >= 2) depth--;
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
          if (verbose >= 2) traceContext.push("", jdkObject.getClass().getName(), "tib");
          Address tibImageAddress = copyToBootImage(rvmType.getTypeInformationBlock(), allocOnly, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (verbose >= 2) traceContext.pop();
          if (tibImageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            fail("can't copy tib for " + jdkObject);
          }
          ObjectModel.setTIB(bootImage, mapEntry.imageAddress, tibImageAddress, rvmType);
        }
      } else if (jdkObject instanceof TIB) {
        Object backing = ((RuntimeTable<?>)jdkObject).getBacking();

        int alignCodeValue = ((TIB)jdkObject).getAlignData();
        if (verbose > 1) say("Encoding value "+ alignCodeValue+" into tib");

        /* Copy the backing array, and then replace its TIB */
        mapEntry.imageAddress = copyToBootImage(backing, allocOnly, overwriteAddress, jdkObject, rvmType.getTypeRef().isRuntimeTable(), alignCodeValue);

        if (verbose > 1) say(String.format("TIB address = %x, encoded value = %d, requested = %d%n",
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
      }  else if (rvmType == RVMType.CodeArrayType) {
        // Handle the code array that is represented as either byte or int arrays
        if (verbose >= 2) depth--;
        Object backing = ((CodeArray)jdkObject).getBacking();
        mapEntry.imageAddress = copyMagicArrayToBootImage(backing, rvmType.asArray(), allocOnly, overwriteAddress, parentObject);
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
      if (verbose >= 2) depth--;
      return mapEntry.imageAddress;
    } catch (Error e) {
      e = new Error(e.getMessage()+ "\nwhile copying " +
          jdkObject + (jdkObject != null ? ":"+jdkObject.getClass():"") + " from " +
          parentObject + (parentObject != null ? ":"+parentObject.getClass():""),
          e.getCause() != null? e.getCause() : e);
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
    if (verbose >= 2) {
      depth--;
      traceContext.push("", jdkObject.getClass().getName(), "tib");
    }
    Address tibImageAddress = copyToBootImage(rvmType.getTypeInformationBlock(), false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
    if (verbose >= 2) {
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

    // copy object fields from host jdk address space into image
    // recurse on values that are references
    RVMField[] rvmFields = rvmScalarType.getInstanceFields();
    for (int i = 0; i < rvmFields.length; ++i) {
      RVMField rvmField       = rvmFields[i];
      TypeReference rvmFieldType   = rvmField.getType();
      Address rvmFieldAddress = scalarImageAddress.plus(rvmField.getOffset());
      String  rvmFieldName    = rvmField.getName().toString();
      Field   jdkFieldAcc     = getJdkFieldAccessor(jdkType, i, INSTANCE_FIELD);

      boolean untracedField = rvmField.isUntraced() || untraced;

      if (jdkFieldAcc == null) {
        // Field not found via reflection
        if (!copyKnownInstanceField(jdkObject, rvmFieldName, rvmFieldType, rvmFieldAddress)) {
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
          } else {
            bootImage.setNullAddressWord(rvmFieldAddress, !untracedField, !untracedField, false);
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
            bootImage.setFullWord(rvmFieldAddress, jdkFieldAcc.getInt(jdkObject));
          } catch (IllegalArgumentException ex) {
            // TODO: Harmony - clean this up
            if (jdkObject instanceof java.util.WeakHashMap && rvmFieldName.equals("loadFactor")) {
              // the field load factor field in Sun/Classpath is a float but
              // in Harmony it has been "optimized" to an int
              bootImage.setFullWord(rvmFieldAddress, 7500);
            } else if (jdkObject instanceof java.lang.ref.ReferenceQueue && rvmFieldName.equals("head")) {
              // Conflicting types between Harmony and Sun
              bootImage.setFullWord(rvmFieldAddress, 0);
            } else {
              System.out.println("type " + rvmScalarType + ", field " + rvmField);
              throw ex;
            }
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
        } else if (rvmFieldType.equals(TypeReference.Address) ||
            rvmFieldType.equals(TypeReference.Word) ||
            rvmFieldType.equals(TypeReference.Extent) ||
            rvmFieldType.equals(TypeReference.Offset)) {
          Object o = jdkFieldAcc.get(jdkObject);
          String msg = " instance field " + rvmField.toString();
          boolean warn = rvmFieldType.equals(TypeReference.Address);
          bootImage.setAddressWord(rvmFieldAddress, getWordValue(o, msg, warn), false, false);
        } else {
          fail("unexpected primitive field type: " + rvmFieldType);
        }
      } else {
        // field is reference type
        Object value = jdkFieldAcc.get(jdkObject);
        if (!allocOnly) {
          Class<?> jdkClass = jdkFieldAcc.getDeclaringClass();
          if (verbose >= 2) traceContext.push(value.getClass().getName(),
              jdkClass.getName(),
              jdkFieldAcc.getName());
          copyReferenceFieldToBootImage(rvmFieldAddress, value, jdkObject,
              !untracedField, !(untracedField || rvmField.isFinal()), rvmFieldName, rvmFieldType);
        }
      }
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
        for (int i = 0; i<arrayCount; ++i) {
          if (values[i] != null) {
            if (verbose >= 2) traceContext.push(values[i].getClass().getName(), jdkClass.getName(), i);
            if (isTIB && values[i] instanceof Word) {
              bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), (Word)values[i], false, false);
            } else if (isTIB && values[i] == LazyCompilationTrampoline.instructions) {
              Address codeAddress = arrayImageAddress.plus(((TIB)parentObject).lazyMethodInvokerTrampolineIndex() << LOG_BYTES_IN_ADDRESS);
              bootImage.setAddressWord(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), codeAddress.toWord(), false, false);
            } else {
              copyReferenceFieldToBootImage(arrayImageAddress.plus(i << LOG_BYTES_IN_ADDRESS), values[i],
                  jdkObject, !untraced, !untraced, null, null);
            }
            if (verbose >= 2) traceContext.pop();
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

    if (verbose >= 2) depth++;

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
      for (int i=0; i<arrayCount; i++) {
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
  private static boolean copyKnownStaticField(Class<?> jdkType, String rvmFieldName,
                                              TypeReference rvmFieldType,
                                              Offset rvmFieldOffset) {
    if (classLibrary == "harmony") {
      if (jdkType.equals(java.lang.Number.class)) {
        throw new Error("Unknown field in java.lang.Number " + rvmFieldName + " " + rvmFieldType);
      } else if (jdkType.equals(java.lang.Boolean.class)) {
        throw new Error("Unknown field in java.lang.Boolean "+ rvmFieldName + " " + rvmFieldType);
      } else if (jdkType.equals(java.lang.Byte.class)) {
        if (rvmFieldName.equals("CACHE") && rvmFieldType.isArrayType()) {
          Statics.setSlotContents(rvmFieldOffset, new Byte[256]);
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Byte " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Double.class)) {
        throw new Error("Unknown field in java.lang.Double " + rvmFieldName + " " + rvmFieldType);
      } else if (jdkType.equals(java.lang.Float.class)) {
        throw new Error("Unknown field in java.lang.Float " + rvmFieldName + " " + rvmFieldType);
      } else if (jdkType.equals(java.lang.Integer.class)) {
        if (rvmFieldName.equals("decimalScale") && rvmFieldType.isArrayType()) {
          int[] java_lang_Integer_decimalScale = new int[] { 1000000000, 100000000, 10000000, 1000000, 100000, 10000, 1000, 100, 10, 1 };
          Statics.setSlotContents(rvmFieldOffset, java_lang_Integer_decimalScale);
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Integer " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Long.class)) {
          throw new Error("Unknown field in java.lang.Long " + rvmFieldName + " " + rvmFieldType);
      } else if (jdkType.equals(java.lang.Short.class)) {
          throw new Error("Unknown field in java.lang.Short " + rvmFieldName + " " + rvmFieldType);
      } else if (jdkType.equals(java.util.HashMap.class)) {
        if (rvmFieldName.equals("DEFAULT_SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 16);
          return true;
        } else {
          throw new Error("Unknown field in java.util.HashMap " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.util.AbstractMap.class)) {
        throw new Error("Unknown field in java.util.AbstractMap " + rvmFieldName + " " + rvmFieldType);
      } else if (jdkType.equals(java.lang.ref.ReferenceQueue.class)) {
        if (rvmFieldName.equals("DEFAULT_QUEUE_SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 128);
          return true;
        } else {
          throw new Error("Unknown field in java.lang.ref.ReferenceQueue " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Throwable.class)) {
        if (rvmFieldName.equals("zeroLengthStackTrace") && rvmFieldType.isArrayType()) {
          Statics.setSlotContents(rvmFieldOffset, new StackTraceElement[0]);
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Throwable " + rvmFieldName + " " + rvmFieldType);
        }
      } else {
        return false;
      }
    } else if (classLibrary == "classpath") {
      if (jdkType.equals(java.lang.Number.class)) {
        if (rvmFieldName.equals("digits") && rvmFieldType.isArrayType()) {
          char[] java_lang_Number_digits = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
            'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
            'u', 'v', 'w', 'x', 'y', 'z'
          };
          Statics.setSlotContents(rvmFieldOffset, java_lang_Number_digits);
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Number " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Boolean.class)) {
        throw new Error("Unknown field in java.lang.Boolean "+ rvmFieldName + " " + rvmFieldType);
      } else if (jdkType.equals(java.lang.Byte.class)) {
        if (rvmFieldName.equals("byteCache") && rvmFieldType.isArrayType()) {
          Byte[] java_lang_Byte_byteCache = new Byte[256];
          // Populate table
          for(int i=-128; i < 128; i++) {
            Byte value = (byte) i;
            BootImageMap.findOrCreateEntry(value);
            java_lang_Byte_byteCache[128+i] = value;
          }
          Statics.setSlotContents(rvmFieldOffset, java_lang_Byte_byteCache);
          return true;
        } else if (rvmFieldName.equals("MIN_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, -128);
          return true;
        } else if (rvmFieldName.equals("MAX_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 127);
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()){
          Statics.setSlotContents(rvmFieldOffset, 8); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Byte " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Double.class)) {
        if (rvmFieldName.equals("ZERO")) {
          Statics.setSlotContents(rvmFieldOffset, Double.valueOf(0.0));
          return true;
        } else if (rvmFieldName.equals("ONE")) {
          Statics.setSlotContents(rvmFieldOffset, Double.valueOf(1.0));
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 64); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Double " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Float.class)) {
        if (rvmFieldName.equals("ZERO")) {
          Statics.setSlotContents(rvmFieldOffset, Float.valueOf(0.0f));
          return true;
        } else if (rvmFieldName.equals("ONE")) {
          Statics.setSlotContents(rvmFieldOffset, Float.valueOf(1.0f));
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 32); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Float " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Integer.class)) {
        if (rvmFieldName.equals("intCache") && rvmFieldType.isArrayType()) {
          Integer[] java_lang_Integer_intCache = new Integer[256];
          // Populate table
          for(int i=-128; i < 128; i++) {
            Integer value = i;
            java_lang_Integer_intCache[128+i] = value;
          }
          Statics.setSlotContents(rvmFieldOffset, java_lang_Integer_intCache);
          return true;
        } else if (rvmFieldName.equals("MIN_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, -128);
          return true;
        } else if (rvmFieldName.equals("MAX_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 127);
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 32); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Integer " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Long.class)) {
        if (rvmFieldName.equals("longCache") && rvmFieldType.isArrayType()) {
          Long[] java_lang_Long_longCache = new Long[256];
          // Populate table
          for(int i=-128; i < 128; i++) {
            Long value = (long)i;
            BootImageMap.findOrCreateEntry(value);
            java_lang_Long_longCache[128+i] = value;
          }
          Statics.setSlotContents(rvmFieldOffset, java_lang_Long_longCache);
          return true;
        } else if (rvmFieldName.equals("MIN_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, -128);
          return true;
        } else if (rvmFieldName.equals("MAX_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 127);
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 64); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Long " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Short.class)) {
        if (rvmFieldName.equals("shortCache") && rvmFieldType.isArrayType()) {
          Short[] java_lang_Short_shortCache = new Short[256];
          // Populate table
          for(short i=-128; i < 128; i++) {
            Short value = i;
            BootImageMap.findOrCreateEntry(value);
            java_lang_Short_shortCache[128+i] = value;
          }
          Statics.setSlotContents(rvmFieldOffset, java_lang_Short_shortCache);
          return true;
        } else if (rvmFieldName.equals("MIN_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, -128);
          return true;
        } else if (rvmFieldName.equals("MAX_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 127);
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 16); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Short " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.util.HashMap.class)) {
        if (rvmFieldName.equals("DEFAULT_CAPACITY") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 11);
          return true;
        } else if (rvmFieldName.equals("DEFAULT_LOAD_FACTOR") && rvmFieldType.isFloatType()) {
          Statics.setSlotContents(rvmFieldOffset, Float.floatToIntBits(0.75f));
          return true;
        } else {
          throw new Error("Unknown field in java.util.HashMap " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.util.AbstractMap.class)) {
        if (rvmFieldName.equals("KEYS") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 0);
          return true;
        } else if (rvmFieldName.equals("VALUES") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 1);
          return true;
        } else if (rvmFieldName.equals("ENTRIES") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 2);
          return true;
        } else {
          throw new Error("Unknown field in java.util.AbstractMap " + rvmFieldName + " " + rvmFieldType);
        }
      } else {
        return false;
      }
    } else {
      throw new Error("Unknown class library: \"" + classLibrary + "\"");
    }
  }

  /**
   * If we can't find a field via reflection we may still determine
   * and copy a value because we know the internals of Classpath.
   * @param jdkObject the object containing the field
   * @param rvmFieldName the name of the field
   * @param rvmFieldType the type reference of the field
   * @param rvmFieldAddress the address that the field is being written to
   */
  private static boolean copyKnownInstanceField(Object jdkObject, String rvmFieldName, TypeReference rvmFieldType, Address rvmFieldAddress)
    throws IllegalAccessException {

    // Class library independent objects
    if (jdkObject instanceof java.lang.Class)   {
      Object value = null;
      String fieldName = null;
      boolean fieldIsFinal = false;
      if(rvmFieldName.equals("type")) {
        // Looks as though we're trying to write the type for Class,
        // lets go over the common ones
        if (jdkObject == java.lang.Boolean.TYPE) {
          value = RVMType.BooleanType;
        } else if (jdkObject == java.lang.Byte.TYPE) {
          value = RVMType.ByteType;
        } else if (jdkObject == java.lang.Character.TYPE) {
          value = RVMType.CharType;
        } else if (jdkObject == java.lang.Double.TYPE) {
          value = RVMType.DoubleType;
        } else if (jdkObject == java.lang.Float.TYPE) {
          value = RVMType.FloatType;
        } else if (jdkObject == java.lang.Integer.TYPE) {
          value = RVMType.IntType;
        } else if (jdkObject == java.lang.Long.TYPE) {
          value = RVMType.LongType;
        } else if (jdkObject == java.lang.Short.TYPE) {
          value = RVMType.ShortType;
        } else if (jdkObject == java.lang.Void.TYPE) {
          value = RVMType.VoidType;
        } else {
          value = TypeReference.findOrCreate((Class<?>)jdkObject).peekType();
          if (value == null) {
            fail("Failed to populate Class.type for " + jdkObject);
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
          bootImage.setNullAddressWord(rvmFieldAddress, true, true, false);
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          imageAddress = copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
          bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, !fieldIsFinal);
        } else {
          if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
          bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, !fieldIsFinal);
        }
        if (verbose >= 2) traceContext.pop();
        return true;
      } else {
        // Unknown Class field or value for type
        return false;
      }
    }
    // Class library dependent fields
    if (classLibrary == "harmony") {
      if ((jdkObject instanceof java.lang.String) &&
          (rvmFieldName.equals("hashCode")) &&
          (rvmFieldType.isIntType())
          ) {
        // Populate String's hashCode value
        bootImage.setFullWord(rvmFieldAddress, jdkObject.hashCode());
        return true;
      } else if (jdkObject instanceof java.util.Locale) {
        String fieldName;
        Object value;
        if (rvmFieldName.equals("countryCode")) {
          value = ((java.util.Locale)jdkObject).getCountry();
          fieldName = "countryCode";
        } else if (rvmFieldName.equals("languageCode")) {
          value = ((java.util.Locale)jdkObject).getLanguage();
          fieldName = "languageCode";
        } else if (rvmFieldName.equals("variantCode")) {
          value = ((java.util.Locale)jdkObject).getVariant();
          fieldName = "languageCode";
        } else {
          return false;
        }
        if (verbose >= 2) traceContext.push(value.getClass().getName(),
                                            "java.util.Locale",
                                            fieldName);
        Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          // object not part of bootimage: install null reference
          if (verbose >= 2) traceContext.traceObjectNotInBootImage();
          throw new Error("Failed to populate " + fieldName + " in Locale");
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          imageAddress = copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
          bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        } else {
          if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
          bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        }
        if (verbose >= 2) traceContext.pop();
        return true;
      } else if ((jdkObject instanceof java.util.WeakHashMap) &&
                 (rvmFieldName.equals("referenceQueue"))){
        Object value = new java.lang.ref.ReferenceQueue();
        if (verbose >= 2) traceContext.push(value.getClass().getName(),
                                            "java.util.WeakHashMap",
                                            "referenceQueue");
        Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          // object not part of bootimage: install null reference
          if (verbose >= 2) traceContext.traceObjectNotInBootImage();
          throw new Error("Failed to populate referenceQueue in WeakHashMap");
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          imageAddress = copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
          bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        } else {
          if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
          bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        }
        if (verbose >= 2) traceContext.pop();
        return true;
      } else if (jdkObject instanceof java.lang.ref.ReferenceQueue) {
        if (rvmFieldName.equals("firstReference")){
          return false;
        } else {
          throw new Error("Unknown field "+rvmFieldName+" in java.lang.ref.ReferenceQueue");
        }
      } else if (jdkObject instanceof java.lang.reflect.Constructor)   {
        Constructor<?> cons = (Constructor<?>)jdkObject;
        if(rvmFieldName.equals("vmConstructor")) {
          // fill in this RVMMethod field
          String typeName = "L" + cons.getDeclaringClass().getName().replace('.','/') + ";";
          RVMType type = TypeReference.findOrCreate(typeName).peekType();
          if (type == null) {
            throw new Error("Failed to find type for Constructor.constructor: " + cons + " " + typeName);
          }
          final RVMClass klass = type.asClass();
          Class<?>[] consParams = cons.getParameterTypes();
          RVMMethod constructor = null;
          loop_over_all_constructors:
          for (RVMMethod vmCons : klass.getConstructorMethods()) {
            TypeReference[] vmConsParams = vmCons.getParameterTypes();
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
            throw new Error("Failed to populate Constructor.cons for " + cons);
          }
          if (verbose >= 2) traceContext.push("vmConstructor",
                                              "java.lang.Constructor",
                                              "cons");
          Address imageAddress = BootImageMap.findOrCreateEntry(constructor).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            // object not part of bootimage: install null reference
            if (verbose >= 2) traceContext.traceObjectNotInBootImage();
            bootImage.setNullAddressWord(rvmFieldAddress, true, false, false);
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = copyToBootImage(constructor, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          } else {
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          }
          if (verbose >= 2) traceContext.pop();
          return true;
        } else if(rvmFieldName.equals("isAccessible")) {
          // This field is inherited accesible flag is actually part of
          // AccessibleObject
          bootImage.setByte(rvmFieldAddress, cons.isAccessible() ? 1 : 0);
          return true;
        } else if(rvmFieldName.equals("invoker")) {
          // Bytecode reflection field, can only be installed in running VM
          bootImage.setNullAddressWord(rvmFieldAddress, true, false, false);
          return true;
        } else {
          // Unknown Constructor field
          throw new Error("Unknown field "+rvmFieldName+" in java.lang.reflect.Constructor");
        }
      } else {
        // unknown field
        return false;
      }
    } else if (classLibrary == "classpath") {
      if ((jdkObject instanceof java.lang.String) &&
          (rvmFieldName.equals("cachedHashCode")) &&
          (rvmFieldType.isIntType())
          ) {
        // Populate String's cachedHashCode value
        bootImage.setFullWord(rvmFieldAddress, jdkObject.hashCode());
        return true;
      } else if (jdkObject instanceof java.lang.reflect.Constructor)   {
        Constructor<?> cons = (Constructor<?>)jdkObject;
        if(rvmFieldName.equals("cons")) {
          // fill in this RVMMethod field
          String typeName = "L" + cons.getDeclaringClass().getName().replace('.','/') + ";";
          RVMType type = TypeReference.findOrCreate(typeName).peekType();
          if (type == null) {
            throw new Error("Failed to find type for Constructor.constructor: " + cons + " " + typeName);
          }
          final RVMClass klass = type.asClass();
          Class<?>[] consParams = cons.getParameterTypes();
          RVMMethod constructor = null;
          loop_over_all_constructors:
          for (RVMMethod vmCons : klass.getConstructorMethods()) {
            TypeReference[] vmConsParams = vmCons.getParameterTypes();
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
            throw new Error("Failed to populate Constructor.cons for " + cons);
          }
          if (verbose >= 2) traceContext.push("VMConstructor",
                                              "java.lang.Constructor",
                                              "cons");
          Object vmcons = java.lang.reflect.JikesRVMSupport.createVMConstructor(constructor);
          Address imageAddress = BootImageMap.findOrCreateEntry(vmcons).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            // object not part of bootimage: install null reference
            if (verbose >= 2) traceContext.traceObjectNotInBootImage();
            bootImage.setNullAddressWord(rvmFieldAddress, true, false, false);
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = copyToBootImage(vmcons, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          } else {
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          }
          if (verbose >= 2) traceContext.pop();
          return true;
        } else if(rvmFieldName.equals("flag")) {
          // This field is inherited accesible flag is actually part of
          // AccessibleObject
          bootImage.setByte(rvmFieldAddress, cons.isAccessible() ? 1 : 0);
          return true;
        } else {
          // Unknown Constructor field
          return false;
        }
      } else if (jdkObject instanceof java.lang.ref.ReferenceQueue) {
        if(rvmFieldName.equals("lock")) {
          VM.sysWriteln("writing the lock field.");
          Object value = new org.jikesrvm.scheduler.LightMonitor();
          if (verbose>=2) traceContext.push(value.getClass().getName(),
                                            "java.lang.ref.ReferenceQueue",
                                            "lock");
          Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            if (verbose >= 2) traceContext.traceObjectNotInBootImage();
            throw new Error("Failed to populate lock in ReferenceQueue");
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          } else {
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          }
          if (verbose>=2) traceContext.pop();
          return true;
        } else if (rvmFieldName.equals("first")){
          return false;
        } else {
          throw new Error("Unknown field "+rvmFieldName+" in java.lang.ref.ReferenceQueue");
        }
      } else if (jdkObject instanceof java.util.BitSet) {
        BitSet bs = (BitSet)jdkObject;
        if(rvmFieldName.equals("bits")) {
          int max = 0; // highest bit set in set
          for(int i=bs.nextSetBit(0); i>=0; i=bs.nextSetBit(i+1)) {
            max = i;
          }
          long[] bits = new long[(max+63)/64];
          for(int i=bs.nextSetBit(0); i>=0; i=bs.nextSetBit(i+1)) {
            bits[i/64] |= 1L << (i & 63);
          }
          if (verbose >= 2) traceContext.push("[J", "java.util.BitSet", "bits");
          Address imageAddress = BootImageMap.findOrCreateEntry(bits).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            // object not part of bootimage: install null reference
            if (verbose >= 2) traceContext.traceObjectNotInBootImage();
            bootImage.setNullAddressWord(rvmFieldAddress, true, false);
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = copyToBootImage(bits, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), false, false);
          } else {
            if (verbose >= 3) traceContext.traceObjectFoundThroughKnown();
            bootImage.setAddressWord(rvmFieldAddress, imageAddress.toWord(), false, false);
          }
          if (verbose >= 2) traceContext.pop();
          return true;
        } else {
          // Unknown BitSet field
          return false;
        }
      } else {
        // Unknown field
        return false;
      }
    } else {
      throw new Error("Unknown class library: \"" + classLibrary + "\"");
    }
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
        public int hashCode() { return System.identityHashCode(wrapper); }
        @Override
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
      Class<?> jdkType = jdkObject.getClass();
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
        Class<?> jdkElementType = jdkType.getComponentType();
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
        for (Class<?> type = jdkType; type != null; type = type.getSuperclass()) {
          Field[] jdkFields = type.getDeclaredFields();
          for (int i = 0, n = jdkFields.length; i < n; ++i) {
            Field  jdkField       = jdkFields[i];
            jdkField.setAccessible(true);
            Class<?>  jdkFieldType   = jdkField.getType();

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
   * Obtain RVM type corresponding to host JDK type.
   *
   * @param jdkType JDK type
   * @return RVM type ({@code null} --> type does not appear in list of classes
   *         comprising bootimage)
   */
  private static RVMType getRvmType(Class<?> jdkType) {
    return bootImageTypes.get(jdkType.getName());
  }

  /**
   * Obtain host JDK type corresponding to target RVM type.
   *
   * @param rvmType RVM type
   * @return JDK type ({@code null} --> type does not exist in host namespace)
   */
  private static Class<?> getJdkType(RVMType rvmType) {
    Throwable x;
    try {
      return Class.forName(rvmType.toString());
    } catch (ExceptionInInitializerError e) {
      throw e;
    } catch (IllegalAccessError e) {
      x = e;
    } catch (UnsatisfiedLinkError e) {
      x = e;
    } catch (NoClassDefFoundError e) {
      x = e;
    } catch (SecurityException e) {
      x = e;
    } catch (ClassNotFoundException e) {
      x = e;
    }
    if (verbose >= 1) {
      say(x.toString());
    }
    return null;
}

  /**
   * Obtain accessor via which a field value may be fetched from host JDK
   * address space.
   *
   * @param jdkType class whose field is sought
   * @param index index in FieldInfo of field sought
   * @param isStatic is field from Static field table, indicates which table to consult
   * @return field accessor (null --> host class does not have specified field)
   */
  private static Field getJdkFieldAccessor(Class<?> jdkType, int index, boolean isStatic) {
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
   * Figure out name of static RVM field whose value lives in specified JTOC
   * slot.
   *
   * @param jtocSlot JTOC slot number
   * @return field name
   */
  private static RVMField getRvmStaticField(Offset jtocOff) {
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

  private static CompiledMethod findMethodOfCode(Object code) {
    for (int i = 0; i < CompiledMethods.numCompiledMethods(); ++i) {
      CompiledMethod compiledMethod = CompiledMethods.getCompiledMethodUnchecked(i);
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

    // Restore previously unnecessary Statics data structures
    Statics.bootImageReportGeneration(staticsJunk);


    FileOutputStream fos = new FileOutputStream(mapFileName);
    BufferedOutputStream bos = new BufferedOutputStream(fos, 128);
    PrintStream out = new PrintStream(bos, false);

    out.println("#! /bin/bash");
    out.println("# This is a method address map, for use with the ``dbx'' debugger.");
    out.println("# To sort by \"code\" address, type \"bash <name-of-this-file>\".");
    out.println("# Bootimage data: "+Integer.toHexString(BOOT_IMAGE_DATA_START.toInt()) +
                "..."+Integer.toHexString(BOOT_IMAGE_DATA_START.toInt()+bootImage.getDataSize()));
    out.println("# Bootimage code: "+Integer.toHexString(BOOT_IMAGE_CODE_START.toInt()) +
                "..."+Integer.toHexString(BOOT_IMAGE_CODE_START.toInt()+bootImage.getCodeSize()));
    out.println("# Bootimage refs: "+Integer.toHexString(BOOT_IMAGE_RMAP_START.toInt()) +
                "..."+Integer.toHexString(BOOT_IMAGE_RMAP_START.toInt()+bootImage.getRMapSize()));

    out.println();
    out.println("(/bin/grep 'code     0x' | /bin/sort -k 4.3,4) << EOF-EOF-EOF");
    out.println();
    out.println("JTOC Map");
    out.println("--------");
    out.println("slot  offset     category contents            details");
    out.println("----  ------     -------- --------            -------");

    String pad = "        ";

    // Numeric JTOC fields
    for (int jtocSlot = Statics.getLowestInUseSlot();
         jtocSlot < Statics.middleOfTable;
         jtocSlot++) {
      Offset jtocOff = Statics.slotAsOffset(jtocSlot);
      String category;
      String contents;
      String details;
      RVMField field = getRvmStaticField(jtocOff);
      RVMField field2 = getRvmStaticField(jtocOff.plus(4));
      boolean couldBeLongLiteral = Statics.isLongSizeLiteral(jtocSlot);
      boolean couldBeIntLiteral = Statics.isIntSizeLiteral(jtocSlot);
      if (couldBeLongLiteral && ((field == null) || (field2 == null))) {
        if ((field == null) && (field2 == null)) {
          category = "literal      ";
          long lval = Statics.getSlotContentsAsLong(jtocOff);
          contents = VM.intAsHexString((int) (lval >> 32)) +
            VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
          details  = lval + "L";
        } else if ((field == null) && (field2 != null)) {
          category = "literal/field";
          long lval = Statics.getSlotContentsAsLong(jtocOff);
          contents = VM.intAsHexString((int) (lval >> 32)) +
            VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
          details  = lval + "L / " + field2.toString();
        } else if ((field != null) && (field2 == null)) {
          category = "literal/field";
          long lval = Statics.getSlotContentsAsLong(jtocOff);
          contents = VM.intAsHexString((int) (lval >> 32)) +
            VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
          details  = lval + "L / " + field.toString();
        } else {
          throw new Error("Unreachable");
        }
        jtocSlot++;
      } else if (couldBeIntLiteral) {
        if (field != null) {
          category = "literal/field";
          int ival = Statics.getSlotContentsAsInt(jtocOff);
          contents = VM.intAsHexString(ival) + pad;
          details  = Integer.toString(ival) + " / " + field.toString();
        } else {
          category = "literal      ";
          int ival = Statics.getSlotContentsAsInt(jtocOff);
          contents = VM.intAsHexString(ival) + pad;
          details  = Integer.toString(ival);
        }
      } else {
        if (field != null) {
          category = "field        ";
          details  = field.toString();
          TypeReference type = field.getType();
          if (type.isIntLikeType()) {
            int ival = Statics.getSlotContentsAsInt(jtocOff);
            contents = VM.intAsHexString(ival) + pad;
          } else if(type.isLongType()) {
            long lval= Statics.getSlotContentsAsLong(jtocOff);
            contents = VM.intAsHexString((int) (lval >> 32)) +
              VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
            jtocSlot++;
          } else if(type.isFloatType()) {
            int ival = Statics.getSlotContentsAsInt(jtocOff);
            contents = Float.toString(Float.intBitsToFloat(ival)) + pad;
          } else if(type.isDoubleType()) {
            long lval= Statics.getSlotContentsAsLong(jtocOff);
            contents = Double.toString(Double.longBitsToDouble(lval)) + pad;
            jtocSlot++;
          } else if (type.isWordLikeType()) {
            if (VM.BuildFor32Addr) {
              int ival = Statics.getSlotContentsAsInt(jtocOff);
              contents = VM.intAsHexString(ival) + pad;
            } else {
              long lval= Statics.getSlotContentsAsLong(jtocOff);
              contents = VM.intAsHexString((int) (lval >> 32)) +
                VM.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
              jtocSlot++;
            }
          } else {
            // Unknown?
            int ival = Statics.getSlotContentsAsInt(jtocOff);
            category = "<? - field>  ";
            details  = "<? - " + field.toString() + ">";
            contents = VM.intAsHexString(ival) + pad;
          }
        } else {
          // Unknown?
          int ival = Statics.getSlotContentsAsInt(jtocOff);
          category = "<?>        ";
          details  = "<?>";
          contents = VM.intAsHexString(ival) + pad;
        }
      }
      out.println((jtocSlot + "        ").substring(0,8) +
                  VM.addressAsHexString(jtocOff.toWord().toAddress()) + " " +
                  category + "  " + contents + "  " + details);
    }

    // Reference JTOC fields
    for (int jtocSlot = Statics.middleOfTable,
           n = Statics.getHighestInUseSlot();
         jtocSlot <= n;
         jtocSlot += Statics.getReferenceSlotSize()) {
      Offset jtocOff = Statics.slotAsOffset(jtocSlot);
      Object obj     = BootImageMap.getObject(getIVal(jtocOff));
      String category;
      String details;
      String contents = VM.addressAsHexString(getReferenceAddr(jtocOff, false)) + pad;
      RVMField field = getRvmStaticField(jtocOff);
      if (Statics.isReferenceLiteral(jtocSlot)) {
        if (field != null) {
          category = "literal/field";
        } else {
          category = "literal      ";
        }
        if (obj == null){
          details = "(null)";
        } else if (obj instanceof String) {
          details = "\""+ obj + "\"";
        } else if (obj instanceof Class) {
          details = obj.toString();;
        } else if (obj instanceof TIB) {
          category = "literal tib  ";
          RVMType type = ((TIB)obj).getType();
          details = (type == null) ? "?" : type.toString();
        } else {
          details = "object "+ obj.getClass();
        }
        if (field != null) {
          details += " / " + field.toString();
        }
      } else if (field != null) {
        category = "field        ";
        details  = field.toString();
      } else if (obj instanceof TIB) {
        // TIBs confuse the statics as their backing is written into the boot image
        category = "tib          ";
        RVMType type = ((TIB)obj).getType();
        details = (type == null) ? "?" : type.toString();
      } else {
        category = "unknown      ";
        if (obj instanceof String) {
          details = "\""+ obj + "\"";
        } else if (obj instanceof Class) {
          details = obj.toString();
        } else {
          CompiledMethod m = findMethodOfCode(obj);
          if (m != null) {
            category = "code         ";
            details = m.getMethod().toString();
          } else if (obj != null) {
            details  = "<?> - unrecognized field or literal of type " + obj.getClass();
          } else {
            details  = "<?>";
          }
        }
      }
      out.println((jtocSlot + "        ").substring(0,8) +
                  VM.addressAsHexString(jtocOff.toWord().toAddress()) + " " +
                  category + "  " + contents + "  " + details);
    }

    out.println();
    out.println("Method Map");
    out.println("----------");
    out.println("                          address             method");
    out.println("                          -------             ------");
    out.println();
    for (int i = 0; i < CompiledMethods.numCompiledMethods(); ++i) {
      CompiledMethod compiledMethod = CompiledMethods.getCompiledMethodUnchecked(i);
      if (compiledMethod != null) {
        RVMMethod m = compiledMethod.getMethod();
        if (m != null && compiledMethod.isCompiled()) {
          CodeArray instructions = compiledMethod.getEntryCodeArray();
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

      SortedSet<BootImageMap.Entry> set = new TreeSet<BootImageMap.Entry>(new Comparator<BootImageMap.Entry>() {
        @Override
        public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
          return Integer.valueOf(a.imageAddress.toInt()).compareTo(b.imageAddress.toInt());
        }
      });
      for (Enumeration<BootImageMap.Entry> e = BootImageMap.elements(); e.hasMoreElements();) {
        BootImageMap.Entry entry = e.nextElement();
        set.add(entry);
      }
      for (Iterator<BootImageMap.Entry> i = set.iterator(); i.hasNext();) {
        BootImageMap.Entry entry = i.next();
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
      ival = Statics.getSlotContentsAsInt(jtocOff);
    } else {
      ival = (int)Statics.getSlotContentsAsLong(jtocOff); // just a cookie
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
      if (jdkObject instanceof RuntimeTable) {
        jdkObject = ((RuntimeTable<?>)jdkObject).getBacking();
      }
      return BootImageMap.getImageAddress(jdkObject, fatalIfNotFound);
    } else {
      return Address.zero();
    }
  }

  /**
   * Get the size of the object in the boot image
   * @param type of object
   * @param obj we want the size of
   * @return size of object
   */
  private static int getSize(RVMType type, Object obj) {
    if (type.isArrayType()) {
      if (obj instanceof RuntimeTable) {
        obj = ((RuntimeTable<?>)obj).getBacking();
      } else if (obj instanceof CodeArray) {
        obj = ((CodeArray)obj).getBacking();
      }
      if (!obj.getClass().isArray()) {
        fail("This should be an array " + obj.getClass() + " " + type);
      }
      return type.asArray().getInstanceSize(Array.getLength(obj));
    } else {
      return type.asClass().getInstanceSize();
    }
  }

  private static final HashMap<RVMType, Integer> typeSizes = new HashMap<RVMType, Integer>();

  /**
   * Get the number of non-final references of the object in the boot image
   * @param type of object
   * @param obj we want the size of
   * @return number of non-final references
   */
  private static int getNumberOfNonFinalReferences(RVMType type, Object obj) {
    if (type.isArrayType()) {
      if (type.asArray().getElementType().isReferenceType()) {
        if (obj instanceof RuntimeTable) {
          obj = ((RuntimeTable<?>)obj).getBacking();
        } else if (obj instanceof CodeArray) {
          obj = ((CodeArray)obj).getBacking();
        }
        if (!obj.getClass().isArray()) {
          fail("This should be an array " + obj.getClass() + " " + type);
        }
        return Array.getLength(obj);
      } else {
        return 0;
      }
    } else {
      Integer size = typeSizes.get(type);
      if (size == null) {
        // discount final references that aren't part of the boot image
        size = type.asClass().getNumberOfNonFinalReferences();
        typeSizes.put(type, size);
      }
      return size;
    }
  }

  /**
   * Get the number of non-final references of the object in the boot image
   * @param type of object
   * @param obj we want the size of
   * @return number of non-final references
   */
  private static int getNumberOfReferences(RVMType type, Object obj) {
    if (type.isArrayType()) {
      if (type.asArray().getElementType().isReferenceType()) {
        if (obj instanceof RuntimeTable) {
          obj = ((RuntimeTable<?>)obj).getBacking();
        } else if (obj instanceof CodeArray) {
          obj = ((CodeArray)obj).getBacking();
        }
        if (!obj.getClass().isArray()) {
          fail("This should be an array " + obj.getClass() + " " + type);
        }
        return Array.getLength(obj);
      } else {
        return 0;
      }
    } else {
      Integer size = typeSizes.get(type);
      if (size == null) {
        // discount final references that aren't part of the boot image
        size = type.getReferenceOffsets().length;
        typeSizes.put(type, size);
      }
      return size;
    }
  }
}
