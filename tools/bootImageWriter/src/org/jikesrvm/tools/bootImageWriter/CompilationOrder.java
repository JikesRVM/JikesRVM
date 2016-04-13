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

import static org.jikesrvm.VM.NOT_REACHED;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.say;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;

public class CompilationOrder {

  /** Print out messages about compilation progress for debugging */
  private static final boolean DEBUG = false;

  // TODO estimates aren't based on any data.
  private static final int OBJECT_SUBCLASS_COUNT_ESTIMATE = 512;
  private static final int INTERFACE_SUBCLASS_COUNT_ESTIMATE = 2;
  private static final int CLASS_SUBCLASS_COUNT_ESTIMATE = 2;

  private final BlockingDeque<RVMType> compilationDeque;

  /** all superclasses that we need to compile */
  private final HashSet<RVMType> missingSuperclasses;

  /**
   * Maps each class to the classes waiting on its initialization.
   */
  private final Map<RVMType, Collection<RVMType>> waitingForSuperclassToInitialize;

  /**
   * Count of workers needed to instantiate all types. Needs to be atomic to ensure
   * that debug printouts are correct if enabled.
   */
  private final AtomicInteger neededWorkersCount;

  public CompilationOrder(int typeCount, int numThreads) {
    compilationDeque = new LinkedBlockingDeque<RVMType>(typeCount);
    float loadFactor = 0.75f; // standard load factor
    int initialCapacity = Math.round(typeCount / loadFactor) + 1;
    int concurrencyLevel = numThreads;
    waitingForSuperclassToInitialize = new ConcurrentHashMap<RVMType, Collection<RVMType>>(initialCapacity,
        loadFactor, concurrencyLevel);
    neededWorkersCount = new AtomicInteger(0);
    missingSuperclasses = new HashSet<RVMType>();
  }

  public Runnable getNextRunnable() throws InterruptedException {
    RVMType nextClass = compilationDeque.takeFirst();
    if (DEBUG) {
      int remainingRunnables = neededWorkersCount.decrementAndGet();
      say("Remaining runnable count: " + remainingRunnables);
    }
    return new BootImageWorker(nextClass, this);
  }

  /**
   * Adds a type to the compilation order. Types are treated as follows:
   * <ol>
   *   <li>primitive types and unboxed types are instantiated immediately because
   *   their instantiation is a no-op</li>
   *   <li>reference types are added to the compilation queue if their superclass
   *   is {@link java.lang.Object}. If their superclass is another class, they are
   *   added to the map for classes waiting on the initialization of their superclass.
   *   As a special case, classes without a super class (e.g. java.lang.Object)
   *   are compiled immediately. Annotations and interfaces will be added to the
   *   compilation queue although they aren't considered to have super classes
   *   in our current implementation .</li>
   *   <li>arrays are added to the compilation queue immediately because their
   *   instantiation depends only on java.lang.Object</li>
   * </ol>
   *
   * @param type the type to process
   */
  public void addType(RVMType type) {
    if (type.isPrimitiveType() || type.isUnboxedType()) {
      type.instantiate();
      if (DEBUG) {
        say("[CompilationOrder] instantiated " + type + " immediately!");
      }
    } else if (type.isArrayType()) {
      addToCompilationQueueAtStart(type);
      if (DEBUG) {
        say("[CompilationOrder] added " + type +
            " (should be array) to queue!");
      }
    } else {
      verifyTypeIsClassType(type);
      RVMClass classType = type.asClass();
      RVMClass superClass = classType.getSuperClass();
      // Note: can't use isJavaLangObject (or similar) here because the fields for
      // that are only available after static initializers have run
      if (superClass == null) {
        if (classType.isInterface()) {
          // Interface or annotation: doesn't need to be compiled now.
          addToCompilationQueueAtStart(type);
          if (DEBUG) {
            say("[CompilationOrder] instantiated " + type +
                " (should be interface or annotation to queue");
          }
        } else {
          // java.lang.Object is the root of the hierarchy and must be compiled
          // before all other reference types, including arrays. It doesn't make
          // sense to make other threads wait on this during compilation, so we
          // compile it immediately.
          type.instantiate();
          if (DEBUG) {
            say("[CompilationOrder] instantiated " + type +
                " (should be java.lang.Object) immediately!");
          }
        }
      } else if (superClass.getSuperClass() == null) {
        // the superclass of the class is java.lang.Object, which we compile
        // immediately. Therefore, the class has no dependencies.
        addToCompilationQueueAtEnd(classType);
        if (DEBUG) {
          say("[CompilationOrder] added " + type + " with superclass " +
              superClass + " (should be java.lang.Object) to queue!");
        }
      } else {
        markAsWaitingForSuperClass(type, superClass);
        if (DEBUG) {
          say("[CompilationOrder] added " + type + " with superclass " +
              superClass + " to map!");
        }
      }
    }
  }

  private void markAsWaitingForSuperClass(RVMType type, RVMClass superClass) {
    Collection<RVMType> collection = waitingForSuperclassToInitialize.get(superClass);
    if (collection == null) {
      int subclassCountEstimate = estimateNumberOfSubclasses(superClass);
      collection = new Vector<RVMType>(subclassCountEstimate);
      waitingForSuperclassToInitialize.put(superClass, collection);
    }
    collection.add(type);
    neededWorkersCount.incrementAndGet();
    missingSuperclasses.add(superClass);
  }

  private void addToCompilationQueueAtEnd(RVMClass classType) {
    compilationDeque.addLast(classType);
    neededWorkersCount.incrementAndGet();
  }

  private void addToCompilationQueueAtStart(RVMType type) {
    compilationDeque.addFirst(type);
    neededWorkersCount.incrementAndGet();
  }

  private int estimateNumberOfSubclasses(RVMClass aClass) {
    if (aClass.isInterface()) {
      return INTERFACE_SUBCLASS_COUNT_ESTIMATE;
    } else if (aClass.getSuperClass() == null) {
      return OBJECT_SUBCLASS_COUNT_ESTIMATE;
    } else {
      return CLASS_SUBCLASS_COUNT_ESTIMATE;
    }
  }

  private void verifyTypeIsClassType(RVMType type) {
    if (VM.VerifyAssertions) {
      boolean classType = type.isClassType();
      if (!classType) {
        String msg = "Unhandled case: expected a class type but found " +
            type;
        VM._assert(VM.NOT_REACHED, msg);
      }
    }
  }

  public int getCountOfNeededWorkers() {
    return neededWorkersCount.get();
  }

  public void instantiationComplete(RVMType instantiatedClass) {
    Collection<RVMType> classesToCompile = waitingForSuperclassToInitialize.remove(instantiatedClass);
    if (classesToCompile == null) {
      if (DEBUG) {
        say("[CompilationOrder] Class " + instantiatedClass +
            " has finished compilation and has no subclasses to compile!");
      }
      return;
    }
    compilationDeque.addAll(classesToCompile);
    if (DEBUG) {
      say("[CompilationOrder] added " + classesToCompile + " to deque because " +
          instantiatedClass + " finished compilation!");
    }
  }

  /**
   * Makes sure that all superclasses are instantiated by detecting missed classes.
   * <p>
   * If the code just called {@code instantiate()} on every class, all superclasses would
   * be compiled automatically. However, the code is explicitly managing superclasses and
   * so must take care to enumerate all superclasses.
   */
  public void fixUpMissingSuperClasses() {
    int round = 1;
    while (!missingSuperclasses.isEmpty()) {
      if (DEBUG) {
        say("[CompilationOrder] fixing up superclasses, round " + round);
        say("[CompilationOrder] fixing up superclasses, remaining classes at start " +
            missingSuperclasses);
      }
      // Everything that's on the queue is not missing and won't need to be processed
      HashSet<RVMType> dequeTypes = new HashSet<RVMType>(compilationDeque);
      missingSuperclasses.removeAll(dequeTypes);

      // Everything that will be initialized when its superclass is compiled won't be
      // missing either
      HashSet<RVMType> willBeAddedToDeque = new HashSet<RVMType>();
      for (Collection<RVMType> c : waitingForSuperclassToInitialize.values()) {
        willBeAddedToDeque.addAll(c);
      }
      missingSuperclasses.removeAll(willBeAddedToDeque);
      // The rest will need to be processed in order to fix up missing superclasses
      HashSet<RVMType> toAddAsTypes = new HashSet<RVMType>(missingSuperclasses);
      for (RVMType type : toAddAsTypes) {
        addType(type);
      }
      round++;
    }

    assertThatCountOfWorkersMatchesCountOfClasses();

    if (DEBUG) {
      say("[CompilationOrder] fixing up superclasses finished!");
    }
  }

  private void assertThatCountOfWorkersMatchesCountOfClasses() {
    if (VM.VerifyAssertions) {
      int totalClassesToCompile = 0;
      totalClassesToCompile += compilationDeque.size();
      for (Collection<RVMType> coll : waitingForSuperclassToInitialize.values()) {
        totalClassesToCompile += coll.size();
      }
      int workersCount = neededWorkersCount.get();
      if (totalClassesToCompile != workersCount) {
        say("total classes to compile: " + totalClassesToCompile);
        say("workers count: " + workersCount);
        VM._assert(NOT_REACHED);
      }
    }
  }

}
