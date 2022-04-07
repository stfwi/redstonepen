package wile.api.rca;

@SuppressWarnings("unused")
public interface RedstoneClientAdapter
{

  /**
   * Frequently invoked tick function, transferring cached
   * RAM data from and to the adapter application on the
   * system side.
   *
   * - The method shall manage reading and writing to this
   *    channel, but also opening, closing, and error/exception
   *    handling.
   *
   * - I/O should be implemented nonblocking.
   *
   * - Inputs are updated, and if changed, marked as changed.
   *
   * - If the outputs are uninitialized or marked as changed,
   *   their value shall be written and the change-marker
   *   unset.
   */
  void tick();

  /**
   * Returns true if the used IPC channels are successfully
   * opened at adapter side of this application. This may not
   * guarantee that a bidirectional communication is possible
   * (depending on the IPC method used), but indicate to the
   * tick() method to close-retry-open, as well as indicate
   * a "issue" marker to the user.
   * Note: A serial port, or a pipe/fifo, or a shared
   *       memory, or UDP socket may be opened successfully,
   *       but is not possible to determine if someone is on
   *       the other side.
   */
  boolean isOpen();

  /**
   * Returns the RAM stored/cached input data word,
   * holding 16 4bit channels.
   * @return The cached input data word.
   */
  long getInputs();

  /**
   * Sets the RAM stored/cached input data word,
   * and marks the input word as changed.
   * @param value The new input data word.
   */
  void setInputs(long value);

  /**
   * Returns true if the input data word is marked
   * as changed.
   * @return True if the input is marked changed, false otherwise.
   */
  boolean isInputsChanged();

  /**
   * Sets/resets the input-changed marker.
   * @param changed New value of the changed-marker.
   */
  void setInputsChanged(boolean changed);

  /**
   * Returns the RAM stored/cached output data word,
   * holding 16 4bit channels.
   * @return The cached output word value.
   */
  long getOutputs();

  /**
   * Sets the RAM stored/cached output data word,
   * and marks the input word as changed.
   * @param value The new output word value.
   */
  void setOutputs(long value);

  /**
   * Returns true if the output data word is marked
   * as changed.
   * @return True if the changed-marker is set, false otherwise.
   */
  boolean isOutputsChanged();

  /**
   * Sets/resets the output-changed marker.
   */
  void setOutputsChanged(boolean changed);

  /**
   * RAM cached channel access: Number of available channels.
   */
  default int numChannels() { return 16; }

  /**
   * RAM cached channel access: Input value for the given
   * channel.
   * @param channel Channel index 0..15
   * @return Input channel value 0..15
   */
  default int getInputChannel(int channel)
  { return (int)((getInputs()>>(4*channel)) & 0xf); }

  /**
   * RAM cached channel access: Output value for the given
   * channel.
   * @param channel Channel index 0..15
   * @return Output channel value 0..15
   */
  default int getOutputChannel(int channel)
  { return (int)((getOutputs()>>(4*channel)) & 0xf); }

  /**
   * RAM cached channel access: Input value assignment for
   * the given channel.
   * @param channel Channel index 0..15
   * @param value Input channel value 0..15
   */
  default void setInputChannel(int channel, int value)
  { setInputs((getInputs() & (~((long)0xf)<<(4*channel))) | ((long)(Math.min(15, Math.max(0, value)) & 0xf))<<(4*channel)); }

  /**
   * RAM cached channel access: Output value assignment for
   * the given channel.
   * @param channel Channel index 0..15
   * @param value Output channel value 0..15
   */
  default void setOutputChannel(int channel, int value)
  { setOutputs((getOutputs() & (~((long)0xf)<<(4*channel))) | ((long)(Math.min(15, Math.max(0, value)) & 0xf))<<(4*channel)); }

}
