/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import java.util.*;
import java.lang.reflect.*;

/**
 * @author unascribed
 */
public class tClass
   {
   public static void gc()
      {
      System.gc();
      }

   public static String hello(String say_this)
      {
      System.out.println("before gc arg=" + say_this);
      gc();
      System.out.println("after  gc arg=" + say_this);
      return "And I Say Hello Back";
      }

   public static int iello(String say_this, int return_this)
      {
      System.out.println("before gc arg=" + say_this);
      gc();
      System.out.println("after  gc arg=" + say_this);
      return return_this;
      }

   public static long lello(String say_this, long return_this)
      {
      System.out.println("before gc arg=" + say_this);
      gc();
      System.out.println("after  gc arg=" + say_this);
      return return_this;
      }

   public static int jello(int return_this, String say_this, int junk, int more_junk)
      {
      System.out.println("before gc arg=" + say_this);
      gc();
      System.out.println("after  gc arg=" + say_this);
      return return_this;
      }

   public String vello(String say_this)
      {
      return "And I Say Vello Back";      
      }

   public tClass(String s)
      {
      System.out.println("tClass constructor called with "+s);
      }

   private tClass()
     {
       System.out.println("tClass private constructor called!" );
     }

   public static void main(String args[]) throws Exception
      {
      // Class.forName
      //
      Class c = Class.forName("tClass");
      System.out.println(c);
      try
         {
         Class c_not_found = Class.forName("NotAClassSoThrowAnExceptionPlease");
         }
      catch (ClassNotFoundException e)
         {
         System.out.println("caught ClassNotFoundException");
         }
      
      // -----------------------------  Class.isArray()
      //
      if (c.isArray()) System.out.println(c+" is an array????");
      else             System.out.println(c+" is not an array...good");

      // ------------------------------  Class.getConstructors
      //
      Constructor ctors[] = c.getConstructors();
      Arrays.sort(ctors, new Comparator() {
              public int compare(Object x, Object y) {
                  return x.toString().compareTo( y.toString() );
              }
          });

      System.out.println(c+" has "+ctors.length+" visible constructors");
      for (int i = 0; i < ctors.length; ++i)
         System.out.println("   " + i + ": " + ctors[i]);

      Constructor declaredCtors[] = c.getDeclaredConstructors();
      Arrays.sort(declaredCtors, new Comparator() {
              public int compare(Object x, Object y) {
                  return x.toString().compareTo( y.toString() );
              }
          });

      System.out.println(c+" has "+declaredCtors.length+" declared constructors");
      for (int i = 0; i < declaredCtors.length; ++i)
         System.out.println("   " + i + ": " + declaredCtors[i]);

      // Class.getMethods  Method.getName
      //
      Method methods[] = c.getMethods();
      Method hello = null;
      Method iello = null;
      Method lello = null;
      Method jello = null;
      Method vello = null;

      Method declaredMethods[] = c.getDeclaredMethods();
      Arrays.sort(declaredMethods, new Comparator() {
              public int compare(Object x, Object y) {
                  return x.toString().compareTo( y.toString() );
              }
          });

      System.out.println(c + " has a total number of methods: "
                         + methods.length );
      for(int i = 0; i < methods.length; i++)
      { 
        // dont print the methods out, signitures are different in
        // java and RVM libraries for java/lang/Object methods.
        // System.out.println( methods[i] );
        if (methods[i].getName().equals("hello")) hello = methods[i];
        if (methods[i].getName().equals("iello")) iello = methods[i];
        if (methods[i].getName().equals("lello")) lello = methods[i];
        if (methods[i].getName().equals("jello")) jello = methods[i];
        if (methods[i].getName().equals("vello")) vello = methods[i];
      }

      System.out.println( " Number of declared methods: " + declaredMethods.length );

      for(int i = 0; i < declaredMethods.length; i++)
        System.out.println( declaredMethods[i] );

      // ------------------------------  invoke methods taking String, returning ref (String)
      //
      if (hello==null) 
         {
         System.out.println("tClass.hello not found!");
         System.exit(-1);
         }
      else
         {
         System.out.println("================= READY TO CALL: "+hello);
         }

      // Method.invoke
      //
      int n_calls = 3;  // loop to see if we can crash gc
      while(n_calls-- > 0)
         {
         String hello_args[] = {"I Say Hello to You!"};
         String result = (String)hello.invoke(null, hello_args);
         System.out.println(result);
         }

      // ------------------------------  invoke methods taking String,int; returning int
      //
      if (iello==null) 
         {
         System.out.println("tClass.iello not found!");
         System.exit(-1);
         }
      else
         {
         System.out.println("================= READY TO CALL: "+iello);
         }

      // Method.invoke
      //
      n_calls = 3;  // loop to see if we can crash gc
      while(n_calls-- > 0)
         {
         Object iello_args[] = {"I Say Iello to You!", new Integer(99)};
         Integer result = (Integer)iello.invoke(null, iello_args);
         System.out.println("Does this>"+result+"< look like 99?");
         }

      // ------------------------------  invoke methods taking String,long; returning long
      //
      if (lello==null) 
         {
         System.out.println("tClass.lello not found!");
         System.exit(-1);
         }
      else
         {
         System.out.println("================= READY TO CALL: "+lello);
         }

      // Method.invoke
      //
      n_calls = 3;  // loop to see if we can crash gc
      while(n_calls-- > 0)
         {
         Object lello_args[] = {"I Say Lello to You!", new Long(99)};
         Long result = (Long)lello.invoke(null, lello_args);
         System.out.println("Does this>"+result+"< look like 99?");
         }


      // ------------------------------  invoke methods taking String,int; returning int
      //
      if (jello==null) 
         {
         System.out.println("tClass.jello not found!");
         System.exit(-1);
         }
      else
         {
         System.out.println("================= READY TO CALL: "+jello);
         }

      // Method.invoke
      //
      n_calls = 3;  // loop to see if we can crash gc
      while(n_calls-- > 0)
         {
         Object jello_args[] = { new Integer(99), "I Say Jello to You!", new Integer(95), new Integer(94)};
         Integer result = (Integer)jello.invoke(null, jello_args);
         System.out.println("Does this>"+result+"< look like 99?");
         }

       // ------------------------------  newInstance
      tClass tc = new tClass("Hi!");
      String initargs[] = {"I'm dynamic!"};
      tClass tc_dyn = (tClass)ctors[0].newInstance(initargs);

      // ------------------------------  invoke methods taking String, returning String, but now virtual
      //
      if (vello==null) 
         {
         System.out.println("tClass.vello not found!");
         System.exit(-1);
         }
      else
         {
         System.out.println("================= READY TO CALL: "+vello);
         }

      // Method.invoke
      //
      n_calls = 3;  // loop to see if we can crash gc
      while(n_calls-- > 0)
         {
         String vello_args[] = {"I Say Vello to You!"};
         String result = (String)vello.invoke(tc_dyn, vello_args);
         System.out.println(result);
         }
  

      }
   

   //------ 

   }
