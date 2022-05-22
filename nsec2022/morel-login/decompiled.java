/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class ClassPathIndexFile {
    private final File root;
    private final List<String> lines;

    private ClassPathIndexFile(File root, List<String> lines) {
        this.root = root;
        this.lines = lines.stream().map(this::extractName).collect(Collectors.toList());
    }

    private String extractName(String line) {
        if (line.startsWith("- \"") && line.endsWith("\"")) {
            return line.substring(3, line.length() - 1);
        }
        throw new IllegalStateException("Malformed classpath index line [" + line + "]");
    }

    int size() {
        return this.lines.size();
    }

    boolean containsEntry(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return this.lines.contains(name);
    }

    List<URL> getUrls() {
        return Collections.unmodifiableList(this.lines.stream().map(this::asUrl).collect(Collectors.toList()));
    }

    private URL asUrl(String line) {
        try {
            return new File(this.root, line).toURI().toURL();
        }
        catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static ClassPathIndexFile loadIfPossible(URL root, String location) throws IOException {
        return ClassPathIndexFile.loadIfPossible(ClassPathIndexFile.asFile(root), location);
    }

    private static ClassPathIndexFile loadIfPossible(File root, String location) throws IOException {
        return ClassPathIndexFile.loadIfPossible(root, new File(root, location));
    }

    private static ClassPathIndexFile loadIfPossible(File root, File indexFile) throws IOException {
        if (indexFile.exists() && indexFile.isFile()) {
            try (FileInputStream inputStream = new FileInputStream(indexFile);){
                ClassPathIndexFile classPathIndexFile = new ClassPathIndexFile(root, ClassPathIndexFile.loadLines(inputStream));
                return classPathIndexFile;
            }
        }
        return null;
    }

    private static List<String> loadLines(InputStream inputStream) throws IOException {
        ArrayList<String> lines = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line = reader.readLine();
        while (line != null) {
            if (!line.trim().isEmpty()) {
                lines.add(line);
            }
            line = reader.readLine();
        }
        return Collections.unmodifiableList(lines);
    }

    private static File asFile(URL url) {
        if (!"file".equals(url.getProtocol())) {
            throw new IllegalArgumentException("URL does not reference a file");
        }
        try {
            return new File(url.toURI());
        }
        catch (URISyntaxException ex) {
            return new File(url.getPath());
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Manifest;
import org.springframework.boot.loader.ClassPathIndexFile;
import org.springframework.boot.loader.Launcher;
import org.springframework.boot.loader.archive.Archive;

public abstract class ExecutableArchiveLauncher
extends Launcher {
    private static final String START_CLASS_ATTRIBUTE = "Start-Class";
    protected static final String BOOT_CLASSPATH_INDEX_ATTRIBUTE = "Spring-Boot-Classpath-Index";
    private final Archive archive;
    private final ClassPathIndexFile classPathIndex;

    public ExecutableArchiveLauncher() {
        try {
            this.archive = this.createArchive();
            this.classPathIndex = this.getClassPathIndex(this.archive);
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected ExecutableArchiveLauncher(Archive archive) {
        try {
            this.archive = archive;
            this.classPathIndex = this.getClassPathIndex(this.archive);
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected ClassPathIndexFile getClassPathIndex(Archive archive) throws IOException {
        return null;
    }

    @Override
    protected String getMainClass() throws Exception {
        Manifest manifest = this.archive.getManifest();
        String mainClass = null;
        if (manifest != null) {
            mainClass = manifest.getMainAttributes().getValue(START_CLASS_ATTRIBUTE);
        }
        if (mainClass == null) {
            throw new IllegalStateException("No 'Start-Class' manifest entry specified in " + this);
        }
        return mainClass;
    }

    @Override
    protected ClassLoader createClassLoader(Iterator<Archive> archives) throws Exception {
        ArrayList<URL> urls = new ArrayList<URL>(this.guessClassPathSize());
        while (archives.hasNext()) {
            urls.add(archives.next().getUrl());
        }
        if (this.classPathIndex != null) {
            urls.addAll(this.classPathIndex.getUrls());
        }
        return this.createClassLoader(urls.toArray(new URL[0]));
    }

    private int guessClassPathSize() {
        if (this.classPathIndex != null) {
            return this.classPathIndex.size() + 10;
        }
        return 50;
    }

    @Override
    protected Iterator<Archive> getClassPathArchivesIterator() throws Exception {
        Archive.EntryFilter searchFilter = this::isSearchCandidate;
        Iterator<Archive> archives = this.archive.getNestedArchives(searchFilter, entry -> this.isNestedArchive(entry) && !this.isEntryIndexed(entry));
        if (this.isPostProcessingClassPathArchives()) {
            archives = this.applyClassPathArchivePostProcessing(archives);
        }
        return archives;
    }

    private boolean isEntryIndexed(Archive.Entry entry) {
        if (this.classPathIndex != null) {
            return this.classPathIndex.containsEntry(entry.getName());
        }
        return false;
    }

    private Iterator<Archive> applyClassPathArchivePostProcessing(Iterator<Archive> archives) throws Exception {
        ArrayList<Archive> list = new ArrayList<Archive>();
        while (archives.hasNext()) {
            list.add(archives.next());
        }
        this.postProcessClassPathArchives(list);
        return list.iterator();
    }

    protected boolean isSearchCandidate(Archive.Entry entry) {
        return true;
    }

    protected abstract boolean isNestedArchive(Archive.Entry var1);

    protected boolean isPostProcessingClassPathArchives() {
        return true;
    }

    protected void postProcessClassPathArchives(List<Archive> archives) throws Exception {
    }

    @Override
    protected boolean isExploded() {
        return this.archive.isExploded();
    }

    @Override
    protected final Archive getArchive() {
        return this.archive;
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader;

import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.springframework.boot.loader.ClassPathIndexFile;
import org.springframework.boot.loader.ExecutableArchiveLauncher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;

public class JarLauncher
extends ExecutableArchiveLauncher {
    private static final String DEFAULT_CLASSPATH_INDEX_LOCATION = "BOOT-INF/classpath.idx";
    static final Archive.EntryFilter NESTED_ARCHIVE_ENTRY_FILTER = entry -> {
        if (entry.isDirectory()) {
            return entry.getName().equals("BOOT-INF/classes/");
        }
        return entry.getName().startsWith("BOOT-INF/lib/");
    };

    public JarLauncher() {
    }

    protected JarLauncher(Archive archive) {
        super(archive);
    }

    @Override
    protected ClassPathIndexFile getClassPathIndex(Archive archive) throws IOException {
        if (archive instanceof ExplodedArchive) {
            String location = this.getClassPathIndexFileLocation(archive);
            return ClassPathIndexFile.loadIfPossible(archive.getUrl(), location);
        }
        return super.getClassPathIndex(archive);
    }

    private String getClassPathIndexFileLocation(Archive archive) throws IOException {
        Manifest manifest = archive.getManifest();
        Attributes attributes = manifest != null ? manifest.getMainAttributes() : null;
        String location = attributes != null ? attributes.getValue("Spring-Boot-Classpath-Index") : null;
        return location != null ? location : DEFAULT_CLASSPATH_INDEX_LOCATION;
    }

    @Override
    protected boolean isPostProcessingClassPathArchives() {
        return false;
    }

    @Override
    protected boolean isSearchCandidate(Archive.Entry entry) {
        return entry.getName().startsWith("BOOT-INF/");
    }

    @Override
    protected boolean isNestedArchive(Archive.Entry entry) {
        return NESTED_ARCHIVE_ENTRY_FILTER.matches(entry);
    }

    public static void main(String[] args) throws Exception {
        new JarLauncher().launch(args);
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.util.Enumeration;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.jar.Handler;
import org.springframework.boot.loader.jar.JarFile;

public class LaunchedURLClassLoader
extends URLClassLoader {
    private static final int BUFFER_SIZE = 4096;
    private final boolean exploded;
    private final Archive rootArchive;
    private final Object packageLock = new Object();
    private volatile DefinePackageCallType definePackageCallType;

    public LaunchedURLClassLoader(URL[] urls, ClassLoader parent) {
        this(false, urls, parent);
    }

    public LaunchedURLClassLoader(boolean exploded, URL[] urls, ClassLoader parent) {
        this(exploded, null, urls, parent);
    }

    public LaunchedURLClassLoader(boolean exploded, Archive rootArchive, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.exploded = exploded;
        this.rootArchive = rootArchive;
    }

    @Override
    public URL findResource(String name) {
        if (this.exploded) {
            return super.findResource(name);
        }
        Handler.setUseFastConnectionExceptions(true);
        try {
            URL uRL = super.findResource(name);
            return uRL;
        }
        finally {
            Handler.setUseFastConnectionExceptions(false);
        }
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        if (this.exploded) {
            return super.findResources(name);
        }
        Handler.setUseFastConnectionExceptions(true);
        try {
            UseFastConnectionExceptionsEnumeration useFastConnectionExceptionsEnumeration = new UseFastConnectionExceptionsEnumeration(super.findResources(name));
            return useFastConnectionExceptionsEnumeration;
        }
        finally {
            Handler.setUseFastConnectionExceptions(false);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("org.springframework.boot.loader.jarmode.")) {
            try {
                Class<?> result = this.loadClassInLaunchedClassLoader(name);
                if (resolve) {
                    this.resolveClass(result);
                }
                return result;
            }
            catch (ClassNotFoundException result) {
                // empty catch block
            }
        }
        if (this.exploded) {
            return super.loadClass(name, resolve);
        }
        Handler.setUseFastConnectionExceptions(true);
        try {
            block10: {
                try {
                    this.definePackageIfNecessary(name);
                }
                catch (IllegalArgumentException ex) {
                    if (this.getPackage(name) != null) break block10;
                    throw new AssertionError((Object)("Package " + name + " has already been defined but it could not be found"));
                }
            }
            Class<?> clazz = super.loadClass(name, resolve);
            return clazz;
        }
        finally {
            Handler.setUseFastConnectionExceptions(false);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Class<?> loadClassInLaunchedClassLoader(String name) throws ClassNotFoundException {
        Class<?> clazz;
        String internalName = name.replace('.', '/') + ".class";
        InputStream inputStream = this.getParent().getResourceAsStream(internalName);
        if (inputStream == null) {
            throw new ClassNotFoundException(name);
        }
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            byte[] bytes = outputStream.toByteArray();
            Class<?> definedClass = this.defineClass(name, bytes, 0, bytes.length);
            this.definePackageIfNecessary(name);
            clazz = definedClass;
        }
        catch (Throwable throwable) {
            try {
                inputStream.close();
                throw throwable;
            }
            catch (IOException ex) {
                throw new ClassNotFoundException("Cannot load resource for class [" + name + "]", ex);
            }
        }
        inputStream.close();
        return clazz;
    }

    private void definePackageIfNecessary(String className) {
        block3: {
            String packageName;
            int lastDot = className.lastIndexOf(46);
            if (lastDot >= 0 && this.getPackage(packageName = className.substring(0, lastDot)) == null) {
                try {
                    this.definePackage(className, packageName);
                }
                catch (IllegalArgumentException ex) {
                    if (this.getPackage(packageName) != null) break block3;
                    throw new AssertionError((Object)("Package " + packageName + " has already been defined but it could not be found"));
                }
            }
        }
    }

    private void definePackage(String className, String packageName) {
        try {
            AccessController.doPrivileged(() -> {
                String packageEntryName = packageName.replace('.', '/') + "/";
                String classEntryName = className.replace('.', '/') + ".class";
                for (URL url : this.getURLs()) {
                    try {
                        java.util.jar.JarFile jarFile;
                        URLConnection connection = url.openConnection();
                        if (!(connection instanceof JarURLConnection) || (jarFile = ((JarURLConnection)connection).getJarFile()).getEntry(classEntryName) == null || jarFile.getEntry(packageEntryName) == null || jarFile.getManifest() == null) continue;
                        this.definePackage(packageName, jarFile.getManifest(), url);
                        return null;
                    }
                    catch (IOException iOException) {
                        // empty catch block
                    }
                }
                return null;
            }, AccessController.getContext());
        }
        catch (PrivilegedActionException privilegedActionException) {
            // empty catch block
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    protected Package definePackage(String name, Manifest man, URL url) throws IllegalArgumentException {
        if (!this.exploded) {
            return super.definePackage(name, man, url);
        }
        Object object = this.packageLock;
        synchronized (object) {
            return this.doDefinePackage(DefinePackageCallType.MANIFEST, () -> super.definePackage(name, man, url));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    protected Package definePackage(String name, String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException {
        if (!this.exploded) {
            return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
        }
        Object object = this.packageLock;
        synchronized (object) {
            Manifest manifest;
            if (this.definePackageCallType == null && (manifest = this.getManifest(this.rootArchive)) != null) {
                return this.definePackage(name, manifest, sealBase);
            }
            return this.doDefinePackage(DefinePackageCallType.ATTRIBUTES, () -> super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase));
        }
    }

    private Manifest getManifest(Archive archive) {
        try {
            return archive != null ? archive.getManifest() : null;
        }
        catch (IOException ex) {
            return null;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private <T> T doDefinePackage(DefinePackageCallType type, Supplier<T> call) {
        DefinePackageCallType existingType = this.definePackageCallType;
        try {
            this.definePackageCallType = type;
            T t = call.get();
            return t;
        }
        finally {
            this.definePackageCallType = existingType;
        }
    }

    public void clearCache() {
        if (this.exploded) {
            return;
        }
        for (URL url : this.getURLs()) {
            try {
                URLConnection connection = url.openConnection();
                if (!(connection instanceof JarURLConnection)) continue;
                this.clearCache(connection);
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
    }

    private void clearCache(URLConnection connection) throws IOException {
        java.util.jar.JarFile jarFile = ((JarURLConnection)connection).getJarFile();
        if (jarFile instanceof JarFile) {
            ((JarFile)jarFile).clearCache();
        }
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private static enum DefinePackageCallType {
        MANIFEST,
        ATTRIBUTES;

    }

    private static class UseFastConnectionExceptionsEnumeration
    implements Enumeration<URL> {
        private final Enumeration<URL> delegate;

        UseFastConnectionExceptionsEnumeration(Enumeration<URL> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasMoreElements() {
            Handler.setUseFastConnectionExceptions(true);
            try {
                boolean bl = this.delegate.hasMoreElements();
                return bl;
            }
            finally {
                Handler.setUseFastConnectionExceptions(false);
            }
        }

        @Override
        public URL nextElement() {
            Handler.setUseFastConnectionExceptions(true);
            try {
                URL uRL = this.delegate.nextElement();
                return uRL;
            }
            finally {
                Handler.setUseFastConnectionExceptions(false);
            }
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.MainMethodRunner;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.jar.JarFile;

public abstract class Launcher {
    private static final String JAR_MODE_LAUNCHER = "org.springframework.boot.loader.jarmode.JarModeLauncher";

    protected void launch(String[] args) throws Exception {
        if (!this.isExploded()) {
            JarFile.registerUrlProtocolHandler();
        }
        ClassLoader classLoader = this.createClassLoader(this.getClassPathArchivesIterator());
        String jarMode = System.getProperty("jarmode");
        String launchClass = jarMode != null && !jarMode.isEmpty() ? JAR_MODE_LAUNCHER : this.getMainClass();
        this.launch(args, launchClass, classLoader);
    }

    @Deprecated
    protected ClassLoader createClassLoader(List<Archive> archives) throws Exception {
        return this.createClassLoader(archives.iterator());
    }

    protected ClassLoader createClassLoader(Iterator<Archive> archives) throws Exception {
        ArrayList<URL> urls = new ArrayList<URL>(50);
        while (archives.hasNext()) {
            urls.add(archives.next().getUrl());
        }
        return this.createClassLoader(urls.toArray(new URL[0]));
    }

    protected ClassLoader createClassLoader(URL[] urls) throws Exception {
        return new LaunchedURLClassLoader(this.isExploded(), this.getArchive(), urls, this.getClass().getClassLoader());
    }

    protected void launch(String[] args, String launchClass, ClassLoader classLoader) throws Exception {
        Thread.currentThread().setContextClassLoader(classLoader);
        this.createMainMethodRunner(launchClass, args, classLoader).run();
    }

    protected MainMethodRunner createMainMethodRunner(String mainClass, String[] args, ClassLoader classLoader) {
        return new MainMethodRunner(mainClass, args);
    }

    protected abstract String getMainClass() throws Exception;

    protected Iterator<Archive> getClassPathArchivesIterator() throws Exception {
        return this.getClassPathArchives().iterator();
    }

    @Deprecated
    protected List<Archive> getClassPathArchives() throws Exception {
        throw new IllegalStateException("Unexpected call to getClassPathArchives()");
    }

    protected final Archive createArchive() throws Exception {
        String path;
        ProtectionDomain protectionDomain = this.getClass().getProtectionDomain();
        CodeSource codeSource = protectionDomain.getCodeSource();
        URI location = codeSource != null ? codeSource.getLocation().toURI() : null;
        String string = path = location != null ? location.getSchemeSpecificPart() : null;
        if (path == null) {
            throw new IllegalStateException("Unable to determine code source archive");
        }
        File root = new File(path);
        if (!root.exists()) {
            throw new IllegalStateException("Unable to determine code source archive from " + root);
        }
        return root.isDirectory() ? new ExplodedArchive(root) : new JarFileArchive(root);
    }

    protected boolean isExploded() {
        return false;
    }

    protected Archive getArchive() {
        return null;
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader;

import java.lang.reflect.Method;

public class MainMethodRunner {
    private final String mainClassName;
    private final String[] args;

    public MainMethodRunner(String mainClass, String[] args) {
        this.mainClassName = mainClass;
        this.args = args != null ? (String[])args.clone() : null;
    }

    public void run() throws Exception {
        Class<?> mainClass = Class.forName(this.mainClassName, false, Thread.currentThread().getContextClassLoader());
        Method mainMethod = mainClass.getDeclaredMethod("main", String[].class);
        mainMethod.setAccessible(true);
        mainMethod.invoke(null, new Object[]{this.args});
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.Launcher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.util.SystemPropertyUtils;

public class PropertiesLauncher
extends Launcher {
    private static final Class<?>[] PARENT_ONLY_PARAMS = new Class[]{ClassLoader.class};
    private static final Class<?>[] URLS_AND_PARENT_PARAMS = new Class[]{URL[].class, ClassLoader.class};
    private static final Class<?>[] NO_PARAMS = new Class[0];
    private static final URL[] NO_URLS = new URL[0];
    private static final String DEBUG = "loader.debug";
    public static final String MAIN = "loader.main";
    public static final String PATH = "loader.path";
    public static final String HOME = "loader.home";
    public static final String ARGS = "loader.args";
    public static final String CONFIG_NAME = "loader.config.name";
    public static final String CONFIG_LOCATION = "loader.config.location";
    public static final String SET_SYSTEM_PROPERTIES = "loader.system";
    private static final Pattern WORD_SEPARATOR = Pattern.compile("\\W+");
    private static final String NESTED_ARCHIVE_SEPARATOR = "!" + File.separator;
    private final File home;
    private List<String> paths = new ArrayList<String>();
    private final Properties properties = new Properties();
    private final Archive parent;
    private volatile ClassPathArchives classPathArchives;

    public PropertiesLauncher() {
        try {
            this.home = this.getHomeDirectory();
            this.initializeProperties();
            this.initializePaths();
            this.parent = this.createArchive();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected File getHomeDirectory() {
        try {
            return new File(this.getPropertyWithDefault(HOME, "${user.dir}"));
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void initializeProperties() throws Exception {
        ArrayList<String> configs = new ArrayList<String>();
        if (this.getProperty(CONFIG_LOCATION) != null) {
            configs.add(this.getProperty(CONFIG_LOCATION));
        } else {
            String[] names;
            for (String name : names = this.getPropertyWithDefault(CONFIG_NAME, "loader").split(",")) {
                configs.add("file:" + this.getHomeDirectory() + "/" + name + ".properties");
                configs.add("classpath:" + name + ".properties");
                configs.add("classpath:BOOT-INF/classes/" + name + ".properties");
            }
        }
        for (String config : configs) {
            InputStream resource = this.getResource(config);
            Throwable throwable = null;
            try {
                if (resource != null) {
                    this.debug("Found: " + config);
                    this.loadResource(resource);
                    return;
                }
                this.debug("Not found: " + config);
            }
            catch (Throwable throwable2) {
                throwable = throwable2;
                throw throwable2;
            }
            finally {
                if (resource == null) continue;
                if (throwable != null) {
                    try {
                        resource.close();
                    }
                    catch (Throwable throwable3) {
                        throwable.addSuppressed(throwable3);
                    }
                    continue;
                }
                resource.close();
            }
        }
    }

    private void loadResource(InputStream resource) throws Exception {
        this.properties.load(resource);
        for (Object key : Collections.list(this.properties.propertyNames())) {
            String text = this.properties.getProperty((String)key);
            String value = SystemPropertyUtils.resolvePlaceholders(this.properties, text);
            if (value == null) continue;
            this.properties.put(key, value);
        }
        if ("true".equals(this.getProperty(SET_SYSTEM_PROPERTIES))) {
            this.debug("Adding resolved properties to System properties");
            for (Object key : Collections.list(this.properties.propertyNames())) {
                String value = this.properties.getProperty((String)key);
                System.setProperty((String)key, value);
            }
        }
    }

    private InputStream getResource(String config) throws Exception {
        if (config.startsWith("classpath:")) {
            return this.getClasspathResource(config.substring("classpath:".length()));
        }
        if (this.isUrl(config = this.handleUrl(config))) {
            return this.getURLResource(config);
        }
        return this.getFileResource(config);
    }

    private String handleUrl(String path) throws UnsupportedEncodingException {
        if ((path.startsWith("jar:file:") || path.startsWith("file:")) && (path = URLDecoder.decode(path, "UTF-8")).startsWith("file:") && (path = path.substring("file:".length())).startsWith("//")) {
            path = path.substring(2);
        }
        return path;
    }

    private boolean isUrl(String config) {
        return config.contains("://");
    }

    private InputStream getClasspathResource(String config) {
        while (config.startsWith("/")) {
            config = config.substring(1);
        }
        config = "/" + config;
        this.debug("Trying classpath: " + config);
        return this.getClass().getResourceAsStream(config);
    }

    private InputStream getFileResource(String config) throws Exception {
        File file = new File(config);
        this.debug("Trying file: " + config);
        if (file.canRead()) {
            return new FileInputStream(file);
        }
        return null;
    }

    private InputStream getURLResource(String config) throws Exception {
        URL url = new URL(config);
        if (this.exists(url)) {
            URLConnection con = url.openConnection();
            try {
                return con.getInputStream();
            }
            catch (IOException ex) {
                if (con instanceof HttpURLConnection) {
                    ((HttpURLConnection)con).disconnect();
                }
                throw ex;
            }
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean exists(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        try {
            connection.setUseCaches(connection.getClass().getSimpleName().startsWith("JNLP"));
            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection)connection;
                httpConnection.setRequestMethod("HEAD");
                int responseCode = httpConnection.getResponseCode();
                if (responseCode == 200) {
                    boolean bl = true;
                    return bl;
                }
                if (responseCode == 404) {
                    boolean bl = false;
                    return bl;
                }
            }
            boolean bl = connection.getContentLength() >= 0;
            return bl;
        }
        finally {
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection)connection).disconnect();
            }
        }
    }

    private void initializePaths() throws Exception {
        String path = this.getProperty(PATH);
        if (path != null) {
            this.paths = this.parsePathsProperty(path);
        }
        this.debug("Nested archive paths: " + this.paths);
    }

    private List<String> parsePathsProperty(String commaSeparatedPaths) {
        ArrayList<String> paths = new ArrayList<String>();
        for (String path : commaSeparatedPaths.split(",")) {
            path = (path = this.cleanupPath(path)) == null || path.isEmpty() ? "/" : path;
            paths.add(path);
        }
        if (paths.isEmpty()) {
            paths.add("lib");
        }
        return paths;
    }

    protected String[] getArgs(String ... args) throws Exception {
        String loaderArgs = this.getProperty(ARGS);
        if (loaderArgs != null) {
            String[] defaultArgs = loaderArgs.split("\\s+");
            String[] additionalArgs = args;
            args = new String[defaultArgs.length + additionalArgs.length];
            System.arraycopy(defaultArgs, 0, args, 0, defaultArgs.length);
            System.arraycopy(additionalArgs, 0, args, defaultArgs.length, additionalArgs.length);
        }
        return args;
    }

    @Override
    protected String getMainClass() throws Exception {
        String mainClass = this.getProperty(MAIN, "Start-Class");
        if (mainClass == null) {
            throw new IllegalStateException("No 'loader.main' or 'Start-Class' specified");
        }
        return mainClass;
    }

    @Override
    protected ClassLoader createClassLoader(Iterator<Archive> archives) throws Exception {
        String customLoaderClassName = this.getProperty("loader.classLoader");
        if (customLoaderClassName == null) {
            return super.createClassLoader(archives);
        }
        LinkedHashSet<URL> urls = new LinkedHashSet<URL>();
        while (archives.hasNext()) {
            urls.add(archives.next().getUrl());
        }
        ClassLoader loader = new LaunchedURLClassLoader(urls.toArray(NO_URLS), this.getClass().getClassLoader());
        this.debug("Classpath for custom loader: " + urls);
        loader = this.wrapWithCustomClassLoader(loader, customLoaderClassName);
        this.debug("Using custom class loader: " + customLoaderClassName);
        return loader;
    }

    private ClassLoader wrapWithCustomClassLoader(ClassLoader parent, String className) throws Exception {
        Class<ClassLoader> type = Class.forName(className, true, parent);
        ClassLoader classLoader = this.newClassLoader(type, PARENT_ONLY_PARAMS, parent);
        if (classLoader == null) {
            classLoader = this.newClassLoader(type, URLS_AND_PARENT_PARAMS, NO_URLS, parent);
        }
        if (classLoader == null) {
            classLoader = this.newClassLoader(type, NO_PARAMS, new Object[0]);
        }
        if (classLoader == null) {
            throw new IllegalArgumentException("Unable to create class loader for " + className);
        }
        return classLoader;
    }

    private ClassLoader newClassLoader(Class<ClassLoader> loaderClass, Class<?>[] parameterTypes, Object ... initargs) throws Exception {
        try {
            Constructor<ClassLoader> constructor = loaderClass.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(initargs);
        }
        catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private String getProperty(String propertyKey) throws Exception {
        return this.getProperty(propertyKey, null, null);
    }

    private String getProperty(String propertyKey, String manifestKey) throws Exception {
        return this.getProperty(propertyKey, manifestKey, null);
    }

    private String getPropertyWithDefault(String propertyKey, String defaultValue) throws Exception {
        return this.getProperty(propertyKey, null, defaultValue);
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private String getProperty(String propertyKey, String manifestKey, String defaultValue) throws Exception {
        String string;
        String value;
        block22: {
            String property;
            if (manifestKey == null) {
                manifestKey = propertyKey.replace('.', '-');
                manifestKey = PropertiesLauncher.toCamelCase(manifestKey);
            }
            if ((property = SystemPropertyUtils.getProperty(propertyKey)) != null) {
                String value3 = SystemPropertyUtils.resolvePlaceholders(this.properties, property);
                this.debug("Property '" + propertyKey + "' from environment: " + value3);
                return value3;
            }
            if (this.properties.containsKey(propertyKey)) {
                String value4 = SystemPropertyUtils.resolvePlaceholders(this.properties, this.properties.getProperty(propertyKey));
                this.debug("Property '" + propertyKey + "' from properties: " + value4);
                return value4;
            }
            try {
                if (this.home == null) break block22;
                try (ExplodedArchive archive222 = new ExplodedArchive(this.home, false);){
                    String value2;
                    Manifest manifest2 = archive222.getManifest();
                    if (manifest2 != null && (value2 = manifest2.getMainAttributes().getValue(manifestKey)) != null) {
                        this.debug("Property '" + manifestKey + "' from home directory manifest: " + value2);
                        String string2 = SystemPropertyUtils.resolvePlaceholders(this.properties, value2);
                        return string2;
                    }
                }
            }
            catch (IllegalStateException archive222) {
                // empty catch block
            }
        }
        Manifest manifest = this.createArchive().getManifest();
        if (manifest != null && (value = manifest.getMainAttributes().getValue(manifestKey)) != null) {
            this.debug("Property '" + manifestKey + "' from archive manifest: " + value);
            return SystemPropertyUtils.resolvePlaceholders(this.properties, value);
        }
        if (defaultValue != null) {
            string = SystemPropertyUtils.resolvePlaceholders(this.properties, defaultValue);
            return string;
        }
        string = defaultValue;
        return string;
    }

    @Override
    protected Iterator<Archive> getClassPathArchivesIterator() throws Exception {
        ClassPathArchives classPathArchives = this.classPathArchives;
        if (classPathArchives == null) {
            this.classPathArchives = classPathArchives = new ClassPathArchives();
        }
        return classPathArchives.iterator();
    }

    public static void main(String[] args) throws Exception {
        PropertiesLauncher launcher = new PropertiesLauncher();
        args = launcher.getArgs(args);
        launcher.launch(args);
    }

    public static String toCamelCase(CharSequence string) {
        if (string == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        Matcher matcher = WORD_SEPARATOR.matcher(string);
        int pos = 0;
        while (matcher.find()) {
            builder.append(PropertiesLauncher.capitalize(string.subSequence(pos, matcher.end()).toString()));
            pos = matcher.end();
        }
        builder.append(PropertiesLauncher.capitalize(string.subSequence(pos, string.length()).toString()));
        return builder.toString();
    }

    private static String capitalize(String str) {
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private void debug(String message) {
        if (Boolean.getBoolean(DEBUG)) {
            System.out.println(message);
        }
    }

    private String cleanupPath(String path) {
        String lowerCasePath;
        if ((path = path.trim()).startsWith("./")) {
            path = path.substring(2);
        }
        if ((lowerCasePath = path.toLowerCase(Locale.ENGLISH)).endsWith(".jar") || lowerCasePath.endsWith(".zip")) {
            return path;
        }
        if (path.endsWith("/*")) {
            path = path.substring(0, path.length() - 1);
        } else if (!path.endsWith("/") && !path.equals(".")) {
            path = path + "/";
        }
        return path;
    }

    void close() throws Exception {
        if (this.classPathArchives != null) {
            this.classPathArchives.close();
        }
        if (this.parent != null) {
            this.parent.close();
        }
    }

    private static final class ArchiveEntryFilter
    implements Archive.EntryFilter {
        private static final String DOT_JAR = ".jar";
        private static final String DOT_ZIP = ".zip";

        private ArchiveEntryFilter() {
        }

        @Override
        public boolean matches(Archive.Entry entry) {
            return entry.getName().endsWith(DOT_JAR) || entry.getName().endsWith(DOT_ZIP);
        }
    }

    private static final class PrefixMatchingArchiveFilter
    implements Archive.EntryFilter {
        private final String prefix;
        private final ArchiveEntryFilter filter = new ArchiveEntryFilter();

        private PrefixMatchingArchiveFilter(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean matches(Archive.Entry entry) {
            if (entry.isDirectory()) {
                return entry.getName().equals(this.prefix);
            }
            return entry.getName().startsWith(this.prefix) && this.filter.matches(entry);
        }
    }

    private class ClassPathArchives
    implements Iterable<Archive> {
        private final List<Archive> classPathArchives;
        private final List<JarFileArchive> jarFileArchives = new ArrayList<JarFileArchive>();

        ClassPathArchives() throws Exception {
            this.classPathArchives = new ArrayList<Archive>();
            for (String path : PropertiesLauncher.this.paths) {
                for (Archive archive : this.getClassPathArchives(path)) {
                    this.addClassPathArchive(archive);
                }
            }
            this.addNestedEntries();
        }

        private void addClassPathArchive(Archive archive) throws IOException {
            if (!(archive instanceof ExplodedArchive)) {
                this.classPathArchives.add(archive);
                return;
            }
            this.classPathArchives.add(archive);
            this.classPathArchives.addAll(this.asList(archive.getNestedArchives(null, new ArchiveEntryFilter())));
        }

        private List<Archive> getClassPathArchives(String path) throws Exception {
            List<Archive> nestedArchives;
            Archive archive;
            String root = PropertiesLauncher.this.cleanupPath(PropertiesLauncher.this.handleUrl(path));
            ArrayList<Archive> lib = new ArrayList<Archive>();
            File file = new File(root);
            if (!"/".equals(root)) {
                if (!this.isAbsolutePath(root)) {
                    file = new File(PropertiesLauncher.this.home, root);
                }
                if (file.isDirectory()) {
                    PropertiesLauncher.this.debug("Adding classpath entries from " + file);
                    archive = new ExplodedArchive(file, false);
                    lib.add(archive);
                }
            }
            if ((archive = this.getArchive(file)) != null) {
                PropertiesLauncher.this.debug("Adding classpath entries from archive " + archive.getUrl() + root);
                lib.add(archive);
            }
            if ((nestedArchives = this.getNestedArchives(root)) != null) {
                PropertiesLauncher.this.debug("Adding classpath entries from nested " + root);
                lib.addAll(nestedArchives);
            }
            return lib;
        }

        private boolean isAbsolutePath(String root) {
            return root.contains(":") || root.startsWith("/");
        }

        private Archive getArchive(File file) throws IOException {
            if (this.isNestedArchivePath(file)) {
                return null;
            }
            String name = file.getName().toLowerCase(Locale.ENGLISH);
            if (name.endsWith(".jar") || name.endsWith(".zip")) {
                return this.getJarFileArchive(file);
            }
            return null;
        }

        private boolean isNestedArchivePath(File file) {
            return file.getPath().contains(NESTED_ARCHIVE_SEPARATOR);
        }

        private List<Archive> getNestedArchives(String path) throws Exception {
            File file;
            Archive parent = PropertiesLauncher.this.parent;
            String root = path;
            if (!root.equals("/") && root.startsWith("/") || parent.getUrl().toURI().equals(PropertiesLauncher.this.home.toURI())) {
                return null;
            }
            int index = root.indexOf(33);
            if (index != -1) {
                file = new File(PropertiesLauncher.this.home, root.substring(0, index));
                if (root.startsWith("jar:file:")) {
                    file = new File(root.substring("jar:file:".length(), index));
                }
                parent = this.getJarFileArchive(file);
                root = root.substring(index + 1);
                while (root.startsWith("/")) {
                    root = root.substring(1);
                }
            }
            if (root.endsWith(".jar") && (file = new File(PropertiesLauncher.this.home, root)).exists()) {
                parent = this.getJarFileArchive(file);
                root = "";
            }
            if (root.equals("/") || root.equals("./") || root.equals(".")) {
                root = "";
            }
            PrefixMatchingArchiveFilter filter = new PrefixMatchingArchiveFilter(root);
            List<Archive> archives = this.asList(parent.getNestedArchives(null, filter));
            if ((root == null || root.isEmpty() || ".".equals(root)) && !path.endsWith(".jar") && parent != PropertiesLauncher.this.parent) {
                archives.add(parent);
            }
            return archives;
        }

        private void addNestedEntries() {
            try {
                Iterator<Archive> archives = PropertiesLauncher.this.parent.getNestedArchives(null, JarLauncher.NESTED_ARCHIVE_ENTRY_FILTER);
                while (archives.hasNext()) {
                    this.classPathArchives.add(archives.next());
                }
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }

        private List<Archive> asList(Iterator<Archive> iterator) {
            ArrayList<Archive> list = new ArrayList<Archive>();
            while (iterator.hasNext()) {
                list.add(iterator.next());
            }
            return list;
        }

        private JarFileArchive getJarFileArchive(File file) throws IOException {
            JarFileArchive archive = new JarFileArchive(file);
            this.jarFileArchives.add(archive);
            return archive;
        }

        @Override
        public Iterator<Archive> iterator() {
            return this.classPathArchives.iterator();
        }

        void close() throws IOException {
            for (JarFileArchive archive : this.jarFileArchives) {
                archive.close();
            }
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader;

import org.springframework.boot.loader.ExecutableArchiveLauncher;
import org.springframework.boot.loader.archive.Archive;

public class WarLauncher
extends ExecutableArchiveLauncher {
    public WarLauncher() {
    }

    protected WarLauncher(Archive archive) {
        super(archive);
    }

    @Override
    protected boolean isPostProcessingClassPathArchives() {
        return false;
    }

    @Override
    protected boolean isSearchCandidate(Archive.Entry entry) {
        return entry.getName().startsWith("WEB-INF/");
    }

    @Override
    public boolean isNestedArchive(Archive.Entry entry) {
        if (entry.isDirectory()) {
            return entry.getName().equals("WEB-INF/classes/");
        }
        return entry.getName().startsWith("WEB-INF/lib/") || entry.getName().startsWith("WEB-INF/lib-provided/");
    }

    public static void main(String[] args) throws Exception {
        new WarLauncher().launch(args);
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.archive;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.jar.Manifest;

public interface Archive
extends Iterable<Entry>,
AutoCloseable {
    public URL getUrl() throws MalformedURLException;

    public Manifest getManifest() throws IOException;

    default public Iterator<Archive> getNestedArchives(EntryFilter searchFilter, EntryFilter includeFilter) throws IOException {
        EntryFilter combinedFilter = entry -> !(searchFilter != null && !searchFilter.matches(entry) || includeFilter != null && !includeFilter.matches(entry));
        List<Archive> nestedArchives = this.getNestedArchives(combinedFilter);
        return nestedArchives.iterator();
    }

    @Deprecated
    default public List<Archive> getNestedArchives(EntryFilter filter) throws IOException {
        throw new IllegalStateException("Unexpected call to getNestedArchives(filter)");
    }

    @Override
    @Deprecated
    public Iterator<Entry> iterator();

    @Override
    @Deprecated
    default public void forEach(Consumer<? super Entry> action) {
        Objects.requireNonNull(action);
        for (Entry entry : this) {
            action.accept(entry);
        }
    }

    @Override
    @Deprecated
    default public Spliterator<Entry> spliterator() {
        return Spliterators.spliteratorUnknownSize(this.iterator(), 0);
    }

    default public boolean isExploded() {
        return false;
    }

    @Override
    default public void close() throws Exception {
    }

    @FunctionalInterface
    public static interface EntryFilter {
        public boolean matches(Entry var1);
    }

    public static interface Entry {
        public boolean isDirectory();

        public String getName();
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.archive;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.jar.Manifest;
import org.springframework.boot.loader.archive.Archive;

public class ExplodedArchive
implements Archive {
    private static final Set<String> SKIPPED_NAMES = new HashSet<String>(Arrays.asList(".", ".."));
    private final File root;
    private final boolean recursive;
    private File manifestFile;
    private Manifest manifest;

    public ExplodedArchive(File root) {
        this(root, true);
    }

    public ExplodedArchive(File root, boolean recursive) {
        if (!root.exists() || !root.isDirectory()) {
            throw new IllegalArgumentException("Invalid source directory " + root);
        }
        this.root = root;
        this.recursive = recursive;
        this.manifestFile = this.getManifestFile(root);
    }

    private File getManifestFile(File root) {
        File metaInf = new File(root, "META-INF");
        return new File(metaInf, "MANIFEST.MF");
    }

    @Override
    public URL getUrl() throws MalformedURLException {
        return this.root.toURI().toURL();
    }

    @Override
    public Manifest getManifest() throws IOException {
        if (this.manifest == null && this.manifestFile.exists()) {
            try (FileInputStream inputStream = new FileInputStream(this.manifestFile);){
                this.manifest = new Manifest(inputStream);
            }
        }
        return this.manifest;
    }

    @Override
    public Iterator<Archive> getNestedArchives(Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) throws IOException {
        return new ArchiveIterator(this.root, this.recursive, searchFilter, includeFilter);
    }

    @Override
    @Deprecated
    public Iterator<Archive.Entry> iterator() {
        return new EntryIterator(this.root, this.recursive, null, null);
    }

    protected Archive getNestedArchive(Archive.Entry entry) throws IOException {
        File file = ((FileEntry)entry).getFile();
        return file.isDirectory() ? new ExplodedArchive(file) : new SimpleJarFileArchive((FileEntry)entry);
    }

    @Override
    public boolean isExploded() {
        return true;
    }

    public String toString() {
        try {
            return this.getUrl().toString();
        }
        catch (Exception ex) {
            return "exploded archive";
        }
    }

    private static class SimpleJarFileArchive
    implements Archive {
        private final URL url;

        SimpleJarFileArchive(FileEntry file) {
            this.url = file.getUrl();
        }

        @Override
        public URL getUrl() throws MalformedURLException {
            return this.url;
        }

        @Override
        public Manifest getManifest() throws IOException {
            return null;
        }

        @Override
        public Iterator<Archive> getNestedArchives(Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) throws IOException {
            return Collections.emptyIterator();
        }

        @Override
        @Deprecated
        public Iterator<Archive.Entry> iterator() {
            return Collections.emptyIterator();
        }

        public String toString() {
            try {
                return this.getUrl().toString();
            }
            catch (Exception ex) {
                return "jar archive";
            }
        }
    }

    private static class FileEntry
    implements Archive.Entry {
        private final String name;
        private final File file;
        private final URL url;

        FileEntry(String name, File file, URL url) {
            this.name = name;
            this.file = file;
            this.url = url;
        }

        File getFile() {
            return this.file;
        }

        @Override
        public boolean isDirectory() {
            return this.file.isDirectory();
        }

        @Override
        public String getName() {
            return this.name;
        }

        URL getUrl() {
            return this.url;
        }
    }

    private static class ArchiveIterator
    extends AbstractIterator<Archive> {
        ArchiveIterator(File root, boolean recursive, Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) {
            super(root, recursive, searchFilter, includeFilter);
        }

        @Override
        protected Archive adapt(FileEntry entry) {
            File file = entry.getFile();
            return file.isDirectory() ? new ExplodedArchive(file) : new SimpleJarFileArchive(entry);
        }
    }

    private static class EntryIterator
    extends AbstractIterator<Archive.Entry> {
        EntryIterator(File root, boolean recursive, Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) {
            super(root, recursive, searchFilter, includeFilter);
        }

        @Override
        protected Archive.Entry adapt(FileEntry entry) {
            return entry;
        }
    }

    private static abstract class AbstractIterator<T>
    implements Iterator<T> {
        private static final Comparator<File> entryComparator = Comparator.comparing(File::getAbsolutePath);
        private final File root;
        private final boolean recursive;
        private final Archive.EntryFilter searchFilter;
        private final Archive.EntryFilter includeFilter;
        private final Deque<Iterator<File>> stack = new LinkedList<Iterator<File>>();
        private FileEntry current;
        private String rootUrl;

        AbstractIterator(File root, boolean recursive, Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) {
            this.root = root;
            this.rootUrl = this.root.toURI().getPath();
            this.recursive = recursive;
            this.searchFilter = searchFilter;
            this.includeFilter = includeFilter;
            this.stack.add(this.listFiles(root));
            this.current = this.poll();
        }

        @Override
        public boolean hasNext() {
            return this.current != null;
        }

        @Override
        public T next() {
            FileEntry entry = this.current;
            if (entry == null) {
                throw new NoSuchElementException();
            }
            this.current = this.poll();
            return this.adapt(entry);
        }

        private FileEntry poll() {
            while (!this.stack.isEmpty()) {
                while (this.stack.peek().hasNext()) {
                    File file = this.stack.peek().next();
                    if (SKIPPED_NAMES.contains(file.getName())) continue;
                    FileEntry entry = this.getFileEntry(file);
                    if (this.isListable(entry)) {
                        this.stack.addFirst(this.listFiles(file));
                    }
                    if (this.includeFilter != null && !this.includeFilter.matches(entry)) continue;
                    return entry;
                }
                this.stack.poll();
            }
            return null;
        }

        private FileEntry getFileEntry(File file) {
            URI uri = file.toURI();
            String name = uri.getPath().substring(this.rootUrl.length());
            try {
                return new FileEntry(name, file, uri.toURL());
            }
            catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private boolean isListable(FileEntry entry) {
            return !(!entry.isDirectory() || !this.recursive && !entry.getFile().getParentFile().equals(this.root) || this.searchFilter != null && !this.searchFilter.matches(entry) || this.includeFilter != null && this.includeFilter.matches(entry));
        }

        private Iterator<File> listFiles(File file) {
            File[] files = file.listFiles();
            if (files == null) {
                return Collections.emptyIterator();
            }
            Arrays.sort(files, entryComparator);
            return Arrays.asList(files).iterator();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }

        protected abstract T adapt(FileEntry var1);
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.archive;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.jar.JarFile;

public class JarFileArchive
implements Archive {
    private static final String UNPACK_MARKER = "UNPACK:";
    private static final int BUFFER_SIZE = 32768;
    private static final FileAttribute<?>[] NO_FILE_ATTRIBUTES = new FileAttribute[0];
    private static final EnumSet<PosixFilePermission> DIRECTORY_PERMISSIONS = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final EnumSet<PosixFilePermission> FILE_PERMISSIONS = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private final JarFile jarFile;
    private URL url;
    private Path tempUnpackDirectory;

    public JarFileArchive(File file) throws IOException {
        this(file, file.toURI().toURL());
    }

    public JarFileArchive(File file, URL url) throws IOException {
        this(new JarFile(file));
        this.url = url;
    }

    public JarFileArchive(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    @Override
    public URL getUrl() throws MalformedURLException {
        if (this.url != null) {
            return this.url;
        }
        return this.jarFile.getUrl();
    }

    @Override
    public Manifest getManifest() throws IOException {
        return this.jarFile.getManifest();
    }

    @Override
    public Iterator<Archive> getNestedArchives(Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) throws IOException {
        return new NestedArchiveIterator(this.jarFile.iterator(), searchFilter, includeFilter);
    }

    @Override
    @Deprecated
    public Iterator<Archive.Entry> iterator() {
        return new EntryIterator(this.jarFile.iterator(), null, null);
    }

    @Override
    public void close() throws IOException {
        this.jarFile.close();
    }

    protected Archive getNestedArchive(Archive.Entry entry) throws IOException {
        JarEntry jarEntry = ((JarFileEntry)entry).getJarEntry();
        if (jarEntry.getComment().startsWith(UNPACK_MARKER)) {
            return this.getUnpackedNestedArchive(jarEntry);
        }
        try {
            JarFile jarFile = this.jarFile.getNestedJarFile(jarEntry);
            return new JarFileArchive(jarFile);
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to get nested archive for entry " + entry.getName(), ex);
        }
    }

    private Archive getUnpackedNestedArchive(JarEntry jarEntry) throws IOException {
        Path path;
        String name = jarEntry.getName();
        if (name.lastIndexOf(47) != -1) {
            name = name.substring(name.lastIndexOf(47) + 1);
        }
        if (!Files.exists(path = this.getTempUnpackDirectory().resolve(name), new LinkOption[0]) || Files.size(path) != jarEntry.getSize()) {
            this.unpack(jarEntry, path);
        }
        return new JarFileArchive(path.toFile(), path.toUri().toURL());
    }

    private Path getTempUnpackDirectory() {
        if (this.tempUnpackDirectory == null) {
            Path tempDirectory = Paths.get(System.getProperty("java.io.tmpdir"), new String[0]);
            this.tempUnpackDirectory = this.createUnpackDirectory(tempDirectory);
        }
        return this.tempUnpackDirectory;
    }

    private Path createUnpackDirectory(Path parent) {
        int attempts = 0;
        while (attempts++ < 1000) {
            String fileName = Paths.get(this.jarFile.getName(), new String[0]).getFileName().toString();
            Path unpackDirectory = parent.resolve(fileName + "-spring-boot-libs-" + UUID.randomUUID());
            try {
                this.createDirectory(unpackDirectory);
                return unpackDirectory;
            }
            catch (IOException iOException) {
            }
        }
        throw new IllegalStateException("Failed to create unpack directory in directory '" + parent + "'");
    }

    private void unpack(JarEntry entry, Path path) throws IOException {
        this.createFile(path);
        path.toFile().deleteOnExit();
        try (InputStream inputStream = this.jarFile.getInputStream(entry);
             OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);){
            int bytesRead;
            byte[] buffer = new byte[32768];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    private void createDirectory(Path path) throws IOException {
        Files.createDirectory(path, this.getFileAttributes(path.getFileSystem(), DIRECTORY_PERMISSIONS));
    }

    private void createFile(Path path) throws IOException {
        Files.createFile(path, this.getFileAttributes(path.getFileSystem(), FILE_PERMISSIONS));
    }

    private FileAttribute<?>[] getFileAttributes(FileSystem fileSystem, EnumSet<PosixFilePermission> ownerReadWrite) {
        if (!fileSystem.supportedFileAttributeViews().contains("posix")) {
            return NO_FILE_ATTRIBUTES;
        }
        return new FileAttribute[]{PosixFilePermissions.asFileAttribute(ownerReadWrite)};
    }

    public String toString() {
        try {
            return this.getUrl().toString();
        }
        catch (Exception ex) {
            return "jar archive";
        }
    }

    private static class JarFileEntry
    implements Archive.Entry {
        private final JarEntry jarEntry;

        JarFileEntry(JarEntry jarEntry) {
            this.jarEntry = jarEntry;
        }

        JarEntry getJarEntry() {
            return this.jarEntry;
        }

        @Override
        public boolean isDirectory() {
            return this.jarEntry.isDirectory();
        }

        @Override
        public String getName() {
            return this.jarEntry.getName();
        }
    }

    private class NestedArchiveIterator
    extends AbstractIterator<Archive> {
        NestedArchiveIterator(Iterator<JarEntry> iterator, Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) {
            super(iterator, searchFilter, includeFilter);
        }

        @Override
        protected Archive adapt(Archive.Entry entry) {
            try {
                return JarFileArchive.this.getNestedArchive(entry);
            }
            catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static class EntryIterator
    extends AbstractIterator<Archive.Entry> {
        EntryIterator(Iterator<JarEntry> iterator, Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) {
            super(iterator, searchFilter, includeFilter);
        }

        @Override
        protected Archive.Entry adapt(Archive.Entry entry) {
            return entry;
        }
    }

    private static abstract class AbstractIterator<T>
    implements Iterator<T> {
        private final Iterator<JarEntry> iterator;
        private final Archive.EntryFilter searchFilter;
        private final Archive.EntryFilter includeFilter;
        private Archive.Entry current;

        AbstractIterator(Iterator<JarEntry> iterator, Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) {
            this.iterator = iterator;
            this.searchFilter = searchFilter;
            this.includeFilter = includeFilter;
            this.current = this.poll();
        }

        @Override
        public boolean hasNext() {
            return this.current != null;
        }

        @Override
        public T next() {
            T result = this.adapt(this.current);
            this.current = this.poll();
            return result;
        }

        private Archive.Entry poll() {
            while (this.iterator.hasNext()) {
                JarFileEntry candidate = new JarFileEntry(this.iterator.next());
                if (this.searchFilter != null && !this.searchFilter.matches(candidate) || this.includeFilter != null && !this.includeFilter.matches(candidate)) continue;
                return candidate;
            }
            return null;
        }

        protected abstract T adapt(Archive.Entry var1);
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.data;

import java.io.IOException;
import java.io.InputStream;

public interface RandomAccessData {
    public InputStream getInputStream() throws IOException;

    public RandomAccessData getSubsection(long var1, long var3);

    public byte[] read() throws IOException;

    public byte[] read(long var1, long var3) throws IOException;

    public long getSize();
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.data;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import org.springframework.boot.loader.data.RandomAccessData;

public class RandomAccessDataFile
implements RandomAccessData {
    private final FileAccess fileAccess;
    private final long offset;
    private final long length;

    public RandomAccessDataFile(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File must not be null");
        }
        this.fileAccess = new FileAccess(file);
        this.offset = 0L;
        this.length = file.length();
    }

    private RandomAccessDataFile(FileAccess fileAccess, long offset, long length) {
        this.fileAccess = fileAccess;
        this.offset = offset;
        this.length = length;
    }

    public File getFile() {
        return this.fileAccess.file;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new DataInputStream();
    }

    @Override
    public RandomAccessData getSubsection(long offset, long length) {
        if (offset < 0L || length < 0L || offset + length > this.length) {
            throw new IndexOutOfBoundsException();
        }
        return new RandomAccessDataFile(this.fileAccess, this.offset + offset, length);
    }

    @Override
    public byte[] read() throws IOException {
        return this.read(0L, this.length);
    }

    @Override
    public byte[] read(long offset, long length) throws IOException {
        if (offset > this.length) {
            throw new IndexOutOfBoundsException();
        }
        if (offset + length > this.length) {
            throw new EOFException();
        }
        byte[] bytes = new byte[(int)length];
        this.read(bytes, offset, 0, bytes.length);
        return bytes;
    }

    private int readByte(long position) throws IOException {
        if (position >= this.length) {
            return -1;
        }
        return this.fileAccess.readByte(this.offset + position);
    }

    private int read(byte[] bytes, long position, int offset, int length) throws IOException {
        if (position > this.length) {
            return -1;
        }
        return this.fileAccess.read(bytes, this.offset + position, offset, length);
    }

    @Override
    public long getSize() {
        return this.length;
    }

    public void close() throws IOException {
        this.fileAccess.close();
    }

    private static final class FileAccess {
        private final Object monitor = new Object();
        private final File file;
        private RandomAccessFile randomAccessFile;

        private FileAccess(File file) {
            this.file = file;
            this.openIfNecessary();
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private int read(byte[] bytes, long position, int offset, int length) throws IOException {
            Object object = this.monitor;
            synchronized (object) {
                this.openIfNecessary();
                this.randomAccessFile.seek(position);
                return this.randomAccessFile.read(bytes, offset, length);
            }
        }

        private void openIfNecessary() {
            if (this.randomAccessFile == null) {
                try {
                    this.randomAccessFile = new RandomAccessFile(this.file, "r");
                }
                catch (FileNotFoundException ex) {
                    throw new IllegalArgumentException(String.format("File %s must exist", this.file.getAbsolutePath()));
                }
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private void close() throws IOException {
            Object object = this.monitor;
            synchronized (object) {
                if (this.randomAccessFile != null) {
                    this.randomAccessFile.close();
                    this.randomAccessFile = null;
                }
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private int readByte(long position) throws IOException {
            Object object = this.monitor;
            synchronized (object) {
                this.openIfNecessary();
                this.randomAccessFile.seek(position);
                return this.randomAccessFile.read();
            }
        }
    }

    private class DataInputStream
    extends InputStream {
        private int position;

        private DataInputStream() {
        }

        @Override
        public int read() throws IOException {
            int read = RandomAccessDataFile.this.readByte(this.position);
            if (read > -1) {
                this.moveOn(1);
            }
            return read;
        }

        @Override
        public int read(byte[] b) throws IOException {
            return this.read(b, 0, b != null ? b.length : 0);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException("Bytes must not be null");
            }
            return this.doRead(b, off, len);
        }

        int doRead(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            int cappedLen = this.cap(len);
            if (cappedLen <= 0) {
                return -1;
            }
            return (int)this.moveOn(RandomAccessDataFile.this.read(b, this.position, off, cappedLen));
        }

        @Override
        public long skip(long n) throws IOException {
            return n <= 0L ? 0L : this.moveOn(this.cap(n));
        }

        private int cap(long n) {
            return (int)Math.min(RandomAccessDataFile.this.length - (long)this.position, n);
        }

        private long moveOn(int amount) {
            this.position += amount;
            return amount;
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Permission;
import java.util.jar.JarFile;

abstract class AbstractJarFile
extends JarFile {
    AbstractJarFile(File file) throws IOException {
        super(file);
    }

    abstract URL getUrl() throws MalformedURLException;

    abstract JarFileType getType();

    abstract Permission getPermission();

    abstract InputStream getInputStream() throws IOException;

    static enum JarFileType {
        DIRECT,
        NESTED_DIRECTORY,
        NESTED_JAR;

    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.loader.jar.StringSequence;

final class AsciiBytes {
    private static final String EMPTY_STRING = "";
    private static final int[] INITIAL_BYTE_BITMASK = new int[]{127, 31, 15, 7};
    private static final int SUBSEQUENT_BYTE_BITMASK = 63;
    private final byte[] bytes;
    private final int offset;
    private final int length;
    private String string;
    private int hash;

    AsciiBytes(String string) {
        this(string.getBytes(StandardCharsets.UTF_8));
        this.string = string;
    }

    AsciiBytes(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    AsciiBytes(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new IndexOutOfBoundsException();
        }
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
    }

    int length() {
        return this.length;
    }

    boolean startsWith(AsciiBytes prefix) {
        if (this == prefix) {
            return true;
        }
        if (prefix.length > this.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; ++i) {
            if (this.bytes[i + this.offset] == prefix.bytes[i + prefix.offset]) continue;
            return false;
        }
        return true;
    }

    boolean endsWith(AsciiBytes postfix) {
        if (this == postfix) {
            return true;
        }
        if (postfix.length > this.length) {
            return false;
        }
        for (int i = 0; i < postfix.length; ++i) {
            if (this.bytes[this.offset + (this.length - 1) - i] == postfix.bytes[postfix.offset + (postfix.length - 1) - i]) continue;
            return false;
        }
        return true;
    }

    AsciiBytes substring(int beginIndex) {
        return this.substring(beginIndex, this.length);
    }

    AsciiBytes substring(int beginIndex, int endIndex) {
        int length = endIndex - beginIndex;
        if (this.offset + length > this.bytes.length) {
            throw new IndexOutOfBoundsException();
        }
        return new AsciiBytes(this.bytes, this.offset + beginIndex, length);
    }

    boolean matches(CharSequence name, char suffix) {
        int charIndex = 0;
        int nameLen = name.length();
        int totalLen = nameLen + (suffix != '\u0000' ? 1 : 0);
        for (int i = this.offset; i < this.offset + this.length; ++i) {
            int b = this.bytes[i];
            int remainingUtfBytes = this.getNumberOfUtfBytes(b) - 1;
            b &= INITIAL_BYTE_BITMASK[remainingUtfBytes];
            for (int j = 0; j < remainingUtfBytes; ++j) {
                b = (b << 6) + (this.bytes[++i] & 0x3F);
            }
            char c = this.getChar(name, suffix, charIndex++);
            if (b <= 65535) {
                if (c == b) continue;
                return false;
            }
            if (c != (b >> 10) + 55232) {
                return false;
            }
            if ((c = this.getChar(name, suffix, charIndex++)) == (b & 0x3FF) + 56320) continue;
            return false;
        }
        return charIndex == totalLen;
    }

    private char getChar(CharSequence name, char suffix, int index) {
        if (index < name.length()) {
            return name.charAt(index);
        }
        if (index == name.length()) {
            return suffix;
        }
        return '\u0000';
    }

    private int getNumberOfUtfBytes(int b) {
        if ((b & 0x80) == 0) {
            return 1;
        }
        int numberOfUtfBytes = 0;
        while ((b & 0x80) != 0) {
            b <<= 1;
            ++numberOfUtfBytes;
        }
        return numberOfUtfBytes;
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (obj.getClass() == AsciiBytes.class) {
            AsciiBytes other = (AsciiBytes)obj;
            if (this.length == other.length) {
                for (int i = 0; i < this.length; ++i) {
                    if (this.bytes[this.offset + i] == other.bytes[other.offset + i]) continue;
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public int hashCode() {
        int hash = this.hash;
        if (hash == 0 && this.bytes.length > 0) {
            for (int i = this.offset; i < this.offset + this.length; ++i) {
                int b = this.bytes[i];
                int remainingUtfBytes = this.getNumberOfUtfBytes(b) - 1;
                b &= INITIAL_BYTE_BITMASK[remainingUtfBytes];
                for (int j = 0; j < remainingUtfBytes; ++j) {
                    b = (b << 6) + (this.bytes[++i] & 0x3F);
                }
                if (b <= 65535) {
                    hash = 31 * hash + b;
                    continue;
                }
                hash = 31 * hash + ((b >> 10) + 55232);
                hash = 31 * hash + ((b & 0x3FF) + 56320);
            }
            this.hash = hash;
        }
        return hash;
    }

    public String toString() {
        if (this.string == null) {
            this.string = this.length == 0 ? EMPTY_STRING : new String(this.bytes, this.offset, this.length, StandardCharsets.UTF_8);
        }
        return this.string;
    }

    static String toString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static int hashCode(CharSequence charSequence) {
        if (charSequence instanceof StringSequence) {
            return charSequence.hashCode();
        }
        return charSequence.toString().hashCode();
    }

    static int hashCode(int hash, char suffix) {
        return suffix != '\u0000' ? 31 * hash + suffix : hash;
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

final class Bytes {
    private Bytes() {
    }

    static long littleEndianValue(byte[] bytes, int offset, int length) {
        long value = 0L;
        for (int i = length - 1; i >= 0; --i) {
            value = value << 8 | (long)(bytes[offset + i] & 0xFF);
        }
        return value;
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.IOException;
import org.springframework.boot.loader.data.RandomAccessData;
import org.springframework.boot.loader.jar.AsciiBytes;
import org.springframework.boot.loader.jar.Bytes;

class CentralDirectoryEndRecord {
    private static final int MINIMUM_SIZE = 22;
    private static final int MAXIMUM_COMMENT_LENGTH = 65535;
    private static final int MAXIMUM_SIZE = 65557;
    private static final int SIGNATURE = 101010256;
    private static final int COMMENT_LENGTH_OFFSET = 20;
    private static final int READ_BLOCK_SIZE = 256;
    private final Zip64End zip64End;
    private byte[] block;
    private int offset;
    private int size;

    CentralDirectoryEndRecord(RandomAccessData data) throws IOException {
        this.block = this.createBlockFromEndOfData(data, 256);
        this.size = 22;
        this.offset = this.block.length - this.size;
        while (!this.isValid()) {
            ++this.size;
            if (this.size > this.block.length) {
                if (this.size >= 65557 || (long)this.size > data.getSize()) {
                    throw new IOException("Unable to find ZIP central directory records after reading " + this.size + " bytes");
                }
                this.block = this.createBlockFromEndOfData(data, this.size + 256);
            }
            this.offset = this.block.length - this.size;
        }
        long startOfCentralDirectoryEndRecord = data.getSize() - (long)this.size;
        Zip64Locator zip64Locator = Zip64Locator.find(data, startOfCentralDirectoryEndRecord);
        this.zip64End = zip64Locator != null ? new Zip64End(data, zip64Locator) : null;
    }

    private byte[] createBlockFromEndOfData(RandomAccessData data, int size) throws IOException {
        int length = (int)Math.min(data.getSize(), (long)size);
        return data.read(data.getSize() - (long)length, length);
    }

    private boolean isValid() {
        if (this.block.length < 22 || Bytes.littleEndianValue(this.block, this.offset + 0, 4) != 101010256L) {
            return false;
        }
        long commentLength = Bytes.littleEndianValue(this.block, this.offset + 20, 2);
        return (long)this.size == 22L + commentLength;
    }

    long getStartOfArchive(RandomAccessData data) {
        long length = Bytes.littleEndianValue(this.block, this.offset + 12, 4);
        long specifiedOffset = this.zip64End != null ? this.zip64End.centralDirectoryOffset : Bytes.littleEndianValue(this.block, this.offset + 16, 4);
        long zip64EndSize = this.zip64End != null ? this.zip64End.getSize() : 0L;
        int zip64LocSize = this.zip64End != null ? 20 : 0;
        long actualOffset = data.getSize() - (long)this.size - length - zip64EndSize - (long)zip64LocSize;
        return actualOffset - specifiedOffset;
    }

    RandomAccessData getCentralDirectory(RandomAccessData data) {
        if (this.zip64End != null) {
            return this.zip64End.getCentralDirectory(data);
        }
        long offset = Bytes.littleEndianValue(this.block, this.offset + 16, 4);
        long length = Bytes.littleEndianValue(this.block, this.offset + 12, 4);
        return data.getSubsection(offset, length);
    }

    int getNumberOfRecords() {
        if (this.zip64End != null) {
            return this.zip64End.getNumberOfRecords();
        }
        long numberOfRecords = Bytes.littleEndianValue(this.block, this.offset + 10, 2);
        return (int)numberOfRecords;
    }

    String getComment() {
        int commentLength = (int)Bytes.littleEndianValue(this.block, this.offset + 20, 2);
        AsciiBytes comment = new AsciiBytes(this.block, this.offset + 20 + 2, commentLength);
        return comment.toString();
    }

    boolean isZip64() {
        return this.zip64End != null;
    }

    private static final class Zip64Locator {
        static final int SIGNATURE = 117853008;
        static final int ZIP64_LOCSIZE = 20;
        static final int ZIP64_LOCOFF = 8;
        private final long zip64EndOffset;
        private final long offset;

        private Zip64Locator(long offset, byte[] block) throws IOException {
            this.offset = offset;
            this.zip64EndOffset = Bytes.littleEndianValue(block, 8, 8);
        }

        private long getZip64EndSize() {
            return this.offset - this.zip64EndOffset;
        }

        private long getZip64EndOffset() {
            return this.zip64EndOffset;
        }

        private static Zip64Locator find(RandomAccessData data, long centralDirectoryEndOffset) throws IOException {
            byte[] block;
            long offset = centralDirectoryEndOffset - 20L;
            if (offset >= 0L && Bytes.littleEndianValue(block = data.read(offset, 20L), 0, 4) == 117853008L) {
                return new Zip64Locator(offset, block);
            }
            return null;
        }
    }

    private static final class Zip64End {
        private static final int ZIP64_ENDTOT = 32;
        private static final int ZIP64_ENDSIZ = 40;
        private static final int ZIP64_ENDOFF = 48;
        private final Zip64Locator locator;
        private final long centralDirectoryOffset;
        private final long centralDirectoryLength;
        private final int numberOfRecords;

        private Zip64End(RandomAccessData data, Zip64Locator locator) throws IOException {
            this.locator = locator;
            byte[] block = data.read(locator.getZip64EndOffset(), 56L);
            this.centralDirectoryOffset = Bytes.littleEndianValue(block, 48, 8);
            this.centralDirectoryLength = Bytes.littleEndianValue(block, 40, 8);
            this.numberOfRecords = (int)Bytes.littleEndianValue(block, 32, 8);
        }

        private long getSize() {
            return this.locator.getZip64EndSize();
        }

        private RandomAccessData getCentralDirectory(RandomAccessData data) {
            return data.getSubsection(this.centralDirectoryOffset, this.centralDirectoryLength);
        }

        private int getNumberOfRecords() {
            return this.numberOfRecords;
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.ValueRange;
import org.springframework.boot.loader.data.RandomAccessData;
import org.springframework.boot.loader.jar.AsciiBytes;
import org.springframework.boot.loader.jar.Bytes;
import org.springframework.boot.loader.jar.FileHeader;
import org.springframework.boot.loader.jar.JarEntryFilter;

final class CentralDirectoryFileHeader
implements FileHeader {
    private static final AsciiBytes SLASH = new AsciiBytes("/");
    private static final byte[] NO_EXTRA = new byte[0];
    private static final AsciiBytes NO_COMMENT = new AsciiBytes("");
    private byte[] header;
    private int headerOffset;
    private AsciiBytes name;
    private byte[] extra;
    private AsciiBytes comment;
    private long localHeaderOffset;

    CentralDirectoryFileHeader() {
    }

    CentralDirectoryFileHeader(byte[] header, int headerOffset, AsciiBytes name, byte[] extra, AsciiBytes comment, long localHeaderOffset) {
        this.header = header;
        this.headerOffset = headerOffset;
        this.name = name;
        this.extra = extra;
        this.comment = comment;
        this.localHeaderOffset = localHeaderOffset;
    }

    void load(byte[] data, int dataOffset, RandomAccessData variableData, long variableOffset, JarEntryFilter filter) throws IOException {
        this.header = data;
        this.headerOffset = dataOffset;
        long compressedSize = Bytes.littleEndianValue(data, dataOffset + 20, 4);
        long uncompressedSize = Bytes.littleEndianValue(data, dataOffset + 24, 4);
        long nameLength = Bytes.littleEndianValue(data, dataOffset + 28, 2);
        long extraLength = Bytes.littleEndianValue(data, dataOffset + 30, 2);
        long commentLength = Bytes.littleEndianValue(data, dataOffset + 32, 2);
        long localHeaderOffset = Bytes.littleEndianValue(data, dataOffset + 42, 4);
        dataOffset += 46;
        if (variableData != null) {
            data = variableData.read(variableOffset + 46L, nameLength + extraLength + commentLength);
            dataOffset = 0;
        }
        this.name = new AsciiBytes(data, dataOffset, (int)nameLength);
        if (filter != null) {
            this.name = filter.apply(this.name);
        }
        this.extra = NO_EXTRA;
        this.comment = NO_COMMENT;
        if (extraLength > 0L) {
            this.extra = new byte[(int)extraLength];
            System.arraycopy(data, (int)((long)dataOffset + nameLength), this.extra, 0, this.extra.length);
        }
        this.localHeaderOffset = this.getLocalHeaderOffset(compressedSize, uncompressedSize, localHeaderOffset, this.extra);
        if (commentLength > 0L) {
            this.comment = new AsciiBytes(data, (int)((long)dataOffset + nameLength + extraLength), (int)commentLength);
        }
    }

    private long getLocalHeaderOffset(long compressedSize, long uncompressedSize, long localHeaderOffset, byte[] extra) throws IOException {
        int length;
        if (localHeaderOffset != 0xFFFFFFFFL) {
            return localHeaderOffset;
        }
        for (int extraOffset = 0; extraOffset < extra.length - 2; extraOffset += length) {
            int id = (int)Bytes.littleEndianValue(extra, extraOffset, 2);
            length = (int)Bytes.littleEndianValue(extra, extraOffset, 2);
            extraOffset += 4;
            if (id != 1) continue;
            int localHeaderExtraOffset = 0;
            if (compressedSize == 0xFFFFFFFFL) {
                localHeaderExtraOffset += 4;
            }
            if (uncompressedSize == 0xFFFFFFFFL) {
                localHeaderExtraOffset += 4;
            }
            return Bytes.littleEndianValue(extra, extraOffset + localHeaderExtraOffset, 8);
        }
        throw new IOException("Zip64 Extended Information Extra Field not found");
    }

    AsciiBytes getName() {
        return this.name;
    }

    @Override
    public boolean hasName(CharSequence name, char suffix) {
        return this.name.matches(name, suffix);
    }

    boolean isDirectory() {
        return this.name.endsWith(SLASH);
    }

    @Override
    public int getMethod() {
        return (int)Bytes.littleEndianValue(this.header, this.headerOffset + 10, 2);
    }

    long getTime() {
        long datetime = Bytes.littleEndianValue(this.header, this.headerOffset + 12, 4);
        return this.decodeMsDosFormatDateTime(datetime);
    }

    private long decodeMsDosFormatDateTime(long datetime) {
        int year = CentralDirectoryFileHeader.getChronoValue((datetime >> 25 & 0x7FL) + 1980L, ChronoField.YEAR);
        int month = CentralDirectoryFileHeader.getChronoValue(datetime >> 21 & 0xFL, ChronoField.MONTH_OF_YEAR);
        int day = CentralDirectoryFileHeader.getChronoValue(datetime >> 16 & 0x1FL, ChronoField.DAY_OF_MONTH);
        int hour = CentralDirectoryFileHeader.getChronoValue(datetime >> 11 & 0x1FL, ChronoField.HOUR_OF_DAY);
        int minute = CentralDirectoryFileHeader.getChronoValue(datetime >> 5 & 0x3FL, ChronoField.MINUTE_OF_HOUR);
        int second = CentralDirectoryFileHeader.getChronoValue(datetime << 1 & 0x3EL, ChronoField.SECOND_OF_MINUTE);
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
    }

    long getCrc() {
        return Bytes.littleEndianValue(this.header, this.headerOffset + 16, 4);
    }

    @Override
    public long getCompressedSize() {
        return Bytes.littleEndianValue(this.header, this.headerOffset + 20, 4);
    }

    @Override
    public long getSize() {
        return Bytes.littleEndianValue(this.header, this.headerOffset + 24, 4);
    }

    byte[] getExtra() {
        return this.extra;
    }

    boolean hasExtra() {
        return this.extra.length > 0;
    }

    AsciiBytes getComment() {
        return this.comment;
    }

    @Override
    public long getLocalHeaderOffset() {
        return this.localHeaderOffset;
    }

    public CentralDirectoryFileHeader clone() {
        byte[] header = new byte[46];
        System.arraycopy(this.header, this.headerOffset, header, 0, header.length);
        return new CentralDirectoryFileHeader(header, 0, this.name, header, this.comment, this.localHeaderOffset);
    }

    static CentralDirectoryFileHeader fromRandomAccessData(RandomAccessData data, long offset, JarEntryFilter filter) throws IOException {
        CentralDirectoryFileHeader fileHeader = new CentralDirectoryFileHeader();
        byte[] bytes = data.read(offset, 46L);
        fileHeader.load(bytes, 0, data, offset, filter);
        return fileHeader;
    }

    private static int getChronoValue(long value, ChronoField field) {
        ValueRange range = field.range();
        return Math.toIntExact(Math.min(Math.max(value, range.getMinimum()), range.getMaximum()));
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.loader.data.RandomAccessData;
import org.springframework.boot.loader.jar.CentralDirectoryEndRecord;
import org.springframework.boot.loader.jar.CentralDirectoryFileHeader;
import org.springframework.boot.loader.jar.CentralDirectoryVisitor;

class CentralDirectoryParser {
    private static final int CENTRAL_DIRECTORY_HEADER_BASE_SIZE = 46;
    private final List<CentralDirectoryVisitor> visitors = new ArrayList<CentralDirectoryVisitor>();

    CentralDirectoryParser() {
    }

    <T extends CentralDirectoryVisitor> T addVisitor(T visitor) {
        this.visitors.add(visitor);
        return visitor;
    }

    RandomAccessData parse(RandomAccessData data, boolean skipPrefixBytes) throws IOException {
        CentralDirectoryEndRecord endRecord = new CentralDirectoryEndRecord(data);
        if (skipPrefixBytes) {
            data = this.getArchiveData(endRecord, data);
        }
        RandomAccessData centralDirectoryData = endRecord.getCentralDirectory(data);
        this.visitStart(endRecord, centralDirectoryData);
        this.parseEntries(endRecord, centralDirectoryData);
        this.visitEnd();
        return data;
    }

    private void parseEntries(CentralDirectoryEndRecord endRecord, RandomAccessData centralDirectoryData) throws IOException {
        byte[] bytes = centralDirectoryData.read(0L, centralDirectoryData.getSize());
        CentralDirectoryFileHeader fileHeader = new CentralDirectoryFileHeader();
        int dataOffset = 0;
        for (int i = 0; i < endRecord.getNumberOfRecords(); ++i) {
            fileHeader.load(bytes, dataOffset, null, 0L, null);
            this.visitFileHeader(dataOffset, fileHeader);
            dataOffset += 46 + fileHeader.getName().length() + fileHeader.getComment().length() + fileHeader.getExtra().length;
        }
    }

    private RandomAccessData getArchiveData(CentralDirectoryEndRecord endRecord, RandomAccessData data) {
        long offset = endRecord.getStartOfArchive(data);
        if (offset == 0L) {
            return data;
        }
        return data.getSubsection(offset, data.getSize() - offset);
    }

    private void visitStart(CentralDirectoryEndRecord endRecord, RandomAccessData centralDirectoryData) {
        for (CentralDirectoryVisitor visitor : this.visitors) {
            visitor.visitStart(endRecord, centralDirectoryData);
        }
    }

    private void visitFileHeader(long dataOffset, CentralDirectoryFileHeader fileHeader) {
        for (CentralDirectoryVisitor visitor : this.visitors) {
            visitor.visitFileHeader(fileHeader, dataOffset);
        }
    }

    private void visitEnd() {
        for (CentralDirectoryVisitor visitor : this.visitors) {
            visitor.visitEnd();
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import org.springframework.boot.loader.data.RandomAccessData;
import org.springframework.boot.loader.jar.CentralDirectoryEndRecord;
import org.springframework.boot.loader.jar.CentralDirectoryFileHeader;

interface CentralDirectoryVisitor {
    public void visitStart(CentralDirectoryEndRecord var1, RandomAccessData var2);

    public void visitFileHeader(CentralDirectoryFileHeader var1, long var2);

    public void visitEnd();
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

interface FileHeader {
    public boolean hasName(CharSequence var1, char var2);

    public long getLocalHeaderOffset();

    public long getCompressedSize();

    public long getSize();

    public int getMethod();
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.springframework.boot.loader.jar.JarFile;
import org.springframework.boot.loader.jar.JarURLConnection;

public class Handler
extends URLStreamHandler {
    private static final String JAR_PROTOCOL = "jar:";
    private static final String FILE_PROTOCOL = "file:";
    private static final String TOMCAT_WARFILE_PROTOCOL = "war:file:";
    private static final String SEPARATOR = "!/";
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("!/", 16);
    private static final String CURRENT_DIR = "/./";
    private static final Pattern CURRENT_DIR_PATTERN = Pattern.compile("/./", 16);
    private static final String PARENT_DIR = "/../";
    private static final String PROTOCOL_HANDLER = "java.protocol.handler.pkgs";
    private static final String[] FALLBACK_HANDLERS = new String[]{"sun.net.www.protocol.jar.Handler"};
    private static URL jarContextUrl;
    private static SoftReference<Map<File, JarFile>> rootFileCache;
    private final JarFile jarFile;
    private URLStreamHandler fallbackHandler;

    public Handler() {
        this(null);
    }

    public Handler(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        if (this.jarFile != null && this.isUrlInJarFile(url, this.jarFile)) {
            return JarURLConnection.get(url, this.jarFile);
        }
        try {
            return JarURLConnection.get(url, this.getRootJarFileFromUrl(url));
        }
        catch (Exception ex) {
            return this.openFallbackConnection(url, ex);
        }
    }

    private boolean isUrlInJarFile(URL url, JarFile jarFile) throws MalformedURLException {
        return url.getPath().startsWith(jarFile.getUrl().getPath()) && url.toString().startsWith(jarFile.getUrlString());
    }

    private URLConnection openFallbackConnection(URL url, Exception reason) throws IOException {
        try {
            URLConnection connection = this.openFallbackTomcatConnection(url);
            connection = connection != null ? connection : this.openFallbackContextConnection(url);
            return connection != null ? connection : this.openFallbackHandlerConnection(url);
        }
        catch (Exception ex) {
            if (reason instanceof IOException) {
                this.log(false, "Unable to open fallback handler", ex);
                throw (IOException)reason;
            }
            this.log(true, "Unable to open fallback handler", ex);
            if (reason instanceof RuntimeException) {
                throw (RuntimeException)reason;
            }
            throw new IllegalStateException(reason);
        }
    }

    private URLConnection openFallbackTomcatConnection(URL url) {
        String file = url.getFile();
        if (this.isTomcatWarUrl(file)) {
            file = file.substring(TOMCAT_WARFILE_PROTOCOL.length());
            file = file.replaceFirst("\\*/", SEPARATOR);
            try {
                URLConnection connection = this.openConnection(new URL("jar:file:" + file));
                connection.getInputStream().close();
                return connection;
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
        return null;
    }

    private boolean isTomcatWarUrl(String file) {
        if (file.startsWith(TOMCAT_WARFILE_PROTOCOL) || !file.contains("*/")) {
            try {
                URLConnection connection = new URL(file).openConnection();
                if (connection.getClass().getName().startsWith("org.apache.catalina")) {
                    return true;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return false;
    }

    private URLConnection openFallbackContextConnection(URL url) {
        try {
            if (jarContextUrl != null) {
                return new URL(jarContextUrl, url.toExternalForm()).openConnection();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private URLConnection openFallbackHandlerConnection(URL url) throws Exception {
        URLStreamHandler fallbackHandler = this.getFallbackHandler();
        return new URL(null, url.toExternalForm(), fallbackHandler).openConnection();
    }

    private URLStreamHandler getFallbackHandler() {
        if (this.fallbackHandler != null) {
            return this.fallbackHandler;
        }
        for (String handlerClassName : FALLBACK_HANDLERS) {
            try {
                Class<?> handlerClass = Class.forName(handlerClassName);
                this.fallbackHandler = (URLStreamHandler)handlerClass.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
                return this.fallbackHandler;
            }
            catch (Exception exception) {
            }
        }
        throw new IllegalStateException("Unable to find fallback handler");
    }

    private void log(boolean warning, String message, Exception cause) {
        block2: {
            try {
                Level level = warning ? Level.WARNING : Level.FINEST;
                Logger.getLogger(this.getClass().getName()).log(level, message, cause);
            }
            catch (Exception ex) {
                if (!warning) break block2;
                System.err.println("WARNING: " + message);
            }
        }
    }

    @Override
    protected void parseURL(URL context, String spec, int start, int limit) {
        if (spec.regionMatches(true, 0, JAR_PROTOCOL, 0, JAR_PROTOCOL.length())) {
            this.setFile(context, this.getFileFromSpec(spec.substring(start, limit)));
        } else {
            this.setFile(context, this.getFileFromContext(context, spec.substring(start, limit)));
        }
    }

    private String getFileFromSpec(String spec) {
        int separatorIndex = spec.lastIndexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("No !/ in spec '" + spec + "'");
        }
        try {
            new URL(spec.substring(0, separatorIndex));
            return spec;
        }
        catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Invalid spec URL '" + spec + "'", ex);
        }
    }

    private String getFileFromContext(URL context, String spec) {
        String file = context.getFile();
        if (spec.startsWith("/")) {
            return this.trimToJarRoot(file) + SEPARATOR + spec.substring(1);
        }
        if (file.endsWith("/")) {
            return file + spec;
        }
        int lastSlashIndex = file.lastIndexOf(47);
        if (lastSlashIndex == -1) {
            throw new IllegalArgumentException("No / found in context URL's file '" + file + "'");
        }
        return file.substring(0, lastSlashIndex + 1) + spec;
    }

    private String trimToJarRoot(String file) {
        int lastSeparatorIndex = file.lastIndexOf(SEPARATOR);
        if (lastSeparatorIndex == -1) {
            throw new IllegalArgumentException("No !/ found in context URL's file '" + file + "'");
        }
        return file.substring(0, lastSeparatorIndex);
    }

    private void setFile(URL context, String file) {
        String path = this.normalize(file);
        String query = null;
        int queryIndex = path.lastIndexOf(63);
        if (queryIndex != -1) {
            query = path.substring(queryIndex + 1);
            path = path.substring(0, queryIndex);
        }
        this.setURL(context, JAR_PROTOCOL, null, -1, null, null, path, query, context.getRef());
    }

    private String normalize(String file) {
        if (!file.contains(CURRENT_DIR) && !file.contains(PARENT_DIR)) {
            return file;
        }
        int afterLastSeparatorIndex = file.lastIndexOf(SEPARATOR) + SEPARATOR.length();
        String afterSeparator = file.substring(afterLastSeparatorIndex);
        afterSeparator = this.replaceParentDir(afterSeparator);
        afterSeparator = this.replaceCurrentDir(afterSeparator);
        return file.substring(0, afterLastSeparatorIndex) + afterSeparator;
    }

    private String replaceParentDir(String file) {
        int parentDirIndex;
        while ((parentDirIndex = file.indexOf(PARENT_DIR)) >= 0) {
            int precedingSlashIndex = file.lastIndexOf(47, parentDirIndex - 1);
            if (precedingSlashIndex >= 0) {
                file = file.substring(0, precedingSlashIndex) + file.substring(parentDirIndex + 3);
                continue;
            }
            file = file.substring(parentDirIndex + 4);
        }
        return file;
    }

    private String replaceCurrentDir(String file) {
        return CURRENT_DIR_PATTERN.matcher(file).replaceAll("/");
    }

    @Override
    protected int hashCode(URL u) {
        return this.hashCode(u.getProtocol(), u.getFile());
    }

    private int hashCode(String protocol, String file) {
        int result = protocol != null ? protocol.hashCode() : 0;
        int separatorIndex = file.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            return result + file.hashCode();
        }
        String source = file.substring(0, separatorIndex);
        String entry = this.canonicalize(file.substring(separatorIndex + 2));
        try {
            result += new URL(source).hashCode();
        }
        catch (MalformedURLException ex) {
            result += source.hashCode();
        }
        return result += entry.hashCode();
    }

    @Override
    protected boolean sameFile(URL u1, URL u2) {
        String canonical2;
        String canonical1;
        String nested2;
        if (!u1.getProtocol().equals("jar") || !u2.getProtocol().equals("jar")) {
            return false;
        }
        int separator1 = u1.getFile().indexOf(SEPARATOR);
        int separator2 = u2.getFile().indexOf(SEPARATOR);
        if (separator1 == -1 || separator2 == -1) {
            return super.sameFile(u1, u2);
        }
        String nested1 = u1.getFile().substring(separator1 + SEPARATOR.length());
        if (!nested1.equals(nested2 = u2.getFile().substring(separator2 + SEPARATOR.length())) && !(canonical1 = this.canonicalize(nested1)).equals(canonical2 = this.canonicalize(nested2))) {
            return false;
        }
        String root1 = u1.getFile().substring(0, separator1);
        String root2 = u2.getFile().substring(0, separator2);
        try {
            return super.sameFile(new URL(root1), new URL(root2));
        }
        catch (MalformedURLException malformedURLException) {
            return super.sameFile(u1, u2);
        }
    }

    private String canonicalize(String path) {
        return SEPARATOR_PATTERN.matcher(path).replaceAll("/");
    }

    public JarFile getRootJarFileFromUrl(URL url) throws IOException {
        String spec = url.getFile();
        int separatorIndex = spec.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new MalformedURLException("Jar URL does not contain !/ separator");
        }
        String name = spec.substring(0, separatorIndex);
        return this.getRootJarFile(name);
    }

    private JarFile getRootJarFile(String name) throws IOException {
        try {
            JarFile result;
            if (!name.startsWith(FILE_PROTOCOL)) {
                throw new IllegalStateException("Not a file URL");
            }
            File file = new File(URI.create(name));
            Map<File, JarFile> cache = rootFileCache.get();
            JarFile jarFile = result = cache != null ? cache.get(file) : null;
            if (result == null) {
                result = new JarFile(file);
                Handler.addToRootFileCache(file, result);
            }
            return result;
        }
        catch (Exception ex) {
            throw new IOException("Unable to open root Jar file '" + name + "'", ex);
        }
    }

    static void addToRootFileCache(File sourceFile, JarFile jarFile) {
        Map<File, JarFile> cache = rootFileCache.get();
        if (cache == null) {
            cache = new ConcurrentHashMap<File, JarFile>();
            rootFileCache = new SoftReference<Map<File, JarFile>>(cache);
        }
        cache.put(sourceFile, jarFile);
    }

    static void captureJarContextUrl() {
        if (Handler.canResetCachedUrlHandlers()) {
            String handlers = System.getProperty(PROTOCOL_HANDLER, "");
            try {
                System.clearProperty(PROTOCOL_HANDLER);
                try {
                    Handler.resetCachedUrlHandlers();
                    jarContextUrl = new URL("jar:file:context.jar!/");
                    URLConnection connection = jarContextUrl.openConnection();
                    if (connection instanceof JarURLConnection) {
                        jarContextUrl = null;
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            finally {
                if (handlers == null) {
                    System.clearProperty(PROTOCOL_HANDLER);
                } else {
                    System.setProperty(PROTOCOL_HANDLER, handlers);
                }
            }
            Handler.resetCachedUrlHandlers();
        }
    }

    private static boolean canResetCachedUrlHandlers() {
        try {
            Handler.resetCachedUrlHandlers();
            return true;
        }
        catch (Error ex) {
            return false;
        }
    }

    private static void resetCachedUrlHandlers() {
        URL.setURLStreamHandlerFactory(null);
    }

    public static void setUseFastConnectionExceptions(boolean useFastConnectionExceptions) {
        JarURLConnection.setUseFastExceptions(useFastConnectionExceptions);
    }

    static {
        rootFileCache = new SoftReference<Object>(null);
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.springframework.boot.loader.jar.AsciiBytes;
import org.springframework.boot.loader.jar.CentralDirectoryFileHeader;
import org.springframework.boot.loader.jar.FileHeader;
import org.springframework.boot.loader.jar.JarEntryCertification;
import org.springframework.boot.loader.jar.JarFile;

class JarEntry
extends java.util.jar.JarEntry
implements FileHeader {
    private final int index;
    private final AsciiBytes name;
    private final AsciiBytes headerName;
    private final JarFile jarFile;
    private long localHeaderOffset;
    private volatile JarEntryCertification certification;

    JarEntry(JarFile jarFile, int index, CentralDirectoryFileHeader header, AsciiBytes nameAlias) {
        super(nameAlias != null ? nameAlias.toString() : header.getName().toString());
        this.index = index;
        this.name = nameAlias != null ? nameAlias : header.getName();
        this.headerName = header.getName();
        this.jarFile = jarFile;
        this.localHeaderOffset = header.getLocalHeaderOffset();
        this.setCompressedSize(header.getCompressedSize());
        this.setMethod(header.getMethod());
        this.setCrc(header.getCrc());
        this.setComment(header.getComment().toString());
        this.setSize(header.getSize());
        this.setTime(header.getTime());
        if (header.hasExtra()) {
            this.setExtra(header.getExtra());
        }
    }

    int getIndex() {
        return this.index;
    }

    AsciiBytes getAsciiBytesName() {
        return this.name;
    }

    @Override
    public boolean hasName(CharSequence name, char suffix) {
        return this.headerName.matches(name, suffix);
    }

    URL getUrl() throws MalformedURLException {
        return new URL(this.jarFile.getUrl(), this.getName());
    }

    @Override
    public Attributes getAttributes() throws IOException {
        Manifest manifest = this.jarFile.getManifest();
        return manifest != null ? manifest.getAttributes(this.getName()) : null;
    }

    @Override
    public Certificate[] getCertificates() {
        return this.getCertification().getCertificates();
    }

    @Override
    public CodeSigner[] getCodeSigners() {
        return this.getCertification().getCodeSigners();
    }

    private JarEntryCertification getCertification() {
        if (!this.jarFile.isSigned()) {
            return JarEntryCertification.NONE;
        }
        JarEntryCertification certification = this.certification;
        if (certification == null) {
            this.certification = certification = this.jarFile.getCertification(this);
        }
        return certification;
    }

    @Override
    public long getLocalHeaderOffset() {
        return this.localHeaderOffset;
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.util.jar.JarEntry;

class JarEntryCertification {
    static final JarEntryCertification NONE = new JarEntryCertification(null, null);
    private final Certificate[] certificates;
    private final CodeSigner[] codeSigners;

    JarEntryCertification(Certificate[] certificates, CodeSigner[] codeSigners) {
        this.certificates = certificates;
        this.codeSigners = codeSigners;
    }

    Certificate[] getCertificates() {
        return this.certificates != null ? (Certificate[])this.certificates.clone() : null;
    }

    CodeSigner[] getCodeSigners() {
        return this.codeSigners != null ? (CodeSigner[])this.codeSigners.clone() : null;
    }

    static JarEntryCertification from(JarEntry certifiedEntry) {
        CodeSigner[] codeSigners;
        Certificate[] certificates = certifiedEntry != null ? certifiedEntry.getCertificates() : null;
        CodeSigner[] codeSignerArray = codeSigners = certifiedEntry != null ? certifiedEntry.getCodeSigners() : null;
        if (certificates == null && codeSigners == null) {
            return NONE;
        }
        return new JarEntryCertification(certificates, codeSigners);
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import org.springframework.boot.loader.jar.AsciiBytes;

interface JarEntryFilter {
    public AsciiBytes apply(AsciiBytes var1);
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Permission;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import org.springframework.boot.loader.data.RandomAccessData;
import org.springframework.boot.loader.data.RandomAccessDataFile;
import org.springframework.boot.loader.jar.AbstractJarFile;
import org.springframework.boot.loader.jar.AsciiBytes;
import org.springframework.boot.loader.jar.CentralDirectoryEndRecord;
import org.springframework.boot.loader.jar.CentralDirectoryFileHeader;
import org.springframework.boot.loader.jar.CentralDirectoryParser;
import org.springframework.boot.loader.jar.CentralDirectoryVisitor;
import org.springframework.boot.loader.jar.Handler;
import org.springframework.boot.loader.jar.JarEntry;
import org.springframework.boot.loader.jar.JarEntryCertification;
import org.springframework.boot.loader.jar.JarEntryFilter;
import org.springframework.boot.loader.jar.JarFileEntries;
import org.springframework.boot.loader.jar.JarFileWrapper;

public class JarFile
extends AbstractJarFile
implements Iterable<java.util.jar.JarEntry> {
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";
    private static final String PROTOCOL_HANDLER = "java.protocol.handler.pkgs";
    private static final String HANDLERS_PACKAGE = "org.springframework.boot.loader";
    private static final AsciiBytes META_INF = new AsciiBytes("META-INF/");
    private static final AsciiBytes SIGNATURE_FILE_EXTENSION = new AsciiBytes(".SF");
    private static final String READ_ACTION = "read";
    private final RandomAccessDataFile rootFile;
    private final String pathFromRoot;
    private final RandomAccessData data;
    private final AbstractJarFile.JarFileType type;
    private URL url;
    private String urlString;
    private JarFileEntries entries;
    private Supplier<Manifest> manifestSupplier;
    private SoftReference<Manifest> manifest;
    private boolean signed;
    private String comment;
    private volatile boolean closed;
    private volatile JarFileWrapper wrapper;

    public JarFile(File file) throws IOException {
        this(new RandomAccessDataFile(file));
    }

    JarFile(RandomAccessDataFile file) throws IOException {
        this(file, "", file, AbstractJarFile.JarFileType.DIRECT);
    }

    private JarFile(RandomAccessDataFile rootFile, String pathFromRoot, RandomAccessData data, AbstractJarFile.JarFileType type) throws IOException {
        this(rootFile, pathFromRoot, data, null, type, null);
    }

    private JarFile(RandomAccessDataFile rootFile, String pathFromRoot, RandomAccessData data, JarEntryFilter filter, AbstractJarFile.JarFileType type, Supplier<Manifest> manifestSupplier) throws IOException {
        super(rootFile.getFile());
        if (System.getSecurityManager() == null) {
            super.close();
        }
        this.rootFile = rootFile;
        this.pathFromRoot = pathFromRoot;
        CentralDirectoryParser parser = new CentralDirectoryParser();
        this.entries = parser.addVisitor(new JarFileEntries(this, filter));
        this.type = type;
        parser.addVisitor(this.centralDirectoryVisitor());
        try {
            this.data = parser.parse(data, filter == null);
        }
        catch (RuntimeException ex) {
            try {
                this.rootFile.close();
                super.close();
            }
            catch (IOException iOException) {
                // empty catch block
            }
            throw ex;
        }
        this.manifestSupplier = manifestSupplier != null ? manifestSupplier : () -> {
            try (InputStream inputStream = this.getInputStream(MANIFEST_NAME);){
                if (inputStream == null) {
                    Manifest manifest = null;
                    return manifest;
                }
                Manifest manifest = new Manifest(inputStream);
                return manifest;
            }
            catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        };
    }

    private CentralDirectoryVisitor centralDirectoryVisitor() {
        return new CentralDirectoryVisitor(){

            @Override
            public void visitStart(CentralDirectoryEndRecord endRecord, RandomAccessData centralDirectoryData) {
                JarFile.this.comment = endRecord.getComment();
            }

            @Override
            public void visitFileHeader(CentralDirectoryFileHeader fileHeader, long dataOffset) {
                AsciiBytes name = fileHeader.getName();
                if (name.startsWith(META_INF) && name.endsWith(SIGNATURE_FILE_EXTENSION)) {
                    JarFile.this.signed = true;
                }
            }

            @Override
            public void visitEnd() {
            }
        };
    }

    JarFileWrapper getWrapper() throws IOException {
        JarFileWrapper wrapper = this.wrapper;
        if (wrapper == null) {
            this.wrapper = wrapper = new JarFileWrapper(this);
        }
        return wrapper;
    }

    @Override
    Permission getPermission() {
        return new FilePermission(this.rootFile.getFile().getPath(), READ_ACTION);
    }

    protected final RandomAccessDataFile getRootJarFile() {
        return this.rootFile;
    }

    RandomAccessData getData() {
        return this.data;
    }

    @Override
    public Manifest getManifest() throws IOException {
        Manifest manifest;
        Manifest manifest2 = manifest = this.manifest != null ? this.manifest.get() : null;
        if (manifest == null) {
            try {
                manifest = this.manifestSupplier.get();
            }
            catch (RuntimeException ex) {
                throw new IOException(ex);
            }
            this.manifest = new SoftReference<Manifest>(manifest);
        }
        return manifest;
    }

    @Override
    public Enumeration<java.util.jar.JarEntry> entries() {
        return new JarEntryEnumeration(this.entries.iterator());
    }

    @Override
    public Stream<java.util.jar.JarEntry> stream() {
        Spliterator<java.util.jar.JarEntry> spliterator = Spliterators.spliterator(this.iterator(), (long)this.size(), 1297);
        return StreamSupport.stream(spliterator, false);
    }

    @Override
    public Iterator<java.util.jar.JarEntry> iterator() {
        return this.entries.iterator(this::ensureOpen);
    }

    public JarEntry getJarEntry(CharSequence name) {
        return this.entries.getEntry(name);
    }

    @Override
    public JarEntry getJarEntry(String name) {
        return (JarEntry)this.getEntry(name);
    }

    public boolean containsEntry(String name) {
        return this.entries.containsEntry(name);
    }

    @Override
    public ZipEntry getEntry(String name) {
        this.ensureOpen();
        return this.entries.getEntry(name);
    }

    @Override
    InputStream getInputStream() throws IOException {
        return this.data.getInputStream();
    }

    @Override
    public synchronized InputStream getInputStream(ZipEntry entry) throws IOException {
        this.ensureOpen();
        if (entry instanceof JarEntry) {
            return this.entries.getInputStream((JarEntry)entry);
        }
        return this.getInputStream(entry != null ? entry.getName() : null);
    }

    InputStream getInputStream(String name) throws IOException {
        return this.entries.getInputStream(name);
    }

    public synchronized JarFile getNestedJarFile(ZipEntry entry) throws IOException {
        return this.getNestedJarFile((JarEntry)entry);
    }

    public synchronized JarFile getNestedJarFile(JarEntry entry) throws IOException {
        try {
            return this.createJarFileFromEntry(entry);
        }
        catch (Exception ex) {
            throw new IOException("Unable to open nested jar file '" + entry.getName() + "'", ex);
        }
    }

    private JarFile createJarFileFromEntry(JarEntry entry) throws IOException {
        if (entry.isDirectory()) {
            return this.createJarFileFromDirectoryEntry(entry);
        }
        return this.createJarFileFromFileEntry(entry);
    }

    private JarFile createJarFileFromDirectoryEntry(JarEntry entry) throws IOException {
        AsciiBytes name = entry.getAsciiBytesName();
        JarEntryFilter filter = candidate -> {
            if (candidate.startsWith(name) && !candidate.equals(name)) {
                return candidate.substring(name.length());
            }
            return null;
        };
        return new JarFile(this.rootFile, this.pathFromRoot + "!/" + entry.getName().substring(0, name.length() - 1), this.data, filter, AbstractJarFile.JarFileType.NESTED_DIRECTORY, this.manifestSupplier);
    }

    private JarFile createJarFileFromFileEntry(JarEntry entry) throws IOException {
        if (entry.getMethod() != 0) {
            throw new IllegalStateException("Unable to open nested entry '" + entry.getName() + "'. It has been compressed and nested jar files must be stored without compression. Please check the mechanism used to create your executable jar file");
        }
        RandomAccessData entryData = this.entries.getEntryData(entry.getName());
        return new JarFile(this.rootFile, this.pathFromRoot + "!/" + entry.getName(), entryData, AbstractJarFile.JarFileType.NESTED_JAR);
    }

    @Override
    public String getComment() {
        this.ensureOpen();
        return this.comment;
    }

    @Override
    public int size() {
        this.ensureOpen();
        return this.entries.getSize();
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        super.close();
        if (this.type == AbstractJarFile.JarFileType.DIRECT) {
            this.rootFile.close();
        }
        this.closed = true;
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("zip file closed");
        }
    }

    boolean isClosed() {
        return this.closed;
    }

    String getUrlString() throws MalformedURLException {
        if (this.urlString == null) {
            this.urlString = this.getUrl().toString();
        }
        return this.urlString;
    }

    @Override
    public URL getUrl() throws MalformedURLException {
        if (this.url == null) {
            String file = this.rootFile.getFile().toURI() + this.pathFromRoot + "!/";
            file = file.replace("file:////", "file://");
            this.url = new URL("jar", "", -1, file, new Handler(this));
        }
        return this.url;
    }

    public String toString() {
        return this.getName();
    }

    @Override
    public String getName() {
        return this.rootFile.getFile() + this.pathFromRoot;
    }

    boolean isSigned() {
        return this.signed;
    }

    JarEntryCertification getCertification(JarEntry entry) {
        try {
            return this.entries.getCertification(entry);
        }
        catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public void clearCache() {
        this.entries.clearCache();
    }

    protected String getPathFromRoot() {
        return this.pathFromRoot;
    }

    @Override
    AbstractJarFile.JarFileType getType() {
        return this.type;
    }

    public static void registerUrlProtocolHandler() {
        Handler.captureJarContextUrl();
        String handlers = System.getProperty(PROTOCOL_HANDLER, "");
        System.setProperty(PROTOCOL_HANDLER, handlers == null || handlers.isEmpty() ? HANDLERS_PACKAGE : handlers + "|" + HANDLERS_PACKAGE);
        JarFile.resetCachedUrlHandlers();
    }

    private static void resetCachedUrlHandlers() {
        try {
            URL.setURLStreamHandlerFactory(null);
        }
        catch (Error error) {
            // empty catch block
        }
    }

    private static class JarEntryEnumeration
    implements Enumeration<java.util.jar.JarEntry> {
        private final Iterator<JarEntry> iterator;

        JarEntryEnumeration(Iterator<JarEntry> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasMoreElements() {
            return this.iterator.hasNext();
        }

        @Override
        public java.util.jar.JarEntry nextElement() {
            return this.iterator.next();
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import org.springframework.boot.loader.data.RandomAccessData;
import org.springframework.boot.loader.jar.AsciiBytes;
import org.springframework.boot.loader.jar.Bytes;
import org.springframework.boot.loader.jar.CentralDirectoryEndRecord;
import org.springframework.boot.loader.jar.CentralDirectoryFileHeader;
import org.springframework.boot.loader.jar.CentralDirectoryVisitor;
import org.springframework.boot.loader.jar.FileHeader;
import org.springframework.boot.loader.jar.JarEntry;
import org.springframework.boot.loader.jar.JarEntryCertification;
import org.springframework.boot.loader.jar.JarEntryFilter;
import org.springframework.boot.loader.jar.JarFile;
import org.springframework.boot.loader.jar.ZipInflaterInputStream;

class JarFileEntries
implements CentralDirectoryVisitor,
Iterable<JarEntry> {
    private static final Runnable NO_VALIDATION;
    private static final String META_INF_PREFIX = "META-INF/";
    private static final Attributes.Name MULTI_RELEASE;
    private static final int BASE_VERSION = 8;
    private static final int RUNTIME_VERSION;
    private static final long LOCAL_FILE_HEADER_SIZE = 30L;
    private static final char SLASH = '/';
    private static final char NO_SUFFIX = '\u0000';
    protected static final int ENTRY_CACHE_SIZE = 25;
    private final JarFile jarFile;
    private final JarEntryFilter filter;
    private RandomAccessData centralDirectoryData;
    private int size;
    private int[] hashCodes;
    private Offsets centralDirectoryOffsets;
    private int[] positions;
    private Boolean multiReleaseJar;
    private JarEntryCertification[] certifications;
    private final Map<Integer, FileHeader> entriesCache = Collections.synchronizedMap(new LinkedHashMap<Integer, FileHeader>(16, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, FileHeader> eldest) {
            return this.size() >= 25;
        }
    });

    JarFileEntries(JarFile jarFile, JarEntryFilter filter) {
        this.jarFile = jarFile;
        this.filter = filter;
        if (RUNTIME_VERSION == 8) {
            this.multiReleaseJar = false;
        }
    }

    @Override
    public void visitStart(CentralDirectoryEndRecord endRecord, RandomAccessData centralDirectoryData) {
        int maxSize = endRecord.getNumberOfRecords();
        this.centralDirectoryData = centralDirectoryData;
        this.hashCodes = new int[maxSize];
        this.centralDirectoryOffsets = Offsets.from(endRecord);
        this.positions = new int[maxSize];
    }

    @Override
    public void visitFileHeader(CentralDirectoryFileHeader fileHeader, long dataOffset) {
        AsciiBytes name = this.applyFilter(fileHeader.getName());
        if (name != null) {
            this.add(name, dataOffset);
        }
    }

    private void add(AsciiBytes name, long dataOffset) {
        this.hashCodes[this.size] = name.hashCode();
        this.centralDirectoryOffsets.set(this.size, dataOffset);
        this.positions[this.size] = this.size;
        ++this.size;
    }

    @Override
    public void visitEnd() {
        this.sort(0, this.size - 1);
        int[] positions = this.positions;
        this.positions = new int[positions.length];
        for (int i = 0; i < this.size; ++i) {
            this.positions[positions[i]] = i;
        }
    }

    int getSize() {
        return this.size;
    }

    private void sort(int left, int right) {
        if (left < right) {
            int pivot = this.hashCodes[left + (right - left) / 2];
            int i = left;
            int j = right;
            while (i <= j) {
                while (this.hashCodes[i] < pivot) {
                    ++i;
                }
                while (this.hashCodes[j] > pivot) {
                    --j;
                }
                if (i > j) continue;
                this.swap(i, j);
                ++i;
                --j;
            }
            if (left < j) {
                this.sort(left, j);
            }
            if (right > i) {
                this.sort(i, right);
            }
        }
    }

    private void swap(int i, int j) {
        JarFileEntries.swap(this.hashCodes, i, j);
        this.centralDirectoryOffsets.swap(i, j);
        JarFileEntries.swap(this.positions, i, j);
    }

    @Override
    public Iterator<JarEntry> iterator() {
        return new EntryIterator(NO_VALIDATION);
    }

    Iterator<JarEntry> iterator(Runnable validator) {
        return new EntryIterator(validator);
    }

    boolean containsEntry(CharSequence name) {
        return this.getEntry(name, FileHeader.class, true) != null;
    }

    JarEntry getEntry(CharSequence name) {
        return this.getEntry(name, JarEntry.class, true);
    }

    InputStream getInputStream(String name) throws IOException {
        FileHeader entry = this.getEntry(name, FileHeader.class, false);
        return this.getInputStream(entry);
    }

    InputStream getInputStream(FileHeader entry) throws IOException {
        if (entry == null) {
            return null;
        }
        InputStream inputStream = this.getEntryData(entry).getInputStream();
        if (entry.getMethod() == 8) {
            inputStream = new ZipInflaterInputStream(inputStream, (int)entry.getSize());
        }
        return inputStream;
    }

    RandomAccessData getEntryData(String name) throws IOException {
        FileHeader entry = this.getEntry(name, FileHeader.class, false);
        if (entry == null) {
            return null;
        }
        return this.getEntryData(entry);
    }

    private RandomAccessData getEntryData(FileHeader entry) throws IOException {
        RandomAccessData data = this.jarFile.getData();
        byte[] localHeader = data.read(entry.getLocalHeaderOffset(), 30L);
        long nameLength = Bytes.littleEndianValue(localHeader, 26, 2);
        long extraLength = Bytes.littleEndianValue(localHeader, 28, 2);
        return data.getSubsection(entry.getLocalHeaderOffset() + 30L + nameLength + extraLength, entry.getCompressedSize());
    }

    private <T extends FileHeader> T getEntry(CharSequence name, Class<T> type, boolean cacheEntry) {
        T entry = this.doGetEntry(name, type, cacheEntry, null);
        if (!this.isMetaInfEntry(name) && this.isMultiReleaseJar()) {
            AsciiBytes nameAlias;
            AsciiBytes asciiBytes = nameAlias = entry instanceof JarEntry ? ((JarEntry)entry).getAsciiBytesName() : new AsciiBytes(name.toString());
            for (int version = RUNTIME_VERSION; version > 8; --version) {
                T versionedEntry = this.doGetEntry("META-INF/versions/" + version + "/" + name, type, cacheEntry, nameAlias);
                if (versionedEntry == null) continue;
                return versionedEntry;
            }
        }
        return entry;
    }

    private boolean isMetaInfEntry(CharSequence name) {
        return name.toString().startsWith(META_INF_PREFIX);
    }

    private boolean isMultiReleaseJar() {
        Boolean multiRelease = this.multiReleaseJar;
        if (multiRelease != null) {
            return multiRelease;
        }
        try {
            Manifest manifest = this.jarFile.getManifest();
            if (manifest == null) {
                multiRelease = false;
            } else {
                Attributes attributes = manifest.getMainAttributes();
                multiRelease = attributes.containsKey(MULTI_RELEASE);
            }
        }
        catch (IOException ex) {
            multiRelease = false;
        }
        this.multiReleaseJar = multiRelease;
        return multiRelease;
    }

    private <T extends FileHeader> T doGetEntry(CharSequence name, Class<T> type, boolean cacheEntry, AsciiBytes nameAlias) {
        int hashCode = AsciiBytes.hashCode(name);
        T entry = this.getEntry(hashCode, name, '\u0000', type, cacheEntry, nameAlias);
        if (entry == null) {
            hashCode = AsciiBytes.hashCode(hashCode, '/');
            entry = this.getEntry(hashCode, name, '/', type, cacheEntry, nameAlias);
        }
        return entry;
    }

    private <T extends FileHeader> T getEntry(int hashCode, CharSequence name, char suffix, Class<T> type, boolean cacheEntry, AsciiBytes nameAlias) {
        for (int index = this.getFirstIndex(hashCode); index >= 0 && index < this.size && this.hashCodes[index] == hashCode; ++index) {
            T entry = this.getEntry(index, type, cacheEntry, nameAlias);
            if (!entry.hasName(name, suffix)) continue;
            return entry;
        }
        return null;
    }

    private <T extends FileHeader> T getEntry(int index, Class<T> type, boolean cacheEntry, AsciiBytes nameAlias) {
        try {
            FileHeader entry;
            long offset = this.centralDirectoryOffsets.get(index);
            FileHeader cached = this.entriesCache.get(index);
            FileHeader fileHeader = entry = cached != null ? cached : CentralDirectoryFileHeader.fromRandomAccessData(this.centralDirectoryData, offset, this.filter);
            if (CentralDirectoryFileHeader.class.equals(entry.getClass()) && type.equals(JarEntry.class)) {
                entry = new JarEntry(this.jarFile, index, (CentralDirectoryFileHeader)entry, nameAlias);
            }
            if (cacheEntry && cached != entry) {
                this.entriesCache.put(index, entry);
            }
            return (T)entry;
        }
        catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private int getFirstIndex(int hashCode) {
        int index = Arrays.binarySearch(this.hashCodes, 0, this.size, hashCode);
        if (index < 0) {
            return -1;
        }
        while (index > 0 && this.hashCodes[index - 1] == hashCode) {
            --index;
        }
        return index;
    }

    void clearCache() {
        this.entriesCache.clear();
    }

    private AsciiBytes applyFilter(AsciiBytes name) {
        return this.filter != null ? this.filter.apply(name) : name;
    }

    JarEntryCertification getCertification(JarEntry entry) throws IOException {
        JarEntryCertification certification;
        JarEntryCertification[] certifications = this.certifications;
        if (certifications == null) {
            certifications = new JarEntryCertification[this.size];
            try (JarInputStream certifiedJarStream = new JarInputStream(this.jarFile.getData().getInputStream());){
                java.util.jar.JarEntry certifiedEntry = null;
                while ((certifiedEntry = certifiedJarStream.getNextJarEntry()) != null) {
                    certifiedJarStream.closeEntry();
                    int index = this.getEntryIndex(certifiedEntry.getName());
                    if (index == -1) continue;
                    certifications[index] = JarEntryCertification.from(certifiedEntry);
                }
            }
            this.certifications = certifications;
        }
        return (certification = certifications[entry.getIndex()]) != null ? certification : JarEntryCertification.NONE;
    }

    private int getEntryIndex(CharSequence name) {
        int hashCode = AsciiBytes.hashCode(name);
        for (int index = this.getFirstIndex(hashCode); index >= 0 && index < this.size && this.hashCodes[index] == hashCode; ++index) {
            FileHeader candidate = this.getEntry(index, FileHeader.class, false, null);
            if (!candidate.hasName(name, '\u0000')) continue;
            return index;
        }
        return -1;
    }

    private static void swap(int[] array, int i, int j) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    private static void swap(long[] array, int i, int j) {
        long temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    static {
        int version;
        NO_VALIDATION = () -> {};
        MULTI_RELEASE = new Attributes.Name("Multi-Release");
        try {
            Object runtimeVersion = Runtime.class.getMethod("version", new Class[0]).invoke(null, new Object[0]);
            version = (Integer)runtimeVersion.getClass().getMethod("major", new Class[0]).invoke(runtimeVersion, new Object[0]);
        }
        catch (Throwable ex) {
            version = 8;
        }
        RUNTIME_VERSION = version;
    }

    private static final class Zip64Offsets
    implements Offsets {
        private final long[] offsets;

        private Zip64Offsets(int size) {
            this.offsets = new long[size];
        }

        @Override
        public void swap(int i, int j) {
            JarFileEntries.swap(this.offsets, i, j);
        }

        @Override
        public void set(int index, long value) {
            this.offsets[index] = value;
        }

        @Override
        public long get(int index) {
            return this.offsets[index];
        }
    }

    private static final class ZipOffsets
    implements Offsets {
        private final int[] offsets;

        private ZipOffsets(int size) {
            this.offsets = new int[size];
        }

        @Override
        public void swap(int i, int j) {
            JarFileEntries.swap(this.offsets, i, j);
        }

        @Override
        public void set(int index, long value) {
            this.offsets[index] = (int)value;
        }

        @Override
        public long get(int index) {
            return this.offsets[index];
        }
    }

    private static interface Offsets {
        public void set(int var1, long var2);

        public long get(int var1);

        public void swap(int var1, int var2);

        public static Offsets from(CentralDirectoryEndRecord endRecord) {
            int size = endRecord.getNumberOfRecords();
            return endRecord.isZip64() ? new Zip64Offsets(size) : new ZipOffsets(size);
        }
    }

    private final class EntryIterator
    implements Iterator<JarEntry> {
        private final Runnable validator;
        private int index = 0;

        private EntryIterator(Runnable validator) {
            this.validator = validator;
            validator.run();
        }

        @Override
        public boolean hasNext() {
            this.validator.run();
            return this.index < JarFileEntries.this.size;
        }

        @Override
        public JarEntry next() {
            this.validator.run();
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            int entryIndex = JarFileEntries.this.positions[this.index];
            ++this.index;
            return (JarEntry)JarFileEntries.this.getEntry(entryIndex, JarEntry.class, false, null);
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Permission;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.springframework.boot.loader.jar.AbstractJarFile;
import org.springframework.boot.loader.jar.JarFile;

class JarFileWrapper
extends AbstractJarFile {
    private final JarFile parent;

    JarFileWrapper(JarFile parent) throws IOException {
        super(parent.getRootJarFile().getFile());
        this.parent = parent;
        if (System.getSecurityManager() == null) {
            super.close();
        }
    }

    @Override
    URL getUrl() throws MalformedURLException {
        return this.parent.getUrl();
    }

    @Override
    AbstractJarFile.JarFileType getType() {
        return this.parent.getType();
    }

    @Override
    Permission getPermission() {
        return this.parent.getPermission();
    }

    @Override
    public Manifest getManifest() throws IOException {
        return this.parent.getManifest();
    }

    @Override
    public Enumeration<JarEntry> entries() {
        return this.parent.entries();
    }

    @Override
    public Stream<JarEntry> stream() {
        return this.parent.stream();
    }

    @Override
    public JarEntry getJarEntry(String name) {
        return this.parent.getJarEntry(name);
    }

    @Override
    public ZipEntry getEntry(String name) {
        return this.parent.getEntry(name);
    }

    @Override
    InputStream getInputStream() throws IOException {
        return this.parent.getInputStream();
    }

    @Override
    public synchronized InputStream getInputStream(ZipEntry ze) throws IOException {
        return this.parent.getInputStream(ze);
    }

    @Override
    public String getComment() {
        return this.parent.getComment();
    }

    @Override
    public int size() {
        return this.parent.size();
    }

    public String toString() {
        return this.parent.toString();
    }

    @Override
    public String getName() {
        return this.parent.getName();
    }

    static JarFile unwrap(java.util.jar.JarFile jarFile) {
        if (jarFile instanceof JarFile) {
            return (JarFile)jarFile;
        }
        if (jarFile instanceof JarFileWrapper) {
            return JarFileWrapper.unwrap(((JarFileWrapper)jarFile).parent);
        }
        throw new IllegalStateException("Not a JarFile or Wrapper");
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.URLStreamHandler;
import java.security.Permission;
import org.springframework.boot.loader.jar.AbstractJarFile;
import org.springframework.boot.loader.jar.AsciiBytes;
import org.springframework.boot.loader.jar.JarEntry;
import org.springframework.boot.loader.jar.JarFile;
import org.springframework.boot.loader.jar.StringSequence;

final class JarURLConnection
extends java.net.JarURLConnection {
    private static ThreadLocal<Boolean> useFastExceptions = new ThreadLocal();
    private static final FileNotFoundException FILE_NOT_FOUND_EXCEPTION = new FileNotFoundException("Jar file or entry not found");
    private static final IllegalStateException NOT_FOUND_CONNECTION_EXCEPTION = new IllegalStateException(FILE_NOT_FOUND_EXCEPTION);
    private static final String SEPARATOR = "!/";
    private static final URL EMPTY_JAR_URL;
    private static final JarEntryName EMPTY_JAR_ENTRY_NAME;
    private static final JarURLConnection NOT_FOUND_CONNECTION;
    private final AbstractJarFile jarFile;
    private Permission permission;
    private URL jarFileUrl;
    private final JarEntryName jarEntryName;
    private java.util.jar.JarEntry jarEntry;

    private JarURLConnection(URL url, AbstractJarFile jarFile, JarEntryName jarEntryName) throws IOException {
        super(EMPTY_JAR_URL);
        this.url = url;
        this.jarFile = jarFile;
        this.jarEntryName = jarEntryName;
    }

    @Override
    public void connect() throws IOException {
        if (this.jarFile == null) {
            throw FILE_NOT_FOUND_EXCEPTION;
        }
        if (!this.jarEntryName.isEmpty() && this.jarEntry == null) {
            this.jarEntry = this.jarFile.getJarEntry(this.getEntryName());
            if (this.jarEntry == null) {
                this.throwFileNotFound(this.jarEntryName, this.jarFile);
            }
        }
        this.connected = true;
    }

    @Override
    public java.util.jar.JarFile getJarFile() throws IOException {
        this.connect();
        return this.jarFile;
    }

    @Override
    public URL getJarFileURL() {
        if (this.jarFile == null) {
            throw NOT_FOUND_CONNECTION_EXCEPTION;
        }
        if (this.jarFileUrl == null) {
            this.jarFileUrl = this.buildJarFileUrl();
        }
        return this.jarFileUrl;
    }

    private URL buildJarFileUrl() {
        try {
            String spec = this.jarFile.getUrl().getFile();
            if (spec.endsWith(SEPARATOR)) {
                spec = spec.substring(0, spec.length() - SEPARATOR.length());
            }
            if (!spec.contains(SEPARATOR)) {
                return new URL(spec);
            }
            return new URL("jar:" + spec);
        }
        catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public java.util.jar.JarEntry getJarEntry() throws IOException {
        if (this.jarEntryName == null || this.jarEntryName.isEmpty()) {
            return null;
        }
        this.connect();
        return this.jarEntry;
    }

    @Override
    public String getEntryName() {
        if (this.jarFile == null) {
            throw NOT_FOUND_CONNECTION_EXCEPTION;
        }
        return this.jarEntryName.toString();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream inputStream;
        if (this.jarFile == null) {
            throw FILE_NOT_FOUND_EXCEPTION;
        }
        if (this.jarEntryName.isEmpty() && this.jarFile.getType() == AbstractJarFile.JarFileType.DIRECT) {
            throw new IOException("no entry name specified");
        }
        this.connect();
        InputStream inputStream2 = inputStream = this.jarEntryName.isEmpty() ? this.jarFile.getInputStream() : this.jarFile.getInputStream(this.jarEntry);
        if (inputStream == null) {
            this.throwFileNotFound(this.jarEntryName, this.jarFile);
        }
        return inputStream;
    }

    private void throwFileNotFound(Object entry, AbstractJarFile jarFile) throws FileNotFoundException {
        if (Boolean.TRUE.equals(useFastExceptions.get())) {
            throw FILE_NOT_FOUND_EXCEPTION;
        }
        throw new FileNotFoundException("JAR entry " + entry + " not found in " + jarFile.getName());
    }

    @Override
    public int getContentLength() {
        long length = this.getContentLengthLong();
        if (length > Integer.MAX_VALUE) {
            return -1;
        }
        return (int)length;
    }

    @Override
    public long getContentLengthLong() {
        if (this.jarFile == null) {
            return -1L;
        }
        try {
            if (this.jarEntryName.isEmpty()) {
                return this.jarFile.size();
            }
            java.util.jar.JarEntry entry = this.getJarEntry();
            return entry != null ? (long)((int)entry.getSize()) : -1L;
        }
        catch (IOException ex) {
            return -1L;
        }
    }

    @Override
    public Object getContent() throws IOException {
        this.connect();
        return this.jarEntryName.isEmpty() ? this.jarFile : super.getContent();
    }

    @Override
    public String getContentType() {
        return this.jarEntryName != null ? this.jarEntryName.getContentType() : null;
    }

    @Override
    public Permission getPermission() throws IOException {
        if (this.jarFile == null) {
            throw FILE_NOT_FOUND_EXCEPTION;
        }
        if (this.permission == null) {
            this.permission = this.jarFile.getPermission();
        }
        return this.permission;
    }

    @Override
    public long getLastModified() {
        if (this.jarFile == null || this.jarEntryName.isEmpty()) {
            return 0L;
        }
        try {
            java.util.jar.JarEntry entry = this.getJarEntry();
            return entry != null ? entry.getTime() : 0L;
        }
        catch (IOException ex) {
            return 0L;
        }
    }

    static void setUseFastExceptions(boolean useFastExceptions) {
        JarURLConnection.useFastExceptions.set(useFastExceptions);
    }

    static JarURLConnection get(URL url, JarFile jarFile) throws IOException {
        int separator;
        StringSequence spec = new StringSequence(url.getFile());
        int index = JarURLConnection.indexOfRootSpec(spec, jarFile.getPathFromRoot());
        if (index == -1) {
            return Boolean.TRUE.equals(useFastExceptions.get()) ? NOT_FOUND_CONNECTION : new JarURLConnection(url, null, EMPTY_JAR_ENTRY_NAME);
        }
        while ((separator = spec.indexOf(SEPARATOR, index)) > 0) {
            JarEntryName entryName = JarEntryName.get(spec.subSequence(index, separator));
            JarEntry jarEntry = jarFile.getJarEntry(entryName.toCharSequence());
            if (jarEntry == null) {
                return JarURLConnection.notFound(jarFile, entryName);
            }
            jarFile = jarFile.getNestedJarFile(jarEntry);
            index = separator + SEPARATOR.length();
        }
        JarEntryName jarEntryName = JarEntryName.get(spec, index);
        if (Boolean.TRUE.equals(useFastExceptions.get()) && !jarEntryName.isEmpty() && !jarFile.containsEntry(jarEntryName.toString())) {
            return NOT_FOUND_CONNECTION;
        }
        return new JarURLConnection(url, jarFile.getWrapper(), jarEntryName);
    }

    private static int indexOfRootSpec(StringSequence file, String pathFromRoot) {
        int separatorIndex = file.indexOf(SEPARATOR);
        if (separatorIndex < 0 || !file.startsWith(pathFromRoot, separatorIndex)) {
            return -1;
        }
        return separatorIndex + SEPARATOR.length() + pathFromRoot.length();
    }

    private static JarURLConnection notFound() {
        try {
            return JarURLConnection.notFound(null, null);
        }
        catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static JarURLConnection notFound(JarFile jarFile, JarEntryName jarEntryName) throws IOException {
        if (Boolean.TRUE.equals(useFastExceptions.get())) {
            return NOT_FOUND_CONNECTION;
        }
        return new JarURLConnection(null, jarFile, jarEntryName);
    }

    static {
        try {
            EMPTY_JAR_URL = new URL("jar:", null, 0, "file:!/", new URLStreamHandler(){

                @Override
                protected URLConnection openConnection(URL u) throws IOException {
                    return null;
                }
            });
        }
        catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
        EMPTY_JAR_ENTRY_NAME = new JarEntryName(new StringSequence(""));
        NOT_FOUND_CONNECTION = JarURLConnection.notFound();
    }

    static class JarEntryName {
        private final StringSequence name;
        private String contentType;

        JarEntryName(StringSequence spec) {
            this.name = this.decode(spec);
        }

        private StringSequence decode(StringSequence source) {
            if (source.isEmpty() || source.indexOf('%') < 0) {
                return source;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream(source.length());
            this.write(source.toString(), bos);
            return new StringSequence(AsciiBytes.toString(bos.toByteArray()));
        }

        private void write(String source, ByteArrayOutputStream outputStream) {
            int length = source.length();
            for (int i = 0; i < length; ++i) {
                char c = source.charAt(i);
                if (c > '\u007f') {
                    try {
                        String encoded = URLEncoder.encode(String.valueOf(c), "UTF-8");
                        this.write(encoded, outputStream);
                        continue;
                    }
                    catch (UnsupportedEncodingException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                if (c == '%') {
                    if (i + 2 >= length) {
                        throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
                    }
                    c = this.decodeEscapeSequence(source, i);
                    i += 2;
                }
                outputStream.write(c);
            }
        }

        private char decodeEscapeSequence(String source, int i) {
            int hi = Character.digit(source.charAt(i + 1), 16);
            int lo = Character.digit(source.charAt(i + 2), 16);
            if (hi == -1 || lo == -1) {
                throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
            }
            return (char)((hi << 4) + lo);
        }

        CharSequence toCharSequence() {
            return this.name;
        }

        public String toString() {
            return this.name.toString();
        }

        boolean isEmpty() {
            return this.name.isEmpty();
        }

        String getContentType() {
            if (this.contentType == null) {
                this.contentType = this.deduceContentType();
            }
            return this.contentType;
        }

        private String deduceContentType() {
            String type = this.isEmpty() ? "x-java/jar" : null;
            type = type != null ? type : URLConnection.guessContentTypeFromName(this.toString());
            type = type != null ? type : "content/unknown";
            return type;
        }

        static JarEntryName get(StringSequence spec) {
            return JarEntryName.get(spec, 0);
        }

        static JarEntryName get(StringSequence spec, int beginIndex) {
            if (spec.length() <= beginIndex) {
                return EMPTY_JAR_ENTRY_NAME;
            }
            return new JarEntryName(spec.subSequence(beginIndex));
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.util.Objects;

final class StringSequence
implements CharSequence {
    private final String source;
    private final int start;
    private final int end;
    private int hash;

    StringSequence(String source) {
        this(source, 0, source != null ? source.length() : -1);
    }

    StringSequence(String source, int start, int end) {
        Objects.requireNonNull(source, "Source must not be null");
        if (start < 0) {
            throw new StringIndexOutOfBoundsException(start);
        }
        if (end > source.length()) {
            throw new StringIndexOutOfBoundsException(end);
        }
        this.source = source;
        this.start = start;
        this.end = end;
    }

    StringSequence subSequence(int start) {
        return this.subSequence(start, this.length());
    }

    @Override
    public StringSequence subSequence(int start, int end) {
        int subSequenceStart = this.start + start;
        int subSequenceEnd = this.start + end;
        if (subSequenceStart > this.end) {
            throw new StringIndexOutOfBoundsException(start);
        }
        if (subSequenceEnd > this.end) {
            throw new StringIndexOutOfBoundsException(end);
        }
        if (start == 0 && subSequenceEnd == this.end) {
            return this;
        }
        return new StringSequence(this.source, subSequenceStart, subSequenceEnd);
    }

    public boolean isEmpty() {
        return this.length() == 0;
    }

    @Override
    public int length() {
        return this.end - this.start;
    }

    @Override
    public char charAt(int index) {
        return this.source.charAt(this.start + index);
    }

    int indexOf(char ch) {
        return this.source.indexOf(ch, this.start) - this.start;
    }

    int indexOf(String str) {
        return this.source.indexOf(str, this.start) - this.start;
    }

    int indexOf(String str, int fromIndex) {
        return this.source.indexOf(str, this.start + fromIndex) - this.start;
    }

    boolean startsWith(String prefix) {
        return this.startsWith(prefix, 0);
    }

    boolean startsWith(String prefix, int offset) {
        int prefixLength = prefix.length();
        int length = this.length();
        if (length - prefixLength - offset < 0) {
            return false;
        }
        return this.source.startsWith(prefix, this.start + offset);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CharSequence)) {
            return false;
        }
        CharSequence other = (CharSequence)obj;
        int n = this.length();
        if (n != other.length()) {
            return false;
        }
        int i = 0;
        while (n-- != 0) {
            if (this.charAt(i) != other.charAt(i)) {
                return false;
            }
            ++i;
        }
        return true;
    }

    public int hashCode() {
        int hash = this.hash;
        if (hash == 0 && this.length() > 0) {
            for (int i = this.start; i < this.end; ++i) {
                hash = 31 * hash + this.source.charAt(i);
            }
            this.hash = hash;
        }
        return hash;
    }

    @Override
    public String toString() {
        return this.source.substring(this.start, this.end);
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jar;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

class ZipInflaterInputStream
extends InflaterInputStream {
    private int available;
    private boolean extraBytesWritten;

    ZipInflaterInputStream(InputStream inputStream, int size) {
        super(inputStream, new Inflater(true), ZipInflaterInputStream.getInflaterBufferSize(size));
        this.available = size;
    }

    @Override
    public int available() throws IOException {
        if (this.available < 0) {
            return super.available();
        }
        return this.available;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = super.read(b, off, len);
        if (result != -1) {
            this.available -= result;
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.inf.end();
    }

    @Override
    protected void fill() throws IOException {
        try {
            super.fill();
        }
        catch (EOFException ex) {
            if (this.extraBytesWritten) {
                throw ex;
            }
            this.len = 1;
            this.buf[0] = 0;
            this.extraBytesWritten = true;
            this.inf.setInput(this.buf, 0, this.len);
        }
    }

    private static int getInflaterBufferSize(long size) {
        size = (size += 2L) > 65536L ? 8192L : size;
        size = size <= 0L ? 4096L : size;
        return (int)size;
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jarmode;

public interface JarMode {
    public boolean accepts(String var1);

    public void run(String var1, String[] var2);
}

/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.core.io.support.SpringFactoriesLoader
 *  org.springframework.util.ClassUtils
 */
package org.springframework.boot.loader.jarmode;

import java.util.List;
import org.springframework.boot.loader.jarmode.JarMode;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;

public final class JarModeLauncher {
    static final String DISABLE_SYSTEM_EXIT = JarModeLauncher.class.getName() + ".DISABLE_SYSTEM_EXIT";

    private JarModeLauncher() {
    }

    public static void main(String[] args) {
        String mode = System.getProperty("jarmode");
        List candidates = SpringFactoriesLoader.loadFactories(JarMode.class, (ClassLoader)ClassUtils.getDefaultClassLoader());
        for (JarMode candidate : candidates) {
            if (!candidate.accepts(mode)) continue;
            candidate.run(mode, args);
            return;
        }
        System.err.println("Unsupported jarmode '" + mode + "'");
        if (!Boolean.getBoolean(DISABLE_SYSTEM_EXIT)) {
            System.exit(1);
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.jarmode;

import java.util.Arrays;
import org.springframework.boot.loader.jarmode.JarMode;

class TestJarMode
implements JarMode {
    TestJarMode() {
    }

    @Override
    public boolean accepts(String mode) {
        return "test".equals(mode);
    }

    @Override
    public void run(String mode, String[] args) {
        System.out.println("running in " + mode + " jar mode " + Arrays.asList(args));
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.boot.loader.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public abstract class SystemPropertyUtils {
    public static final String PLACEHOLDER_PREFIX = "${";
    public static final String PLACEHOLDER_SUFFIX = "}";
    public static final String VALUE_SEPARATOR = ":";
    private static final String SIMPLE_PREFIX = "${".substring(1);

    public static String resolvePlaceholders(String text) {
        if (text == null) {
            return text;
        }
        return SystemPropertyUtils.parseStringValue(null, text, text, new HashSet<String>());
    }

    public static String resolvePlaceholders(Properties properties, String text) {
        if (text == null) {
            return text;
        }
        return SystemPropertyUtils.parseStringValue(properties, text, text, new HashSet<String>());
    }

    private static String parseStringValue(Properties properties, String value, String current, Set<String> visitedPlaceholders) {
        StringBuilder buf = new StringBuilder(current);
        int startIndex = current.indexOf(PLACEHOLDER_PREFIX);
        while (startIndex != -1) {
            int endIndex = SystemPropertyUtils.findPlaceholderEndIndex(buf, startIndex);
            if (endIndex != -1) {
                int separatorIndex;
                String placeholder = buf.substring(startIndex + PLACEHOLDER_PREFIX.length(), endIndex);
                String originalPlaceholder = placeholder;
                if (!visitedPlaceholders.add(originalPlaceholder)) {
                    throw new IllegalArgumentException("Circular placeholder reference '" + originalPlaceholder + "' in property definitions");
                }
                String propVal = SystemPropertyUtils.resolvePlaceholder(properties, value, placeholder = SystemPropertyUtils.parseStringValue(properties, value, placeholder, visitedPlaceholders));
                if (propVal == null && (separatorIndex = placeholder.indexOf(VALUE_SEPARATOR)) != -1) {
                    String actualPlaceholder = placeholder.substring(0, separatorIndex);
                    String defaultValue = placeholder.substring(separatorIndex + VALUE_SEPARATOR.length());
                    propVal = SystemPropertyUtils.resolvePlaceholder(properties, value, actualPlaceholder);
                    if (propVal == null) {
                        propVal = defaultValue;
                    }
                }
                if (propVal != null) {
                    propVal = SystemPropertyUtils.parseStringValue(properties, value, propVal, visitedPlaceholders);
                    buf.replace(startIndex, endIndex + PLACEHOLDER_SUFFIX.length(), propVal);
                    startIndex = buf.indexOf(PLACEHOLDER_PREFIX, startIndex + propVal.length());
                } else {
                    startIndex = buf.indexOf(PLACEHOLDER_PREFIX, endIndex + PLACEHOLDER_SUFFIX.length());
                }
                visitedPlaceholders.remove(originalPlaceholder);
                continue;
            }
            startIndex = -1;
        }
        return buf.toString();
    }

    private static String resolvePlaceholder(Properties properties, String text, String placeholderName) {
        String propVal = SystemPropertyUtils.getProperty(placeholderName, null, text);
        if (propVal != null) {
            return propVal;
        }
        return properties != null ? properties.getProperty(placeholderName) : null;
    }

    public static String getProperty(String key) {
        return SystemPropertyUtils.getProperty(key, null, "");
    }

    public static String getProperty(String key, String defaultValue) {
        return SystemPropertyUtils.getProperty(key, defaultValue, "");
    }

    public static String getProperty(String key, String defaultValue, String text) {
        try {
            String name;
            String propVal = System.getProperty(key);
            if (propVal == null) {
                propVal = System.getenv(key);
            }
            if (propVal == null) {
                name = key.replace('.', '_');
                propVal = System.getenv(name);
            }
            if (propVal == null) {
                name = key.toUpperCase(Locale.ENGLISH).replace('.', '_');
                propVal = System.getenv(name);
            }
            if (propVal != null) {
                return propVal;
            }
        }
        catch (Throwable ex) {
            System.err.println("Could not resolve key '" + key + "' in '" + text + "' as system property or in environment: " + ex);
        }
        return defaultValue;
    }

    private static int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
        int index = startIndex + PLACEHOLDER_PREFIX.length();
        int withinNestedPlaceholder = 0;
        while (index < buf.length()) {
            if (SystemPropertyUtils.substringMatch(buf, index, PLACEHOLDER_SUFFIX)) {
                if (withinNestedPlaceholder > 0) {
                    --withinNestedPlaceholder;
                    index += PLACEHOLDER_SUFFIX.length();
                    continue;
                }
                return index;
            }
            if (SystemPropertyUtils.substringMatch(buf, index, SIMPLE_PREFIX)) {
                ++withinNestedPlaceholder;
                index += SIMPLE_PREFIX.length();
                continue;
            }
            ++index;
        }
        return -1;
    }

    private static boolean substringMatch(CharSequence str, int index, CharSequence substring) {
        for (int j = 0; j < substring.length(); ++j) {
            int i = index + j;
            if (i < str.length() && str.charAt(i) == substring.charAt(j)) continue;
            return false;
        }
        return true;
    }
}

/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.uuid.impl.RandomBasedGenerator
 *  ctf.morelogin.MorelOginController
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package ctf.morelogin;

import com.fasterxml.uuid.impl.RandomBasedGenerator;
import java.util.Random;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MorelOginController {
    private static final Random random = new Random();

    @GetMapping(value={"/getToken"})
    public String getToken() {
        UUID uuid = new RandomBasedGenerator(random).generate();
        return uuid.toString();
    }
}

/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ctf.morelogin.MorelOginService
 *  org.springframework.boot.SpringApplication
 *  org.springframework.boot.autoconfigure.SpringBootApplication
 */
package ctf.morelogin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MorelOginService {
    public static void main(String[] args) {
        SpringApplication.run(MorelOginService.class, (String[])args);
    }
}

