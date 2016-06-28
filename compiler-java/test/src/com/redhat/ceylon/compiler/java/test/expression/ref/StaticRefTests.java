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
package com.redhat.ceylon.compiler.java.test.expression.ref;

import org.junit.Ignore;
import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerTests;
import com.redhat.ceylon.compiler.java.test.JdkVersionDependentTests;

public class StaticRefTests extends JdkVersionDependentTests {
    
    public StaticRefTests(String target, String source) {
        super(target, source);
    }

    @Test
    public void testRefAttributeRef() {
        compareWithJavaSource("AttributeRef");
        //compile("AttributeRef.ceylon");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.attributeRef");
    }
    
    @Test
    public void testRefValueParameterRef() {
        compareWithJavaSource("ValueParameterRef");
        //compile("ValueParameterRef.ceylon");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.valueParameterRef");
    }
    
    @Test
    public void testRefMethodRef() {
        compareWithJavaSource("MethodRef");
        //compile("MethodRef.ceylon");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.methodRef");
    }
    
    @Test
    public void testRefFunctionalParameterRef() {
        compareWithJavaSource("FunctionalParameterRef");
        //compile("FunctionalParameterRef.ceylon");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.functionalParameterRef");
    }
    
    @Test
    public void testRefMemberClassRef() {
        compareWithJavaSource("MemberClassRef");
        //compile("MemberClassRef.ceylon");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.memberClassRef");
    }
    
    @Test
    public void testRefMemberClassRefInFunction() {
        compareWithJavaSource("MemberClassRefInFunction");
        //compile("MemberClassRef.ceylon");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.memberClassRefInFunction");
    }
    
    @Test
    public void testRefFunrefs() {
        compareWithJavaSource("funrefs");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.funrefs");
        
    }
    
    @Test
    public void testRefBug1569() {
        compareWithJavaSource("Bug1569");
    }
    
    @Test
    public void testRefConstructorRef() {
        compareWithJavaSource("ConstructorRef");
        //compile("MemberClassRef.ceylon");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.constructorRef");
    }
    
    @Test
    public void testRefMemberClassConstructorRef() {
        compareWithJavaSource("MemberClassConstructorRef");
        //compile("MemberClassConstructorRef.ceylon");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.memberClassConstructorRef");
    }
    
    @Test
    public void testRefBug5898() {
        compareWithJavaSource("Bug5898");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.bug5898");
    }
    
    @Test
    public void testRefBug5899() {
        compareWithJavaSource("Bug5899");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.bug5899");
    }
    
    @Test
    public void testRefBug5965() {
        compareWithJavaSource("Bug5965");
        run("com.redhat.ceylon.compiler.java.test.expression.ref.bug5965");
    }

}
