package org.walkmod.javalang.walkers

import com.google.common.base.Function
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.walkmod.conf.entities.TransformationConfig
import org.walkmod.conf.entities.impl.ChainConfigImpl
import org.walkmod.conf.entities.impl.TransformationConfigImpl
import org.walkmod.conf.entities.impl.WalkerConfigImpl
import org.walkmod.conf.entities.impl.WriterConfigImpl
import org.walkmod.javalang.ast.CompilationUnit
import org.walkmod.javalang.compiler.Compiler
import org.walkmod.javalang.visitors.VoidVisitor
import org.walkmod.javalang.writers.StringWriter
import org.walkmod.walkers.VisitorContext

import java.nio.file.Files

class ExampleAstChangeVisitorTest extends SemanticTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String saveUserDir

    @Before
    public void setUp() {
        saveUserDir = System.getProperty("user.dir")
    }

    @After
    public void tearDown() throws Exception {
        if (saveUserDir != null) {
            System.setProperty("user.dir", saveUserDir)
        }
    }

    @Test
    void testMigrateAssertWithDefaultWalker() throws Exception {
        checkMigrateAssert(new Function<String, String>() {
            @Override
            String apply(String src) {
                return applyVisitorWithDefaultWalker(src, new ExampleAstChangeVisitor())
            }
        })
    }

    @Test
    void testMigrateAssertDirectly() throws Exception {
        checkMigrateAssert(new Function<String, String>() {
            @Override
            String apply(String src) {
                return applyVisitorDirectly(src, new ExampleAstChangeVisitor())
            }
        })
    }

    private static void checkMigrateAssert(Function<String,String> migrator) throws Exception {
        final String src = '''\
import junit.framework.Assert;

public class Usage extends Assert {

  public void f() {
    fail("x");
  }
}
'''
        def refactored = migrator.apply(src)
        Assertions.assertThat(refactored)
                .isEqualTo(src
                .    replace("junit.framework.Assert", 'org.junit.Assert')
                .    replace(" fail", ' Assert.fail')
        )
    }

    private String applyVisitorWithDefaultWalker(String src, final visitor) {
        String readerWriterPath = "src/test/resources"
        final File dir = temporaryFolder.newFolder(readerWriterPath.split("/"))
        final File tmpDir = temporaryFolder.getRoot()
        // set current working directory
        System.setProperty("user.dir", tmpDir.getAbsolutePath())

        def compiler = new Compiler();
        compiler.compile(new File(CLASSES_DIR).getAbsoluteFile(), dir, src)

        File srcFile = new File(dir, "Usage.java")
        srcFile.text = src

        System.out.println("dir=" + dir)
//        Thread.sleep(1000 * 100)

        DefaultJavaWalker walker = new DefaultJavaWalker() {
            @Override
            protected String getReaderPath() {
                return readerWriterPath;
            }
        }
        walker.setWriter(new StringWriter())
        walker.setParser(new DefaultJavaParser() {})
        walker.setVisitors([visitor])
        walker.setClassLoader(getClassLoader());

        ChainConfigImpl cfg = new ChainConfigImpl();
        WalkerConfigImpl walkerCfg = new WalkerConfigImpl();

        List<TransformationConfig> transformations = new LinkedList<TransformationConfig>();
        TransformationConfigImpl tcfg = new TransformationConfigImpl();
        tcfg.setVisitorInstance(visitor);
        transformations.add(tcfg);
        walkerCfg.setTransformations(transformations);

        cfg.setWalkerConfig(walkerCfg);
        walker.setChainConfig(cfg);

        def writerConfig = new WriterConfigImpl()
        writerConfig.setPath(readerWriterPath)
        cfg.setWriterConfig(writerConfig)

        walker.walk(srcFile)

//        Thread.sleep(100 * 1000)
        return new String(Files.readAllBytes(srcFile.toPath()), "UTF-8")
    }

    private String applyVisitorDirectly(String src, VoidVisitor visitor) {
        CompilationUnit cu = compile(src)
        def vc = new VisitorContext()
        cu.accept(visitor, vc)
        return pretty(cu)
    }

    private static String pretty(CompilationUnit cu) {
        cu.getPrettySource((char) ' ', 0, 2)
    }
}