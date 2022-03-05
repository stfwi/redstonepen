package wile.api.rca;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * File Memory Mapped Redstone Client Adapter implementation.
 */
@SuppressWarnings("unused")
public class FmmRedstoneClientAdapter
{
  /**
   * Memory Mapping auxiliary class.
   * Wraps the common processes of using MappedByteBuffer/RandomAccessFile
   * in java. Under Linux the native `mmap()` functionality will be implicitly
   * used, Windows provides the similar functionality via the `CreateFileMapping()`/
   * `OpenFileMapping()` WINAPI functions (a bit more is needed, but in essence
   * these are the key functions).
   */
  private static class FileMemMap
  {
    private final String mapFilePath;
    private final int mapOffset;
    private final int mapSize;
    private final byte[] mapData;
    private final boolean isWrite;
    private final int reopenDelay;
    private RandomAccessFile file = null;
    private FileChannel channel = null;
    private MappedByteBuffer buffer = null;

    public FileMemMap(String path, boolean output, int reopen_delay, int size)
    {
      mapFilePath = path;
      reopenDelay = reopen_delay;
      isWrite = output;
      mapOffset = 0;
      mapSize = size;
      mapData = new byte[mapSize];
      Arrays.fill(mapData, (byte)'0');
    }

    public String path()
    { return mapFilePath; }

    public int size()
    { return mapSize; }

    public byte[] data()
    { return mapData; }

    public byte get(int index)
    { return (index<0 || index>=size()) ? (0) : (mapData[index]); }

    public void set(int index, byte value)
    { if((index>=0) && (index<size())) mapData[index] = value; }

    public boolean closed()
    { return (buffer == null) || (file == null); }

    public boolean open(boolean initialize)
    {
      if((mapOffset <0) || (size()<=0) || (size()>4096)) {
        return false;
      }
      close();
      try {
        if(initialize) {
          if(!Files.exists(Path.of(mapFilePath)) || (Files.size(Path.of(mapFilePath)) < size())) {
            Files.write(Path.of(mapFilePath), mapData);
          }
        }
        file = new RandomAccessFile(new File(mapFilePath), isWrite ? "rw" : "r");
        channel = file.getChannel();
        if(channel.size() < size()) {
          close();
          return false;
        }
        buffer = channel.map(isWrite ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY, 0, size());
        if(isWrite) {
          buffer.clear().put(mapData);
        }
        return true;
      } catch(Throwable ex) {
        close();
      }
      return false;
    }

    public void close()
    {
      if(channel != null) try { channel.close(); } catch(Throwable ignored){}
      if(file != null) try { file.close(); } catch(Throwable ignored){}
      channel = null;
      file = null;
      buffer = null;
    }

    public void remove()
    { try { @SuppressWarnings("unused") boolean b=new File(mapFilePath).delete(); } catch(Throwable ignored){} }

    public boolean tick()
    {
      try {
        if(isWrite) {
          buffer.clear().put(mapData);
        } else {
          buffer.clear().get(mapData);
        }
        return true;
      } catch(Throwable ex) {
        close();
      }
      return false;
    }
  }

  /**
   * Memory Mapped IPC adapter implementation.
   */
  public static class Adapter implements RedstoneClientAdapter
  {
    private static final int ERROR_RELOAD_DELAY = 20;   // MC side: Corresponds to 1 second.
    private static final int MAP_SIZE = 16;             // 16 hex digits -> int64_t === long.
    private static Adapter singletonInstance = null;    // MC side: Adapter instance.
    private static boolean featureEnabled = true;       // MC side: Fast-branch feature enabled value.
    private final FileMemMap inFile;                    // Mapped IPC input file for this implementation side.
    private final FileMemMap outFile;                   // Mapped IPC output file for this implementation side.
    private final int reopenDelay;                      // Adapter side dependent retry-after-error delay.
    private final boolean isSystemSide;                 // Adapter side (false:mod, true:system).
    private long inputDataWord = 0;                     // Cached IPC input data word.
    private long outputDataWord = 0;                    // Cached IPC output data word.
    private boolean inputDataChanged = false;           // Cached IPC input data dirty marker.
    private boolean outputDataChanged = true;           // Cached IPC output data dirty marker.
    private boolean ipcOpen = false;                    // Indicator that both mmap paths are opened successfully.

    /**
     * Returns the IPC map file path ("[prefix].i.mmap" or "[prefix].o.mmap") from
     * the reference perspective of the MC mod side.
     *
     * @param mcSideOutput True if output for the mod side and input for system side, or vice versa.
     * @return System formatted IPC file path string representation.
     */
    public static String ipcIoPath(boolean mcSideOutput)
    { return Path.of(System.getProperty("java.io.tmpdir"), "redstonepen").toString() + "." + (mcSideOutput ? 'o' : 'i') +".mmap"; }

    /**
     * Returns true if the feature is enabled. Initialized after
     * the first invocation of `instance()`.
     * @return bool
     */
    public static boolean available()
    { return (featureEnabled) && (singletonInstance != null); }

    /**
     * Mod side singleton instance getter. Returns `null`
     * on error or if the preconditions to allow enabling
     * this feature are not met.
     *
     * If you use this class for writing a system-side
     * adapter program, call the constructor directly.
     *
     * @return Adapter
     */
    public static Adapter instance()
    {
      if(!featureEnabled) {
        return null; // Fast path, referrs to >=99% of all users.
      } else if(singletonInstance != null) {
        return singletonInstance; // Already instantiated.
      } else {
        // Instantiation if the system side adapter has created
        // the IPC files, otherwise disable the feature completly
        // until MC is restarted.
        try {
          if((!Files.exists(Path.of(ipcIoPath(false))))
          || (!Files.exists(Path.of(ipcIoPath(true ))))
          ) {
            featureEnabled = false;
          } else {
            singletonInstance = new Adapter(false);
          }
        } catch(Exception ignored) {
          featureEnabled = false;
        }
        return singletonInstance;
      }
    }

    /**
     * File Memory Mapped Adapter constructor. On the mod side
     * this will always be instantiated with argument `false`,
     * system-side IPC adapter applications must instantiate
     * with `true`. This mainly affects the input/output file
     * naming, but also retry-delays on the mod side.
     *
     * @param systemSide True for system-side applications, false for the mod implementation.
     */
    public Adapter(boolean systemSide)
    {
      isSystemSide = systemSide;
      reopenDelay = systemSide ? 0 : ERROR_RELOAD_DELAY;
      inFile  = new FileMemMap(ipcIoPath(systemSide), false, reopenDelay, MAP_SIZE);
      outFile = new FileMemMap(ipcIoPath(!systemSide), true, reopenDelay, MAP_SIZE);
    }

    /**
     * Input data getter.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public long getInputs()
    { return inputDataWord; }

    /**
     * Input data setter.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public void setInputs(long val)
    { if(val != inputDataWord) { inputDataWord = val; inputDataChanged = true; } }

    /**
     * Input changed/"dirty" getter.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public boolean isInputsChanged()
    { return inputDataChanged; }

    /**
     * Input changed/"dirty" setter.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public void setInputsChanged(boolean changed)
    { inputDataChanged = changed; }

    /**
     * Output data getter.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public long getOutputs()
    { return outputDataWord; }

    /**
     * Output data setter.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public void setOutputs(long val)
    { if(val != outputDataWord) { outputDataWord = val; outputDataChanged = true; } }

    /**
     * Output changed/"dirty" getter.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public boolean isOutputsChanged()
    { return outputDataChanged; }

    /**
     * Output changed/"dirty" setter.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public void setOutputsChanged(boolean changed)
    { outputDataChanged = changed; }

    /**
     * IPC opened getter.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public boolean isOpen()
    { return ipcOpen; }

    /**
     * Timed/client tick related cyclic IPC I/O method.
     * @see wile.api.rca.RedstoneClientAdapter
     */
    public void tick()
    {
      if(!ipcOpen) {
        if(isSystemSide) {
          // Sys side: Open and initialize/create actual map files. If one
          // fails a retry is done next tick.
          ipcOpen = inFile.open(true) && outFile.open(true);
        } else if(inFile.closed() || outFile.closed()) {
          // MC side: Force reopen both files. One of the files may have been
          // deleted and re-created, so that the mem-maps would read/write
          // valid but obsilete buffers.
          ipcOpen = inFile.open(false) && outFile.open(false);
        }
      } else {
        // Both memory maps there and opened, IPC possible.
        if(inFile.tick()) {
          // Input buffer update successful, convert and
          // check for changes.
          long val = 0;
          for(int i=0; i<16; ++i) {
            val = (val<<4) | hex2nibble(inFile.get(i));
          }
          if(val != inputDataWord) {
            inputDataWord = val;
            inputDataChanged = true;
          }
        }
        if(outputDataChanged) {
          // Data in the buffer have to be updated.
          long val = outputDataWord;
          for(int i=15; i>=0; --i) {
            byte b = nibble2hex((int)(val & 0xf));
            outFile.set(i, b);
            val >>= 4;
          }
          outputDataChanged = !outFile.tick();
        }
        // Any error will cause a silent retry-reopen next tick.
        ipcOpen = (!inFile.closed()) && (!outFile.closed());
      }
    }

    /**
     * Traditional exception free, zero defaulting hex digit parsing.
     * Invalid characters yield zero.
     */
    private static int hex2nibble(byte b)
    {
      if((b>=(byte)'A') && (b<=(byte)'F')) return (b-((byte)'A')+10);
      if((b>=(byte)'a') && (b<=(byte)'f')) return (b-((byte)'a')+10);
      if((b>=(byte)'0') && (b<=(byte)'9')) return (b-((byte)'0'));
      return 0;
    }

    /**
     * Traditional exception free, zero defaulting hex digit composing.
     * Invalid input data are returned as ASCII '0' character value.
     */
    private static byte nibble2hex(int n)
    {
      if(n <= 0x0) return ((byte)'0');
      if(n <= 0x9) return (byte)(((byte)'0')+n);
      if(n <= 0xf) return (byte)(((byte)'a')+(n-10));
      return ((byte)'0'); // invalid->0, not 0xf clamped.
    }
  }
}
