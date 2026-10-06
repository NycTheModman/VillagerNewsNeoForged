package com.javafied.villagernews.client.guide;

import com.javafied.villagernews.client.guide.GuideBook.SearchEntry;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideBookTest {
	@Test
	void searchRanksNamesBeforeSectionsAndWordStartsFirst() {
		List<SearchEntry> index = List.of(new SearchEntry("Unsheared", "Everyday Actions", "a"),
				new SearchEntry("Shear a Sheep", "Everyday Actions", "b"), new SearchEntry("Fishing", "Shears & Things", "c"),
				new SearchEntry("Walk", "Movement", "d"));
		assertEquals(List.of("b", "a", "c"), GuideBook.search(index, " SHEAR ", 8).stream().map(SearchEntry::page).toList());
		assertEquals(1, GuideBook.search(index, "shear", 1).size());
		assertEquals(List.of(), GuideBook.search(index, "   ", 8));
	}

	@Test
	void bedrockOnlyColoursDontUnderline() {
		Component text = BedrockText.of("§eTrigger\n§nCopper §lbold§r plain");
		assertEquals("Trigger\nCopper bold plain", text.getString());
		Component copper = text.getSiblings().get(1);
		assertEquals(false, copper.getStyle().isUnderlined(), "§n is Bedrock's copper colour");
	}
}
