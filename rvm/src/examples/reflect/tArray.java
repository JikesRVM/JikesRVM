/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import java.lang.reflect.*;

/**
 * @author unascribed
 */
public class tArray
   {
   private int i;

   tArray(int i)
      {
      this.i = i;
      }

   public String
   toString()
      {
      return "tArray " + i;
      }
      
   public static void main(String args[]) throws Exception
      {
      Class  elementType = Class.forName("tArray");
      int    length      = 10;
      Object array[]     = (Object []) Array.newInstance(elementType, length);
      
      for (int i = 0, n = array.length; i < n; ++i)
         array[i] = new tArray(i);

      for (int i = 0, n = array.length; i < n; ++i)
         System.out.println(i + ": " + array[i]);
      }
   }
