/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Search the VM_Methods for matching names
 * @author Ton Ngo
 */
import java.util.*;

class CommandCompletion {
  // note: 
  // - for VM_Method, toString() gives the whole name with class, signature
  //   and getName.toString() gives just the method name
  // - the list starts at 1, not 0
  private static VM_Method [] methods;   
  private static VM_Class [] classes;

  public CommandCompletion() {
  }

  /**
   * This should be done after the needed classes have been loaded
   * so that the dictionaries will contain stuffs
   */
  public static void init () {
    // get the list of all VM_Methods
    methods = VM_MethodDictionary.getValues();
    if (methods==null)
      System.out.println("ERROR:  cannot get the method list");

    Vector tmp_cls = new Vector(500); 
    // search the entire methods list to get a list of all VM_Class
    for (int i=1; i<methods.length; i++) {
      VM_Class cls = methods[i].getDeclaringClass();
      boolean skip = false;
      for (int j=0; j<tmp_cls.size(); j++) {
	if (cls==((VM_Class)tmp_cls.elementAt(j))) {
	  skip = true;
	  break;
	}
      }      
      if (!skip)
	tmp_cls.addElement(cls);
    }

    classes = new VM_Class[tmp_cls.size()];
    for (int j=0; j<tmp_cls.size(); j++) {
      classes[j] = (VM_Class) tmp_cls.elementAt(j);
    }

  }

  /**
   * Find the first matching class name
   * @param start  the beginning portion of the class name
   * @return the full class name, null if none is found or there are multiple matches
   */
  public static String getClassStartsWith(String start) {
    boolean multiple = false;
    String candidate = null;
    for (int i=0; i<classes.length; i++) {
      String className = classes[i].toString();
      if (className.startsWith(start)) {
	// CommandLine.log.println("getClassStartsWith: found " + className);
	if (candidate==null)
	  candidate = className;
	else 
	  multiple = true;
      }      
    }
    if (!multiple)
      return candidate;
    else
      return null;
  }

  /**
   * Find the first matching method name, with unknown class
   * @param start  the beginning portion of the method name
   * @return the full method name, null if none is found or there are multiple matches
   */
  public static String getMethodStartsWith(String start) {    
    boolean multiple = false;
    String candidate = null;

    CommandLine.log.println("getMethodStartsWith: receive " + start);

    for (int i=1; i<methods.length; i++) {
      String methodName = methods[i].getName().toString();
      if (methodName.startsWith(start)) {
	if (candidate==null)
	  candidate = methodName;
	else 
	  multiple = true;
      }
    }

    if (!multiple)
      return candidate;
    else
      return null;
  }

  /**
   * Find the first matching method name given a class
   * @param start  the beginning portion of the method name
   * @param cls  the VM_Class to search
   * @return the full method name, null if none is found or there are multiple matches
   */
  public static String getMethodStartsWith(VM_Class cls, String start) {    
    boolean multiple = false;
    String candidate = null;
    VM_Method methods[] = cls.getDeclaredMethods();

    for (int i = 0, n = methods.length; i < n; ++i) {
      String methodName = methods[i].getName().toString();
      if (methodName.startsWith(start)) {
	if (candidate==null)
	  candidate = methodName;
	else 
	  multiple = true;
      }
    }

    if (!multiple)
      return candidate;
    else
      return null;
  }

  /**
   * Find the first matching field name given a class
   * @param start  the beginning portion of the field name
   * @param cls  the VM_Class to search
   * @return the full field name, null if none is found or there are multiple matches
   */
  public static String getFieldStartsWith(VM_Class cls, String start) {
    boolean multiple = false;
    String candidate = null;
    VM_Field fields[] = cls.getDeclaredFields();

    // CommandLine.log.println("getFieldStartsWith: class " + cls.toString());

    for (int i = 0; i < fields.length; ++i) {
      String fieldName = fields[i].getName().toString();
      if (fieldName.startsWith(start)) {
	if (candidate==null)
	  candidate = fieldName;
	else 
	  multiple = true;
      }
    }
    if (!multiple)
      return candidate;
    else
      return null;
  }


  /**
   * For the jdp commands that get autocompletion, see if the command syntax
   * still needs another space key to be valid
   * @param command a jdp command
   * @return the same command with the last word completed if possible 
   *         (class, method or field name), 
   *         null if no unique name was found 
   */
  public static boolean moreSpaceKeyExpected(String command) {
    String [] words = CommandLine.toArgs(command);
    if (words==null || words.length<2)
      return true;
    if (words[0].equals("print")      || words[0].equals("p")  ||
	words[0].equals("printclass") || words[0].equals("pc") ||
	words[0].equals("break")      || words[0].equals("b")) {
      return false;
    }
    return true;
  }


  /**
   * Auto completion for command line based on the given jdp command
   * @param command a jdp command
   * @return the same command with the last word completed if possible 
   *         (class, method or field name), 
   *         null if no unique name was found 
   */
  public static String complete(String command, OsProcess user) {
    String fullString=null;

    // break up the text line into an array of words
    String [] words = CommandLine.toArgs(command);
    if (words==null || words.length<2)
      return null;

    // parsing should match the jdp command syntax in Debugger.java
    if (words[0].equals("print") || words[0].equals("p")) {
      // for the print local variable command, we expect a field name to start
      fullString = completeLocalAndField(words[1], user);

    } else if (words[0].equals("printclass") || words[0].equals("pc")) {
      fullString = completeClassAndField(words[1]);

    } else if (words[0].equals("break") || words[0].equals("b")) {
      // for the breakpoint command, we expect either a class or method name
      fullString = completeClassAndMethod(words[1]);
    }

    if (fullString!=null)
      return words[0] + " " + fullString;
    else
      return null;
  }

  /**
   * Complete an expression as expected in the jdp command "printclass":
   *      classname.field... 
   * The partial expression may be either a class name or the field name of the class
   * @param expr an partial expression of the form classname.field... 
   * @result the completed expression, or null if no completion 
   *         (multiple matches or no match)
   */
  private static String completeClassAndField(String expr) {
    int i;
    // for the printclass command, we expect a class name to start
    String className = CommandLine.varParseClass(expr);
    String fieldNames[] = CommandLine.varParseField(expr);

    // only the class name given, complete the class name 
    if (fieldNames==null) {
      if (Character.isDigit(className.charAt(0))) 
	return null;            // not a name, return with no completion
      else 
	return getClassStartsWith(className);
    } 
    
    // To complete the last field, we have to go back to the start
    // of the expression and find the class
    // Deference each field until the last field
    try {
      VM_Class cls = VM_Class.forName(className);        
      CommandLine.log.println("completeClassAndField: found class " + cls.getName().toString());
      for (i=0; i<fieldNames.length-1; i++) {
	// look up the class for this field to set up for the next field
	VM_Field field = BootMap.findVMField(cls.getName().toString(), fieldNames[i]);
	VM_Type type = field.getType();
	if (type.isArrayType()) 
	  type = type.asArray().getInnermostElementType();
	if (type.isClassType()) 
	  cls = type.asClass();	
      }

      // Given class name, complete the last field name
      String lastField = getFieldStartsWith(cls, fieldNames[fieldNames.length-1]);

      // substitute the last field name
      if (lastField!=null) {
	// i = expr.indexOf(fieldNames[fieldNames.length-1]);
	// return expr.substring(0,i) + lastField;
	return substituteTail(expr, fieldNames[fieldNames.length-1], lastField);
      } else {
	return null;
      }

    } catch (VM_ResolutionException e) {
      // no match for the class name at the start
      return null;
    } catch (BmapNotFoundException e1) {
      // no match for one of the fields
      return null;
    }
  }

  /**
   * Complete an expression as expected in the jdp command "print"
   *     localvar.field... 
   * the partial expression may be either the local variable name or 
   * a field name if the local variable is an object
   * (This method cannot be static because we need to access the memory and 
   * register values through JNI, which needs the process ID in OsProcess)
   * @param expr an partial expression of the form classname.field... 
   * @param user  reference to OsProcess to access the stack
   * @result the completed expression, or null if no completion 
   *         (multiple matches or no match)
   */
  private static String completeLocalAndField(String expr, OsProcess user) {
    // the full expression without the stack frame number
    String varname = CommandLine.localParseName(expr);

    CommandLine.log.println("completeLocalAndField: " + varname);

    if (varname==null)
      return null;    // no text to complete, return
    
    // check with the local variable name at this frame
    int frame = CommandLine.localParseFrame(expr);
    if (frame==-1)
      frame = 0;      // no frame default to frame 0

    try {
      VM_Method mth = user.bmap.getMethodAtFrame(frame);
      VM_LocalVariable [] locals = mth.getLocalVariables();
      if (locals==null)
      	return null;    // no info on local variable for this method
      
      // if there is no nested field, try to complete the local name
      int colon = varname.indexOf(".");
      if (colon==-1) {
      	for (int i=0; i<locals.length; i++) {
      	  String candidate = locals[i].getName();
      	  if (candidate.startsWith(varname)) {
      	    return substituteTail(expr, varname, candidate);
      	  }
      	}
      	return null;     // no local variable name match
      }
      
      // if there is any nested field, first change the local name to its class name
      // then pass on to completeClassAndField()
      String requestedLocal = varname.substring(0,colon);
      for (int i=0; i<locals.length; i++) {
      	String candidate = locals[i].getName();
      	if (candidate.equals(requestedLocal)) {
      	  String localClassName = locals[i].getType().asClass().getName().toString();
	  localClassName = localClassName.replace('.','/');
      	  String converted = substituteHead(varname, requestedLocal, localClassName);
	  CommandLine.log.println("completeLocalAndField: converted, " + converted);
      	  String completed = completeClassAndField(converted);    
	  // the local name has been replaced with its class name, 
	  // so put the local name back in the completed expression
	  completed = substituteHead(completed, localClassName, requestedLocal);
	  return substituteTail(expr, varname, completed);
      	}
      }
      return null;   // no name match for the requested local variable
    } catch (Exception e) {
      // fail in getMethodAtFrame()
      return null;
    }
  }

  /**
   * Complete an expression as expected in the jdp command "break":
   *    class.method
   * The partial expression may be either the class name or the method name
   * @param expr an partial expression of the form class.method
   * @result the completed expression, or null if no completion 
   *         (multiple matches or no match)
   * 
   */
  private static String completeClassAndMethod(String expr) {
    String fullName;
    String classname = CommandLine.breakpointParseClass(expr);
    String methodname = CommandLine.breakpointParseMethod(expr);

    CommandLine.log.println("completeClassAndMethod: " + classname + 
			    ", " + methodname);
    // if the parser gives no classname, this may mean that the use
    // just hasn't gotten to the period yet to start the method name,
    // so first try to complete the classname, 
    // then if no match, try the method name
    if (classname.equals("")) {
      fullName = getClassStartsWith(expr);
      if (fullName==null)
	return getMethodStartsWith(expr);
      else
	return fullName;
    }

    // the class name was given, complete using the method names in this class
    try {
      VM_Class cls = VM_Class.forName(classname);
      fullName = getMethodStartsWith(cls, methodname);
      // substitute the full method name
      if (fullName!=null) {
	return substituteTail(expr, methodname, fullName);
      } else {
	return null;
      }    
    } catch (VM_ResolutionException e) {
      return null;
    }
  }

  private static String substituteTail(String expr, String oldName, String newName) {
    int i = expr.lastIndexOf(oldName);
    if (i!=-1)
      return expr.substring(0,i) + newName;
    else 
      return expr;
  }

  private static String substituteHead(String expr, String oldName, String newName) {
    int i = expr.indexOf(oldName);
    if (expr.startsWith(oldName))
      return newName + expr.substring(oldName.length());
    else 
      return expr;
  }

}
