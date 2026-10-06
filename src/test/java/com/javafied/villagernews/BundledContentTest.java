package com.javafied.villagernews;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class BundledContentTest {
 private JsonObject json(String path)throws Exception {
  try(var in=getClass().getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}
 }
 @Test void everyDialogueHasBundledAudio()throws Exception {
  var dialogs=json("/bundled/server/dialogs.json").getAsJsonObject("dialogs");
  var sounds=json("/assets/villagernewsjavafied/sounds.json");assertEquals(535,dialogs.size());int lines=0;
  for(var entry:dialogs.entrySet())for(var line:entry.getValue().getAsJsonObject().getAsJsonArray("lines")){
   lines++;String sound=line.getAsJsonObject().get("sound").getAsString().replace(':','.');assertTrue(sounds.has(sound),sound);
   for(var audio:sounds.getAsJsonObject(sound).getAsJsonArray("sounds")){
    String id=audio.isJsonPrimitive()?audio.getAsString():audio.getAsJsonObject().get("name").getAsString();String[] parts=id.split(":",2);
    assertNotNull(getClass().getResource("/assets/"+parts[0]+"/sounds/"+parts[1]+".ogg"),id);
   }
  }assertEquals(2236,lines);
 }
}
