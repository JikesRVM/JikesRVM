/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
public class tak_db{

  // double tak(double x, double y, double z);

  public static void main(String argv[])
  { 
        int i;
        System.out.println("Tak is running\n");
        //for (i=0; i<1000; i++){
          double result = tak(18,12,6);
          System.out.println(result + "\n");
        //}

        //System.exit(0);
  }

  static boolean run() {
    double d = tak_db.tak(18, 12, 6);
    System.out.println("Tak_db returned: " + d);
    return true;
  }


static double tak(double x, double y, double z)
{
   if (y >= x)
   {
      return z;
   }
   else
   {
      return tak(tak (x-1, y, z),
                 tak (y-1, z, x),
                 tak (z-1, x, y));
   }
}

}
