package com.tyron.code.java.analysis;

import com.google.common.truth.Truth;
import com.tyron.code.project.ModuleManager;
import com.tyron.code.project.file.FileManager;
import com.tyron.code.project.file.SimpleFileManager;
import com.tyron.code.project.impl.FileSystemModuleManager;
import com.tyron.code.project.impl.ModuleInitializer;
import com.tyron.code.project.impl.model.JavaModuleImpl;
import com.tyron.code.project.impl.model.JdkModuleImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import shadow.javax.lang.model.element.Element;
import shadow.javax.lang.model.element.ElementKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ElementHandleTest {

    protected FileManager fileManager;
    protected ModuleManager moduleManager;
    protected JavaModuleImpl rootModule;
    protected Analyzer analyzer;

    @BeforeAll
    void setup() throws IOException {
        Path root = Files.createTempDirectory("test");
        fileManager = new SimpleFileManager(root, List.of());
        moduleManager = new FileSystemModuleManager(fileManager, root);
        moduleManager.initialize();

        rootModule = (JavaModuleImpl) moduleManager.getRootModule().getIncludedModules().get(0);

        JdkModuleImpl jdkModule = new JdkModuleImpl(moduleManager, Paths.get("src/test/resources/android.jar"), "11");
        new ModuleInitializer().initializeModule(jdkModule);
        rootModule.setJdk(jdkModule);

        analyzer = new Analyzer(fileManager, rootModule);
    }


    @Test
void test() throws Exception {
    // نام فایل باید برابر با نام کلاس باشد
    Path file = Files.createTempFile("Main", ".java");
    String contents = """
        class Main {
            static void main() {
                Main.main();
            }
            
            void instance() {}
        }
        """;
    Files.writeString(file, contents);

    CompletableFuture<ElementHandle<?>> handleFuture = new CompletableFuture<>();
    analyzer.analyze(file, contents, analysisResult -> {
        Element element = analysisResult.analyzed().iterator().next();
        handleFuture.complete(ElementHandle.create(element));
    });

    ElementHandle<?> handle = handleFuture.get(10, TimeUnit.SECONDS);
    assertNotNull(handle);
    assertEquals(ElementKind.CLASS, handle.getKind());
    assertEquals("Main", handle.getQualifiedName());

    CompletableFuture<Element> resolveFuture = new CompletableFuture<>();
    analyzer.analyze(file, contents, analysisResult -> {
        Element resolved = handle.resolve(analysisResult);
        resolveFuture.complete(resolved);
    });

    Element resolvedElement = resolveFuture.get(10, TimeUnit.SECONDS);
    assertNotNull(resolvedElement);
    assertEquals(ElementKind.CLASS, resolvedElement.getKind());
    assertEquals("Main", resolvedElement.getSimpleName().toString());
}
}
