/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.redhat.ceylon.compiler.java.test.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.redhat.ceylon.compiler.java.test.CompilerTests;
import com.redhat.ceylon.compiler.java.test.JdkVersionDependentTests;

public class Java8InterfaceTests extends JdkVersionDependentTests {
    
    public Java8InterfaceTests(String target, String source) {
        super(target, source);
    }
    
    @Parameters(name="-target {0}")
    public static Iterable<Object[]> testParameters() {
        return Arrays.<Object[]>asList(
                new Object[]{"8", "8"});
    }

    List<String> optsForJava8Interfaces() {
        ArrayList<String> opts = new ArrayList<String>(getDefaultOptions());
        opts.add("-target");
        opts.add("8");
        opts.add("-source");
        opts.add("8");
        return opts;
    }
    
    @Override
    public void  compareWithJavaSource(String source) {
        super.compareWithJavaSource(optsForJava8Interfaces(), source+".src", source+".ceylon");
    }
    
    @Test
    public void ifaceWithConcreteMethods() {
        compareWithJavaSource("iface/InterfaceWithConcreteMethods");
        compareWithJavaSource("iface/InterfaceWithConcreteMethodsSatisfier");
    }
    
    @Test
    public void ifaceWithConcreteAttributes() {
        compareWithJavaSource("iface/InterfaceWithConcreteAttributes");
        compareWithJavaSource("iface/InterfaceWithConcreteAttributesSatisfier");
    }
    
    @Test
    public void genericIface() {
        compareWithJavaSource("iface/GenericInterface");
    }
    
    @Test
    public void qualifiers() {
        compareWithJavaSource("iface/Qualifiers");
    }
    
    @Test
    public void disambig() {
        compareWithJavaSource("iface/Disambig");
    }
    
    @Test
    public void defaultSatisfyCompanion() {
        compareWithJavaSource("iface/DefaultSatisfyCompanion");
    }
    
    @Test
    public void companionSatisfyDefault() {
        compareWithJavaSource("iface/CompanionSatisfyDefault");
    }
    
    @Test
    public void interfaceWithTypeMembers() {
        compareWithJavaSource("iface/InterfaceWithTypeMembers");
    }
    
    @Test
    public void mixed() {
        compareWithJavaSource("iface/Mixed");
    }
    
    @Test
    public void invokingObjectMethods() {
        compareWithJavaSource("iface/InvokingObjectMethods");
    }
    
    @Test
    public void namedArgumentInvocation() {
        compareWithJavaSource("iface/NamedArgumentInvocation");
    }
    
    @Test
    public void superToCompanion() {
        compareWithJavaSource("iface/SuperToCompanion");
    }
    
    @Test
    public void interfaceWithMpl() {
        compareWithJavaSource("iface/InterfaceWithMpl");
    }
    
    @Test
    public void super_() {
        compareWithJavaSource("iface/Super");
    }
    
    @Test
    public void superWithinSyntheticClass() {
        compareWithJavaSource("iface/SuperWithinSyntheticClass");
    }
    
    @Test
    public void outer() {
        compareWithJavaSource("iface/Outer");
    }
    
    @Test
    public void localClasses() {
        compareWithJavaSource("iface/LocalClasses");
    }
    
    @Test
    public void nonSharedMethods() {
        compileAndRun("com.redhat.ceylon.compiler.java.test.structure.iface.nonSharedMethods",
                "iface/NonSharedMethods.ceylon");
    }
    
    @Test
    public void inheritFrom() {
        compareWithJavaSource("iface/InheritFrom");
        run("com.redhat.ceylon.compiler.java.test.structure.iface.inheritFrom");
    }
    
    @Test
    public void emptyWithLeading() {
        //compareWithJavaSource("iface/EmptyWithLeading");
        compile("iface/EmptyWithLeading.ceylon");
        run("com.redhat.ceylon.compiler.java.test.structure.iface.emptyWithLeading");
    }
    
    @Test
    public void memberTypeAlias() {
        compareWithJavaSource("iface/MemberTypeAlias");
    }
}

