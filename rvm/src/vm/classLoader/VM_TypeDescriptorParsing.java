/* -*-coding: iso-8859-1-*-
 * (C) Copyright IBM Corp. 2003
 */
//$Id$



package com.ibm.JikesRVM.classloader;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/** <p>A Java class for parsing type descriptors and class names.  The class
     is <code>abstract</code> to eliminate the temptation to instantiate it,
     since it contains only static methods.

    <p>There are five similar kinds of descriptors and names that we have to
     deal with.  We don't have methods for parsing all of them.

    <p> In this documentation, I will refer to <i>The Java Native Interface
     Programmer's Guide and Specification</i> as the <i>JNI Guide</i>.

    <p> Some of the types I discuss below are described in §12.3 of the JNI
     Guide.

   <dl>
    <dt>Fully-qualified class names and fully-qualified interface names</dt>
    <dd>These are the dot-separated names, such as "java.lang.String" or
     "java.util.Map".
     <p>We can validate these with the static method #isJavaClassName(String)
      in this class. 
    </dd>
    
    <dt>JNI Class Descriptor (including array classes),<br> These include the
     internal Form of fully-qualified class names 
     and internal form of fully-qualified interface names</dt>
    <dd>These 
    <dd>&ldquo;It can be derived from a fully qualified class or interface
     name as defined in The Java Language Specification by substituting the "."
     character with the "/" character.  For example, the JNI class descriptor
     for <code>java.lang.String</code> is "<code>java/lang/String</code>&rdquo;
     Array classes are formed using the "[" character followed by the field
     descriptor of the element type.  The class descrpitor for "int[]" is "[I".
     <P>We do not have an interface for parsing these right now. 
    </dd>
    
    <dt>Field Descriptors</dt>
    <dd>Described in §12.3.3 of the JNI Guide.
     Examples: 
       <ul> 
        <li>"Z" for boolean<br>
        <li> "B" for byte
        <li>"D" for double
        <li>"Ljava/lang/String;" for java.lang.String
        <li> "[I" for int[].
       </ul>
    </dd>
    
    <dt>Method Descriptors</dt>
    <dd>Described in §12.3.4 of the JNI guide.  To quote:

     <blockquote>

       Method Descriptors are formed by placing the field descriptors of all
       argument types in a pair of parentheses, and following that by the
       field descriptor of the return type.  There are no spaces or other
       separator characters between the argument types.  "<code>V</code>" is
       used to denote the <code>void</code> method return type.  Constructors
       use "<code>V</code>" as their return type and use "<code>&lt;init&gt;"
       as their name.
     </blockquote>
    
     Example: The method with signature "<code>byte f(int i, String s)</code>"
     has the Method Descriptor "<code>(ILjava/lang/String;)B</code>"

    <dt>VM_TypeReference names</dt>
    <dd>Inside Jikes RVM, we use the VM_TypeReference class to represent the
    reference in some class file to some type (class, interface, primitive, or
    array).  We also use them to represent Void (VM_TypeReference.Void).</dd>
    VM_TypeReference names are just field descriptors plus "V".
   </dl>

    @author Steven Augart
    @date July 31, 2003 
*/

public abstract class VM_TypeDescriptorParsing 
  implements VM_ClassLoaderConstants // gets us constants that we use in
                                     // isJavaPrimitive 
{
  /** Is the string <code>s</code> a legal name for a Java class or interface?
   * This will take either fully-qualified names or names that are not fully
   * qualified. 
   * <p>
   * @param s The string to check for whether it's a valid name for a Java
   *          class.  This is a string of the form, for example:
   * "<code>java.lang.String</code>" 
   * @return <code>true</code> if <code>s</code> is valid, <code>false</code>
   * otherwise.
   *
   * <p>

   * <small><b>Implementation Question for wiser heads than mine:</b>
   * Would it be more efficient for me to convert this to a <code>char</code>
   * array? 
   * That's the way the example in <i>The Java Class Libraries</i> for
   * <code>Character.isJavaIdentifier<i>*</i>()</code> is written.  Or is the
   * <code>String.charAt()</code> method inexpensive?</small> */
  public static boolean isJavaClassName(String s) 
    throws VM_PragmaInterruptible 
  {
    boolean identStart = true;  // pretend we just saw a .
    for (int i = 0; i < s.length(); ++i) {
      char c = s.charAt(i);
      if (identStart) {
        if (! Character.isJavaIdentifierStart(c))
          return false;         // failure to match identifier start.
        identStart = false;     // on to the next one.
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

    boolean identStart = true;  // pretend we just saw a separator
    for (int i = first; i <= last; ++i) {
      char c = val[i];
      if (identStart) {
        if (! Character.isJavaIdentifierStart(c))
          return false;         // failure to match identifier start.
        identStart = false;     // on to the next one.
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

    if (isJavaPrimitive(val[i]))        {
      // A primitive should be just 1 char long
      if ( i != val.length - 1)
        // if this isn't the last character, scream.
        malformed("nothing should follow the primitive typecode '" 
                  + Character.toString(val[i]) + "'", s);
      return;                   // otherwise all is well.
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

  // These are test routines you can use to do unit testing on the methods in
  // this class::
  //  // Test isJavaClassName()
//   public static void main(String[] args) {
//     for (int i = 0; i < args.length; ++i) {
//       System.out.println(args[i] + " is " 
//                       + (TypeDescriptorParsing.isJavaClassName(args[i]) ? "" : "NOT " ) + "a valid Java class name.");
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
