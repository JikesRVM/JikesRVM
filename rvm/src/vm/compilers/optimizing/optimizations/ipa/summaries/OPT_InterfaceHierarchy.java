/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This class holds, for each interface, the set of initialized classes
 * that implement the interface.
 *
 * @author Stephen Fink
 */
public class OPT_InterfaceHierarchy {

  private static boolean ENABLED = true;

  /**
   * a mapping from VM_Class (an interface) to a set of classes that
   * claim to implement this interface.
   */
  private static JDK2_HashMap interfaceMapping = new JDK2_HashMap();

  /**
   * Notify this dictionary that a new class has been initialized. 
   * This method updates the dictionary to record the interface
   * implementors.
   */
  public static void notifyClassInitialized(VM_Class c) {
    if (!ENABLED) return;

    VM_Class klass = c;
    if (klass.isInterface()) return;

    while (klass != null) {
      VM_Class[] declaredInterfaces = klass.getDeclaredInterfaces();
      for (int i=0; i<declaredInterfaces.length; i++) {
        VM_Class I = declaredInterfaces[i];
        noteImplements(klass,I);
      }
      klass = klass.getSuperClass();
    }
  }

  /**
   * Note that class c implements interface I;
   */
  private static void noteImplements(VM_Class c, VM_Class I) {
    JDK2_HashSet implementsSet = findOrCreateSet(I);
    implementsSet.add(c);
  }

  /**
   * Return the set of classes that implement a given interface. Create a
   * set if none found.
   */
  private static JDK2_HashSet findOrCreateSet(VM_Class I) {
    JDK2_HashSet set = (JDK2_HashSet)interfaceMapping.get(I);
    if (set == null) {
      set = new JDK2_HashSet(3);
      interfaceMapping.put(I,set);
    }
    return set;
  }

  /**
   * Return the set of all classes known to implement interface I.
   */
  private static JDK2_HashSet allImplementors(VM_Class I) {
    // get the set of classes registered as implementing I
    JDK2_HashSet result = findOrCreateSet(I);
    
    // also add any classes that implement a sub-interface of I.
    VM_Class[] subI = I.getSubClasses();
    // need to do this kludge to avoid recursive concurrent modification
    for (int i=0; i<subI.length; i++) {
      result.addAll(allImplementors(subI[i]));
    }

    // also add any sub-classes of these classes.
    // need to cache additions to avoid modifying the set while iterating
    JDK2_HashSet toAdd = new JDK2_HashSet(5);
    for (JDK2_Iterator i = result.iterator(); i.hasNext(); ) {
      VM_Class c = (VM_Class)i.next();
      toAdd.addAll(allSubClasses(c));
    }
    result.addAll(toAdd);

    return result;
  }
  /**
   * Return the set of all classes known to extend C
   */
  private static JDK2_HashSet allSubClasses(VM_Class C) {
    JDK2_HashSet result = new JDK2_HashSet(5);
    
    // also add any classes that implement a sub-interface of I.
    VM_Class[] subC = C.getSubClasses();
    for (int i=0; i<subC.length; i++) {
      result.add(subC[i]);
      result.addAll(allSubClasses(subC[i]));
    }

    return result;
  }

  /**
   * If, in the current class hierarchy, there is exactly one method that
   * defines the interface method foo, then return the unique
   * implementation.  If there is not a unique implementation, return
   * null.
   */
  public static VM_Method getUniqueImplementation(VM_Method foo) {
    VM_Class I = foo.getDeclaringClass();

    JDK2_HashSet classes = allImplementors(I);
    VM_Method firstMethod = null;
    VM_Atom name = foo.getName();
    VM_Atom desc = foo.getDescriptor();

    for (JDK2_Iterator i = classes.iterator(); i.hasNext(); ) {
      VM_Class klass = (VM_Class)i.next();
      VM_Method m = klass.findDeclaredMethod(name,desc);
      if (firstMethod == null) 
        firstMethod = m;

      if (m != firstMethod)
        return null;
    }
    return firstMethod;
  }
}
