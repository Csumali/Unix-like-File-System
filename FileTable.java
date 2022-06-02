import java.util.Vector;
import java.util.Vector;

public class FileTable {
  private Vector table;  // the actual entity of this file table
  private Directory dir; // the root directory

  public FileTable(Directory directory) { // constructor
    table = new Vector(); // instantiate a file (structure) table
    dir = directory;      // receive a reference to the Director
  }                       // from the file system

  // major public methods

  // allocate a new file (structure) table entry for this file name
  // allocate/retrieve and register the corresponding inode using dir
  // increment this inode's count
  // immediately write back this inode to the disk
  // return a reference to this file (structure) table entry
  public synchronized FileTableEntry falloc(String filename, String mode) {
    short entryMode = getEntryMode(mode);
    if (entryMode < 0) { // invalid mode
      return null;
    }
    short iNumber;
    Inode fileNode = null;
    FileTableEntry fte = null;

    while (true) {
      if (filename.equals("/")) {
        iNumber = 0;
      } else {
        iNumber = dir.namei(filename);
      }
      if (iNumber < 0) {
        if (entryMode == 0) {
          return null;
        }
        if ((iNumber = dir.ialloc(filename)) < 0) {
          return null;
        }
        fileNode = new Inode();
        break;
      }
      fileNode = new Inode(iNumber);
      if (fileNode.flag == 4) {
        return null;
      }
      if (fileNode.flag == 0 || fileNode.flag == 1) {
        break;
      }
      if (entryMode == 0 && fileNode.flag == 0) {
        break;
      }
      try {
        wait();
      } catch (InterruptedException e) {
      }
    }
    fileNode.count++;
    fileNode.toDisk(iNumber);
    fte = new FileTableEntry(fileNode, iNumber, mode);
    table.add(fte);
    return fte;
  }

  // receive a file table entry reference
  // save the corresponding inode to the disk
  // free this file table entry.
  // return true if this file table entry found in my table
  public synchronized boolean ffree(FileTableEntry e) {
    if (e == null) {
      return true;
    }

    if (!table.removeElement(e)) {
      return false;
    }

    Inode fileNode = e.inode;
    short iNumber = e.iNumber;

    if (fileNode.count > 0) {
      fileNode.count--;
    }

    if (fileNode.count == 0) {
      fileNode.flag = 0;
    }

    fileNode.toDisk(iNumber);

    if (fileNode.flag == 0 || fileNode.flag == 1) {
      notify();
    }
    e = null;
    return true;
  }

  public synchronized boolean fempty() {
    return table.isEmpty(); // return if table is empty
  }                         // should be called before starting a format

  // returns mode of FileTableEntry given its mode field
  public static short getEntryMode(String mode) {
    if (mode.equalsIgnoreCase("r")) { // read only
      return 0;
    } else if (mode.equalsIgnoreCase("w")) { // write only
      return 1;
    } else if (mode.equalsIgnoreCase("w+")) {// read and write
      return 2;
    } else if (mode.equalsIgnoreCase("a")) { // append
      return 3;
    }
    return -1; // invalid mode
  }
}
