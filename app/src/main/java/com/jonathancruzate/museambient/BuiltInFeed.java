package com.jonathancruzate.museambient;
import java.util.*;
/**
 * Bundled playlist, curated to the panel (v0.2.1, Jonathan's rule):
 * ONLY art that fits the TV's aspect ratio. The Q80R is 16:9 (1.778);
 * every item below was verified against the Wikimedia Commons API
 * (prop=imageinfo, iiprop=size) and sits within plus/minus 5 percent of
 * 16:9, i.e. ratio 1.69 to 1.87. ImageLoader enforces the same rule on
 * ANY feed at decode time. Dimensions below are the Commons originals.
 *
 *   The Heart of the Andes (Church, 1859)        6400x3503   1.827
 *   Autumn, On the Hudson River (Cropsey, 1860)  4000x2201   1.817
 *   Fox Hunt (Homer, 1893)                       5000x2788   1.793
 *   Rye (Shishkin, 1878)                         4000x2254   1.775
 *   A Dash for the Timber (Remington, 1889)      4236x2400   1.765
 *   The Icebergs (Church, 1861)                  4000x2266   1.765
 *   Grand Canyon of the Yellowstone (Moran, 1872) 1555x895   1.737
 *   The Chasm of the Colorado (Moran, 1873)      1565x902    1.735
 *   The Valley of Wyoming (Cropsey, 1865)        3807x2199   1.731
 *   Cosmic Cliffs, Carina (JWST, 2022)           14575x8441  1.727
 *   Washington Crossing the Delaware (Leutze, 1851) 3888x2285 1.702
 *
 * Removed from the v0.1.0 list for failing the ratio rule (all verified):
 * Starry Night 1.263, Impression Sunrise 1.301, The Milkmaid 0.892,
 * Girl with a Pearl Earring 0.844, Mona Lisa 0.671, The Kiss 0.997,
 * Wanderer above the Sea of Fog 0.781, The Night Watch 1.194,
 * The Fighting Temeraire 1.346, Great Wave 1.461, Red Fuji 1.498,
 * Grande Jatte 1.502, Birth of Venus 1.592, Water Lilies (file not
 * found under its v0.1.0 name on re-verification). Also checked and
 * rejected: The Gulf Stream 1.663, Eaton's Neck 2.071, Emigrants
 * Crossing the Plains 1.605, the MMA scan of Washington Crossing 1.561
 * (a different Met scan of the same canvas, above, is in tolerance).
 * The two Moran originals are small (about 1.5k wide); they are the best
 * Commons scans of those canvases and will render softer than the rest.
 *
 * Images load via Wikimedia Commons Special:FilePath at width=3840:
 *   https://commons.wikimedia.org/wiki/Special:FilePath/<file>?width=3840
 * (ImageLoader steps down to 2560/1920 per item if the 3840 fetch fails.
 * Non-ASCII filenames are percent-encoded; both encoded names below were
 * verified to resolve with an HTTP 302 from Special:FilePath.)
 */
public class BuiltInFeed {
    private static String u(String f){return "https://commons.wikimedia.org/wiki/Special:FilePath/"+f+"?width=3840";}
    public static List<ArtItem> items(){
        List<ArtItem> l=new ArrayList<>();
        l.add(new ArtItem("The Heart of the Andes","Frederic Edwin Church","1859","The Met, Open Access",u("Church Heart of the Andes.jpg")));
        l.add(new ArtItem("Autumn, On the Hudson River","Jasper Francis Cropsey","1860","National Gallery of Art",u("Autumn--On the Hudson River-1860-Jasper Francis Cropsey.jpg")));
        l.add(new ArtItem("Washington Crossing the Delaware","Emanuel Leutze","1851","The Met, Open Access",u("Emanuel Leutze Washington Crossing the Delaware The Metropolitan Museum of Art.jpg")));
        l.add(new ArtItem("The Icebergs","Frederic Edwin Church","1861","Dallas Museum of Art",u("The Icebergs (Frederic Edwin Church).jpg")));
        l.add(new ArtItem("Fox Hunt","Winslow Homer","1893","Pennsylvania Academy of the Fine Arts",u("Fox Hunt 1893 Winslow Homer.jpg")));
        l.add(new ArtItem("A Dash for the Timber","Frederic Remington","1889","Amon Carter Museum of American Art",u("A Dash for the Timber by Frederic Remington.jpg")));
        l.add(new ArtItem("The Valley of Wyoming","Jasper Francis Cropsey","1865","The Met, Open Access",u("The Valley of Wyoming MET DT4598.jpg")));
        l.add(new ArtItem("Rye","Ivan Shishkin","1878","State Tretyakov Gallery",u("Ivan Shishkin - %D0%A0%D0%BE%D0%B6%D1%8C - Google Art Project.jpg")));
        l.add(new ArtItem("Cosmic Cliffs in the Carina Nebula","NASA / ESA / CSA, James Webb Space Telescope","2022","NASA, Public Domain",u("NASA%E2%80%99s Webb Reveals Cosmic Cliffs, Glittering Landscape of Star Birth.jpg")));
        l.add(new ArtItem("The Grand Canyon of the Yellowstone","Thomas Moran","1872","Smithsonian American Art Museum",u("Thomas Moran - Grand Canyon of the Yellowstone.jpg")));
        l.add(new ArtItem("The Chasm of the Colorado","Thomas Moran","1873","Smithsonian American Art Museum",u("Thomas Moran - The Chasm of the Colorado - L.1968.84.2 - Smithsonian American Art Museum.jpg")));
        return l;
    }
}
