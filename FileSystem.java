public class FileSystem {
    private SuperBlock superBlock;
    private Directory directory;
    private FileTable fileTable;

    // Constructor
    FileSystem(int diskSize) {
        superBlock = new SuperBlock(diskSize);
        directory = new Directory(superBlock.totalBlocks * 16);
        fileTable = new FileTable(directory);

        // read the "/" file from disk
        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            // the directory has some data.
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
    }

    // Formats the disk 
    // Files specifies the maximum number of files to be created
    // The return value is 0 on success, otherwise -1.  
    int format(int files) {
        superBlock.format(files);
        directory = new Directory(superBlock.totalBlocks * 16);
        fileTable = new FileTable(directory);
        return 0;
    }

    // Opens the file specified by the fileName string in the given mode 
    // Allocates a new FileTableEntry and returns it
    FileTableEntry open(String fileName, String mode) {
        FileTableEntry ftEnt = fileTable.falloc(fileName, mode);
        if (mode.equals("w")) {
            if (deallocAllBlocks(ftEnt) == false) // need to implement
            return null;
        }
        return ftEnt;
    }

    // Clears data from all blocks
    private boolean deallocAllBlocks(FileTableEntry ftEnt) {
        Inode inode = ftEnt.inode;
        // return if there are other processes before dellocating
        if (inode.count > 1) return false;

        // Clear all direct blocks
        for (int i = 0; i < inode.directSize; i++) {
            if (inode.direct[i] != -1) {
                byte[] emptyBuffer = new byte[Disk.blockSize];
                SysLib.rawwrite(inode.direct[i], emptyBuffer);
                superBlock.returnBlock(inode.direct[i]);
                inode.direct[i] = -1;
            }
        }

        // Clear indirect blocks
        if (inode.indirect != -1) {
            byte[] indirectBlock = new byte[Disk.blockSize];
            SysLib.rawread(inode.indirect, indirectBlock);
            int offset = 0;
            short blockId;

            // clear blocks linked to indirect block
            while ((blockId = SysLib.bytes2short(indirectBlock, offset)) > 0) {
                byte[] emptyBuffer = new byte[Disk.blockSize];
                SysLib.rawwrite(blockId, emptyBuffer);
                superBlock.returnBlock(blockId);
                offset += 2;
            }

            // clear indirect block
            byte[] emptyBuffer = new byte[Disk.blockSize];
            SysLib.rawwrite(inode.indirect, emptyBuffer);
            superBlock.returnBlock(inode.indirect);
            inode.indirect = -1;
        }
        return true;
    }

    // Writes the contents of buffer to the file indicated by fd, 
    // starting at the position indicated by the seek pointer.
    int write(FileTableEntry ftEnt, byte[] buffer) {
        if (ftEnt.mode.equals("r")) return -1;

        Inode inode = ftEnt.inode;
        // Wait if there are other processes before writing
        synchronized(ftEnt) {
        int seekPtr = ftEnt.seekPtr;
        int bufferIndex = 0;
        
        if (ftEnt.mode.equals("a")) seekPtr = inode.length; // set pointer to end if mode is append

        // Deal with remainder if pointer does not start from 0 in direct blocks
        if (seekPtr % Disk.blockSize != 0 && seekPtr < inode.directSize * Disk.blockSize) {
            int remainder = seekPtr % Disk.blockSize;
            int directIndex = seekPtr / Disk.blockSize;

            // Allocate a new block and add to direct pointers
            // if there is not enough space
            if (inode.direct[directIndex] == -1) {
                short newBlock = (short)superBlock.getFreeBlock();
                inode.direct[directIndex] = newBlock;
            }

            // Copies 0 upto seekPtr of the block into writeBuffer
            byte[] remainderBuffer = new byte[Disk.blockSize];
            SysLib.rawread(inode.direct[directIndex], remainderBuffer);
            byte[] writeBuffer = new byte[Disk.blockSize];
            System.arraycopy(remainderBuffer, 0, writeBuffer, 0, remainder);
            
            // If we don't need to write the whole block,
            // just perform write and return
            if (buffer.length < (Disk.blockSize - remainder)) {
                System.arraycopy(buffer, bufferIndex, writeBuffer, remainder, buffer.length);
                SysLib.rawwrite(inode.direct[directIndex], writeBuffer);
                ftEnt.seekPtr += buffer.length;
                inode.length += buffer.length;
                return buffer.length;
            }

            // Writes buffer from seekPtr to the end of the block into WriteBuffer
            System.arraycopy(buffer, bufferIndex, writeBuffer, remainder, writeBuffer.length - remainderBuffer.length);
            SysLib.rawwrite(inode.direct[directIndex], writeBuffer);
            seekPtr += writeBuffer.length - remainderBuffer.length;
            bufferIndex += writeBuffer.length - remainderBuffer.length;
        }

        // writes to disk from direct
        while (seekPtr < inode.directSize * Disk.blockSize) {
            int directIndex = seekPtr / Disk.blockSize;

            // Allocate a new block and add to direct pointers
            // if there is not enough space
            if (inode.direct[directIndex] == -1) {
                short newBlock = (short)superBlock.getFreeBlock();
                inode.direct[directIndex] = newBlock;
            }

            byte[] writeBuffer = new byte[Disk.blockSize];

            // If we don't need to write the whole block,
            // just perform write and return
            if (buffer.length - bufferIndex <= Disk.blockSize) {
                byte[] leftover = new byte[Disk.blockSize];
                SysLib.rawread(inode.direct[directIndex], leftover);
                System.arraycopy(leftover, 0, writeBuffer, 0, leftover.length);
                System.arraycopy(buffer, bufferIndex, writeBuffer, 0, buffer.length - bufferIndex);
                SysLib.rawwrite(inode.direct[directIndex], writeBuffer);
                seekPtr += buffer.length - bufferIndex;
                ftEnt.seekPtr = seekPtr;
                inode.length += buffer.length;
                return buffer.length;
            }

            // Writes buffer from seekPtr to the end of the block into WriteBuffer
            System.arraycopy(buffer, bufferIndex, writeBuffer, 0, writeBuffer.length);
            seekPtr += writeBuffer.length;
            bufferIndex += writeBuffer.length;
            SysLib.rawwrite(inode.direct[directIndex], writeBuffer);
        }


        // if seek pointer is in indirect block
        if (seekPtr >= inode.directSize * Disk.blockSize) {
            // Allocate new indirect block if there isn't one
            if (inode.indirect == -1) {
                short newBlock = (short)superBlock.getFreeBlock();
                inode.indirect = newBlock;
            }

            // Reads the indirect block
            byte[] indirectBlock = new byte[Disk.blockSize];
            SysLib.rawread(inode.indirect, indirectBlock);
            int offset = (seekPtr - inode.directSize * Disk.blockSize) / Disk.blockSize * 2;
            short blockId;

            // Deal with seek pointer if it is in the middle of a block
            if (seekPtr % Disk.blockSize != 0) {
                int remainder = seekPtr % Disk.blockSize;
                byte[] remainderBuffer = new byte[Disk.blockSize];

                // Locate next block number in indirect block
                // Allocate new block if there isn't a next block
                if (SysLib.bytes2short(indirectBlock, offset) <= 0) {
                    short newBlock = (short)superBlock.getFreeBlock();
                    SysLib.short2bytes(newBlock, indirectBlock, offset);
                    SysLib.rawwrite(inode.indirect, indirectBlock);
                }

                // Locate next block number in indirect block
                blockId = SysLib.bytes2short(indirectBlock, offset);
                SysLib.rawread(blockId, remainderBuffer);
                byte[] writeBuffer = new byte[Disk.blockSize];
                System.arraycopy(remainderBuffer, 0, writeBuffer, 0, remainder);
                
                // If we don't need to write the whole block,
                // just perform write and return
                if (buffer.length - bufferIndex < (Disk.blockSize - remainder)) {
                    System.arraycopy(buffer, bufferIndex, writeBuffer, remainder, buffer.length - bufferIndex);
                    SysLib.rawwrite(blockId, writeBuffer);
                    ftEnt.seekPtr += buffer.length - bufferIndex;
                    inode.length += buffer.length;
                    return buffer.length;
                }

                // Writes buffer from seekPtr to the end of the block into WriteBuffer
                System.arraycopy(buffer, bufferIndex, writeBuffer, remainder, writeBuffer.length - remainder);
                SysLib.rawwrite(blockId, writeBuffer);
                seekPtr += writeBuffer.length - remainder;
                bufferIndex += writeBuffer.length - remainder;
                offset += 2;
            }

            // Write to disk from indirect
            while (bufferIndex < buffer.length) {

                // Locate next block number in indirect block
                // Allocate new block if there isn't a next block
                if ((blockId = SysLib.bytes2short(indirectBlock, offset)) <= 0) {
                    short newBlock = (short)superBlock.getFreeBlock();
                    SysLib.short2bytes(newBlock, indirectBlock, offset);
                    blockId = newBlock;
                    SysLib.rawwrite(inode.indirect, indirectBlock);
                }

                byte[] writeBuffer = new byte[Disk.blockSize];

                // If we don't need to write the whole block,
                // just perform write and return
                if (buffer.length - bufferIndex <= Disk.blockSize) {
                    byte[] leftover = new byte[Disk.blockSize];
                    SysLib.rawread(blockId, leftover);
                    System.arraycopy(leftover, 0, writeBuffer, 0, leftover.length);
                    System.arraycopy(buffer, bufferIndex, writeBuffer, 0, buffer.length - bufferIndex);
                    SysLib.rawwrite(blockId, writeBuffer);
                    seekPtr += buffer.length - bufferIndex;
                    ftEnt.seekPtr = seekPtr;
                    inode.length += buffer.length;
                    return buffer.length;
                }

                // Writes buffer from seekPtr to the end of the block into WriteBuffer
                System.arraycopy(buffer, bufferIndex, writeBuffer, 0, writeBuffer.length);
                seekPtr += writeBuffer.length;
                bufferIndex += writeBuffer.length;
                SysLib.rawwrite(blockId, writeBuffer);
                offset += 2;
            }
        }
        ftEnt.seekPtr = seekPtr;
        inode.length += bufferIndex;
        return bufferIndex;
        }
    }

    // closes the file corresponding to fd, commits all file transactions on this file,
    // and unregisters fd from the user file descriptor table of the calling thread's TCB. 
    // The return value is 0 in success, otherwise -1.
    int close(FileTableEntry ftEnt) {
        synchronized(ftEnt) {
            ftEnt.inode.count--;
            if (ftEnt.inode.count == 0) {
                if (fileTable.ffree(ftEnt)) return 0;
                return -1;
            }
            return 0;
        }
    }

    // reads up to buffer.length bytes from the file indicated by fd, 
    // starting at the position currently pointed to by the seek pointer.
    // The return value is the number of bytes that have been read, 
    // or a negative value upon an error.  
    int read(FileTableEntry ftEnt, byte buffer[]) {
        if (ftEnt.mode.equals("w") || ftEnt.mode.equals("a")) return -1;

        Inode inode = ftEnt.inode;
        int seekPtr = ftEnt.seekPtr;
        int bufferIndex = 0;

        synchronized(ftEnt) {
        // Deal with remainder if pointer does not start from 0
        if (seekPtr % Disk.blockSize != 0 && seekPtr < inode.directSize * Disk.blockSize) {
            int remainder = seekPtr % Disk.blockSize;
            int directIndex = seekPtr / Disk.blockSize;
            if (inode.direct[directIndex] == -1) return 0;

            byte[] readBuffer = new byte[Disk.blockSize];
            SysLib.rawread(inode.direct[directIndex], readBuffer);
            if (buffer.length < (Disk.blockSize - remainder)) {
                System.arraycopy(readBuffer, remainder, buffer, 0, buffer.length);
                ftEnt.seekPtr += buffer.length;
                return buffer.length;
            }
            System.arraycopy(readBuffer, remainder, buffer, 0, readBuffer.length - remainder);
            seekPtr += readBuffer.length - remainder;
            bufferIndex += readBuffer.length - remainder;
        }
        // reads to disk from direct
        while (seekPtr < inode.directSize * Disk.blockSize) {
            int directIndex = seekPtr / Disk.blockSize;
            if (inode.direct[directIndex] == -1) return bufferIndex;

            byte[] readBuffer = new byte[Disk.blockSize];
            SysLib.rawread(inode.direct[directIndex], readBuffer);
            if (buffer.length - bufferIndex <= Disk.blockSize) {
                System.arraycopy(readBuffer, 0, buffer, bufferIndex, buffer.length - bufferIndex);
                seekPtr += buffer.length - bufferIndex;
                ftEnt.seekPtr = seekPtr;
                return buffer.length;
            }
            System.arraycopy(readBuffer, 0, buffer, bufferIndex, readBuffer.length);
            seekPtr += readBuffer.length;
            bufferIndex += readBuffer.length;
        }
        // if seek pointer is in indirect block
        if (inode.indirect == -1) return bufferIndex;

        byte[] indirectBlock = new byte[Disk.blockSize];
        SysLib.rawread(inode.indirect, indirectBlock);
        int offset = (seekPtr - inode.directSize * Disk.blockSize) / Disk.blockSize * 2;
        short blockId;

        // Deal with seek pointer if it is in the middle of a block
        if (seekPtr % Disk.blockSize != 0) {
            int remainder = seekPtr % Disk.blockSize;
            if (offset >= indirectBlock.length) return bufferIndex;

            blockId = SysLib.bytes2short(indirectBlock, offset);
            byte[] readBuffer = new byte[Disk.blockSize];
            SysLib.rawread(blockId, readBuffer);
            
            if (buffer.length - bufferIndex < (Disk.blockSize - remainder)) {
                System.arraycopy(readBuffer, remainder, buffer, 0, buffer.length - bufferIndex);
                ftEnt.seekPtr += buffer.length - bufferIndex;
                return buffer.length;
            }
            System.arraycopy(readBuffer, remainder, buffer, bufferIndex, readBuffer.length - remainder);
            seekPtr += readBuffer.length - remainder;
            bufferIndex += readBuffer.length - remainder;
            offset += 2;
        }

        // Keep reading from indirect block if buffer still has space or it hasn't reached the end
        while (bufferIndex < buffer.length && (blockId = SysLib.bytes2short(indirectBlock, offset)) > 0) {
            if (offset >= indirectBlock.length) return bufferIndex;
            byte[] readBuffer = new byte[Disk.blockSize];
            SysLib.rawread(blockId, readBuffer);
            if (buffer.length - bufferIndex <= Disk.blockSize) {
                System.arraycopy(readBuffer, 0, buffer, bufferIndex, buffer.length - bufferIndex);
                seekPtr += buffer.length - bufferIndex;
                ftEnt.seekPtr = seekPtr;
                return buffer.length;
            }
            System.arraycopy(readBuffer, 0, buffer, bufferIndex, readBuffer.length);
            seekPtr += readBuffer.length;
            bufferIndex += readBuffer.length;
            offset += 2;
        }
        ftEnt.seekPtr = seekPtr;
        return bufferIndex;
        }
    }

    // returns the size in bytes of the file indicated by fd.
    int fsize(FileTableEntry dirEnt) {
        synchronized (dirEnt) {
            return dirEnt.inode.length;
        }
    }

    // Sets seekPtr by offset based on whence
    int seek(FileTableEntry ftEnt, int offset, int whence) {
        if (whence == 0) {
            ftEnt.seekPtr = offset;
        } else if (whence == 1) {
            ftEnt.seekPtr += offset;
        } else if (whence == 2) {
            ftEnt.seekPtr = ftEnt.inode.length + offset;
        } else return -1;
        if (ftEnt.seekPtr < 0) ftEnt.seekPtr = 0;
        else if (ftEnt.seekPtr > ftEnt.inode.length) ftEnt.seekPtr = ftEnt.inode.length;
        return ftEnt.seekPtr;
    }

    // destroys the file specified by fileName
    // If the file is currently open, it is not destroyed until 
    // the last open on it is closed, but new attempts to open it will fail.  
    int delete(String filename) {
        short iNumber = directory.namei(filename);
        if (iNumber == -1) return -1;
        if (directory.ifree(iNumber)) return 0;
        return -1; 
    }
}
