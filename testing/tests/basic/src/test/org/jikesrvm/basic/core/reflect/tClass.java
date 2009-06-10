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
package test.org.jikesrvm.basic.core.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

class tClass {

  private static class ToStringComparator implements Comparator<Object> {
    static final ToStringComparator COMPARATOR = new ToStringComparator();

    public int compare(Object x, Object y) {
      return x.toString().compareTo(y.toString());
    }
  }

  public static void gc() {
    System.gc();
  }

  public static String hello(String say_this) {
    System.out.println("before gc arg=" + say_this);
    gc();
    System.out.println("after  gc arg=" + say_this);
    return "And I Say Hello Back";
  }

  public static int iello(String say_this, int return_this) {
    System.out.println("before gc arg=" + say_this);
    gc();
    System.out.println("after  gc arg=" + say_this);
    return return_this;
  }

  public static long lello(String say_this, long return_this) {
    System.out.println("before gc arg=" + say_this);
    gc();
    System.out.println("after  gc arg=" + say_this);
    return return_this;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public static int jello(int return_this, String say_this, int junk, int more_junk) {
    System.out.println("before gc arg=" + say_this);
    gc();
    System.out.println("after  gc arg=" + say_this);
    return return_this;
  }

  public String vello(String say_this) {
    return "And I Say Vello Back";
  }

  public tClass(String s) {
    System.out.println("tClass constructor called with " + s);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  private tClass() {
    System.out.println("tClass private constructor called!");
  }

  public static void main(String[] args) throws Exception {
    // Class.forName
    //
    Class c = Class.forName("test.org.jikesrvm.basic.core.reflect.tClass");
    System.out.println(c);
    try {
      Class.forName("NotAClassSoThrowAnExceptionPlease");
    } catch (ClassNotFoundException e) {
      System.out.println("caught ClassNotFoundException");
    }

    // -----------------------------  Class.isArray()
    //
    if (c.isArray()) System.out.println(c + " is an array????");
    else System.out.println(c + " is not an array...good");

    // ------------------------------  Class.getConstructors
    //
    Constructor[] ctors = c.getConstructors();
    Arrays.sort(ctors, ToStringComparator.COMPARATOR);

    System.out.println(c + " has " + ctors.length + " visible constructors");
    for (int i = 0; i < ctors.length; ++i)
      System.out.println("   " + i + ": " + ctors[i]);

    Constructor[] declaredCtors = c.getDeclaredConstructors();
    Arrays.sort(declaredCtors, ToStringComparator.COMPARATOR);

    System.out.println(c + " has " + declaredCtors.length + " declared constructors");
    for (int i = 0; i < declaredCtors.length; ++i)
      System.out.println("   " + i + ": " + declaredCtors[i]);

    // Class.getMethods  Method.getName
    //
    Method[] methods = c.getMethods();
    Method hello = null;
    Method iello = null;
    Method lello = null;
    Method jello = null;
    Method vello = null;

    Method[] declaredMethods = c.getDeclaredMethods();
    Arrays.sort(declaredMethods, ToStringComparator.COMPARATOR);

    System.out.println(c + " has a total number of methods: " + methods.length);
    for (final Method method : methods) {
      // dont print the methods out, signitures are different in
      // java and RVM libraries for java/lang/Object methods.
      // System.out.println( methods[i] );
      if (method.getName().equals("hello")) hello = method;
      if (method.getName().equals("iello")) iello = method;
      if (method.getName().equals("lello")) lello = method;
      if (method.getName().equals("jello")) jello = method;
      if (method.getName().equals("vello")) vello = method;
    }

    System.out.println(" Number of declared methods: " + declaredMethods.length);

    for (Method method : declaredMethods) System.out.println(method);

    // ------------------------------  invoke methods taking String, returning ref (String)
    //
    if (hello == null) {
      System.out.println("tClass.hello not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + hello);
    }

    // Method.invoke
    //
    int n_calls = 3;  // loop to see if we can crash gc
    while (n_calls-- > 0) {
      String result = (String) hello.invoke(null, "I Say Hello to You!");
      System.out.println(result);
    }

    // ------------------------------  invoke methods taking String,int; returning int
    //
    if (iello == null) {
      System.out.println("tClass.iello not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + iello);
    }

    // Method.invoke
    //
    n_calls = 3;  // loop to see if we can crash gc
    while (n_calls-- > 0) {
      Integer result = (Integer) iello.invoke(null, "I Say Iello to You!", 99);
      System.out.println("Does this>" + result + "< look like 99?");
    }

    // ------------------------------  invoke methods taking String,long; returning long
    //
    if (lello == null) {
      System.out.println("tClass.lello not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + lello);
    }

    // Method.invoke
    //
    n_calls = 3;  // loop to see if we can crash gc
    while (n_calls-- > 0) {
      Long result = (Long) lello.invoke(null, "I Say Lello to You!", 99);
      System.out.println("Does this>" + result + "< look like 99?");
    }

    // ------------------------------  invoke methods taking String,int; returning int
    //
    if (jello == null) {
      System.out.println("tClass.jello not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + jello);
    }

    // Method.invoke
    //
    n_calls = 3;  // loop to see if we can crash gc
    while (n_calls-- > 0) {
      Integer result = (Integer) jello.invoke(null, 99, "I Say Jello to You!", 95, 94);
      System.out.println("Does this>" + result + "< look like 99?");
    }

    // ------------------------------  newInstance
    new tClass("Hi!");
    tClass tc_dyn = (tClass) ctors[0].newInstance("I'm dynamic!");

    // ------------------------------  invoke methods taking String, returning String, but now virtual
    //
    if (vello == null) {
      System.out.println("tClass.vello not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + vello);
    }

    // Method.invoke
    //
    n_calls = 3;  // loop to see if we can crash gc
    while (n_calls-- > 0) {
      String result = (String) vello.invoke(tc_dyn, "I Say Vello to You!");
      System.out.println(result);
    }
  }
}
