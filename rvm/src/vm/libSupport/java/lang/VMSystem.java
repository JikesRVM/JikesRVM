/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import com.ibm.JikesRVM.VM_Statics;
import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Field;

import com.ibm.JikesRVM.librarySupport.SystemSupport;

import java.lang.reflect.Field;
import java.util.Properties;
import java.io.*;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
 */
final class VMSystem
{

  static void arraycopy(Object src, int srcStart, Object dest, int destStart, int len) {
      SystemSupport.arraycopy(src,srcStart,dest,destStart,len);
  }
    
  static int identityHashCode(Object o) {
      return SystemSupport.getDefaultHashCode(o);
  }

  static boolean isWordsBigEndian() {
      //-#if RVM_FOR_IA32
      return true;
      //-#else
      return false;
      //-#endif
  }

  public static long currentTimeMillis() {
      return SystemSupport.currentTimeMillis();
  }

  static void setIn(InputStream in) {
      try {
	  VM_Field inField = 
	      ((VM_Class)JikesRVMSupport.getTypeForClass(System.class))
	          .findDeclaredField( 
		      VM_Atom.findOrCreateUnicodeAtom("in"), 
		      VM_Atom.findOrCreateUnicodeAtom("Ljava/io/InputStream;"));

	  inField.setObjectValue(null, in);
      } catch (Exception e) {
	  throw new Error( e.toString() );
      }
  }

  static void setOut(PrintStream out) {
      try {
	  VM_Field outField = 
	      ((VM_Class)JikesRVMSupport.getTypeForClass(System.class))
	          .findDeclaredField( 
		      VM_Atom.findOrCreateUnicodeAtom("out"), 
		      VM_Atom.findOrCreateUnicodeAtom("Ljava/io/PrintStream;"));

	  outField.setObjectValue(null, out);
      } catch (Exception e) {
	  throw new Error( e.toString() );
      }
  }

  static void setErr(PrintStream err) {
      try {
	  VM_Field errField = 
	      ((VM_Class)JikesRVMSupport.getTypeForClass(System.class))
	          .findDeclaredField( 
		      VM_Atom.findOrCreateUnicodeAtom("err"), 
		      VM_Atom.findOrCreateUnicodeAtom("Ljava/io/PrintStream;"));

	  errField.setObjectValue(null, err);
      } catch (Exception e) {
	  throw new Error( e.toString() );
      }
  }

    static InputStream makeStandardInputStream() { return null; }

    static PrintStream makeStandardOutputStream() { return null; }

    static PrintStream makeStandardErrorStream() { return null; }

    static String internString(String string) {
	try {
	    return (String)
		VM_Statics.getSlotContentsAsObject( 
		    VM_Statics.findOrCreateStringLiteral( 
			VM_Atom.findOrCreateUnicodeAtom( string ) ) );
	} catch (UTFDataFormatException ex) {
	    throw new InternalError( ex.toString() );
	}
    }

}
