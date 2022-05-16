#!/usr/bin/djs
/**
 * CLI (duktape-cc) based JS interpreter mmap functionality
 * based RCA "class".
 * @std es5
 *
 * @param {boolean} reverse Reverse (MC side simulation).
 * @constructor
 */
include("./redstone-client-adapter.djs");


const run_control = {
  exit: false,
  read_timeout: 50e-3,
  logger: function(line) { alert("[info] " + line.replace(/[\r\n]/g," | ")); }
};

/**
 * Bridges serial port data and the RCA file memory map.
 */
 function run_serial_port_adapter(port)
 {
   port = port || (function(){
     const ports = sys.serialport.portlist();
     if(ports.length==0) return "";
     var port_path = "";
     for(var name in ports) {
       port_path = ports[name];
       if(name.search(/^(ttyusb|vcp)/i)>=0) break;
     }
     if(!port_path) {
       throw new Error("No serial ports detected.");
     }
     return port_path; // in doubt the last one, as the mcu is most likely the last one added.
   })();

   const tty = new sys.serialport();
   if(!tty.open(port, "115200n81")) {
     throw new Error("Could not open '"+port+": " + tty.errormessage);
   }

   var rx_buffer = "";
   const receive_serial_data = function(timeout) {
     var rx_chars = "";
     try { rx_chars = tty.read(timeout); } catch(ex) { run_control.logger("[excp] " + ex.message); }
     if(!rx_chars) return []; // nothing received
     rx_buffer += rx_chars;
     if(rx_buffer.length > 1024) rx_buffer = ""; // 1k line length is more than enough
     if(rx_chars.search(/[\r\n]/)<0) []; // no full line received
     const lines = rx_buffer.split(/[\n\r]+/);
     rx_buffer = lines.pop(); // last unfinished line chunk, often empty.
     const data = lines.filter(function(line){
       line = line.replace(/\s+/,"");
       if(line.search(/^[0-9a-fA-F]{16}$/) === 0) return true;
       run_control.logger("rx: '"+ line +"'");
       return false;
     });
     return (data.length>0) ? (lines.pop()) : ""; // Only the last data line is relevant.
   };

   const rca = new RedstoneClientAdapter(false);

   while(!run_control.exit) {
     // A fixed thread sleep can save some performance depending on the host machine.
     // Let's say the baud rate (aka bit rate) is 115200, and every sent byte has one
     // start bit, 8 data bits, and one stop bit. Then we need about the time for 10bit
     // for sending one byte, and a frame (line) has 16 hex digits + 1 newline character.
     // That is 115200/10/17 = 677 frames per second, or (1/x) or about 1.5ms.
     sys.sleep(20e-3);
     if(!tty.isopen) { tty.close(); tty.open(); }
     const channels_hex = receive_serial_data(run_control.read_timeout-20e-3);
     if(channels_hex != "") {
       rca.data(channels_hex);
     }
   }
   tty.close();
   return 0;
 }

/**
 * Rudimentary integration test sequence.
 * @returns {number} exit code
 */
function test_sequence_values(mc_side_simulation, tick_delay_ms, infinitely, minor)
{
  mc_side_simulation = mc_side_simulation || false;
  tick_delay_ms = (tick_delay_ms || 10e-3);
  alert("Test sequence, mc-side="+mc_side_simulation+", tick_delay_ms="+tick_delay_ms+" ...");
  const rca = new RedstoneClientAdapter(mc_side_simulation);
  const test_data = [];
  while(test_data.length < rca.num_channels()) { test_data.push(0); }
  const setrca = function(data) {
    rca.data(data);
    sys.sleep(tick_delay_ms);
    print("i:"+JSON.stringify(rca.data()) + " | o:" + JSON.stringify(test_data));
  };
  const valfor = function(v) { return v; }
  do {
    if(minor) {
      for(var channel=0; channel < rca.num_channels(); ++channel) {
        test_data[channel] = valfor(channel + (channel>=8 ? -1 : 1));
      }
      setrca(test_data);
      for(var channel=0; channel < rca.num_channels(); ++channel) {
        test_data[channel] = valfor(channel);
      }
      setrca(test_data);
    } else {
      for(var val=0; val<16; ++val) {
        for(var channel=0; channel < rca.num_channels(); ++channel) {
          test_data[channel] = valfor(val);
          setrca(test_data);
        }
      }
      for(var val=15; val>=0; --val) {
        for(var channel=0; channel < rca.num_channels(); ++channel) {
          test_data[channel] = valfor(val);
          setrca(test_data);        }
      }
    }
  } while(infinitely && !run_control.exit);
  return 0;
}

function test_mod_side_data_reflection(channel_map, mcside)
{
  channel_map = channel_map || "0123456789abcdef"; // Input channel to output channel map indices.
  channel_map = channel_map.split("").map(function(e){ return Number.parseInt(e,16) }).filter(function(e){ return !Number.isNaN(e)});
  if(channel_map.length != 16) throw new Error("map argument must be 16 hex digits, defining which input channel is echoed at which output channel.");
  print("Channel echo mapping: " + JSON.stringify(channel_map));
  const rca = new RedstoneClientAdapter(!!mcside);
  var last_input = "";
  while(true) {
    sys.sleep(1e-3);
    const inp = rca.data();
    const json_inp = JSON.stringify(inp);
    if(last_input == json_inp) continue;
    last_input = json_inp;
    const out = channel_map.map(function(i){return inp[i];});
    rca.data(out);
    print("i:"+JSON.stringify(inp) + " | o:" + JSON.stringify(out));
  }
}


/**
 * Main function. Called implicitly after all JS
 * code and libraries are loaded.
 *
 * @param {string[]} args
 * @returns {number}
 */
function main(args)
{
  const command = args.shift() || "";
  const mc_side = args.indexOf("mc-side")>=0;
  if(command == "run") {
    return run_serial_port_adapter.apply(null, args);
  } else if(command == "echo") {
    const test_map_arduino_nano = "cdef0123456789ab";
    return test_mod_side_data_reflection(test_map_arduino_nano);
  } else if(command == "test") {
    const tick_delay = 200e-3;
    return test_sequence_values(mc_side, tick_delay, args.indexOf("inf")>=0, args.indexOf("minor")>=0);
  } else {
    alert( ((command=="") ? "No command given (1st argument)." : ("Unknown command '"+command+"'")) + ", say 'test [inf]' or 'run [<serial-port>]' or 'echo'" );
    return 1;
  }
}
