/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import java.lang.reflect.*;
import java.util.*;

/**
 * @author unascribed
 */
public class tField {
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

  public static void main(String args[]) throws Exception {
    tField t = new tField();

    Class tf_type = Class.forName("tField");
    Field fields[] = tf_type.getFields();
    Arrays.sort(fields, new Comparator() {
        public int compare(Object x, Object y) {
          return x.toString().compareTo( y.toString() );
        }
      });
    
    t.printFields(fields);

    t.testBoolean(fields);
    t.testByte(fields);
    t.testShort(fields);
    t.testInt(fields);
    t.testLong(fields);
    t.testFloat(fields);
    t.testDouble(fields);

    t.printFields(fields);
  }

  private void printFields(Field[] fields) throws Exception {
    System.out.println("\n** Current value of fields is **\n");
    for (int i = 0; i < fields.length; i++) {
      System.out.println(i+"\t "+fields[i]+" = "+fields[i].get(this));
    }
  }

  private void testBoolean(Field[] fields) throws Exception {
    System.out.println("\n** Set Booleans to false **\n");

    for (int j = 0; j < fields.length; j++) {
      try {
        fields[j].setBoolean(this, false); 
        System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(this));  
      } catch(Exception e) {
        System.out.print(j+"\t "+fields[j]+" \t ");
        System.out.println(e);
      }
    }
  }

  private void testByte(Field[] fields) throws Exception {
    System.out.println("\n** Set bytes to 12 **\n");

    for (int j = 0; j < fields.length; j++) {
      try {
        fields[j].setByte(this, (byte)12); 
        System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(this));  
      } catch(Exception e) {
        System.out.print(j+"\t "+fields[j]+" \t ");
        System.out.println(e);
      }
    }
  }

  private void testShort(Field[] fields) throws Exception {
    System.out.println("\n** Set shorts to 2 **\n");

    for (int j = 0; j < fields.length; j++) {
      try {
        fields[j].setShort(this, (short)2); 
        System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(this));
      } catch(Exception e) {
        System.out.print(j+"\t "+fields[j]+" \t ");
        System.out.println(e);
      }
    }
  }

  private void testInt(Field[] fields) throws Exception {
    System.out.println("\n** Set int to 3 **\n");

    for (int j = 0; j < fields.length; j++) {
      try {
        fields[j].setInt(this, 3); 
        System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(this));
      } catch(Exception e) {
        System.out.print(j+"\t "+fields[j]+" \t ");
        System.out.println(e);
      }
    } 
  }
   
  private void testLong(Field[] fields) throws Exception {
    System.out.println("\n** Set long to 4 **\n");

    for (int j = 0; j < fields.length; j++) {
      try {
        fields[j].setLong(this, 4); 
        System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(this));
      } catch(Exception e) {
        System.out.print(j+"\t "+fields[j]+" \t ");
        System.out.println(e);
      }
    }
  }

  private void testFloat(Field[] fields) throws Exception {
    System.out.println("\n** Set float to 5.0 **\n");

    for (int j = 0; j < fields.length; j++) {
      try {
        fields[j].setFloat(this, 5.0F); 
        System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(this));  
      } catch(Exception e) {
        System.out.print(j+"\t "+fields[j]+" \t ");
        System.out.println(e);
      }
    }
  }

  private void testDouble(Field[] fields) throws Exception {
    System.out.println("\n** Set double to 6.0 **\n");

    for (int j = 0; j < fields.length; j++) {
      try {
        fields[j].setDouble(this, 6.0); 
        System.out.println("OK:"+j+"\t "+fields[j]+" = "+fields[j].get(this));  
      } catch(Exception e) {
        System.out.print(j+"\t "+fields[j]+" \t ");
        System.out.println(e);
      }
    }
  }


}




class HasFieldOfUnloadedType
   {
   static UnloadedClass u;
   }

class UnloadedClass
   {
   
   }
