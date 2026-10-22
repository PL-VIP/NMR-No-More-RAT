package untrusted.smoke;

public final class FabricClasspathSmoke {
    private FabricClasspathSmoke() { }

    public static void main(String[] args) throws Exception {
        Class<?> loaderUtil = Class.forName("net.fabricmc.loader.impl.util.LoaderUtil");
        loaderUtil.getMethod("verifyClasspath").invoke(null);
        System.out.println("NMR_FABRIC_CLASSPATH_OK: Fabric found no duplicate ASM classes");
    }
}
