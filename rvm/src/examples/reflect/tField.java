/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import java.lang.reflect.*;
import java.util.*;

/**
 * @author unascribed
 */
public class tField
   {
   public static boolean sboolean = true;
   public static byte sbyte = 127;
   public static short sshort = 1;
   public static int sint = 1;
   public static long slong = 1;
   public static float sfloat = 1.0F;
   public static double sdouble = 1.0;

   public        boolean mboolean = true;
   public        byte mbyte = 127;
   public        short mshort = 1;
   public        int mint = 1;
   public        long mlong = 1;
   public        float mfloat = 1.0F;
   public        double mdouble = 1.0;




   public static void main(String args[]) throws Exception
      {
      tField t = new tField();

      Class tf_type = Class.forName("tField");
      Field fields[] = tf_type.getFields();
      Arrays.sort(fields, new Comparator() {
	      public int compare(Object x, Object y) {
		  return x.toString().compareTo( y.toString() );
	      }
	  });

      for (int i = 0; i < fields.length; i++)
	 {
	 System.out.println(i+"\t "+fields[i]+" = "+fields[i].get(t));
	 }

      System.out.println("\n** Set Booleans to false **\n");

      for (int j = 0; j < fields.length; j++)
	 {
	 try 
	    {
	    fields[j].setBoolean(t, false); 
	    System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(t));	
	    }
	 catch(Exception e)
	    {
	    System.out.print(j+"\t "+fields[j]+" \t ");
	    System.out.println(e);
	    }
	 }

      System.out.println("\n** Set bytes to 12 **\n");

      for (int j = 0; j < fields.length; j++)
	 {
	 try 
	    {
	    fields[j].setByte(t, (byte)12); 
	    System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(t));	
	    }
	 catch(Exception e)
	    {
	    System.out.print(j+"\t "+fields[j]+" \t ");
	    System.out.println(e);
	    }
	 }

      System.out.println("\n** Set shorts to 2 **\n");

      for (int j = 0; j < fields.length; j++)
	 {
	 try 
	    {
	    fields[j].setShort(t, (short)2); 
	    System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(t));	
	    }
	 catch(Exception e)
	    {
	    System.out.print(j+"\t "+fields[j]+" \t ");
	    System.out.println(e);
	    }
	 }

      System.out.println("\n** Set int to 2 **\n");

      for (int j = 0; j < fields.length; j++)
	 {
	 try 
	    {
	    fields[j].setInt(t, 2); 
	    System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(t));	
	    }
	 catch(Exception e)
	    {
	    System.out.print(j+"\t "+fields[j]+" \t ");
	    System.out.println(e);
	    }
	 } 
   
      System.out.println("\n** Set long to 2 **\n");

      for (int j = 0; j < fields.length; j++)
	 {
	 try 
	    {
	    fields[j].setLong(t, 2); 
	    System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(t));	
	    }
	 catch(Exception e)
	    {
	    System.out.print(j+"\t "+fields[j]+" \t ");
	    System.out.println(e);
	    }
	 }

      System.out.println("\n** Set float to 2.0 **\n");

      for (int j = 0; j < fields.length; j++)
	 {
	 try 
	    {
	    fields[j].setFloat(t, 2.0F); 
	    System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(t));	
	    }
	 catch(Exception e)
	    {
	    System.out.print(j+"\t "+fields[j]+" \t ");
	    System.out.println(e);
	    }
	 }

      System.out.println("\n** Set double to 2.0 **\n");

      for (int j = 0; j < fields.length; j++)
	 {
	 try 
	    {
	    fields[j].setDouble(t, 2.0); 
	    System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(t));	
	    }
	 catch(Exception e)
	    {
	    System.out.print(j+"\t "+fields[j]+" \t ");
	    System.out.println(e);
	    }
	 }

      for (int i = 0; i < fields.length; i++)
	 {
	 System.out.println(i+"\t "+fields[i]+" = "+fields[i].get(t));
	 }


/* ?? Is there any way to get a Field whose type is not loaded and then try to act on it?? 
      System.out.println("\n** ** Test unloaded class semantics **  **\n");
      
      Class hf = Class.forName("HasFieldOfUnloadedType");
      Field hff[] = hf.getFields();
      hff[0].setInt(null, 3);
      */      
      }
   }




class HasFieldOfUnloadedType
   {
   static UnloadedClass u;
   }

class UnloadedClass
   {
   
   }
