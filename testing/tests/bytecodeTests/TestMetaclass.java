/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author unascribed
 */
// Class.forName
// Class.newInstance
//

class TestMetaclass extends Thread
   {
   static void
   createInstanceOf(String typeName)
      {
      SystemOut.println("Trying \"" + typeName + "\"");
      try {
          Class c = Class.forName(typeName);
          SystemOut.println("classForName: " + c.getName());
          Object o = c.newInstance();
          SystemOut.println("newInstance: " + o);
          }
      catch(Throwable e)
          { 
          SystemOut.println(e);
          }
      SystemOut.println();
      }
      
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestMetaclass");
      
      createInstanceOf("foobar");               // ClassNotFoundException (no such class)
      createInstanceOf("I");                    // ClassNotFoundException (no such class)
      createInstanceOf("java.lang.Number");     // InstantiationException (can't instantiate abstract class)
      createInstanceOf("[Ljava.lang.String;");  // InstantiationException (can't instantiate arrays)
      createInstanceOf("[I");                   // InstantiationException (can't instantiate arrays)
      createInstanceOf("java.lang.Integer");    // NoSuchMethodError      (no default constructor)
      createInstanceOf("java.lang.String");     // ok
      }
   }
