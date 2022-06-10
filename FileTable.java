import java.util.Vector;
import java.util.Vector;

public class FileTable {
  private Vector table; // the actual entity of this file table
  private Directory dir; // the root directory

  // Constructor
  public FileTable(Directory directory) {
    table = new Vector(); // instantiate a file (structure) table
    dir = directory; // receive a reference to the Director
  } // from the file system

  // Allocate a new file (structure) table entry for this file name
  // Allocates/retrieves and register the corresponding inode using dir
  // Increments this inode's count
  // Immediately writes back this inode to the disk
  // Returns a reference to this file (structure) table entry
  public synchronized FileTableEntry falloc(String filename, String mode) {
    short entryMode = getFileTableEntryMode(mode);
    if (entryMode < 0) { // invalid mode
      return null;
    }

    short iNumber = -1; // inode number
    Inode inode = null; // hold inode

    while (true) {
      // get the inumber from the inode for the corresponding file name
      iNumber = (filename.equals("/") ? (short) 0 : dir.namei(filename));

      if (iNumber < 0) {
        if (entryMode == 0) {
          return null;
        }
        if ((iNumber = dir.ialloc(filename)) < 0) {
          return null;
        }
        inode = new Inode();
        break;
      }
      // when iNumber >= 0
      inode = new Inode(iNumber);
      if (inode.flag == 4) {
        return null;
      }
      if (inode.flag == 0 || inode.flag == 1) {
        break;
      }
      if (entryMode == 0 && inode.flag == 0) {
        break;
      }
      try {
        wait();
      } catch (InterruptedException e) {
      }
    }
    inode.count++; // increase the number of users
    inode.toDisk(iNumber);
    FileTableEntry e = new FileTableEntry(inode, iNumber, mode);
    table.addElement(e);
    return e;
  }

  // Receives a file table entry reference
  // Saves the corresponding inode to the disk
  // Frees this file table entry.
  // Returns true if this file table entry found in my table
  public synchronized boolean ffree(FileTableEntry e) {
    if (e == null) {
      return true;
    }

    if (!table.removeElement(e)) {
      return false;
    }

    Inode inode = e.inode;
    short iNumber = e.iNumber;

    if (inode.count > 0) {
      // decrease the count of users of that file
      inode.count--;
    }

    if (inode.count == 0) {
      inode.flag = 0;
    }

    // save the corresponding inode to the disk
    inode.toDisk(iNumber);

    if (inode.flag == 0 || inode.flag == 1) {
      notify();
    }
    e = null;
    return true;
  }

  // Returns true/false on whether there is not any FileTableEntry cached inside
  // FileTable
  public synchronized boolean fempty() {
    return table.isEmpty(); // return if table is empty
  } // should be called before starting a format

  // Returns a mode of the table entry given its mode field
  public static short getFileTableEntryMode(String mode) {
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