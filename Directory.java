public class Directory {
  private static int maxChars = 30; // max characters of each file name
  private int fsize[];              // each element stores a different file size
  private char fnames[][];          // each element stores a different file name

  public Directory(int maxInumber) {  // directory constructor
    fsize = new int[maxInumber];      // maxInumber = max files
    for (int i = 0; i < maxInumber; i++)
      fsize[i] = 0;                   // all file size initialized to 0
    fnames = new char[maxInumber][maxChars];
    
    String root = "/"; // entry(inode) 0 is "/"
    fsize[0] = root.length(); // fsize[0] is the size of "/".
    root.getChars(0, fsize[0], fnames[0], 0); // fnames[0] includes "/"
  }

  // assumes data[] received directory information from disk
  // initialize the Directory instance with this data[]
  public int bytes2directory(byte data[]) {
    int offset = 0;
    for (int i = 0; i < fsize.length; i++, offset += 4) {
      fsize[i] = SysLib.bytes2int(data, offset);
    }
    // initializes Directory with data[]
    for (int i = 0; i < fsize.length; i++, offset += (maxChars * 2)) {
      String fname= new String(data, offset, maxChars * 2);
      fname.getChars(0, fsize[i], fnames[i], 0);
    }
    return 0;
  }

  // converts and return Directory information into a plain byte array
  // this byte array will be written back to disk
  public byte[] directory2bytes() {
    byte[] data = new byte[fsize.length * 4 + fnames.length * maxChars * 2];
    int offset = 0;
    for (int i = 0; i < fsize.length; i++, offset += 4) {
      SysLib.int2bytes(fsize[i], data, offset);
    }
    for (int i = 0; i < fnames.length; i++, offset += maxChars * 2) {
      String tableEntry = new String(fnames[i], 0, fsize[i]);
      byte[] bytes = tableEntry.getBytes();
      System.arraycopy(bytes, 0, data, offset, bytes.length);
    }
    return data;
  }

  // filename is the one of a file to be created.
  // allocates a new inode number for this filename
  public short ialloc(String filename) {
    for (int i = 1, l = fsize.length; i < l; i++) {
      if (fsize[i] == 0) {
        fsize[i] = Math.min(filename.length(), maxChars);
        filename.getChars(0, fsize[i], fnames[i], 0);
        return (short) i;
      }
    }
    return -1;
  }

  // deallocates this inumber (inode number)
  // the corresponding file will be deleted.
  public boolean ifree(short iNumber) {
    if (fsize[iNumber] < 0) {
      return false;
    }
    fsize[iNumber] = 0;
    return true;
  }

  // returns the inumber corresponding to this filename
  public short namei(String filename) {
    String fname;
    for (int i = 0; i < fsize.length; i++) {
      if (fsize[i] == filename.length()) {
        fname = new String(fnames[i], 0, fsize[i]);
        if (filename.compareTo(fname) == 0) {
          return (short) i;
        }
      }
    }
    return -1;
  }
}