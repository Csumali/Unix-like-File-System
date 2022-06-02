import java.io.*;

/**
 * Directly calls SysLib.exec( "Loader" ) to launch a loader thread (tid[0]).
 */
public class Boot
{
    static final int OK = 0;
    static final int ERROR = -1;

    /**
     * Invokes SysLib.boot( ) to start a scheduler and a disk thread,
     * creates a disk cache, and thereafter invokes SysLib.exec(
     * "Loader" ) to launch a loader thread.
     * @param args[] won't be used.
     */
    public static void main ( String args[] ) {
	SysLib.cerr( "threadOS ver 2.0:\n" );
	SysLib.boot( );
	SysLib.cerr( "Type ? for help\n" );

	String[] loader = new String[1];
	loader[0] = "Loader";
	SysLib.exec( loader );
    }
}
