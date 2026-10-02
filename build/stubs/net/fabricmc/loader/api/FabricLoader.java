package net.fabricmc.loader.api;
import java.nio.file.Path;
public class FabricLoader {
    private static final FabricLoader I = new FabricLoader();
    public static FabricLoader getInstance(){ return I; }
    public Path getGameDir(){ return java.nio.file.Paths.get("."); }
}
