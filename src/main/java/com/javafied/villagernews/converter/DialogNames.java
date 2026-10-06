package com.javafied.villagernews.converter;

import com.javafied.villagernews.names.AddonNames;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gives every dialog a readable name, worked out from the add-on itself so a
 * new add-on version (with every id re-rolled) mostly names itself:
 * <ol>
 * <li>the names file, where it lists the dialog ({@link AddonNames.Kind#DIALOG});</li>
 * <li>else the dialog's guide title, where only one dialog has it
 * ("Start Work" -> {@code start_work});</li>
 * <li>else, for a later part of a conversation, the name of the line that
 * opens it and the part's number ({@code <name>_part_2});</li>
 * <li>else it keeps its id - reported, so a names file can pick it up.</li>
 * </ol>
 */
final class DialogNames {
	/** The script's guide entries: {@code [<table>.<dialog id>]: {title: "..."}}. */
	private static final Pattern GUIDE_TITLE = Pattern.compile("\\[[A-Za-z_$][\\w$]*\\.(\\w+)\\]:\\{title:\"((?:[^\"\\\\]|\\\\.)*)\"");

	private DialogNames() {
	}

	/** @return dialog id -> name, for every dialog in {@code ids}; ids left unnamed go into {@code unnamed} */
	static Map<String, String> assign(String script, Set<String> ids, List<List<String>> conversations, AddonNames names,
			List<String> unnamed) {
		Map<String, String> byId = new LinkedHashMap<>();
		Set<String> taken = new HashSet<>();
		for (Map.Entry<String, String> listed : names.all(AddonNames.Kind.DIALOG).entrySet()) {
			if (ids.contains(listed.getValue())) {
				byId.put(listed.getValue(), listed.getKey());
				taken.add(listed.getKey());
			}
		}

		Map<String, String> titleNames = new HashMap<>();
		Map<String, Integer> titleUses = new HashMap<>();
		Matcher m = GUIDE_TITLE.matcher(script);
		while (m.find()) {
			if (ids.contains(m.group(1)) && !titleNames.containsKey(m.group(1))) {
				String name = slug(unescape(m.group(2)));
				titleNames.put(m.group(1), name);
				titleUses.merge(name, 1, Integer::sum);
			}
		}
		for (Map.Entry<String, String> titled : titleNames.entrySet()) {
			String name = titled.getValue();
			if (!byId.containsKey(titled.getKey()) && !name.isEmpty() && titleUses.get(name) == 1 && taken.add(name)) {
				byId.put(titled.getKey(), name);
			}
		}

		for (List<String> conversation : conversations) {
			String opener = byId.get(conversation.getFirst());
			for (int part = 1; opener != null && part < conversation.size(); part++) {
				String id = conversation.get(part);
				String name = opener + "_part_" + (part + 1);
				if (!byId.containsKey(id) && taken.add(name)) {
					byId.put(id, name);
				}
			}
		}

		for (String id : ids) {
			if (!byId.containsKey(id)) {
				unnamed.add(id);
				byId.put(id, id);
			}
		}
		return byId;
	}

	/** Minecraft's style: lower case, words joined by underscores. */
	static String slug(String title) {
		// A possessive stays one word ("villager's" -> villagers); other apostrophes split words ("o'lantern").
		return title.toLowerCase(java.util.Locale.ROOT).replaceAll("['\u2019]s\\b", "s").replaceAll("[^a-z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
	}

	private static String unescape(String text) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\' && i + 1 < text.length()) {
				char next = text.charAt(++i);
				if (next == 'u' && i + 4 < text.length()) {
					out.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
					i += 4;
				} else {
					out.append(next == 'n' ? ' ' : next);
				}
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}
}
