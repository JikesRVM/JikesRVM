/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

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
	  Field inField = Class.forName("java.lang.System").getField("in");
	  inField.set(null, in);
      } catch (Exception e) {
	  throw new Error( e.toString() );
      }
  }

  static void setOut(PrintStream out) {
      try {
	  Field outField = Class.forName("java.lang.System").getField("out");
	  outField.set(null, out);
      } catch (Exception e) {
	  throw new Error( e.toString() );
      }
  }

  static void setErr(PrintStream err) {
      try {
	  Field errField = Class.forName("java.lang.System").getField("err");
	  errField.set(null, err);
      } catch (Exception e) {
	  throw new Error( e.toString() );
      }
  }
}
