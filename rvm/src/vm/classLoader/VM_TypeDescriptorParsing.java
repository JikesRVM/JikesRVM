/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$

/** A Class for parsing type descriptors.
 * @author Steven Augart
 * @date July 31, 2003 */


package com.ibm.JikesRVM.classloader;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

public class VM_TypeDescriptorParsing 
  implements VM_ClassLoaderConstants // gets us constants.
{
  // Vacuous.  Keeps us from creating a default constructor.  We don't need to
  // instantiate this class, since it has only static methods.
  private VM_TypeDescriptorParsing() {};
  
  /** Is the string @param s a valid name for a Java class? 
   *
   * Would it be more efficient for me to convert this to a char array?
   * That's the way the example in The Java Class Libraries for
   * Character.isJavaIdentifier*()  is worded.  Or is the String.charAt()
   * method not too expensive? */
  public static boolean isJavaClassName(String s) 
    throws VM_PragmaInterruptible 
  {
    boolean identStart = true;	// pretend we just saw a .
    for (int i = 0; i < s.length(); ++i) {
      char c = s.charAt(i);
      if (identStart) {
	if (! Character.isJavaIdentifierStart(c))
	  return false;		// failure to match identifier start.
	identStart = false;	// on to the next one.
	continue;
      }
      if (c == '.') {
	identStart = true;
	continue;
      }
      /* We have a character that is not the first one of a Java identifier */
      if (!Character.isJavaIdentifierPart(c))
	return false;
      /* And on we go around the loop */
    }
    // Must not finish by needing the start of another identifier.
    return ! identStart;
  }

  /** Is this the internal form of a Java class name?  (the one with the "/"
   * instead of the "." separating components?)
   * Takes a character array (i.e., an exploded string) and the indices of the
   * first and last characters of the array that are to be checked. */
  public static boolean isJavaClassNameInternalForm(
		char val[], int first, int last) 
  {
    if (val[first++] != ClassTypeCode) // the L
      return false;
    if (val[last--] != ';')
      // malformed("a class ('L') must end in a ';'");
      return false;

    boolean identStart = true;	// pretend we just saw a separator
    for (int i = first; i <= last; ++i) {
      char c = val[i];
      if (identStart) {
	if (! Character.isJavaIdentifierStart(c))
	  return false;		// failure to match identifier start.
	identStart = false;	// on to the next one.
	continue;
      }
      if (c == '/') {
	identStart = true;
	continue;
      }
      /* We have a character that is not the first one of a Java identifier */
      if (!Character.isJavaIdentifierPart(c))
	return false;
      /* And on we go around the loop */
    }
    // Must not finish by needing the start of another identifier.
    return ! identStart;
  }

  public static boolean isValidTypeDescriptor(String s) {
    try {
      validateAsTypeDescriptor(s);
      return true;
    } catch (IllegalArgumentException iae) {
      return false;
    }
  }

  public static boolean isValidTypeDescriptor(VM_Atom a) {
    try {
      validateAsTypeDescriptor(a);
      return true;
    } catch (IllegalArgumentException iae) {
      return false;
    }
  }

  public static void validateAsTypeDescriptor(VM_Atom a) 
    throws IllegalArgumentException,
	   VM_PragmaInterruptible
  {
    try {
      // Atoms are always utf-8.
      String s = a.toUnicodeString();
    } catch (java.io.UTFDataFormatException udfe) {
      IllegalArgumentException iae 
	= new IllegalArgumentException("The atom in question does not represent a valid UTF8 string, so it's not a type descriptor.");
      iae.initCause(udfe);
      throw iae;
    }
  }

  /** Validate that the String @param s is a valid type descriptor. 
      @throws IllegalArgumentException if it isn't.
  */
  public static void validateAsTypeDescriptor(String s) 
    throws IllegalArgumentException,
	   VM_PragmaInterruptible
  {
    char val[] = s.toCharArray();
    
    int i = 0;
    if (val.length == 0)
      malformed("is the empty string", s);

    // array dimensions precede the rest.
    while (val[i] == '[') {
      if (++i >= val.length)
	malformed("has just '[' chars", s);
    }
    if (VM.VerifyAssertions)     // logically impossible:
      VM._assert(i < val.length);

    if (val[i] == VoidTypeCode && i != 0)
      malformed("can't have an array of void", s);

    if (isJavaPrimitive(val[i]))	{
      // A primitive should be just 1 char long
      if ( i != val.length - 1)
	// if this isn't the last character, scream.
	malformed("nothing should follow the primitive typecode '" 
		  + Character.toString(val[i]) + "'", s);
      return;			// otherwise all is well.
    }

    // logically impossible:
    if (VM.VerifyAssertions) 
      VM._assert(val[i] != '[' && !isJavaPrimitive(val[i]));
    // All that's left is ClassTypeCode
    if (val[i] != ClassTypeCode)
      malformed("unknown character '"
		+ Character.toString(val[i]) + "'", s);
    if (!isJavaClassNameInternalForm(val, i, val.length - 1))
      malformed("doesn't end with a valid class name in internal form", s);
  }
  
  private static boolean isJavaPrimitive(char c) {
    byte b = (byte ) c;
    if (c != (char) b)
      return false;
    return isJavaPrimitive(b);
  }


  private static boolean isJavaPrimitive(byte b) {
    switch (b) {
    case VoidTypeCode:
    case BooleanTypeCode:
    case ByteTypeCode:
    case ShortTypeCode:
    case CharTypeCode: 
    case IntTypeCode: 
    case LongTypeCode: 
    case FloatTypeCode:
    case DoubleTypeCode:
      return true;
    default:
      return false;
    }
  }

  /** Gripe and throw <code>IllegalArgumentException</code> if we get a
   * malformed type name. */
  private static void malformed(String msg, String typeName) 
    throws IllegalArgumentException
  {
    throw new IllegalArgumentException("Malformed type name"
		+ ((msg == null ) ? "" : ": " + msg) + ": \"" + typeName + "\"");
  }

  // These are test routines you can use to do unit testing on these:
  //  // Test isJavaClassName()
//   public static void main(String[] args) {
//     for (int i = 0; i < args.length; ++i) {
//       System.out.println(args[i] + " is " 
// 			 + (TypeDescriptorParsing.isJavaClassName(args[i]) ? "" : "NOT " ) + "a valid Java class name.");
//     }
//   }

//   // Test validateAsTypeDescriptor()
//   public static void main(String[] args) {
//     for (int i = 0; i < args.length; ++i) {
//       System.out.println("Validating " + args[i] + " as a type descriptor.");
//       validateAsTypeDescriptor(args[i]);
      
//     }
//   }
 
}
