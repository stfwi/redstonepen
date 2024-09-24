#
# Python3 RCA example.
#
import sys, os, time
sys.path.append(os.path.abspath("."))
from rca import *

# Instantiate RCA, write initial DI data:
rca = RedstoneClientAdapter(game_directory=".")
rca.set_input_channels([0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15])

# Example loop dumping DO0..15, DI0..15, and modifying DI0 and DI1 by counting.
t0 = time.time()
test1 = 0
test2 = 0

for i in range(1000):
  t = time.time() - t0
  test1 = (test1+1) % 16
  test2 = int(test1/4) * 4
  rca.set_input_channels([test1,test2,2,3,4,5,6,7,8,9,10,11,12,13,14,15])
  di_channels = rca.get_input_channels()
  do_channels = rca.get_output_channels()
  print("t:", f'{t:8.3f}', "| DI[0..15]:", di_channels, "| DO[0..15]:", do_channels)
  time.sleep(100e-3)

rca.close()

#eof
