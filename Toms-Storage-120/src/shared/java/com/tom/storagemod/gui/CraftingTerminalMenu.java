package com.tom.storagemod.gui;

import java.util.List;

import net.minecraft.client.RecipeBookCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import com.google.common.collect.Lists;

import com.tom.storagemod.Content;
import com.tom.storagemod.tile.CraftingTerminalBlockEntity;
import com.tom.storagemod.util.IAutoFillTerminal;
import com.tom.storagemod.util.IDataReceiver;
import com.tom.storagemod.util.StoredItemStack;
import com.tom.storagemod.util.TerminalCraftingFiller;
import com.tom.storagemod.util.TerminalRecipePlacer;

public class CraftingTerminalMenu extends StorageTerminalMenu implements IAutoFillTerminal, IDataReceiver, IForgeMenu {
	public static class SlotCrafting extends Slot {
		public SlotCrafting(Container inventoryIn, int index, int xPosition, int yPosition) {
			super(inventoryIn, index, xPosition, yPosition);
		}
	}

	private final CraftingContainer craftMatrix;
	private final ResultContainer craftResult;
	private Slot craftingResultSlot;
	private final List<ContainerListener> listeners = Lists.newArrayList();

	@Override
	public void addSlotListener(ContainerListener listener) {
		super.addSlotListener(listener);
		listeners.add(listener);
	}

	@Override
	public void removeSlotListener(ContainerListener listener) {
		super.removeSlotListener(listener);
		listeners.remove(listener);
	}

	public CraftingTerminalMenu(int id, Inventory inv, CraftingTerminalBlockEntity te) {
		super(Content.craftingTerminalCont.get(), id, inv, te);
		craftMatrix = te.getCraftingInv();
		craftResult = te.getCraftResult();
		init();
		this.addPlayerSlots(inv, 8, 174);
		te.registerCrafting(this);
	}

	public CraftingTerminalMenu(int id, Inventory inv) {
		super(Content.craftingTerminalCont.get(), id, inv);
		craftMatrix = new TransientCraftingContainer(this, 3, 3);
		craftResult = new ResultContainer();
		init();
		this.addPlayerSlots(inv, 8, 174);
	}

	@Override
	public void removed(Player playerIn) {
		super.removed(playerIn);
		if(te != null)
			((CraftingTerminalBlockEntity) te).unregisterCrafting(this);
	}

	private void init() {
		int x = -4;
		int y = 94;
		this.addSlot(craftingResultSlot = new Result(x + 124, y + 35));

		for(int i = 0; i < 3; ++i) {
			for(int j = 0; j < 3; ++j) {
				this.addSlot(new SlotCrafting(craftMatrix, j + i * 3, x + 30 + j * 18, y + 17 + i * 18));
			}
		}
	}

	private class Result extends ResultSlot {

		public Result(int x, int y) {
			super(pinv.player, craftMatrix, craftResult, 0, x, y);
		}

		@Override
		public void onTake(Player thePlayer, ItemStack stack) {
			this.checkTakeAchievements(stack);
			if (!pinv.player.getCommandSenderWorld().isClientSide) {
				((CraftingTerminalBlockEntity) te).craft(thePlayer);
			}
		}
	}

	@Override
	protected void addStorageSlots() {
		addStorageSlots(5, 8, 18);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slotIn) {
		return slotIn.container != craftResult && super.canTakeItemForPickAll(stack, slotIn);
	}

	@Override
	public ItemStack shiftClickItems(Player playerIn, int index) {
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack itemstack1 = slot.getItem();
			itemstack = itemstack1.copy();
			if (index == 0) {
				if(te == null)return ItemStack.EMPTY;

				if (!((CraftingTerminalBlockEntity)te).canCraft() || !this.moveItemStackTo(itemstack1, 10, 46, true)) {
					return ItemStack.EMPTY;
				}

				slot.onQuickCraft(itemstack1, itemstack);

				if (itemstack1.isEmpty()) {
					slot.set(ItemStack.EMPTY);
				} else {
					slot.setChanged();
				}

				if (itemstack1.getCount() == itemstack.getCount()) {
					return ItemStack.EMPTY;
				}

				slot.onTake(playerIn, itemstack1);
				if (index == 0) {
					playerIn.drop(itemstack1, false);
				}

				return itemstack;
			} else if (index > 0 && index < 10) {
				if(te == null)return ItemStack.EMPTY;
				ItemStack stack = ((CraftingTerminalBlockEntity) te).pushStack(itemstack);
				slot.set(stack);
				if (!playerIn.level().isClientSide)
					broadcastChanges();
			}
			slot.onTake(playerIn, itemstack1);
		}
		return ItemStack.EMPTY;
	}

	public void onCraftMatrixChanged() {
		for (int i = 0; i < slots.size(); ++i) {
			Slot slot = slots.get(i);

			if (slot instanceof SlotCrafting || slot == craftingResultSlot) {
				for (ContainerListener listener : listeners) {
					if (listener instanceof ServerPlayer) {
						((ServerPlayer) listener).connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), i, slot.getItem()));
					}
				}
			}
		}
	}

	@Override
	public boolean clickMenuButton(Player playerIn, int id) {
		if(te != null && id == 0)
			((CraftingTerminalBlockEntity) te).clear(playerIn);
		else if(te != null && id == 1)
			((CraftingTerminalBlockEntity) te).polymorphUpdate();
		else super.clickMenuButton(playerIn, id);
		return false;
	}

	@Override
	public void fillCraftSlotsStackedContents(StackedContents itemHelperIn) {
		this.craftMatrix.fillStackedContents(itemHelperIn);
		if(te != null)sync.fillStackedContents(itemHelperIn);
		else itemList.forEach(e -> {
			itemHelperIn.accountSimpleStack(e.getActualStack());
		});
	}

	@Override
	public void clearCraftingContent() {
		this.craftMatrix.clearContent();
		this.craftResult.clearContent();
	}

	@Override
	public boolean recipeMatches(Recipe<? super CraftingContainer> recipeIn) {
		return recipeIn.matches(this.craftMatrix, this.pinv.player.level());
	}

	@Override
	public int getResultSlotIndex() {
		return 0;
	}

	@Override
	public int getGridWidth() {
		return this.craftMatrix.getWidth();
	}

	@Override
	public int getGridHeight() {
		return this.craftMatrix.getHeight();
	}

	@Override
	public int getSize() {
		return 10;
	}

	@Override
	public java.util.List<RecipeBookCategories> getRecipeBookCategories() {
		return Lists.newArrayList(RecipeBookCategories.CRAFTING_SEARCH, RecipeBookCategories.CRAFTING_EQUIPMENT, RecipeBookCategories.CRAFTING_BUILDING_BLOCKS, RecipeBookCategories.CRAFTING_MISC, RecipeBookCategories.CRAFTING_REDSTONE);
	}

	@Override
	public TerminalRecipePlacer getRecipePlacer() {
		return new TerminalRecipePlacer(this, (CraftingTerminalBlockEntity) te, pinv.player);
	}

	@Override
	public void receive(CompoundTag message) {
		super.receive(message);
		if(message.contains("fill")) {
			var id = ResourceLocation.tryParse(message.getString("fill"));
			if (id != null) {
				var recipe = pinv.player.level().getRecipeManager().byKey(id).orElse(null);
				if (recipe != null) {
					new TerminalCraftingFiller((CraftingTerminalBlockEntity) te, pinv.player, sync).placeRecipe(recipe);
				}
			}
		}
	}

	@Override
	public List<StoredItemStack> getStoredItems() {
		return itemList;
	}

	public Slot getCraftingResultSlot() {
		return craftingResultSlot;
	}

	public Player getPlayer() {
		return pinv.player;
	}
}
