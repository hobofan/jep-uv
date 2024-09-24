package com.hobofan.jepuv;

import java.io.*;
import java.lang.reflect.Field;

import jep.*;

import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class JepSetup {
    private enum OS {
        WINDOWS, LINUX, MAC_ARM64, MAC_X86_64
    }

    private static OS getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OS.LINUX;
        } else if (osName.contains("mac")) {
            if (osArch.contains("aarch64")) {
                return OS.MAC_ARM64;
            } else {
                return OS.MAC_X86_64;
            }
        }
        return null;
    }

    public static JepConfig setupJepLibraryNew() throws IOException, JepException, InterruptedException {
        // NOTE: Can be adjusted to fit the project
        String pythonTempDirSuffix = "my-library-python";
        String uvTempDirSuffix = "my-library-uv";
        String pythonPackage = "my_package";

        // Extract all files from python to temp directory
        String pythonProjectDistroPath = "/python/";
        File pythonProjectTempDir = new File(System.getProperty("java.io.tmpdir"), pythonTempDirSuffix);
        pythonProjectTempDir.mkdirs();
        try {
            extractResources(pythonProjectDistroPath, pythonProjectTempDir);
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to extract JEP resources", e);
        }

        File uvTempDir = new File(System.getProperty("java.io.tmpdir"), uvTempDirSuffix);
        uvTempDir.mkdirs();

        System.out.println("UV temp dir: " + uvTempDir.getAbsolutePath());
        installUv(pythonProjectTempDir, uvTempDir);
        File uvExecutable = new File(uvTempDir, "bin/uv");

        // TODO: Install python via `uv python install` in my_package directory
        // TODO: Detect python path via `uv python find` in my_package directory

        JepConfig config = new JepConfig();
        return config;
    }

    /**
     * Installs UV from the python project directory into the uv temp directory
     */
    public static void installUv(File pythonProjectTempDir, File uvTempDir) throws IOException, InterruptedException {
        String uvInstallFileName = "";
        switch (getOS()) {
            case WINDOWS:
                uvInstallFileName = "install-uv.ps1";
                break;
            case LINUX:
            case MAC_ARM64:
            case MAC_X86_64:
                uvInstallFileName = "install-uv.sh";
                break;
        }

        File uvInstallFile = new File(pythonProjectTempDir, uvInstallFileName);
        // Make script executable
        uvInstallFile.setExecutable(true);
        // Execute script with envvar `UV_INSTALL_DIR` set to uvTempDir
        ProcessBuilder pb = new ProcessBuilder(uvInstallFile.getAbsolutePath());
        pb.environment().put("UV_INSTALL_DIR", uvTempDir.getAbsolutePath());
        // Log the stdout/stderr output of the script to a string, and if the exit code is not 0, throw an exception
        StringBuilder output = new StringBuilder();
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Failed to install UV: " + output);
        } else {
            // Check if the install was successful
            File uvExecutable = new File(uvTempDir, "bin/uv");
            if (!uvExecutable.exists() || !uvExecutable.canExecute()) {
                throw new IllegalStateException("UV executable not found or not executable");
            }
        }
    }

    public static JepConfig setupJepLibrary() throws IOException, JepException {
        // First, discover and set the Python library path
//        String providedPythonLibPath = null;
        String providedPythonLibPath = "/Users/hobofan/.asdf/installs/python/3.10.14/lib";
        String pythonLibPath = discoverPythonLibPath(providedPythonLibPath);
//        System.out.println("Python library path: " + pythonLibPath);
        if (pythonLibPath != null) {
            // Set java.library.path
            String libraryPath = System.getProperty("java.library.path");
            System.setProperty("java.library.path", libraryPath + File.pathSeparator + pythonLibPath);

            String javaVersion = System.getProperty("java.version");
            String[] versionParts = javaVersion.split("\\.");
            int majorVersion = Integer.parseInt(versionParts[0]);

            if (majorVersion < 9) {
                // Force Java to reload the java.library.path
                try {
                    Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
                    fieldSysPath.setAccessible(true);
                    fieldSysPath.set(null, null);
                } catch (Exception e) {
                    System.err.println("Failed to set java.library.path: " + e.getMessage());
                }
            }

            // Set LD_LIBRARY_PATH or DYLD_LIBRARY_PATH
            OS os = getOS();
            if (os == OS.LINUX || os == OS.MAC_ARM64 || os == OS.MAC_X86_64) {
                String envVar = (os == OS.LINUX) ? "LD_LIBRARY_PATH" : "DYLD_LIBRARY_PATH";
                String currentPath = System.getenv(envVar);
                String newPath = (currentPath == null) ? pythonLibPath : pythonLibPath + File.pathSeparator + currentPath;
                try {
                    setEnv(envVar, newPath);
                } catch (Exception e) {
                    System.err.println("Failed to set " + envVar + ": " + e.getMessage());
                }
            }

            loadPythonLibrary(pythonLibPath);
        }


        String jepLibraryPath = System.getenv("JEP_LIBRARY_PATH");
        if (jepLibraryPath == null) {
            // Extract all files from jep-distro to temp directory
            String jepDistroPath = "/jep-distro/";
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "jep-plugin-lib");
            tempDir.mkdirs();

            try {
                extractResources(jepDistroPath, tempDir);

                // Set jepLibraryPath to the extracted library
                String libraryName = getJepLibraryName();
                File extractedLibrary = new File(tempDir, "jep/" + libraryName);
                if (!extractedLibrary.exists()) {
                    throw new IllegalStateException("JEP library not found after extraction: " + libraryName);
                }
                jepLibraryPath = extractedLibrary.getAbsolutePath();
            } catch (IOException | URISyntaxException e) {
                throw new IllegalStateException("Failed to extract JEP resources", e);
            }
        }

        // Extract all files from python to temp directory
        String pythonProjectDistroPath = "/python/";
        File pythonProjectTempDir = new File(System.getProperty("java.io.tmpdir"), "cytokinesis-plugin-python");
        pythonProjectTempDir.mkdirs();
        try {
            extractResources(pythonProjectDistroPath, pythonProjectTempDir);
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to extract JEP resources", e);
        }


        try {
            patchJepLibrary(jepLibraryPath, pythonLibPath);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        MainInterpreter.setJepLibraryPath(jepLibraryPath);

//        HACK: Currently not setting python home, but we would likely need to. Currently to circumvent this, the native libraries have to be installed manually into the host machine Python interpreters env
//        PyConfig pyConfig = new PyConfig();
//        String homePath = pythonProjectTempDir.getAbsolutePath() + "/.venv";
//        pyConfig.setPythonHome(homePath);
//        System.out.println("Set Python home path: " + homePath);
//        MainInterpreter.setInitParams(pyConfig);

        JepConfig config = new JepConfig();
        Path libraryPath = Paths.get(jepLibraryPath);
        String jepPackagePath = libraryPath.getParent().getParent().toString();
        config.addIncludePaths(jepPackagePath);
        addPythonIncludePaths(config, pythonProjectTempDir);
//        System.out.println("JEP package path: " + jepPackagePath);
        SharedInterpreter.setConfig(config);

        // Try initializing the interpreter
        try {
            SharedInterpreter interpreter = new SharedInterpreter();
            interpreter.close();
        } catch (Exception e) {
            System.err.println("Error initializing shared Python interpreter: " + e.getMessage());
        }

        return config;
    }

    private static void addPythonIncludePaths(JepConfig config, File tempDir) {
        // Add the tempDir itself
        config.addIncludePaths(tempDir.getAbsolutePath());

        // Find and add the site-packages directory
        Path venvPath = tempDir.toPath().resolve(".venv");
        if (Files.exists(venvPath)) {
            try (Stream<Path> walk = Files.walk(venvPath, 5)) { // Limit depth to avoid excessive searching
                Path sitePackagesPath = walk
                    .filter(p -> p.getFileName().toString().equals("site-packages"))
                    .findFirst()
                    .orElse(null);

                if (sitePackagesPath != null) {
                    config.addIncludePaths(sitePackagesPath.toString());
                } else {
                    System.err.println("Warning: site-packages directory not found in .venv");
                }
            } catch (Exception e) {
                System.err.println("Error while searching for site-packages: " + e.getMessage());
            }
        } else {
            System.err.println("Warning: .venv directory not found in " + tempDir.getAbsolutePath());
        }
    }

    /**
     * On macOS, we have to patch the load path for some reason, as there is a baked-in path to where the libpython is loaded from it seems.
     *
     * @param jepLibraryPath
     * @param pythonLibPath
     * @throws IOException
     * @throws InterruptedException
     */
    private static void patchJepLibrary(String jepLibraryPath, String pythonLibPath) throws IOException, InterruptedException {
        OS os = getOS();
        if (os != OS.MAC_ARM64 && os != OS.MAC_X86_64) {
            // Only proceed with patching for macOS
            return;
        }

        // Construct the full path to the Python library
        String fullPythonLibPath = new File(pythonLibPath, "libpython3.10.dylib").getAbsolutePath();

        // Use install_name_tool to modify the library
        ProcessBuilder pb = new ProcessBuilder(
            "install_name_tool",
            "-change",
            "/Users/morodmi/.pyenv/versions/3.10.13/lib/libpython3.10.dylib",
            fullPythonLibPath,
            jepLibraryPath
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException("install_name_tool failed with exit code: " + exitCode);
        }

        System.out.println("Successfully patched JEP library: " + jepLibraryPath);
    }


    private static void extractResources(String resourcePath, File destDir) throws IOException, URISyntaxException {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        // Get the URL of the resource directory
        URL resourceUrl = JepSetup.class.getResource(resourcePath);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Resource path not found: " + resourcePath);
        }

        // Determine if the resources are in a JAR file or a directory
        if (resourceUrl.getProtocol().equals("jar")) {
            // Extract resources from a JAR file
            extractFromJar(resourceUrl, resourcePath, destDir);
        } else {
            // Extract resources from a file system directory
            extractFromFileSystem(resourceUrl, resourcePath, destDir);
        }
    }

    private static void extractFromJar(URL resourceUrl, String resourcePath, File destDir) throws IOException {
        String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));
        try (JarFile jarFile = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String pathPrefix = resourcePath.substring(1);
                if (entry.getName().startsWith(pathPrefix)) {
                    // System.out.println("Extracting " + entry.getName());
                    // System.out.println("ResourcePath " + resourcePath);
                    File destFile = new File(destDir, entry.getName().substring(pathPrefix.length()));
                    if (entry.isDirectory()) {
                        destFile.mkdirs();
                    } else {
                        InputStream is = jarFile.getInputStream(entry);
                        try (FileOutputStream fos = new FileOutputStream(destFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void extractFromFileSystem(URL resourceUrl, String resourcePath, File destDir) throws IOException, URISyntaxException {
        Path resourceDirectory = Paths.get(resourceUrl.toURI());
        Files.walk(resourceDirectory)
            .forEach(sourcePath -> {
                try {
                    Path destinationPath = destDir.toPath().resolve(resourceDirectory.relativize(sourcePath).toString());
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(destinationPath);
                    } else {
                        Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }


    private static String discoverPythonLibPath(String providedPythonLibPath) {
        if (providedPythonLibPath != null) {
            return providedPythonLibPath;
        }
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"python", "-c",
                "import sysconfig; print(sysconfig.get_config_var('LIBDIR'))"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            return line;
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to discover Python library path: " + e.getMessage());
        }
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"python3", "-c",
                    "import sysconfig; print(sysconfig.get_config_var('LIBDIR'))"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            return line;
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to discover Python library path: " + e.getMessage());
        }
        return null;
    }

    private static String getJepLibraryName() {
        OS os = getOS();
        if (os == null) {
            throw new IllegalStateException("Unsupported operating system");
        }

        switch (os) {
            case WINDOWS:
                return "jep.dll";
            case LINUX:
                return "libjep.so";
            case MAC_ARM64:
                return "libjep.arm64.jnilib";
            case MAC_X86_64:
                return "libjep.x86_64.jnilib";
            default:
                throw new IllegalStateException("Unsupported operating system");
        }
    }

    private static void loadPythonLibrary(String pythonLibPath) {
        OS os = getOS();
        String libName;
        switch (os) {
            case WINDOWS:
                libName = "python3.10.dll";
                break;
            case LINUX:
                libName = "libpython3.10.so";
                break;
            case MAC_ARM64:
            case MAC_X86_64:
                libName = "libpython3.10.dylib";
                break;
            default:
                throw new IllegalStateException("Unsupported operating system");
        }

        String fullPath = new File(pythonLibPath, libName).getAbsolutePath();
        try {
            System.load(fullPath);
            System.out.println("Successfully loaded Python library: " + fullPath);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load Python library: " + fullPath);
            e.printStackTrace();
        }
    }


    private static void setEnv(String key, String value) throws Exception {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> env = (java.util.Map<String, String>) theEnvironmentField.get(null);
            env.put(key, value);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> cienv = (java.util.Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            cienv.put(key, value);
        } catch (NoSuchFieldException e) {
            Class[] classes = Collections.class.getDeclaredClasses();
            java.util.Map<String, String> env = System.getenv();
            for (Class cl : classes) {
                if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(env);
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, String> map = (java.util.Map<String, String>) obj;
                    map.clear();
                    map.putAll(System.getenv());
                    map.put(key, value);
                    break;
                }
            }
        }
    }

}