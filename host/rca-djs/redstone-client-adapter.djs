/**
 * @file redstone-client-adapter.djs
 * @std es5
 *
 * JavaScript (duktape-cc) I/O implementation for the RCA.
 */


/**
 * Redstone Client Adapter I/O "class".
 * @constructor
 * @param {boolean} simulate_mc_side
 */
function RedstoneClientAdapter(simulate_mc_side)
{
  const reverse = simulate_mc_side || false;
  const map_hex_character_count = 16;
  const mc_input_path  = (fs.tmpdir() + "/redstonepen.i.mmap").replace(/\\/g, "/");
  const mc_output_path = (fs.tmpdir() + "/redstonepen.o.mmap").replace(/\\/g, "/");

  var mc_input_map, mc_output_map;
  const reset_mappings = function() {
    fs.writefile(mc_input_path, "0".repeat(map_hex_character_count) + "\n");
    mc_input_map = new sys.mmap(mc_input_path, "rws", map_hex_character_count); // Mapped as input (from the system) in the mod.
    fs.writefile(mc_output_path, "0".repeat(map_hex_character_count) + "\n");
    mc_output_map = new sys.mmap(mc_output_path, "rws", map_hex_character_count); // Mapped as output (to the system) in the mod.
  };
  // Map get/set
  const io_for = function(map, val) {
    if(val === undefined) {
      const out = [];
      for(var i=0; i<map_hex_character_count; ++i) {
        const n = Number.parseInt(String.fromCharCode(map.get(i)), 16);
        out.push(Number.isNaN(n) ? 0 : n);
      }
      return out.reverse();
    } else if(typeof(val)==='string') {
      const out = val.toLowerCase().replace(/[\s]/g,"").replace(/[^0-9a-f]/g,"0").split("");
      for(var i=0; (i<map_hex_character_count) && (i<out.length); ++i) {
        map.set(i, out[i].charCodeAt(0));
      }
    } else if(Array.isArray(val)) {
      const out = val.map(function(n){ return Math.min(15, Math.max(0, n)); }).reverse();
      for(var i=0; (i<map_hex_character_count) && (i<out.length); ++i) {
        map.set(i, sprintf("%1x", out[i]).charCodeAt(0));
      }
    }
  }
  reset_mappings();

  // Methods
  this.num_channels = function() {
    return map_hex_character_count;
  }
  this.is_reverse = function() {
    return reverse;
  }
  this.reset = function() {
    reset_mappings();
  }
  this.sync = function() {
    mc_input_map.sync();
    mc_output_map.sync();
  }
  this.input_path  = function() {
    return mc_input_path;
  };
  this.output_path = function() {
    return mc_output_path;
  };
  this.input_data = function(hex) {
    return io_for(mc_input_map, hex);
  };
  this.output_data = function(hex) {
    return io_for(mc_output_map, hex);
  };
  this.data = function(hex) {
    if(!reverse) {
      return (hex===undefined) ? (this.output_data()) : (this.input_data(hex));
    } else {
      return (hex===undefined) ? (this.input_data()) : (this.output_data(hex));
    }
  }
}
