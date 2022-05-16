// @file systray-menu.djs
include("../redstone-client-adapter.djs");

if(app.already_running) exit(0);
app.icon("icons/gunpowder.ico");  // Initial icon
app.tooltip("RCA - Serial Port");
app.timer(10);                    // 10ms `app.events.on_timer()` period.

const tty = new sys.serialport();
const rca = new RedstoneClientAdapter(false);

const state = {
  settings: {
    port: "",
    baud: 115200,
    port_settings: "n81",
    log_file: "rca.log",
    log_file_max_size: (8*1024*1024),
    log_verbosity: 1
  },
  tracing: {
    error_counter: 0,
    num_lines_received: 0,
    last_rx: "",
    last_tx: "",
  },
  status: "Port closed.",
  open_port: "",
  connected: false,
  rx_buffer: ""
}

function log(text)
{
  if(state.settings.log_verbosity <= 0) {
    return;
  } else if(!state.settings.log_file) {
    print(text);
  } else {
    if(fs.size(state.settings.log_file) > state.settings.log_file_max_size) {
      fs.write(state.settings.log_file, text+"\n");
    } else {
      fs.append(state.settings.log_file, text+"\n");
    }
  }
}

function initialize()
{
  const settings_file = app.directory + "/settings.json";
  var saved_settings = fs.read(settings_file);
  if(saved_settings) {
    try {
      saved_settings = JSON.parse(saved_settings);
      if(typeof(saved_settings)!=='object') {
        throw new Error("Settings file does contain a JSON object.");
      } else {
        state.settings = saved_settings;
      }
    } catch(ex) {
      log("Invalid JSON in settings file '" + settings_file + "'");
    }
  }
  for(var i=0; i<sys.args.length-1; ++i) {
    if(sys.args[i] == '-l') {
      state.settings.log_file = sys.args[i+1];
      log("Log file '" + state.settings.log_file + "'");
    }
  }
  log("Initialized.");
}

function get_context_menu()
{
  const portlist_menu = (function(){
    const ports = sys.serialport.portlist();
    const menu = [];
    const set_port = function() { state.settings.port = this.port; }
    menu.push({ name: "Disconnected", checked: (!state.settings.port), port: "", onclick: function() {
      state.settings.port = "";
      state.tracing.num_lines_received = 0;
      tty.close();
    }});
    menu.push({ separator: true });
    for(var key in ports) {
      menu.push({
        name: key + " (" + ports[key] + ")",
        checked: (state.settings.port.toLowerCase()===key.toLowerCase() || state.settings.port.toLowerCase()===ports[key].toLowerCase()),
        port: key,
        onclick: set_port
      });
    }
    return menu;
  })();

  const baudrate_menu = (function(){
    const menu = [];
    const set_baud = function() { state.settings.baud = this.baud; };
    const baudrates = [115200, 57600, 9600, "-", 576000, 921600, 460800, 230400, 38400, 19200];
    baudrates.filter(function(br) {
      if(br == "-") {
        menu.push({ separator: true});
      } else {
        menu.push({
          name: "" + br,
          baud: br,
          onclick: set_baud,
          checked: (br === state.settings.baud)
        });
      }
    });
    return menu;
  })();

  const logging_menu = (function(){
    const set_verbosity = function() { state.settings.log_verbosity = this.verbosity; }
    return [
      { name: "0: Off", verbosity:0, checked: state.settings.log_verbosity<=0, onclick: set_verbosity},
      { name: "1: Low", verbosity:1, checked: state.settings.log_verbosity==1, onclick: set_verbosity},
      { name: "2: Middle", verbosity:2, checked: state.settings.log_verbosity==2, onclick: set_verbosity},
      { name: "3: High", verbosity:3, checked: state.settings.log_verbosity>=3, onclick: set_verbosity},
    ];
  })();

  return [
    {
      name: "Status: " + ((!state.status) ? "closed" : state.status),
      icon:"icons/book.ico",
      disabled: true
    },
    {separator:true},
    {
      name: "Settings",
      menu: [
        {
          name: "Serial port",
          menu: portlist_menu
        },
        {
          name: "Baud rate",
          menu: baudrate_menu
        },
        {
          name: "Logging",
          menu: logging_menu
        },

      ]
    },
    {
      name: "Exit",
      icon: "icons/door.ico",
      onclick: function() { exit(0); }
    },
  ];
}

function transceive()
{
  // Changed settings
  if((state.open_port != tty.port) || (state.settings.baud != tty.baudrate)) {
    tty.close();
  }

  // Open, purge
  if(tty.closed) {
    state.tracing.last_rx = "";
    state.tracing.last_tx = "";
    state.status = "Port closed";
    if(state.settings.port && state.settings.baud) {
      try {
        const settings = state.settings.baud + state.settings.port_settings;
        state.open_port = tty.open(state.settings.port, settings) ? tty.port : "";
        tty.dtr(true);
        tty.rts(true);
        tty.purge();
        tty.read();
      } catch(ignored) {
        // we check errors below
      }
      if(tty.isopen) {
        state.status = "Port open";
      } else if(tty.error) {
        state.status = "Error: " + tty.errormessage;
      }
    }
  }

  // Icon update
  if(state.connected != tty.isopen) {
    state.connected = tty.isopen;
    app.icon(state.connected ? "icons/redstone.ico" : "icons/gunpowder.ico");
    log("Port open: " + state.connected);
  }
  if(!state.connected) return;

  // Receive from serial
  const inp_channels_hex = (function(){
    var rx_chars = "";
    try { rx_chars = tty.read(1); } catch(ex) { ++state.tracing.error_counter; tty.close(); }
    if(!rx_chars) return ""; // nothing received
    state.rx_buffer += rx_chars;
    if(state.rx_buffer.length > 1024) state.rx_buffer = ""; // 1k line length is more than enough
    if(rx_chars.search(/[\r\n]/)<0) return ""; // no full line received
    const lines = state.rx_buffer.split(/[\n\r]+/);
    state.rx_buffer = lines.pop(); // last unfinished line chunk, often empty.
    const data = lines.filter(function(line){
      if((state.settings.log_verbosity > 1) && (state.tracing.last_rx != line)) {
        state.tracing.last_rx = line;
        log("rx:         '" + line + "'");
      }
      line = line.replace(/\s+/,"");
      const isdata = (line.search(/^[0-9a-fA-F]{16}$/) === 0);
      if(isdata) {
        ++state.tracing.num_lines_received;
      }
      return isdata;
    });
    return (data.length>0) ? (lines.pop()) : ""; // Only the last data line is relevant.
  })();

  // Assign RCA input memory map, send RCA output map back to micro controller.
  if(inp_channels_hex != "") {
    rca.data(inp_channels_hex);
    state.status = "Transmissions: " + state.tracing.num_lines_received
    const out_channels_hex = rca.data().map(function(cv){return sprintf("%1x", cv)}).reverse().join("");
    tty.write(out_channels_hex + "\n");
    if((state.settings.log_verbosity > 1) && (state.tracing.last_tx != out_channels_hex)) {
      state.tracing.last_tx = out_channels_hex;
      log("tx: '" + out_channels_hex + "'");
    }
  }
}

app.events.on_mouse = function(event, key_states) {
  try {
    switch(event) {
      case "mup": { exit(0); break; }
      case "rup":
      case "lup": {
        const clicked = app.menu(get_context_menu());
        if(clicked && (clicked.port || clicked.baud || clicked.verbosity)) {
          fs.write(app.directory + "/settings.json", JSON.stringify(state.settings, null, 1));
        }
        break;
      }
    }
  } catch(ex) {
    log(JSON.stringify({exception:ex.stack}));
  }
};

app.events.on_timer = function() {
  try {
    transceive();
    if(state.tracing.error_counter > 0) state.tracing.error_counter -= 1;
  } catch(ex) {
    state.tracing.error_counter += 2;
    tty.close();
    log(JSON.stringify({exception: ex.stack}));
    if(state.tracing.error_counter > 100) exit(1);
  }
};

initialize();
