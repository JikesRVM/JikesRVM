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

import java.lang.reflect.Method;
import org.jikesrvm.architecture.ArchConstants;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.mm.mminterface.Selected.Constraints;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * A class to <em>estimate</em> the code size impact of garbage collector
 * barriers.
 * <p>
 * This will only run on Jikes RVM as it reaches into the
 * internals of the VM to get access to the generated machine code for
 * the prototypical methods.
 * <p>
 * It can also be used with org.jikesrvm.tools.oth.OptTestHarness to manually inspect and
 * tune the barrier code sequences.
 */
class BarrierSize {

  boolean booleanField;
  byte byteField;
  char charField;
  short shortField;
  int intField;
  float floatField;
  double doubleField;
  long longField;
  Object objectField;
  Address addressField;
  Word wordField;
  Extent extentField;
  Offset offsetField;

  boolean[] booleanArray;
  byte[] byteArray;
  char[] charArray;
  short[] shortArray;
  int[] intArray;
  float[] floatArray;
  double[] doubleArray;
  long[] longArray;
  Object[] objectArray;

  /** size for the empty method in units */
  private static int emptySize;

  /** the class object for this class */
  private static Class<?> thisClass;

  /**
   * The size is either in bytes or number of instructions,
   * depending on the architecture.
   */
  private static final String unit = (ArchConstants.getInstructionWidth() == 1) ?
      " bytes" : " instructions";

  @NoInline
  void emptyInstanceMethod() {
    // no content
  }

  @NoInline
  void writeToBooleanField(boolean b) {
    this.booleanField = b;
  }

  @NoInline
  boolean readFromBooleanField() {
    return this.booleanField;
  }

  @NoInline
  void writeToByteField(byte b) {
    this.byteField = b;
  }

  @NoInline
  byte readFromByteField() {
    return this.byteField;
  }

  @NoInline
  void writeToCharField(char c) {
    this.charField = c;
  }

  @NoInline
  char readFromCharField() {
    return this.charField;
  }

  @NoInline
  void writeToShortField(short s) {
    this.shortField = s;
  }

  @NoInline
  short readFromShortField() {
    return this.shortField;
  }

  @NoInline
  void writeToIntField(int i) {
    this.intField = i;
  }

  @NoInline
  int readFromIntField() {
    return this.intField;
  }

  @NoInline
  void writeToFloatField(float f) {
    this.floatField = f;
  }

  @NoInline
  float readFromFloatField() {
    return this.floatField;
  }

  @NoInline
  void writeToDoubleField(double d) {
    this.doubleField = d;
  }

  @NoInline
  double readFromDoubleField() {
    return this.doubleField;
  }

  @NoInline
  void writeToLongField(long l) {
    this.longField = l;
  }

  @NoInline
  Long readFromLongField() {
    return this.longField;
  }

  @NoInline
  void writeToObjectField(Object o) {
    this.objectField = o;
  }

  @NoInline
  Object readFromObjectField() {
    return this.objectField;
  }

  @NoInline
  void writeToAddressField(Address a) {
    this.addressField = a;
  }

  @NoInline
  Address readFromAddressField() {
    return this.addressField;
  }

  @NoInline
  void writeToWordField(Word w) {
    this.wordField = w;
  }

  @NoInline
  Word readFromWordField() {
    return this.wordField;
  }

  @NoInline
  void writeToExtentField(Extent e) {
    this.extentField = e;
  }

  @NoInline
  Extent readFromExtentField() {
    return this.extentField;
  }

  @NoInline
  void writeToOffsetField(Offset o) {
    this.offsetField = o;
  }

  @NoInline
  Offset readFromOffsetField() {
    return this.offsetField;
  }

  @NoInline
  void writeToBooleanArray(boolean b) {
    this.booleanArray[0] = b;
  }

  @NoInline
  boolean readFromBooleanArray() {
    return this.booleanArray[0];
  }

  @NoInline
  void writeToByteArray(byte b) {
    this.byteArray[0] = b;
  }

  @NoInline
  byte readFromByteArray() {
    return this.byteArray[0];
  }

  @NoInline
  void writeToCharArray(char c) {
    this.charArray[0] = c;
  }

  @NoInline
  char readFromCharArray() {
    return this.charArray[0];
  }

  @NoInline
  void writeToShortArray(short s) {
    this.shortArray[0] = s;
  }

  @NoInline
  short readFromShortArray() {
    return this.shortArray[0];
  }

  @NoInline
  void writeToIntArray(int i) {
    this.intArray[0] = i;
  }

  @NoInline
  int readFromIntArray() {
    return this.intArray[0];
  }

  @NoInline
  void writeToFloatArray(float f) {
    this.floatArray[0] = f;
  }

  @NoInline
  float readFromFloatArray() {
    return this.floatArray[0];
  }

  @NoInline
  void writeToDoubleArray(double d) {
    this.doubleArray[0] = d;
  }

  @NoInline
  double readFromDoubleArray() {
    return this.doubleArray[0];
  }

  @NoInline
  void writeToLongArray(long l) {
    this.longArray[0] = l;
  }

  @NoInline
  Long readFromLongArray() {
    return this.longArray[0];
  }

  @NoInline
  void writeToObjectArray(Object o) {
    this.objectArray[0] = o;
  }

  private static int methodInstructionCount(String name, Class<?>... parameterTypes) throws SecurityException, NoSuchMethodException {
    Method m = thisClass.getDeclaredMethod(name, parameterTypes);
    RVMMethod internalMethod = java.lang.reflect.JikesRVMSupport.getMethodOf(m);
    internalMethod.compile();
    CompiledMethod compiledMethod = internalMethod.getCurrentCompiledMethod();
    // Subtract off prologue/epilogue size. This is approximate!
    // If you really care, count the instructions by hand!
    return compiledMethod.numberOfInstructions() - emptySize;
  }

  private static void printBarrierRequirement(String barrierType, boolean barrierProperty) {
    System.out.println("Plan requires " + barrierType + " barriers: " + barrierProperty);
  }

  private static void printSizeForMethod(String barrierType, int size) {
    System.out.println("Size of method with " + barrierType + " is " + size + unit);
  }

  private static void printHeader(String header) {
    System.out.println(" ------- " + header + " ------- ");
  }


  public static void main(String[] args) throws Exception {
    RVMClass barrierSize = JikesRVMSupport.getTypeForClass(BarrierSize.class).asClass();
    barrierSize.prepareForFirstUse();

    thisClass = Class.forName("BarrierSize");
    emptySize = methodInstructionCount("emptyInstanceMethod", (Class[])null);
    Constraints constraints = Selected.Constraints.get();

    printHeader("reference barriers");

    int oFieldWriteSize = methodInstructionCount("writeToObjectField", Object.class);
    int oFieldReadSize = methodInstructionCount("readFromObjectField", (Class[])null);

    printBarrierRequirement("object reference write", constraints.needsObjectReferenceWriteBarrier());
    printSizeForMethod("object reference write", oFieldWriteSize);

    printBarrierRequirement("object reference read", constraints.needsObjectReferenceReadBarrier());
    printSizeForMethod("object reference read", oFieldReadSize);

    System.out.println();

    printHeader("primitive barriers");

    int booleanWriteSize = methodInstructionCount("writeToBooleanField", boolean.class);
    printBarrierRequirement("boolean write", constraints.needsBooleanWriteBarrier());
    printSizeForMethod("boolean write", booleanWriteSize);

    int booleanReadSize = methodInstructionCount("readFromBooleanField", (Class[])null);
    printBarrierRequirement("boolean read", constraints.needsBooleanWriteBarrier());
    printSizeForMethod("boolean read", booleanReadSize);

    int byteWriteSize = methodInstructionCount("writeToByteField", byte.class);
    printBarrierRequirement("byte write", constraints.needsByteWriteBarrier());
    printSizeForMethod("byte write", byteWriteSize);

    int byteReadSize = methodInstructionCount("readFromByteField", (Class[])null);
    printBarrierRequirement("byte read", constraints.needsByteWriteBarrier());
    printSizeForMethod("byte read", byteReadSize);

    int charWriteSize = methodInstructionCount("writeToCharField", char.class);
    printBarrierRequirement("char write", constraints.needsCharWriteBarrier());
    printSizeForMethod("char write", charWriteSize);

    int charReadSize = methodInstructionCount("readFromCharField", (Class[])null);
    printBarrierRequirement("char read", constraints.needsCharWriteBarrier());
    printSizeForMethod("char read", charReadSize);

    int shortWriteSize = methodInstructionCount("writeToShortField", short.class);
    printBarrierRequirement("short write", constraints.needsShortWriteBarrier());
    printSizeForMethod("short write", shortWriteSize);

    int shortReadSize = methodInstructionCount("readFromShortField", (Class[])null);
    printBarrierRequirement("short read", constraints.needsShortWriteBarrier());
    printSizeForMethod("short read", shortReadSize);

    int intWriteSize = methodInstructionCount("writeToIntField", int.class);
    printBarrierRequirement("int write", constraints.needsIntWriteBarrier());
    printSizeForMethod("int write", intWriteSize);

    int intReadSize = methodInstructionCount("readFromIntField", (Class[])null);
    printBarrierRequirement("int read", constraints.needsIntWriteBarrier());
    printSizeForMethod("int read", intReadSize);

    int floatWriteSize = methodInstructionCount("writeToFloatField", float.class);
    printBarrierRequirement("float write", constraints.needsFloatWriteBarrier());
    printSizeForMethod("float write", floatWriteSize);

    int floatReadSize = methodInstructionCount("readFromFloatField", (Class[])null);
    printBarrierRequirement("float read", constraints.needsFloatWriteBarrier());
    printSizeForMethod("float read", floatReadSize);

    int doubleWriteSize = methodInstructionCount("writeToDoubleField", double.class);
    printBarrierRequirement("double write", constraints.needsDoubleWriteBarrier());
    printSizeForMethod("double write", doubleWriteSize);

    int doubleReadSize = methodInstructionCount("readFromDoubleField", (Class[])null);
    printBarrierRequirement("double read", constraints.needsDoubleWriteBarrier());
    printSizeForMethod("double read", doubleReadSize);

    int longWriteSize = methodInstructionCount("writeToLongField", long.class);
    printBarrierRequirement("long write", constraints.needsLongWriteBarrier());
    printSizeForMethod("long write", longWriteSize);

    int longReadSize = methodInstructionCount("readFromLongField", (Class[])null);
    printBarrierRequirement("long read", constraints.needsLongWriteBarrier());
    printSizeForMethod("long read", longReadSize);

    System.out.println();

    printHeader("primitive array store barriers");

    int booleanArrayWriteSize = methodInstructionCount("writeToBooleanArray", boolean.class);
    printBarrierRequirement("boolean array write", constraints.needsBooleanWriteBarrier());
    printSizeForMethod("boolean array write", booleanArrayWriteSize);

    int booleanArrayReadSize = methodInstructionCount("readFromBooleanArray", (Class[])null);
    printBarrierRequirement("boolean array read", constraints.needsBooleanWriteBarrier());
    printSizeForMethod("boolean array read", booleanArrayReadSize);

    int byteArrayWriteSize = methodInstructionCount("writeToByteArray", byte.class);
    printBarrierRequirement("byte array write", constraints.needsByteWriteBarrier());
    printSizeForMethod("byte array write", byteArrayWriteSize);

    int byteArrayReadSize = methodInstructionCount("readFromByteArray", (Class[])null);
    printBarrierRequirement("byte array read", constraints.needsByteWriteBarrier());
    printSizeForMethod("byte array read", byteArrayReadSize);

    int charArrayWriteSize = methodInstructionCount("writeToCharArray", char.class);
    printBarrierRequirement("char array write", constraints.needsCharWriteBarrier());
    printSizeForMethod("char array write", charArrayWriteSize);

    int charArrayReadSize = methodInstructionCount("readFromCharArray", (Class[])null);
    printBarrierRequirement("char array read", constraints.needsCharWriteBarrier());
    printSizeForMethod("char array read", charArrayReadSize);

    int shortArrayWriteSize = methodInstructionCount("writeToShortArray", short.class);
    printBarrierRequirement("short array write", constraints.needsShortWriteBarrier());
    printSizeForMethod("short array write", shortArrayWriteSize);

    int shortArrayReadSize = methodInstructionCount("readFromShortArray", (Class[])null);
    printBarrierRequirement("short array read", constraints.needsShortWriteBarrier());
    printSizeForMethod("short array read", shortArrayReadSize);

    int intArrayWriteSize = methodInstructionCount("writeToIntArray", int.class);
    printBarrierRequirement("int array write", constraints.needsIntWriteBarrier());
    printSizeForMethod("int array write", intArrayWriteSize);

    int intArrayReadSize = methodInstructionCount("readFromIntArray", (Class[])null);
    printBarrierRequirement("int array read", constraints.needsIntWriteBarrier());
    printSizeForMethod("int array read", intArrayReadSize);

    int floatArrayWriteSize = methodInstructionCount("writeToFloatArray", float.class);
    printBarrierRequirement("float array write", constraints.needsFloatWriteBarrier());
    printSizeForMethod("float array write", floatArrayWriteSize);

    int floatArrayReadSize = methodInstructionCount("readFromFloatArray", (Class[])null);
    printBarrierRequirement("float array read", constraints.needsFloatWriteBarrier());
    printSizeForMethod("float array read", floatArrayReadSize);

    int doubleArrayWriteSize = methodInstructionCount("writeToDoubleArray", double.class);
    printBarrierRequirement("double array write", constraints.needsDoubleWriteBarrier());
    printSizeForMethod("double array write", doubleArrayWriteSize);

    int doubleArrayReadSize = methodInstructionCount("readFromDoubleArray", (Class[])null);
    printBarrierRequirement("double array read", constraints.needsDoubleWriteBarrier());
    printSizeForMethod("double array read", doubleArrayReadSize);

    int longArrayWriteSize = methodInstructionCount("writeToLongArray", long.class);
    printBarrierRequirement("long array write", constraints.needsLongWriteBarrier());
    printSizeForMethod("long array write", longArrayWriteSize);

    int longArrayReadSize = methodInstructionCount("readFromLongArray", (Class[])null);
    printBarrierRequirement("long array read", constraints.needsLongWriteBarrier());
    printSizeForMethod("long array read", longArrayReadSize);

    System.out.println();

    printHeader("unboxed barriers");

    int addressWriteSize = methodInstructionCount("writeToAddressField", Address.class);
    printBarrierRequirement("address write", constraints.needsAddressWriteBarrier());
    printSizeForMethod("address write", addressWriteSize);

    int addressReadSize = methodInstructionCount("readFromAddressField", (Class[])null);
    printBarrierRequirement("address read", constraints.needsAddressWriteBarrier());
    printSizeForMethod("address read", addressReadSize);

    int wordWriteSize = methodInstructionCount("writeToWordField", Word.class);
    printBarrierRequirement("word write", constraints.needsWordWriteBarrier());
    printSizeForMethod("word write", wordWriteSize);

    int wordReadSize = methodInstructionCount("readFromWordField", (Class[])null);
    printBarrierRequirement("word read", constraints.needsWordWriteBarrier());
    printSizeForMethod("word read", wordReadSize);

    int extentWriteSize = methodInstructionCount("writeToExtentField", Extent.class);
    printBarrierRequirement("extent write", constraints.needsExtentWriteBarrier());
    printSizeForMethod("extent write", extentWriteSize);

    int extentReadSize = methodInstructionCount("readFromExtentField", (Class[])null);
    printBarrierRequirement("extent read", constraints.needsExtentWriteBarrier());
    printSizeForMethod("extent read", extentReadSize);

    int offsetWriteSize = methodInstructionCount("writeToOffsetField", Offset.class);
    printBarrierRequirement("offset write", constraints.needsOffsetWriteBarrier());
    printSizeForMethod("offset write", offsetWriteSize);

    int offsetReadSize = methodInstructionCount("readFromOffsetField", (Class[])null);
    printBarrierRequirement("offset read", constraints.needsOffsetWriteBarrier());
    printSizeForMethod("offset read", offsetReadSize);
  }

}
