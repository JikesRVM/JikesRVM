/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
/***
class Test extends ClassLoader
   {
   Class 
   myLoader(String s)
      throws Exception
      {
      return findSystemClass(s);
      }

   public Class
   loadClass(String s, boolean b)
      {
      SystemOut.println("!!NOT REACHED!!");
      return null;
      }
***/

class TestClassLoading
   {
   Class 
   myLoader(String s)
      throws Exception
      {
      return Class.forName(s);
      }
      
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestClassLoading");
      new TestClassLoading().test();
      }
      
   void
   test()
      {
      try { simple();                   } catch(Exception e) { SystemOut.println(e); }
      try { primitive();                } catch(Exception e) { SystemOut.println(e); }
      try { arrayOfSimple();            } catch(Exception e) { SystemOut.println(e); }
      try { arrayOfArrayOfSimple();     } catch(Exception e) { SystemOut.println(e); }
      try { arrayOfPrimitive();         } catch(Exception e) { SystemOut.println(e); }
      try { arrayOfArrayOfPrimitive();  } catch(Exception e) { SystemOut.println(e); }
      
      SystemOut.println("bye");
      }
      
      
   void simple()
      throws Exception
      {
      Class c = myLoader("java.lang.String");
      SystemOut.println("class=" + c);
      String i = (String)c.newInstance();
      SystemOut.println("instance=" + i);
      }
      
   void primitive()
      throws Exception
      {
      Class c = myLoader("I");
      SystemOut.println("class=" + c);
      Integer i = (Integer)c.newInstance();
      SystemOut.println("instance=" + i);
      }
      
   void arrayOfSimple()
      throws Exception
      {
      Class c = myLoader("[Ljava.lang.String;");
      SystemOut.println("class=" + c);
      String i[] = (String[])c.newInstance();
      SystemOut.println("instance=" + i + " len=" + i.length);
      }
      
   void arrayOfArrayOfSimple()
      throws Exception
      {
      Class c = myLoader("[[Ljava.lang.String;");
      SystemOut.println("class=" + c);
      String i[][] = (String[][])c.newInstance();
      SystemOut.println("instance=" + i + " len=" + i.length);
      }
      
   void arrayOfPrimitive()
      throws Exception
      {
      Class c = myLoader("[I");
      SystemOut.println("class=" + c);
      int    i[] = (int[])c.newInstance();
      SystemOut.println("instance=" + i + " len=" + i.length);
      }
      
   void arrayOfArrayOfPrimitive()
      throws Exception
      {
      Class c = myLoader("[[I");
      SystemOut.println("class=" + c);
      int    i[][] = (int[][])c.newInstance();
      SystemOut.println("instance=" + i + " len=" + i.length);
      }
   }
