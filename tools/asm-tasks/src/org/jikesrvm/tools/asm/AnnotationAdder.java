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
package org.jikesrvm.tools.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.BootstrapClassLoader;

import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoEscapes;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.RuntimePure;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Add annotations to classes using the ASM framework.
 */
public final class AnnotationAdder {
  /** Verbose debug information */
  private static final boolean VERBOSE = false;

  /** A triple of string constants used to identify an element to annotate */
  private static final class ElementTriple {
    private final String className;
    private final String elementName;
    private final String signature;
    ElementTriple(String className, String elementName, String signature) {
      this.className = className;
      this.elementName = elementName;
      this.signature = signature;
    }
    String getClassName() {
      return className;
    }
    public int hashCode() {
      return className.hashCode() + elementName.hashCode() + signature.hashCode();
    }
    public boolean equals(Object other) {
      if (other instanceof ElementTriple) {
        ElementTriple triple = (ElementTriple)other;
        return className.equals(triple.className) && elementName.equals(triple.elementName) &&
          signature.equals(triple.signature);
      } else {
        return false;
      }
    }
    public String toString() {
      return className + " " + elementName + " " + signature;
    }
  }

  /**
   * Elements we're looking to adapt and the annotations we want adding to them
   */
  private static final Map<AnnotatedElement, Set<Class<? extends Annotation>>> thingsToAnnotate =
    new HashMap<AnnotatedElement, Set<Class<? extends Annotation>>>();

  /**
   * More elements we're looking to adapt and the annotations we want adding to them
   */
  private static final Map<ElementTriple, Set<Class<? extends Annotation>>> thingsToAnnotate2 =
    new HashMap<ElementTriple, Set<Class<? extends Annotation>>>();

  /**
   * Name of class library
   */
  private static String classLibrary;

  /**
   * Destination directory for annotated classes
   */
  private static String destinationDir;

  /**
   * Elements that have been annotated
   */
  private static final Set<AnnotatedElement> annotatedElements =
    new HashSet<AnnotatedElement>();

  /**
   * More elements that have been annotated
   */
  private static final Set<ElementTriple> annotatedElements2 =
    new HashSet<ElementTriple>();

  /**
   * Add annotation to element
   * @param ann annotation to add
   * @param elem element to add it to
   */
  private static void addToAdapt(Class<? extends Annotation> ann, AnnotatedElement elem) {
    if (elem == null)
      throw new Error("Can't adapt a null element");
    if (ann == null)
      throw new Error("Can't annotate with null");
    Set<Class<? extends Annotation>> set = thingsToAnnotate.get(elem);
    if (set == null) {
      set = new HashSet<Class<? extends Annotation>>();
    }
    set.add(ann);
    thingsToAnnotate.put(elem, set);
  }

  /**
   * Add annotation to element
   * @param ann annotation to add
   * @param elem element to add it to
   */
  private static void addToAdapt(Class<? extends Annotation> ann, String className,
                                 String methodName, String signature) {
    if (ann == null)
      throw new Error("Can't annotate with null");
    ElementTriple triple = new ElementTriple(className, methodName, signature);
    Set<Class<? extends Annotation>> set = thingsToAnnotate2.get(triple);
    if (set == null) {
      set = new HashSet<Class<? extends Annotation>>();
    }
    set.add(ann);
    thingsToAnnotate2.put(triple, set);
  }

  /** Set up things to adapt */
  private static void setup() {
    try {
      if (classLibrary.toLowerCase().equals("gnu classpath")) {
        // java.lang.Throwable
        for (Constructor c : Throwable.class.getConstructors()) {
          addToAdapt(NoEscapes.class, c);
        }

        // java.nio.Buffer
        addToAdapt(Inline.class,
                   "java/nio/Buffer",
                   "<init>",
                   "(IIIILgnu/classpath/Pointer;)V");

        // gnu.java.nio.charset.ByteEncodeLoopHelper
        addToAdapt(Inline.class,
                   "gnu/java/nio/charset/ByteEncodeLoopHelper",
                   "normalEncodeLoop",
                   "(Ljava/nio/CharBuffer;Ljava/nio/ByteBuffer;)Ljava/nio/charset/CoderResult;");
        addToAdapt(Inline.class,
                   "gnu/java/nio/charset/ByteEncodeLoopHelper",
                   "arrayEncodeLoop",
                   "(Ljava/nio/CharBuffer;Ljava/nio/ByteBuffer;)Ljava/nio/charset/CoderResult;");

        // gnu.java.nio.charset.ByteDecodeLoopHelper
        addToAdapt(Inline.class,
                   "gnu/java/nio/charset/ByteDecodeLoopHelper",
                   "normalDecodeLoop",
                   "(Ljava/nio/ByteBuffer;Ljava/nio/CharBuffer;)Ljava/nio/charset/CoderResult;");
        addToAdapt(Inline.class,
                   "gnu/java/nio/charset/ByteDecodeLoopHelper",
                   "arrayDecodeLoop",
                   "(Ljava/nio/ByteBuffer;Ljava/nio/CharBuffer;)Ljava/nio/charset/CoderResult;");
      }
      // Proxy
      addToAdapt(RuntimePure.class, Proxy.class.getMethod("getProxyClass", new Class[]{ClassLoader.class, Class[].class}));
      // BigDecimal
      addToAdapt(Pure.class, BigDecimal.class.getMethod("abs", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("abs", new Class[]{MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("add", new Class[]{BigDecimal.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("add", new Class[]{BigDecimal.class, MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("byteValueExact", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("compareTo", new Class[]{BigDecimal.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("divide", new Class[]{BigDecimal.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("divide", new Class[]{BigDecimal.class, int.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("divide", new Class[]{BigDecimal.class, int.class, int.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("divide", new Class[]{BigDecimal.class, int.class, RoundingMode.class}));
      //addToAdapt(Pure.class, BigDecimal.class.getMethod("divide", new Class[]{BigDecimal.class, MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("doubleValue", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("equals", new Class[]{Object.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("floatValue", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("hashCode", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("intValue", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("intValueExact", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("longValue", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("longValueExact", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("max", new Class[]{BigDecimal.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("min", new Class[]{BigDecimal.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("movePointLeft", new Class[]{int.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("movePointRight", new Class[]{int.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("multiply", new Class[]{BigDecimal.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("multiply", new Class[]{BigDecimal.class, MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("negate", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("negate", new Class[]{MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("plus", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("plus", new Class[]{MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("pow", new Class[]{int.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("pow", new Class[]{int.class, MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("precision", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("remainder", new Class[]{BigDecimal.class}));
      //addToAdapt(Pure.class, BigDecimal.class.getMethod("remainder", new Class[]{BigDecimal.class, MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("round", new Class[]{MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("scale", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("scaleByPowerOfTen", new Class[]{int.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("setScale", new Class[]{int.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("setScale", new Class[]{int.class, int.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("shortValue", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("shortValueExact", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("signum", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("stripTrailingZeros", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("subtract", new Class[]{BigDecimal.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("subtract", new Class[]{BigDecimal.class, MathContext.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("toBigInteger", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("toBigIntegerExact", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("toEngineeringString", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("toPlainString", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("toString", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("ulp", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("unscaledValue", new Class[0]));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("valueOf", new Class[]{double.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("valueOf", new Class[]{long.class}));
      addToAdapt(Pure.class, BigDecimal.class.getMethod("valueOf", new Class[]{long.class, int.class}));

      // BigInteger
      addToAdapt(Pure.class, BigInteger.class.getMethod("abs", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("add", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("and", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("andNot", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("bitCount", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("bitLength", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("clearBit", new Class[]{int.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("compareTo", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("divide", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("divideAndRemainder", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("doubleValue", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("equals", new Class[]{Object.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("flipBit", new Class[]{int.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("floatValue", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("gcd", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("getLowestSetBit", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("hashCode", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("intValue", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("isProbablePrime", new Class[]{int.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("longValue", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("max", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("min", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("mod", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("modInverse", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("modPow", new Class[]{BigInteger.class, BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("multiply", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("negate", new Class[0]));
      //addToAdapt(Pure.class, BigInteger.class.getMethod("nextProbablePrime", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("not", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("or", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("pow", new Class[]{int.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("remainder", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("setBit", new Class[]{int.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("shiftLeft", new Class[]{int.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("shiftRight", new Class[]{int.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("signum", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("subtract", new Class[]{BigInteger.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("testBit", new Class[]{int.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("toByteArray", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("toString", new Class[0]));
      addToAdapt(Pure.class, BigInteger.class.getMethod("toString", new Class[]{int.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("valueOf", new Class[]{long.class}));
      addToAdapt(Pure.class, BigInteger.class.getMethod("xor", new Class[]{BigInteger.class}));

      // Double
      addToAdapt(Pure.class, Double.class.getMethod("byteValue", new Class[0]));
      addToAdapt(Pure.class, Double.class.getMethod("compare", new Class[]{double.class, double.class}));
      addToAdapt(Pure.class, Double.class.getMethod("compareTo", new Class[]{Double.class}));
      addToAdapt(Pure.class, Double.class.getMethod("doubleValue", new Class[0]));
      addToAdapt(Pure.class, Double.class.getMethod("equals", new Class[]{Object.class}));
      addToAdapt(Pure.class, Double.class.getMethod("floatValue", new Class[0]));
      addToAdapt(Pure.class, Double.class.getMethod("hashCode", new Class[0]));
      addToAdapt(Pure.class, Double.class.getMethod("intValue", new Class[0]));
      addToAdapt(Pure.class, Double.class.getMethod("longValue", new Class[0]));
      addToAdapt(Pure.class, Double.class.getMethod("parseDouble", new Class[]{String.class}));
      addToAdapt(Pure.class, Double.class.getMethod("shortValue", new Class[0]));
      addToAdapt(Pure.class, Double.class.getMethod("toHexString", new Class[]{double.class}));
      addToAdapt(Pure.class, Double.class.getMethod("toString", new Class[0]));
      addToAdapt(Pure.class, Double.class.getMethod("toString", new Class[]{double.class}));
      addToAdapt(Pure.class, Double.class.getMethod("valueOf", new Class[]{double.class}));
      addToAdapt(Pure.class, Double.class.getMethod("valueOf", new Class[]{String.class}));

      // Float
      addToAdapt(Pure.class, Float.class.getMethod("byteValue", new Class[0]));
      addToAdapt(Pure.class, Float.class.getMethod("compare", new Class[]{float.class, float.class}));
      addToAdapt(Pure.class, Float.class.getMethod("compareTo", new Class[]{Float.class}));
      addToAdapt(Pure.class, Float.class.getMethod("doubleValue", new Class[0]));
      addToAdapt(Pure.class, Float.class.getMethod("equals", new Class[]{Object.class}));
      addToAdapt(Pure.class, Float.class.getMethod("floatValue", new Class[0]));
      addToAdapt(Pure.class, Float.class.getMethod("hashCode", new Class[0]));
      addToAdapt(Pure.class, Float.class.getMethod("intValue", new Class[0]));
      addToAdapt(Pure.class, Float.class.getMethod("longValue", new Class[0]));
      addToAdapt(Pure.class, Float.class.getMethod("parseFloat", new Class[]{String.class}));
      addToAdapt(Pure.class, Float.class.getMethod("shortValue", new Class[0]));
      addToAdapt(Pure.class, Float.class.getMethod("toHexString", new Class[]{float.class}));
      addToAdapt(Pure.class, Float.class.getMethod("toString", new Class[0]));
      addToAdapt(Pure.class, Float.class.getMethod("toString", new Class[]{float.class}));
      addToAdapt(Pure.class, Float.class.getMethod("valueOf", new Class[]{float.class}));
      addToAdapt(Pure.class, Float.class.getMethod("valueOf", new Class[]{String.class}));

      // Integer
      addToAdapt(Pure.class, Integer.class.getMethod("bitCount", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("byteValue", new Class[0]));
      addToAdapt(Pure.class, Integer.class.getMethod("compareTo", new Class[]{Integer.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("decode", new Class[]{String.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("doubleValue", new Class[0]));
      addToAdapt(Pure.class, Integer.class.getMethod("equals", new Class[]{Object.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("floatValue", new Class[0]));
      addToAdapt(Pure.class, Integer.class.getMethod("hashCode", new Class[0]));
      addToAdapt(Pure.class, Integer.class.getMethod("highestOneBit", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("intValue", new Class[0]));
      addToAdapt(Pure.class, Integer.class.getMethod("longValue", new Class[0]));
      addToAdapt(Pure.class, Integer.class.getMethod("lowestOneBit", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("numberOfLeadingZeros", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("numberOfTrailingZeros", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("parseInt", new Class[]{String.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("parseInt", new Class[]{String.class, int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("reverse", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("reverseBytes", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("rotateLeft", new Class[]{int.class, int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("rotateRight", new Class[]{int.class, int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("shortValue", new Class[0]));
      addToAdapt(Pure.class, Integer.class.getMethod("signum", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("toBinaryString", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("toHexString", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("toOctalString", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("toString", new Class[0]));
      addToAdapt(Pure.class, Integer.class.getMethod("toString", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("toString", new Class[]{int.class, int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("valueOf", new Class[]{int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("valueOf", new Class[]{String.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("valueOf", new Class[]{String.class, int.class}));

      // Long
      addToAdapt(Pure.class, Long.class.getMethod("bitCount", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("byteValue", new Class[0]));
      addToAdapt(Pure.class, Long.class.getMethod("compareTo", new Class[]{Long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("decode", new Class[]{String.class}));
      addToAdapt(Pure.class, Long.class.getMethod("doubleValue", new Class[0]));
      addToAdapt(Pure.class, Long.class.getMethod("equals", new Class[]{Object.class}));
      addToAdapt(Pure.class, Long.class.getMethod("floatValue", new Class[0]));
      addToAdapt(Pure.class, Long.class.getMethod("hashCode", new Class[0]));
      addToAdapt(Pure.class, Long.class.getMethod("highestOneBit", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("intValue", new Class[0]));
      addToAdapt(Pure.class, Long.class.getMethod("longValue", new Class[0]));
      addToAdapt(Pure.class, Long.class.getMethod("lowestOneBit", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("numberOfLeadingZeros", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("numberOfTrailingZeros", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("parseLong", new Class[]{String.class}));
      addToAdapt(Pure.class, Long.class.getMethod("parseLong", new Class[]{String.class, int.class}));
      addToAdapt(Pure.class, Long.class.getMethod("reverse", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("reverseBytes", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("rotateLeft", new Class[]{long.class, int.class}));
      addToAdapt(Pure.class, Long.class.getMethod("rotateRight", new Class[]{long.class, int.class}));
      addToAdapt(Pure.class, Long.class.getMethod("shortValue", new Class[0]));
      addToAdapt(Pure.class, Long.class.getMethod("signum", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("toBinaryString", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("toHexString", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("toOctalString", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("toString", new Class[0]));
      addToAdapt(Pure.class, Long.class.getMethod("toString", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("toString", new Class[]{long.class, int.class}));
      addToAdapt(Pure.class, Long.class.getMethod("valueOf", new Class[]{long.class}));
      addToAdapt(Pure.class, Long.class.getMethod("valueOf", new Class[]{String.class}));
      addToAdapt(Pure.class, Long.class.getMethod("valueOf", new Class[]{String.class, int.class}));

      // Enum
      if (classLibrary.toLowerCase().equals("harmony")) {
        addToAdapt(Uninterruptible.class, Enum.class.getMethod("ordinal", new Class[0]));
        addToAdapt(Uninterruptible.class, Enum.class.getMethod("name", new Class[0]));
      }

      // String
      if (!classLibrary.toLowerCase().equals("harmony")) {
          addToAdapt(Pure.class, String.class.getMethod("charAt", new Class[]{int.class}));
          addToAdapt(Pure.class, String.class.getMethod("getBytes", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("getBytes", new Class[0]));
          addToAdapt(Pure.class, String.class.getMethod("equals", new Class[]{Object.class}));
          addToAdapt(Pure.class, String.class.getMethod("equalsIgnoreCase", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("compareTo", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("compareToIgnoreCase", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("regionMatches", new Class[]{int.class, String.class, int.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("regionMatches", new Class[]{boolean.class, int.class, String.class, int.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("startsWith", new Class[]{String.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("startsWith", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("endsWith", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("hashCode", new Class[0]));
          addToAdapt(Pure.class, String.class.getMethod("indexOf", new Class[]{int.class}));
          addToAdapt(Pure.class, String.class.getMethod("indexOf", new Class[]{int.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("lastIndexOf", new Class[]{int.class}));
          addToAdapt(Pure.class, String.class.getMethod("lastIndexOf", new Class[]{int.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("indexOf", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("indexOf", new Class[]{String.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("lastIndexOf", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("lastIndexOf", new Class[]{String.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("substring", new Class[]{int.class}));
          addToAdapt(Pure.class, String.class.getMethod("substring", new Class[]{int.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("subSequence", new Class[]{int.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("concat", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("replace", new Class[]{char.class, char.class}));
          addToAdapt(Pure.class, String.class.getMethod("substring", new Class[]{int.class}));
          addToAdapt(Pure.class, String.class.getMethod("matches", new Class[]{String.class}));
          addToAdapt(Pure.class, String.class.getMethod("replaceFirst", new Class[]{String.class, String.class}));
          addToAdapt(Pure.class, String.class.getMethod("replaceAll", new Class[]{String.class, String.class}));
          addToAdapt(Pure.class, String.class.getMethod("toLowerCase", new Class[]{Locale.class}));
          addToAdapt(Pure.class, String.class.getMethod("toLowerCase", new Class[0]));
          addToAdapt(Pure.class, String.class.getMethod("toUpperCase", new Class[]{Locale.class}));
          addToAdapt(Pure.class, String.class.getMethod("toUpperCase", new Class[0]));
          addToAdapt(Pure.class, String.class.getMethod("trim", new Class[0]));
          addToAdapt(Pure.class, String.class.getMethod("valueOf", new Class[]{boolean.class}));
          addToAdapt(Pure.class, String.class.getMethod("valueOf", new Class[]{char.class}));
          addToAdapt(Pure.class, String.class.getMethod("valueOf", new Class[]{int.class}));
          addToAdapt(Pure.class, String.class.getMethod("valueOf", new Class[]{long.class}));
          addToAdapt(Pure.class, String.class.getMethod("valueOf", new Class[]{float.class}));
          addToAdapt(Pure.class, String.class.getMethod("valueOf", new Class[]{double.class}));
          addToAdapt(Pure.class, String.class.getMethod("intern", new Class[0]));
          addToAdapt(Pure.class, String.class.getMethod("codePointCount", new Class[]{int.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("offsetByCodePoints", new Class[]{int.class, int.class}));
          addToAdapt(Pure.class, String.class.getMethod("length", new Class[0]));
      }
    } catch (Exception e) {
      System.out.println("Exception " + e);
      throw new Error(e);
    }
  }

  /**
   * Find the annotations to add to the given method and remove from the set
   * of things to annotate
   * @param className name of class we're adding annotation to
   * @param methodName name of method we're adding annotation to
   * @param methodDesc descriptor of method
   * @return set on annotations to add to method or null if none
   */
  static Set<Class<? extends Annotation>> findAnnotationsForMethod(String className, String methodName, String methodDesc) {
    for (AnnotatedElement elem: thingsToAnnotate.keySet()) {
      if (elem instanceof Method) {
        Method m = (Method)elem;
        if (m.getDeclaringClass().getName().equals(className) && m.getName().equals(methodName) &&
            Type.getMethodDescriptor(m).equals(methodDesc)) {
          annotatedElements.add(m);
          return thingsToAnnotate.get(m);
        }
      } else if (elem instanceof Constructor) {
        Constructor m = (Constructor)elem;
        if (m.getDeclaringClass().getName().equals(className) &&
            Type.getConstructorDescriptor(m).equals(methodDesc)) {
          annotatedElements.add(m);
          return thingsToAnnotate.get(m);
        }
      }
    }
    ElementTriple triple = new ElementTriple(className, methodName, methodDesc);
    Set<Class<? extends Annotation>> set = thingsToAnnotate2.get(triple);
    if (set != null) {
      annotatedElements2.add(triple);
    }
    return set;
  }

  /**
   * Main entry point
   * @param args args[0] is the classpath to use to read classes, args[1] is
   *        the destination directory
   */
  public static void main(final String[] args) {
    Set<Class<?>> processedClasses = new HashSet<Class<?>>();

    classLibrary = args[0];
    RVMClassLoader.init(args[1]);
    destinationDir = args[2] + "/";

    setup();

    for(AnnotatedElement elem: thingsToAnnotate.keySet()) {
      try {
        Class<?> c = getClassForElement(elem);
        if (!processedClasses.contains(c)) {
          adaptClass(c.getName());
          processedClasses.add(c);
        }
      } catch (Exception e) {
        throw new Error("Error processing "+elem, e);
      }
    }

    Set<String> processedClasses2 = new HashSet<String>();

    for(ElementTriple triple: thingsToAnnotate2.keySet()) {
      String c = triple.getClassName();
      if (!processedClasses2.contains(c)) {
        adaptClass(c);
        processedClasses2.add(c);
      }
    }

    for (AnnotatedElement elem: annotatedElements) {
      thingsToAnnotate.remove(elem);
    }

    for (ElementTriple triple: annotatedElements2) {
      thingsToAnnotate2.remove(triple);
    }

    if (thingsToAnnotate.size() > 0 || thingsToAnnotate2.size() > 0) {
      for (AnnotatedElement elem: thingsToAnnotate.keySet()) {
        System.out.println("Error finding element to annotate: " + elem);
      }
      for (ElementTriple triple: thingsToAnnotate2.keySet()) {
        System.out.println("Error finding element to annotate: " + triple);
      }
      throw new Error("Error finding elements to annotate");
    }
  }

  /**
   * Given an annotated element return the class that declares it
   * @param elem the annotated element
   * @return the declaring class or null if the declaring class is unknown
   */
  private static Class<?> getClassForElement(AnnotatedElement elem) {
    if (elem instanceof Method) {
      return ((Method)elem).getDeclaringClass();
    } else if (elem instanceof Constructor) {
      return ((Constructor)elem).getDeclaringClass();
    }
    return null;
  }
  /**
   * Chain together a ClassReader than will read the given class and a
   * ClassWriter to write out a new class with an adapter in the middle to add
   * annotations
   * @param fromName the name of the class we're coming from
   */
  private static void adaptClass(String fromName) {
    if (VERBOSE) System.out.println("Adding annotations to class: " + fromName);

    // gets an input stream to read the bytecode of the class
    String resource = fromName.replace('.', '/') + ".class";
    InputStream is = BootstrapClassLoader.getBootstrapClassLoader().getResourceAsStream(resource);
    byte[] b;

    // adapts the class on the fly
    try {
      ClassReader cr = new ClassReader(is);
      ClassWriter cw = new ClassWriter(0);
      ClassVisitor cv = new AddAnnotationClassAdapter(cw, fromName);
      cr.accept(cv, 0);
      b = cw.toByteArray();
    } catch (Exception e) {
      throw new Error("Couldn't find class " + fromName + " (" + resource + ")", e);
    }

    // store the adapted class on disk
    try {
      File file = new File(destinationDir + resource);
      new File(file.getParent()).mkdirs(); // ensure we have a directory to write to
      FileOutputStream fos = new FileOutputStream(file);
      fos.write(b);
      fos.close();
    } catch (Exception e) {
      throw new Error("Error writing to " + destinationDir + resource + " to disk", e);
    }
  }

  /**
   * Class responsible for processing classes and adding annotations
   */
  private static final class AddAnnotationClassAdapter extends ClassAdapter implements Opcodes {
    /** name of class */
    private final String className;

    /**
     * Constructor
     * @param cv the reader of the class
     * @param name the name of the class being read
     */
    public AddAnnotationClassAdapter(ClassVisitor cv, String name) {
      super(cv);
      this.className = name;
    }

    /**
     * Called when adapting a method. Determine whether we need to add an
     * annotation and if so vary the method visitor result
     * @param access flags
     * @param name of method
     * @param desc descriptor
     * @param signature generic signature
     * @param exceptions exceptions thrown
     * @return regular method visitor or one that will apply annotations
     */
    @Override
    public MethodVisitor visitMethod(final int access, final String name,
        final String desc, final String signature, final String[] exceptions) {
      MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
      if (mv != null) {
        Set<Class<? extends Annotation>> annotations = findAnnotationsForMethod(className, name, desc);
        if (annotations != null) {
          if (VERBOSE) System.out.println("Found method: " + name);
          return new AddAnnotationMethodAdapter(mv, annotations);
        }
      }
      return mv;
    }
  }

  /**
   * Class responsible for processing methods and adding annotations
   */
  private static final class AddAnnotationMethodAdapter extends MethodAdapter implements Opcodes {
    /** Annotations to add to method */
    private final Set<Class<? extends Annotation>> toAddAnnotations;
    /** Annotations found on method */
    private final Set<String> presentAnnotations = new HashSet<String>();
    /**
     * Constructor
     * @param mv the reader of the method
     * @param anns annotations to add
     */
    public AddAnnotationMethodAdapter(MethodVisitor mv, Set<Class<? extends Annotation>> anns) {
      super(mv);
      toAddAnnotations = anns;
    }

    /**
     * Visit annotation remembering what we see so that we don't add the same
     * annotation twice
     * @param desc descriptor of annotation
     * @param visible is it runtime visible
     * @return default annotation reader
     */
    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      presentAnnotations.add(desc);
      if (VERBOSE) System.out.println("Found annotation: " + desc);
      return mv.visitAnnotation(desc, visible);
    }

    /**
     * We've finished processing method, check what annotations were found and
     * then add those that weren't
     */
    @Override
    public void visitEnd() {
      outer:
      for (Class<? extends Annotation> toAddAnn : toAddAnnotations) {
        Type toAddAnnType = Type.getType(toAddAnn);
        if (VERBOSE) System.out.println("Annotation: " + toAddAnn);
        for (String presentAnn : presentAnnotations) {
          if (toAddAnnType.equals(Type.getType(presentAnn))) {
            if (VERBOSE) System.out.println("Annotation already present: " + toAddAnn + " " + presentAnn);
            continue outer;
          }
        }
        if (VERBOSE) System.out.println("Adding annotation: " + toAddAnn);
        mv.visitAnnotation("L"+toAddAnnType.getInternalName()+";", true);
      }
      mv.visitEnd();
    }
  }
}
