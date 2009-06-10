/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package test.org.jikesrvm.basic.core.reflect;

import java.lang.reflect.Method;

class tInstance {
  public int ifield;
  public double dfield;
  public boolean bfield;
  public Object ofield;

  public static void gc() {
    System.gc();
  }

  public int iuserFunction(int i) {
    gc();
    return ifield + i;
  }

  public double duserFunction(double d) {
    gc();
    return d + dfield;
  }

  public boolean buserFunction(boolean x) {
    gc();
    if (x)
      return bfield;
    else
      return x;
  }

  public Object ouserFunction(String s) {
    gc();
    ofield = s + "abc";
    return ofield;
  }

  public void vuserFunction(int i, Integer x) {
    gc();
    ifield = ifield + i + x;
  }

  public static void main(String[] args) throws Exception {
    // Class.forName
    //
    Class c = Class.forName("test.org.jikesrvm.basic.core.reflect.tInstance");
    tInstance myInstance = (tInstance) c.newInstance();
    myInstance.ifield = 4;
    myInstance.dfield = 8.8;
    myInstance.bfield = true;
    myInstance.ofield = null;

    System.out.println(c);

    // Class.getMethods  Method.getName
    //
    Method[] methods = c.getMethods();
    Method imethod = null;
    Method dmethod = null;
    Method bmethod = null;
    Method omethod = null;
    Method vmethod = null;

    for (Method method : methods) {
      String methodName = method.getName();
      if (methodName.equals("iuserFunction"))
        imethod = method;
      else if (methodName.equals("duserFunction"))
        dmethod = method;
      else if (methodName.equals("buserFunction"))
        bmethod = method;
      else if (methodName.equals("ouserFunction"))
        omethod = method;
      else if (methodName.equals("vuserFunction"))
        vmethod = method;
    }

    Object[] methodargs = new Object[1];

    /******************/
    //  int           //
    /******************/

    if (imethod == null) {
      System.out.println("tInstance.iuserFunction not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + imethod);
      methodargs[0] = 3;
      int iresult = (Integer) imethod.invoke(myInstance, methodargs);
      if (iresult != 7) {
        System.out.println("Wrong answer from iuserFunction");
        System.out.println(iresult);
        System.exit(1);
      }
    }

    /******************/
    //  double        //
    /******************/

    if (dmethod == null) {
      System.out.println("tInstance.duserFunction not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + dmethod);
      methodargs[0] = 3.4;
      double dresult = (Double) dmethod.invoke(myInstance, methodargs);
      if (dresult < 12.2 || dresult >= 12.2000001) {
        System.out.println("Wrong answer from duserFunction");
        System.out.println(dresult);
        System.exit(1);
      }
    }

    /******************/
    //  boolean       //
    /******************/

    if (bmethod == null) {
      System.out.println("tInstance.buserFunction not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + bmethod);
      methodargs[0] = Boolean.TRUE;
      boolean bresult = (Boolean) bmethod.invoke(myInstance, methodargs);
      if (!bresult) {
        System.out.println("Wrong answer from buserFunction");
        System.exit(1);
      }
    }

    /******************/
    //  Object        //
    /******************/

    if (omethod == null) {
      System.out.println("tInstance.ouserFunction not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + omethod);
      methodargs[0] = "123";
      Object oresult = omethod.invoke(myInstance, methodargs);
      if (!(oresult instanceof java.lang.String) || !oresult.equals("123abc")) {
        System.out.println("Wrong answer from ouserFunction");
        System.exit(1);
      }
    }

    /*******************/
    //  Void  two args //
    /*******************/

    if (vmethod == null) {
      System.out.println("tInstance.vuserFunction not found!");
      System.exit(1);
    } else {
      System.out.println("================= READY TO CALL: " + vmethod);
      Object[] twoargs = new Object[2];
      twoargs[0] = 4;
      twoargs[1] = 10;
      Object vresult = vmethod.invoke(myInstance, twoargs);
      if ((vresult != null) || (myInstance.ifield != 18)) {
        System.out.println("Wrong results from vuserFunction");
        System.exit(1);
      }
    }
    System.out.println("Test success");

  }
}
