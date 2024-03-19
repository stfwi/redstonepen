
## C++ RCA Example implementation

This example shows a simple example how to interface the RLC
with a c++ program via file memory mapping.

#### Building

To compile, you need to have

  - g++ (must support c++17, tested with versions >= 11.0.0),
  - GNU Make (tested with versions >= v4.1),
  - Unix standard tools (for Windows, this comes with GIT if
    you install it globally. Things like `ls`, `cp`, `rm`
    have to directly work when typed in the shell/cmd).

The Make targets implemented are:

  - `make clean`: Clears caches/build artifacts
  - `make binary`: Compiles and links the example program in ./build/rca-test
  - `make all`: Convenience alias of `make binary`.
  - `make run`: Compile and run in one go.

#### Code

The application entry point, as likely expected, is `int main(...){}`
in `./src/main.cc`. In `./include` there is

  -  `util.hh`: Convenience wrapper functions for better example
      readability.

  -  `mmap.hh`: Exports class `memory_mapped_file`, which is used
      to open and map the RCA input and output files.

  -  `rca.hh`: Exports class `rlc_client_adapter`, which provides
     the `DI0..DI15` and `DO0..DO15` channel access from the c++
     side to the `main.cc`.

#### Example Program Behavior

By default, the test program runs for 60s. During that time, it ticks
about every 10ms (simple main loop with thread sleep, basically double
as fast as a Minecraft tick). Changes in RLC RCA outputs are tracked
as printed line.

Every 250ms (25 ticks), it increments the channel that is mapped to
`DI0`. So in MC, we should see that `DI0` counts up to 15 and restarts
at 0.

All other channels are example echo channels. So if an RLC sets `DO1`,
then `DI1` will have the same value after it was communicated forth and
back.

You can modify the application runtime with the first command line
argument (in milliseconds). So either `make run ARGS=600000` or
`make binary; ./build/rca-test 600000` will let the program for 10min.

#### Example Run

*Start the app at least once before starting Minecraft*, because the mod
will not enable RCA if it does not see the I/O map files in your temp
directory.

RLC program used was:

  ```sh
  # 250ms incremented by RCA c++
  R = DI0

  # 10 tick counter setting DO0
  # every 500ms (10tk*50ms)
  DO0 = CNT1(TIV1(10)) % 16

  # Echo channels
  DO4 = (DO4+1) % 16
  DO5 = (DO5+2) % 16
  DO6 = (DO6+4) % 16

  # Forward Green to Blue over RCA
  DO8 = G
  B = DI8
  ```

Application output:

  ```sh
  $ make run ARGS=20000
  [c++ ] build/rca-test
  [run ] Starting rca-test ...
  Test run-time set to 20000 via CLI argument.
  # --(comments)------------------------------------------------------
  #  TIME/ticks  | RLC DOx (written by RLC) | RLC DIx (written by APP)
  # ------------------------------------------------------------------
  #
  #                                DO8 = G
  #                                | DO6 = (DO6+4) % 16
  #                                | |DO5 = (DO5+2) % 16
  #                                | ||DO4 = (DO4+1) % 16
  #                                | |||   DO0 = CNT1(TIV1(10)) % 16
  #                                | |||   |
  #                                | |||   |                 echo DO8
  #                                | |||   |                 | echo DO4..6
  #                                | |||   |                 | |||   if(tick % 25 == 0) rca.set_input(...);
  #                                | |||   |                 | |||   |
  #                                | |||   |                 | |||   |
  tick=0x00000088 outputs=0x0000000008420001 inputs=0x0000000000000006
  tick=0x00000095 outputs=0x000000000c630001 inputs=0x0000000008420006
  tick=0x000000a1 outputs=0x0000000000840001 inputs=0x000000000c630007
  tick=0x000000ae outputs=0x0000000008c60002 inputs=0x0000000000840007
  tick=0x000000ba outputs=0x000000000ce70002 inputs=0x0000000008c60008
  tick=0x000000c7 outputs=0x00000000084a0003 inputs=0x000000000ce70008
  tick=0x000000d3 outputs=0x000000000c6b0003 inputs=0x00000000084a0009
  tick=0x000000df outputs=0x00000000008c0003 inputs=0x000000000c6b0009
  tick=0x000000ec outputs=0x0000000008ce0004 inputs=0x00000000008c000a
  tick=0x000000f8 outputs=0x000000000cef0004 inputs=0x0000000008ce000a
  tick=0x00000105 outputs=0x0000000f08420005 inputs=0x000000000cef000b
  tick=0x00000111 outputs=0x0000000f0c630005 inputs=0x0000000f0842000b
  tick=0x0000011e outputs=0x0000000f00840005 inputs=0x0000000f0c63000c
  tick=0x0000012a outputs=0x0000000f08c60006 inputs=0x0000000f0084000c
  tick=0x00000137 outputs=0x0000000f0ce70006 inputs=0x0000000f08c6000d
  tick=0x00000144 outputs=0x0000000f084a0007 inputs=0x0000000f0ce7000d
  tick=0x00000150 outputs=0x0000000f0c6b0007 inputs=0x0000000f084a000e
  tick=0x0000015d outputs=0x0000000f008c0007 inputs=0x0000000f0c6b000e
  tick=0x00000169 outputs=0x000000000cef0008 inputs=0x0000000f008c000f
  tick=0x00000176 outputs=0x0000000000000008 inputs=0x000000000cef000f
  tick=0x00000182 outputs=0x0000000008420009 inputs=0x0000000000000000
  tick=0x0000018f outputs=0x000000000c630009 inputs=0x0000000008420000
  tick=0x0000019b outputs=0x0000000000840009 inputs=0x000000000c630001
  tick=0x000001a8 outputs=0x0000000008c6000a inputs=0x0000000000840001
  tick=0x000001b4 outputs=0x000000000ce7000a inputs=0x0000000008c60002
  tick=0x000001c1 outputs=0x0000000f084a000b inputs=0x000000000ce70002
  tick=0x000001ce outputs=0x0000000f0c6b000b inputs=0x0000000f084a0003
  tick=0x000001da outputs=0x0000000f008c000b inputs=0x0000000f0c6b0003
  tick=0x000001e7 outputs=0x0000000f08ce000c inputs=0x0000000f008c0004
  tick=0x000001f3 outputs=0x0000000f0cef000c inputs=0x0000000f08ce0004
  tick=0x00000200 outputs=0x0000000f0842000d inputs=0x0000000f0cef0005
  tick=0x0000020c outputs=0x0000000f0c63000d inputs=0x0000000f08420005
  tick=0x00000217 outputs=0x0000000f0084000d inputs=0x0000000f0c630006
  tick=0x00000224 outputs=0x0000000f08c6000e inputs=0x0000000f00840006
  tick=0x00000230 outputs=0x000000000008000e inputs=0x0000000f08c60007
  tick=0x0000023d outputs=0x00000000084a000f inputs=0x0000000000080007
  tick=0x0000024a outputs=0x000000000c6b000f inputs=0x00000000084a0008
  tick=0x00000256 outputs=0x00000000008c000f inputs=0x000000000c6b0008
  tick=0x00000263 outputs=0x0000000008ce0000 inputs=0x00000000008c0009
  tick=0x0000026f outputs=0x000000000cef0000 inputs=0x0000000008ce0009
  tick=0x0000027c outputs=0x0000000008420001 inputs=0x000000000cef000a
  tick=0x00000289 outputs=0x000000000c630001 inputs=0x000000000842000a
  tick=0x00000295 outputs=0x0000000000840001 inputs=0x000000000c63000b
  tick=0x000002a1 outputs=0x0000000008c60002 inputs=0x000000000084000b
  tick=0x000002ae outputs=0x0000000f00080002 inputs=0x0000000008c6000c
  tick=0x000002ba outputs=0x0000000f084a0003 inputs=0x0000000f0008000c
  tick=0x000002c4 outputs=0x0000000f0c6b0003 inputs=0x0000000f084a000d
  tick=0x000002d1 outputs=0x0000000f008c0003 inputs=0x0000000f0c6b000d
  tick=0x000002dd outputs=0x0000000f08ce0004 inputs=0x0000000f008c000e
  tick=0x000002e9 outputs=0x0000000f0cef0004 inputs=0x0000000f08ce000e
  tick=0x000002f6 outputs=0x0000000f08420005 inputs=0x0000000f0cef000f
  tick=0x00000303 outputs=0x0000000f0c630005 inputs=0x0000000f0842000f
  tick=0x0000030f outputs=0x0000000f00840005 inputs=0x0000000f0c630000
  tick=0x0000031c outputs=0x0000000f08c60006 inputs=0x0000000f00840000
  tick=0x00000328 outputs=0x0000000f0ce70006 inputs=0x0000000f08c60001
  tick=0x00000335 outputs=0x0000000f084a0007 inputs=0x0000000f0ce70001
  tick=0x00000341 outputs=0x0000000f0c6b0007 inputs=0x0000000f084a0002
  tick=0x0000034e outputs=0x0000000f008c0007 inputs=0x0000000f0c6b0002
  tick=0x0000035a outputs=0x0000000f08ce0008 inputs=0x0000000f008c0003
  tick=0x00000367 outputs=0x000000000cef0008 inputs=0x0000000f08ce0003
  tick=0x00000374 outputs=0x0000000008420009 inputs=0x000000000cef0004
  tick=0x00000380 outputs=0x000000000c630009 inputs=0x0000000008420004
  tick=0x0000038d outputs=0x0000000000840009 inputs=0x000000000c630005
  tick=0x00000399 outputs=0x0000000008c6000a inputs=0x0000000000840005
  tick=0x000003a6 outputs=0x000000000ce7000a inputs=0x0000000008c60006
  tick=0x000003b2 outputs=0x00000000084a000b inputs=0x000000000ce70006
  tick=0x000003bf outputs=0x000000000c6b000b inputs=0x00000000084a0007
  tick=0x000003cb outputs=0x00000000008c000b inputs=0x000000000c6b0007
  tick=0x000003d8 outputs=0x0000000008ce000c inputs=0x00000000008c0008
  tick=0x000003e4 outputs=0x000000000cef000c inputs=0x0000000008ce0008
  tick=0x000003f1 outputs=0x000000000842000d inputs=0x000000000cef0009
  tick=0x000003fe outputs=0x000000000c63000d inputs=0x0000000008420009
  tick=0x0000040a outputs=0x000000000084000d inputs=0x000000000c63000a
  tick=0x00000417 outputs=0x0000000f0ce7000e inputs=0x000000000084000a
  tick=0x00000423 outputs=0x0000000f0008000e inputs=0x0000000f0ce7000b
  tick=0x00000430 outputs=0x0000000f084a000f inputs=0x0000000f0008000b
  tick=0x0000043c outputs=0x0000000f0c6b000f inputs=0x0000000f084a000c
  tick=0x00000449 outputs=0x0000000f008c000f inputs=0x0000000f0c6b000c
  tick=0x00000455 outputs=0x0000000f08ce0000 inputs=0x0000000f008c000d
  tick=0x00000462 outputs=0x0000000f0cef0000 inputs=0x0000000f08ce000d
  tick=0x0000046e outputs=0x0000000f08420001 inputs=0x0000000f0cef000e
  tick=0x0000047b outputs=0x0000000f0c630001 inputs=0x0000000f0842000e
  tick=0x00000488 outputs=0x0000000f00840001 inputs=0x0000000f0c63000f
  tick=0x00000494 outputs=0x0000000f08c60002 inputs=0x0000000f0084000f
  tick=0x000004a1 outputs=0x0000000f0ce70002 inputs=0x0000000f08c60000
  tick=0x000004ad outputs=0x00000000084a0003 inputs=0x0000000f0ce70000
  tick=0x000004ba outputs=0x000000000c6b0003 inputs=0x00000000084a0001
  tick=0x000004c6 outputs=0x00000000008c0003 inputs=0x000000000c6b0001
  tick=0x000004d3 outputs=0x0000000008ce0004 inputs=0x00000000008c0002
  tick=0x000004df outputs=0x000000000cef0004 inputs=0x0000000008ce0002
  tick=0x000004ec outputs=0x0000000008420005 inputs=0x000000000cef0003
  tick=0x000004f7 outputs=0x000000000c630005 inputs=0x0000000008420003
  exit.
  ```

~~~
