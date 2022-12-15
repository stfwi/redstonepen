/*
 * @file ControlBox.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.network.NetworkHooks;
import wile.redstonepen.ModContent;
import wile.redstonepen.libmc.blocks.StandardBlocks;
import wile.redstonepen.libmc.detail.Auxiliaries;
import wile.redstonepen.libmc.detail.Networking;
import wile.redstonepen.libmc.detail.SidedProxy;
import wile.redstonepen.libmc.ui.GuiTextEditing;
import wile.redstonepen.libmc.ui.Guis;
import wile.redstonepen.libmc.ui.TooltipDisplay;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;


public class ControlBox
{
  //--------------------------------------------------------------------------------------------------------------------
  // Definitions
  //--------------------------------------------------------------------------------------------------------------------

  public static class Defs
  {
    public static final List<String> PORT_NAMES = Arrays.asList("d", "u", "r", "y", "g", "b");
  }

  //--------------------------------------------------------------------------------------------------------------------
  // ControlBoxBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class ControlBoxBlock extends CircuitComponents.DirectedComponentBlock implements StandardBlocks.IStandardBlock
  {
    public ControlBoxBlock(long config, AbstractBlock.Properties builder, AxisAlignedBB[] aabb)
    { super(config, builder, aabb); }

    @Override
    public List<ItemStack> dropList(BlockState state, World world, @Nullable TileEntity te, boolean explosion)
    {
      final ItemStack stack = new ItemStack(this.asItem());
      if(te instanceof ControlBoxBlockEntity) {
        final CompoundNBT tedata = new CompoundNBT();
        ((ControlBoxBlockEntity)te).save(tedata);
        if(tedata.contains("logic") && !tedata.getCompound("logic").getString("code").trim().isEmpty()) {
          stack.getOrCreateTag().put("tedata", tedata);
        }
      }
      return Collections.singletonList(stack);
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    { return true; }

    @Override
    @Nullable
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    { return new ControlBoxBlockEntity(); }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable IBlockReader world, List<ITextComponent> tooltip, ITooltipFlag flag)
    {
      if(Auxiliaries.Tooltip.addInformation(stack, world, tooltip, flag, true)) return;
      if((stack.getTag()==null) || (!stack.getTag().contains("tedata")) || (!stack.getTag().getCompound("tedata").contains("logic"))) return;
      Arrays.stream(stack.getTag().getCompound("tedata").getCompound("logic").getString("code").split("\\n"))
        .map(s->s.replaceAll("#.*$", "").trim())
        .filter(s->!s.isEmpty())
        .map(s->(new StringTextComponent(s)).withStyle(TextFormatting.DARK_GREEN))
        .forEach(tooltip::add);
    }

    @Override
    public void setPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
      if((world.isClientSide) || (!stack.hasTag())) return;
      final TileEntity te = world.getBlockEntity(pos);
      if(!(te instanceof ControlBoxBlockEntity)) return;
      ControlBoxBlockEntity cbe = (ControlBoxBlockEntity)te;
      final CompoundNBT nbt = stack.getTag();
      if(nbt.contains("tedata")) {
        CompoundNBT te_nbt = nbt.getCompound("tedata");
        if(!te_nbt.isEmpty()) cbe.readnbt(te_nbt);
      }
      cbe.setCustomName(Auxiliaries.getItemLabel(stack));
      te.setChanged();
    }

    @Override
    public int getSignal(BlockState state, IBlockReader world, BlockPos pos, Direction redstone_side)
    {
      final TileEntity te = world.getBlockEntity(pos);
      if(!(te instanceof ControlBoxBlockEntity)) return 0;
      Direction internal_side = getReverseStateMappedFacing(state, redstone_side.getOpposite());
      return (((ControlBoxBlockEntity)te).logic_.output_data >> (4*internal_side.ordinal())) & 0xf;
    }

    @Override
    public int getDirectSignal(BlockState state, IBlockReader world, BlockPos pos, Direction redstone_side)
    { return getSignal(state, world, pos, redstone_side); }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rayTraceResult)
    {
      final TileEntity te = world.getBlockEntity(pos);
      if(!(te instanceof ControlBoxBlockEntity)) return ActionResultType.FAIL;
      if(world.isClientSide) return ActionResultType.SUCCESS;
      if(player.getItemInHand(hand).getItem()==Items.DEBUG_STICK) {
        ((ControlBoxBlockEntity)te).toggle_trace(player);
      } else {
        NetworkHooks.openGui((ServerPlayerEntity)player,(INamedContainerProvider)te);
      }
      return ActionResultType.CONSUME;
    }

    @Override
    public BlockState update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      if(world.isClientSide) return state;
      final TileEntity te = world.getBlockEntity(pos);
      if(!(te instanceof ControlBoxBlockEntity)) return state;
      final ControlBoxBlockEntity cb = (ControlBoxBlockEntity)te;
      if(fromPos==null) { cb.tick_timer_=0; return state; }
      BlockPos diff = fromPos.subtract(pos);
      Direction world_side = Direction.fromNormal(diff.getX(), diff.getY(), diff.getZ());
      cb.signal_update(world_side, getReverseStateMappedFacing(state, world_side));
      return state;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity
  //--------------------------------------------------------------------------------------------------------------------

  public static class ControlBoxBlockEntity extends TileEntity implements ITickableTileEntity, INamedContainerProvider, INameable, Networking.IPacketTileNotifyReceiver
  {
    public static final int TICK_INTERVAL = 4;
    private final IInventory block_inventory_ = new Inventory(1);
    private final IntArray container_fields_ = new IntArray(ControlBoxUiContainer.NUM_OF_FIELDS);
    private final ControlBoxLogic.Logic logic_ = new ControlBoxLogic.Logic();
    private ITextComponent custom_name_ = null;
    private boolean trace_ = false;
    private int tick_timer_ = 0;
    private int num_signal_updates_received_ = 0;
    private int tick_interval_ = TICK_INTERVAL;

    public ControlBoxBlockEntity()
    { super(ModContent.TET_CONTROLBOX); }

    public ControlBoxBlockEntity(TileEntityType<?> te_type)
    { super(te_type); }

    public void readnbt(CompoundNBT nbt)
    {
      if(nbt.contains("name", Constants.NBT.TAG_STRING)) custom_name_ = Auxiliaries.unserializeTextComponent(nbt.getString("name"));
      final CompoundNBT logic_data = nbt.contains("logic", Constants.NBT.TAG_COMPOUND) ? nbt.getCompound("logic") : new CompoundNBT();
      logic_.code(logic_data.getString("code"));
      logic_.input_data = logic_data.getInt("input");
      logic_.output_data = logic_data.getInt("output");
      final CompoundNBT logic_symbols = logic_data.contains("symbols", Constants.NBT.TAG_COMPOUND) ? logic_data.getCompound("symbols") : new CompoundNBT();
      logic_.symbols_.clear();
      logic_symbols.getAllKeys().forEach(k->logic_.symbols_.put(k, logic_symbols.getInt(k)));
    }

    private void writenbt(CompoundNBT nbt)
    {
      if(custom_name_ != null) nbt.putString("name", Auxiliaries.serializeTextComponent(custom_name_));
      final CompoundNBT logic_data = new CompoundNBT();
      logic_data.putString("code", logic_.code());
      logic_data.putInt("input", logic_.input_data);
      logic_data.putInt("output", logic_.output_data);
      final CompoundNBT logic_symbols = new CompoundNBT();
      for(Map.Entry<String,Integer> e:logic_.symbols_.entrySet()) logic_symbols.putInt(e.getKey(), e.getValue());
      logic_data.put("symbols", logic_symbols);
      nbt.put("logic", logic_data);
    }

    // TileEntity/MenuProvider -------------------------------------------------------

    @Override
    public void load(BlockState state, CompoundNBT nbt)
    { super.load(state, nbt); readnbt(nbt); }

    @Override
    public CompoundNBT save(CompoundNBT nbt)
    { super.save(nbt); writenbt(nbt); return nbt; }

    @Override
    public void setRemoved()
    { super.setRemoved(); }

    @Override
    public ITextComponent getName()
    {
      if(custom_name_ != null) return custom_name_;
      final Block block = getBlockState().getBlock();
      if(block!=null) return new  TranslationTextComponent(block.getDescriptionId());
      return new StringTextComponent("Redstone Logic Control");
    }

    @Override
    @Nullable
    public ITextComponent getCustomName()
    { return custom_name_; }

    @Override
    public boolean hasCustomName()
    { return (custom_name_ != null); }

    public void setCustomName(ITextComponent name)
    { custom_name_ = name; }

    @Override
    public ITextComponent getDisplayName()
    { return INameable.super.getDisplayName(); }

    @Override
    public Container createMenu(int id, PlayerInventory inventory, PlayerEntity player)
    { return new ControlBoxUiContainer(id, inventory, block_inventory_, IWorldPosCallable.create(level, worldPosition), container_fields_); }

    @Override
    public void tick()
    {
      if(getLevel().isClientSide) return;
      if(--tick_timer_ > 0) return;
      tick_timer_ = tick_interval_;
      final long tick = System.nanoTime();
      final World world = getLevel();
      final BlockState device_state = getBlockState();
      final BlockPos device_pos = getBlockPos();
      final boolean device_enabled = (device_state.getValue(ControlBoxBlock.STATE) > 0);
      if(!(device_state.getBlock() instanceof ControlBoxBlock)) return;
      final Set<String> esyms = logic_.expressions().symbols;
      final int last_output_data = logic_.output_data;
      final int last_input_data = logic_.input_data;
      try {
        // Input fetching
        {
          logic_.input_data = 0;
          for(Direction d:Direction.values()) {
            final int mask = 0xf<<(4*d.ordinal());
            if((logic_.output_mask & mask) != 0) continue;
            final Direction world_dir = ControlBoxBlock.getForwardStateMappedFacing(device_state, d);
            final BlockPos target_pos = device_pos.relative(world_dir);
            final int p = world.getSignal(device_pos.relative(world_dir), world_dir);
            logic_.input_data |= (p & 0xf)<<(4*d.ordinal());
            if(device_enabled) {
              // Comparator overrides only if really needed - may be inventories that do expensive lookups.
              final String port_name = Defs.PORT_NAMES.get(d.ordinal());
              if(esyms.contains(port_name+".co")) {
                final BlockState target_state = world.getBlockState(target_pos);
                if(target_state.hasAnalogOutputSignal()) {
                  @SuppressWarnings("deprecation")
                  final int cov = target_state.getBlock().getAnalogOutputSignal(target_state, world, target_pos);
                  logic_.symbol(port_name+".co", cov);
                } else {
                  logic_.symbol(port_name+".co", 0);
                }
              }
            }
          }
        }
        // Logic processing
        {
          if(trace_) logic_.symbol(".perf1", (int)(MathHelper.clamp(System.nanoTime()-tick, 0, 0x7fffffff))/1000);
          if(!device_enabled) {
            logic_.output_data = 0;
          } else {
            logic_.symbol(".clock", (int)(world.getGameTime() & 0x7fffffffL)); // wraps over in >3years
            logic_.symbol(".time", (int)(world.getDayTime() % 24000));
            logic_.tick();
          }
        }
        // Output setting
        {
          if(logic_.output_data != last_output_data) {
            final ControlBoxBlock device_block = (ControlBoxBlock)(device_state.getBlock());
            for(Direction d:Direction.values()) {
              if((logic_.output_mask & 0xf<<(4*d.ordinal())) == 0) continue;
              final Direction world_dir = ControlBoxBlock.getForwardStateMappedFacing(device_state, d);
              device_block.notifyOutputNeighbourOfStateChange(device_state, world, device_pos, world_dir);
            }
          }
          container_fields_.set(0, logic_.input_data|logic_.output_data);
          if((logic_.output_data != last_output_data) || (logic_.input_data != last_input_data)) world.blockEntityChanged(device_pos, this);
        }
      } catch(Throwable ex) {
        Auxiliaries.logError("RLC tick exception!" + ex);
        world.removeBlock(getBlockPos(), true);
        return;
      }
      // Elision of signal updates during this tick, including after output setting
      {
        if(logic_.symbols().containsKey("tickrate")) tick_interval_ = MathHelper.clamp(logic_.symbols().getOrDefault("tickrate", TICK_INTERVAL), 1, 20);
        tick_timer_ = tick_interval_;
        final int dl = logic_.symbol(".deadline");
        if((dl>0) && (dl<tick_timer_)) tick_timer_ = dl;
        logic_.intr_redges = 0;
        logic_.intr_fedges = 0;
        if(trace_) logic_.symbol(".perf2", (int)(MathHelper.clamp(System.nanoTime()-tick, 0, 0x7fffffff))/1000);
      }
    }

    @Override
    public void onServerPacketReceived(CompoundNBT nbt)
    { readnbt(nbt); }

    // -------------------------------------------------------------------------------------------

    public boolean getEnabled()
    { return (getBlockState().getValue(ControlBoxBlock.STATE)!=0); }

    public void setEnabled(boolean en)
    {
      if(en == getEnabled()) return;
      getLevel().setBlock(getBlockPos(), getBlockState().setValue(ControlBoxBlock.STATE, en?1:0), 1|16);
      if(!en) logic_.symbols_.clear();
    }

    public String getCode()
    { return logic_.code(); }

    public void setCode(String text)
    { logic_.code(text); }

    public void signal_update(Direction from_world_side, Direction from_mapped_side)
    {
      final int shift = 4*from_mapped_side.ordinal();
      final int mask = 0xf<<shift;
      if((logic_.input_mask & mask) == 0) return; // no input there
      int signal_intr = mask & (getLevel().getSignal(getBlockPos().relative(from_world_side), from_world_side)<<shift);
      int signal_data = mask & (logic_.input_data);
      if(signal_intr == signal_data) return; // no signal change
      ++num_signal_updates_received_;
      if((signal_intr!=0) && (signal_data==0)) {
        logic_.intr_redges |= mask;
        tick_timer_ = 0;
      } else if((signal_intr==0) && (signal_data!=0)) {
        logic_.intr_fedges |= mask;
        tick_timer_ = 0;
      }
      // Else no boolean "powered" signal changed. No need to update next tick.
    }

    public void toggle_trace(@Nullable PlayerEntity player)
    { trace_ = !trace_; if(player!=null) Auxiliaries.playerChatMessage(player, "Trace: " + trace_); }

    public boolean trace_enabled()
    { return trace_; }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // GuiContainer
  //--------------------------------------------------------------------------------------------------------------------

  public static class ControlBoxUiContainer extends Container implements Networking.INetworkSynchronisableContainer
  {
    protected static final int NUM_OF_SLOTS = 1;
    protected static final int NUM_OF_FIELDS = 1;
    protected final PlayerEntity player_;
    protected final IInventory inventory_;
    protected final IWorldPosCallable wpc_;
    private final IIntArray fields_;
    private volatile CompoundNBT received_server_data_ = new CompoundNBT();
    //------------------------------------------------------------------------------------------------------------------
    public int field(int index) { return fields_.get(index); }
    public PlayerEntity player() { return player_ ; }
    public IInventory inventory() { return inventory_ ; }
    public World world() { return player_.level; }
    public @Nullable ControlBoxBlockEntity te() {
      return wpc_.evaluate((w,p)->{TileEntity te=w.getBlockEntity(p); return (te instanceof ControlBoxBlockEntity) ? ((ControlBoxBlockEntity)te): (null); }).orElse(null); }
    //------------------------------------------------------------------------------------------------------------------

    public ControlBoxUiContainer(int cid, PlayerInventory player_inventory)
    { this(cid, player_inventory, new Inventory(ControlBoxUiContainer.NUM_OF_SLOTS), IWorldPosCallable.NULL, new IntArray(ControlBoxUiContainer.NUM_OF_FIELDS)); }

    private ControlBoxUiContainer(int cid, PlayerInventory player_inventory, IInventory block_inventory, IWorldPosCallable wpc, IIntArray fields)
    {
      super(ModContent.CT_CONTROLBOX, cid);
      player_ = player_inventory.player;
      inventory_ = block_inventory;
      wpc_ = wpc;
      wpc_.execute((w,p)->inventory_.startOpen(player_));
      fields_ = fields;
      addDataSlots(fields_);
      for(int x=0; x<9; ++x) addSlot(new Slot(player_inventory, x, 28+x*18, 183)); // player hotbar slots: 0..8
    }

    @Override
    public boolean stillValid(PlayerEntity player)
    { return inventory_.stillValid(player); }

    @Override
    public void removed(PlayerEntity player)
    { super.removed(player); inventory_.stopOpen(player); }

    // Container client/server synchronisation --------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(String message)
    { onGuiAction(message, new CompoundNBT()); }

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(String message, CompoundNBT nbt)
    {
      nbt.putString("action", message);
      Networking.PacketContainerSyncClientToServer.sendToServer(containerId, nbt);
    }

    public CompoundNBT composeServerData(ControlBoxBlockEntity te, boolean full)
    {
      final ControlBoxLogic.Logic logic = te().logic_;
      final CompoundNBT nbt = new CompoundNBT();
      nbt.putString("action", "serverdata");
      nbt.putBoolean("enabled", te.getEnabled());
      nbt.putInt("inputs", logic.input_mask);
      nbt.putInt("outputs", logic.output_mask);
      if(!logic.symbols().isEmpty()) {
        final CompoundNBT sym_nbt = new CompoundNBT();
        logic.symbols().forEach(sym_nbt::putInt);
        nbt.put("symbols", sym_nbt);
      }
      if(!logic.valid()) {
        final CompoundNBT err_nbt = new CompoundNBT();
        logic.errors().forEach((e,l)->err_nbt.putString(e.toString(), l));
        nbt.put("errors", err_nbt);
      } else {
        nbt.put("errors", new CompoundNBT());
      }
      if(!full) return nbt;
      nbt.putBoolean("debug", te.trace_enabled());
      nbt.putString("code", te.getCode());
      return nbt;
    }

    public CompoundNBT fetchReceivedServerData()
    {
      final CompoundNBT received = received_server_data_;
      received_server_data_ = new CompoundNBT();
      return received;
    }

    @Override
    public void onServerPacketReceived(int windowId, CompoundNBT nbt)
    {
      switch(nbt.getString("action")) {
        case "serverdata": received_server_data_ = nbt;
      }
    }

    @Override
    public void onClientPacketReceived(int windowId, PlayerEntity player, CompoundNBT nbt)
    {
      final ControlBoxBlockEntity te = te();
      if(te==null) return;
      int sync = 0;
      switch(nbt.getString("action")) {
        case "enabled": { te.setEnabled(!te.getEnabled()); sync = 1; break; }
        case "codeupdate": { te.setCode(nbt.getString("code")); break; }
        case "serverdata": { sync = 2; break; }
        case "servervalues": { sync = 1; break; }
        default: {
          final int slotId = nbt.contains("slot") ? nbt.getInt("slot") : -1;
          //switch(nbt.getString("action")) { default -> {} }
          if(sync > 0) {
            inventory_.setChanged();
            player.inventory.setChanged();
            broadcastChanges();
          }
        }
      }
      if(sync > 0) Networking.PacketContainerSyncServerToClient.sendToListeners(world(), this, composeServerData(te, sync>1));
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // GUI
  //--------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  public static class ControlBoxGui extends Guis.ContainerGui<ControlBoxUiContainer>
  {
    private final String tooltip_prefix = ModContent.CONTROLBOX_BLOCK.getDescriptionId() + "";
    private final GuiTextEditing.MultiLineTextBox textbox;
    private final Guis.CheckBox start_stop;
    private final Guis.ImageButton cb_copy_all;
    private final Guis.ImageButton cb_paste_all;
    private final Guis.Image cb_error_indicator;
    private final List<Guis.TextBox> port_stati;
    private final List<Guis.Image> port_stati_i_indicators;
    private final List<Guis.Image> port_stati_o_indicators;
    private final Map<String, Integer> symbols_ = new HashMap<>();
    private final List<Tuple<Integer, String>> errors_ = new ArrayList<>();
    private int last_port_data_ = 0;
    private int update_counter_ = 0;
    private boolean focus_editor_ = false;
    private boolean debug_enabled_ = false;
    private boolean code_requested_ = false;

    public ControlBoxGui(ControlBoxUiContainer container, PlayerInventory player_inventory, ITextComponent title)
    {
      super(container, player_inventory, title,"textures/gui/control_box_gui.png", 238, 206);
      titleLabelX = 17; titleLabelY = -10;
      start_stop = new Guis.CheckBox(getBackgroundImage(), 12, 12, Guis.Coord2d.of(15,213), Guis.Coord2d.of(28,213));
      cb_copy_all = new Guis.ImageButton(getBackgroundImage(), 12, 12, Guis.Coord2d.of(41,213));
      cb_paste_all = new Guis.ImageButton(getBackgroundImage(), 12, 12, Guis.Coord2d.of(54,213));
      cb_error_indicator = new Guis.Image(getBackgroundImage(), 5, 2, Guis.Coord2d.of(68,213));
      textbox = new GuiTextEditing.MultiLineTextBox(29, 12, 156, 170, new StringTextComponent("Code"));
      port_stati = new ArrayList<>();
      port_stati_i_indicators = new ArrayList<>();
      port_stati_o_indicators = new ArrayList<>();
    }

    @Override
    public void init()
    {
      super.init();
      {
        textbox.init(this, Guis.Coord2d.of(29, 12)).setFontColor(0xdddddd).onValueChanged((tb)->push_code(textbox.getValue()));//.onMouseMove((tb, xy)->{});
        addButton(textbox);
        start_stop.init(this, Guis.Coord2d.of(196, 14)).tooltip(Auxiliaries.localizable(tooltip_prefix+".tooltips.runstop"));
        start_stop.onclick((cb)->{getMenu().onGuiAction("enabled"); focus_editor_=true; });
        addButton(start_stop);
        cb_copy_all.init(this, Guis.Coord2d.of(212, 14)).tooltip(Auxiliaries.localizable(tooltip_prefix+".tooltips.copyall"));
        cb_copy_all.onclick((cb)->{SidedProxy.setClipboard(textbox.getValue()); focus_editor_=true; });
        cb_copy_all.visible = false;
        addButton(cb_copy_all);
        cb_paste_all.init(this, Guis.Coord2d.of(212, 14)).tooltip(Auxiliaries.localizable(tooltip_prefix+".tooltips.pasteall"));
        cb_paste_all.onclick((cb)->{textbox.setValue(SidedProxy.getClipboard().orElse("")); push_code(textbox.getValue()); focus_editor_=true; });
        cb_paste_all.visible = false;
        addButton(cb_paste_all);
        cb_error_indicator.init(this, Guis.Coord2d.of(230, 14));
        cb_error_indicator.visible = false;
        addButton(cb_error_indicator);
      }
      {
        int ygap=12, x0=getGuiLeft()+205, y0=getGuiTop()+56;
        int[] liney_map = { (5*ygap), (4*ygap), (0), (2*ygap), (3*ygap), (ygap) };
        port_stati.clear();
        port_stati_i_indicators.clear();
        port_stati_o_indicators.clear();
        port_stati.add(new Guis.TextBox(x0, y0+liney_map[0], 30,10, new StringTextComponent("down"), font));   // DOWN
        port_stati.add(new Guis.TextBox(x0, y0+liney_map[1], 30,10, new StringTextComponent("up"), font));     // UP
        port_stati.add(new Guis.TextBox(x0, y0+liney_map[2], 30,10, new StringTextComponent("red"), font));    // NORTH
        port_stati.add(new Guis.TextBox(x0, y0+liney_map[3], 30,10, new StringTextComponent("yellow"), font)); // SOUTH
        port_stati.add(new Guis.TextBox(x0, y0+liney_map[4], 30,10, new StringTextComponent("green"), font));  // WEST
        port_stati.add(new Guis.TextBox(x0, y0+liney_map[5], 30,10, new StringTextComponent("blue"), font));   // EAST
        for(int i=0; i<port_stati.size(); ++i) {
          {
            final Guis.TextBox tb = port_stati.get(i);
            tb.setEditable(false);
            tb.setBordered(false);
            tb.setTextColor(0xffdddddd);
            tb.setTextColorUneditable(0xffdddddd);
            tb.setValue(String.format("%1s=00", Defs.PORT_NAMES.get(i).toUpperCase()));
            addButton(tb);
          }
          {
            final Guis.Image img = new Guis.Image(getBackgroundImage(), 3, 6, Guis.Coord2d.of(78, 215));
            img.init(this, Guis.Coord2d.of(191, 56+liney_map[i]));
            port_stati_i_indicators.add(img);
            addButton(img);
          }
          {
            final Guis.Image img = new Guis.Image(getBackgroundImage(), 3, 6, Guis.Coord2d.of(84, 215));
            img.init(this, Guis.Coord2d.of(189, 56+liney_map[i]));
            port_stati_o_indicators.add(img);
            addButton(img);
          }
        }
      }
      {
        final List<TooltipDisplay.TipRange> tooltips = new ArrayList<>();
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+200,getGuiTop()+36, 36, 16, ()->{
          final ITextComponent c = new StringTextComponent("");
          symbols_.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach((kv)->{
            final String k = kv.getKey();
            if((!debug_enabled_) && (k.startsWith(".") || Defs.PORT_NAMES.contains(k) || k.endsWith(".re") || k.endsWith(".fe"))) return;
            c.getSiblings().add(new StringTextComponent(String.format("%s = %d\n", k.toUpperCase(), kv.getValue())));
          });
          return c;
        }));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+196,getGuiTop()+14, 16, 16, ()->
          (errors_.isEmpty()) ? (StringTextComponent.EMPTY) : (Auxiliaries.localizable(tooltip_prefix+".error."+errors_.get(0).getB()))
        ));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+12, 5, 8, Auxiliaries.localizable(tooltip_prefix+".help.1")));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+22, 5, 3, Auxiliaries.localizable(tooltip_prefix+".help.2")));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+27, 5, 5, Auxiliaries.localizable(tooltip_prefix+".help.3")));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+34, 5, 5, Auxiliaries.localizable(tooltip_prefix+".help.4")));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+41, 5, 6, Auxiliaries.localizable(tooltip_prefix+".help.5")));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+49, 5, 4, Auxiliaries.localizable(tooltip_prefix+".help.6")));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+55, 5, 5, Auxiliaries.localizable(tooltip_prefix+".help.7")));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+62, 5, 3, Auxiliaries.localizable(tooltip_prefix+".help.8")));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+67, 5, 7, Auxiliaries.localizable(tooltip_prefix+".help.9")));
        tooltips.add(new TooltipDisplay.TipRange(getGuiLeft()+18,getGuiTop()+76, 5, 3, Auxiliaries.localizable(tooltip_prefix+".help.10")));
        tooltip_.init(tooltips).delay(50);
      }
      setInitialFocus(textbox);
      getMenu().onGuiAction("serverdata");
    }

    @Override
    public void tick()
    {
      // IO update.
      {
        int io = getMenu().field(0);
        if(last_port_data_ != io) {
          last_port_data_ = io;
          for(int i=0; i<port_stati.size(); ++i) {
            port_stati.get(i).setValue(String.format("%1s=%02d", Defs.PORT_NAMES.get(i).toUpperCase(), io & 0xf));
            io >>= 4;
          }
        }
      }
      // Received server data.
      {
        final CompoundNBT nbt = getMenu().fetchReceivedServerData();
        if(!nbt.isEmpty()) {
          if(nbt.contains("code")) {
            textbox.setValue(nbt.getString("code"));
            focus_editor_ = true;
          }
          if(nbt.contains("enabled")) {
            start_stop.checked(nbt.getBoolean("enabled"));
            focus_editor_ = true;
          }
          if(nbt.contains("debug")) {
            debug_enabled_ = nbt.getBoolean("debug");
          }
          if(nbt.contains("inputs")) {
            int mask = nbt.getInt("inputs");
            for(int i=0; i<Defs.PORT_NAMES.size(); ++i) {
              port_stati_i_indicators.get(i).visible = ((mask & 0xf) != 0);
              mask >>=4;
            }
          }
          if(nbt.contains("outputs")) {
            int mask = nbt.getInt("outputs");
            for(int i=0; i<Defs.PORT_NAMES.size(); ++i) {
              port_stati_o_indicators.get(i).visible = ((mask & 0xf) != 0);
              mask >>=4;
            }
          }
          if(nbt.contains("symbols", Constants.NBT.TAG_COMPOUND)) {
            CompoundNBT sym_nbt = nbt.getCompound("symbols");
            symbols_.clear();
            sym_nbt.getAllKeys().forEach(k->symbols_.put(k, sym_nbt.getInt(k)));
          }
          if(nbt.contains("errors", Constants.NBT.TAG_COMPOUND)) {
            CompoundNBT err_nbt = nbt.getCompound("errors");
            errors_.clear();
            err_nbt.getAllKeys().forEach(k->{ try { errors_.add(new Tuple<>(Integer.parseInt(k), err_nbt.getString(k))); } catch(Throwable ignored) {} });
            if(errors_.isEmpty()) {
              cb_error_indicator.visible = false;
              cb_error_indicator.x = 0;
              cb_error_indicator.y = 0;
              cb_error_indicator.tooltip(StringTextComponent.EMPTY);
            } else {
              Guis.Coord2d exy = textbox.getPositionAtIndex(errors_.get(0).getA());
              cb_error_indicator.tooltip(Auxiliaries.localizable(tooltip_prefix+".error."+errors_.get(0).getB()));
              cb_error_indicator.visible = true;
              cb_error_indicator.x = exy.x;
              cb_error_indicator.y = exy.y+8;
            }
          }
        } else if(--update_counter_ <= 0) {
          update_counter_ = 4;
          if(!code_requested_) {
            code_requested_ = true;
            getMenu().onGuiAction("serverdata");
          } else {
            getMenu().onGuiAction("servervalues");
          }
        }
      }
      // UI update
      {
        start_stop.active = errors_.isEmpty();
        if(!start_stop.active) { start_stop.checked(false); } else { cb_error_indicator.visible = false; }
        textbox.active = !start_stop.checked();
        textbox.setFontColor(textbox.active ? 0xffeeeeee : 0xff999999);
        cb_paste_all.visible = textbox.active && textbox.getValue().trim().isEmpty();
        cb_copy_all.visible = !cb_paste_all.visible;
        if(focus_editor_) {
          focus_editor_ = false;
          if(!isDragging()) {
            children().forEach(wg->wg.changeFocus(false));
            textbox.changeFocus(true);
            setFocused(textbox);
          }
        }
      }
    }

    @Override
    public void render(MatrixStack mx, int mouseX, int mouseY, float partialTicks)
    { super.render(mx, mouseX, mouseY, partialTicks); }

    @Override
    protected void renderLabels(MatrixStack mx, int x, int y)
    {
      font.draw(mx, title, (float)titleLabelX+1, (float)titleLabelY+1, 0x303030);
      font.draw(mx, title, (float)titleLabelX, (float)titleLabelY, 0x707070);
    }

    @Override
    protected void slotClicked(Slot hoveredSlot, int hoveredIndex, int no, ClickType clickType)
    {}

    private void push_code(String text)
    {
      final CompoundNBT nbt = new CompoundNBT();
      nbt.putString("code", text);
      getMenu().onGuiAction("codeupdate", nbt);
    }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // Logic
  //--------------------------------------------------------------------------------------------------------------------

  private static class ControlBoxLogic
  {
    private static class Logic
    {
      public int input_mask  = 0x00000000; // 24bit, direction ordinal nibbles
      public int input_data  = 0x00000000;
      public int output_mask = 0x00000000;
      public int output_data = 0x00000000;
      public int intr_redges = 0x00000000; // Rising edges seen between logic ticks
      public int intr_fedges = 0x00000000; // Falling edges seen between logic ticks

      private static int counter_function(String sym, MathExpr.Expr[] x, Map<String, Integer> m)
      {
        final int nargs = x.length;
        if(nargs <= 0) return 0;
        int q = m.getOrDefault(sym,0);
        if(nargs >= 5 && x[4].calc(m)>0) {
          q = 0;
        } else if(nargs == 1) {
          if(x[0].calc(m) > 0) ++q;
        } else {
          int x0 = x[0].calc(m);
          int x1 = x[1].calc(m);
          if((x0>0) && (x1<=0)) { ++q; } else if((x0<=0) && (x1>0)) { --q; }
        }
        if(nargs >= 4) {
          q = MathHelper.clamp(q, x[2].calc(m), x[3].calc(m));
        } else if(nargs >= 3) {
          q = MathHelper.clamp(q, 0, x[2].calc(m));
        } else {
          q = MathHelper.clamp(q, 0, 0x7fffffff);
        }
        m.put(sym, q);
        return q;
      }

      private static int timer_on_function(String sym, MathExpr.Expr[] x, Map<String, Integer> m)
      {
        if(x.length != 2) { m.remove("." + sym + ".clk"); m.remove(sym + ".et"); m.remove(sym + ".pt"); return 0; } // Invalid.
        final int in = x[0].calc(m);
        final int pt = x[1].calc(m);
        if(in <= 0) {
          // Signal 0.
          m.remove("." + sym + ".clk");
          m.put(sym + ".et", 0);
          return MathExpr.Expr.bool_false();
        } else if(pt <= 0) {
          // No time defined or changed.
          return (in>0) ? MathExpr.Expr.bool_true() : MathExpr.Expr.bool_false();
        } else {
          final int now = m.getOrDefault(".clock", 0);
          int et = m.getOrDefault(sym + ".et", 0);
          if(et >= pt) {
            return MathExpr.Expr.bool_true();
          } else if(et <= 0) {
            m.put("." + sym + ".clk", now);
            m.put(sym + ".et", 1);
            m.put(sym + ".pt", pt);
            m.put(".deadline", Math.min(m.getOrDefault(".deadline", 20), pt));
            return MathExpr.Expr.bool_false();
          } else {
            et = Math.min(now-m.getOrDefault("." + sym + ".clk", now), pt);
            m.put(sym + ".et", et);
            if(et >= pt) {
              m.remove("." + sym + ".clk");
              return MathExpr.Expr.bool_true();
            } else {
              m.put(".deadline", Math.min(m.getOrDefault(".deadline", 20), pt-et));
              return MathExpr.Expr.bool_false();
            }
          }
        }
      }

      private static int timer_off_function(String sym, MathExpr.Expr[] x, Map<String, Integer> m)
      {
        if(x.length != 2) { m.remove("." + sym + ".clk"); m.remove(sym + ".et"); m.remove(sym + ".pt"); return 0; } // Invalid.
        final int in = x[0].calc(m);
        final int pt = x[1].calc(m);
        if(in > 0) {
          // Signal not false.
          m.remove("." + sym + ".clk");
          m.put(sym + ".et", 0);
          return MathExpr.Expr.bool_true();
        } else if(pt <= 0) {
          // No time defined or changed.
          return (in<=0) ? MathExpr.Expr.bool_true() : MathExpr.Expr.bool_false();
        } else {
          final int now = m.getOrDefault(".clock", 0);
          int et = m.getOrDefault(sym + ".et", 0);
          if(et >= pt) {
            return MathExpr.Expr.bool_false();
          } else if(et <= 0) {
            m.put("." + sym + ".clk", now);
            m.put(sym + ".et", 1);
            m.put(sym + ".pt", pt);
            m.put(".deadline", Math.min(m.getOrDefault(".deadline", 20), pt));
            return MathExpr.Expr.bool_true();
          } else {
            et = Math.min(now-m.getOrDefault("." + sym + ".clk", now), pt);
            m.put(sym + ".et", et);
            if(et >= pt) {
              m.remove("." + sym + ".clk");
              return MathExpr.Expr.bool_false();
            } else {
              m.put(".deadline", Math.min(m.getOrDefault(".deadline", 20), pt-et));
              return MathExpr.Expr.bool_true();
            }
          }
        }
      }

      private static int timer_pulse_function(String sym, MathExpr.Expr[] x, Map<String, Integer> m)
      {
        if(x.length != 2) { m.remove("." + sym + ".clk"); m.remove(sym + ".et"); m.remove(sym + ".pt"); return 0; } // Invalid.
        final int in = x[0].calc(m);
        final int pt = x[1].calc(m);
        if(pt <= 0) return (in>0) ? MathExpr.Expr.bool_true() : MathExpr.Expr.bool_false();
        int et = m.getOrDefault(sym + ".et", 0);
        if(et > 0) {
          if(et >= pt) {
            // Timer expired, waiting for falling input edge.
            if(in<=0) m.put(sym + ".et", 0);
            return MathExpr.Expr.bool_false();
          } else {
            // Timer running, output constant during that time.
            final int now = m.getOrDefault(".clock", 0);
            et = Math.min(now-m.getOrDefault("." + sym + ".clk", now), pt);
            m.put(sym + ".et", et);
            if(et >= pt) {
              m.remove("." + sym + ".clk");
              return MathExpr.Expr.bool_false();
            } else {
              m.put(".deadline", Math.min(m.getOrDefault(".deadline", 20), pt-et));
              return MathExpr.Expr.bool_true();
            }
          }
        } else if(in > 0) {
          // Input rising edge or initial signal.
          m.put("." + sym + ".clk", m.getOrDefault(".clock", 0));
          m.put(sym + ".et", 1);
          m.put(sym + ".pt", pt);
          m.put(".deadline", Math.min(m.getOrDefault(".deadline", 20), pt));
          return MathExpr.Expr.bool_true();
        } else {
          // Signal 0, not started.
          return MathExpr.Expr.bool_false();
        }
      }

      private static int limit_function(MathExpr.Expr[] x, Map<String, Integer> m)
      {
        switch(x.length) {
          case 0: return 0;
          case 1: return Math.min(15,Math.max(0,x[0].calc(m)));
          case 2: return Math.min(x[1].calc(m),Math.max(0,x[0].calc(m)));
          default: return Math.min(x[2].calc(m),Math.max(x[1].calc(m),x[0].calc(m)));
        }
      }

      private static int if_function(MathExpr.Expr[] x, Map<String, Integer> m)
      {
        switch(x.length) {
          case 0: return 0;
          case 1: return ((x[0].calc(m)>0)?15:0);
          case 2: return ((x[0].calc(m)>0) ? x[1].calc(m) : 0);
          default: return ((x[0].calc(m)>0) ? x[1].calc(m) : x[2].calc(m));
        }
      }

      private static List<MathExpr.ExprFuncDef> make_functions()
      {
        return Arrays.asList(
          new MathExpr.ExprFuncDef("inv", 1, (x,m)->Math.min(15, Math.max(0, 15-x[0].calc(m)))),
          new MathExpr.ExprFuncDef("max", -1, (x,m)->(Arrays.stream(x).mapToInt(e->e.calc(m)).max().orElse(0))),
          new MathExpr.ExprFuncDef("min", -1, (x,m)->(Arrays.stream(x).mapToInt(e->e.calc(m)).min().orElse(0))),
          new MathExpr.ExprFuncDef("lim", -1, (x,m)->limit_function(x,m)),
          new MathExpr.ExprFuncDef("if", -1, (x,m)->if_function(x,m)),
          new MathExpr.ExprFuncDef("mean", -1, (x,m)->((x.length==0)?(0):(Arrays.stream(x).mapToInt(e->e.calc(m)).sum()/x.length))),
          new MathExpr.ExprFuncDef("rnd",  0, (x,m)->((int)(Math.random()*16.0))),
          new MathExpr.ExprFuncDef("clock",  0, (x,m)->m.getOrDefault(".clock", 0)),
          new MathExpr.ExprFuncDef("time",  0, (x,m)->m.getOrDefault(".time", 0)),
          new MathExpr.ExprFuncDef("cnt1", -1, (x,m)->counter_function(".cnt1", x, m)),
          new MathExpr.ExprFuncDef("cnt2", -1, (x,m)->counter_function(".cnt2", x, m)),
          new MathExpr.ExprFuncDef("cnt3", -1, (x,m)->counter_function(".cnt3", x, m)),
          new MathExpr.ExprFuncDef("cnt4", -1, (x,m)->counter_function(".cnt4", x, m)),
          new MathExpr.ExprFuncDef("ton1", 2, (x,m)->timer_on_function("ton1", x, m)),
          new MathExpr.ExprFuncDef("ton2", 2, (x,m)->timer_on_function("ton2", x, m)),
          new MathExpr.ExprFuncDef("ton3", 2, (x,m)->timer_on_function("ton3", x, m)),
          new MathExpr.ExprFuncDef("ton4", 2, (x,m)->timer_on_function("ton4", x, m)),
          new MathExpr.ExprFuncDef("tof1", 2, (x,m)->timer_off_function("tof1", x, m)),
          new MathExpr.ExprFuncDef("tof2", 2, (x,m)->timer_off_function("tof2", x, m)),
          new MathExpr.ExprFuncDef("tof3", 2, (x,m)->timer_off_function("tof3", x, m)),
          new MathExpr.ExprFuncDef("tof4", 2, (x,m)->timer_off_function("tof4", x, m)),
          new MathExpr.ExprFuncDef("tp1", 2, (x,m)->timer_pulse_function("tp1", x, m)),
          new MathExpr.ExprFuncDef("tp2", 2, (x,m)->timer_pulse_function("tp2", x, m)),
          new MathExpr.ExprFuncDef("tp3", 2, (x,m)->timer_pulse_function("tp3", x, m)),
          new MathExpr.ExprFuncDef("tp4", 2, (x,m)->timer_pulse_function("tp4", x, m))
        );
      }

      private static final List<MathExpr.ExprFuncDef> functions_ = make_functions();
      private final Map<String,Integer> symbols_ = new HashMap<>();
      private MultiLineMathExpr expressions_ = MultiLineMathExpr.EMPTY;
      private String code_ = "";

      public boolean valid()
      { return expressions_.invalid_entries.isEmpty(); }

      public MultiLineMathExpr expressions()
      { return expressions_; }

      public Map<Integer,String> errors()
      { return expressions_.invalid_entries.stream().collect(Collectors.toMap(e->(e.offset+e.parsed.pe), e->(e.parsed.error))); }

      public void symbol(String key, int value)
      { symbols_.put(key.toLowerCase(), value); }

      public int symbol(String key)
      { return symbols_.getOrDefault(key.toLowerCase(), 0); }

      public Map<String,Integer> symbols()
      { return symbols_; }

      public String code()
      { return code_; }

      public boolean code(String new_code)
      {
        if(code_.equals(new_code) && !expressions_.isEmpty()) return expressions_.invalid_entries.isEmpty();
        code_ = new_code;
        expressions_ = MultiLineMathExpr.of(code_, "", functions_);
        input_mask = 0;
        output_mask = 0;
        symbols_.clear();
        final String[] suffixes = {"", ".re", ".fe", ".co", ".co.re", ".co.fe"};
        for(int i=0; i<Defs.PORT_NAMES.size(); ++i ) {
          final String port = Defs.PORT_NAMES.get(i);
          if(expressions_.symbols.contains(port+".co.re") || expressions_.symbols.contains(port+".co.fe")) {
            expressions_.symbols.add(port+".co");
          }
          if(expressions_.assignments.contains(port)) {
            output_mask |= 0xf<<(4*i);
          } else if(Arrays.stream(suffixes).map(s->port+s).anyMatch(expressions_.symbols::contains)) {
            input_mask |= 0xf<<(4*i);
          }
        }
        output_data &= output_mask;
        return expressions_.invalid_entries.isEmpty();
      }

      public void tick()
      {
        // Input symbols
        for(int i=0; i<Defs.PORT_NAMES.size(); ++i ) {
          if((input_mask & (0xf<<(4*i))) != 0) symbol(Defs.PORT_NAMES.get(i), (input_data>>(4*i)) & 0xf);
        }
        // Edge detection
        expressions_.symbols.forEach((esym)->{
          if(!esym.contains(".")) return;
          final boolean isrising = esym.endsWith(".re");
          if(isrising || esym.endsWith(".fe")) {
            final Map<String, Integer> syms = symbols();
            final String symref = esym.substring(0, esym.length()-3);
            if(!syms.containsKey(symref) && (!Defs.PORT_NAMES.contains(symref))) return;
            final String symlast = "."+esym+".d";
            final int q1 = (syms.getOrDefault(symref,0)>0) ? 15:0;
            boolean edge = false;
            if(syms.containsKey(symlast)) {
              final int q0 = (syms.getOrDefault(symlast,0)>0) ? 15:0;
              edge = (isrising) ? ((q0<=0) && (q1>0)) : ((q0>0) && (q1<=0));
            }
            symbol(esym, edge ? 15 : 0);
            symbol(symlast, q1);
          }
        });
        for(int i=0; i<Defs.PORT_NAMES.size(); ++i ) {
          int port_mask = 0xf<<(4*i);
          if((intr_redges & port_mask) != 0) symbol(Defs.PORT_NAMES.get(i)+".re", 15);
          if((intr_fedges & port_mask) != 0) symbol(Defs.PORT_NAMES.get(i)+".fe", 15);
        }
        intr_redges = 0;
        intr_fedges = 0;

        // Calculation
        symbol(".deadline", 40);
        final Map<String,Integer> assigned = expressions_.recalculate(symbols_, (entry, mem)->{
          switch(entry.parsed.assignment_symbol) {
            case"r": case"b": case"y": case"g": case"u": case"d": return (Math.max(0, Math.min(15, entry.last_result)));
            default: return entry.last_result;
          }
        });
        // Assign outputs and update mem.
        assigned.forEach(this::symbol);
        output_data = 0;
        for(int i=0; i<Defs.PORT_NAMES.size(); ++i) output_data |= (symbol(Defs.PORT_NAMES.get(i))& 0xf)<<(4*i);
        output_data &= output_mask;
      }
    }

    public static class MultiLineMathExpr
    {
      public static final MultiLineMathExpr EMPTY = new MultiLineMathExpr();

      public static class Entry
      {
        public final int line_index, offset;
        public final MathExpr.ParsedLine parsed;
        public int last_result = 0;

        public Entry(int line_index, int offset, MathExpr.ParsedLine parsed)
        { this.line_index=line_index; this.offset=offset; this.parsed=parsed; }
      }

      public static MultiLineMathExpr of(String code)
      { return of(code, "", Collections.emptyList()); }

      public static MultiLineMathExpr of(String code, String assignment_variable)
      { return of(code, assignment_variable, Collections.emptyList()); }

      public static MultiLineMathExpr of(String code, String assignment_variable, Collection<MathExpr.ExprFuncDef> functions)
      {
        if(code.trim().isEmpty()) return EMPTY;
        final List<Entry> entries = new ArrayList<>();
        final String[] lines = code.replace("[\\r\\n\\s]+$", "").split("[\\r]?[\\n]");
        final List<Entry> parse_errors = new ArrayList<>();
        final Set<String> symbols = new HashSet<>();
        final Set<String> assignments = new HashSet<>();
        int line_index=0, offset=0;
        for(String line:lines) {
          if(!line.trim().isEmpty()) {
            final Entry entry = new Entry(line_index, offset, MathExpr.ParsedLine.of(line, assignment_variable, functions));
            if(!entry.parsed.error.isEmpty()) {
              parse_errors.add(entry);
            } else {
              symbols.addAll(entry.parsed.symbols);
              if(entry.parsed.expression.type == MathExpr.ExprType.ASSIGN) {
                entries.add(entry);
                assignments.add(entry.parsed.assignment_symbol);
              }
            }
          }
          offset += line.length();
        }
        return (entries.isEmpty() && parse_errors.isEmpty()) ? EMPTY : (new MultiLineMathExpr(entries, parse_errors, symbols, assignments));
      }

      public MultiLineMathExpr()
      { this(Collections.emptyList(), Collections.emptyList(), Collections.emptySet(), Collections.emptySet()); }

      public MultiLineMathExpr(List<Entry> lines, List<Entry> parse_error_entries, Set<String> symbols, Set<String> assignments)
      { this.entries=lines; this.invalid_entries=parse_error_entries; this.symbols=symbols; this.assignments=assignments; }

      public boolean isEmpty()
      { return entries.isEmpty(); }

      public Map<String,Integer> recalculate(Map<String,Integer> mem, BiFunction<Entry, Map<String,Integer>, Integer> assignment_post_processor)
      {
        final Map<String,Integer> assigned = new HashMap<>();
        for(Entry entry:entries) {
          entry.last_result = entry.parsed.expression.calc(mem);
          if(!entry.parsed.assignment_symbol.isEmpty()) {
            assigned.put(entry.parsed.assignment_symbol, assignment_post_processor.apply(entry, mem));
          }
        }
        return assigned;
      }

      public final List<Entry> entries;
      public final List<Entry> invalid_entries;
      public final Set<String> symbols;
      public final Set<String> assignments;
    }

    public static class MathExpr
    {
      private enum ExprType { VOID, CONST, VARREF, FUNC, NEG, NOT, MPY, DIV, MOD, ADD, SUB, AND, OR, XOR, NEQ, EQ, LE, GE, LT, GT, ASSIGN }

      public static class Expr
      {
        public static final Expr EMPTY = new Expr(ExprType.VOID, "<EMPTY>");
        protected Expr(ExprType type, String name) { this.type=type; this.name=name; }
        public int calc(Map<String, Integer> mem) { return 0; }
        public String toString() { return "{VOID}"; }
        public final ExprType type;
        public final String name;
        //-------------------------------
        public static int bool_true()  { return 15; }
        public static int bool_false() { return 0; }
        public static int assignment_sanitize(int x) { return x; }
      }

      public static abstract class ExprOp extends Expr
      {
        public ExprOp(ExprType type, List<Expr> args) { super(type, type.toString()); arguments=args; }
        public abstract int calc(Map<String, Integer> mem);
        public String toString() { return name+"{" + arguments.stream().map(Object::toString).collect(Collectors.joining(",")) + "}"; }
        public final List<Expr> arguments;
      }

      public static class ExprConst extends Expr
      {
        public ExprConst(int val) { super(ExprType.CONST, "<CONST>"); value=val; }
        public int calc(Map<String, Integer> mem) { return value; }
        public String toString() { return "CONST{"+value+"}"; }
        private final int value;
      }

      public static class ExprVarRef extends Expr
      {
        public ExprVarRef(String ref) { super(ExprType.VARREF, ref); }
        public int calc(Map<String, Integer> mem) { return mem.getOrDefault(name, 0); }
        public String toString() { return "SYM{'"+name+"'}"; }
      }

      public static class ExprFunc extends Expr
      {
        public ExprFunc(String name, int nargs, BiFunction<Expr[], Map<String, Integer>, Integer> fn, List<Expr> args)
        {
          super(ExprType.FUNC, name);
          arguments = args;
          this.func = fn;
          this.num_arguments = nargs;
          if((nargs >= 0) && (nargs != args.size())) throw new RuntimeException("invalid_number_of_arguments");
        }
        public int calc(Map<String, Integer> mem) { return func.apply(arguments.toArray(new Expr[0]), mem); }
        public String toString() { return "FN{'"+name+"'(" + arguments.stream().map(Object::toString).collect(Collectors.joining(",")) + ")}"; }
        public int nargs() { return num_arguments; }
        protected final List<Expr> arguments;
        protected final int num_arguments;
        private final BiFunction<Expr[], Map<String, Integer>, Integer> func;
      }

      public static class ExprNeg extends ExprOp
      {
        public ExprNeg(List<Expr> args) { super(ExprType.NEG, args); }
        public int calc(Map<String, Integer> mem) { return -arguments.get(0).calc(mem); }
      }

      public static class ExprNot extends ExprOp
      {
        public ExprNot(List<Expr> args) { super(ExprType.NOT, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem)==0) ? bool_true() : bool_false(); }
      }

      public static class ExprMpy extends ExprOp
      {
        public ExprMpy(List<Expr> args) { super(ExprType.MPY, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem) * arguments.get(1).calc(mem)); }
      }

      public static class ExprDiv extends ExprOp
      {
        public ExprDiv(List<Expr> args) { super(ExprType.DIV, args); }
        public int calc(Map<String, Integer> mem) { int b=arguments.get(1).calc(mem); return (b<=0) ? (0) : (arguments.get(0).calc(mem)/b); }
      }

      public static class ExprMod extends ExprOp
      {
        public ExprMod(List<Expr> args) { super(ExprType.MOD, args); }
        public int calc(Map<String, Integer> mem) { int b=arguments.get(1).calc(mem); return (b<=0) ? (0) : (arguments.get(0).calc(mem)%b); }
      }

      public static class ExprAdd extends ExprOp
      {
        public ExprAdd(List<Expr> args) { super(ExprType.ADD, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem) + arguments.get(1).calc(mem)); }
      }

      public static class ExprSub extends ExprOp
      {
        public ExprSub(List<Expr> args) { super(ExprType.SUB, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem) - arguments.get(1).calc(mem)); }
      }

      public static class ExprAnd extends ExprOp
      {
        public ExprAnd(List<Expr> args) { super(ExprType.AND, args); }
        public int calc(Map<String, Integer> mem) { return ((arguments.get(0).calc(mem)>0) && (arguments.get(1).calc(mem)>0)) ? bool_true() : bool_false(); }
      }

      public static class ExprOr extends ExprOp
      {
        public ExprOr(List<Expr> args) { super(ExprType.OR, args); }
        public int calc(Map<String, Integer> mem) { return ((arguments.get(0).calc(mem)>0) || (arguments.get(1).calc(mem)>0)) ? bool_true() : bool_false(); }
      }

      public static class ExprXor extends ExprOp
      {
        public ExprXor(List<Expr> args) { super(ExprType.XOR, args); }
        public int calc(Map<String, Integer> mem) { return ((arguments.get(0).calc(mem)>0) ^ (arguments.get(1).calc(mem)>0)) ? bool_true() : bool_false(); }
      }

      public static class ExprNeq extends ExprOp
      {
        public ExprNeq(List<Expr> args) { super(ExprType.NEQ, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem) != arguments.get(1).calc(mem)) ? bool_true() : bool_false(); }
      }

      public static class ExprEq extends ExprOp
      {
        public ExprEq(List<Expr> args) { super(ExprType.EQ, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem) == arguments.get(1).calc(mem)) ? bool_true() : bool_false(); }
      }

      public static class ExprGe extends ExprOp
      {
        public ExprGe(List<Expr> args) { super(ExprType.GE, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem) >= arguments.get(1).calc(mem)) ? bool_true() : bool_false(); }
      }

      public static class ExprLe extends ExprOp
      {
        public ExprLe(List<Expr> args) { super(ExprType.LE, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem) <= arguments.get(1).calc(mem)) ? bool_true() : bool_false(); }
      }

      public static class ExprGt extends ExprOp
      {
        public ExprGt(List<Expr> args) { super(ExprType.GT, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem) > arguments.get(1).calc(mem)) ? bool_true() : bool_false(); }
      }

      public static class ExprLt extends ExprOp
      {
        public ExprLt(List<Expr> args) { super(ExprType.LT, args); }
        public int calc(Map<String, Integer> mem) { return (arguments.get(0).calc(mem) < arguments.get(1).calc(mem)) ? bool_true() : bool_false(); }
      }

      public static class ExprAssign extends Expr
      {
        public final Expr value;
        public ExprAssign(String ref, Expr value) { super(ExprType.ASSIGN, ref); this.value=value; }
        public int calc(Map<String, Integer> mem) {
          final int res = value.calc(mem);
          if(!name.isEmpty()) { mem.put(name, res); }
          return res;
        }
        public String toString() { return "ASSIGN{'"+name+"'," + value + "}"; }
      }

      public static class ExprFuncDef
      {
        ExprFuncDef(String name, int nargs, BiFunction<Expr[], Map<String, Integer>, Integer> func) { this.name=name; this.func=func; this.nargs=nargs; }
        public final String name;
        public final BiFunction<Expr[], Map<String, Integer>, Integer> func;
        public final int nargs;
      }

      public static class ParsedLine
      {
        public final Expr expression;
        public final String assignment_symbol;
        public final String error;
        public final String line;
        public final Set<String> symbols = new HashSet<>();

        public int pe = -1;
        public char c = ' ';
        public final Map<String, ExprFuncDef> functions = new HashMap<>();

        private static List<Expr> list_of(Expr... exprs)
        { return Arrays.asList(exprs); }

        public static ParsedLine of(String line)
        { return of(line, ""); }

        public static ParsedLine of(String line, String default_assignment_variable)
        { return of(line, default_assignment_variable, Collections.emptyList()); }

        public static ParsedLine of(String line, String default_assignment_variable, Collection<ExprFuncDef> functions)
        { return new ParsedLine(line, default_assignment_variable, functions); }

        public String toString()
        {
          return "ParsedLine{"
            + " line:\"" + line.replaceAll("\"", "\\\"") + "\","
            + ((error.isEmpty()) ? "" : (" error:\"" + error.replaceAll("\"", "\\\"") + " @pos=" + pe + "\","))
            + " sym:\"" + String.join(",", symbols) + "\","
            + " expr:\"" + expression + "\" "
            + "}";
        }

        private ParsedLine(String line, String default_assignment_variable, Collection<ExprFuncDef> functions)
        {
          this.line = line;
          Expr exp = Expr.EMPTY;
          String err = "";
          String assign = "";
          functions.forEach((def)->this.functions.put(def.name.toLowerCase(), def));
          if(!line.matches("^[\\s]*#.*")) {
            try {
              adv();
              exp = expr_assign(default_assignment_variable);
              if(pe < line.length()) {
                exp = Expr.EMPTY;
                err = "invalid_character";
              } else {
                assign = exp.name.toLowerCase();
              }
            } catch(Exception e) {
              err = e.getMessage();
            }
          }
          this.expression = exp;
          this.assignment_symbol = assign;
          this.error = err;
        }

        private void adv()
        {
          if(++pe >= line.length()) {
            c = '\0';
          } else if((c=='\n') || (c=='\r') || (c=='#')) { // EOL, line comment start
            pe = line.length();
            c = '\0';
          } else {
            final int ci = line.charAt(pe);
            if(ci>127) {
              throw new RuntimeException("invalid_character");
            } else {
              c = Character.toLowerCase((char)ci);
            }
          }
        }

        private boolean adv(char match)
        {
          while((c==' ') || (c=='\t') || (c=='#')) adv();
          if(c != match) return false;
          adv();
          return true;
        }

        private boolean adv(String match)
        {
          while((c==' ') || (c=='\t') || (c=='#')) adv();
          if(!line.regionMatches(true, pe, match, 0, match.length())) return false;
          pe += match.length()-1;
          adv();
          return true;
        }

        private Expr expr_assign(String default_assignment_variable)
        {
          String ref = default_assignment_variable;
          if(line.matches("^[\\s]*[a-zA-Z][\\w.]*[\\s]*[=][^=].*")) {
            ref = const_literal();
            if(!adv('=')) throw new RuntimeException("expected_assignment");
            if(functions.containsKey(ref.toLowerCase())) throw new RuntimeException("symbol_readonly");
          }
          symbols.add(ref);
          return new ExprAssign(ref, expr());
        }

        private Expr expr()
        { return expr_or(); }

        private Expr expr_or()
        {
          Expr x = expr_xor();
          while(true) {
            if(adv("or")) x = new ExprOr(list_of(x, expr_xor()));
            else if(adv("||")) x = new ExprOr(list_of(x, expr_xor()));
            else if(adv('|')) x = new ExprOr(list_of(x, expr_xor()));
            else return x;
          }
        }

        private Expr expr_xor()
        {
          Expr x = expr_and();
          while(true) {
            if(adv("xor")) x = new ExprXor(list_of(x, expr_and()));
            else if(adv('^')) x = new ExprXor(list_of(x, expr_and()));
            else return x;
          }
        }

        private Expr expr_and()
        {
          Expr x = expr_rel();
          while(true) {
            if(adv("and")) x = new ExprAnd(list_of(x, expr_rel()));
            else if(adv("&&")) x = new ExprAnd(list_of(x, expr_rel()));
            else if(adv('&')) x = new ExprAnd(list_of(x, expr_rel()));
            else return x;
          }
        }

        private Expr expr_rel()
        {
          Expr x = arith_add();
          while(true) {
            if(adv("!=")) x = new ExprNeq(list_of(x, arith_add()));
            if(adv("<>")) x = new ExprNeq(list_of(x, arith_add()));
            if(adv("==")) x = new ExprEq(list_of(x, arith_add()));
            if(adv(">=")) x = new ExprGe(list_of(x, arith_add()));
            if(adv("<=")) x = new ExprLe(list_of(x, arith_add()));
            if(adv('>')) x = new ExprGt(list_of(x, arith_add()));
            if(adv('<')) x = new ExprLt(list_of(x, arith_add()));
            else return x;
          }
        }

        private Expr arith_add()
        {
          Expr x = arith_mpy();
          while(true) {
            if(adv('+')) x = new ExprAdd(list_of(x, arith_mpy()));
            else if(adv('-')) x = new ExprSub(list_of(x, arith_mpy()));
            else return x;
          }
        }

        private Expr arith_mpy()
        {
          Expr x = arith_fact();
          while(true) {
            if (adv('*')) x = new ExprMpy(list_of(x, arith_fact()));
            else if (adv('/')) x = new ExprDiv(list_of(x, arith_fact()));
            else if (adv('%')) x = new ExprMod(list_of(x, arith_fact()));
            else return x;
          }
        }

        private Expr arith_fact()
        {
          if(adv('+')) adv();
          if(adv('-') && (!adv('-'))) return new ExprNeg(list_of(arith_fact()));
          if(adv('!')) return new ExprNot(list_of(arith_fact()));
          if(adv('(')) {
            Expr e = expr();
            if(!adv(')')) throw new RuntimeException("missing_closing_parenthesis");
            return e;
          } else if((c>='0') && (c<='9')) {
            return new ExprConst(const_number());
          } else if(c>='a' && c<='z') {
            final String sym = const_literal();
            if(sym.equals("not")) {
              return new ExprNot(list_of(expr()));
            } else if(adv('(')) {
              List<Expr> args = new ArrayList<>();
              if(!adv(')')) {
                args.add(expr());
                while(adv(',')) args.add(expr());
                if(!adv(')')) throw new RuntimeException("missing_closing_function_parenthesis");
              }
              final ExprFuncDef fn = functions.getOrDefault(sym, null);
              if(fn==null) throw new RuntimeException("unknown_function");
              return new ExprFunc(fn.name, fn.nargs, fn.func, args);
            } else {
              if(functions.containsKey(sym)) throw new RuntimeException("missing_function_arguments");
              symbols.add(sym);
              return new ExprVarRef(sym);
            }
          }
          throw new RuntimeException("unexpected_character");
        }

        private String const_literal()
        {
          if(c<'a' || c>'z') return "";
          final int p0 = this.pe;
          while((c>='a' && c<='z') || (c>='0' && c<='9') || (c=='.') || (c=='_')) adv(); // Dot is allowed
          return line.substring(p0, this.pe).toLowerCase();
        }

        private int const_number()
        {
          final int p0 = this.pe;
          while((c>='0') && (c<='9')) adv();
          return Integer.parseInt(line.substring(p0, this.pe));
        }

      }
    }
  }

}
