/** This is a public domain routine to print out the Boot Class Path. */
public class BootClassPath {
  private BootClassPath() {}

  public static void main(String[] args) {
    System.out.println(System.getProperty("sun.boot.class.path"));
  }
}
