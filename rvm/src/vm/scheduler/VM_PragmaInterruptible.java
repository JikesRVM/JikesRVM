/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * A pragma that can be used to declare that a 
 * particular method is interruptible.  
 * Used to override the class-wide pragma
 * implied by implementing VM_Uninteruptible.
 * 
 * @Dave Grove
 */
public class VM_PragmaInterruptible extends VM_PragmaException {
  private static final VM_Class vmClass = getVMClass(VM_PragmaInterruptible.class);
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(vmClass, method);
  }
}
