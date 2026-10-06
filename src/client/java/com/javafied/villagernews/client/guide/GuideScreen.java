package com.javafied.villagernews.client.guide;

import com.javafied.villagernews.client.guide.GuideBook.Control;
import com.javafied.villagernews.client.guide.GuideBook.Entry;
import com.javafied.villagernews.client.guide.GuideBook.Page;
import com.javafied.villagernews.client.guide.GuideBook.SearchEntry;
import com.javafied.villagernews.guide.GuidePayloads;
import com.javafied.villagernews.guide.GuideSettings;

import com.javafied.villagernews.platform.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The Villager News Handbook. A hand port of the add-on's page builders - a
 * menu of buttons, a list of entries, a menu with a search box over every
 * trigger, a trigger's own page, and the settings - drawing the pages the
 * converter read out of the add-on. "Back" goes to the page you came from,
 * as in the add-on's form.
 */
public final class GuideScreen extends Screen {
	private static final int MAX_COLUMN_WIDTH = 310;
	private static final int SPACER = 6;
	private static final int DIVIDER_COLOR = 0x60FFFFFF;

	/** The world settings the server last sent (null: none - it hasn't got the mod, or not yet). */
	static volatile GuidePayloads.Settings worldSettings;

	private final GuideBook book;
	private final Deque<String> history = new ArrayDeque<>();
	private String page;
	private EditBox search;
	private String query = "";
	private HeaderAndFooterLayout layout;
	private ScrollableLayout scroll;

	public GuideScreen(GuideBook book) {
		super(BedrockText.of(book.title()));
		this.book = book;
		this.page = book.start();
	}

	@Override
	protected void init() {
		Page current = book.pages().get(page);
		layout = new HeaderAndFooterLayout(this);
		layout.addTitleHeader(BedrockText.of(book.titleOf(page)), font);
		LinearLayout column = LinearLayout.vertical().spacing(2);
		column.defaultCellSetting().alignHorizontallyCenter();
		if (current != null) {
			switch (current.type()) {
				case "menu" -> menu(column, current);
				case "entries" -> entries(column, current);
				case "search" -> searchMenu(column, current);
				case "trigger" -> {
					column.addChild(label(current.text()));
					back(column, true);
				}
				case "settings" -> settings(column, current);
				default -> back(column, true);
			}
		}
		scroll = layout.addToContents(new ScrollableLayout(minecraft, column, layout.getContentHeight()));
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
		if (search != null && !query.isEmpty()) {
			setFocused(search);
		}
	}

	@Override
	protected void repositionElements() {
		if (layout == null) {
			return;
		}
		// The page's own layout first: the outer one only places the scroll area (as vanilla's screens do).
		scroll.arrangeElements();
		scroll.setMaxHeight(layout.getContentHeight());
		layout.arrangeElements();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// The add-on's page builders.

	private void menu(LinearLayout column, Page p) {
		column.addChild(SpacerElement.height(SPACER));
		column.addChild(label(p.text()));
		if (!p.buttons().isEmpty()) {
			column.addChild(SpacerElement.height(SPACER));
		}
		p.buttons().forEach(b -> column.addChild(link(b.label(), b.page())));
		if (p.back()) {
			back(column, true);
		}
	}

	private void entries(LinearLayout column, Page p) {
		column.addChild(SpacerElement.height(SPACER));
		if (!p.text().isEmpty()) {
			column.addChild(label(p.text()));
			column.addChild(new Divider(columnWidth()));
		}
		for (Entry entry : p.entries()) {
			column.addChild(new MultiLineTextWidget(BedrockText.of(entry.header()).copy().withStyle(Style.EMPTY.withBold(true)), font)
					.setMaxWidth(columnWidth()));
			column.addChild(SpacerElement.height(SPACER / 2));
			column.addChild(label(entry.body()));
			column.addChild(new Divider(columnWidth()));
		}
		if (p.back()) {
			back(column, true);
		}
	}

	/** A menu with a search box over every trigger: while searching, the matches replace the menu's buttons. */
	private void searchMenu(LinearLayout column, Page p) {
		column.addChild(SpacerElement.height(SPACER));
		column.addChild(label(p.text()));
		column.addChild(SpacerElement.height(SPACER));
		if (search == null) {
			search = new EditBox(font, columnWidth(), 20, BedrockText.of(book.ui().searchLabel()));
			search.setHint(BedrockText.of(book.ui().searchLabel()).copy().withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true)));
			search.setValue(query);
			search.setResponder(value -> {
				if (!value.equals(query)) {
					query = value;
					rebuildWidgets();
				}
			});
		}
		column.addChild(search);
		boolean searching = !query.trim().isEmpty();
		if (searching) {
			List<SearchEntry> matches = GuideBook.search(p.search(), query, book.ui().maxResults());
			if (matches.isEmpty()) {
				column.addChild(SpacerElement.height(SPACER));
				column.addChild(label(book.ui().noResults()));
			}
			for (SearchEntry match : matches) {
				column.addChild(link(match.label(), match.page()));
			}
			column.addChild(new Divider(columnWidth()));
			column.addChild(button(book.ui().clearSearch(), () -> search.setValue("")));
		} else {
			if (!p.buttons().isEmpty()) {
				column.addChild(SpacerElement.height(SPACER));
			}
			p.buttons().forEach(b -> column.addChild(link(b.label(), b.page())));
		}
		back(column, true);
	}

	private void settings(LinearLayout column, Page p) {
		column.addChild(SpacerElement.height(SPACER));
		GuidePayloads.Settings world = worldSettings;
		for (Control control : p.controls()) {
			AbstractWidget widget = switch (control.id()) {
				case "subtitles" -> toggle(control, ClientSettings.subtitles(), ClientSettings::setSubtitles);
				case "style" -> dropdown(control, ClientSettings.style(), ClientSettings::setStyle);
				case GuideSettings.CHATTINESS -> dropdown(control, world == null ? GuideSettings.CHATTY : world.chattiness(),
						value -> change(GuideSettings.CHATTINESS, value));
				case GuideSettings.RARE_LINES -> dropdown(control, world == null ? GuideSettings.RARE_DEFAULT : world.rareLines(),
						value -> change(GuideSettings.RARE_LINES, value));
				case GuideSettings.SPECIAL_VILLAGERS -> toggle(control, world == null || world.specialVillagers(),
						value -> change(GuideSettings.SPECIAL_VILLAGERS, value ? 1 : 0));
				default -> null;
			};
			if (widget == null) {
				continue;
			}
			boolean worldSetting = !control.id().equals("subtitles") && !control.id().equals("style");
			if (worldSetting && (world == null || !world.canEdit())) {
				widget.active = false;
				widget.setTooltip(Tooltip.create(Component.translatable(world == null
						? "guide.villagernewsjavafied.no_server_settings" : "guide.villagernewsjavafied.operators_only")));
			}
			column.addChild(widget);
			column.addChild(new MultiLineTextWidget(BedrockText.of(control.description()).copy().withStyle(ChatFormatting.GRAY), font)
					.setMaxWidth(columnWidth()));
			column.addChild(SpacerElement.height(SPACER));
		}
		back(column, false);
	}

	private AbstractWidget toggle(Control control, boolean value, java.util.function.Consumer<Boolean> onChange) {
		return CycleButton.onOffBuilder(value).create(0, 0, columnWidth(), 20, BedrockText.of(control.label()),
				(button, v) -> onChange.accept(v));
	}

	private AbstractWidget dropdown(Control control, int value, java.util.function.IntConsumer onChange) {
		List<Integer> values = IntStream.range(0, control.items().size()).boxed().toList();
		if (values.isEmpty()) {
			return null;
		}
		int initial = Math.max(0, Math.min(values.size() - 1, value));
		return CycleButton.<Integer>builder(i -> BedrockText.of(control.items().get(i))).withValues(values).withInitialValue(initial)
				.create(0, 0, columnWidth(), 20, BedrockText.of(control.label()), (button, v) -> onChange.accept(v));
	}

	private static void change(String setting, int value) {
		if (ClientPlayNetworking.canSend(GuidePayloads.Change.TYPE)) {
			ClientPlayNetworking.send(new GuidePayloads.Change(setting, value));
		}
	}

	// Elements.

	private MultiLineTextWidget label(String text) {
		return new MultiLineTextWidget(BedrockText.of(text), font).setMaxWidth(columnWidth());
	}

	private AbstractWidget link(String label, String target) {
		return button(label, () -> open(target));
	}

	private AbstractWidget button(String label, Runnable onPress) {
		return net.minecraft.client.gui.components.Button.builder(BedrockText.of(label), b -> onPress.run()).width(columnWidth()).build();
	}

	private void back(LinearLayout column, boolean spaced) {
		if (spaced) {
			column.addChild(SpacerElement.height(SPACER));
		}
		column.addChild(button(book.ui().back().isEmpty() ? "<" : book.ui().back(), this::goBack));
	}

	private int columnWidth() {
		return Math.min(MAX_COLUMN_WIDTH, width - 40);
	}

	// Navigation.

	/** Goes to a page, as its button would. */
	public void openPage(String target) {
		open(target);
	}

	/** Types into the search box, if this page has one. */
	public void search(String text) {
		if (search != null) {
			search.setValue(text);
		}
	}

	private void open(String target) {
		if (!book.pages().containsKey(target)) {
			return;
		}
		history.push(page);
		show(target);
	}

	private void goBack() {
		if (history.isEmpty()) {
			onClose();
		} else {
			show(history.pop());
		}
	}

	private void show(String target) {
		page = target;
		search = null;
		query = "";
		rebuildWidgets();
	}

	/** The form's divider: a faint line across the column. */
	private static final class Divider extends AbstractWidget {
		Divider(int width) {
			super(0, 0, width, 7, Component.empty());
			active = false;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.hLine(getX(), getX() + getWidth() - 1, getY() + 3, DIVIDER_COLOR);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
