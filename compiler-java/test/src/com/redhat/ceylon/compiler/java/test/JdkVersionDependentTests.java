package com.redhat.ceylon.compiler.java.test;

import java.util.Arrays;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.redhat.ceylon.compiler.java.test.CompilerTests.ModuleWithArtifact;
import com.redhat.ceylon.model.typechecker.model.Module;

@RunWith(Parameterized.class)
public class JdkVersionDependentTests extends CompilerTests {

    @Parameters(name="-target {0}")
    public static Iterable<Object[]> testParameters() {
        return Arrays.<Object[]>asList(
                new Object[]{"7", "7"}, 
                new Object[]{"8", "8"}
                );
    }
    
    protected final String target;
    private final String source;
    
    public JdkVersionDependentTests(String target, String source) {
        super();
        this.target = target;
        this.source = source;
        if (target != null) {
            getDefaultOptions().add("-target");
            getDefaultOptions().add(target);
        }
        if (source != null) {
            getDefaultOptions().add("-source");
            getDefaultOptions().add(source);
        }
        if ("8".equals(target)) {
            getDefaultOptions().add("-interfaces");
            getDefaultOptions().add("default");
        } else {
            getDefaultOptions().add("-interfaces");
            getDefaultOptions().add("companion");
        }
    }
    @Override
    protected String getSrcName(String name) {
        String src = name+".src";
        if ("8".equals(target)) {
            src = name+".src8";
        } else {
            src = name+".src";
        }
        return src;
    }
    
    @Override
    protected final String getCarSuffix() {
        if ("8".equals(target)) {
            return "-jdk8.car";
        } else {
            return super.getCarSuffix();
        }
        
    }
    
    @Override
    public String getClassPathAsPath() {
        return getClassPathAsPath("8".equals(target));
    }

    @Override
    public String[] getClassPath() {
        return getClassPath("8".equals(target));
    }
    
    @Override
    public String getLanguageModuleCar() {
        return getLanguageModuleCar("8".equals(target));
    }
}
