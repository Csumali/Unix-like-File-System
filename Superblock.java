public class Superblock {
  private static final int defaultInodeBlocks = 64;
  public int totalBlocks; // the number of disk blocks
  public int totalInodes; // the number of inodes
  public int freeList; // the block number of the free list's head

  // Constructor
  public Superblock(int diskSize) {
    // allocate space for superblock
    byte[] superBlock = new byte[Disk.blockSize];

    // read the superblock from Disk
    SysLib.rawread(0, superBlock);
    totalBlocks = SysLib.bytes2int(superBlock, 0);
    totalInodes = SysLib.bytes2int(superBlock, 4);
    freeList = SysLib.bytes2int(superBlock, 8);

    // check disk contents are valid
    if (totalBlocks == diskSize && totalInodes > 0 && freeList >= 2)
      // disk contents are valid
      return;
    else {
      // if invalid, call format()
      totalBlocks = diskSize;
      format(defaultInodeBlocks);
    }
  }

  // Writes back totalBlocks, totalInodes, and freeList to Disk
  public void sync() {
    // create a byte array to contain the variables
    // when they are converted back from int to bytes
    byte[] block = new byte[Disk.blockSize];
    SysLib.int2bytes(totalBlocks, block, 0);
    SysLib.int2bytes(totalInodes, block, 4);
    SysLib.int2bytes(freeList, block, 8);
    // write it to the disk
    SysLib.rawwrite(0, block);
  }

  // Initializes the superblock
  // Initializes each inode and immediately write it back to Disk
  // initialize free blocks
  public void format(int numOfInodes) {
    byte[] block = null;

    totalInodes = numOfInodes;

    for (int i = 0; i < totalInodes; i++) {
      // reset inode in disk
      Inode tempNode = new Inode();
      tempNode.toDisk((short) i);
    }

    // set free list based on the number of Inodes
    int var;
    if (numOfInodes % 16 == 0)
      var = 1;
    else
      var = 2;

    freeList = (numOfInodes / 16) + var;

    // create new free blocks and write it into the disk
    for (int i = totalBlocks - 2; i >= freeList; i--) {
      block = new byte[Disk.blockSize];
      // erase
      for (int j = 0; j < Disk.blockSize; j++) {
        block[j] = (byte) 0;
      }
      SysLib.int2bytes(i + 1, block, 0);
      SysLib.rawwrite(i, block);
    }
    // write Null pointer in last block
    SysLib.int2bytes(-1, block, 0);
    SysLib.rawwrite(totalBlocks - 1, block);

    // update super block
    sync();
  }

  // Gets a new free block from the free list
  public int getFreeBlock() {
    // read the first free block
    if (freeList > 0 && freeList < totalBlocks) {
      byte[] block = new byte[Disk.blockSize];
      SysLib.rawread(freeList, block);

      int tempVal = freeList;

      // move head index to the next space
      freeList = SysLib.bytes2int(block, 0);

      // return block location
      return tempVal;
    }
    return -1; // invalid freeList state
  }

  // Returns this old block to the free list. The list can be a stack.
  public boolean returnBlock(int oldBlockNumber) {
    // make a temporary byte array
    byte[] buffer = new byte[Disk.blockSize];

    // convert free list into bytes and put it in buffer
    SysLib.short2bytes((short) freeList, buffer, 0);

    // write to the disk
    SysLib.rawwrite(oldBlockNumber, buffer);

    // set oldBlockNumber to freelist
    freeList = oldBlockNumber;
    return true;
  }
}