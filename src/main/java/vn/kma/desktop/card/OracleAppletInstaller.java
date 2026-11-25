package vn.kma.desktop.card;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Runs only the project's fixed CAP installer; accepts no shell commands or client paths. */
public final class OracleAppletInstaller {
    private OracleAppletInstaller() {}
    public static Map<String,Object> status() {
        Path sim=simulator();
        return Map.of("capReady",Files.isRegularFile(cap()),"installerReady",Files.isRegularFile(Path.of("card-applet/build/tools/InstallApplet.class")),
            "sdkReady",Files.isRegularFile(sim.resolve("client/AMService/amservice.jar"))&&Files.isRegularFile(sim.resolve("client/COMService/socketprovider.jar"))&&Files.isRegularFile(sim.resolve("samples/client.config.properties")));
    }
    private static Path simulator(){return Path.of(System.getenv().getOrDefault("JC_HOME_SIMULATOR","D:/SmartCard/simulator"));}
    private static Path cap(){return Path.of("card-applet/build/cap/kma/idp/card/javacard/card.cap");}
    public static void install() throws Exception {
        if(status().values().stream().anyMatch(v->Boolean.FALSE.equals(v)))throw new CardOperationException("ORACLE_INSTALL_FILES_MISSING");
        Path sim=simulator(), java=Path.of(System.getProperty("java.home"),"bin","java.exe");
        var process=new ProcessBuilder(java.toString(),"-p",sim.resolve("client/AMService/amservice.jar")+";"+sim.resolve("client/COMService/socketprovider.jar"),
            "--add-modules","ALL-MODULE-PATH","-cp",Path.of("card-applet/build/tools").toAbsolutePath().toString(),"InstallApplet",cap().toAbsolutePath().toString(),sim.resolve("samples/client.config.properties").toString())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new CardOperationException("ORACLE_INSTALL_TIMEOUT");}
        if(process.exitValue()!=0)throw new CardOperationException("ORACLE_INSTALL_FAILED");
    }
}
