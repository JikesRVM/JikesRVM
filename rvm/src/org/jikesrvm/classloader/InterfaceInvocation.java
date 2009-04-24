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
package org.jikesrvm.classloader;

import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.ArchitectureSpecific.InterfaceMethodConflictResolver;
import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.IMT;
import org.jikesrvm.objectmodel.ITable;
import org.jikesrvm.objectmodel.ITableArray;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.objectmodel.TIBLayoutConstants;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.pragma.Entrypoint;

/**
 * Runtime system mechanisms and data structures to implement interface invocation.
 *
 * We support two mechanisms:
 * <pre>
 *   IMT-based (Alpern, Cocchi, Fink, Grove, and Lieber OOPSLA'01).
 *   ITable-based (searched at dispatch time with 1 entry move-to-front cache(
  * </pre>
 */
public class InterfaceInvocation implements TIBLayoutConstants, SizeConstants {

  /*
   * PART I: runtime routines to implement the invokeinterface bytecode.
   *         these routines are called from the generated code
   *         as part of the interface invocation sequence.
   */

  /**
   * Resolve an interface method call.
   * This routine is never called by the IMT-based dispatching code.
   * It is only called for directly indexed ITables when the table
   * index was unknown at compile time (ie the target Interface was not loaded).
   *
   * @param target object to which interface method is to be applied
   * @param mid id of the MemberReference for the target interface method.
   * @return machine code corresponding to desired interface method
   */
  @Entrypoint
  public static CodeArray invokeInterface(Object target, int mid) throws IncompatibleClassChangeError {

    MethodReference mref = MemberReference.getMemberRef(mid).asMethodReference();
    RVMMethod sought = mref.resolveInterfaceMethod();
    RVMClass I = sought.getDeclaringClass();
    RVMClass C = Magic.getObjectType(target).asClass();
    if (VM.BuildForITableInterfaceInvocation) {
      TIB tib = C.getTypeInformationBlock();
      ITable iTable = findITable(tib, I.getInterfaceId());
      return iTable.getCode(getITableIndex(I, mref.getName(), mref.getDescriptor()));
    } else {
      if (!RuntimeEntrypoints.isAssignableWith(I, C)) throw new IncompatibleClassChangeError();
      RVMMethod found = C.findVirtualMethod(sought.getName(), sought.getDescriptor());
      if (found == null) throw new IncompatibleClassChangeError();
      return found.getCurrentEntryCodeArray();
    }
  }

  /**
   * Return a reference to the itable for a given class, interface pair
   * We might not have created the iTable yet, in which case we will do that and then return it.
   *
   * @param tib the TIB for the class
   * @param id interface id of the interface sought (NOT dictionary id!!)
   * @return iTable for desired interface
   */
  @Entrypoint
  public static ITable findITable(TIB tib, int id) throws IncompatibleClassChangeError {
    ITableArray iTables = tib.getITableArray();
    // Search for the right ITable
    RVMType I = RVMClass.getInterface(id);
    if (iTables != null) {
      // check the cache at slot 0
      ITable iTable = iTables.get(0);
      if (iTable.isFor(I)) {
        return iTable; // cache hit :)
      }

      // cache miss :(
      // Have to search the 'real' entries for the iTable
      for (int i = 1; i < iTables.length(); i++) {
        iTable = iTables.get(i);
        if (iTable.isFor(I)) {
          // found it; update cache
          iTables.set(0, iTable);
          return iTable;
        }
      }
    }

    // Didn't find the itable, so we don't yet know if
    // the class implements the interface. :(((
    // Therefore, we need to establish that and then
    // look for the iTable again.
    RVMClass C = (RVMClass) tib.getType();
    if (!RuntimeEntrypoints.isAssignableWith(I, C)) throw new IncompatibleClassChangeError();
    synchronized (C) {
      installITable(C, (RVMClass) I);
    }
    ITable iTable = findITable(tib, id);
    if (VM.VerifyAssertions) VM._assert(iTable != null);
    return iTable;
  }

  /**
   * <code>mid</code> is the dictionary id of an interface method we are trying to invoke
   * <code>RHStib</code> is the TIB of an object on which we are attempting to invoke it.
   *
   * We were unable to resolve the member reference at compile time.
   * Therefore we must resolve it now and then call invokeinterfaceImplementsTest
   * with the right LHSclass.
   *
   * @param mid     Dictionary id of the {@link MemberReference} for the target interface method.
   * @param rhsObject  The object on which we are attempting to invoke the interface method
   */
  @Entrypoint
  public static void unresolvedInvokeinterfaceImplementsTest(int mid, Object rhsObject)
      throws IncompatibleClassChangeError {
    RVMMethod sought = MemberReference.getMemberRef(mid).asMethodReference().resolveInterfaceMethod();
    RVMClass LHSclass = sought.getDeclaringClass();
    if (!LHSclass.isResolved()) {
      LHSclass.resolve();
    }
    /* If the object is not null, ensure that it implements the interface.
     * If it is null, then we return to our caller and let them raise the
     * null pointer exception when they attempt to get the object's TIB so
     * they can actually make the interface call.
     */
    if (rhsObject != null) {
      TIB RHStib = ObjectModel.getTIB(rhsObject);
      if (LHSclass.isInterface() && DynamicTypeCheck.instanceOfInterface(LHSclass, RHStib)) return;
      // Raise an IncompatibleClassChangeError.
      throw new IncompatibleClassChangeError();
    }
  }

  /*
  * PART II: Code to initialize the interface dispatching data structures.
  *          Called during the instantiate step of class loading.
  *          Preconditions:
  *            (1) the caller has the lock on the RVMClass object
  *                whose data structures and being initialized.
  *            (2) the VMT for the class contains valid code.
  */

  /**
   * Main entrypoint called from RVMClass.instantiate to
   * initialize the interface dispatching data structures for
   * the given class.
   *
   * @param klass the RVMClass to initialize the dispatch structures for.
   */
  public static void initializeDispatchStructures(RVMClass klass) {
    // if klass is abstract, we'll never use the dispatching structures.
    if (klass.isAbstract()) return;
    RVMClass[] interfaces = klass.getAllImplementedInterfaces();
    if (interfaces.length != 0) {
      if (VM.BuildForIMTInterfaceInvocation) {
        IMTDict d = buildIMTDict(klass, interfaces);
        populateIMT(klass, d);
      }
    }
  }

  /**
   * Build up a description of the IMT contents for the given class.
   * NOTE: this structure is only used during class loading, so
   *       we don't have to worry about making it space efficient.
   *
   * @param klass the RVMClass whose IMT we are going to build.
   * @return an IMTDict that describes the IMT we need to build for the class.
   */
  private static IMTDict buildIMTDict(RVMClass klass, RVMClass[] interfaces) {
    IMTDict d = new IMTDict(klass);
    for (RVMClass i : interfaces) {
      RVMMethod[] interfaceMethods = i.getDeclaredMethods();
      for (RVMMethod im : interfaceMethods) {
        if (im.isClassInitializer()) continue;
        if (VM.VerifyAssertions) VM._assert(im.isPublic() && im.isAbstract());
        InterfaceMethodSignature sig = InterfaceMethodSignature.findOrCreate(im.getMemberRef());
        RVMMethod vm = klass.findVirtualMethod(im.getName(), im.getDescriptor());
        // NOTE: if there is some error condition, then we are playing a dirty trick and
        //       pretending that a static method of RuntimeEntrypoints is a virtual method.
        //       Since the methods in question take no arguments, we can get away with this.
        if (vm == null || vm.isAbstract()) {
          vm = Entrypoints.raiseAbstractMethodError;
        } else if (!vm.isPublic()) {
          vm = Entrypoints.raiseIllegalAccessError;
        }
        d.addElement(sig, vm);
      }
    }
    return d;
  }

  /**
   * Populate an indirect IMT for C using the IMTDict d
   */
  private static void populateIMT(RVMClass klass, IMTDict d) {
    TIB tib = klass.getTypeInformationBlock();
    IMT IMT = MemoryManager.newIMT();
    klass.setIMT(IMT);
    d.populateIMT(klass, tib, IMT);
    tib.setImt(IMT);
  }

  /**
   * Build and install an iTable for the given class interface pair
   * (used for iTable miss on searched iTables).
   */
  private static void installITable(RVMClass C, RVMClass I) {
    TIB tib = C.getTypeInformationBlock();
    ITableArray iTables = tib.getITableArray();

    if (iTables == null) {
      iTables = MemoryManager.newITableArray(2);
      tib.setITableArray(iTables);
    } else {
      for(int i=0; i < iTables.length(); i++) {
        if (iTables.get(i).isFor(I)) {
          return; // some other thread just built the iTable
        }
      }
      ITableArray tmp = MemoryManager.newITableArray(iTables.length() + 1);
      for(int i=0; i < iTables.length(); i++) {
        tmp.set(i, iTables.get(i));
      }
      iTables = tmp;
      tib.setITableArray(iTables);
    }
    if (VM.VerifyAssertions) VM._assert(iTables.get(iTables.length() - 1) == null);
    ITable iTable = buildITable(C, I);
    iTables.set(iTables.length() - 1, iTable);
    // iTables[0] is a move to front cache; fill it here so we can
    // assume it always contains some iTable.
    iTables.set(0, iTable);
  }

  /**
   * Build a single ITable for the pair of class C and interface I
   */
  private static ITable buildITable(RVMClass C, RVMClass I) {
    RVMMethod[] interfaceMethods = I.getDeclaredMethods();
    TIB tib = C.getTypeInformationBlock();
    ITable iTable = MemoryManager.newITable(interfaceMethods.length + 1);
    iTable.set(0, I);
    for (RVMMethod im : interfaceMethods) {
      if (im.isClassInitializer()) continue;
      if (VM.VerifyAssertions) VM._assert(im.isPublic() && im.isAbstract());
      RVMMethod vm = C.findVirtualMethod(im.getName(), im.getDescriptor());
      // NOTE: if there is some error condition, then we are playing a dirty trick and
      //       pretending that a static method of RuntimeEntrypoints is a virtual method.
      //       Since the methods in question take no arguments, we can get away with this.
      if (vm == null || vm.isAbstract()) {
        vm = Entrypoints.raiseAbstractMethodError;
      } else if (!vm.isPublic()) {
        vm = Entrypoints.raiseIllegalAccessError;
      }
      if (vm.isStatic()) {
        vm.compile();
        iTable.set(getITableIndex(I, im.getName(), im.getDescriptor()), vm.getCurrentEntryCodeArray());
      } else {
        iTable.set(getITableIndex(I, im.getName(), im.getDescriptor()), tib.getVirtualMethod(vm.getOffset()));
      }
    }
    return iTable;
  }

  /*
  * PART III: Supporting low-level code for manipulating IMTs and ITables
  */

  /**
   * Return the index of the interface method m in the itable
   */
  public static int getITableIndex(RVMClass klass, Atom mname, Atom mdesc) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildForITableInterfaceInvocation);
    if (VM.VerifyAssertions) VM._assert(klass.isInterface());
    RVMMethod[] methods = klass.getDeclaredMethods();
    for (int i = 0; i < methods.length; i++) {
      if (methods[i].getName() == mname && methods[i].getDescriptor() == mdesc) {
        return i + 1;
      }
    }
    return -1;
  }

  /**
   * If there is an an IMT or ITable entry that contains
   * compiled code for the argument method, then update it to
   * contain the current compiled code for the method.
   *
   * @param klass the RVMClass who's IMT/ITable is being reset
   * @param m the method that needs to be updated.
   */
  public static void updateTIBEntry(RVMClass klass, RVMMethod m) {
    TIB tib = klass.getTypeInformationBlock();
    if (VM.BuildForIMTInterfaceInvocation) {
      RVMMethod[] map = klass.noIMTConflictMap;
      if (map != null) {
        for (int i = 0; i < IMT_METHOD_SLOTS; i++) {
          if (map[i] == m) {
            IMT imt = tib.getImt();
            imt.set(i, m.getCurrentEntryCodeArray());
            return; // all done -- a method is in at most 1 IMT slot
          }
        }
      }
    } else if (VM.BuildForITableInterfaceInvocation) {
      if (tib.getITableArray() != null) {
        ITableArray iTables = tib.getITableArray();
        Atom name = m.getName();
        Atom desc = m.getDescriptor();
        for (int i=0; i< iTables.length(); i++) {
          ITable iTable = iTables.get(i);
          if (iTable != null) {
            RVMClass I = iTable.getInterfaceClass();
            RVMMethod[] interfaceMethods = I.getDeclaredMethods();
            for (RVMMethod im : interfaceMethods) {
              if (im.getName() == name && im.getDescriptor() == desc) {
                iTable.set(getITableIndex(I, name, desc), m.getCurrentEntryCodeArray());
              }
            }
          }
        }
      }
    }
  }

  /*
   * Helper class used for IMT construction
   */
  private static final class IMTDict {
    private final RVMClass klass;
    private final Link[] links;

    IMTDict(RVMClass c) {
      klass = c;
      links = new Link[IMT_METHOD_SLOTS];
    }

    // Convert from the internally visible IMTOffset to an index
    // into my internal data structure.
    private int getIndex(InterfaceMethodSignature sig) {
      int idx = sig.getIMTOffset().toInt() >> LOG_BYTES_IN_ADDRESS;
      return idx;
    }

    // count the number of signatures in the given IMT slot
    private int populationCount(int index) {
      Link p = links[index];
      int count = 0;
      while (p != null) {
        count++;
        p = p.next;
      }
      return count;
    }

    private RVMMethod getSoleTarget(int index) {
      if (VM.VerifyAssertions) VM._assert(populationCount(index) == 1);
      return links[index].method;
    }

    // Add an element to the IMT dictionary (does nothing if already there)
    public void addElement(InterfaceMethodSignature sig, RVMMethod m) {
      int index = getIndex(sig);
      Link p = links[index];
      if (p == null || p.signature.getId() > sig.getId()) {
        links[index] = new Link(sig, m, p);
      } else {
        Link q = p;
        while (p != null && p.signature.getId() <= sig.getId()) {
          if (p.signature.getId() == sig.getId()) return; // already there so nothing to do.
          q = p;
          p = p.next;
        }
        q.next = new Link(sig, m, p);
      }
    }

    // populate the
    public void populateIMT(RVMClass klass, TIB tib, IMT imt) {
      for (int slot = 0; slot < links.length; slot++) {
        int count = populationCount(slot);
        if (count == 0) {
          Entrypoints.raiseAbstractMethodError.compile();
          set(tib, imt, slot, Entrypoints.raiseAbstractMethodError.getCurrentEntryCodeArray());
        } else if (count == 1) {
          RVMMethod target = getSoleTarget(slot);
          if (target.isStatic()) {
            target.compile();
            set(tib, imt, slot, target.getCurrentEntryCodeArray());
          } else {
            set(tib, imt, slot, tib.getVirtualMethod(target.getOffset()));
            if (klass.noIMTConflictMap == null) {
              klass.noIMTConflictMap = new RVMMethod[IMT_METHOD_SLOTS];
            }
            klass.noIMTConflictMap[slot] = target;
          }
        } else {
          RVMMethod[] targets = new RVMMethod[count];
          int[] sigIds = new int[count];
          int idx = 0;
          for (Link p = links[slot]; p != null; idx++, p = p.next) {
            targets[idx] = p.method;
            sigIds[idx] = p.signature.getId();
          }
          CodeArray conflictResolutionStub = InterfaceMethodConflictResolver.createStub(sigIds, targets);
          klass.addCachedObject(Magic.codeArrayAsObject(conflictResolutionStub));
          set(tib, imt, slot, conflictResolutionStub);
        }
      }
    }

    private void set(TIB tib, IMT imt, int extSlot, CodeArray value) {
      imt.set(extSlot, value);
    }

    private static final class Link {
      final InterfaceMethodSignature signature;
      final RVMMethod method;
      Link next;

      Link(InterfaceMethodSignature sig, RVMMethod m, Link n) {
        signature = sig;
        method = m;
        next = n;
      }
    }
  }
}
