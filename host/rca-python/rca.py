#!/usr/bin/python3
import mmap, os

#
# RCA python3 implementation.
#
class RedstoneClientAdapter:

  ## Constructor
  def __init__(self, game_directory='.'):
    # Auxiliary expressions, redstone value range limiting and hex dump.
    lim = lambda x: [max(min(v, 15), 0) for v in x]
    self._xd = lambda x:"".join([f"{v:1x}" for v in reversed(lim(x))]) # hexdump
    self._xp = lambda x: list(map(lambda x: int(x, 16), reversed(list(x))))
    # Instance variables
    self._ifile = None
    self._imap = None
    self._ofile = None
    self._omap = None
    # Directory check before creating I/O files
    fp = lambda fn: os.path.join(game_directory, fn)
    if not os.path.isfile(fp("usercache.json")):
      raise ValueError("Given game directory does not contain the usercache.json file (" + os.path.abspath(game_directory) + ").")
    ipath = fp("redstonepen.i.mmap")
    opath = fp("redstonepen.o.mmap")
    # Create files
    ifile = open(ipath, mode="wt")
    ifile.write("0000000000000000\n")
    ifile.close()
    ofile = open(opath, mode="wt")
    ofile.write("0000000000000000\n")
    ofile.close()
    # Open memory maps
    self._ifile = open(ipath, mode="r+b")
    self._imap = mmap.mmap(self._ifile.fileno(), 16, access=mmap.ACCESS_WRITE)
    self._ofile = open(opath, mode="r+b")
    self._omap = mmap.mmap(self._ofile.fileno(), 16, access=mmap.ACCESS_READ)

  ## Destructor, optional
  def __del__(self):
    self.close()

  ## Close files and memory maps
  def close(self):
    if(self._imap != None):
      self._imap.close()
      self._imap = None
    if(self._omap != None):
      self._omap.close()
      self._omap = None
    if(self._ifile != None):
      self._ifile.close()
      self._ifile = None
    if(self._ofile != None):
      self._ofile.close()
      self._ofile = None

  # Set mod input channels as complete array/list
  def set_input_channels(self, redstone_value_list):
    if(len(redstone_value_list) != 16):
      raise ValueError("Value list not a list of 16 redstone values")
    self._imap.seek(0)
    self._imap.write(bytearray(self._xd(redstone_value_list), 'ascii'))

  # Get mod input channels as complete array/list
  def get_input_channels(self):
    self._imap.seek(0)
    return self._xp(self._imap.read(16).decode('ascii'))

  # Get mod output channels as complete array/list
  def get_output_channels(self):
    self._omap.seek(0)
    return self._xp(self._omap.read(16).decode('ascii'))

#eof
